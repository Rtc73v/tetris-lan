package com.echo.tetrislan.ai;

import com.echo.tetrislan.TetrisView;
import com.echo.tetrislan.net.PeerInfo;
import com.echo.tetrislan.core.BoardCodec;
import com.echo.tetrislan.core.Rules;
import com.echo.tetrislan.core.GameClock;
import com.echo.tetrislan.core.Piece;

public class BotController {
    private final TetrisView view;

    public BotController(TetrisView view) {
        this.view = view;
    }

    public int botCount() { return view.bots.size(); }
    public int realPlayerCount() {
        int n = 1; // self
        for (PeerInfo pi : view.peerInfos.values()) {
            if (!pi.isBot) n++;
        }
        return n;
    }
    public void hostStartGame() {
        if (!view.canHostStart()) return;
        view.syncStart();
    }
    public void addBot() {
        if (!view.isHost) return;
        if (view.playerCount() >= view.MAX_PLAYERS) return;
        String bname = "BOT_" + view.nextBotId++;
        String bhost = "bot_" + bname;
        long seed = System.currentTimeMillis();
        BotPlayer bot = new BotPlayer(bname, bhost, seed);
        bot.next = bot.randomPiece();
        bot.cur = bot.randomPiece();
        bot.cur.x = (10 - bot.cur.s[0].length) / 2;
        bot.cur.y = 0;
        view.bots.put(bhost, bot);
        PeerInfo pi = new PeerInfo(bname, true);
        pi.name = bname;
        view.peerInfos.put(bhost, pi);
        view.peerNames.put(bhost, bname);
        view.addChat("系统: " + bname + " 加入游戲");
    }
    public void removeBot() {
        if (!view.isHost || view.bots.isEmpty()) return;
        String key = view.bots.keySet().iterator().next();
        BotPlayer bot = view.bots.remove(key);
        view.peerInfos.remove(key);
        view.peerNames.remove(key);
        view.readyPeers.remove(key);
        view.addChat("系统: " + (bot != null ? bot.name : "电脑") + " 离开游戲");
    }
    public void clearBots() {
        view.bots.clear();
    }
    public void tickBots() {
        if (view.solo || view.bots.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (BotPlayer bot : view.bots.values()) {
            if (bot.over) continue;
            if (now < bot.thinkUntil) continue;
            // Dynamic difficulty: scale with real players' performance
            int avgLevel = bot.level;
            int realCount = 0;
            int totalLevel = bot.level;
            float realEfficiency = 0; // lines per minute
            long elapsedMin = Math.max(1, GameClock.elapsed(now, view.modeStartAt, view.pausedTotalMs, view.paused, view.pauseStartedAt) / 60000);
            for (PeerInfo pi : view.peerInfos.values()) {
                if (!pi.isBot) {
                    totalLevel += pi.level;
                    realEfficiency += (float)pi.lines / elapsedMin;
                    realCount++;
                }
            }
            if (realCount > 0) {
                avgLevel = totalLevel / (realCount + 1);
                realEfficiency /= realCount;
            }
            float botEfficiency = (float)bot.lines / elapsedMin;
            // Keep bot within 80%-120% of real player efficiency
            int speedAdj = 0;
            if (botEfficiency < realEfficiency * 0.8f) speedAdj = 1;
            else if (botEfficiency > realEfficiency * 1.2f) speedAdj = -1;
            bot.iq = Math.min(10, Math.max(3, avgLevel + 2));
            bot.actionSpeed = Math.min(10, Math.max(2, avgLevel + speedAdj));
            // 放慢bot下落速度：基础延迟 + 等级因子减弱 + actionSpeed影响减小
            bot.dropDelay = Math.max(1050, Math.min(3600, 4200 - bot.level * 15 - bot.actionSpeed * 7));

            // Bot AI tick
            botTick(bot);
        }
    }
    public void botTick(BotPlayer bot) {
        long now = System.currentTimeMillis();
        if (bot.lastTick == 0) bot.lastTick = now;
        long elapsed = now - bot.lastTick;
        bot.lastTick = now;

        // Handle pending garbage
        if (bot.pendingGarbage > 0 && now >= bot.garbageDueAt) {
            int rows = bot.pendingGarbage;
            bot.pendingGarbage = 0;
            for (int r = 0; r < rows; r++) {
                for (int y = 0; y < 19; y++) {
                    System.arraycopy(bot.board[y + 1], 0, bot.board[y], 0, 10);
                }
                int hole = bot.rnd.nextInt(10);
                for (int x = 0; x < 10; x++) bot.board[19][x] = (x == hole) ? 0 : 7;
            }
        }

        // Simple gravity
        if (bot.cur != null) {
            if (elapsed >= bot.dropDelay) {
                if (botCanMove(bot, 0, 1)) {
                    bot.cur.y++;
                } else {
                    botLock(bot);
                }
            }
        }

        // AI decision making
        if (bot.cur != null && bot.thinkUntil <= now) {
            BotPlayer.BotDecision bestCur = evaluateBest(bot);
            BotPlayer.BotDecision bestHold = null;
            if (bot.hold != 0 && bot.canHold && bestCur != null) {
                int savedType = bot.cur.type;
                bot.cur = new Piece(bot.hold);
                bot.cur.x = (10 - bot.cur.s[0].length) / 2;
                bot.cur.y = 0;
                bot.cur.rot = 0;
                int savedHold = bot.hold;
                bot.hold = savedType;
                bestHold = evaluateBest(bot);
                int t = bot.cur.type;
                bot.cur = new Piece(savedHold);
                bot.cur.x = (10 - bot.cur.s[0].length) / 2;
                bot.cur.y = 0;
                bot.cur.rot = 0;
                bot.hold = t;
            }
            BotPlayer.BotDecision best = bestCur;
            if (bestHold != null && bestHold.score > bestCur.score) {
                int t = bot.cur.type;
                bot.cur = new Piece(bot.hold);
                bot.cur.x = (10 - bot.cur.s[0].length) / 2;
                bot.cur.y = 0;
                bot.cur.rot = 0;
                bot.hold = t;
                bot.canHold = false;
                best = bestHold;
            }
            if (best != null) {
                int savedType = bot.cur.type;
                bot.cur = new Piece(savedType);
                bot.cur.x = (10 - bot.cur.s[0].length) / 2;
                bot.cur.y = 0;
                bot.cur.rot = 0;
                int moves = 0;
                while (bot.cur.rot != best.rot && moves < 4) {
                    if (!botRotateSRS(bot, true)) break;
                    moves++;
                }
                while (bot.cur.x < best.x && moves < 20) {
                    if (botCanMove(bot, 1, 0)) bot.cur.x++;
                    else break;
                    moves++;
                }
                while (bot.cur.x > best.x && moves < 20) {
                    if (botCanMove(bot, -1, 0)) bot.cur.x--;
                    else break;
                    moves++;
                }
                if (bot.cur.rot == best.rot && bot.cur.x == best.x && best.hardDrop) {
                    while (botCanMove(bot, 0, 1)) bot.cur.y++;
                    botLock(bot);
                }
            }
            bot.thinkUntil = now + Math.max(600, 2700 - bot.actionSpeed * 13);
        }
    }
    public BotPlayer.BotDecision evaluateBest(BotPlayer bot) {
        if (bot.cur == null) return null;
        BotPlayer.BotDecision best = null;
        double bestScore = -1e9;
        int originalX = bot.cur.x;
        int originalY = bot.cur.y;
        int originalRot = bot.cur.rot;
        int[][] originalS = bot.cur.s;

        for (int targetRot = 0; targetRot < 4; targetRot++) {
            bot.cur.x = originalX;
            bot.cur.y = originalY;
            bot.cur.rot = originalRot;
            bot.cur.s = originalS;
            int diff = (targetRot - originalRot + 4) % 4;
            boolean rotOk = true;
            for (int i = 0; i < diff; i++) {
                if (!botRotateSRS(bot, true)) { rotOk = false; break; }
            }
            if (!rotOk) continue;
            int minX = -3, maxX = 10;
            for (int tx = minX; tx <= maxX; tx++) {
                bot.cur.x = tx;
                bot.cur.y = originalY;
                if (!botCanMove(bot, 0, 0)) continue;
                while (botCanMove(bot, 0, 1)) bot.cur.y++;
                double s = evaluateBoard(bot);
                if (s > bestScore) {
                    bestScore = s;
                    best = new BotPlayer.BotDecision(tx, bot.cur.rot, true);
                    best.score = s;
                }
            }
        }

        bot.cur.x = originalX;
        bot.cur.y = originalY;
        bot.cur.rot = originalRot;
        bot.cur.s = originalS;
        return best;
    }
    public double evaluateBoard(BotPlayer bot) {
        int[][] sim = new int[20][10];
        for (int y = 0; y < 20; y++) System.arraycopy(bot.board[y], 0, sim[y], 0, 10);
        Piece pc = bot.cur;
        int[][] s = pc.s;
        for (int r = 0; r < s.length; r++) {
            for (int c = 0; c < s[r].length; c++) {
                if (s[r][c] != 0) {
                    int yy = pc.y + r;
                    int xx = pc.x + c;
                    if (yy >= 0 && yy < 20 && xx >= 0 && xx < 10) sim[yy][xx] = pc.type;
                }
            }
        }
        int cleared = 0;
        for (int y = 19; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < 10; x++) if (sim[y][x] == 0) { full = false; break; }
            if (full) {
                cleared++;
                for (int yy = y; yy > 0; yy--) System.arraycopy(sim[yy - 1], 0, sim[yy], 0, 10);
                for (int x = 0; x < 10; x++) sim[0][x] = 0;
                y++;
            }
        }
        double score = 0;
        int aggregateHeight = 0;
        int holes = 0;
        int bumpiness = 0;
        int[] heights = new int[10];
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 20; y++) {
                if (sim[y][x] != 0) { heights[x] = 20 - y; break; }
            }
            aggregateHeight += heights[x];
            boolean blockFound = false;
            for (int y = 0; y < 20; y++) {
                if (sim[y][x] != 0) blockFound = true;
                else if (blockFound) holes++;
            }
        }
        for (int x = 0; x < 9; x++) bumpiness += Math.abs(heights[x] - heights[x + 1]);
        int rowTransitions = 0;
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 9; x++) {
                boolean a = sim[y][x] != 0;
                boolean b = sim[y][x+1] != 0;
                if (a != b) rowTransitions++;
            }
        }
        int wellDepth = 0;
        for (int x = 0; x < 10; x++) {
            int depth = 0;
            for (int y = 0; y < 20; y++) {
                if (sim[y][x] != 0) break;
                boolean leftBlocked = (x == 0) || (sim[y][x-1] != 0);
                boolean rightBlocked = (x == 9) || (sim[y][x+1] != 0);
                if (leftBlocked && rightBlocked) depth++;
                else break;
            }
            wellDepth += depth * depth;
        }
        double iqFactor = bot.iq / 5.0;
        score -= aggregateHeight * 0.51 * iqFactor;
        score -= holes * 0.76 * iqFactor;
        score -= bumpiness * 0.18 * iqFactor;
        score -= rowTransitions * 0.15 * iqFactor;
        score += wellDepth * 0.05 * iqFactor;
        score += cleared * cleared * 12 * iqFactor;
        if (cleared >= 4) score += 60 * iqFactor;
        if (pc.type == 3 && pc.rot != 0) score += 25 * iqFactor;
        return score;
    }
    public boolean botCanMove(BotPlayer bot, int dx, int dy) {
        if (bot.cur == null) return false;
        int[][] s = bot.cur.s;
        for (int r = 0; r < s.length; r++) {
            for (int c = 0; c < s[r].length; c++) {
                if (s[r][c] != 0) {
                    int xx = bot.cur.x + c + dx;
                    int yy = bot.cur.y + r + dy;
                    if (xx < 0 || xx >= 10 || yy >= 20) return false;
                    if (yy >= 0 && bot.board[yy][xx] != 0) return false;
                }
            }
        }
        return true;
    }
    public void botRotate(BotPlayer bot, boolean cw) {
        if (bot.cur == null) return;
        int n = bot.cur.s.length;
        int[][] a = new int[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (cw) a[c][n - 1 - r] = bot.cur.s[r][c];
                else a[n - 1 - c][r] = bot.cur.s[r][c];
            }
        }
        if (botOk(bot, 0, 0, a)) {
            bot.cur.s = a;
            bot.cur.rot = (bot.cur.rot + (cw ? 1 : 3)) % 4;
        }
    }
    public boolean botRotateSRS(BotPlayer bot, boolean cw) {
        if (bot.cur == null) return false;
        int[][] ns = view.rot(bot.cur.s, cw);
        int newRot = (bot.cur.rot + (cw ? 1 : 3)) % 4;
        int idx = cw ? bot.cur.rot * 2 : ((bot.cur.rot + 3) % 4) * 2 + 1;
        int[][][] table = (bot.cur.type == 1) ? view.SRS_I : view.SRS_JLSTZ;
        for (int[] k : table[idx]) {
            if (botOk(bot, k[0], k[1], ns)) {
                bot.cur.s = ns;
                bot.cur.x += k[0];
                bot.cur.y += k[1];
                bot.cur.rot = newRot;
                bot.cur.spin = (bot.cur.type == 3 && (k[0] != 0 || k[1] != 0));
                bot.cur.mini = false;
                return true;
            }
        }
        return false;
    }
    public boolean botOk(BotPlayer bot, int dx, int dy, int[][] s) {
        for (int r = 0; r < s.length; r++) {
            for (int c = 0; c < s[r].length; c++) {
                if (s[r][c] != 0) {
                    int xx = bot.cur.x + c + dx;
                    int yy = bot.cur.y + r + dy;
                    if (xx < 0 || xx >= 10 || yy >= 20) return false;
                    if (yy >= 0 && bot.board[yy][xx] != 0) return false;
                }
            }
        }
        return true;
    }
    public int botTspin(BotPlayer bot) {
        if (bot.cur == null || bot.cur.type != 3 || !bot.cur.spin) return 0;
        int cx = bot.cur.x + 1, cy = bot.cur.y + 1;
        int n = 0;
        int[][] pts = {{cx-1,cy-1},{cx+1,cy-1},{cx-1,cy+1},{cx+1,cy+1}};
        for (int[] q : pts) {
            int x = q[0], y = q[1];
            if (x < 0 || x >= 10 || y >= 20 || (y >= 0 && bot.board[y][x] != 0)) n++;
        }
        if (n < 3) return 0;
        return n == 4 ? 2 : 1;
    }
    public int botGarbageFor(BotPlayer bot, int n, int spinType) {
        return view.garbageFor(n, spinType, bot.b2b, bot.combo, bot.badges);
    }
    public int botDoClear(BotPlayer bot) {
        int n = 0;
        for (int y = 19; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < 10; x++) if (bot.board[y][x] == 0) { full = false; break; }
            if (full) {
                n++;
                for (int yy = y; yy > 0; yy--) System.arraycopy(bot.board[yy - 1], 0, bot.board[yy], 0, 10);
                for (int x = 0; x < 10; x++) bot.board[0][x] = 0;
                y++;
            }
        }
        return n;
    }
    public void botLock(BotPlayer bot) {
        if (bot.cur == null) return;
        int[][] s = bot.cur.s;
        boolean lockOut = false;
        for (int r = 0; r < s.length; r++) {
            for (int c = 0; c < s[r].length; c++) {
                if (s[r][c] != 0) {
                    int yy = bot.cur.y + r;
                    int xx = bot.cur.x + c;
                    if (yy < 0) lockOut = true;
                    else if (yy < 20 && xx >= 0 && xx < 10) bot.board[yy][xx] = bot.cur.type;
                }
            }
        }
        if (lockOut) {
            bot.over = true;
            PeerInfo pi = view.peerInfos.get(bot.hostKey);
            if (pi != null) pi.over = true;
            if (!bot.lastAttacker.isEmpty()) view.notifyKO(bot.name, bot.lastAttacker);
            bot.cur = null;
            return;
        }
        int spinType = botTspin(bot);
        int cleared = botDoClear(bot);
        if (cleared > 0) {
            bot.combo++;
            boolean difficult = cleared == 4 || spinType >= 1;
            if (difficult) bot.b2b++;
            else bot.b2b = 0;
            int base = new int[]{0, 100, 300, 500, 800}[cleared];
            if (spinType == 2) base = cleared == 1 ? 800 : cleared == 2 ? 1200 : 1600;
            else if (spinType == 1) base = cleared == 1 ? 200 : cleared == 2 ? 400 : 600;
            int bonus = bot.combo > 0 ? bot.combo * 50 : 0;
            bot.score += (base + bonus) * bot.level;
            bot.lines += cleared;
            bot.level = 1 + bot.lines / 10;
            int garbage = botGarbageFor(bot, cleared, spinType);
            if (garbage > 0 && bot.pendingGarbage > 0) {
                int cancel = Math.min(garbage, bot.pendingGarbage);
                bot.pendingGarbage -= cancel; garbage -= cancel;
            }
            if (view.p2p != null && garbage > 0) {
                view.p2p.sendGarbage(garbage);
                if (!view.over) {
                    view.pendingGarbage += garbage;
                    view.lastAttacker = bot.name;
                    view.garbageDueAt = System.currentTimeMillis() + 1800;
                    view.p2pStatus = bot.name + " 送了 " + garbage + " 行";
                    view.tone(view.sGarbage);
                    view.fx("WARNING +" + garbage, true);
                }
            }
        } else {
            bot.combo = -1;
        }
        bot.canHold = true;
        bot.cur = bot.next;
        bot.next = bot.randomPiece();
        if (bot.cur != null) {
            bot.cur.x = (10 - bot.cur.s[0].length) / 2;
            bot.cur.y = 0;
            bot.cur.rot = 0;
            if (!botCanMove(bot, 0, 0)) {
                bot.over = true;
                PeerInfo pi = view.peerInfos.get(bot.hostKey);
                if (pi != null) pi.over = true;
                if (!bot.lastAttacker.isEmpty()) view.notifyKO(bot.name, bot.lastAttacker);
            }
        }
        // Sync bot board to view.peerInfos for local preview
        PeerInfo pi = view.peerInfos.get(bot.hostKey);
        if (pi != null) {
            int[][] copy = new int[20][10];
            for (int y = 0; y < 20; y++) System.arraycopy(bot.board[y], 0, copy[y], 0, 10);
            pi.board = copy;
        }
    }
    public void sendBotStates() {
        if (view.p2p == null) return;
        for (BotPlayer bot : view.bots.values()) {
            if (bot.over) continue;
            view.p2p.publishBotState(bot.name, bot.score, bot.lines, bot.level, bot.over, bot.kos, bot.badges, view.encodeBoardStatic(bot.board));
        }
    }

}
