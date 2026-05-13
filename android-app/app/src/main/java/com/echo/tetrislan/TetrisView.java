package com.echo.tetrislan;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.text.InputType;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TetrisView extends View implements Runnable {
    private int C = 10, R = 20;
    private static final int MAX_PLAYERS = 3;
    private static final int MODE_CLASSIC = 0, MODE_SPRINT = 1, MODE_ULTRA = 2, MODE_MARATHON = 3, MODE_INVISIBLE = 4, MODE_DIG = 5, MODE_SURVIVAL = 6;
    private static final String APP_VERSION = "v1.9.0";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final SharedPreferences sp;
    private final List<Btn> btns = new ArrayList<>();
    private final List<FxParticle> particles = new ArrayList<>();
    private Thread loop;
    private P2pTransport p2p;
    private String p2pStatus = "P2P未启动";
    private String roomName = "TETRIS";
    private final String roomPass = "1234";
    private String playerName;
    private String playerId;
    private String lastRoomName = null;
    private String lastRoomHost = null;
    private String roomHost = null;
    private static class DiscoveredRoom {
        String room, host, name;
        long lastSeenMs;
        DiscoveredRoom(String r, String h, String n) { room=r; host=h; name=n; lastSeenMs=System.currentTimeMillis(); }
    }
    private final List<DiscoveredRoom> foundRooms = new ArrayList<>();
    private boolean p2pDiscovery = false;
    private boolean selfReady = false;
    private final Map<String, Boolean> readyPeers = new HashMap<>();
    private final Map<String, String> peerNames = new java.util.LinkedHashMap<>();
    private final Map<String, PeerInfo> peerInfos = new java.util.LinkedHashMap<>();
    private String reconnectCheckHost = null;
    private long reconnectCheckUntil = 0;
    private long pendingStartAt = 0, pendingStartSeed = 0;
    private final List<String> chat = new ArrayList<>();
    private int pendingGarbage = 0;
    private int combo = -1, b2b = 0, badges = 0, kos = 0;
    private long garbageDueAt = 0;
    private String fxText = "";
    private long fxUntil = 0, shakeUntil = 0, flashUntil = 0;
    private int soloStage = 1;
    private long rankingUntil = 0;
    private RectF soloOverRestartBtn = null;
    private RectF soloOverRetryBtn = null;
    private long lastAnyPeerUpdate = 0;
    private boolean networkFrozen = false;
    private ToneGenerator toneGen;
    private int sMove, sRotate, sDrop, sClear, sTetris, sGarbage, sReady;
    private long lastP2pSend = 0;
    private boolean running = true, menu = true, over = true, paused = false, settings = false, confirmQuit = false;
    private boolean solo = true, canHold = true;
    private int gameMode = MODE_CLASSIC;
    private long modeStartAt = 0, pauseStartedAt = 0, pausedTotalMs = 0;
    private String finishText = "";
    private boolean invisible = false;
    private int digTargetLines = 0;
    private int menuPage = 0; // 0 main, 1 solo actions, 2 multiplayer actions
    private int[][] board = new int[R][C];
    private Piece cur, next;
    private int hold = 0, score = 0, lines = 0, level = 1;
    private long lastDrop = 0, dropMs = 1000, lastSave = 0;
    private long dasMs = 167, arrMs = 33, softMs = 120;
    private static final long HARD_COOLDOWN_MS = 350;
    private static final long LOCK_DELAY_MS = 500, ARE_MS = 400;
    private static final int MAX_LOCK_RESETS = 15;
    private boolean leftHeld = false, rightHeld = false, softHeld = false, hardReady = true;
    private long leftStart = 0, rightStart = 0, arrAt = 0, softStart = 0, softAt = 0, lastHard = 0;
    private int pendingIRS = 0; // 0=none, 1=cw, -1=ccw
    private boolean pendingIHS = false;
    private long lockUntil = 0;
    private int lockResets = 0;
    private boolean onGround = false;
    private long areUntil = 0;
    private boolean clearing = false;
    private int activeAction = -1;
    private final Map<Integer, Integer> pointerActions = new HashMap<>();
    private int[] bag = new int[7];
    private int bagIndex = 7;
    private boolean isHost = false;
    private BroadcastReceiver batteryReceiver;
    private int batteryPct = -1;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private float bx, by, cell, bw, bh, topBtnY;

    private static final int[][][] SHAPES = {
        {},
        {{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},
        {{1,1},{1,1}},
        {{0,1,0},{1,1,1},{0,0,0}},
        {{0,1,1},{1,1,0},{0,0,0}},
        {{1,1,0},{0,1,1},{0,0,0}},
        {{1,0,0},{1,1,1},{0,0,0}},
        {{0,0,1},{1,1,1},{0,0,0}}
    };
    private static final int[] COLORS = {0,0xff00e5ff,0xffffeb3b,0xffe040fb,0xff69f0ae,0xffff5252,0xff448aff,0xffffab40};

    private static class Theme {
        String name;
        int bg, board, boardStroke, blockFlash, btn, btnOn, btnPause, btnTop, text, textMuted, score, fxText;
        int[] colors;
        Theme(String n, int bg, int bo, int bs, int bf, int btn, int boN, int bp, int bt, int tx, int tm, int sc, int fx, int[] co) {
            this.name=n; this.bg=bg; this.board=bo; this.boardStroke=bs; this.blockFlash=bf;
            this.btn=btn; this.btnOn=boN; this.btnPause=bp; this.btnTop=bt;
            this.text=tx; this.textMuted=tm; this.score=sc; this.fxText=fx; this.colors=co;
        }
    }
    private static final Theme[] THEMES = {
        new Theme("霓虹", 0xff080810, 0xff121220, 0xff2a2a40, 0xffffffff,
            0xcc1e1e30, 0xff00e5ff, 0xffffab40, 0xff252542,
            Color.WHITE, 0xffaaaaaa, 0xff00e5ff, 0xffffeb3b,
            new int[]{0,0xff00e5ff,0xffffeb3b,0xffe040fb,0xff69f0ae,0xffff5252,0xff448aff,0xffffab40})
    };
    private Theme theme() { return THEMES[0]; }

    // SRS kick tables: index = oldRot*2 + (cw?0:1)  but mapping:
    // cw: 0->R(0), R->2(2), 2->L(4), L->0(6)
    // ccw: R->0(1), 2->R(3), L->2(5), 0->L(7)
    private static final int[][][] SRS_JLSTZ = {
        {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}},
        {{0,0},{1,0},{1,-1},{0,2},{1,2}},
        {{0,0},{1,0},{1,-1},{0,2},{1,2}},
        {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}},
        {{0,0},{1,0},{1,1},{0,-2},{1,-2}},
        {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}},
        {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}},
        {{0,0},{1,0},{1,1},{0,-2},{1,-2}}
    };
    private static final int[][][] SRS_I = {
        {{0,0},{-2,0},{1,0},{-2,-1},{1,2}},
        {{0,0},{2,0},{-1,0},{2,1},{-1,-2}},
        {{0,0},{-1,0},{2,0},{-1,2},{2,-1}},
        {{0,0},{1,0},{-2,0},{1,-2},{-2,1}},
        {{0,0},{2,0},{-1,0},{2,1},{-1,-2}},
        {{0,0},{-2,0},{1,0},{-2,-1},{1,2}},
        {{0,0},{1,0},{-2,0},{1,-2},{-2,1}},
        {{0,0},{-1,0},{2,0},{-1,2},{2,-1}}
    };

    public TetrisView(Context c) {
        super(c);
        sp = c.getSharedPreferences("tetris_native", Context.MODE_PRIVATE);
        playerId = sp.getString("player_id", null);
        if (playerId == null) {
            playerId = "pid" + System.currentTimeMillis() + rnd.nextInt(10000);
            sp.edit().putString("player_id", playerId).apply();
        }
        playerName = sp.getString("player_name", null);
        if (playerName == null) {
            playerName = "P" + (100 + rnd.nextInt(900));
            sp.edit().putString("player_name", playerName).apply();
        }
        lastRoomName = sp.getString("last_room_name", null);
        lastRoomHost = sp.getString("last_room_host", null);
        dasMs = sp.getLong("das_ms", 167);
        arrMs = sp.getLong("arr_ms", 33);
        softMs = sp.getLong("soft_ms", 120);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        initSound();
        setFocusable(true);
        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                int level = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                batteryPct = level >= 0 && scale > 0 ? (int)(level * 100f / scale) : -1;
            }
        };
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loop = new Thread(this, "tetris-loop");
        loop.start();
        if (batteryReceiver != null) {
            getContext().registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        if (p2p != null) p2p.stop();
        if (toneGen != null) toneGen.release();
        if (batteryReceiver != null) {
            try { getContext().unregisterReceiver(batteryReceiver); } catch (Exception e) {}
        }
        super.onDetachedFromWindow();
    }

    @Override public void run() {
        while (running) {
            long now = System.currentTimeMillis();
            if (pendingStartAt > 0 && now >= pendingStartAt) {
                long seed = pendingStartSeed;
                pendingStartAt = 0;
                pendingStartSeed = 0;
                start(seed);
            }
            if (!menu && !over && !paused && !settings) {
                if (gameMode == MODE_ULTRA && modeElapsedMs(now) >= 120000) {
                    if (score < soloStage * 5000) finishGame("时间到 未达标");
                }
                if (gameMode == MODE_DIG && modeElapsedMs(now) >= 180000) {
                    if (lines < 10 * soloStage) finishGame("时间到 未达标");
                }
                if (over) {
                    // Timed modes can finish between piece locks.
                } else if (areUntil > 0) {
                    if (now >= areUntil) {
                        areUntil = 0; clearing = false;
                        applyGarbage();
                        spawn();
                    }
                    input(now);
                } else if (cur != null) {
                    boolean grounded = !ok(cur, 0, 1, cur.s);
                    if (grounded) {
                        if (!onGround) {
                            lockUntil = now + LOCK_DELAY_MS;
                            lockResets = 0;
                            onGround = true;
                        } else if (now >= lockUntil) {
                            lock();
                            onGround = false;
                        }
                    } else {
                        onGround = false;
                        if (now - lastDrop > dropMs) {
                            if (!move(0, 1)) lock();
                            lastDrop = now;
                        }
                    }
                    input(now);
                }
                if (solo && now - lastSave > 5000) {
                    save(false);
                    lastSave = now;
                }
                if (!solo && p2p != null && now - lastP2pSend > 500) {
                    sendP2pState();
                    sendBotStates();
                    lastP2pSend = now;
                }
                if (!solo) {
                    tickBots();
                    cleanupDisconnectedPeers(now);
                    if (!menu && !over && lastAnyPeerUpdate > 0 && !peerNames.isEmpty() && now - lastAnyPeerUpdate > 10000) {
                        if (!networkFrozen) {
                            networkFrozen = true;
                            paused = true;
                            addChat("系统: 检测到断线，游戏已暂停");
                        }
                    }
                    if (reconnectCheckHost != null && now > reconnectCheckUntil) {
                        reconnectCheckHost = null; reconnectCheckUntil = 0;
                        addChat("系统: 目标房间已不在线");
                        stopP2p(); menuPage = 0;
                    }
                }
            }
            if (!menu && !solo && p2p != null && rankingUntil == 0) {
                checkMultiFinish();
            }
            if (rankingUntil > 0 && now >= rankingUntil) {
                rankingUntil = 0;
                returnToRoom();
            }
            postInvalidate();
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(theme().bg);
        c.drawRect(0, 0, w, h, p);
        long now = System.currentTimeMillis();
        if (now < shakeUntil) c.translate((rnd.nextFloat()-.5f)*10f, (rnd.nextFloat()-.5f)*10f);
        if (menu) { drawMenu(c, w, h); return; }
        layoutGame(w, h);
        drawTop(c);
        drawBoard(c);
        drawSide(c);
        drawParticles(c);
        drawBtns(c);
        drawFxOverlay(c, w, h);
        if (over) {
            drawCenter(c, finishText.isEmpty() ? "游戏结束" : finishText, "点设置或主界面");
            if (solo && !menu && gameMode != MODE_CLASSIC) {
                float btnW = Math.max(108, w * 0.31f), bhBtn = btnW * 0.56f;
                float cy = h/2f + 130;
                float gap = 16;
                float totalW = btnW * 2 + gap;
                float lx = w/2f - totalW/2;
                float rx = w/2f + gap/2;
                soloOverRestartBtn = new RectF(lx, cy - bhBtn/2, lx + btnW, cy + bhBtn/2);
                soloOverRetryBtn = new RectF(rx, cy - bhBtn/2, rx + btnW, cy + bhBtn/2);
                p.setColor(theme().btn); p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(soloOverRestartBtn, 16, 16, p);
                c.drawRoundRect(soloOverRetryBtn, 16, 16, p);
                p.setColor(theme().text); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(32);
                c.drawText("从头再来", soloOverRestartBtn.centerX(), soloOverRestartBtn.centerY() + 12, p);
                c.drawText("本关重试", soloOverRetryBtn.centerX(), soloOverRetryBtn.centerY() + 12, p);
            } else {
                soloOverRestartBtn = null;
                soloOverRetryBtn = null;
            }
        }
        if (paused && !over) drawCenter(c, "暂停", "点暂停继续");
        if (settings) drawSettings(c, w, h);
    }

    private void drawMenu(Canvas c, int w, int h) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(theme().text); p.setTextSize(56); c.drawText("俄罗斯方块 " + APP_VERSION, w/2f, h*0.16f, p);
        if (menuPage == 0) {
            drawMenuButton(c, "单人模式", w*0.14f, h*0.30f, w*0.86f, h*0.39f, false);
            drawMenuButton(c, "多人模式", w*0.14f, h*0.43f, w*0.86f, h*0.52f, false);
            p.setColor(theme().textMuted); p.setTextSize(28);
            c.drawText("先选模式，再开局/读取/保存", w/2f, h*0.60f, p);
            return;
        }
        boolean multi = menuPage == 2;
        if (multi && p2p == null) {
            startP2p();
            p2pDiscovery = true;
            if ("P2P未启动".equals(p2pStatus)) p2pStatus = "搜索房间中...";
        }
        p.setColor(theme().score); p.setTextSize(36); c.drawText(multi ? "多人大厅" : "单人模式", w/2f, h*0.20f, p);
        if (!multi) {
            float btnH = h * 0.058f, gap = h * 0.010f, sy = h * 0.28f;
            drawMenuButton(c, "经典模式",   w*0.08f, sy,               w*0.46f, sy+btnH, false);
            drawMenuButton(c, "冲刺40行",   w*0.54f, sy,               w*0.92f, sy+btnH, false);
            drawMenuButton(c, "限时得分",   w*0.08f, sy+btnH+gap,      w*0.46f, sy+2*btnH+gap, false);
            drawMenuButton(c, "马拉松",     w*0.54f, sy+btnH+gap,      w*0.92f, sy+2*btnH+gap, false);
            drawMenuButton(c, "隐形模式",   w*0.08f, sy+2*(btnH+gap),  w*0.46f, sy+3*btnH+2*gap, false);
            drawMenuButton(c, "挖掘挑战",   w*0.54f, sy+2*(btnH+gap),  w*0.92f, sy+3*btnH+2*gap, false);
            drawMenuButton(c, "无尽生存",   w*0.08f, sy+3*(btnH+gap),  w*0.46f, sy+4*btnH+3*gap, false);
            float by = sy + 4*(btnH+gap) + gap*2;
            drawMenuButton(c, "返回主菜单", w*0.14f, by,               w*0.86f, by+btnH, false);
            p.setColor(theme().textMuted); p.setTextSize(20);
            float descY = by + btnH + h*0.025f;
            c.drawText("经典:传统玩法可存档  冲刺:竞速40行  限时:2分钟得分  马拉松:150行通关", w/2f, descY, p);
            c.drawText("隐形:落底后隐形  挖掘:清除垃圾行  生存:速度无限提升", w/2f, descY + h*0.028f, p);
            return;
        }
        if (p2p == null || p2pDiscovery) {
            drawMenuButton(c, "创建房间", w*0.08f, h*0.31f, w*0.46f, h*0.39f, false);
            drawMenuButton(c, "输入房号", w*0.54f, h*0.31f, w*0.92f, h*0.39f, false);
            if (lastRoomName != null) {
                drawMenuButton(c, "重连上一局", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false);
                drawMenuButton(c, "修改名称", w*0.54f, h*0.42f, w*0.92f, h*0.50f, false);
            } else {
                drawMenuButton(c, "修改名称", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false);
            }
            drawMenuButton(c, "返回主菜单", w*0.15f, h*0.53f, w*0.85f, h*0.61f, false);
        } else {
            if (isHost) {
                boolean canStart = canHostStart();
                drawMenuButton(c, canStart ? "开始游戏" : "等待准备", w*0.08f, h*0.31f, w*0.46f, h*0.39f, canStart);
            } else {
                drawMenuButton(c, selfReady ? "取消准备" : "准备", w*0.08f, h*0.31f, w*0.46f, h*0.39f, selfReady);
            }
            drawMenuButton(c, "聊天", w*0.54f, h*0.31f, w*0.92f, h*0.39f, false);
            drawMenuButton(c, "修改名称", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false);
            drawMenuButton(c, "退出房间", w*0.54f, h*0.42f, w*0.92f, h*0.50f, false);
            if (isHost) {
                int bc = botCount();
                if (playerCount() < MAX_PLAYERS) {
                    drawMenuButton(c, "+电脑", w*0.08f, h*0.53f, w*0.46f, h*0.61f, false);
                }
                if (bc > 0) {
                    drawMenuButton(c, "-电脑", w*0.54f, h*0.53f, w*0.92f, h*0.61f, false);
                } else if (!peerNames.isEmpty()) {
                    drawMenuButton(c, "踢人", w*0.54f, h*0.53f, w*0.92f, h*0.61f, false);
                }
            }
        }
        p.setColor(theme().text); p.setTextSize(28);
        c.drawText("房间 " + roomName + "  准备 " + readyCount() + "/" + playerCount() + "  2-" + MAX_PLAYERS + "人", w/2f, h*0.68f, p);
        if (p2p != null && !p2pDiscovery) {
            p.setColor(theme().score); p.setTextSize(28);
            c.drawText("玩家: " + playerName + (isHost ? "[房主]" : ""), w/2f, h*0.72f, p);
            int py = 0;
            for (java.util.Map.Entry<String, String> e : peerNames.entrySet()) {
                String name = e.getValue();
                boolean isBot = name.startsWith("BOT_");
                String tag = isBot ? " [电脑]" : (readyPeers.getOrDefault(e.getKey(), false) ? " [已准备]" : "");
                c.drawText(name + tag, w/2f, h*(0.755f + 0.035f*py), p);
                py++;
            }
        } else {
            p.setColor(theme().textMuted); p.setTextSize(24);
            c.drawText("玩家: " + playerName, w/2f, h*0.56f, p);
            int nf = foundRooms.size();
            p.setColor(theme().textMuted); p.setTextSize(22);
            if (nf == 0) {
                c.drawText("正在搜索附近的房间...", w/2f, h*0.60f, p);
            } else {
                c.drawText("—— 附近的房间 ——", w/2f, h*0.59f, p);
                float cardY = h*0.612f, cardH = h*0.058f, cardGap = h*0.008f, cardL = w*0.06f, cardR = w*0.94f;
                p.setTextAlign(Paint.Align.LEFT);
                int limit = Math.min(nf, 4);
                for (int i = 0; i < limit; i++) {
                    DiscoveredRoom dr = foundRooms.get(i);
                    float cy = cardY + i * (cardH + cardGap);
                    p.setColor(theme().btn); p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(new RectF(cardL, cy, cardR, cy+cardH), 12, 12, p);
                    p.setColor(theme().boardStroke); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1);
                    c.drawRoundRect(new RectF(cardL+1, cy+1, cardR-1, cy+cardH-1), 11, 11, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(theme().score);
                    c.drawRoundRect(new RectF(cardL, cy, cardL+4, cy+cardH), 2, 2, p);
                    p.setColor(theme().text); p.setTextSize(24);
                    c.drawText(dr.room, cardL+14, cy+16, p);
                    p.setColor(theme().textMuted); p.setTextSize(18);
                    c.drawText("房主: " + dr.name, cardL+14, cy+cardH-8, p);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setColor(theme().score); p.setTextSize(26);
                    c.drawText("→", cardR-28, cy+cardH/2+9, p);
                    p.setTextAlign(Paint.Align.LEFT);
                }
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(theme().textMuted); p.setTextSize(18);
                c.drawText("发现 " + nf + " 个房间 · 点击房间加入", w/2f, cardY + limit*(cardH+cardGap) + h*0.010f, p);
            }
        }
        p.setColor(theme().textMuted); p.setTextSize(24);
        c.drawText(p2pStatus, w/2f, h*0.84f, p);
        int start = Math.max(0, chat.size() - 4);
        for (int i=start;i<chat.size();i++) c.drawText(chat.get(i), w/2f, h*(0.875f + 0.035f*(i-start)), p);
        if (pendingStartAt > 0) {
            long left = Math.max(0, (pendingStartAt - System.currentTimeMillis() + 999) / 1000);
            p.setColor(theme().score); p.setTextSize(42); c.drawText("倒计时 " + left, w/2f, h*0.96f, p);
        }
    }

    private void drawMenuButton(Canvas c, String text, float l, float t, float r, float b, boolean on) {
        p.setStyle(Paint.Style.FILL); p.setColor(on ? theme().btnOn : theme().btn);
        c.drawRoundRect(new RectF(l,t,r,b), 28, 28, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(on ? theme().btnOn : theme().boardStroke);
        c.drawRoundRect(new RectF(l+2,t+2,r-2,b-2), 26, 26, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? Color.BLACK : theme().text); p.setTextSize(38); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, (l+r)/2, (t+b)/2 + 13, p);
    }

    private void layoutGame(int w, int h) {
        float top = 120, bottomControls = h - 180;
        bw = Math.min(w * 0.74f, (bottomControls - top) * C / (float)R);
        bh = bw * R / C;
        bx = 8; by = top;
        cell = bw / C;
        btns.clear();
        float topW = Math.max(120, w * 0.28f), topH = 52;
        topBtnY = 48;
        addBtn("设置", 8, w - topW * 0.52f, topBtnY, topW, topH);
        float bwBtn = Math.max(108, w * 0.31f), bhBtn = bwBtn * 0.56f, gap = 10;
        float y2 = h - bhBtn/2 - 24, y1 = y2 - bhBtn - gap;
        addBtn("旋转", 0, bwBtn/2+8, y1, bwBtn, bhBtn);
        addBtn("速降", 1, w/2f, y1, bwBtn, bhBtn);
        addBtn("逆旋", 2, w-bwBtn/2-8, y1, bwBtn, bhBtn);
        addBtn("左移", 3, bwBtn/2+8, y2, bwBtn, bhBtn);
        addBtn("软降", 4, w/2f, y2, bwBtn, bhBtn);
        addBtn("右移", 5, w-bwBtn/2-8, y2, bwBtn, bhBtn);
        float sideLeft = bx + bw + 8;
        float sideW = Math.max(70, w - sideLeft - 6);
        float sideX = sideLeft + sideW / 2;
        if (solo) {
            addBtn("暂停", 6, sideX, by + bh - bhBtn*1.62f, sideW, bhBtn);
        }
        addBtn("暂存", 7, sideX, by + bh - bhBtn*.52f, sideW, bhBtn);
    }

    private void addBtn(String text, int action, float cx, float cy, float w, float h) { btns.add(new Btn(text, action, new RectF(cx-w/2, cy-h/2, cx+w/2, cy+h/2))); }

    private void drawTop(Canvas c) {
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(64); p.setColor(theme().text);
        c.drawText("分", 12, 80, p); p.setTextSize(52); p.setColor(theme().score); c.drawText(String.valueOf(score), 72, 80, p);
        if (solo && !menu) {
            p.setTextSize(28); p.setColor(theme().textMuted);
            String stageInfo = "";
            if (gameMode == MODE_SPRINT) stageInfo = "S" + soloStage + " 目标" + (40 * soloStage) + "行";
            else if (gameMode == MODE_ULTRA) stageInfo = "S" + soloStage + " 目标" + (soloStage * 5000) + "分";
            else if (gameMode == MODE_DIG) stageInfo = "S" + soloStage + " 目标" + (10 * soloStage) + "行";
            else if (gameMode == MODE_MARATHON) stageInfo = "S" + soloStage + " 目标" + (150 * soloStage) + "行";
            if (!stageInfo.isEmpty()) {
                c.drawText(stageInfo, 12, 108, p);
            }
        }
        p.setTextSize(40); p.setColor(theme().text); c.drawText("级", 220, 80, p); p.setTextSize(52); p.setColor(theme().score); c.drawText(String.valueOf(level), 270, 80, p);
        p.setTextSize(40); p.setColor(theme().text); c.drawText("行", 400, 80, p); p.setTextSize(52); p.setColor(theme().score); c.drawText(String.valueOf(lines), 450, 80, p);
    }

    private void drawBoard(Canvas c) {
        p.setStyle(Paint.Style.FILL); p.setColor(theme().board); c.drawRoundRect(new RectF(bx,by,bx+bw,by+bh), 8, 8, p);
        if (!invisible || over) { for (int y=0;y<R;y++) for (int x=0;x<C;x++) if (board[y][x] != 0) block(c,x,y,board[y][x],1f); }
        if (cur != null) {
            int gy = ghostY();
            drawPiece(c, cur, gy, 0.28f);
            drawPiece(c, cur, cur.y, 1f);
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(theme().boardStroke); c.drawRect(bx,by,bx+bw,by+bh,p); p.setStyle(Paint.Style.FILL);
        String timeStr = timeFmt.format(new Date());
        String battStr = batteryPct >= 0 ? (batteryPct + "%") : "";
        String info = timeStr + (battStr.isEmpty() ? "" : "  " + battStr);
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(30); p.setColor(theme().textMuted);
        c.drawText(info, bx, by + bh + 36, p);
    }

    private void drawSide(Canvas c) {
        float sx = bx + bw + 8;
        float sideW = Math.max(110, getWidth() - sx - 6);
        float cx = sx + sideW / 2;
        float box = Math.min(160, sideW);
        float y = by + 36;
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(42); p.setColor(theme().textMuted);
        c.drawText("下一个", cx, y, p); mini(c, next, cx-box/2, y+16, box);
        y += 16 + box + 20;
        c.drawText("暂存", cx, y, p); mini(c, hold==0?null:new Piece(hold), cx-box/2, y+16, box);
        y += 16 + box + 24;
        c.drawText("对手", cx, y, p);
        if (solo) {
            p.setTextSize(34); c.drawText(modeName(), cx, y+40, p);
            p.setTextSize(30); c.drawText(modeProgress(), cx, y+78, p); y += 38;
        } else {
            p.setTextSize(22); p.setColor(theme().score);
            for (PeerInfo pi : peerInfos.values()) {
                y += 28;
                String net = (System.currentTimeMillis() - pi.lastUpdateMs) > 2000 ? "!" : "";
                String status = pi.over ? "KO" : (pi.score + "/" + pi.lines + " L" + pi.level);
                c.drawText(pi.name + net + " " + status, cx, y, p);
                // 迷你棋盘
                if (pi.board != null) {
                    y += 6;
                    float miniCell = Math.min(8, (sideW - 20) / 10f);
                    float miniBoardW = miniCell * 10;
                    float miniBoardH = miniCell * 20;
                    float miniX = cx - miniBoardW / 2;
                    p.setColor(theme().board); c.drawRect(miniX, y, miniX + miniBoardW, y + miniBoardH, p);
                    for (int r = 0; r < 20; r++) {
                        for (int col = 0; col < 10; col++) {
                            int val = pi.board[r][col];
                            if (val != 0) {
                                p.setColor(theme().colors[val]);
                                c.drawRect(miniX + col * miniCell, y + r * miniCell,
                                           miniX + (col + 1) * miniCell - 1, y + (r + 1) * miniCell - 1, p);
                            }
                        }
                    }
                    y += miniBoardH;
                }
            }
            if (peerInfos.isEmpty()) {
                y += 28;
                c.drawText("等待玩家...", cx, y, p);
            }
            p.setColor(theme().textMuted);
        }
        y += 50;
        p.setColor(theme().score); p.setTextSize(32);
        float statLeft = sx + 10;
        float statRight = sx + sideW - 10;
        float lineH = 34;
        p.setTextAlign(Paint.Align.LEFT);  c.drawText("连击", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText(String.valueOf(Math.max(0, combo)), statRight, y, p); y += lineH;
        p.setTextAlign(Paint.Align.LEFT);  c.drawText("B2B", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText(String.valueOf(b2b), statRight, y, p); y += lineH;
        p.setTextAlign(Paint.Align.LEFT);  c.drawText("KO", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText(String.valueOf(kos), statRight, y, p); y += lineH;
        p.setTextAlign(Paint.Align.LEFT);  c.drawText("徽章", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText(String.valueOf(badges), statRight, y, p);
    }

    private void mini(Canvas c, Piece pc, float x, float y, float box) {
        p.setColor(theme().board); c.drawRoundRect(new RectF(x,y,x+box,y+box), 8, 8, p);
        if (pc == null) return;
        float z = box / 3.5f;
        for (int r=0;r<pc.s.length;r++) for (int col=0;col<pc.s[r].length;col++) if (pc.s[r][col] != 0) {
            p.setColor(theme().colors[pc.type]); c.drawRect(x+6+col*z, y+8+r*z, x+6+(col+1)*z-2, y+8+(r+1)*z-2, p);
        }
    }

    private void drawBtns(Canvas c) {
        p.setTextAlign(Paint.Align.CENTER);
        for (Btn b: btns) {
            if (b.action >= 8) p.setTextSize(40); else p.setTextSize(36);
            p.setColor(b.action==6 ? theme().btnPause : (b.action>=8 ? theme().btnTop : theme().btn));
            c.drawRoundRect(b.r, 16, 16, p);
            p.setColor(theme().text); c.drawText(b.text, b.r.centerX(), b.r.centerY()+(b.action>=8?12:13), p);
        }
    }

    private void drawCenter(Canvas c, String a, String b) {
        p.setTextAlign(Paint.Align.CENTER); p.setColor(0xdd000000); c.drawRoundRect(new RectF(50,getHeight()/2f-110,getWidth()-50,getHeight()/2f+110),24,24,p);
        p.setColor(Color.WHITE); p.setTextSize(52); c.drawText(a, getWidth()/2f, getHeight()/2f-28, p);
        p.setColor(0xffaaaaaa); p.setTextSize(34); c.drawText(b, getWidth()/2f, getHeight()/2f+32, p);
        if (over && !menu) {
            p.setColor(0xff888899); p.setTextSize(28);
            String irsTxt = pendingIRS == 0 ? "预旋转: 无 (点旋转/逆旋)" : (pendingIRS > 0 ? "预旋转: 顺时针" : "预旋转: 逆时针");
            String ihsTxt = "预暂存: " + (pendingIHS ? "开 (点暂存切换)" : "关 (点暂存切换)");
            c.drawText(irsTxt, getWidth()/2f, getHeight()/2f+76, p);
            c.drawText(ihsTxt, getWidth()/2f, getHeight()/2f+108, p);
        }
    }
    private void drawParticles(Canvas c) {
        long now = System.currentTimeMillis();
        for (int i=particles.size()-1;i>=0;i--) {
            FxParticle f = particles.get(i);
            float life = (now - f.born) / 650f;
            if (life >= 1f) { particles.remove(i); continue; }
            p.setColor(applyAlpha(f.color, 1f-life));
            c.drawCircle(f.x + f.vx*life, f.y + f.vy*life, f.size*(1f-life*.35f), p);
        }
    }

    private void drawFxOverlay(Canvas c, int w, int h) {
        long now = System.currentTimeMillis();
        if (now < flashUntil) {
            p.setColor(applyAlpha(theme().blockFlash, .16f));
            c.drawRect(0, 0, w, h, p);
        }
        if (now < fxUntil && !fxText.isEmpty()) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(theme().fxText); p.setTextSize(48);
            c.drawText(fxText, w/2f, h*.42f, p);
        }
    }

    private void fx(String text, boolean strong) {
        fxText = text; fxUntil = System.currentTimeMillis() + 850; flashUntil = System.currentTimeMillis() + (strong ? 260 : 140);
        if (strong) shakeUntil = System.currentTimeMillis() + 240;
        for (int i=0;i<(strong?34:18);i++) particles.add(new FxParticle(bx+bw/2, by+bh*.45f, (rnd.nextFloat()-.5f)*bw, (rnd.nextFloat()-.75f)*bh*.55f, theme().colors[1+rnd.nextInt(7)], 4+rnd.nextFloat()*7));
    }


    private void drawSettings(Canvas c, int w, int h) {
        p.setColor(theme().bg); c.drawRect(0, 0, w, h, p);
        p.setTextAlign(Paint.Align.CENTER); p.setColor(theme().text); p.setTextSize(46); c.drawText("设置", w/2f, h*0.18f, p);
        if (solo) {
            drawMenuButton(c, "继续游戏", w*.16f, h*.26f, w*.84f, h*.33f, false);
            drawMenuButton(c, "读取存档", w*.16f, h*.35f, w*.84f, h*.42f, false);
            drawMenuButton(c, "手动保存", w*.16f, h*.44f, w*.84f, h*.51f, false);
        } else {
            drawMenuButton(c, "继续游戏", w*.16f, h*.32f, w*.84f, h*.40f, false);
        }
        // DAS/ARR/软降调节
        float rowH = h * 0.068f;
        float sy = h * 0.55f;
        float btnW = w * 0.12f;
        String[][] cfg = {{"DAS", String.valueOf(dasMs)}, {"ARR", String.valueOf(arrMs)}, {"软降", String.valueOf(softMs)}};
        for (int i = 0; i < 3; i++) {
            float y = sy + i * rowH;
            p.setTextAlign(Paint.Align.LEFT); p.setColor(theme().textMuted); p.setTextSize(26);
            c.drawText(cfg[i][0] + " " + cfg[i][1] + "ms", w*0.18f, y + rowH*0.6f, p);
            drawMenuButton(c, "-", w*0.60f, y, w*0.60f+btnW, y+rowH*0.85f, false);
            drawMenuButton(c, "+", w*0.74f, y, w*0.74f+btnW, y+rowH*0.85f, false);
        }
        drawMenuButton(c, "返回主界面", w*.16f, h*.82f, w*.84f, h*.89f, false);
        if (confirmQuit) {
            p.setColor(0x88000000); c.drawRect(0, 0, w, h, p);
            float dw=w*.78f, dh=h*.30f, dx=(w-dw)/2, dy=(h-dh)/2;
            p.setStyle(Paint.Style.FILL); p.setColor(theme().bg);
            c.drawRoundRect(new RectF(dx, dy, dx+dw, dy+dh), 24, 24, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(theme().boardStroke);
            c.drawRoundRect(new RectF(dx, dy, dx+dw, dy+dh), 24, 24, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(theme().text); p.setTextSize(42);
            c.drawText("保存游戏", w/2f, dy+dh*.28f, p);
            p.setColor(theme().textMuted); p.setTextSize(30);
            c.drawText("是否保存当前游戏进度？", w/2f, dy+dh*.54f, p);
            float qbW=dw*.27f, btnH=dh*.22f, btnY=dy+dh*.76f, gap=dw*.05f;
            drawMenuButton(c, "保存", w/2f-qbW*1.5f-gap, btnY, w/2f-qbW*.5f-gap, btnY+btnH, false);
            drawMenuButton(c, "不保存", w/2f-qbW/2f, btnY, w/2f+qbW/2f, btnY+btnH, false);
            drawMenuButton(c, "取消", w/2f+qbW*.5f+gap, btnY, w/2f+qbW*1.5f+gap, btnY+btnH, false);
        }
    }

    private void block(Canvas c, int x, int y, int type, float alpha) {
        p.setColor(applyAlpha(theme().colors[type], alpha));
        float l=bx+x*cell, t=by+y*cell;
        c.drawRect(l+1,t+1,l+cell-2,t+cell-2,p);
        p.setColor(applyAlpha(theme().blockFlash, alpha*.22f));
        c.drawRect(l+2,t+2,l+cell-3,Math.max(t+3,t+cell*.28f),p);
    }
    private int applyAlpha(int color, float alpha) { return (Math.round(255*alpha)<<24) | (color & 0x00ffffff); }
    private void drawPiece(Canvas c, Piece pc, int yy, float alpha) { for(int r=0;r<pc.s.length;r++) for(int x=0;x<pc.s[r].length;x++) if(pc.s[r][x]!=0) block(c,pc.x+x,yy+r,pc.type,alpha); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int masked = e.getActionMasked();
        if (masked == MotionEvent.ACTION_DOWN || masked == MotionEvent.ACTION_POINTER_DOWN) {
            int idx = e.getActionIndex();
            float x=e.getX(idx), y=e.getY(idx);
            if (menu) return touchMenu(x,y);
            if (settings) return touchSettings(x, y);
            if (over && !menu && solo && gameMode != MODE_CLASSIC) {
                if (soloOverRestartBtn != null && soloOverRestartBtn.contains(x, y)) {
                    soloStage = 1;
                    start();
                    return true;
                }
                if (soloOverRetryBtn != null && soloOverRetryBtn.contains(x, y)) {
                    start();
                    return true;
                }
            }
            for (Btn b: btns) if (b.r.contains(x,y)) {
                int pointerId = e.getPointerId(idx);
                pointerActions.put(pointerId, b.action);
                activeAction = b.action;
                pressAction(b.action);
                return true;
            }
            return true;
        }
        if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL || masked == MotionEvent.ACTION_POINTER_UP) {
            if (masked == MotionEvent.ACTION_CANCEL || masked == MotionEvent.ACTION_UP) {
                releaseAllActions();
                return true;
            }
            int idx = e.getActionIndex();
            int pointerId = e.getPointerId(idx);
            Integer action = pointerActions.remove(pointerId);
            if (action != null) releaseAction(action);
            return true;
        }
        return true;
    }

    private boolean touchMenu(float x, float y) {
        int w=getWidth(), h=getHeight();
        if (menuPage == 0) {
            if (hit(x,y,w*.14f,h*.30f,w*.86f,h*.39f)) { solo=true; menuPage=1; return true; }
            if (hit(x,y,w*.14f,h*.43f,w*.86f,h*.52f)) { solo=false; menuPage=2; return true; }
            return true;
        }
        if (menuPage == 1) {
            float btnH = h * 0.058f, gap = h * 0.010f, sy = h * 0.28f;
            if (hit(x,y,w*.08f,sy,w*.46f,sy+btnH)) { askClassic(); return true; }
            if (hit(x,y,w*.54f,sy,w*.92f,sy+btnH)) { startMode(MODE_SPRINT); return true; }
            if (hit(x,y,w*.08f,sy+btnH+gap,w*.46f,sy+2*btnH+gap)) { startMode(MODE_ULTRA); return true; }
            if (hit(x,y,w*.54f,sy+btnH+gap,w*.92f,sy+2*btnH+gap)) { startMode(MODE_MARATHON); return true; }
            if (hit(x,y,w*.08f,sy+2*(btnH+gap),w*.46f,sy+3*btnH+2*gap)) { startMode(MODE_INVISIBLE); return true; }
            if (hit(x,y,w*.54f,sy+2*(btnH+gap),w*.92f,sy+3*btnH+2*gap)) { startMode(MODE_DIG); return true; }
            if (hit(x,y,w*.08f,sy+3*(btnH+gap),w*.46f,sy+4*btnH+3*gap)) { startMode(MODE_SURVIVAL); return true; }
            float by = sy + 4*(btnH+gap) + gap*2;
            if (hit(x,y,w*.14f,by,w*.86f,by+btnH)) { menuPage=0; return true; }
            return true;
        }
        if (p2p == null || p2pDiscovery) {
            if (hit(x,y,w*.08f,h*.31f,w*.46f,h*.39f)) { createRoom(); return true; }
            if (hit(x,y,w*.54f,h*.31f,w*.92f,h*.39f)) { askRoom(); return true; }
            if (lastRoomName != null) {
                if (hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { reconnectLastRoom(); return true; }
                if (hit(x,y,w*.54f,h*.42f,w*.92f,h*.50f)) { askName(); return true; }
                if (hit(x,y,w*.15f,h*.53f,w*.85f,h*.61f)) { stopP2p(); menuPage=0; return true; }
            } else {
                if (hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { askName(); return true; }
                if (hit(x,y,w*.15f,h*.53f,w*.85f,h*.61f)) { stopP2p(); menuPage=0; return true; }
            }
            // 点击发现的房间卡片加入
            int nf = foundRooms.size();
            float cardY = h*0.612f, cardH = h*0.058f, cardGap = h*0.008f, cardL = w*0.06f, cardR = w*0.94f;
            int limit = Math.min(nf, 4);
            for (int i = 0; i < limit; i++) {
                float cy = cardY + i * (cardH + cardGap);
                if (hit(x,y,cardL,cy,cardR,cy+cardH)) {
                    DiscoveredRoom dr = foundRooms.get(i);
                    joinRoom(dr.room, dr.host);
                    return true;
                }
            }
        } else {
            if (hit(x,y,w*.08f,h*.31f,w*.46f,h*.39f)) {
                if (isHost) { if (canHostStart()) hostStartGame(); return true; }
                else { toggleReady(); return true; }
            }
            if (hit(x,y,w*.54f,h*.31f,w*.92f,h*.39f)) { askChat(); return true; }
            if (hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { askName(); return true; }
            if (hit(x,y,w*.54f,h*.42f,w*.92f,h*.50f)) { leaveRoom(); return true; }
            if (isHost) {
                int bc = botCount();
                if (hit(x,y,w*.08f,h*.53f,w*.46f,h*.61f)) {
                    if (playerCount() < MAX_PLAYERS) { addBot(); return true; }
                }
                if (hit(x,y,w*.54f,h*.53f,w*.92f,h*.61f)) {
                    if (bc > 0) { removeBot(); return true; }
                    else if (!peerNames.isEmpty()) { kickPlayer(); return true; }
                }
            }
        }
        return true;
    }

    private boolean touchSettings(float x, float y) {
        int w=getWidth(), h=getHeight();
        if (confirmQuit) {
            float dw=w*.78f, dh=h*.28f, dy=(h-dh)/2;
            float btnW=dw*.27f, btnH=dh*.22f, btnY=dy+dh*.72f, gap=dw*.05f;
            float bx1=w/2f-btnW*1.5f-gap, bx2=w/2f-btnW/2f, bx3=w/2f+btnW/2f+gap;
            if (hit(x,y,bx1,btnY,bx1+btnW,btnY+btnH)) { save(true); confirmQuit=false; goMenu(); return true; }
            if (hit(x,y,bx2,btnY,bx2+btnW,btnY+btnH)) { confirmQuit=false; goMenu(); return true; }
            if (hit(x,y,bx3,btnY,bx3+btnW,btnY+btnH)) { confirmQuit=false; return true; }
            return true;
        }
        if (solo && hit(x,y,w*.16f,h*.26f,w*.84f,h*.33f)) { settings=false; setPaused(false); return true; }
        if (solo && hit(x,y,w*.16f,h*.35f,w*.84f,h*.42f)) { if (solo) load(); settings=false; return true; }
        if (solo && hit(x,y,w*.16f,h*.44f,w*.84f,h*.51f)) { save(true); return true; }
        if (!solo && hit(x,y,w*.16f,h*.32f,w*.84f,h*.40f)) { settings=false; return true; }
        // DAS/ARR/软降 点击
        float rowH = h * 0.068f;
        float sy = h * 0.55f;
        float btnW = w * 0.12f;
        for (int i = 0; i < 3; i++) {
            float rowY = sy + i * rowH;
            if (hit(x,y,w*.60f,rowY,w*.60f+btnW,rowY+rowH*0.85f)) {
                if (i==0) { dasMs = Math.max(0, dasMs - 17); sp.edit().putLong("das_ms", dasMs).apply(); }
                else if (i==1) { arrMs = Math.max(0, arrMs - 5); sp.edit().putLong("arr_ms", arrMs).apply(); }
                else { softMs = Math.max(20, softMs - 20); sp.edit().putLong("soft_ms", softMs).apply(); }
                return true;
            }
            if (hit(x,y,w*.74f,rowY,w*.74f+btnW,rowY+rowH*0.85f)) {
                if (i==0) { dasMs = Math.min(500, dasMs + 17); sp.edit().putLong("das_ms", dasMs).apply(); }
                else if (i==1) { arrMs = Math.min(200, arrMs + 5); sp.edit().putLong("arr_ms", arrMs).apply(); }
                else { softMs = Math.min(500, softMs + 20); sp.edit().putLong("soft_ms", softMs).apply(); }
                return true;
            }
        }
        if (hit(x,y,w*.16f,h*.82f,w*.84f,h*.89f)) {
            if (!over && !menu) { confirmQuit = true; return true; }
            goMenu(); return true;
        }
        return true;
    }
    private boolean hit(float x,float y,float l,float t,float r,float b){return x>=l&&x<=r&&y>=t&&y<=b;}

    private void startP2p() {
        if (p2p != null) return;
        p2pStatus = "房间广播中";
        p2p = new P2pTransport(roomName, roomPass, playerName, playerId, isHost, new P2pTransport.Listener() {
            @Override public void onRoomFound(String room, String host, String name, boolean hostRole) {
                if (reconnectCheckHost != null && reconnectCheckHost.equals(host)) {
                    reconnectCheckHost = null; reconnectCheckUntil = 0;
                    addChat("系统: 找到目标房间，正在加入");
                }
                if (roomName.equals(room) && hostRole && !isHost) roomHost = host;
                if (!roomName.equals(room) && hostRole) {
                    boolean found = false;
                    long now = System.currentTimeMillis();
                    Iterator<DiscoveredRoom> it = foundRooms.iterator();
                    while (it.hasNext()) {
                        DiscoveredRoom dr = it.next();
                        if (now - dr.lastSeenMs > 30000) { it.remove(); continue; }
                        if (dr.room.equals(room) && dr.host.equals(host)) {
                            dr.name = name; dr.lastSeenMs = now;
                            found = true;
                        }
                    }
                    if (!found) foundRooms.add(new DiscoveredRoom(room, host, name));
                }
            }
            @Override public void onPeer(String host, String name, int score, int lines, int level, boolean over, int kos, int badges, String board, String via) {
                lastAnyPeerUpdate = System.currentTimeMillis();
                if (networkFrozen) { networkFrozen = false; paused = false; addChat("系统: 网络恢复，游戏继续"); }
                if (!acceptPeer(host, name)) return;
                PeerInfo pi = peerInfos.get(host);
                if (pi == null) { pi = new PeerInfo(name); peerInfos.put(host, pi); }
                pi.name = name; pi.score = score; pi.lines = lines; pi.level = level;
                pi.over = over; pi.kos = kos; pi.badges = badges;
                pi.lastUpdateMs = System.currentTimeMillis(); pi.via = via;
                if (board != null && !board.isEmpty()) pi.board = decodeBoard(board);
                peerNames.put(host, name);
                p2pStatus = "已连接 " + playerCount() + "/" + MAX_PLAYERS;
            }
            @Override public void onReady(String host, String name, boolean ready) {
                if (!acceptPeer(host, name)) return;
                peerNames.put(host, name); readyPeers.put(host, ready);
                addChat("系统: " + name + (ready ? " 已准备" : " 取消准备"));
                maybeCountdown();
            }
            @Override public void onChat(String host, String name, String text) { if (acceptPeer(host, name)) addChat(name + ": " + text); }
            @Override public void onStart(String host, long seed, long startAt) {
                if (!fromRoomHost(host)) return;
                pendingStartSeed = seed; pendingStartAt = startAt; p2pStatus = "3秒后开始";
            }
            @Override public void onGarbage(int rows) { pendingGarbage += rows; garbageDueAt = System.currentTimeMillis() + 1800; p2pStatus = "收到垃圾 " + rows; tone(sGarbage); fx("WARNING +" + rows, true); }
            @Override public void onLeave(String host, String name) {
                peerNames.remove(host); readyPeers.remove(host); peerInfos.remove(host);
                addChat("系统: " + name + " 离开了房间");
                p2pStatus = p2p == null ? "P2P未启动" : "已连接 " + playerCount() + "/" + MAX_PLAYERS;
            }
            @Override public void onKick(String host, String name, String targetName, String reason) {
                if (!fromRoomHost(host)) return;
                if (targetName.equals(playerName)) {
                    stopP2p(); isHost = false;
                    fx("被踢出", true);
                    addChat("系统: 你被房主踢出 (" + reason + ")");
                    return;
                }
                for (java.util.Iterator<java.util.Map.Entry<String, String>> it = peerNames.entrySet().iterator(); it.hasNext(); ) {
                    java.util.Map.Entry<String, String> e = it.next();
                    if (e.getValue().equals(targetName)) {
                        it.remove(); readyPeers.remove(e.getKey());
                        addChat("系统: " + targetName + " 被踢出");
                        break;
                    }
                }
            }
            @Override public void onDisband(String host, String name) {
                if (!fromRoomHost(host)) return;
                stopP2p(); isHost = false;
                fx("房间已解散", true);
                addChat("系统: 房主解散了房间");
            }
            @Override public void onDisconnect(String host) {
                PeerInfo pi = peerInfos.get(host);
                if (pi != null) {
                    pi.disconnected = true;
                    pi.disconnectedAt = System.currentTimeMillis();
                    addChat("系统: " + pi.name + " 断开连接，30秒内可重连");
                } else {
                    String name = peerNames.get(host);
                    if (name != null) addChat("系统: " + name + " 断开连接");
                }
                // 房主断开，选举新房主
                if (roomHost != null && roomHost.equals(host) && !isHost) {
                    String newHost = null;
                    String newHostName = null;
                    for (java.util.Map.Entry<String, PeerInfo> e : peerInfos.entrySet()) {
                        if (e.getValue().disconnected) continue;
                        if (e.getValue().name.startsWith("BOT_")) continue;
                        newHost = e.getKey();
                        newHostName = e.getValue().name;
                        break;
                    }
                    if (newHost != null) {
                        roomHost = newHost;
                        addChat("系统: 房主已断开，" + newHostName + " 成为新房主");
                        if (p2p != null && p2p.getLocalAddresses().contains(newHost)) {
                            isHost = true;
                            p2p.setHostRole(true);
                            addChat("系统: 你已成为新房主");
                        }
                    }
                }
                p2pStatus = p2p == null ? "P2P未启动" : "已连接 " + playerCount() + "/" + MAX_PLAYERS;
            }
            @Override public void onSurrender(String host, String name) {
                PeerInfo pi = peerInfos.get(host);
                if (pi != null) { pi.over = true; }
                addChat("系统: " + name + " 认输");
                checkMultiFinish();
            }
            @Override public void onReturnLobby(String host, String name) {
                if (!fromRoomHost(host)) return;
                returnToRoom();
            }
            @Override public void onBotState(String host, String botName, int score, int lines, int level, boolean over, int kos, int badges) {
                PeerInfo pi = peerInfos.get(host);
                if (pi == null) { pi = new PeerInfo(botName); peerInfos.put(host, pi); }
                pi.name = botName; pi.score = score; pi.lines = lines; pi.level = level;
                pi.over = over; pi.kos = kos; pi.badges = badges;
                pi.lastUpdateMs = System.currentTimeMillis(); pi.via = "bot";
                peerNames.put(host, botName);
            }
            @Override public void onReconnect(String host, String name) {
                PeerInfo pi = peerInfos.get(host);
                if (pi != null) {
                    pi.disconnected = false;
                    pi.disconnectedAt = 0;
                }
                addChat("系统: " + name + " 重新连接");
            }
            @Override public void onError(String message) { p2pStatus = "P2P错误"; }
        });
        p2p.start();
    }

    private void sendP2pState() {
        p2p.publishState(score, lines, level, over, kos, badges, encodeBoard());
    }

    private void syncStart() {
        startP2p();
        long seed = System.currentTimeMillis();
        long at = System.currentTimeMillis() + 3000;
        pendingStartSeed = seed;
        pendingStartAt = at;
        p2p.sendStart(seed, at);
        p2pStatus = "3秒后开始";
    }

    private void createRoom() {
        if (p2p != null) {
            if (isHost) { if (p2p != null) p2p.sendDisband(); }
            else { if (p2p != null) p2p.sendLeave(); }
            stopP2p();
        }
        isHost = true;
        roomHost = null;
        roomName = "R" + (1000 + rnd.nextInt(9000));
        p2pDiscovery = false;
        restartP2p();
        addChat("系统: 已创建房间 " + roomName);
    }

    private void askRoom() {
        askText("输入房间号", roomName, text -> {
            roomName = clean(text, "TETRIS");
            if (p2p != null) { p2p.sendLeave(); stopP2p(); }
            isHost = false;
            roomHost = null;
            restartP2p();
            addChat("系统: 已进入房间 " + roomName);
        });
    }

    private void joinRoom(String room, String host) {
        if (p2p != null) { p2p.sendLeave(); stopP2p(); }
        isHost = false;
        roomName = room;
        roomHost = host;
        p2pDiscovery = false;
        restartP2p();
        addChat("系统: 加入发现房间 " + roomName);
    }

    private void toggleReady() {
        startP2p();
        selfReady = !selfReady;
        p2p.sendReady(selfReady);
        addChat("我: " + (selfReady ? "已准备" : "取消准备"));
        maybeCountdown();
    }

    private void maybeCountdown() {
        // 房主不再自动开局，只是更新状态
    }
    private boolean canHostStart() {
        if (!isHost || pendingStartAt > 0) return false;
        int pc = playerCount();
        if (pc < 2) return false;
        for (java.util.Map.Entry<String, PeerInfo> e : peerInfos.entrySet()) {
            if (e.getValue().name.startsWith("BOT_")) continue;
            if (!Boolean.TRUE.equals(readyPeers.get(e.getKey()))) return false;
        }
        return true;
    }

    private int playerCount() {
        int n = 1;
        long now = System.currentTimeMillis();
        for (PeerInfo pi : peerInfos.values()) {
            if (!pi.disconnected || (now - pi.disconnectedAt < 30000)) n++;
        }
        return Math.min(MAX_PLAYERS, n);
    }
    private int readyCount() { int n = (!isHost && selfReady) ? 1 : 0; for (Boolean r: readyPeers.values()) if (r) n++; return Math.min(MAX_PLAYERS, n); }
    private boolean isRoomFullForNewPeer(String host) { return !peerNames.containsKey(host) && playerCount() >= MAX_PLAYERS; }
    private boolean fromRoomHost(String host) { return isHost || roomHost == null || roomHost.equals(host); }
    private boolean acceptPeer(String host, String name) {
        if (!isRoomFullForNewPeer(host)) return true;
        if (isHost && p2p != null) p2p.sendKick(host, name, "房间已满");
        p2pStatus = "房间已满 " + MAX_PLAYERS + "/" + MAX_PLAYERS;
        return false;
    }

    private void leaveRoom() {
        if (!menu && !over && p2p != null) p2p.sendSurrender();
        if (p2p != null) p2p.sendLeave();
        saveLastRoom();
        stopP2p();
        isHost = false;
        addChat("系统: 已退出房间");
    }

    private void saveLastRoom() {
        if (roomName != null) {
            sp.edit().putString("last_room_name", roomName).putString("last_room_host", roomHost).apply();
            lastRoomName = roomName;
            lastRoomHost = roomHost;
        }
    }

    private void reconnectLastRoom() {
        if (lastRoomName == null) return;
        roomName = lastRoomName;
        roomHost = lastRoomHost;
        if (p2p != null) { p2p.sendLeave(); stopP2p(); }
        isHost = false;
        p2pDiscovery = false;
        restartP2p();
        reconnectCheckHost = lastRoomHost;
        reconnectCheckUntil = System.currentTimeMillis() + 5000;
        addChat("系统: 尝试重连 " + roomName + "，检测中...");
    }

    private void askName() {
        askText("修改名称", playerName, text -> {
            String v = clean(text, playerName);
            if (v.length() > 8) v = v.substring(0, 8);
            if (!v.isEmpty() && !v.equals(playerName)) {
                playerName = v;
                sp.edit().putString("player_name", playerName).apply();
                addChat("系统: 名称已改为 " + playerName);
            }
        });
    }

    private void disbandRoom() {
        if (!isHost) return;
        if (p2p != null) p2p.sendDisband();
        stopP2p();
        isHost = false;
        addChat("系统: 房间已解散");
    }

    private void kickPlayer() {
        if (!isHost || peerNames.isEmpty()) return;
        String[] items = new String[peerNames.size()];
        final String[] hosts = new String[peerNames.size()];
        int i = 0;
        for (java.util.Map.Entry<String, String> e : peerNames.entrySet()) {
            hosts[i] = e.getKey();
            items[i] = e.getValue() + " (" + e.getKey() + ")";
            i++;
        }
        new AlertDialog.Builder(getContext())
            .setTitle("选择要踢出的玩家")
            .setItems(items, (dialog, which) -> {
                String targetHost = hosts[which];
                String targetName = peerNames.get(targetHost);
                if (p2p != null) p2p.sendKick(targetHost, targetName, "被房主踢出");
                peerNames.remove(targetHost);
                readyPeers.remove(targetHost);
                addChat("系统: 已踢出 " + targetName);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void askChat() {
        askText("聊天", "", text -> {
            String t = clean(text, "");
            if (t.isEmpty()) return;
            startP2p();
            p2p.sendChat(t);
            addChat("我: " + t);
        });
    }

    private void askText(String title, String value, TextDone done) {
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(getContext()).setTitle(title).setView(input)
            .setPositiveButton("确定", (d, which) -> done.apply(input.getText().toString()))
            .setNegativeButton("取消", null).show();
    }

    private void askClassic() {
        new AlertDialog.Builder(getContext())
            .setTitle("经典模式")
            .setItems(new String[]{"新游戏", "读取存档"}, (dialog, which) -> {
                if (which == 0) startMode(MODE_CLASSIC);
                else load();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private String clean(String s, String fallback) {
        if (s == null) return fallback;
        String v = s.trim().replace("|", "");
        return v.isEmpty() ? fallback : v;
    }

    private void addChat(String line) {
        chat.add(line.length() > 32 ? line.substring(0, 32) : line);
        while (chat.size() > 20) chat.remove(0);
    }

    private void restartP2p() {
        stopP2p();
        selfReady = false; readyPeers.clear(); peerNames.clear(); pendingStartAt = 0; pendingStartSeed = 0;
        startP2p();
    }

    private void stopP2p() {
        if (p2p != null) { p2p.stop(); p2p = null; }
        p2pStatus = "P2P未启动";
        selfReady = false; readyPeers.clear(); peerNames.clear(); peerInfos.clear(); foundRooms.clear(); p2pDiscovery = false;
        rankingUntil = 0;
        bots.clear(); nextBotId = 1;
    }

    private void pressAction(int a) {
        long now = System.currentTimeMillis();
        if (a==3) { leftHeld = true; leftStart = now; arrAt = now; move(-1,0); return; }
        if (a==5) { rightHeld = true; rightStart = now; arrAt = now; move(1,0); return; }
        if (a==4) { softHeld = true; softStart = now; softAt = 0; if (move(0,1)) score++; return; }
        act(a);
    }

    private void releaseAction(int a) {
        if (a==3) leftHeld = false;
        if (a==5) rightHeld = false;
        if (a==4) softHeld = false;
        if (a==1) hardReady = true;
        activeAction = -1;
    }

    private void releaseAllActions() {
        leftHeld = false;
        rightHeld = false;
        softHeld = false;
        hardReady = true;
        pointerActions.clear();
        activeAction = -1;
    }

    private void input(long now) {
        if (leftHeld && !rightHeld) repeatHorizontal(-1, now, leftStart);
        else if (rightHeld && !leftHeld) repeatHorizontal(1, now, rightStart);
        if (softHeld) {
            long held = now - softStart;
            long interval = Math.max(14, softMs - held / 12);
            if (now - softAt > interval) { if (move(0,1)) score++; softAt = now; }
        }
    }

    private void repeatHorizontal(int dir, long now, long startedAt) {
        if (now - startedAt <= dasMs) return;
        if (arrMs == 0) { while(move(dir,0)); return; }
        if (now - arrAt > arrMs) { move(dir,0); arrAt = now; }
    }

    private void act(int a) {
        if (a==8) { settings=true; setPaused(true); releaseAction(activeAction); return; }
        if (a==6) { if (!solo) return; setPaused(!paused); tone(sReady); return; }
        if (a==7) { 
            if (over && !menu) { pendingIHS = !pendingIHS; tone(sReady); return; }
            hold(); return; 
        }
        if (over && !menu) {
            if (a==0) { pendingIRS = (pendingIRS == 1) ? 0 : 1; tone(sReady); return; }
            if (a==2) { pendingIRS = (pendingIRS == -1) ? 0 : -1; tone(sReady); return; }
            if (a==1) { goMenu(); return; }
            return;
        }
        if (over) return;
        if (a==0) rotate(true); else if (a==2) rotate(false); else if (a==3) move(-1,0); else if (a==5) move(1,0); else if (a==4) move(0,1); else if (a==1) hardDrop();
    }

    private void hardDrop() {
        long now = System.currentTimeMillis();
        if (!hardReady || now - lastHard < HARD_COOLDOWN_MS) return;
        hardReady = false;
        lastHard = now;
        while(move(0,1)) score++;
        tone(sDrop);
        fx("DROP", false);
        onGround = false;
        lock();
        lastDrop = now;
    }

    private void goMenu() {
        if (solo) save(false);
        menu = true;
        settings = false;
        paused = false;
        confirmQuit = false;
        menuPage = solo ? 1 : 2;
        pendingIRS = 0;
        pendingIHS = false;
    }

    private void setPaused(boolean value) {
        if (paused == value) return;
        if (!solo && value) return; // 多人模式禁止暂停
        long now = System.currentTimeMillis();
        if (value) {
            pauseStartedAt = now;
        } else if (pauseStartedAt > 0) {
            pausedTotalMs += now - pauseStartedAt;
            pauseStartedAt = 0;
        }
        paused = value;
    }

    private long modeElapsedMs(long now) {
        if (modeStartAt <= 0) return 0;
        long extraPause = paused && pauseStartedAt > 0 ? now - pauseStartedAt : 0;
        return Math.max(0, now - modeStartAt - pausedTotalMs - extraPause);
    }

    private String formatTime(long ms) {
        long sec = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60);
    }

    private String modeName() {
        if (gameMode == MODE_SPRINT) return "冲刺40行";
        if (gameMode == MODE_ULTRA) return "限时得分";
        if (gameMode == MODE_MARATHON) return "马拉松";
        if (gameMode == MODE_INVISIBLE) return "隐形模式";
        if (gameMode == MODE_DIG) return "挖掘挑战";
        if (gameMode == MODE_SURVIVAL) return "无尽生存";
        return "经典模式";
    }

    private String modeProgress() {
        long elapsed = modeElapsedMs(System.currentTimeMillis());
        if (gameMode == MODE_SPRINT) return Math.min(lines, 40 * soloStage) + "/" + (40 * soloStage) + "行 " + formatTime(elapsed);
        if (gameMode == MODE_ULTRA) return "剩余 " + formatTime(120000 - elapsed) + " 目标 " + (soloStage * 5000) + "分";
        if (gameMode == MODE_MARATHON) return Math.min(lines, 150 * soloStage) + "/" + (150 * soloStage) + "行";
        if (gameMode == MODE_INVISIBLE) return "行 " + lines + " " + formatTime(elapsed);
        if (gameMode == MODE_DIG) return Math.min(lines, digTargetLines) + "/" + digTargetLines + "行 " + formatTime(elapsed);
        if (gameMode == MODE_SURVIVAL) return "级 " + level + " 关 " + soloStage + " " + formatTime(elapsed);
        return "时间 " + formatTime(elapsed);
    }

    private void finishGame(String text) {
        over = true;
        finishText = text;
        releaseAllActions();
        if (!solo && p2p != null) {
            sendP2pState();
        }
    }

    private void checkMultiFinish() {
        if (solo || p2p == null) return;
        int alive = over ? 0 : 1;
        int playing = over ? 0 : 1;
        for (PeerInfo pi : peerInfos.values()) {
            if (!pi.over && !pi.disconnected) alive++;
            if (!pi.over) playing++;
        }
        if (alive <= 1 && rankingUntil == 0) {
            if (!over) finishGame("获胜");
            showRankingAndReturn();
        } else if (playing > 0 && allPeersOver() && rankingUntil == 0) {
            showRankingAndReturn();
        }
    }
    private boolean allPeersOver() {
        if (!over) return false;
        for (PeerInfo pi : peerInfos.values()) if (!pi.over) return false;
        return true;
    }

    private void cleanupDisconnectedPeers(long now) {
        java.util.Iterator<java.util.Map.Entry<String, PeerInfo>> it = peerInfos.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, PeerInfo> e = it.next();
            PeerInfo pi = e.getValue();
            if (pi.disconnected && now - pi.disconnectedAt > 30000) {
                String name = pi.name;
                it.remove();
                peerNames.remove(e.getKey());
                readyPeers.remove(e.getKey());
                addChat("系统: " + name + " 超时未重连，已移除");
            }
        }
    }
    private void showRankingAndReturn() {
        long now = System.currentTimeMillis();
        java.util.List<PeerInfo> all = new java.util.ArrayList<>();
        if (!over || finishText.contains("获胜")) {
            PeerInfo self = new PeerInfo(playerName);
            self.score = score; self.lines = lines; self.kos = kos; self.badges = badges; self.over = over;
            all.add(self);
        }
        for (PeerInfo pi : peerInfos.values()) all.add(pi);
        all.sort((a,b) -> {
            if (a.over != b.over) return a.over ? 1 : -1;
            if (b.score != a.score) return b.score - a.score;
            if (b.kos != a.kos) return b.kos - a.kos;
            if (b.badges != a.badges) return b.badges - a.badges;
            return b.lines - a.lines;
        });
        StringBuilder sb = new StringBuilder("排名 ");
        for (int i=0;i<all.size();i++) {
            PeerInfo pi = all.get(i);
            sb.append("#").append(i+1).append(" ").append(pi.name)
              .append(" K").append(pi.kos).append(" B").append(pi.badges)
              .append(" ").append(pi.score).append("/").append(pi.lines);
            if (i < all.size()-1) sb.append("  ");
        }
        finishText = sb.toString();
        rankingUntil = now + 5000;
    }
    private void returnToRoom() {
        menu = true; menuPage = 2; over = false; paused = false; settings = false;
        finishText = ""; rankingUntil = 0;
        selfReady = false; readyPeers.clear(); peerInfos.clear();
        score = 0; lines = 0; level = 1; pendingGarbage = 0;
        combo = -1; b2b = 0; badges = 0; kos = 0;
        clearBots();
        if (p2p != null) p2p.publishState(0, 0, 1, false, 0, 0, encodeBoard());
        if (isHost && p2p != null) p2p.sendReturnLobby();
    }
    private boolean checkModeFinish() {
        if (!solo) return false;
        if (gameMode == MODE_SPRINT && lines >= 40 * soloStage) { advanceStage(); return true; }
        if (gameMode == MODE_MARATHON && lines >= 150 * soloStage) { advanceStage(); return true; }
        if (gameMode == MODE_DIG && lines >= digTargetLines) { advanceStage(); return true; }
        if (gameMode == MODE_ULTRA && score >= soloStage * 5000) { advanceStage(); return true; }
        return false;
    }

    private void advanceStage() {
        soloStage++;
        fx("STAGE " + soloStage, true);
        tone(sReady);
        board = new int[R][C];
        hold = 0;
        pendingGarbage = 0;
        combo = -1;
        b2b = 0;
        garbageDueAt = 0;
        areUntil = 0;
        clearing = false;
        onGround = false;
        lockUntil = 0;
        lockResets = 0;
        bagIndex = 7;
        canHold = true;
        particles.clear();
        modeStartAt = System.currentTimeMillis();
        if (gameMode == MODE_DIG) {
            int target = Math.min(20, 10 * soloStage);
            digTargetLines = target;
            for (int y = R - target; y < R; y++) {
                int hole = rnd.nextInt(C);
                for (int x = 0; x < C; x++) board[y][x] = (x == hole) ? 0 : 7;
            }
        }
        next = randomPiece();
        if (pendingIRS != 0) {
            next.s = rot(next.s, pendingIRS > 0);
            next.rot = (pendingIRS > 0) ? 1 : 3;
            pendingIRS = 0;
        }
        spawn();
        if (pendingIHS) {
            pendingIHS = false;
            hold();
        }
        lastDrop = System.currentTimeMillis();
    }

    private void start() { start(System.currentTimeMillis()); }
    private void startMode(int mode) { gameMode = mode; soloStage = 1; start(); }
    private void start(long seed) { if (!solo) seed = seed ^ playerName.hashCode() ^ playerId.hashCode(); rnd.setSeed(seed); board=new int[R][C]; score=0; lines=0; level=1; dropMs=1000; hold=0; pendingGarbage=0; combo=-1; b2b=0; badges=0; kos=0; garbageDueAt=0; areUntil=0; clearing=false; onGround=false; lockUntil=0; lockResets=0; bagIndex=7; releaseAllActions(); canHold=true; over=false; paused=false; settings=false; finishText=""; pausedTotalMs=0; pauseStartedAt=0; modeStartAt=System.currentTimeMillis(); invisible=false; digTargetLines=0; C=10; R=20; if(gameMode==MODE_DIG){digTargetLines=10; for(int y=R-10;y<R;y++){int hole=rnd.nextInt(C); for(int x=0;x<C;x++)board[y][x]=(x==hole)?0:7;}} if(gameMode==MODE_SURVIVAL){dropMs=800;} if(gameMode==MODE_INVISIBLE){invisible=true;} particles.clear(); next=randomPiece(); if(pendingIRS!=0){next.s=rot(next.s,pendingIRS>0);next.rot=(pendingIRS>0)?1:3;pendingIRS=0;} spawn(); if(pendingIHS){pendingIHS=false;hold();} if (!solo) { for (BotPlayer bot : bots.values()) { bot.rnd = new Random(seed ^ bot.name.hashCode()); bot.board = new int[20][10]; bot.score = 0; bot.lines = 0; bot.level = 1; bot.over = false; bot.dropDelay = 600; bot.actionSpeed = 3; bot.iq = 5; bot.pendingGarbage = 0; bot.combo = -1; bot.b2b = 0; bot.badges = 0; bot.kos = 0; bot.garbageDueAt = 0; bot.canHold = true; bot.bagIndex = 7; bot.fillBag(); bot.next = bot.randomPiece(); bot.cur = bot.randomPiece(); bot.cur.x = (10 - bot.cur.s[0].length) / 2; bot.cur.y = 0; bot.cur.rot = 0; bot.lastTick = 0; bot.thinkUntil = 0; } }
        lastDrop=System.currentTimeMillis(); menu=false; tone(sReady); }
    private Piece randomPiece(){ if(bagIndex>=7) fillBag(); return new Piece(bag[bagIndex++]); }
    private void fillBag(){ for(int i=0;i<7;i++) bag[i]=i+1; for(int i=6;i>0;i--){int j=rnd.nextInt(i+1); int t=bag[i]; bag[i]=bag[j]; bag[j]=t;} bagIndex=0; }
    private void spawn(){ cur=next==null?randomPiece():next; next=randomPiece(); cur.x=(C-cur.s[0].length)/2; cur.y=0; cur.rot=0; cur.spin=false; cur.mini=false; onGround=false; canHold=true; if(!ok(cur,0,0,cur.s)){ if(!solo){ finishGame("被KO"); if(p2p!=null){ sendP2pState(); checkMultiFinish(); } } else { finishGame("游戏结束"); } return; } }
    private boolean move(int dx,int dy){ if(cur==null||!ok(cur,dx,dy,cur.s)) return false; cur.x+=dx; cur.y+=dy; if(dx!=0){ tone(sMove); if(onGround&&lockResets<MAX_LOCK_RESETS){ lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS; lockResets++; } } return true; }
    private boolean ok(Piece pc,int dx,int dy,int[][] s){ for(int r=0;r<s.length;r++) for(int x=0;x<s[r].length;x++) if(s[r][x]!=0){int xx=pc.x+x+dx, yy=pc.y+r+dy; if(xx<0||xx>=C||yy>=R) return false; if(yy>=0&&board[yy][xx]!=0)return false;} return true; }
    private void rotate(boolean cw){ if(cur==null)return; int[][] ns=rot(cur.s,cw); int newRot=(cur.rot+(cw?1:3))%4; int idx=cw?cur.rot*2:((cur.rot+3)%4)*2+1; int[][][] table=(cur.type==1)?SRS_I:SRS_JLSTZ; for(int ki=0;ki<table[idx].length;ki++){ int[] k=table[idx][ki]; if(ok(cur,k[0],k[1],ns)){cur.s=ns;cur.x+=k[0];cur.y+=k[1];cur.rot=newRot;cur.spin=(cur.type==3&&(k[0]!=0||k[1]!=0));cur.mini=false;if(onGround&&lockResets<MAX_LOCK_RESETS){lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS;lockResets++;}tone(sRotate);return;} } }
    private int[][] rot(int[][] s, boolean cw){ int n=s.length; int[][] a=new int[n][n]; for(int r=0;r<n;r++) for(int c=0;c<n;c++) if(cw)a[c][n-1-r]=s[r][c]; else a[n-1-c][r]=s[r][c]; return a; }
    private int ghostY(){ int y=cur.y; while(ok(cur,0,y-cur.y+1,cur.s)) y++; return y; }
    private void lock(){ boolean lockOut=false; for(int r=0;r<cur.s.length;r++) for(int x=0;x<cur.s[r].length;x++) if(cur.s[r][x]!=0){int yy=cur.y+r; if(yy<0) lockOut=true; else board[yy][cur.x+x]=cur.type;} if(lockOut){finishGame("游戏结束");return;} int spinType=checkTSpin(); int n=doClear(spinType); if(checkModeFinish()) return; if(n>0){areUntil=System.currentTimeMillis()+ARE_MS;clearing=true;}else{applyGarbage();spawn();} }
    private int checkTSpin(){ if(cur==null||cur.type!=3||!cur.spin)return 0; int cx=cur.x+1, cy=cur.y+1, n=0; int[][] pts={{cx-1,cy-1},{cx+1,cy-1},{cx-1,cy+1},{cx+1,cy+1}}; for(int[] q:pts){int x=q[0],y=q[1]; if(x<0||x>=C||y>=R||(y>=0&&board[y][x]!=0))n++;} if(n<3)return 0; return n==4?2:1; }
    private int doClear(int spinType){ int n=0; for(int y=R-1;y>=0;y--){ boolean full=true; for(int x=0;x<C;x++) if(board[y][x]==0){full=false;break;} if(full){ lineBurst(y); for(int yy=y;yy>0;yy--) board[yy]=board[yy-1].clone(); board[0]=new int[C]; n++; y++; }} if(n>0){ combo++; boolean difficult=n==4||spinType>=1; if(difficult)b2b++; else b2b=0; int base=new int[]{0,100,300,500,800}[n]; if(spinType==2) base=n==1?800:n==2?1200:1600; else if(spinType==1) base=n==1?200:n==2?400:600; int bonus=combo>0?combo*50:0; score+=(base+bonus)*level; lines+=n; level=lines/10+1; if(gameMode==MODE_SURVIVAL) dropMs=Math.max(40,1000-(level-1)*130); else dropMs=Math.max(80,1000-(level-1)*90); int garbage=garbageFor(n, spinType); if(garbage>0&&pendingGarbage>0){int cancel=Math.min(garbage,pendingGarbage); pendingGarbage-=cancel; garbage-=cancel;} if(!solo&&p2p!=null&&garbage>0)p2p.sendGarbage(garbage); fx((spinType>=1?(spinType==2?"T-SPIN ":"T-SPIN MINI "):(n==4?"TETRIS ":"CLEAR "))+n+(combo>1?" COMBO "+combo:""), difficult||n>=3); tone(difficult?sTetris:sClear); } else { combo=-1; } return n; }
    private int garbageFor(int n, int spinType){ int g=0; if(spinType==2)g=n==1?2:n==2?4:6; else if(spinType==1)g=n==1?0:n==2?1:2; else if(n==2)g=1; else if(n==3)g=2; else if(n==4)g=4; if(b2b>1&&(spinType>=1||n==4))g++; if(combo>1)g+=(combo<4?1:combo<6?2:3); g += badges/2; return g; }
    private void lineBurst(int row){ for(int i=0;i<18;i++) particles.add(new FxParticle(bx+rnd.nextFloat()*bw, by+(row+.5f)*cell, (rnd.nextFloat()-.5f)*bw*.7f, (rnd.nextFloat()-.5f)*90f, theme().blockFlash, 3+rnd.nextFloat()*5)); }
    private void applyGarbage(){ if(pendingGarbage>0&&garbageDueAt==0)garbageDueAt=System.currentTimeMillis()+1800; if(pendingGarbage<=0||System.currentTimeMillis()<garbageDueAt)return; while(pendingGarbage>0){ for(int y=0;y<R-1;y++) board[y]=board[y+1].clone(); int hole=rnd.nextInt(C); board[R-1]=new int[C]; for(int x=0;x<C;x++) board[R-1][x]=(x==hole)?0:7; pendingGarbage--; } garbageDueAt=0; fx("GARBAGE", true); tone(sGarbage); }
    private void hold(){ if(!canHold||cur==null||over)return; int t=cur.type; if(hold==0){hold=t; spawn();} else {cur=new Piece(hold); cur.x=3; cur.y=0; hold=t;} canHold=false; tone(sReady); }

    private void save(boolean toast) { if(!solo||gameMode!=MODE_CLASSIC||cur==null||over)return; try{ JSONObject o=new JSONObject(); o.put("board", arr(board)); o.put("cur", cur.json()); o.put("next", next.json()); o.put("hold", hold); o.put("score", score); o.put("lines", lines); o.put("level", level); o.put("drop", dropMs); o.put("bagIndex", bagIndex); JSONArray bagArr=new JSONArray(); for(int v:bag) bagArr.put(v); o.put("bag", bagArr); sp.edit().putString("save", o.toString()).apply(); }catch(Exception ignored){} }
    private void load(){ try{ String s=sp.getString("save", null); if(s==null)return; C=10; R=20; JSONObject o=new JSONObject(s); board=board(o.getJSONArray("board")); cur=new Piece(o.getJSONObject("cur")); next=new Piece(o.getJSONObject("next")); hold=o.optInt("hold"); score=o.optInt("score"); lines=o.optInt("lines"); level=o.optInt("level",1); dropMs=o.optLong("drop",1000); bagIndex=o.optInt("bagIndex",7); JSONArray bagArr=o.optJSONArray("bag"); if(bagArr!=null&&bagArr.length()==7){ for(int i=0;i<7;i++) bag[i]=bagArr.getInt(i); } gameMode=MODE_CLASSIC; finishText=""; modeStartAt=System.currentTimeMillis(); pausedTotalMs=0; pauseStartedAt=0; over=false; paused=false; settings=false; menu=false; }catch(Exception ignored){} }
    private void initSound(){ try{ toneGen=new ToneGenerator(AudioManager.STREAM_MUSIC, 45); sMove=ToneGenerator.TONE_PROP_BEEP; sRotate=ToneGenerator.TONE_PROP_ACK; sDrop=ToneGenerator.TONE_PROP_NACK; sClear=ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD; sTetris=ToneGenerator.TONE_CDMA_ABBR_ALERT; sGarbage=ToneGenerator.TONE_SUP_ERROR; sReady=ToneGenerator.TONE_PROP_PROMPT; }catch(Exception ignored){} }
    private void tone(int id){ if(toneGen!=null&&id!=0) toneGen.startTone(id, 70); }

    private JSONArray arr(int[][] b)throws Exception{ JSONArray a=new JSONArray(); for(int y=0;y<R;y++){JSONArray row=new JSONArray(); for(int x=0;x<C;x++) row.put(b[y][x]); a.put(row);} return a; }
    private int[][] board(JSONArray a)throws Exception{ int[][] b=new int[R][C]; for(int y=0;y<R;y++){JSONArray row=a.getJSONArray(y); for(int x=0;x<C;x++) b[y][x]=row.getInt(x);} return b; }

    private interface TextDone { void apply(String text); }
    private static class FxParticle { float x,y,vx,vy,size; int color; long born=System.currentTimeMillis(); FxParticle(float x,float y,float vx,float vy,int color,float size){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.color=color;this.size=size;} }
    private static class BotPlayer {
        String name;
        String hostKey;
        int[][] board = new int[20][10];
        Piece cur;
        Piece next;
        int hold = 0;
        int score = 0, lines = 0, level = 1;
        boolean over = false;
        long thinkUntil = 0;
        int dropDelay = 600;
        int actionSpeed = 3; // 1-10, higher = faster
        int iq = 5; // 1-10
        long lastTick = 0;
        int pendingGarbage = 0;
        int combo = -1, b2b = 0, badges = 0, kos = 0;
        long garbageDueAt = 0;
        boolean canHold = true;
        int bagIndex = 7;
        int[] bag = new int[7];
        Random rnd;
        BotPlayer(String name, String hostKey, long seed) {
            this.name = name; this.hostKey = hostKey;
            this.rnd = new Random(seed ^ name.hashCode());
            fillBag();
        }
        void fillBag() {
            for (int i = 0; i < 7; i++) bag[i] = i + 1;
            for (int i = 6; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int t = bag[i]; bag[i] = bag[j]; bag[j] = t;
            }
            bagIndex = 0;
        }
        Piece randomPiece() {
            if (bagIndex >= 7) fillBag();
            return new Piece(bag[bagIndex++]);
        }
    }

    private final java.util.Map<String, BotPlayer> bots = new java.util.HashMap<>();
    private int nextBotId = 1;

    private int botCount() { return bots.size(); }
    private int realPlayerCount() {
        int n = 1; // self
        for (PeerInfo pi : peerInfos.values()) {
            if (!pi.name.startsWith("BOT_")) n++;
        }
        return n;
    }

    private void hostStartGame() {
        if (!canHostStart()) return;
        syncStart();
    }

    private void addBot() {
        if (!isHost) return;
        if (playerCount() >= MAX_PLAYERS) return;
        String bname = "BOT_" + nextBotId++;
        String bhost = "bot_" + bname;
        long seed = System.currentTimeMillis();
        BotPlayer bot = new BotPlayer(bname, bhost, seed);
        bot.next = bot.randomPiece();
        bot.cur = bot.randomPiece();
        bot.cur.x = (10 - bot.cur.s[0].length) / 2;
        bot.cur.y = 0;
        bots.put(bhost, bot);
        PeerInfo pi = new PeerInfo(bname);
        pi.name = bname;
        peerInfos.put(bhost, pi);
        peerNames.put(bhost, bname);
        addChat("系统: " + bname + " 加入游戲");
    }

    private void removeBot() {
        if (!isHost || bots.isEmpty()) return;
        String key = bots.keySet().iterator().next();
        BotPlayer bot = bots.remove(key);
        peerInfos.remove(key);
        peerNames.remove(key);
        readyPeers.remove(key);
        addChat("系统: " + (bot != null ? bot.name : "电脑") + " 离开游戲");
    }

    private void clearBots() {
        bots.clear();
    }

    private void tickBots() {
        if (solo || bots.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (BotPlayer bot : bots.values()) {
            if (bot.over) continue;
            if (now < bot.thinkUntil) continue;
            // Dynamic difficulty: scale with real players' performance
            int avgLevel = bot.level;
            int realCount = 0;
            int totalLevel = bot.level;
            float realEfficiency = 0; // lines per minute
            long elapsedMin = Math.max(1, modeElapsedMs(now) / 60000);
            for (PeerInfo pi : peerInfos.values()) {
                if (!pi.name.startsWith("BOT_")) {
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
            bot.dropDelay = Math.max(120, Math.min(800, 900 - bot.level * 70 - bot.actionSpeed * 30));

            // Bot AI tick
            botTick(bot);
        }
    }

    private void botTick(BotPlayer bot) {
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
            BotDecision bestCur = evaluateBest(bot);
            BotDecision bestHold = null;
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
            BotDecision best = bestCur;
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
            bot.thinkUntil = now + Math.max(50, 500 - bot.actionSpeed * 40);
        }
    }

    private static class BotDecision {
        int x, rot;
        boolean hardDrop;
        double score = 0;
        BotDecision(int x, int rot, boolean hardDrop) { this.x = x; this.rot = rot; this.hardDrop = hardDrop; }
    }

    private BotDecision evaluateBest(BotPlayer bot) {
        if (bot.cur == null) return null;
        BotDecision best = null;
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
                    best = new BotDecision(tx, bot.cur.rot, true);
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

    private double evaluateBoard(BotPlayer bot) {
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

    private boolean botCanMove(BotPlayer bot, int dx, int dy) {
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

    private void botRotate(BotPlayer bot, boolean cw) {
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

    private boolean botRotateSRS(BotPlayer bot, boolean cw) {
        if (bot.cur == null) return false;
        int[][] ns = rot(bot.cur.s, cw);
        int newRot = (bot.cur.rot + (cw ? 1 : 3)) % 4;
        int idx = cw ? bot.cur.rot * 2 : ((bot.cur.rot + 3) % 4) * 2 + 1;
        int[][][] table = (bot.cur.type == 1) ? SRS_I : SRS_JLSTZ;
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

    private boolean botOk(BotPlayer bot, int dx, int dy, int[][] s) {
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

    private boolean botTspin(BotPlayer bot) {
        if (bot.cur == null || bot.cur.type != 3 || !bot.cur.spin) return false;
        int cx = bot.cur.x + 1, cy = bot.cur.y + 1;
        int n = 0;
        int[][] pts = {{cx-1,cy-1},{cx+1,cy-1},{cx-1,cy+1},{cx+1,cy+1}};
        for (int[] q : pts) {
            int x = q[0], y = q[1];
            if (x < 0 || x >= 10 || y >= 20 || (y >= 0 && bot.board[y][x] != 0)) n++;
        }
        return n >= 3;
    }

    private int botDoClear(BotPlayer bot) {
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

    private void botLock(BotPlayer bot) {
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
            bot.cur = null;
            return;
        }
        boolean spin = botTspin(bot);
        int cleared = botDoClear(bot);
        if (cleared > 0) {
            bot.combo++;
            boolean difficult = cleared == 4 || spin;
            if (difficult) bot.b2b++;
            else bot.b2b = 0;
            int base = new int[]{0, 100, 300, 500, 800}[cleared];
            if (spin) base = cleared == 1 ? 800 : cleared == 2 ? 1200 : 1600;
            int bonus = bot.combo > 0 ? bot.combo * 50 : 0;
            bot.score += (base + bonus) * bot.level;
            bot.lines += cleared;
            bot.level = 1 + bot.lines / 10;
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
            }
        }
    }

    private void sendBotStates() {
        if (p2p == null) return;
        for (BotPlayer bot : bots.values()) {
            if (bot.over) continue;
            p2p.publishBotState(bot.name, bot.score, bot.lines, bot.level, bot.over, bot.kos, bot.badges);
        }
    }


    private String encodeBoard() {
        StringBuilder sb = new StringBuilder(200);
        for (int r = 0; r < R; r++) {
            for (int col = 0; col < C; col++) {
                int v = board[r][col];
                sb.append(v == 0 ? '.' : (char)('0' + v));
            }
        }
        return sb.toString();
    }
    private static int[][] decodeBoard(String s) {
        if (s == null || s.length() < 200) return null;
        int[][] b = new int[20][10];
        for (int i = 0; i < 200; i++) {
            char ch = s.charAt(i);
            b[i / 10][i % 10] = (ch == '.' || ch < '0' || ch > '7') ? 0 : (ch - '0');
        }
        return b;
    }
    private static class PeerInfo {
        String name;
        int score, lines, level, kos, badges;
        boolean over;
        long lastUpdateMs;
        String via;
        boolean disconnected = false;
        long disconnectedAt = 0;
        int[][] board = null;
        PeerInfo(String name) { this.name = name; }
    }
    private static class Btn { String text; int action; RectF r; Btn(String t,int a,RectF rr){text=t;action=a;r=rr;} }
    private static class Piece { int type,x=3,y=0,rot=0; boolean spin=false, mini=false; int[][] s; Piece(int t){type=t; s=copy(SHAPES[t]); rot=0;} Piece(JSONObject o)throws Exception{type=o.getInt("type");x=o.getInt("x");y=o.getInt("y");rot=o.optInt("rot",0);JSONArray a=o.getJSONArray("s");s=new int[a.length()][a.length()];for(int r=0;r<a.length();r++){JSONArray row=a.getJSONArray(r);for(int c=0;c<row.length();c++)s[r][c]=row.getInt(c);}} JSONObject json()throws Exception{JSONObject o=new JSONObject();o.put("type",type);o.put("x",x);o.put("y",y);o.put("rot",rot);o.put("spin",spin);o.put("mini",mini);JSONArray a=new JSONArray();for(int[] rr:s){JSONArray row=new JSONArray();for(int v:rr)row.put(v);a.put(row);}o.put("s",a);return o;} static int[][] copy(int[][] m){int[][] n=new int[m.length][m.length];for(int i=0;i<m.length;i++)n[i]=m[i].clone();return n;} }
}
