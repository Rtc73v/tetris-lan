package com.echo.tetrislan;

import com.echo.tetrislan.net.DiscoveredRoom;
import com.echo.tetrislan.render.ColorUtil;
import com.echo.tetrislan.render.FxParticle;
import com.echo.tetrislan.render.LayoutState;
import com.echo.tetrislan.render.BoardRenderer;
import com.echo.tetrislan.render.FxRenderer;
import com.echo.tetrislan.render.InvisibleRenderer;
import com.echo.tetrislan.render.TrainingRenderer;
import com.echo.tetrislan.render.MenuRenderer;
import com.echo.tetrislan.render.Theme;
import com.echo.tetrislan.ui.Btn;
import com.echo.tetrislan.ui.TouchUtil;
import com.echo.tetrislan.net.PeerInfo;

import com.echo.tetrislan.core.Piece;
import com.echo.tetrislan.core.GameClock;
import com.echo.tetrislan.SoloModeController;
import com.echo.tetrislan.modes.InvisibleModeController;

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
import android.widget.Toast;

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
private static final int MODE_CLASSIC = 0, MODE_SPRINT = 1, MODE_ULTRA = 2, MODE_MARATHON = 3, MODE_INVISIBLE = 4, MODE_DIG = 5, MODE_TRAINING = 6;
    public static final String VERSION = "v1.26.15";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MenuRenderer menuRenderer = new MenuRenderer(p);
    private final BoardRenderer boardRenderer = new BoardRenderer(p);
    private final FxRenderer fxRenderer = new FxRenderer(p);
    private final InvisibleRenderer invisibleRenderer = new InvisibleRenderer();
    private final TrainingRenderer trainingRenderer = new TrainingRenderer(p);
    private final InputController inputController = new InputController(this);
    private final SoloModeController soloModeController = new SoloModeController(this);
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
    private String lastAttacker = "";
    private long garbageDueAt = 0;
    private String fxText = "";
    private long fxUntil = 0, shakeUntil = 0, flashUntil = 0;
    int soloStage = 1;
    private long rankingUntil = 0;
    private RectF soloOverRestartBtn = null;
    private RectF soloOverRetryBtn = null;
    private long lastAnyPeerUpdate = 0;
    private boolean networkFrozen = false;
    private ToneGenerator toneGen;
    private int sMove, sRotate, sDrop, sClear, sTetris, sGarbage, sReady;
    private long lastP2pSend = 0;
    private boolean running = true, menu = true, over = true, paused = false, settings = false, confirmQuit = false;
    private boolean newHighScore = false;
    boolean solo = true;
    private boolean canHold = true;
    int gameMode = MODE_CLASSIC;
    int classicSpeed = 0; // 0=普通(经典), 1=高速(原生存)
    private long modeStartAt = 0, pauseStartedAt = 0, pausedTotalMs = 0;
    private String finishText = "";
    private boolean invisible = false;
    private long invisibleFlashUntil = 0; // 全局闪烁截止时间（消除行时触发）
    private long invisibleNearUntil = 0;  // 落点附近闪烁截止时间（锁定时触发）
    private long invisiblePreviewUntil = 0; // 新方块生成后全板预览截止时间
    private long invisibleDangerUntil = 0;  // 危险高度全板警示截止时间
    private int invisibleNearCY = -1;      // 落点中心行
    private int invisibleNearCX = -1;      // 落点中心列
    private static final long INVISIBLE_FLASH_MS = 400;  // 消除闪现时长（fallback）
    private static final long INVISIBLE_NEAR_MS = 300;   // 落点附近闪现时长（fallback）
    private static final int INVISIBLE_NEAR_RANGE = 2;   // 落点附近显示范围（行数上下）
    private static final long INVISIBLE_EDGE_PERIOD_MS = 2200;
    private static final long INVISIBLE_EDGE_ON_MS = 450;
    private static final long INVISIBLE_GAP_PERIOD_MS = 3500;
    private static final long INVISIBLE_GAP_ON_MS = 700;
    private int digTargetLines = 0;
    private int digCleared = 0;
    int trainTech = 0;
    private int trainSuccess = 0;
    private String trainFailText = "";
    private long trainResetAt = 0;
    private Piece trainDemoPiece = null; // 训练模式目标落点
    private Piece trainDemoStartPiece = null; // 训练模式演示起手
    private int trainDemoStartX = 0, trainDemoStartY = 0, trainDemoTargetX = 0, trainDemoTargetY = 0, trainDemoRotDir = 1;
    private boolean[][] isGarbage;
    int menuPage = 0; // 0 main, 1 solo actions, 2 multiplayer actions, 3 new/load for solo mode, 4 training technique select
    int pendingStartMode = MODE_CLASSIC; // mode selected waiting for new/load choice
    private int statusBarH = 0;
    private int[][] board = new int[R][C];
    private Piece cur, next;
    int hold = 0, score = 0, lines = 0, level = 1;
    private long lastDrop = 0, dropMs = 1000, lastSave = 0;
    long dasMs = 167, arrMs = 33, softMs = 120;
    private static final long HARD_COOLDOWN_MS = 350;
    private static final long LOCK_DELAY_MS = 500, ARE_MS = 400;
    private static final int MAX_LOCK_RESETS = 15;
    boolean leftHeld = false, rightHeld = false, softHeld = false;
    private boolean hardReady = true;
    long leftStart = 0, rightStart = 0, arrAt = 0, softStart = 0, softAt = 0;
    private long lastHard = 0;
    private int pendingIRS = 0; // 0=none, 1=cw, -1=ccw
    private boolean pendingIHS = false;
    private long lockUntil = 0;
    private int lockResets = 0;
    private boolean onGround = false;
    private boolean lastActionWasRotate = false;
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
    private final LayoutState layoutState = new LayoutState();

    private static final int[] COLORS = {0,0xff00e5ff,0xffffeb3b,0xffe040fb,0xff69f0ae,0xffff5252,0xff448aff,0xffffab40};

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
        try {
            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) statusBarH = getResources().getDimensionPixelSize(resId);
        } catch (Exception ignored) {}
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
            if (!menu && !over && !paused && (solo ? !settings : true)) {
                if (solo && gameMode == MODE_ULTRA && GameClock.elapsed(now, modeStartAt, pausedTotalMs, paused, pauseStartedAt) >= Math.max(60000, 120000 - (soloStage - 1) * 15000)) {
                    if (score < soloStage * 5000) finishGame("时间到 未达标");
                }
                if (solo && gameMode == MODE_DIG && GameClock.elapsed(now, modeStartAt, pausedTotalMs, paused, pauseStartedAt) >= 180000) {
                    if (digCleared < digTargetLines) finishGame("时间到 未达标");
                }
                if (solo && gameMode == MODE_TRAINING && trainResetAt > 0) {
                    if (now >= trainResetAt) {
                        trainResetAt = 0;
                        setupTrainingBoard();
                        spawn();
                        next = trainNextPiece();
                    }
                } else if (over) {
                    // Timed modes can finish between piece locks.
                } else if (areUntil > 0) {
                    if (now >= areUntil) {
                        areUntil = 0; clearing = false;
                        applyGarbage();
                        spawn();
                    }
                    inputController.input(now);
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
                    inputController.input(now);
                }
                if (solo && now - lastSave > 5000) {
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
                    if (!menu && !over && lastAnyPeerUpdate > 0 && !peerNames.isEmpty() && now - lastAnyPeerUpdate > 30000) {
                        if (!networkFrozen) {
                            networkFrozen = true;
                            addChat("系统: 检测到网络不稳定");
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
        if (menu) {
            if (menuPage == 2 && p2p == null) {
                startP2p();
                p2pDiscovery = true;
                if ("P2P未启动".equals(p2pStatus)) p2pStatus = "搜索房间中...";
            }
            menuRenderer.drawMenu(c, w, h, theme(), VERSION, menuPage,
                modeName(), sp.contains(saveKey()),
                roomName, playerName, p2pStatus, lastRoomName,
                isHost, selfReady,
                readyCount(), playerCount(), botCount(), canHostStart(),
                p2p, p2pDiscovery,
                foundRooms, peerNames, readyPeers, chat,
                pendingStartAt, MAX_PLAYERS);
            return;
        }
        layoutGame(w, h);
        drawTop(c);
        if (invisible && !over) {
            int stack = InvisibleModeController.maxStackHeight(board, R);
            if (stack >= 15) invisibleDangerUntil = now + 1500;
        }
        boardRenderer.drawBoard(c, layoutState, theme(), board, cur, ghostY(), invisible, over, now,
            invisibleFlashUntil, invisiblePreviewUntil, invisibleDangerUntil,
            invisibleNearUntil, invisibleNearCY, invisibleNearCX, level,
            pendingGarbage, garbageDueAt, score, lines);
        if (invisible && !over) {
            boolean showAll = !invisible || over || now < invisibleFlashUntil || now < invisiblePreviewUntil || now < invisibleDangerUntil;
            if (!showAll) {
                if (level >= 6) invisibleRenderer.drawInvisibleEdge(c, now, board, p, layoutState, theme());
                if (level >= 8) invisibleRenderer.drawInvisibleGaps(c, now, board, p, layoutState);
            }
        }
        if (solo && gameMode == MODE_TRAINING && trainDemoPiece != null && !over && !paused) {
            trainingRenderer.drawTrainingDemo(c, now, layoutState, theme(),
                trainDemoStartPiece, trainDemoPiece,
                trainDemoStartX, trainDemoStartY,
                trainDemoTargetX, trainDemoTargetY,
                trainDemoRotDir, trainTech);
        }
        drawSide(c);
        fxRenderer.drawParticles(c, particles, now);
        drawBtns(c);
        fxRenderer.drawFxOverlay(c, w, h, theme(), now, flashUntil, fxUntil, fxText);
        if (over) {
            if (!solo && rankingUntil > 0 && !rankingLines.isEmpty()) {
                drawRankingOverlay(c, w, h);
            } else if (solo && !menu && gameMode == MODE_TRAINING) {
                // Auto-reset handled in game loop, show minimal overlay
            } else if (solo && !menu && gameMode != MODE_CLASSIC) {
                // 关卡模式：只展示按钮，不画覆盖文字避免重叠
            } else {
                drawCenter(c, finishText.isEmpty() ? "游戏结束" : finishText, "点设置或主界面");
            }
            if (solo && !menu && gameMode != MODE_CLASSIC) {
                float btnW = Math.max(108, w * 0.31f), bhBtn = btnW * 0.56f;
                float cy = h/2f + 130;
                float gap = 16;
                float totalW = btnW * 2 + gap;
                float lx = w/2f - totalW/2;
                float rx = w/2f + gap/2;
                // 最高分
                String hsKey2 = "hs_" + gameMode + "_0";
                int hs = sp.getInt(hsKey2, 0);
                p.setColor(theme().score); p.setTextSize(36); p.setTextAlign(Paint.Align.CENTER);
                String hsText = "最高分 " + hs + (newHighScore ? " 新纪录!" : "");
                c.drawText(hsText, w/2f, cy - bhBtn/2 - 70, p);
                soloOverRestartBtn = new RectF(lx, cy - bhBtn/2, lx + btnW, cy + bhBtn/2);
                soloOverRetryBtn = new RectF(rx, cy - bhBtn/2, rx + btnW, cy + bhBtn/2);
                p.setColor(theme().btn); p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(soloOverRestartBtn, 16, 16, p);
                c.drawRoundRect(soloOverRetryBtn, 16, 16, p);
                p.setColor(theme().text); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(32);
                c.drawText("从头再来", soloOverRestartBtn.centerX(), soloOverRestartBtn.centerY() + 12, p);
                c.drawText("本关重试", soloOverRetryBtn.centerX(), soloOverRetryBtn.centerY() + 12, p);
            } else if (solo && !menu && gameMode == MODE_CLASSIC && over) {
                // 经典模式：在结束界面显示最高分
                String hsKey2 = "hs_" + gameMode + "_" + classicSpeed;
                int hs = sp.getInt(hsKey2, 0);
                p.setColor(theme().score); p.setTextSize(30); p.setTextAlign(Paint.Align.CENTER);
                String hsText = "最高分 " + hs + (newHighScore ? " 新纪录!" : "");
                c.drawText(hsText, w/2f, h/2f + 140, p);
                soloOverRestartBtn = null;
                soloOverRetryBtn = null;
            } else {
                soloOverRestartBtn = null;
                soloOverRetryBtn = null;
            }
        }
        if (paused && !over) drawCenter(c, "暂停", "点暂停继续");
        if (settings) drawSettings(c, w, h);
    }

    private void layoutGame(int w, int h) {
        float top = 120 + statusBarH, bottomControls = h - 180;
        bw = Math.min(w * 0.74f, (bottomControls - top) * C / (float)R);
        bh = bw * R / C;
        bx = 8; by = top;
        cell = bw / C;
        btns.clear();
        float topW = Math.max(120, w * 0.28f), topH = 52;
        topBtnY = 48 + statusBarH;
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
        layoutState.bx = bx; layoutState.by = by; layoutState.bw = bw; layoutState.bh = bh;
        layoutState.cell = cell; layoutState.sideX = sideX; layoutState.sideW = sideW;
    }

    private void addBtn(String text, int action, float cx, float cy, float w, float h) { btns.add(new Btn(text, action, new RectF(cx-w/2, cy-h/2, cx+w/2, cy+h/2))); }

    private void drawTop(Canvas c) {
        if (!solo || menu) return;
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(28); p.setColor(theme().textMuted);
        float sy = statusBarH + 8;
        String stageInfo = "";
        if (gameMode == MODE_SPRINT) stageInfo = "S" + soloStage + " 目标" + (40 * soloStage) + "行";
        else if (gameMode == MODE_ULTRA) stageInfo = "S" + soloStage + " 目标" + (soloStage * 5000) + "分";
        else if (gameMode == MODE_DIG) stageInfo = "S" + soloStage + " 目标" + (10 * soloStage) + "行";
        else if (gameMode == MODE_MARATHON) stageInfo = "S" + soloStage + " 目标" + (150 * soloStage) + "行";
        else if (gameMode == MODE_TRAINING) stageInfo = "练习: " + trainTechName();
        if (!stageInfo.isEmpty()) c.drawText(stageInfo, 12, sy + 100, p);
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
        c.drawText(solo ? "模式" : "对手", cx, y, p);
        if (solo) {
            p.setColor(theme().score); p.setTextSize(32); c.drawText(modeName(), cx, y+38, p);
            p.setColor(theme().text); p.setTextSize(24);
            String progress = modeProgress();
            String[] lines = progress.split("\n");
            float lineH = 28;
            for (int i = 0; i < lines.length; i++) {
                c.drawText(lines[i], cx, y + 68 + i * lineH, p);
            }
            y += 68 + lines.length * lineH + 8;
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
    private void drawRankingOverlay(Canvas c, int w, int h) {
        int n = rankingLines.size();
        if (n == 0) return;
        float lineH = 44;
        float pad = 24;
        float boxH = n * lineH + pad * 2;
        float top = h * 0.22f;
        p.setColor(0xdd000000);
        c.drawRoundRect(new RectF(w * 0.08f, top, w * 0.92f, top + boxH), 24, 24, p);
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            String txt = rankingLines.get(i);
            if (i == 0) {
                p.setColor(0xffffff00); p.setTextSize(40);
            } else {
                p.setColor(Color.WHITE); p.setTextSize(32);
            }
            c.drawText(txt, w / 2f, top + pad + i * lineH + 28, p);
        }
        p.setColor(0xffaaaaaa); p.setTextSize(28);
        c.drawText("点设置或主界面", w / 2f, top + boxH - 10, p);
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
            menuRenderer.drawMenuButton(c,  "继续游戏", w*.16f, h*.26f, w*.84f, h*.33f, false, theme());
            menuRenderer.drawMenuButton(c,  "读取存档", w*.16f, h*.35f, w*.84f, h*.42f, false, theme());
            menuRenderer.drawMenuButton(c,  "手动保存", w*.16f, h*.44f, w*.84f, h*.51f, false, theme());
        } else {
            menuRenderer.drawMenuButton(c,  "继续游戏", w*.16f, h*.32f, w*.84f, h*.40f, false, theme());
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
            menuRenderer.drawMenuButton(c,  "-", w*0.60f, y, w*0.60f+btnW, y+rowH*0.85f, false, theme());
            menuRenderer.drawMenuButton(c,  "+", w*0.74f, y, w*0.74f+btnW, y+rowH*0.85f, false, theme());
        }
        menuRenderer.drawMenuButton(c,  "返回主界面", w*.16f, h*.82f, w*.84f, h*.89f, false, theme());
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
            menuRenderer.drawMenuButton(c,  "保存", w/2f-qbW*1.5f-gap, btnY, w/2f-qbW*.5f-gap, btnY+btnH, false, theme());
            menuRenderer.drawMenuButton(c,  "不保存", w/2f-qbW/2f, btnY, w/2f+qbW/2f, btnY+btnH, false, theme());
            menuRenderer.drawMenuButton(c,  "取消", w/2f+qbW*.5f+gap, btnY, w/2f+qbW*1.5f+gap, btnY+btnH, false, theme());
        }
    }













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
            if (TouchUtil.hit(x,y,w*.14f,h*.30f,w*.86f,h*.39f)) { solo=true; menuPage=1; return true; }
            if (TouchUtil.hit(x,y,w*.14f,h*.43f,w*.86f,h*.52f)) { solo=false; gameMode=MODE_CLASSIC; invisible=false; menuPage=2; return true; }
            return true;
        }
        if (menuPage == 1) {
            float btnH = h * 0.058f, gap = h * 0.010f, sy = h * 0.28f;
            if (TouchUtil.hit(x,y,w*.08f,sy,w*.46f,sy+btnH)) { soloModeController.openSoloMode(MODE_CLASSIC, 0); return true; }
            if (TouchUtil.hit(x,y,w*.54f,sy,w*.92f,sy+btnH)) { soloModeController.openSoloMode(MODE_CLASSIC, 1); return true; }
            if (TouchUtil.hit(x,y,w*.08f,sy+btnH+gap,w*.46f,sy+2*btnH+gap)) { soloModeController.openSoloMode(MODE_SPRINT, 0); return true; }
            if (TouchUtil.hit(x,y,w*.54f,sy+btnH+gap,w*.92f,sy+2*btnH+gap)) { soloModeController.openSoloMode(MODE_ULTRA, 0); return true; }
            if (TouchUtil.hit(x,y,w*.08f,sy+2*(btnH+gap),w*.46f,sy+3*btnH+2*gap)) { soloModeController.openSoloMode(MODE_MARATHON, 0); return true; }
            if (TouchUtil.hit(x,y,w*.54f,sy+2*(btnH+gap),w*.92f,sy+3*btnH+2*gap)) { soloModeController.openSoloMode(MODE_INVISIBLE, 0); return true; }
            if (TouchUtil.hit(x,y,w*.08f,sy+3*(btnH+gap),w*.46f,sy+4*btnH+3*gap)) { soloModeController.openSoloMode(MODE_DIG, 0); return true; }
            if (TouchUtil.hit(x,y,w*.54f,sy+3*(btnH+gap),w*.92f,sy+4*btnH+3*gap)) { soloModeController.openSoloMode(MODE_TRAINING, 0); return true; }
            float by = sy + 4*(btnH+gap) + gap*2;
            if (TouchUtil.hit(x,y,w*.14f,by,w*.86f,by+btnH)) { menuPage=0; return true; }
            return true;
        }
        if (menuPage == 4) {
            float btnH = h * 0.070f, gap = h * 0.018f, sy = h * 0.32f;
            for (int i = 0; i < 3; i++) {
                float ty = sy + i * (btnH + gap);
                if (TouchUtil.hit(x, y, w*.14f, ty, w*.86f, ty + btnH)) {
                    trainTech = i; soloModeController.startMode(MODE_TRAINING); return true;
                }
            }
            float backY = sy + 3 * (btnH + gap) + gap * 2;
            if (TouchUtil.hit(x, y, w*.14f, backY, w*.86f, backY + btnH)) { menuPage = 1; return true; }
            return true;
        }
        if (menuPage == 3) {
            if (TouchUtil.hit(x,y,w*.14f,h*.30f,w*.86f,h*.39f)) { soloModeController.startMode(pendingStartMode); return true; }
            if (TouchUtil.hit(x,y,w*.14f,h*.43f,w*.86f,h*.52f)) { if (sp.contains(saveKey())) load(); return true; }
            if (TouchUtil.hit(x,y,w*.14f,h*.56f,w*.86f,h*.65f)) { menuPage=1; return true; }
            return true;
        }
        if (p2p == null || p2pDiscovery) {
            if (TouchUtil.hit(x,y,w*.08f,h*.31f,w*.46f,h*.39f)) { createRoom(); return true; }
            if (TouchUtil.hit(x,y,w*.54f,h*.31f,w*.92f,h*.39f)) { askRoom(); return true; }
            if (lastRoomName != null) {
                if (TouchUtil.hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { reconnectLastRoom(); return true; }
                if (TouchUtil.hit(x,y,w*.54f,h*.42f,w*.92f,h*.50f)) { askName(); return true; }
                if (TouchUtil.hit(x,y,w*.15f,h*.53f,w*.85f,h*.61f)) { stopP2p(); menuPage=0; return true; }
            } else {
                if (TouchUtil.hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { askName(); return true; }
                if (TouchUtil.hit(x,y,w*.15f,h*.53f,w*.85f,h*.61f)) { stopP2p(); menuPage=0; return true; }
            }
            // 点击发现的房间卡片加入
            int nf = foundRooms.size();
            float cardY = h*0.612f, cardH = h*0.058f, cardGap = h*0.008f, cardL = w*0.06f, cardR = w*0.94f;
            int limit = Math.min(nf, 4);
            for (int i = 0; i < limit; i++) {
                float cy = cardY + i * (cardH + cardGap);
                if (TouchUtil.hit(x,y,cardL,cy,cardR,cy+cardH)) {
                    DiscoveredRoom dr = foundRooms.get(i);
                    joinRoom(dr.room, dr.host);
                    return true;
                }
            }
        } else {
            if (TouchUtil.hit(x,y,w*.08f,h*.31f,w*.46f,h*.39f)) {
                if (isHost) { if (canHostStart()) hostStartGame(); return true; }
                else { toggleReady(); return true; }
            }
            if (TouchUtil.hit(x,y,w*.54f,h*.31f,w*.92f,h*.39f)) { askChat(); return true; }
            if (TouchUtil.hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { askName(); return true; }
            if (TouchUtil.hit(x,y,w*.54f,h*.42f,w*.92f,h*.50f)) { leaveRoom(); return true; }
            if (isHost) {
                int bc = botCount();
                if (TouchUtil.hit(x,y,w*.08f,h*.53f,w*.46f,h*.61f)) {
                    if (playerCount() < MAX_PLAYERS) { addBot(); return true; }
                }
                if (TouchUtil.hit(x,y,w*.54f,h*.53f,w*.92f,h*.61f)) {
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
            if (TouchUtil.hit(x,y,bx1,btnY,bx1+btnW,btnY+btnH)) { save(true); confirmQuit=false; goMenu(); return true; }
            if (TouchUtil.hit(x,y,bx2,btnY,bx2+btnW,btnY+btnH)) { confirmQuit=false; goMenu(); return true; }
            if (TouchUtil.hit(x,y,bx3,btnY,bx3+btnW,btnY+btnH)) { confirmQuit=false; return true; }
            return true;
        }
        if (solo && TouchUtil.hit(x,y,w*.16f,h*.26f,w*.84f,h*.33f)) { settings=false; setPaused(false); return true; }
        if (solo && TouchUtil.hit(x,y,w*.16f,h*.35f,w*.84f,h*.42f)) { load(); settings=false; return true; }
        if (solo && TouchUtil.hit(x,y,w*.16f,h*.44f,w*.84f,h*.51f)) { save(true); return true; }
        if (!solo && TouchUtil.hit(x,y,w*.16f,h*.32f,w*.84f,h*.40f)) { settings=false; return true; }
        // DAS/ARR/软降 点击
        float rowH = h * 0.068f;
        float sy = h * 0.55f;
        float btnW = w * 0.12f;
        for (int i = 0; i < 3; i++) {
            float rowY = sy + i * rowH;
            if (TouchUtil.hit(x,y,w*.60f,rowY,w*.60f+btnW,rowY+rowH*0.85f)) {
                if (i==0) { dasMs = Math.max(0, dasMs - 10); sp.edit().putLong("das_ms", dasMs).apply(); }
                else if (i==1) { arrMs = Math.max(0, arrMs - 2); sp.edit().putLong("arr_ms", arrMs).apply(); }
                else { softMs = Math.max(10, softMs - 10); sp.edit().putLong("soft_ms", softMs).apply(); }
                return true;
            }
            if (TouchUtil.hit(x,y,w*.74f,rowY,w*.74f+btnW,rowY+rowH*0.85f)) {
                if (i==0) { dasMs = Math.min(500, dasMs + 10); sp.edit().putLong("das_ms", dasMs).apply(); }
                else if (i==1) { arrMs = Math.min(200, arrMs + 2); sp.edit().putLong("arr_ms", arrMs).apply(); }
                else { softMs = Math.min(500, softMs + 10); sp.edit().putLong("soft_ms", softMs).apply(); }
                return true;
            }
        }
        if (TouchUtil.hit(x,y,w*.16f,h*.82f,w*.84f,h*.89f)) {
            if (!over && !menu) { confirmQuit = true; return true; }
            goMenu(); return true;
        }
        return true;
    }
    

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
                if (networkFrozen) { networkFrozen = false; addChat("系统: 网络恢复，游戏继续"); }
                if (!acceptPeer(host, name)) return;
                PeerInfo pi = peerInfos.get(host);
                if (pi == null) { pi = new PeerInfo(name); peerInfos.put(host, pi); }
                pi.name = name; pi.score = score; pi.lines = lines; pi.level = level;
                pi.over = over; pi.kos = kos; pi.badges = badges;
                pi.lastUpdateMs = System.currentTimeMillis(); pi.via = via;
                pi.disconnected = false; pi.disconnectedAt = 0;
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
            @Override public void onGarbage(String fromName, int rows) { pendingGarbage += rows; lastAttacker = fromName; garbageDueAt = System.currentTimeMillis() + 1800; p2pStatus = fromName + " 送了 " + rows + " 行"; tone(sGarbage); fx("WARNING +" + rows, true); }
            @Override public void onKO(String host, String targetName, String killerName) {
                if (killerName.equals(playerName)) kos++;
                for (PeerInfo pi : peerInfos.values()) { if (pi.name.equals(killerName)) { pi.kos++; break; } }
                for (BotPlayer bot : bots.values()) { if (bot.name.equals(killerName)) { bot.kos++; break; } }
            }
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
            @Override public void onBotState(String host, String botName, int score, int lines, int level, boolean over, int kos, int badges, String board) {
                PeerInfo pi = peerInfos.get(host);
                if (pi == null) { pi = new PeerInfo(botName); peerInfos.put(host, pi); }
                pi.name = botName; pi.score = score; pi.lines = lines; pi.level = level;
                pi.over = over; pi.kos = kos; pi.badges = badges;
                if (board != null && !board.isEmpty()) pi.board = decodeBoard(board);
                pi.lastUpdateMs = System.currentTimeMillis(); pi.via = "bot";
                lastAnyPeerUpdate = System.currentTimeMillis();
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
        new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert)
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
        new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setView(input)
            .setPositiveButton("确定", (d, which) -> done.apply(input.getText().toString()))
            .setNegativeButton("取消", null).show();
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





    private void act(int a) {
        if (a==8) { settings=true; if (solo) setPaused(true); releaseAction(activeAction); return; }
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
        if (gameMode == MODE_TRAINING) return "T-Spin训练 " + trainTechName();
        return gameMode==MODE_CLASSIC ? (classicSpeed==1 ? "经典 高速" : "经典 普通") : "经典模式";
    }

    private String trainTechName() {
        switch (trainTech) {
            case 0: return "T-Spin Mini";
            case 1: return "T-Spin 单";
            case 2: return "T-Spin 双";
            default: return "T-Spin";
        }
    }

    private String modeProgress() {
        long elapsed = GameClock.elapsed(System.currentTimeMillis(), modeStartAt, pausedTotalMs, paused, pauseStartedAt);
        if (gameMode == MODE_SPRINT) return Math.min(lines, 40 * soloStage) + "/" + (40 * soloStage) + "行 " + formatTime(elapsed);
        if (gameMode == MODE_ULTRA) { long limit = Math.max(60000, 120000 - (soloStage - 1) * 15000); return "剩余 " + formatTime(limit - elapsed) + " 目标 " + (soloStage * 5000) + "分"; }
        if (gameMode == MODE_MARATHON) return Math.min(lines, 150 * soloStage) + "/" + (150 * soloStage) + "行";
        if (gameMode == MODE_INVISIBLE) {
            String assist = level >= 8 ? "边缘+空缺" : (level >= 6 ? "边缘" : "无");
            return "行 " + lines + " " + formatTime(elapsed) + "\n显形 " + String.format(Locale.US, "%.1f", InvisibleModeController.invisibleRevealMs(level, dropMs, board, R)/1000f) + "s 辅助 " + assist;
        }
        if (gameMode == MODE_DIG) return digCleared + "/" + digTargetLines + "行 " + formatTime(elapsed);
        if (gameMode == MODE_TRAINING) return "成功 " + trainSuccess + " 次\n" + TrainingRenderer.trainDemoInputText(trainTech) + (trainFailText.isEmpty() ? "" : "\n" + trainFailText);
        return "时间 " + formatTime(elapsed) + (classicSpeed==1 ? " 高速" : "");
    }

    private void finishGame(String text) {
        over = true;
        finishText = text;
        releaseAllActions();
        // 保存最高分
        newHighScore = false;
        String hsKey = "hs_" + gameMode + "_" + (gameMode == MODE_CLASSIC ? classicSpeed : 0);
        int prev = sp.getInt(hsKey, 0);
        if (score > prev) { sp.edit().putInt(hsKey, score).apply(); newHighScore = true; }
        if (!solo && p2p != null) {
            sendP2pState();
        }
    }

    private void notifyKO(String targetName, String killerName) {
        if (p2p != null) p2p.sendKO(targetName, killerName);
        for (BotPlayer bot : bots.values()) { if (bot.name.equals(killerName)) { bot.kos++; break; } }
        for (PeerInfo pi : peerInfos.values()) { if (pi.name.equals(killerName)) { pi.kos++; break; } }
    }

    private void checkMultiFinish() {
        if (solo || p2p == null) return;
        // 检查是否有真人peer
        boolean hasHumanPeer = false;
        for (PeerInfo pi : peerInfos.values()) {
            if (pi.name != null && !pi.name.startsWith("BOT_")) {
                hasHumanPeer = true;
                break;
            }
        }
        // 没有真人玩家时，使用原始逻辑（剩1人或全部结束则结算）
        if (!hasHumanPeer) {
            int alive = over ? 0 : 1;
            for (PeerInfo pi : peerInfos.values()) {
                if (!pi.over) alive++;
            }
            if (alive <= 1 && rankingUntil == 0) {
                if (!over) finishGame("获胜");
                showRankingAndReturn();
            }
            return;
        }
        // 有真人玩家时，只统计真人存活
        boolean selfIsHuman = playerName == null || !playerName.startsWith("BOT_");
        int humanAlive = (selfIsHuman && !over) ? 1 : 0;
        for (PeerInfo pi : peerInfos.values()) {
            if (pi.name != null && !pi.name.startsWith("BOT_") && !pi.over) {
                humanAlive++;
            }
        }
        if (humanAlive <= 0 && rankingUntil == 0) {
            showRankingAndReturn();
        } else if (humanAlive == 1 && !over && selfIsHuman && rankingUntil == 0) {
            finishGame("获胜");
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
            // 清理断线真人（超30秒）
            if (pi.disconnected && now - pi.disconnectedAt > 30000) {
                it.remove();
                peerNames.remove(e.getKey());
                readyPeers.remove(e.getKey());
                addChat("系统: " + pi.name + " 超时未重连，已移除");
                continue;
            }
            // 清理已结束的Bot（无独立网络连接，留在peerInfos仅用于显示）
            if (pi.name != null && pi.name.startsWith("BOT_") && pi.over) {
                String botKey = e.getKey();
                // 确认Bot实例已不存在才清理
                if (!bots.containsKey(botKey)) {
                    it.remove();
                    peerNames.remove(botKey);
                    readyPeers.remove(botKey);
                }
            }
        }
    }
    private java.util.List<String> rankingLines = new java.util.ArrayList<>();
    private void showRankingAndReturn() {
        long now = System.currentTimeMillis();
        java.util.List<PeerInfo> all = new java.util.ArrayList<>();
        PeerInfo self = new PeerInfo(playerName);
        self.score = score; self.lines = lines; self.kos = kos; self.badges = badges; self.over = over;
        all.add(self);
        for (PeerInfo pi : peerInfos.values()) all.add(pi);
        all.sort((a,b) -> {
            if (a.over != b.over) return a.over ? 1 : -1;
            if (b.score != a.score) return b.score - a.score;
            if (b.kos != a.kos) return b.kos - a.kos;
            if (b.badges != a.badges) return b.badges - a.badges;
            return b.lines - a.lines;
        });
        rankingLines.clear();
        rankingLines.add("对局结束 - 排名");
        for (int i=0;i<all.size();i++) {
            PeerInfo pi = all.get(i);
            String status = pi.over ? "[KO]" : "";
            rankingLines.add("#" + (i+1) + " " + pi.name + " " + status + "  " + pi.score + "分  " + pi.lines + "行  K" + pi.kos + " B" + pi.badges);
        }
        finishText = "对局结束";
        rankingUntil = now + 5000;
    }
    private void returnToRoom() {
        menu = true; menuPage = 2; over = false; paused = false; settings = false;
        finishText = ""; rankingUntil = 0; rankingLines.clear();
        selfReady = false; readyPeers.clear(); peerInfos.clear();
        score = 0; lines = 0; level = 1; dropMs = 1000; pendingGarbage = 0;
        combo = -1; b2b = 0; badges = 0; kos = 0;
        gameMode = MODE_CLASSIC; invisible = false; soloStage = 1;
        trainTech = 0; trainSuccess = 0; trainDemoPiece = null; trainDemoStartPiece = null;
        clearBots();
        if (p2p != null) p2p.publishState(0, 0, 1, false, 0, 0, encodeBoard());
        if (isHost && p2p != null) p2p.sendReturnLobby();
    }
    private boolean checkModeFinish() {
        if (!solo || gameMode == MODE_TRAINING) return false;
        if (gameMode == MODE_SPRINT && lines >= 40 * soloStage) { advanceStage(); return true; }
        if (gameMode == MODE_MARATHON && lines >= 150 * soloStage) { advanceStage(); return true; }
        if (gameMode == MODE_DIG && digCleared >= digTargetLines) { advanceStage(); return true; }
        if (gameMode == MODE_ULTRA && score >= soloStage * 5000) { advanceStage(); return true; }
        return false;
    }

    private void advanceStage() {
        soloStage++;
        fx("STAGE " + soloStage, true);
        tone(sReady);
        if (gameMode == MODE_DIG) {
            int increment = 10; // 每关增量 10 行垃圾
            int totalGarbage = Math.min(20, 10 * soloStage); // 累计垃圾行数上限 20
            digTargetLines = totalGarbage;
            // 只检查增量行（顶部 increment 行必须为空，为现有方块留空间）
            for (int y = 0; y < increment; y++) {
                for (int x = 0; x < C; x++) {
                    if (board[y][x] != 0) {
                        finishGame("被垃圾行撑爆");
                        return;
                    }
                }
            }
            // 上移现有内容（只移增量，不清盘）
            for (int y = 0; y < R - increment; y++) {
                board[y] = board[y + increment].clone();
                isGarbage[y] = isGarbage[y + increment].clone();
            }
            // 底部填充新垃圾行（增量）
            for (int y = R - increment; y < R; y++) {
                int hole = rnd.nextInt(C);
                for (int x = 0; x < C; x++) {
                    board[y][x] = (x == hole) ? 0 : 7;
                    isGarbage[y][x] = (x != hole);
                }
            }
            digCleared = 0;
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
            return;
        }
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
        if (gameMode != MODE_INVISIBLE) invisible = false;
        modeStartAt = System.currentTimeMillis();
        // === 逐关递进难度 ===
        int stageGarbage = 0;
        if (gameMode == MODE_SPRINT) {
            dropMs = Math.max(500, 1000 - (soloStage - 1) * 150);
            stageGarbage = Math.min(5, soloStage - 1);
        } else if (gameMode == MODE_MARATHON) {
            dropMs = Math.max(120, 1000 - (soloStage - 1) * 100);
            stageGarbage = Math.min(5, soloStage - 1);
        } else if (gameMode == MODE_ULTRA) {
            dropMs = Math.max(500, 1000 - (soloStage - 1) * 120);
        }
        // 底部填充障碍行
        for (int i = 0; i < stageGarbage; i++) {
            int hole = rnd.nextInt(C);
            for (int x = 0; x < C; x++)
                board[R - 1 - i][x] = (x == hole) ? 0 : 7;
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

    void start() { start(System.currentTimeMillis()); }

    private void setupTrainingBoard() {
        board = new int[R][C]; score = 0; lines = 0; level = 1; hold = 0; pendingGarbage = 0;
        combo = -1; b2b = 0; canHold = true; bagIndex = 7; over = false;
        if (trainTech < 0 || trainTech > 2) trainTech = 0;
        switch (trainTech) {
            case 0: // T-Spin Mini：左向小口，逆旋卡入，只占三角。
                fillRowExcept(19, 5);
                fillRowExcept(18, 4, 5, 6);
                board[17][6] = 7;
                setDemoTPiece(4, 17, 3, 3, 0, -1);
                break;
            case 1: // T-Spin Single：右移到1行槽，顺旋后消1行。
                fillRowExcept(19, 4);
                fillRowExcept(18, 0, 3, 4, 5);
                fillRowExcept(17, 4);
                board[15][3] = 7;
                setDemoTPiece(3, 15, 2, 2, 0, 1);
                break;
            case 2: // T-Spin Double：左移进入2行深槽，顺旋后消2行。
                fillRowExcept(19, 0);
                fillRowExcept(18, 0, 4);
                fillRowExcept(17, 0, 3, 4, 5);
                fillRowExcept(16, 4);
                fillRowExcept(15, 3, 4, 5);
                board[14][3] = 7;
                setDemoTPiece(3, 14, 2, 4, 0, 1);
                break;
        }
        next = trainNextPiece();
    }
    private void fillRow(int y) { for (int x = 0; x < C; x++) board[y][x] = 7; }
    private void fillRowExcept(int y, int... gaps) {
        for (int x = 0; x < C; x++) {
            boolean gap = false;
            for (int g : gaps) if (x == g) { gap = true; break; }
            board[y][x] = gap ? 0 : 7;
        }
    }
    private int[][] tShape(int rot) {
        switch (rot & 3) {
            case 1: return new int[][]{{0,1,0},{0,1,1},{0,1,0}};
            case 2: return new int[][]{{0,0,0},{1,1,1},{0,1,0}};
            case 3: return new int[][]{{0,1,0},{1,1,0},{0,1,0}};
            default: return new int[][]{{0,1,0},{1,1,1},{0,0,0}};
        }
    }
    private int[][] iVertical() { return new int[][]{{0,1,0,0},{0,1,0,0},{0,1,0,0},{0,1,0,0}}; }
    private void setDemoTPiece(int targetX, int targetY, int targetRot, int startX, int startY, int rotDir) {
        trainDemoPiece = new Piece(3); trainDemoPiece.s = tShape(targetRot); trainDemoPiece.rot = targetRot; trainDemoPiece.x = targetX; trainDemoPiece.y = targetY;
        int startRot = (targetRot + (rotDir > 0 ? 3 : 1)) & 3;
        trainDemoStartPiece = new Piece(3); trainDemoStartPiece.s = tShape(startRot); trainDemoStartPiece.rot = startRot; trainDemoStartPiece.x = startX; trainDemoStartPiece.y = startY;
        trainDemoStartX = startX; trainDemoStartY = startY; trainDemoTargetX = targetX; trainDemoTargetY = targetY; trainDemoRotDir = rotDir;
    }
    private void setDemoIPiece(int x, int y) { trainDemoPiece = new Piece(1); trainDemoPiece.s = iVertical(); trainDemoPiece.rot = 1; trainDemoPiece.x = x; trainDemoPiece.y = y; }
    private void setDemoOPiece(int x, int y) { trainDemoPiece = new Piece(2); trainDemoPiece.x = x; trainDemoPiece.y = y; }
    private void addComboRows() {
        // Shift board up by 2 rows, add 2 nearly-complete rows at bottom
        // Each row has 1 random gap
        for (int y = 0; y < R - 2; y++) { board[y] = board[y + 2]; }
        for (int i = 0; i < 2; i++) {
            int row = R - 2 + i;
            int gap = rnd.nextInt(C);
            for (int x = 0; x < C; x++) board[row][x] = (x == gap) ? 0 : 7;
        }
    }
    private Piece trainNextPiece() {
        Piece p;
        switch (trainTech) {
            case 0:
                p = new Piece(3);
                p.s = tShape(0); // Mini 起手朝上，逆时针转成朝左。
                p.rot = 0;
                break;
            case 1: case 2:
                p = new Piece(3);
                p.s = tShape(1); // Single/Double 起手朝右，顺时针转成朝下。
                p.rot = 1;
                break;
            default:
                trainTech = 0;
                p = new Piece(3);
                p.s = tShape(0);
                p.rot = 0;
                break;
        }
        return p;
    }
    private void start(long seed) { if (!solo) seed = seed ^ playerName.hashCode() ^ playerId.hashCode(); rnd.setSeed(seed); newHighScore=false;        board=new int[R][C]; score=0; lines=0; level=1; dropMs=1000; hold=0; pendingGarbage=0; combo=-1; b2b=0; badges=0; kos=0; garbageDueAt=0; areUntil=0; clearing=false; onGround=false; lockUntil=0; lockResets=0; bagIndex=7; releaseAllActions(); canHold=true; over=false; paused=false; settings=false; finishText=""; pausedTotalMs=0; pauseStartedAt=0; modeStartAt=System.currentTimeMillis(); invisible=false; invisibleFlashUntil=0; invisibleNearUntil=0; invisiblePreviewUntil=0; invisibleDangerUntil=0; invisibleNearCY=-1; invisibleNearCX=-1; digTargetLines=0; digCleared=0; trainDemoPiece=null; trainDemoStartPiece=null; trainResetAt=0; trainFailText=""; lastActionWasRotate=false; isGarbage=new boolean[R][C]; C=10; R=20; if(solo&&gameMode==MODE_DIG){digTargetLines=10; for(int y=R-10;y<R;y++){int hole=rnd.nextInt(C); for(int x=0;x<C;x++){board[y][x]=(x==hole)?0:7; isGarbage[y][x]=(x!=hole);}}}if(solo&&gameMode==MODE_CLASSIC&&classicSpeed==1){dropMs=800;} if(solo&&gameMode==MODE_INVISIBLE){invisible=true;}        if(solo&&gameMode==MODE_TRAINING){dropMs=2000;setupTrainingBoard();trainSuccess=0;} particles.clear(); if(!(solo&&gameMode==MODE_TRAINING)) next=randomPiece();if(pendingIRS!=0){next.s=rot(next.s,pendingIRS>0);next.rot=(pendingIRS>0)?1:3;pendingIRS=0;} spawn(); if(pendingIHS){pendingIHS=false;hold();} if (!solo) { for (BotPlayer bot : bots.values()) { bot.rnd = new Random(seed ^ bot.name.hashCode()); bot.board = new int[20][10]; bot.score = 0; bot.lines = 0; bot.level = 1; bot.over = false; bot.dropDelay = 600; bot.actionSpeed = 3; bot.iq = 5; bot.pendingGarbage = 0; bot.combo = -1; bot.b2b = 0; bot.badges = 0; bot.kos = 0; bot.garbageDueAt = 0; bot.canHold = true; bot.bagIndex = 7; bot.fillBag(); bot.next = bot.randomPiece(); bot.cur = bot.randomPiece(); bot.cur.x = (10 - bot.cur.s[0].length) / 2; bot.cur.y = 0; bot.cur.rot = 0; bot.lastTick = 0; bot.thinkUntil = 0; } }
        lastDrop=System.currentTimeMillis(); menu=false; tone(sReady); }
    private Piece randomPiece(){ if(solo&&gameMode==MODE_TRAINING) return trainNextPiece(); if(bagIndex>=7) fillBag(); return new Piece(bag[bagIndex++]); }
    private void fillBag(){ for(int i=0;i<7;i++) bag[i]=i+1; for(int i=6;i>0;i--){int j=rnd.nextInt(i+1); int t=bag[i]; bag[i]=bag[j]; bag[j]=t;} bagIndex=0; }
    private void spawn(){ cur=next==null?randomPiece():next; next=randomPiece(); cur.x=(C-cur.s[0].length)/2; cur.y=0; if(solo&&gameMode==MODE_TRAINING&&trainDemoStartPiece!=null){cur.x=trainDemoStartX;cur.y=trainDemoStartY;cur.s=tShape(trainDemoStartPiece.rot);cur.rot=trainDemoStartPiece.rot;} if(!(solo&&gameMode==MODE_TRAINING))cur.rot=0; cur.spin=false; cur.mini=false; lastActionWasRotate=false; onGround=false; canHold=true; if(invisible){ invisiblePreviewUntil=System.currentTimeMillis()+Math.min(1200+Math.max(0,(int)((700-dropMs)*0.5f)),2500); }        if(!ok(cur,0,0,cur.s)){ if(!solo){ if(pendingGarbage>0&&!lastAttacker.isEmpty()) notifyKO(playerName,lastAttacker); finishGame("被KO"); if(p2p!=null){ sendP2pState(); checkMultiFinish(); } } else { finishGame("游戏结束"); } return; } }
    boolean move(int dx,int dy){ if(cur==null||!ok(cur,dx,dy,cur.s)) return false; if(dx!=0) lastActionWasRotate=false; cur.x+=dx; cur.y+=dy; if(dx!=0){ tone(sMove); if(onGround&&lockResets<MAX_LOCK_RESETS){ lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS; lockResets++; } } return true; }
    private boolean ok(Piece pc,int dx,int dy,int[][] s){ for(int r=0;r<s.length;r++) for(int x=0;x<s[r].length;x++) if(s[r][x]!=0){int xx=pc.x+x+dx, yy=pc.y+r+dy; if(xx<0||xx>=C||yy>=R) return false; if(yy>=0&&board[yy][xx]!=0)return false;} return true; }
    private void rotate(boolean cw){ if(cur==null)return; int[][] ns=rot(cur.s,cw); int newRot=(cur.rot+(cw?1:3))%4; int idx=cw?cur.rot*2:((cur.rot+3)%4)*2+1; int[][][] table=(cur.type==1)?SRS_I:SRS_JLSTZ; for(int ki=0;ki<table[idx].length;ki++){ int[] k=table[idx][ki]; if(ok(cur,k[0],k[1],ns)){cur.s=ns;cur.x+=k[0];cur.y+=k[1];cur.rot=newRot;cur.spin=(cur.type==3);lastActionWasRotate=(cur.type==3);cur.mini=(cur.type==3&&ki>0&&ki<4);if(onGround&&lockResets<MAX_LOCK_RESETS){lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS;lockResets++;}tone(sRotate);return;} } }
    private int[][] rot(int[][] s, boolean cw){ int n=s.length; int[][] a=new int[n][n]; for(int r=0;r<n;r++) for(int c=0;c<n;c++) if(cw)a[c][n-1-r]=s[r][c]; else a[n-1-c][r]=s[r][c]; return a; }
    private int ghostY(){ int y=cur.y; while(ok(cur,0,y-cur.y+1,cur.s)) y++; return y; }
    private void lock(){ int spinType=checkTSpin(); boolean trainLandedOnTarget=trainingTargetMatched(); boolean lockOut=false; for(int r=0;r<cur.s.length;r++) for(int x=0;x<cur.s[r].length;x++) if(cur.s[r][x]!=0){int yy=cur.y+r; if(yy<0) lockOut=true; else board[yy][cur.x+x]=cur.type;} // 隐身模式：落点附近闪现
        if(invisible&&!lockOut){int cy=0,cx=0,cnt=0;for(int r=0;r<cur.s.length;r++)for(int x2=0;x2<cur.s[r].length;x2++)if(cur.s[r][x2]!=0){cy+=cur.y+r;cx+=cur.x+x2;cnt++;}invisibleNearCY=cnt>0?cy/cnt:-1;invisibleNearCX=cnt>0?cx/cnt:-1;invisibleNearUntil=System.currentTimeMillis()+InvisibleModeController.invisibleRevealMs(level, dropMs, board, R);}
        if(lockOut){if(solo&&gameMode==MODE_TRAINING){trainResetAt=System.currentTimeMillis()+1000;cur=null;next=trainNextPiece();return;}finishGame("游戏结束");return;} int n=doClear(spinType);        if(solo&&gameMode==MODE_TRAINING){boolean ok=trainSuccess(spinType,n,trainLandedOnTarget);if(ok){trainSuccess++;trainFailText="";fx("✓ "+trainTechName()+" "+trainSuccess,true);}else{trainFailText=trainFailReason(spinType,n,trainLandedOnTarget);fx(trainFailText,false);}trainResetAt=System.currentTimeMillis()+1000;cur=null;next=trainNextPiece();return;} if(checkModeFinish()) return; if(n>0){areUntil=System.currentTimeMillis()+ARE_MS;clearing=true;}else{applyGarbage();spawn();} }
    private boolean trainSuccess(int spinType, int cleared, boolean onTarget){ switch(trainTech){case 0:return onTarget&&spinType>=1&&cleared==1;case 1:return onTarget&&spinType>=1&&cleared==1;case 2:return onTarget&&spinType>=1&&cleared==2;}return false;}
    private String trainFailReason(int spinType, int cleared, boolean onTarget){ if(!lastActionWasRotate)return "失败: 最后一步必须旋转"; if(!onTarget)return "失败: 没有转进目标槽"; if(spinType<1)return "失败: T槽角位不对"; if(trainTech==2&&cleared!=2)return "失败: 需要消2行"; if((trainTech==0||trainTech==1)&&cleared!=1)return "失败: 需要消1行"; return "失败: 位置不对"; }
    private boolean trainingTargetMatched(){ if(!(solo&&gameMode==MODE_TRAINING)||cur==null||trainDemoPiece==null)return true; if(cur.type!=trainDemoPiece.type||cur.x!=trainDemoTargetX||cur.y!=trainDemoTargetY||cur.rot!=trainDemoPiece.rot)return false; for(int r=0;r<cur.s.length;r++)for(int x=0;x<cur.s[r].length;x++){boolean a=cur.s[r][x]!=0; boolean b=r<trainDemoPiece.s.length&&x<trainDemoPiece.s[r].length&&trainDemoPiece.s[r][x]!=0; if(a!=b)return false;} return true; }
    private int checkTSpin(){ if(cur==null||cur.type!=3||!lastActionWasRotate)return 0; int cx=cur.x+1, cy=cur.y+1, n=0; int[][] pts={{cx-1,cy-1},{cx+1,cy-1},{cx-1,cy+1},{cx+1,cy+1}}; for(int[] q:pts){int x=q[0],y=q[1]; if(x<0||x>=C||y>=R||(y>=0&&board[y][x]!=0))n++;} if(n<3)return 0; return n==4?2:1; }
    private int doClear(int spinType){ int n=0; int garbageCleared=0; for(int y=R-1;y>=0;y--){ boolean full=true; for(int x=0;x<C;x++) if(board[y][x]==0){full=false;break;} if(full){ boolean hasGarbage=false; if(solo&&gameMode==MODE_DIG){ for(int x=0;x<C;x++) if(isGarbage[y][x]){hasGarbage=true;break;} } lineBurst(y); for(int yy=y;yy>0;yy--){ board[yy]=board[yy-1].clone(); if(solo&&gameMode==MODE_DIG) isGarbage[yy]=isGarbage[yy-1].clone(); } board[0]=new int[C]; if(solo&&gameMode==MODE_DIG) isGarbage[0]=new boolean[C]; n++; y++; if(solo&&gameMode==MODE_DIG && hasGarbage) garbageCleared++; }}        if(n>0){ invisibleFlashUntil=System.currentTimeMillis()+Math.min(InvisibleModeController.invisibleRevealMs(level, dropMs, board, R)+700, 5000); combo++; boolean difficult=n==4||spinType>=1; if(difficult)b2b++; else b2b=0; int base=new int[]{0,100,300,500,800}[n]; if(spinType==2) base=n==1?800:n==2?1200:1600; else if(spinType==1) base=n==1?200:n==2?400:600; int bonus=combo>0?combo*50:0; score+=(base+bonus)*level; lines+=n; if(solo&&gameMode==MODE_DIG) digCleared+=garbageCleared; if(solo&&gameMode==MODE_MARATHON){level=soloStage;dropMs=Math.max(120,1000-(soloStage-1)*100);}
else if(solo&&gameMode==MODE_CLASSIC&&classicSpeed==1){level=lines/10+1;dropMs=Math.max(40,1000-(level-1)*130);}
else{level=lines/10+1;dropMs=Math.max(80,1000-(level-1)*90);} int garbage=garbageFor(n, spinType); if(garbage>0&&pendingGarbage>0){int cancel=Math.min(garbage,pendingGarbage); pendingGarbage-=cancel; garbage-=cancel;}            if(!solo&&p2p!=null&&garbage>0){ p2p.sendGarbage(garbage); for(BotPlayer bot:bots.values()){ if(!bot.over){ bot.pendingGarbage+=garbage; bot.garbageDueAt=System.currentTimeMillis()+1800; bot.lastAttacker=playerName; } } }fx((spinType>=1?(spinType==2?"T-SPIN ":"T-SPIN MINI "):(n==4?"TETRIS ":"CLEAR "))+n+(combo>1?" COMBO "+combo:""), difficult||n>=3); tone(difficult?sTetris:sClear);
        } else { combo=-1; } return n; }
    private int garbageFor(int n, int spinType){ return garbageFor(n, spinType, b2b, combo, badges); }
    private static int garbageFor(int n, int spinType, int b2b, int combo, int badges){ int g=0; if(spinType==2)g=n==1?2:n==2?4:6; else if(spinType==1)g=n==1?0:n==2?1:2; else if(n==2)g=1; else if(n==3)g=2; else if(n==4)g=4; if(b2b>1&&(spinType>=1||n==4))g++; if(combo>1)g+=(combo<4?1:combo<6?2:3); g += badges/2; return Math.min(g, 4); }
    private void lineBurst(int row){ for(int i=0;i<18;i++) particles.add(new FxParticle(bx+rnd.nextFloat()*bw, by+(row+.5f)*cell, (rnd.nextFloat()-.5f)*bw*.7f, (rnd.nextFloat()-.5f)*90f, theme().blockFlash, 3+rnd.nextFloat()*5)); }
    private void applyGarbage(){ if(pendingGarbage>0&&garbageDueAt==0)garbageDueAt=System.currentTimeMillis()+1800; if(pendingGarbage<=0||System.currentTimeMillis()<garbageDueAt)return; while(pendingGarbage>0){ for(int y=0;y<R-1;y++) board[y]=board[y+1].clone(); int hole=rnd.nextInt(C); board[R-1]=new int[C]; for(int x=0;x<C;x++) board[R-1][x]=(x==hole)?0:7; pendingGarbage--; } garbageDueAt=0; fx("GARBAGE", true); tone(sGarbage); }
    private void hold(){ if(!canHold||cur==null||over)return; int t=cur.type; lastActionWasRotate=false; if(hold==0){hold=t; spawn();} else {cur=new Piece(hold); cur.x=3; cur.y=0; hold=t;}    if(solo&&gameMode==MODE_TRAINING){if(cur.type==3&&trainTech==0){cur.s=tShape(0);cur.rot=0;}else if(cur.type==3&&(trainTech==1||trainTech==2)){cur.s=tShape(1);cur.rot=1;}} canHold=false; tone(sReady); }

    private String saveKey() {
        if (gameMode == MODE_TRAINING) return "save_6_" + trainTech;
        return "save_" + gameMode + "_" + (gameMode == MODE_CLASSIC ? classicSpeed : 0);
    }

    private void save(boolean toast) {
        if(gameMode==MODE_TRAINING){if(toast)Toast.makeText(getContext(),"训练模式无法保存",Toast.LENGTH_SHORT).show();return;}
        if(!solo||cur==null||over){if(toast)Toast.makeText(getContext(),"当前无法保存",Toast.LENGTH_SHORT).show();return;}
        try{
            JSONObject o=new JSONObject();
            o.put("gameMode", gameMode);
            o.put("board", arr(board));
            o.put("cur", cur.json());
            o.put("next", next.json());
            o.put("hold", hold);
            o.put("score", score); o.put("lines", lines); o.put("level", level);
            o.put("drop", dropMs); o.put("bagIndex", bagIndex);
            o.put("soloStage", soloStage); o.put("classicSpeed", classicSpeed);
            o.put("digCleared", digCleared); o.put("digTargetLines", digTargetLines);
            o.put("invisible", invisible);
            o.put("pendingGarbage", pendingGarbage); o.put("combo", combo); o.put("b2b", b2b);
            o.put("badges", badges); o.put("kos", kos); o.put("canHold", canHold);
            o.put("elapsedMs", GameClock.elapsed(System.currentTimeMillis(), modeStartAt, pausedTotalMs, paused, pauseStartedAt));
            o.put("isGarbage", encodeGarbage());
            JSONArray bagArr=new JSONArray(); for(int v:bag) bagArr.put(v);
            o.put("bag", bagArr);
            sp.edit().putString(saveKey(), o.toString()).apply();
            if(toast) Toast.makeText(getContext(), modeName() + " 已保存", Toast.LENGTH_SHORT).show();
        }catch(Exception e){ if(toast) Toast.makeText(getContext(), "保存失败", Toast.LENGTH_SHORT).show(); }
    }
    private void load(){
        try{
            String s=sp.getString(saveKey(), null);
            if(s==null)return;
            C=10; R=20;
            JSONObject o=new JSONObject(s);
            gameMode=o.optInt("gameMode", gameMode);
            board=board(o.getJSONArray("board"));
            cur=new Piece(o.getJSONObject("cur"));
            next=new Piece(o.getJSONObject("next"));
            hold=o.optInt("hold");
            score=o.optInt("score"); lines=o.optInt("lines"); level=o.optInt("level",1);
            dropMs=o.optLong("drop",1000); bagIndex=o.optInt("bagIndex",7);
            soloStage=o.optInt("soloStage",1); classicSpeed=o.optInt("classicSpeed",classicSpeed);
            digCleared=o.optInt("digCleared",0); digTargetLines=o.optInt("digTargetLines",0);
            invisible=o.optBoolean("invisible",false);
            pendingGarbage=o.optInt("pendingGarbage",0); combo=o.optInt("combo",-1); b2b=o.optInt("b2b",0);
            badges=o.optInt("badges",0); kos=o.optInt("kos",0); canHold=o.optBoolean("canHold",true);
            decodeGarbage(o.optString("isGarbage",""));
            JSONArray bagArr=o.optJSONArray("bag");
            if(bagArr!=null&&bagArr.length()==7){ for(int i=0;i<7;i++) bag[i]=bagArr.getInt(i); }
            long elapsed=o.optLong("elapsedMs",0);
            finishText=""; modeStartAt=System.currentTimeMillis()-elapsed; pausedTotalMs=0; pauseStartedAt=0;
            over=false; paused=false; settings=false; menu=false;
        }catch(Exception ignored){}
    }
    private void initSound(){ try{ toneGen=new ToneGenerator(AudioManager.STREAM_MUSIC, 45); sMove=ToneGenerator.TONE_PROP_BEEP; sRotate=ToneGenerator.TONE_PROP_ACK; sDrop=ToneGenerator.TONE_PROP_NACK; sClear=ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD; sTetris=ToneGenerator.TONE_CDMA_ABBR_ALERT; sGarbage=ToneGenerator.TONE_SUP_ERROR; sReady=ToneGenerator.TONE_PROP_PROMPT; }catch(Exception ignored){} }
    private void tone(int id){ if(toneGen!=null&&id!=0) toneGen.startTone(id, 70); }

    private JSONArray arr(int[][] b)throws Exception{ JSONArray a=new JSONArray(); for(int y=0;y<R;y++){JSONArray row=new JSONArray(); for(int x=0;x<C;x++) row.put(b[y][x]); a.put(row);} return a; }
    private int[][] board(JSONArray a)throws Exception{ int[][] b=new int[R][C]; for(int y=0;y<R;y++){JSONArray row=a.getJSONArray(y); for(int x=0;x<C;x++) b[y][x]=row.getInt(x);} return b; }

    private interface TextDone { void apply(String text); }
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
        String lastAttacker = "";
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
            long elapsedMin = Math.max(1, GameClock.elapsed(now, modeStartAt, pausedTotalMs, paused, pauseStartedAt) / 60000);
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
            // 放慢bot下落速度：基础延迟 + 等级因子减弱 + actionSpeed影响减小
            bot.dropDelay = Math.max(1050, Math.min(3600, 4200 - bot.level * 15 - bot.actionSpeed * 7));

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
            bot.thinkUntil = now + Math.max(600, 2700 - bot.actionSpeed * 13);
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

    private int botTspin(BotPlayer bot) {
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

    private int botGarbageFor(BotPlayer bot, int n, int spinType) {
        return garbageFor(n, spinType, bot.b2b, bot.combo, bot.badges);
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
            PeerInfo pi = peerInfos.get(bot.hostKey);
            if (pi != null) pi.over = true;
            if (!bot.lastAttacker.isEmpty()) notifyKO(bot.name, bot.lastAttacker);
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
            if (p2p != null && garbage > 0) {
                p2p.sendGarbage(garbage);
                if (!over) {
                    pendingGarbage += garbage;
                    lastAttacker = bot.name;
                    garbageDueAt = System.currentTimeMillis() + 1800;
                    p2pStatus = bot.name + " 送了 " + garbage + " 行";
                    tone(sGarbage);
                    fx("WARNING +" + garbage, true);
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
                PeerInfo pi = peerInfos.get(bot.hostKey);
                if (pi != null) pi.over = true;
                if (!bot.lastAttacker.isEmpty()) notifyKO(bot.name, bot.lastAttacker);
            }
        }
        // Sync bot board to peerInfos for local preview
        PeerInfo pi = peerInfos.get(bot.hostKey);
        if (pi != null) {
            int[][] copy = new int[20][10];
            for (int y = 0; y < 20; y++) System.arraycopy(bot.board[y], 0, copy[y], 0, 10);
            pi.board = copy;
        }
    }

    private void sendBotStates() {
        if (p2p == null) return;
        for (BotPlayer bot : bots.values()) {
            if (bot.over) continue;
            p2p.publishBotState(bot.name, bot.score, bot.lines, bot.level, bot.over, bot.kos, bot.badges, encodeBoardStatic(bot.board));
        }
    }


    private String encodeBoard() { return encodeBoardStatic(board); }
    private static String encodeBoardStatic(int[][] b) {
        StringBuilder sb = new StringBuilder(200);
        for (int r = 0; r < 20; r++) {
            for (int col = 0; col < 10; col++) {
                int v = b[r][col];
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
    private String encodeGarbage() {
        if (isGarbage == null) return "";
        StringBuilder sb = new StringBuilder(200);
        for (int r = 0; r < 20; r++)
            for (int c = 0; c < 10; c++)
                sb.append(isGarbage[r][c] ? '1' : '0');
        return sb.toString();
    }
    private void decodeGarbage(String s) {
        isGarbage = new boolean[20][10];
        if (s == null || s.length() < 200) return;
        for (int i = 0; i < 200 && i < s.length(); i++)
            isGarbage[i / 10][i % 10] = s.charAt(i) == '1';
    }
}
