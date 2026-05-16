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
import com.echo.tetrislan.core.BoardCodec;
import com.echo.tetrislan.core.SaveManager;
import com.echo.tetrislan.core.Rules;
import com.echo.tetrislan.ai.BotPlayer;
import com.echo.tetrislan.net.P2pTransport;
import com.echo.tetrislan.net.TetrisP2pListener;

import com.echo.tetrislan.core.Piece;
import com.echo.tetrislan.core.GameClock;
import com.echo.tetrislan.ui.InputController;
import com.echo.tetrislan.modes.SoloModeController;
import com.echo.tetrislan.ui.TouchRouter;
import com.echo.tetrislan.modes.InvisibleModeController;
import com.echo.tetrislan.modes.TrainingModeController;
import com.echo.tetrislan.modes.DigModeController;

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
import com.echo.tetrislan.net.MultiplayerController;
import com.echo.tetrislan.ai.BotController;

public class TetrisView extends View implements Runnable {
    public MultiplayerController multiplayerController = new MultiplayerController(this);
    public BotController botController = new BotController(this);
    public int C = 10, R = 20;
    public static final int MAX_PLAYERS = 3;
public static final int MODE_CLASSIC = 0, MODE_SPRINT = 1, MODE_ULTRA = 2, MODE_MARATHON = 3, MODE_INVISIBLE = 4, MODE_DIG = 5, MODE_TRAINING = 6;
    public static final String VERSION = "v1.26.29";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MenuRenderer menuRenderer = new MenuRenderer(p);
    private final BoardRenderer boardRenderer = new BoardRenderer(p);
    private final FxRenderer fxRenderer = new FxRenderer(p);
    private final InvisibleRenderer invisibleRenderer = new InvisibleRenderer();
    private final TrainingRenderer trainingRenderer = new TrainingRenderer(p);
    private final InputController inputController = new InputController(this);
    private final TouchRouter touchRouter = new TouchRouter(this);
    public final SoloModeController soloModeController = new SoloModeController(this);
    public SaveManager saveManager;
    public final Random rnd = new Random();
    public final SharedPreferences sp;
    public final List<Btn> btns = new ArrayList<>();
    private final List<FxParticle> particles = new ArrayList<>();
    private Thread loop;
    public P2pTransport p2p;
    public String p2pStatus = "P2P未启动";
    public String roomName = "TETRIS";
    public final String roomPass = "1234";
    public String playerName;
    public String playerId;
    public String lastRoomName = null;
    public String lastRoomHost = null;
    public String roomHost = null;
    public final List<DiscoveredRoom> foundRooms = new ArrayList<>();
    public boolean p2pDiscovery = false;
    public boolean selfReady = false;
    public final Map<String, Boolean> readyPeers = new HashMap<>();
    public final Map<String, String> peerNames = new java.util.LinkedHashMap<>();
    public final Map<String, PeerInfo> peerInfos = new java.util.LinkedHashMap<>();
    public String reconnectCheckHost = null;
    public long reconnectCheckUntil = 0;
    public long pendingStartAt = 0, pendingStartSeed = 0;
    private final List<String> chat = new ArrayList<>();
    public int pendingGarbage = 0;
    public int combo = -1, b2b = 0, badges = 0, kos = 0;
    public String lastAttacker = "";
    public long garbageDueAt = 0;
    private String fxText = "";
    private long fxUntil = 0, shakeUntil = 0, flashUntil = 0;
    public int soloStage = 1;
    public long rankingUntil = 0;
    public java.util.List<String> rankingLines = new java.util.ArrayList<>();
    public RectF soloOverRestartBtn = null;
    public RectF soloOverRetryBtn = null;
    public long lastAnyPeerUpdate = 0;
    public boolean networkFrozen = false;
    private ToneGenerator toneGen;
    public int sMove, sRotate, sDrop, sClear, sTetris, sGarbage, sReady;
    private long lastP2pSend = 0;
    public boolean running = true, menu = true, over = true, paused = false, settings = false, confirmQuit = false;
    private boolean newHighScore = false;
    public boolean solo = true;
    public boolean canHold = true;
    public int gameMode = MODE_CLASSIC;
    public int classicSpeed = 0; // 0=普通(经典), 1=高速(原生存)
    public long modeStartAt = 0, pauseStartedAt = 0, pausedTotalMs = 0;
    public String finishText = "";
    public boolean invisible = false;
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
    public int digTargetLines = 0;
    public int digCleared = 0;
    public int trainTech = 0;
    public int trainSuccess = 0;
    private String trainFailText = "";
    private long trainResetAt = 0;
    public Piece trainDemoPiece = null; // 训练模式目标落点
    public Piece trainDemoStartPiece = null; // 训练模式演示起手
    private int trainDemoStartX = 0, trainDemoStartY = 0, trainDemoTargetX = 0, trainDemoTargetY = 0, trainDemoRotDir = 1;
    public boolean[][] isGarbage;
    public int menuPage = 0; // 0 main, 1 solo actions, 2 multiplayer actions, 3 new/load for solo mode, 4 training technique select
    public int pendingStartMode = MODE_CLASSIC; // mode selected waiting for new/load choice
    private int statusBarH = 0;
    public int[][] board = new int[R][C];
    public Piece cur, next;
    public int hold = 0, score = 0, lines = 0, level = 1;
    public long lastDrop = 0, dropMs = 1000, lastSave = 0;
    public long dasMs = 167, arrMs = 33, softMs = 120;
    private static final long HARD_COOLDOWN_MS = 350;
    private static final long LOCK_DELAY_MS = 500, ARE_MS = 400;
    private static final int MAX_LOCK_RESETS = 15;
    public boolean leftHeld = false, rightHeld = false, softHeld = false;
    private boolean hardReady = true;
    public long leftStart = 0, rightStart = 0, arrAt = 0, softStart = 0, softAt = 0;
    private long lastHard = 0;
    private int pendingIRS = 0; // 0=none, 1=cw, -1=ccw
    private boolean pendingIHS = false;
    private long lockUntil = 0;
    private int lockResets = 0;
    private boolean onGround = false;
    private boolean lastActionWasRotate = false;
    private long areUntil = 0;
    private boolean clearing = false;
    public int activeAction = -1;
    public final Map<Integer, Integer> pointerActions = new HashMap<>();
    public int[] bag = new int[7];
    public int bagIndex = 7;
    public boolean isHost = false;
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
    public static final int[][][] SRS_JLSTZ = {
        {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}},
        {{0,0},{1,0},{1,-1},{0,2},{1,2}},
        {{0,0},{1,0},{1,-1},{0,2},{1,2}},
        {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}},
        {{0,0},{1,0},{1,1},{0,-2},{1,-2}},
        {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}},
        {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}},
        {{0,0},{1,0},{1,1},{0,-2},{1,-2}}
    };
    public static final int[][][] SRS_I = {
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
        saveManager = new SaveManager(this, sp);
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
                        next = TrainingModeController.trainNextPiece(trainTech);
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
                modeName(), saveManager.hasSave(),
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
                // 关卡模式：显示失败原因
                String ft = finishText.isEmpty() ? "游戏结束" : finishText;
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(0xdd000000);
                c.drawRoundRect(new RectF(50, h/2f - 130, w - 50, h/2f - 50), 24, 24, p);
                p.setColor(Color.WHITE); p.setTextSize(46);
                c.drawText(ft, w/2f, h/2f - 72, p);
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
        else if (gameMode == MODE_TRAINING) stageInfo = "练习: " + TrainingModeController.trainTechName(trainTech);
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




    public void fx(String text, boolean strong) {
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
        return touchRouter.onTouchEvent(e);
    }

    public void startP2p() {
        if (p2p != null) return;
        p2pStatus = "房间广播中";
        p2p = new P2pTransport(roomName, roomPass, playerName, playerId, isHost, new TetrisP2pListener(this));
        p2p.start();
    }

    private void sendP2pState() {
        multiplayerController.sendP2pState();
    }

    public void syncStart() {
        multiplayerController.syncStart();
    }

    public void createRoom() {
        multiplayerController.createRoom();
    }

    public void askRoom() {
        multiplayerController.askRoom();
    }

    public void joinRoom(String room, String host) {
        multiplayerController.joinRoom(room, host);
    }

    public void toggleReady() {
        multiplayerController.toggleReady();
    }

    private void maybeCountdown() {
        multiplayerController.maybeCountdown();
    }

    public boolean canHostStart() {
        return multiplayerController.canHostStart();
    }

    public int playerCount() {
        return multiplayerController.playerCount();
    }

    private int readyCount() {
        return multiplayerController.readyCount();
    }

    private boolean isRoomFullForNewPeer(String host) {
        return multiplayerController.isRoomFullForNewPeer(host);
    }

    public boolean fromRoomHost(String host) {
        return multiplayerController.fromRoomHost(host);
    }

    public boolean acceptPeer(String host, String name) {
        return multiplayerController.acceptPeer(host, name);
    }

    public void leaveRoom() {
        multiplayerController.leaveRoom();
    }

    private void saveLastRoom() {
        multiplayerController.saveLastRoom();
    }

    public void reconnectLastRoom() {
        multiplayerController.reconnectLastRoom();
    }


    public void askName() {
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

    public void kickPlayer() {
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

    public void askChat() {
        askText("聊天", "", text -> {
            String t = clean(text, "");
            if (t.isEmpty()) return;
            startP2p();
            p2p.sendChat(t);
            addChat("我: " + t);
        });
    }

    public void askText(String title, String value, TextDone done) {
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setView(input)
            .setPositiveButton("确定", (d, which) -> done.apply(input.getText().toString()))
            .setNegativeButton("取消", null).show();
    }

    public String clean(String s, String fallback) {
        if (s == null) return fallback;
        String v = s.trim().replace("|", "");
        return v.isEmpty() ? fallback : v;
    }

    public void addChat(String line) {
        chat.add(line.length() > 32 ? line.substring(0, 32) : line);
        while (chat.size() > 20) chat.remove(0);
    }

    public void restartP2p() {
        stopP2p();
        selfReady = false; readyPeers.clear(); peerNames.clear(); pendingStartAt = 0; pendingStartSeed = 0;
        startP2p();
    }

    public void stopP2p() {
        if (p2p != null) { p2p.stop(); p2p = null; }
        p2pStatus = "P2P未启动";
        selfReady = false; readyPeers.clear(); peerNames.clear(); peerInfos.clear(); foundRooms.clear(); p2pDiscovery = false;
        rankingUntil = 0;
        bots.clear(); nextBotId = 1;
    }

    public void pressAction(int a) {
        long now = System.currentTimeMillis();
        if (a==3) { leftHeld = true; leftStart = now; arrAt = now; move(-1,0); return; }
        if (a==5) { rightHeld = true; rightStart = now; arrAt = now; move(1,0); return; }
        if (a==4) { softHeld = true; softStart = now; softAt = 0; if (move(0,1)) score++; return; }
        act(a);
    }

    public void releaseAction(int a) {
        if (a==3) leftHeld = false;
        if (a==5) rightHeld = false;
        if (a==4) softHeld = false;
        if (a==1) hardReady = true;
        activeAction = -1;
    }

    public void releaseAllActions() {
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

    public void goMenu() {
        menu = true;
        settings = false;
        paused = false;
        confirmQuit = false;
        menuPage = solo ? 1 : 2;
        pendingIRS = 0;
        pendingIHS = false;
    }

    public void setPaused(boolean value) {
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

    public String modeName() {
        if (gameMode == MODE_SPRINT) return "冲刺40行";
        if (gameMode == MODE_ULTRA) return "限时得分";
        if (gameMode == MODE_MARATHON) return "马拉松";
        if (gameMode == MODE_INVISIBLE) return "隐形模式";
        if (gameMode == MODE_DIG) return "挖掘挑战";
        if (gameMode == MODE_TRAINING) return "T-Spin训练 " + TrainingModeController.trainTechName(trainTech);
        return gameMode==MODE_CLASSIC ? (classicSpeed==1 ? "经典 高速" : "经典 普通") : "经典模式";
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

    public void finishGame(String text) {
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

    public void notifyKO(String targetName, String killerName) {
        multiplayerController.notifyKO(targetName, killerName);
    }

    public void checkMultiFinish() {
        multiplayerController.checkMultiFinish();
    }

    private boolean allPeersOver() {
        return multiplayerController.allPeersOver();
    }

    private void cleanupDisconnectedPeers(long now) {
        multiplayerController.cleanupDisconnectedPeers(now);
    }

    private void showRankingAndReturn() {
        multiplayerController.showRankingAndReturn();
    }

    public void returnToRoom() {
        multiplayerController.returnToRoom();
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
        applySoloStageParams();
        if (gameMode == MODE_SPRINT) {
            stageGarbage = Math.min(5, soloStage - 1);
        } else if (gameMode == MODE_MARATHON) {
            stageGarbage = Math.min(5, soloStage - 1);
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

    private void applySoloStageParams() {
        if (!solo) return;
        if (gameMode == MODE_MARATHON) {
            level = soloStage;
            dropMs = Math.max(120, 1000 - (soloStage - 1) * 100);
        } else if (gameMode == MODE_SPRINT) {
            dropMs = Math.max(500, 1000 - (soloStage - 1) * 150);
        } else if (gameMode == MODE_ULTRA) {
            dropMs = Math.max(500, 1000 - (soloStage - 1) * 120);
        }
    }

    public void start() { start(System.currentTimeMillis()); }

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
        next = TrainingModeController.trainNextPiece(trainTech);
    }
    private void fillRow(int y) { for (int x = 0; x < C; x++) board[y][x] = 7; }
    private void fillRowExcept(int y, int... gaps) {
        for (int x = 0; x < C; x++) {
            boolean gap = false;
            for (int g : gaps) if (x == g) { gap = true; break; }
            board[y][x] = gap ? 0 : 7;
        }
    }
    private void setDemoTPiece(int targetX, int targetY, int targetRot, int startX, int startY, int rotDir) {
        trainDemoPiece = new Piece(3); trainDemoPiece.s = TrainingModeController.tShape(targetRot); trainDemoPiece.rot = targetRot; trainDemoPiece.x = targetX; trainDemoPiece.y = targetY;
        int startRot = (targetRot + (rotDir > 0 ? 3 : 1)) & 3;
        trainDemoStartPiece = new Piece(3); trainDemoStartPiece.s = TrainingModeController.tShape(startRot); trainDemoStartPiece.rot = startRot; trainDemoStartPiece.x = startX; trainDemoStartPiece.y = startY;
        trainDemoStartX = startX; trainDemoStartY = startY; trainDemoTargetX = targetX; trainDemoTargetY = targetY; trainDemoRotDir = rotDir;
    }
    private void setDemoIPiece(int x, int y) { trainDemoPiece = new Piece(1); trainDemoPiece.s = TrainingModeController.iVertical(); trainDemoPiece.rot = 1; trainDemoPiece.x = x; trainDemoPiece.y = y; }
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
    private void start(long seed) {
        rnd.setSeed(seed);
        for (BotPlayer bot : bots.values()) {
            bot.rnd.setSeed(seed);
            bot.bagIndex = 7;
        }
        newHighScore=false;        board=new int[R][C]; score=0; lines=0; level=1; dropMs=1000; hold=0; pendingGarbage=0; combo=-1; b2b=0; badges=0; kos=0; garbageDueAt=0; areUntil=0; clearing=false; onGround=false; lockUntil=0; lockResets=0; bagIndex=7; releaseAllActions(); canHold=true; over=false; paused=false; settings=false; finishText=""; pausedTotalMs=0; pauseStartedAt=0; modeStartAt=System.currentTimeMillis(); invisible=false; invisibleFlashUntil=0; invisibleNearUntil=0; invisiblePreviewUntil=0; invisibleDangerUntil=0; invisibleNearCY=-1; invisibleNearCX=-1; digTargetLines=0; digCleared=0; trainDemoPiece=null; trainDemoStartPiece=null; trainResetAt=0; trainFailText=""; lastActionWasRotate=false; isGarbage=new boolean[R][C]; C=10; R=20;        if(solo&&gameMode==MODE_DIG){digTargetLines=Math.min(20, 10 * soloStage); DigModeController.generateDigBoard(board, isGarbage, R, C, digTargetLines, rnd);}if(solo&&gameMode==MODE_CLASSIC&&classicSpeed==1){dropMs=800;} if(solo&&gameMode==MODE_INVISIBLE){invisible=true;}        if(solo&&gameMode==MODE_TRAINING){dropMs=2000;setupTrainingBoard();trainSuccess=0;} particles.clear(); applySoloStageParams(); if(!(solo&&gameMode==MODE_TRAINING)) next=randomPiece();if(pendingIRS!=0){next.s=rot(next.s,pendingIRS>0);next.rot=(pendingIRS>0)?1:3;pendingIRS=0;} spawn(); if(pendingIHS){pendingIHS=false;hold();} if (!solo) { for (BotPlayer bot : bots.values()) { bot.board = new int[20][10]; bot.score = 0; bot.lines = 0; bot.level = 1; bot.over = false; bot.dropDelay = 600; bot.actionSpeed = 3; bot.iq = 5; bot.pendingGarbage = 0; bot.combo = -1; bot.b2b = 0; bot.badges = 0; bot.kos = 0; bot.garbageDueAt = 0; bot.canHold = true; bot.bagIndex = 7; bot.fillBag(); bot.next = bot.randomPiece(); bot.cur = bot.randomPiece(); bot.cur.x = (10 - bot.cur.s[0].length) / 2; bot.cur.y = 0; bot.cur.rot = 0; bot.lastTick = 0; bot.thinkUntil = 0; } }
        lastDrop=System.currentTimeMillis(); menu=false; tone(sReady); }
    private Piece randomPiece(){ if(solo&&gameMode==MODE_TRAINING) return TrainingModeController.trainNextPiece(trainTech); if(bagIndex>=7) fillBag(); return new Piece(bag[bagIndex++]); }
    private void fillBag(){ for(int i=0;i<7;i++) bag[i]=i+1; for(int i=6;i>0;i--){int j=rnd.nextInt(i+1); int t=bag[i]; bag[i]=bag[j]; bag[j]=t;} bagIndex=0; }
    private void spawn(){ cur=next==null?randomPiece():next; next=randomPiece(); cur.x=(C-cur.s[0].length)/2; cur.y=0; if(solo&&gameMode==MODE_TRAINING&&trainDemoStartPiece!=null){cur.x=trainDemoStartX;cur.y=trainDemoStartY;cur.s=TrainingModeController.tShape(trainDemoStartPiece.rot);cur.rot=trainDemoStartPiece.rot;} if(!(solo&&gameMode==MODE_TRAINING))cur.rot=0; cur.spin=false; cur.mini=false; lastActionWasRotate=false; onGround=false; canHold=true; if(invisible){ invisiblePreviewUntil=System.currentTimeMillis()+Math.min(1200+Math.max(0,(int)((700-dropMs)*0.5f)),2500); }        if(!ok(cur,0,0,cur.s)){ if(!solo){ if(pendingGarbage>0&&!lastAttacker.isEmpty()) notifyKO(playerName,lastAttacker); finishGame("被KO"); if(p2p!=null){ sendP2pState(); checkMultiFinish(); } } else { finishGame("游戏结束"); } return; } }
    public boolean move(int dx,int dy){ if(cur==null||!ok(cur,dx,dy,cur.s)) return false; if(dx!=0) lastActionWasRotate=false; cur.x+=dx; cur.y+=dy; if(dx!=0){ tone(sMove); if(onGround&&lockResets<MAX_LOCK_RESETS){ lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS; lockResets++; } } return true; }
    private boolean ok(Piece pc,int dx,int dy,int[][] s){ for(int r=0;r<s.length;r++) for(int x=0;x<s[r].length;x++) if(s[r][x]!=0){int xx=pc.x+x+dx, yy=pc.y+r+dy; if(xx<0||xx>=C||yy>=R) return false; if(yy>=0&&board[yy][xx]!=0)return false;} return true; }
    private void rotate(boolean cw){ if(cur==null)return; int[][] ns=rot(cur.s,cw); int newRot=(cur.rot+(cw?1:3))%4; int idx=cw?cur.rot*2:((cur.rot+3)%4)*2+1; int[][][] table=(cur.type==1)?SRS_I:SRS_JLSTZ; for(int ki=0;ki<table[idx].length;ki++){ int[] k=table[idx][ki]; if(ok(cur,k[0],k[1],ns)){cur.s=ns;cur.x+=k[0];cur.y+=k[1];cur.rot=newRot;cur.spin=(cur.type==3);lastActionWasRotate=(cur.type==3);cur.mini=(cur.type==3&&ki>0&&ki<4);if(onGround&&lockResets<MAX_LOCK_RESETS){lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS;lockResets++;}tone(sRotate);return;} } }
    public int[][] rot(int[][] s, boolean cw){ int n=s.length; int[][] a=new int[n][n]; for(int r=0;r<n;r++) for(int c=0;c<n;c++) if(cw)a[c][n-1-r]=s[r][c]; else a[n-1-c][r]=s[r][c]; return a; }
    private int ghostY(){ int y=cur.y; while(ok(cur,0,y-cur.y+1,cur.s)) y++; return y; }
    private void lock(){ int spinType=Rules.checkTSpin(board, R, C, cur, lastActionWasRotate); boolean trainLandedOnTarget=TrainingModeController.trainingTargetMatched(cur, trainDemoPiece, trainDemoTargetX, trainDemoTargetY); boolean lockOut=false; for(int r=0;r<cur.s.length;r++) for(int x=0;x<cur.s[r].length;x++) if(cur.s[r][x]!=0){int yy=cur.y+r; if(yy<0) lockOut=true; else board[yy][cur.x+x]=cur.type;} // 隐身模式：落点附近闪现
        if(invisible&&!lockOut){int cy=0,cx=0,cnt=0;for(int r=0;r<cur.s.length;r++)for(int x2=0;x2<cur.s[r].length;x2++)if(cur.s[r][x2]!=0){cy+=cur.y+r;cx+=cur.x+x2;cnt++;}invisibleNearCY=cnt>0?cy/cnt:-1;invisibleNearCX=cnt>0?cx/cnt:-1;invisibleNearUntil=System.currentTimeMillis()+InvisibleModeController.invisibleRevealMs(level, dropMs, board, R);}
        if(lockOut){if(solo&&gameMode==MODE_TRAINING){trainResetAt=System.currentTimeMillis()+1000;cur=null;next=TrainingModeController.trainNextPiece(trainTech);return;}finishGame("游戏结束");return;} int n=doClear(spinType);        if(solo&&gameMode==MODE_TRAINING){boolean ok=TrainingModeController.trainSuccess(trainTech, spinType, n, trainLandedOnTarget);if(ok){trainSuccess++;trainFailText="";fx("✓ "+TrainingModeController.trainTechName(trainTech)+" "+trainSuccess,true);}else{trainFailText=TrainingModeController.trainFailReason(trainTech, lastActionWasRotate, spinType, n, trainLandedOnTarget);fx(trainFailText,false);}trainResetAt=System.currentTimeMillis()+1000;cur=null;next=TrainingModeController.trainNextPiece(trainTech);return;}        if(checkModeFinish()) return; if(n>0){areUntil=System.currentTimeMillis()+ARE_MS;clearing=true;}else{applyGarbage();spawn();} }
    private int doClear(int spinType){ int n=0; int garbageCleared=0; for(int y=R-1;y>=0;y--){ boolean full=true; for(int x=0;x<C;x++) if(board[y][x]==0){full=false;break;} if(full){ boolean hasGarbage=false; if(solo&&gameMode==MODE_DIG){ for(int x=0;x<C;x++) if(isGarbage[y][x]){hasGarbage=true;break;} } lineBurst(y); for(int yy=y;yy>0;yy--){ board[yy]=board[yy-1].clone(); if(solo&&gameMode==MODE_DIG) isGarbage[yy]=isGarbage[yy-1].clone(); } board[0]=new int[C]; if(solo&&gameMode==MODE_DIG) isGarbage[0]=new boolean[C]; n++; y++; if(solo&&gameMode==MODE_DIG && hasGarbage) garbageCleared++; }}        if(n>0){ invisibleFlashUntil=System.currentTimeMillis()+Math.min(InvisibleModeController.invisibleRevealMs(level, dropMs, board, R)+700, 5000); combo++; boolean difficult=n==4||spinType>=1; if(difficult)b2b++; else b2b=0; int base=new int[]{0,100,300,500,800}[n]; if(spinType==2) base=n==1?800:n==2?1200:1600; else if(spinType==1) base=n==1?200:n==2?400:600; int bonus=combo>0?combo*50:0; score+=(base+bonus)*level; lines+=n; if(solo&&gameMode==MODE_DIG) digCleared+=garbageCleared; if(solo&&gameMode==MODE_MARATHON){level=soloStage;dropMs=Math.max(120,1000-(soloStage-1)*100);}
else if(solo&&gameMode==MODE_CLASSIC&&classicSpeed==1){level=lines/10+1;dropMs=Math.max(40,1000-(level-1)*130);}
else{level=lines/10+1;dropMs=Math.max(80,1000-(level-1)*90);} int garbage=Rules.garbageFor(n, spinType, b2b, combo, badges); if(garbage>0&&pendingGarbage>0){int cancel=Math.min(garbage,pendingGarbage); pendingGarbage-=cancel; garbage-=cancel;}            if(!solo&&p2p!=null&&garbage>0){ p2p.sendGarbage(garbage); for(BotPlayer bot:bots.values()){ if(!bot.over){ bot.pendingGarbage+=garbage; bot.garbageDueAt=System.currentTimeMillis()+1800; bot.lastAttacker=playerName; } } }fx((spinType>=1?(spinType==2?"T-SPIN ":"T-SPIN MINI "):(n==4?"TETRIS ":"CLEAR "))+n+(combo>1?" COMBO "+combo:""), difficult||n>=3); tone(difficult?sTetris:sClear);
        } else { combo=-1; } return n; }
    public static int garbageFor(int n, int spinType, int b2b, int combo, int badges){ int g=0; if(spinType==2)g=n==1?2:n==2?4:6; else if(spinType==1)g=n==1?0:n==2?1:2; else if(n==2)g=1; else if(n==3)g=2; else if(n==4)g=4; if(b2b>1&&(spinType>=1||n==4))g++; if(combo>1)g+=(combo<4?1:combo<6?2:3); g += badges/2; return Math.min(g, 4); }
    private void lineBurst(int row){ for(int i=0;i<18;i++) particles.add(new FxParticle(bx+rnd.nextFloat()*bw, by+(row+.5f)*cell, (rnd.nextFloat()-.5f)*bw*.7f, (rnd.nextFloat()-.5f)*90f, theme().blockFlash, 3+rnd.nextFloat()*5)); }
    private void applyGarbage(){ if(pendingGarbage>0&&garbageDueAt==0)garbageDueAt=System.currentTimeMillis()+1800; if(pendingGarbage<=0||System.currentTimeMillis()<garbageDueAt)return; while(pendingGarbage>0){ for(int y=0;y<R-1;y++) board[y]=board[y+1].clone(); int hole=rnd.nextInt(C); board[R-1]=new int[C]; for(int x=0;x<C;x++) board[R-1][x]=(x==hole)?0:7; pendingGarbage--; } garbageDueAt=0; fx("GARBAGE", true); tone(sGarbage); }
    private void hold(){ if(!canHold||cur==null||over)return; int t=cur.type; lastActionWasRotate=false; if(hold==0){hold=t; spawn();} else {cur=new Piece(hold); cur.x=3; cur.y=0; hold=t;}    if(solo&&gameMode==MODE_TRAINING){if(cur.type==3&&trainTech==0){cur.s=TrainingModeController.tShape(0);cur.rot=0;}else if(cur.type==3&&(trainTech==1||trainTech==2)){cur.s=TrainingModeController.tShape(1);cur.rot=1;}} canHold=false; tone(sReady); }

    private void initSound(){ try{ toneGen=new ToneGenerator(AudioManager.STREAM_MUSIC, 45); sMove=ToneGenerator.TONE_PROP_BEEP; sRotate=ToneGenerator.TONE_PROP_ACK; sDrop=ToneGenerator.TONE_PROP_NACK; sClear=ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD; sTetris=ToneGenerator.TONE_CDMA_ABBR_ALERT; sGarbage=ToneGenerator.TONE_SUP_ERROR; sReady=ToneGenerator.TONE_PROP_PROMPT; }catch(Exception ignored){} }
    public void tone(int id){ if(toneGen!=null&&id!=0) toneGen.startTone(id, 70); }

    public interface TextDone { void apply(String text); }

    public final java.util.Map<String, BotPlayer> bots = new java.util.HashMap<>();
    public int nextBotId = 1;

    public int botCount() {
        return botController.botCount();
    }

    private int realPlayerCount() {
        return botController.realPlayerCount();
    }

    public void hostStartGame() {
        botController.hostStartGame();
    }

    public void addBot() {
        botController.addBot();
    }

    public void removeBot() {
        botController.removeBot();
    }

    public void clearBots() {
        botController.clearBots();
    }

    private void tickBots() {
        botController.tickBots();
    }

    private void botTick(BotPlayer bot) {
        botController.botTick(bot);
    }

    private BotPlayer.BotDecision evaluateBest(BotPlayer bot) {
        return botController.evaluateBest(bot);
    }

    private double evaluateBoard(BotPlayer bot) {
        return botController.evaluateBoard(bot);
    }

    private boolean botCanMove(BotPlayer bot, int dx, int dy) {
        return botController.botCanMove(bot, dx, dy);
    }

    private void botRotate(BotPlayer bot, boolean cw) {
        botController.botRotate(bot, cw);
    }

    private boolean botRotateSRS(BotPlayer bot, boolean cw) {
        return botController.botRotateSRS(bot, cw);
    }

    private boolean botOk(BotPlayer bot, int dx, int dy, int[][] s) {
        return botController.botOk(bot, dx, dy, s);
    }

    private int botTspin(BotPlayer bot) {
        return botController.botTspin(bot);
    }

    private int botGarbageFor(BotPlayer bot, int n, int spinType) {
        return botController.botGarbageFor(bot, n, spinType);
    }

    private int botDoClear(BotPlayer bot) {
        return botController.botDoClear(bot);
    }

    private void botLock(BotPlayer bot) {
        botController.botLock(bot);
    }

    private void sendBotStates() {
        botController.sendBotStates();
    }



    public static String encodeBoardStatic(int[][] b) {
        StringBuilder sb = new StringBuilder(200);
        for (int r = 0; r < 20; r++) {
            for (int col = 0; col < 10; col++) {
                int v = b[r][col];
                sb.append(v == 0 ? '.' : (char)('0' + v));
            }
        }
        return sb.toString();
    }
    public static int[][] decodeBoard(String s) {
        if (s == null || s.length() < 200) return null;
        int[][] b = new int[20][10];
        for (int i = 0; i < 200; i++) {
            char ch = s.charAt(i);
            b[i / 10][i % 10] = (ch == '.' || ch < '0' || ch > '7') ? 0 : (ch - '0');
        }
        return b;
    }
}
