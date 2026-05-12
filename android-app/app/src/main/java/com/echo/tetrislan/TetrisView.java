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
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TetrisView extends View implements Runnable {
    private static final int C = 10, R = 20;
    private static final String APP_VERSION = "v1.6.8";
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
    private final String playerName = "P" + (100 + rnd.nextInt(900));
    private String peerHost = null, peerName = "-", peerVia = "-";
    private String foundRoom = "-", foundHost = "-", foundName = "-";
    private boolean selfReady = false, peerReady = false;
    private final Map<String, Boolean> readyPeers = new HashMap<>();
    private final Map<String, String> peerNames = new HashMap<>();
    private long pendingStartAt = 0, pendingStartSeed = 0;
    private final List<String> chat = new ArrayList<>();
    private int peerScore = 0, peerLines = 0;
    private int pendingGarbage = 0;
    private int combo = -1, b2b = 0, badges = 0, kos = 0;
    private long garbageDueAt = 0;
    private String fxText = "";
    private long fxUntil = 0, shakeUntil = 0, flashUntil = 0;
    private ToneGenerator toneGen;
    private int sMove, sRotate, sDrop, sClear, sTetris, sGarbage, sReady;
    private long lastP2pSend = 0;
    private boolean running = true, menu = true, over = true, paused = false, settings = false, confirmQuit = false;
    private boolean solo = true, canHold = true;
    private int menuPage = 0; // 0 main, 1 solo actions, 2 multiplayer actions
    private int[][] board = new int[R][C];
    private Piece cur, next;
    private int hold = 0, score = 0, lines = 0, level = 1;
    private long lastDrop = 0, dropMs = 1000, lastSave = 0;
    private static final long DAS_MS = 167, ARR_MS = 33, SOFT_MS = 120, HARD_COOLDOWN_MS = 350;
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
            new int[]{0,0xff00e5ff,0xffffeb3b,0xffe040fb,0xff69f0ae,0xffff5252,0xff448aff,0xffffab40}),
        new Theme("经典", 0xff000000, 0xff111111, 0xff333333, 0xff888888,
            0xcc222222, 0xff00e5ff, 0xffff6600, 0xff333333,
            Color.WHITE, 0xff888888, 0xff00ffff, 0xffffff00,
            new int[]{0,0xff00ffff,0xffffff00,0xffff00ff,0xff00ff00,0xffff0000,0xff0000ff,0xffffa500}),
        new Theme("GameBoy", 0xff9bbc0f, 0xff8bac0f, 0xff306230, 0xff0f380f,
            0xcc8bac0f, 0xff306230, 0xff0f380f, 0xff8bac0f,
            0xff0f380f, 0xff306230, 0xff0f380f, 0xff306230,
            new int[]{0,0xff0f380f,0xff306230,0xff0f380f,0xff306230,0xff0f380f,0xff306230,0xff8bac0f}),
        new Theme("极简", 0xffffffff, 0xfff5f5f5, 0xffcccccc, 0xff111111,
            0xccdddddd, 0xff333333, 0xff888888, 0xffeeeeee,
            0xff111111, 0xff888888, 0xff000000, 0xff555555,
            new int[]{0,0xff333333,0xff666666,0xff999999,0xffaaaaaa,0xffbbbbbb,0xffcccccc,0xff444444})
    };
    private int themeIndex = 0;
    private Theme theme() { return THEMES[themeIndex]; }

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
        themeIndex = sp.getInt("theme", 0);
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
                if (areUntil > 0) {
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
                    lastP2pSend = now;
                }
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
        if (over) drawCenter(c, "游戏结束", "点设置或主界面");
        if (paused && !over) drawCenter(c, "暂停", "点暂停继续");
        if (settings) drawSettings(c, w, h);
    }

    private void drawMenu(Canvas c, int w, int h) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(theme().text); p.setTextSize(56); c.drawText("俄罗斯方块 " + APP_VERSION, w/2f, h*0.16f, p);
        if (menuPage == 0) {
            drawMenuButton(c, "单人模式", w*0.14f, h*0.30f, w*0.86f, h*0.39f, false);
            drawMenuButton(c, "多人模式", w*0.14f, h*0.43f, w*0.86f, h*0.52f, false);
            drawMenuButton(c, "主题: " + theme().name, w*0.14f, h*0.58f, w*0.86f, h*0.65f, false);
            p.setColor(theme().textMuted); p.setTextSize(28);
            c.drawText("先选模式，再开局/读取/保存", w/2f, h*0.75f, p);
            return;
        }
        boolean multi = menuPage == 2;
        p.setColor(theme().score); p.setTextSize(36); c.drawText(multi ? "多人大厅" : "单人模式", w/2f, h*0.25f, p);
        if (!multi) {
            drawMenuButton(c, "开始新局", w*0.14f, h*0.34f, w*0.86f, h*0.43f, false);
            drawMenuButton(c, "读取存档", w*0.14f, h*0.46f, w*0.86f, h*0.55f, false);
            drawMenuButton(c, "手动保存", w*0.14f, h*0.58f, w*0.86f, h*0.67f, false);
            drawMenuButton(c, "返回主菜单", w*0.14f, h*0.73f, w*0.86f, h*0.82f, false);
            p.setColor(theme().textMuted); p.setTextSize(28); c.drawText("单人支持自动/手动保存", w/2f, h*0.90f, p);
            return;
        }
        if (p2p == null) {
            drawMenuButton(c, "创建房间", w*0.08f, h*0.31f, w*0.46f, h*0.39f, false);
            drawMenuButton(c, "输入房号", w*0.54f, h*0.31f, w*0.92f, h*0.39f, false);
            drawMenuButton(c, "加入发现", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false);
            drawMenuButton(c, "返回主菜单", w*0.54f, h*0.42f, w*0.92f, h*0.50f, false);
        } else {
            drawMenuButton(c, selfReady ? "取消准备" : "准备", w*0.08f, h*0.31f, w*0.46f, h*0.39f, selfReady);
            drawMenuButton(c, "聊天", w*0.54f, h*0.31f, w*0.92f, h*0.39f, false);
            drawMenuButton(c, "退出房间", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false);
            if (isHost) {
                drawMenuButton(c, "解散房间", w*0.54f, h*0.42f, w*0.92f, h*0.50f, false);
            } else {
                drawMenuButton(c, "返回主菜单", w*0.54f, h*0.42f, w*0.92f, h*0.50f, false);
            }
            if (isHost && !peerNames.isEmpty()) {
                drawMenuButton(c, "踢人", w*0.08f, h*0.53f, w*0.46f, h*0.61f, false);
            }
        }
        p.setColor(theme().text); p.setTextSize(28);
        c.drawText("房间 " + roomName + "  准备 " + readyCount() + "/" + playerCount() + "  最少2人", w/2f, h*0.68f, p);
        if (p2p != null) {
            p.setColor(theme().score); p.setTextSize(28);
            c.drawText("玩家: " + playerName + (isHost ? "[房主]" : ""), w/2f, h*0.72f, p);
            int py = 0;
            for (java.util.Map.Entry<String, String> e : peerNames.entrySet()) {
                c.drawText(e.getValue() + (readyPeers.getOrDefault(e.getKey(), false) ? " [已准备]" : ""), w/2f, h*(0.755f + 0.035f*py), p);
                py++;
            }
        } else {
            p.setColor(theme().textMuted); p.setTextSize(24);
            c.drawText("发现 " + foundRoom + " / " + foundName + " " + foundHost, w/2f, h*0.72f, p);
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
        addBtn("暂停", 6, sideX, by + bh - bhBtn*1.62f, sideW, bhBtn);
        addBtn("暂存", 7, sideX, by + bh - bhBtn*.52f, sideW, bhBtn);
    }

    private void addBtn(String text, int action, float cx, float cy, float w, float h) { btns.add(new Btn(text, action, new RectF(cx-w/2, cy-h/2, cx+w/2, cy+h/2))); }

    private void drawTop(Canvas c) {
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(64); p.setColor(theme().text);
        c.drawText("分", 12, 80, p); p.setTextSize(52); p.setColor(theme().score); c.drawText(String.valueOf(score), 72, 80, p);
        p.setTextSize(40); p.setColor(theme().text); c.drawText("级", 220, 80, p); p.setTextSize(52); p.setColor(theme().score); c.drawText(String.valueOf(level), 270, 80, p);
        p.setTextSize(40); p.setColor(theme().text); c.drawText("行", 400, 80, p); p.setTextSize(52); p.setColor(theme().score); c.drawText(String.valueOf(lines), 450, 80, p);
    }

    private void drawBoard(Canvas c) {
        p.setStyle(Paint.Style.FILL); p.setColor(theme().board); c.drawRoundRect(new RectF(bx,by,bx+bw,by+bh), 8, 8, p);
        for (int y=0;y<R;y++) for (int x=0;x<C;x++) if (board[y][x] != 0) block(c,x,y,board[y][x],1f);
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
        p.setTextSize(34); c.drawText(solo ? "单人模式" : p2pStatus, cx, y+40, p);
        if (!solo) { c.drawText(peerName + " " + peerScore + "/" + peerLines + " " + peerVia + " G" + pendingGarbage, cx, y+78, p); y += 38; }
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
        p.setTextAlign(Paint.Align.CENTER); p.setColor(theme().text); p.setTextSize(46); c.drawText("设置", w/2f, h*0.22f, p);
        drawMenuButton(c, "继续游戏", w*.16f, h*.32f, w*.84f, h*.40f, false);
        drawMenuButton(c, "读取存档", w*.16f, h*.43f, w*.84f, h*.51f, false);
        drawMenuButton(c, "手动保存", w*.16f, h*.54f, w*.84f, h*.62f, false);
        drawMenuButton(c, "返回主界面", w*.16f, h*.68f, w*.84f, h*.76f, false);
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
            float btnW=dw*.27f, btnH=dh*.22f, btnY=dy+dh*.76f, gap=dw*.05f;
            drawMenuButton(c, "保存", w/2f-btnW*1.5f-gap, btnY, w/2f-btnW*.5f-gap, btnY+btnH, false);
            drawMenuButton(c, "不保存", w/2f-btnW/2f, btnY, w/2f+btnW/2f, btnY+btnH, false);
            drawMenuButton(c, "取消", w/2f+btnW*.5f+gap, btnY, w/2f+btnW*1.5f+gap, btnY+btnH, false);
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
            if (hit(x,y,w*.14f,h*.58f,w*.86f,h*.65f)) { themeIndex = (themeIndex + 1) % THEMES.length; sp.edit().putInt("theme", themeIndex).apply(); return true; }
            return true;
        }
        if (menuPage == 1) {
            if (hit(x,y,w*.14f,h*.34f,w*.86f,h*.43f)) { start(); return true; }
            if (hit(x,y,w*.14f,h*.46f,w*.86f,h*.55f)) { load(); return true; }
            if (hit(x,y,w*.14f,h*.58f,w*.86f,h*.67f)) { save(true); return true; }
            if (hit(x,y,w*.14f,h*.73f,w*.86f,h*.82f)) { menuPage=0; return true; }
            return true;
        }
        if (p2p == null) {
            if (hit(x,y,w*.08f,h*.31f,w*.46f,h*.39f)) { createRoom(); return true; }
            if (hit(x,y,w*.54f,h*.31f,w*.92f,h*.39f)) { askRoom(); return true; }
            if (hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { joinFound(); return true; }
            if (hit(x,y,w*.54f,h*.42f,w*.92f,h*.50f)) { stopP2p(); menuPage=0; return true; }
        } else {
            if (hit(x,y,w*.08f,h*.31f,w*.46f,h*.39f)) { toggleReady(); return true; }
            if (hit(x,y,w*.54f,h*.31f,w*.92f,h*.39f)) { askChat(); return true; }
            if (hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { leaveRoom(); return true; }
            if (hit(x,y,w*.54f,h*.42f,w*.92f,h*.50f)) {
                if (isHost) { disbandRoom(); return true; }
                else { stopP2p(); menuPage=0; return true; }
            }
            if (isHost && !peerNames.isEmpty() && hit(x,y,w*.08f,h*.53f,w*.46f,h*.61f)) { kickPlayer(); return true; }
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
        if (hit(x,y,w*.16f,h*.32f,w*.84f,h*.40f)) { settings=false; paused=false; return true; }
        if (hit(x,y,w*.16f,h*.43f,w*.84f,h*.51f)) { if (solo) load(); settings=false; return true; }
        if (hit(x,y,w*.16f,h*.54f,w*.84f,h*.62f)) { save(true); return true; }
        if (hit(x,y,w*.16f,h*.68f,w*.84f,h*.76f)) {
            if (!over && !menu) { confirmQuit = true; return true; }
            goMenu(); return true;
        }
        return true;
    }
    private boolean hit(float x,float y,float l,float t,float r,float b){return x>=l&&x<=r&&y>=t&&y<=b;}

    private void startP2p() {
        if (p2p != null) return;
        p2pStatus = "房间广播中";
        p2p = new P2pTransport(roomName, roomPass, playerName, new P2pTransport.Listener() {
            @Override public void onRoomFound(String room, String host, String name) {
                if (!roomName.equals(room)) { foundRoom = room; foundHost = host; foundName = name; }
            }
            @Override public void onPeer(String host, String name, int score, int lines, String via) {
                peerHost = host;
                peerName = name;
                peerScore = score;
                peerLines = lines;
                peerVia = via;
                peerNames.put(host, name);
                p2pStatus = "已连接 " + playerCount() + "/10";
            }
            @Override public void onReady(String host, String name, boolean ready) {
                peerHost = host; peerName = name; peerReady = ready;
                peerNames.put(host, name); readyPeers.put(host, ready);
                addChat("系统: " + name + (ready ? " 已准备" : " 取消准备"));
                maybeCountdown();
            }
            @Override public void onChat(String host, String name, String text) { addChat(name + ": " + text); }
            @Override public void onStart(long seed, long startAt) {
                pendingStartSeed = seed; pendingStartAt = startAt; p2pStatus = "3秒后开始";
            }
            @Override public void onGarbage(int rows) { pendingGarbage += rows; garbageDueAt = System.currentTimeMillis() + 1800; p2pStatus = "收到垃圾 " + rows; tone(sGarbage); fx("WARNING +" + rows, true); }
            @Override public void onLeave(String host, String name) {
                peerNames.remove(host); readyPeers.remove(host);
                addChat("系统: " + name + " 离开了房间");
            }
            @Override public void onKick(String host, String name, String targetName, String reason) {
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
                stopP2p(); isHost = false;
                fx("房间已解散", true);
                addChat("系统: 房主解散了房间");
            }
            @Override public void onError(String message) { p2pStatus = "P2P错误"; }
        });
        p2p.start();
    }

    private void sendP2pState() {
        p2p.publishState(score, lines, level, over);
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
        roomName = "R" + (1000 + rnd.nextInt(9000));
        restartP2p();
        addChat("系统: 已创建房间 " + roomName);
    }

    private void askRoom() {
        askText("输入房间号", roomName, text -> {
            roomName = clean(text, "TETRIS");
            if (p2p != null) { p2p.sendLeave(); stopP2p(); }
            isHost = false;
            restartP2p();
            addChat("系统: 已进入房间 " + roomName);
        });
    }

    private void joinFound() {
        if ("-".equals(foundRoom)) return;
        if (p2p != null) { p2p.sendLeave(); stopP2p(); }
        isHost = false;
        roomName = foundRoom;
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
        if (readyCount() >= 2 && pendingStartAt == 0 && isRoomLeader()) syncStart();
    }

    private int playerCount() { return Math.min(10, 1 + peerNames.size()); }
    private int readyCount() { int n = selfReady ? 1 : 0; for (Boolean r: readyPeers.values()) if (r) n++; return Math.min(10, n); }
    private boolean isRoomLeader() {
        String leader = playerName;
        for (String n: peerNames.values()) if (n.compareTo(leader) < 0) leader = n;
        return playerName.equals(leader);
    }

    private void leaveRoom() {
        if (p2p != null) p2p.sendLeave();
        stopP2p();
        isHost = false;
        addChat("系统: 已退出房间");
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
        selfReady = false; peerReady = false; readyPeers.clear(); peerNames.clear(); pendingStartAt = 0; pendingStartSeed = 0;
        startP2p();
    }

    private void stopP2p() {
        if (p2p != null) { p2p.stop(); p2p = null; }
        p2pStatus = "P2P未启动";
        selfReady = false; peerReady = false; readyPeers.clear(); peerNames.clear();
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
            long interval = Math.max(14, SOFT_MS - held / 12);
            if (now - softAt > interval) { if (move(0,1)) score++; softAt = now; }
        }
    }

    private void repeatHorizontal(int dir, long now, long startedAt) {
        if (now - startedAt <= DAS_MS) return;
        if (ARR_MS == 0) { while(move(dir,0)); return; }
        if (now - arrAt > ARR_MS) { move(dir,0); arrAt = now; }
    }

    private void act(int a) {
        if (a==8) { settings=true; paused=true; releaseAction(activeAction); return; }
        if (a==6) { paused=!paused; tone(sReady); return; }
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

    private void start() { start(System.currentTimeMillis()); }
    private void start(long seed) { rnd.setSeed(seed); board=new int[R][C]; score=0; lines=0; level=1; dropMs=1000; hold=0; pendingGarbage=0; combo=-1; b2b=0; badges=0; kos=0; garbageDueAt=0; areUntil=0; clearing=false; onGround=false; lockUntil=0; lockResets=0; bagIndex=7; releaseAllActions(); canHold=true; over=false; paused=false; settings=false; particles.clear(); next=randomPiece(); if(pendingIRS!=0){next.s=rot(next.s,pendingIRS>0);next.rot=(pendingIRS>0)?1:3;pendingIRS=0;} spawn(); if(pendingIHS){pendingIHS=false;hold();} lastDrop=System.currentTimeMillis(); menu=false; tone(sReady); }
    private Piece randomPiece(){ if(bagIndex>=7) fillBag(); return new Piece(bag[bagIndex++]); }
    private void fillBag(){ for(int i=0;i<7;i++) bag[i]=i+1; for(int i=6;i>0;i--){int j=rnd.nextInt(i+1); int t=bag[i]; bag[i]=bag[j]; bag[j]=t;} bagIndex=0; }
    private void spawn(){ cur=next==null?randomPiece():next; next=randomPiece(); cur.x=3; cur.y=0; cur.rot=0; cur.spin=false; onGround=false; canHold=true; if(!ok(cur,0,0,cur.s)) over=true; }
    private boolean move(int dx,int dy){ if(cur==null||!ok(cur,dx,dy,cur.s)) return false; cur.x+=dx; cur.y+=dy; if(dx!=0){ tone(sMove); if(onGround&&lockResets<MAX_LOCK_RESETS){ lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS; lockResets++; } } return true; }
    private boolean ok(Piece pc,int dx,int dy,int[][] s){ for(int r=0;r<s.length;r++) for(int x=0;x<s[r].length;x++) if(s[r][x]!=0){int xx=pc.x+x+dx, yy=pc.y+r+dy; if(xx<0||xx>=C||yy>=R) return false; if(yy>=0&&board[yy][xx]!=0)return false;} return true; }
    private void rotate(boolean cw){ if(cur==null)return; int[][] ns=rot(cur.s,cw); int newRot=(cur.rot+(cw?1:3))%4; int idx=cw?cur.rot*2:((cur.rot+3)%4)*2+1; int[][][] table=(cur.type==1)?SRS_I:SRS_JLSTZ; for(int[] k:table[idx]) if(ok(cur,k[0],k[1],ns)){cur.s=ns;cur.x+=k[0];cur.y+=k[1];cur.rot=newRot;cur.spin=true;if(onGround&&lockResets<MAX_LOCK_RESETS){lockUntil=System.currentTimeMillis()+LOCK_DELAY_MS;lockResets++;}tone(sRotate);return;} }
    private int[][] rot(int[][] s, boolean cw){ int n=s.length; int[][] a=new int[n][n]; for(int r=0;r<n;r++) for(int c=0;c<n;c++) if(cw)a[c][n-1-r]=s[r][c]; else a[n-1-c][r]=s[r][c]; return a; }
    private int ghostY(){ int y=cur.y; while(ok(cur,0,y-cur.y+1,cur.s)) y++; return y; }
    private void lock(){ boolean lockOut=false; for(int r=0;r<cur.s.length;r++) for(int x=0;x<cur.s[r].length;x++) if(cur.s[r][x]!=0){int yy=cur.y+r; if(yy<0) lockOut=true; else board[yy][cur.x+x]=cur.type;} if(lockOut){over=true;return;} boolean spin=tspin(); int n=doClear(spin); if(n>0){areUntil=System.currentTimeMillis()+ARE_MS;clearing=true;}else{applyGarbage();spawn();} }
    private boolean tspin(){ if(cur==null||cur.type!=3||!cur.spin)return false; int cx=cur.x+1, cy=cur.y+1, n=0; int[][] pts={{cx-1,cy-1},{cx+1,cy-1},{cx-1,cy+1},{cx+1,cy+1}}; for(int[] q:pts){int x=q[0],y=q[1]; if(x<0||x>=C||y>=R||(y>=0&&board[y][x]!=0))n++;} return n>=3; }
    private int doClear(boolean spin){ int n=0; for(int y=R-1;y>=0;y--){ boolean full=true; for(int x=0;x<C;x++) if(board[y][x]==0){full=false;break;} if(full){ lineBurst(y); for(int yy=y;yy>0;yy--) board[yy]=board[yy-1].clone(); board[0]=new int[C]; n++; y++; }} if(n>0){ combo++; boolean difficult=n==4||spin; if(difficult)b2b++; else b2b=0; int base=new int[]{0,100,300,500,800}[n]; if(spin)base=n==1?800:n==2?1200:1600; int bonus=combo>0?combo*50:0; score+=(base+bonus)*level; lines+=n; level=lines/10+1; dropMs=Math.max(80,1000-(level-1)*90); int garbage=garbageFor(n, spin); if(garbage>0&&pendingGarbage>0){int cancel=Math.min(garbage,pendingGarbage); pendingGarbage-=cancel; garbage-=cancel;} if(!solo&&p2p!=null&&garbage>0)p2p.sendGarbage(garbage); fx((spin?"T-SPIN ":(n==4?"TETRIS ":"CLEAR "))+n+(combo>1?" COMBO "+combo:""), difficult||n>=3); tone(difficult?sTetris:sClear); } else { combo=-1; } return n; }
    private int garbageFor(int n, boolean spin){ int g=0; if(spin)g=n==1?2:n==2?4:6; else if(n==2)g=1; else if(n==3)g=2; else if(n==4)g=4; if(b2b>1&&(spin||n==4))g++; if(combo>1)g+=(combo<4?1:combo<6?2:3); g += badges/2; return g; }
    private void lineBurst(int row){ for(int i=0;i<18;i++) particles.add(new FxParticle(bx+rnd.nextFloat()*bw, by+(row+.5f)*cell, (rnd.nextFloat()-.5f)*bw*.7f, (rnd.nextFloat()-.5f)*90f, theme().blockFlash, 3+rnd.nextFloat()*5)); }
    private void applyGarbage(){ if(pendingGarbage>0&&garbageDueAt==0)garbageDueAt=System.currentTimeMillis()+1800; if(pendingGarbage<=0||System.currentTimeMillis()<garbageDueAt)return; while(pendingGarbage>0){ for(int y=0;y<R-1;y++) board[y]=board[y+1].clone(); int hole=rnd.nextInt(C); board[R-1]=new int[C]; for(int x=0;x<C;x++) board[R-1][x]=(x==hole)?0:7; pendingGarbage--; } garbageDueAt=0; fx("GARBAGE", true); tone(sGarbage); }
    private void hold(){ if(!canHold||cur==null||over)return; int t=cur.type; if(hold==0){hold=t; spawn();} else {cur=new Piece(hold); cur.x=3; cur.y=0; hold=t;} canHold=false; tone(sReady); }

    private void save(boolean toast) { if(!solo||cur==null||over)return; try{ JSONObject o=new JSONObject(); o.put("board", arr(board)); o.put("cur", cur.json()); o.put("next", next.json()); o.put("hold", hold); o.put("score", score); o.put("lines", lines); o.put("level", level); o.put("drop", dropMs); o.put("bagIndex", bagIndex); JSONArray bagArr=new JSONArray(); for(int v:bag) bagArr.put(v); o.put("bag", bagArr); sp.edit().putString("save", o.toString()).apply(); }catch(Exception ignored){} }
    private void load(){ try{ String s=sp.getString("save", null); if(s==null)return; JSONObject o=new JSONObject(s); board=board(o.getJSONArray("board")); cur=new Piece(o.getJSONObject("cur")); next=new Piece(o.getJSONObject("next")); hold=o.optInt("hold"); score=o.optInt("score"); lines=o.optInt("lines"); level=o.optInt("level",1); dropMs=o.optLong("drop",1000); bagIndex=o.optInt("bagIndex",7); JSONArray bagArr=o.optJSONArray("bag"); if(bagArr!=null&&bagArr.length()==7){ for(int i=0;i<7;i++) bag[i]=bagArr.getInt(i); } over=false; paused=false; settings=false; menu=false; }catch(Exception ignored){} }
    private void initSound(){ try{ toneGen=new ToneGenerator(AudioManager.STREAM_MUSIC, 45); sMove=ToneGenerator.TONE_PROP_BEEP; sRotate=ToneGenerator.TONE_PROP_ACK; sDrop=ToneGenerator.TONE_PROP_NACK; sClear=ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD; sTetris=ToneGenerator.TONE_CDMA_ABBR_ALERT; sGarbage=ToneGenerator.TONE_SUP_ERROR; sReady=ToneGenerator.TONE_PROP_PROMPT; }catch(Exception ignored){} }
    private void tone(int id){ if(toneGen!=null&&id!=0) toneGen.startTone(id, 70); }

    private JSONArray arr(int[][] b)throws Exception{ JSONArray a=new JSONArray(); for(int y=0;y<R;y++){JSONArray row=new JSONArray(); for(int x=0;x<C;x++) row.put(b[y][x]); a.put(row);} return a; }
    private int[][] board(JSONArray a)throws Exception{ int[][] b=new int[R][C]; for(int y=0;y<R;y++){JSONArray row=a.getJSONArray(y); for(int x=0;x<C;x++) b[y][x]=row.getInt(x);} return b; }

    private interface TextDone { void apply(String text); }
    private static class FxParticle { float x,y,vx,vy,size; int color; long born=System.currentTimeMillis(); FxParticle(float x,float y,float vx,float vy,int color,float size){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.color=color;this.size=size;} }
    private static class Btn { String text; int action; RectF r; Btn(String t,int a,RectF rr){text=t;action=a;r=rr;} }
    private static class Piece { int type,x=3,y=0,rot=0; boolean spin=false; int[][] s; Piece(int t){type=t; s=copy(SHAPES[t]); rot=0;} Piece(JSONObject o)throws Exception{type=o.getInt("type");x=o.getInt("x");y=o.getInt("y");rot=o.optInt("rot",0);JSONArray a=o.getJSONArray("s");s=new int[a.length()][a.length()];for(int r=0;r<a.length();r++){JSONArray row=a.getJSONArray(r);for(int c=0;c<row.length();c++)s[r][c]=row.getInt(c);}} JSONObject json()throws Exception{JSONObject o=new JSONObject();o.put("type",type);o.put("x",x);o.put("y",y);o.put("rot",rot);JSONArray a=new JSONArray();for(int[] rr:s){JSONArray row=new JSONArray();for(int v:rr)row.put(v);a.put(row);}o.put("s",a);return o;} static int[][] copy(int[][] m){int[][] n=new int[m.length][m.length];for(int i=0;i<m.length;i++)n[i]=m[i].clone();return n;} }
}
