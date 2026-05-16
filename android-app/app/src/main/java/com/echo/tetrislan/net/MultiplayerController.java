package com.echo.tetrislan.net;

import com.echo.tetrislan.TetrisView;
import com.echo.tetrislan.ai.BotPlayer;
import com.echo.tetrislan.core.BoardCodec;
import java.util.Iterator;
import java.util.Map;

public class MultiplayerController {
    private final TetrisView view;

    public MultiplayerController(TetrisView view) {
        this.view = view;
    }

    public void sendP2pState() {
        view.p2p.publishState(view.score, view.lines, view.level, view.over, view.kos, view.badges, BoardCodec.encode(view.board, view.R, view.C));
    }
    public void syncStart() {
        view.startP2p();
        long seed = System.currentTimeMillis();
        long at = System.currentTimeMillis() + 3000;
        view.pendingStartSeed = seed;
        view.pendingStartAt = at;
        view.p2p.sendStart(seed, at);
        view.p2pStatus = "3秒后开始";
    }
    public void createRoom() {
        if (view.p2p != null) {
            if (view.isHost) { if (view.p2p != null) view.p2p.sendDisband(); }
            else { if (view.p2p != null) view.p2p.sendLeave(); }
            view.stopP2p();
        }
        view.isHost = true;
        view.roomHost = null;
        view.roomName = "R" + (1000 + view.rnd.nextInt(9000));
        view.p2pDiscovery = false;
        view.restartP2p();
        view.addChat("系统: 已创建房间 " + view.roomName);
    }
    public void askRoom() {
        view.askText("输入房间号", view.roomName, text -> {
            view.roomName = view.clean(text, "TETRIS");
            if (view.p2p != null) { view.p2p.sendLeave(); view.stopP2p(); }
            view.isHost = false;
            view.roomHost = null;
            view.restartP2p();
            view.addChat("系统: 已进入房间 " + view.roomName);
        });
    }
    public void joinRoom(String room, String host) {
        if (view.p2p != null) { view.p2p.sendLeave(); view.stopP2p(); }
        view.isHost = false;
        view.roomName = room;
        view.roomHost = host;
        view.p2pDiscovery = false;
        view.restartP2p();
        view.addChat("系统: 加入发现房间 " + view.roomName);
    }
    public void toggleReady() {
        view.startP2p();
        view.selfReady = !view.selfReady;
        view.p2p.sendReady(view.selfReady);
        view.addChat("我: " + (view.selfReady ? "已准备" : "取消准备"));
        maybeCountdown();
    }
    public void maybeCountdown() {
        // 房主不再自动开局，只是更新状态
    }
    public boolean canHostStart() {
        if (!view.isHost || view.pendingStartAt > 0) return false;
        if (!view.selfReady) return false;
        int pc = playerCount();
        if (pc < 2) return false;
        for (java.util.Map.Entry<String, PeerInfo> e : view.peerInfos.entrySet()) {
            if (e.getValue().isBot) continue;
            if (!Boolean.TRUE.equals(view.readyPeers.get(e.getKey()))) return false;
        }
        return true;
    }
    public int playerCount() {
        int n = 1;
        long now = System.currentTimeMillis();
        for (PeerInfo pi : view.peerInfos.values()) {
            if (!pi.disconnected || (now - pi.disconnectedAt < 30000)) n++;
        }
        return Math.min(view.MAX_PLAYERS, n);
    }
    public int readyCount() { int n = view.selfReady ? 1 : 0; for (Boolean r: view.readyPeers.values()) if (r) n++; return Math.min(view.MAX_PLAYERS, n); }
    public boolean isRoomFullForNewPeer(String host) { return !view.peerNames.containsKey(host) && playerCount() >= view.MAX_PLAYERS; }
    public boolean fromRoomHost(String host) { return view.isHost || view.roomHost == null || view.roomHost.equals(host); }
    public boolean acceptPeer(String host, String name) {
        if (!isRoomFullForNewPeer(host)) return true;
        if (view.isHost && view.p2p != null) view.p2p.sendKick(host, name, "房间已满");
        view.p2pStatus = "房间已满 " + view.MAX_PLAYERS + "/" + view.MAX_PLAYERS;
        return false;
    }
    public void leaveRoom() {
        if (!view.menu && !view.over && view.p2p != null) view.p2p.sendSurrender();
        if (view.p2p != null) view.p2p.sendLeave();
        saveLastRoom();
        view.stopP2p();
        view.isHost = false;
        view.addChat("系统: 已退出房间");
    }
    public void saveLastRoom() {
        if (view.roomName != null) {
            view.sp.edit().putString("last_room_name", view.roomName).putString("last_room_host", view.roomHost).apply();
            view.lastRoomName = view.roomName;
            view.lastRoomHost = view.roomHost;
        }
    }
    public void reconnectLastRoom() {
        if (view.lastRoomName == null) return;
        view.roomName = view.lastRoomName;
        view.roomHost = view.lastRoomHost;
        if (view.p2p != null) { view.p2p.sendLeave(); view.stopP2p(); }
        view.isHost = false;
        view.p2pDiscovery = false;
        view.restartP2p();
        view.reconnectCheckHost = view.lastRoomHost;
        view.reconnectCheckUntil = System.currentTimeMillis() + 5000;
        view.addChat("系统: 尝试重连 " + view.roomName + "，检测中...");
    }
    public void notifyKO(String targetName, String killerName) {
        if (view.p2p != null) view.p2p.sendKO(targetName, killerName);
        for (BotPlayer bot : view.bots.values()) { if (bot.name.equals(killerName)) { bot.kos++; break; } }
        for (PeerInfo pi : view.peerInfos.values()) { if (pi.name.equals(killerName)) { pi.kos++; break; } }
    }
    public void checkMultiFinish() {
        if (view.solo || view.p2p == null) return;
        // 检查是否有真人peer
        boolean hasHumanPeer = false;
        for (PeerInfo pi : view.peerInfos.values()) {
            if (pi.name != null && !pi.isBot) {
                hasHumanPeer = true;
                break;
            }
        }
        // 没有真人玩家时，使用原始逻辑（剩1人或全部结束则结算）
        if (!hasHumanPeer) {
            int alive = view.over ? 0 : 1;
            for (PeerInfo pi : view.peerInfos.values()) {
                if (!pi.over) alive++;
            }
            if (alive <= 1 && view.rankingUntil == 0) {
                if (!view.over) view.finishGame("获胜");
                showRankingAndReturn();
            }
            return;
        }
        // 有真人玩家时，只统计真人存活
        boolean selfIsHuman = true;
        int humanAlive = (selfIsHuman && !view.over) ? 1 : 0;
        for (PeerInfo pi : view.peerInfos.values()) {
            if (pi.name != null && !pi.isBot && !pi.over) {
                humanAlive++;
            }
        }
        if (humanAlive <= 1 && view.rankingUntil == 0) {
            if (humanAlive == 1 && !view.over) {
                view.finishGame("获胜");
            }
            showRankingAndReturn();
        }
    }
    public boolean allPeersOver() {
        if (!view.over) return false;
        for (PeerInfo pi : view.peerInfos.values()) if (!pi.over) return false;
        return true;
    }
    public void cleanupDisconnectedPeers(long now) {
        java.util.Iterator<java.util.Map.Entry<String, PeerInfo>> it = view.peerInfos.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, PeerInfo> e = it.next();
            PeerInfo pi = e.getValue();
            // 清理断线真人（超30秒）
            if (pi.disconnected && now - pi.disconnectedAt > 30000) {
                it.remove();
                view.peerNames.remove(e.getKey());
                view.readyPeers.remove(e.getKey());
                view.addChat("系统: " + pi.name + " 超时未重连，已移除");
                continue;
            }
            // 清理已结束的Bot（无独立网络连接，留在peerInfos仅用于显示）
            if (pi.name != null && pi.isBot && pi.over) {
                String botKey = e.getKey();
                // 确认Bot实例已不存在才清理
                if (!view.bots.containsKey(botKey)) {
                    it.remove();
                    view.peerNames.remove(botKey);
                    view.readyPeers.remove(botKey);
                }
            }
        }
    }
    public void showRankingAndReturn() {
        long now = System.currentTimeMillis();
        java.util.List<PeerInfo> all = new java.util.ArrayList<>();
        PeerInfo self = new PeerInfo(view.playerName);
        self.score = view.score; self.lines = view.lines; self.kos = view.kos; self.badges = view.badges; self.over = view.over;
        all.add(self);
        for (PeerInfo pi : view.peerInfos.values()) all.add(pi);
        all.sort((a,b) -> {
            if (a.over != b.over) return a.over ? 1 : -1;
            if (b.score != a.score) return b.score - a.score;
            if (b.kos != a.kos) return b.kos - a.kos;
            if (b.badges != a.badges) return b.badges - a.badges;
            return b.lines - a.lines;
        });
        view.rankingLines.clear();
        view.rankingLines.add("对局结束 - 排名");
        for (int i=0;i<all.size();i++) {
            PeerInfo pi = all.get(i);
            String status = pi.over ? "[KO]" : "";
            view.rankingLines.add("#" + (i+1) + " " + pi.name + " " + status + "  " + pi.score + "分  " + pi.lines + "行  K" + pi.kos + " B" + pi.badges);
        }
        view.finishText = "对局结束";
        view.rankingUntil = now + 5000;
    }
    public void returnToRoom() {
        view.menu = true; view.menuPage = 2; view.over = false; view.paused = false; view.settings = false;
        view.finishText = ""; view.rankingUntil = 0; view.rankingLines.clear();
        view.selfReady = false; view.readyPeers.clear(); view.peerInfos.clear();
        view.score = 0; view.lines = 0; view.level = 1; view.dropMs = 1000; view.pendingGarbage = 0;
        view.combo = -1; view.b2b = 0; view.badges = 0; view.kos = 0;
        view.gameMode = view.MODE_CLASSIC; view.invisible = false; view.soloStage = 1;
        view.trainTech = 0; view.trainSuccess = 0; view.trainDemoPiece = null; view.trainDemoStartPiece = null;
        view.clearBots();
        if (view.p2p != null) view.p2p.publishState(0, 0, 1, false, 0, 0, BoardCodec.encode(view.board, view.R, view.C));
        if (view.isHost && view.p2p != null) view.p2p.sendReturnLobby();
    }

}
