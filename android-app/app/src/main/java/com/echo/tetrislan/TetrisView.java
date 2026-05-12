package com.echo.tetrislan;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.text.InputType;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TetrisView extends View implements Runnable {
    private static final int C = 10, R = 20;
    private static final String APP_VERSION = "v1.3";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final SharedPreferences sp;
    private final List<Btn> btns = new ArrayList<>();
    private Thread loop;
    private P2pTransport p2p;
    private String p2pStatus = "P2P未启动";
    private String roomName = "TETRIS";
    private final String roomPass = "1234";
    private final String playerName = "P" + (100 + rnd.nextInt(900));
    private String peerHost = null, peerName = "-", peerVia = "-";
    private String foundRoom = "-", foundHost = "-", foundName = "-";
    private boolean selfReady = false, peerReady = false;
    private long pendingStartAt = 0, pendingStartSeed = 0;
    private final List<String> chat = new ArrayList<>();
    private int peerScore = 0, peerLines = 0;
    private int pendingGarbage = 0;
    private long lastP2pSend = 0;
    private boolean running = true, menu = true, over = true, paused = false, settings = false;
    private boolean solo = true, canHold = true;
    private int menuPage = 0; // 0 main, 1 solo actions, 2 multiplayer actions
    private int[][] board = new int[R][C];
    private Piece cur, next;
    private int hold = 0, score = 0, lines = 0, level = 1;
    private long lastDrop = 0, dropMs = 1000, lastSave = 0;
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

    public TetrisView(Context c) {
        super(c);
        sp = c.getSharedPreferences("tetris_native", Context.MODE_PRIVATE);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        setFocusable(true);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loop = new Thread(this, "tetris-loop");
        loop.start();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        if (p2p != null) p2p.stop();
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
                if (now - lastDrop > dropMs) {
                    if (!move(0, 1)) lock();
                    lastDrop = now;
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
        p.setColor(0xff080810);
        c.drawRect(0, 0, w, h, p);
        if (menu) { drawMenu(c, w, h); return; }
        layoutGame(w, h);
        drawTop(c);
        drawBoard(c);
        drawSide(c);
        drawBtns(c);
        if (over) drawCenter(c, "游戏结束", "点设置或主界面");
        if (paused && !over) drawCenter(c, "暂停", "点暂停继续");
        if (settings) drawSettings(c, w, h);
    }

    private void drawMenu(Canvas c, int w, int h) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE); p.setTextSize(44); c.drawText("Tetris Native " + APP_VERSION, w/2f, h*0.16f, p);
        if (menuPage == 0) {
            drawMenuButton(c, "单人模式", w*0.14f, h*0.30f, w*0.86f, h*0.39f, false);
            drawMenuButton(c, "多人模式", w*0.14f, h*0.43f, w*0.86f, h*0.52f, false);
            p.setColor(0xffaaaaaa); p.setTextSize(24);
            c.drawText("先选模式，再开局/读取/保存", w/2f, h*0.66f, p);
            return;
        }
        boolean multi = menuPage == 2;
        p.setColor(0xff00e5ff); p.setTextSize(28); c.drawText(multi ? "多人大厅" : "单人模式", w/2f, h*0.25f, p);
        if (!multi) {
            drawMenuButton(c, "开始新局", w*0.14f, h*0.34f, w*0.86f, h*0.43f, false);
            drawMenuButton(c, "读取存档", w*0.14f, h*0.46f, w*0.86f, h*0.55f, false);
            drawMenuButton(c, "手动保存", w*0.14f, h*0.58f, w*0.86f, h*0.67f, false);
            drawMenuButton(c, "返回主菜单", w*0.14f, h*0.73f, w*0.86f, h*0.82f, false);
            p.setColor(0xffaaaaaa); p.setTextSize(22); c.drawText("单人支持自动/手动保存", w/2f, h*0.90f, p);
            return;
        }
        drawMenuButton(c, "创建房间", w*0.08f, h*0.31f, w*0.46f, h*0.39f, false);
        drawMenuButton(c, "输入房号", w*0.54f, h*0.31f, w*0.92f, h*0.39f, false);
        drawMenuButton(c, "加入发现", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false);
        drawMenuButton(c, selfReady ? "取消准备" : "准备", w*0.54f, h*0.42f, w*0.92f, h*0.50f, selfReady);
        drawMenuButton(c, "聊天", w*0.08f, h*0.53f, w*0.46f, h*0.61f, false);
        drawMenuButton(c, "返回主菜单", w*0.54f, h*0.53f, w*0.92f, h*0.61f, false);
        p.setColor(Color.WHITE); p.setTextSize(22);
        c.drawText("房间 " + roomName + "  我:" + (selfReady?"已准备":"未准备") + " 对方:" + (peerReady?"已准备":"未准备"), w/2f, h*0.68f, p);
        p.setColor(0xffaaaaaa); p.setTextSize(19);
        c.drawText("发现 " + foundRoom + " / " + foundName + " " + foundHost, w/2f, h*0.72f, p);
        c.drawText(p2pStatus, w/2f, h*0.76f, p);
        int start = Math.max(0, chat.size() - 4);
        for (int i=start;i<chat.size();i++) c.drawText(chat.get(i), w/2f, h*(0.81f + 0.035f*(i-start)), p);
        if (pendingStartAt > 0) {
            long left = Math.max(0, (pendingStartAt - System.currentTimeMillis() + 999) / 1000);
            p.setColor(0xffffab40); p.setTextSize(34); c.drawText("倒计时 " + left, w/2f, h*0.96f, p);
        }
    }

    private void drawMenuButton(Canvas c, String text, float l, float t, float r, float b, boolean on) {
        p.setStyle(Paint.Style.FILL); p.setColor(on ? 0xff00e5ff : 0xff1e1e30);
        c.drawRoundRect(new RectF(l,t,r,b), 20, 20, p);
        p.setColor(on ? Color.BLACK : Color.WHITE); p.setTextSize(30); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, (l+r)/2, (t+b)/2 + 10, p);
    }

    private void layoutGame(int w, int h) {
        float top = 60, bottomControls = h - 150;
        bw = Math.min(w * 0.74f, (bottomControls - top) * C / (float)R);
        bh = bw * R / C;
        bx = 8; by = top;
        cell = bw / C;
        btns.clear();
        float topW = Math.max(96, w * 0.22f), topH = 48;
        topBtnY = 30;
        addBtn("设置", 8, w - topW * 1.55f, topBtnY, topW, topH);
        addBtn("主界面", 9, w - topW * 0.52f, topBtnY, topW, topH);
        float bwBtn = Math.max(102, w * 0.31f), bhBtn = bwBtn * 0.56f, gap = 9;
        float y2 = h - bhBtn/2 - 8, y1 = y2 - bhBtn - gap;
        addBtn("旋转", 0, bwBtn/2+6, y1, bwBtn, bhBtn);
        addBtn("速降", 1, w/2f, y1, bwBtn, bhBtn);
        addBtn("逆旋", 2, w-bwBtn/2-6, y1, bwBtn, bhBtn);
        addBtn("左移", 3, bwBtn/2+6, y2, bwBtn, bhBtn);
        addBtn("软降", 4, w/2f, y2, bwBtn, bhBtn);
        addBtn("右移", 5, w-bwBtn/2-6, y2, bwBtn, bhBtn);
        float sideLeft = bx + bw + 8;
        float sideW = Math.max(70, w - sideLeft - 6);
        float sideX = sideLeft + sideW / 2;
        addBtn("暂停", 6, sideX, by + bh - bhBtn*1.62f, sideW, bhBtn);
        addBtn("暂存", 7, sideX, by + bh - bhBtn*.52f, sideW, bhBtn);
    }

    private void addBtn(String text, int action, float cx, float cy, float w, float h) { btns.add(new Btn(text, action, new RectF(cx-w/2, cy-h/2, cx+w/2, cy+h/2))); }

    private void drawTop(Canvas c) {
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(31); p.setColor(Color.WHITE);
        c.drawText("分", 8, 42, p); p.setColor(0xff00e5ff); c.drawText(String.valueOf(score), 50, 42, p);
        p.setColor(Color.WHITE); c.drawText("级", 122, 42, p); p.setColor(0xff00e5ff); c.drawText(String.valueOf(level), 164, 42, p);
        p.setColor(Color.WHITE); c.drawText("行", 214, 42, p); p.setColor(0xff00e5ff); c.drawText(String.valueOf(lines), 256, 42, p);
        p.setColor(0xff888899); p.setTextSize(22); c.drawText((solo ? "单人" : "P2P") + " " + APP_VERSION, 310, 42, p);
    }

    private void drawBoard(Canvas c) {
        p.setStyle(Paint.Style.FILL); p.setColor(0xff121220); c.drawRoundRect(new RectF(bx,by,bx+bw,by+bh), 8, 8, p);
        for (int y=0;y<R;y++) for (int x=0;x<C;x++) if (board[y][x] != 0) block(c,x,y,board[y][x],1f);
        if (cur != null) {
            int gy = ghostY();
            drawPiece(c, cur, gy, 0.28f);
            drawPiece(c, cur, cur.y, 1f);
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xff2a2a40); c.drawRect(bx,by,bx+bw,by+bh,p); p.setStyle(Paint.Style.FILL);
    }

    private void drawSide(Canvas c) {
        float sx = bx + bw + 8;
        float sideW = Math.max(70, getWidth() - sx - 6);
        float cx = sx + sideW / 2;
        float box = Math.min(92, sideW);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(23); p.setColor(0xffaaaaaa);
        c.drawText("下一个", cx, by+24, p); mini(c, next, cx-box/2, by+36, box);
        c.drawText("暂存", cx, by+146, p); mini(c, hold==0?null:new Piece(hold), cx-box/2, by+158, box);
        c.drawText("对手", cx, by+268, p);
        p.setTextSize(18); c.drawText(solo ? "单人模式" : p2pStatus, cx, by+300, p);
        if (!solo) c.drawText(peerName + " " + peerScore + "/" + peerLines + " " + peerVia + " G" + pendingGarbage, cx, by+326, p);
    }

    private void mini(Canvas c, Piece pc, float x, float y, float box) {
        p.setColor(0xff121220); c.drawRoundRect(new RectF(x,y,x+box,y+box), 8, 8, p);
        if (pc == null) return;
        float z = box / 4.5f;
        for (int r=0;r<pc.s.length;r++) for (int col=0;col<pc.s[r].length;col++) if (pc.s[r][col] != 0) {
            p.setColor(COLORS[pc.type]); c.drawRect(x+6+col*z, y+8+r*z, x+6+(col+1)*z-2, y+8+(r+1)*z-2, p);
        }
    }

    private void drawBtns(Canvas c) {
        p.setTextAlign(Paint.Align.CENTER);
        for (Btn b: btns) {
            if (b.action == 8 || b.action == 9) p.setTextSize(22); else p.setTextSize(30);
            p.setColor(b.action==6 ? 0xffffab40 : (b.action>=8 ? 0xff252542 : 0xcc1e1e30));
            c.drawRoundRect(b.r, 16, 16, p);
            p.setColor(Color.WHITE); c.drawText(b.text, b.r.centerX(), b.r.centerY()+(b.action>=8?8:11), p);
        }
    }

    private void drawCenter(Canvas c, String a, String b) {
        p.setTextAlign(Paint.Align.CENTER); p.setColor(0xdd000000); c.drawRoundRect(new RectF(50,getHeight()/2f-90,getWidth()-50,getHeight()/2f+80),20,20,p);
        p.setColor(Color.WHITE); p.setTextSize(38); c.drawText(a, getWidth()/2f, getHeight()/2f-20, p);
        p.setColor(0xffaaaaaa); p.setTextSize(24); c.drawText(b, getWidth()/2f, getHeight()/2f+28, p);
    }

    private void drawSettings(Canvas c, int w, int h) {
        p.setColor(0xee050509); c.drawRect(0, 0, w, h, p);
        p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(38); c.drawText("设置", w/2f, h*0.22f, p);
        drawMenuButton(c, "继续游戏", w*.16f, h*.32f, w*.84f, h*.40f, false);
        drawMenuButton(c, "读取存档", w*.16f, h*.43f, w*.84f, h*.51f, false);
        drawMenuButton(c, "手动保存", w*.16f, h*.54f, w*.84f, h*.62f, false);
        drawMenuButton(c, "返回主界面", w*.16f, h*.68f, w*.84f, h*.76f, false);
    }

    private void block(Canvas c, int x, int y, int type, float alpha) {
        p.setColor(applyAlpha(COLORS[type], alpha));
        float l=bx+x*cell, t=by+y*cell;
        c.drawRect(l+1,t+1,l+cell-2,t+cell-2,p);
    }
    private int applyAlpha(int color, float alpha) { return (Math.round(255*alpha)<<24) | (color & 0x00ffffff); }
    private void drawPiece(Canvas c, Piece pc, int yy, float alpha) { for(int r=0;r<pc.s.length;r++) for(int x=0;x<pc.s[r].length;x++) if(pc.s[r][x]!=0) block(c,pc.x+x,yy+r,pc.type,alpha); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        float x=e.getX(), y=e.getY();
        if (menu) return touchMenu(x,y);
        if (settings) return touchSettings(x, y);
        for (Btn b: btns) if (b.r.contains(x,y)) { act(b.action); return true; }
        return true;
    }

    private boolean touchMenu(float x, float y) {
        int w=getWidth(), h=getHeight();
        if (menuPage == 0) {
            if (hit(x,y,w*.14f,h*.30f,w*.86f,h*.39f)) { solo=true; menuPage=1; return true; }
            if (hit(x,y,w*.14f,h*.43f,w*.86f,h*.52f)) { solo=false; menuPage=2; startP2p(); return true; }
            return true;
        }
        if (menuPage == 1) {
            if (hit(x,y,w*.14f,h*.34f,w*.86f,h*.43f)) { start(); return true; }
            if (hit(x,y,w*.14f,h*.46f,w*.86f,h*.55f)) { load(); return true; }
            if (hit(x,y,w*.14f,h*.58f,w*.86f,h*.67f)) { save(true); return true; }
            if (hit(x,y,w*.14f,h*.73f,w*.86f,h*.82f)) { menuPage=0; return true; }
            return true;
        }
        if (hit(x,y,w*.08f,h*.31f,w*.46f,h*.39f)) { createRoom(); return true; }
        if (hit(x,y,w*.54f,h*.31f,w*.92f,h*.39f)) { askRoom(); return true; }
        if (hit(x,y,w*.08f,h*.42f,w*.46f,h*.50f)) { joinFound(); return true; }
        if (hit(x,y,w*.54f,h*.42f,w*.92f,h*.50f)) { toggleReady(); return true; }
        if (hit(x,y,w*.08f,h*.53f,w*.46f,h*.61f)) { askChat(); return true; }
        if (hit(x,y,w*.54f,h*.53f,w*.92f,h*.61f)) { stopP2p(); menuPage=0; return true; }
        return true;
    }

    private boolean touchSettings(float x, float y) {
        int w=getWidth(), h=getHeight();
        if (hit(x,y,w*.16f,h*.32f,w*.84f,h*.40f)) { settings=false; paused=false; return true; }
        if (hit(x,y,w*.16f,h*.43f,w*.84f,h*.51f)) { if (solo) load(); settings=false; return true; }
        if (hit(x,y,w*.16f,h*.54f,w*.84f,h*.62f)) { save(true); return true; }
        if (hit(x,y,w*.16f,h*.68f,w*.84f,h*.76f)) { goMenu(); return true; }
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
                p2pStatus = "已连接 " + host;
            }
            @Override public void onReady(String host, String name, boolean ready) {
                peerHost = host; peerName = name; peerReady = ready;
                addChat("系统: " + name + (ready ? " 已准备" : " 取消准备"));
                maybeCountdown();
            }
            @Override public void onChat(String host, String name, String text) { addChat(name + ": " + text); }
            @Override public void onStart(long seed, long startAt) {
                pendingStartSeed = seed; pendingStartAt = startAt; p2pStatus = "3秒后开始";
            }
            @Override public void onGarbage(int rows) { pendingGarbage += rows; p2pStatus = "收到垃圾 " + rows; }
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
        roomName = "R" + (1000 + rnd.nextInt(9000));
        restartP2p();
        addChat("系统: 已创建房间 " + roomName);
    }

    private void askRoom() {
        askText("输入房间号", roomName, text -> {
            roomName = clean(text, "TETRIS");
            restartP2p();
            addChat("系统: 已进入房间 " + roomName);
        });
    }

    private void joinFound() {
        if ("-".equals(foundRoom)) return;
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
        if (selfReady && peerReady && pendingStartAt == 0 && playerName.compareTo(peerName) < 0) syncStart();
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
        chat.add(line.length() > 28 ? line.substring(0, 28) : line);
        while (chat.size() > 20) chat.remove(0);
    }

    private void restartP2p() {
        stopP2p();
        selfReady = false; peerReady = false; pendingStartAt = 0; pendingStartSeed = 0;
        startP2p();
    }

    private void stopP2p() {
        if (p2p != null) { p2p.stop(); p2p = null; }
        p2pStatus = "P2P未启动";
        selfReady = false; peerReady = false;
    }

    private void act(int a) {
        if (a==8) { settings=true; paused=true; return; }
        if (a==9) { goMenu(); return; }
        if (a==6) { paused=!paused; return; }
        if (a==7) { hold(); return; }
        if (over) return;
        if (a==0) rotate(true); else if (a==2) rotate(false); else if (a==3) move(-1,0); else if (a==5) move(1,0); else if (a==4) move(0,1); else if (a==1) { while(move(0,1)); lock(); }
    }

    private void goMenu() {
        if (solo) save(false);
        menu = true;
        settings = false;
        paused = false;
        menuPage = solo ? 1 : 2;
    }

    private void start() { start(System.currentTimeMillis()); }
    private void start(long seed) { rnd.setSeed(seed); board=new int[R][C]; score=0; lines=0; level=1; dropMs=1000; hold=0; pendingGarbage=0; canHold=true; over=false; paused=false; settings=false; next=randomPiece(); spawn(); lastDrop=System.currentTimeMillis(); menu=false; }
    private Piece randomPiece(){ return new Piece(1+rnd.nextInt(7)); }
    private void spawn(){ cur=next==null?randomPiece():next; next=randomPiece(); cur.x=3; cur.y=0; canHold=true; if(!ok(cur,0,0,cur.s)) over=true; }
    private boolean move(int dx,int dy){ if(cur==null||!ok(cur,dx,dy,cur.s)) return false; cur.x+=dx; cur.y+=dy; return true; }
    private boolean ok(Piece pc,int dx,int dy,int[][] s){ for(int r=0;r<s.length;r++) for(int x=0;x<s[r].length;x++) if(s[r][x]!=0){int xx=pc.x+x+dx, yy=pc.y+r+dy; if(xx<0||xx>=C||yy>=R) return false; if(yy>=0&&board[yy][xx]!=0)return false;} return true; }
    private void rotate(boolean cw){ if(cur==null)return; int[][] ns=rot(cur.s,cw); int[] kicks={0,-1,1,-2,2}; for(int k:kicks) if(ok(cur,k,0,ns)){cur.s=ns;cur.x+=k;return;} }
    private int[][] rot(int[][] s, boolean cw){ int n=s.length; int[][] a=new int[n][n]; for(int r=0;r<n;r++) for(int c=0;c<n;c++) if(cw)a[c][n-1-r]=s[r][c]; else a[n-1-c][r]=s[r][c]; return a; }
    private int ghostY(){ int y=cur.y; while(ok(cur,0,y-cur.y+1,cur.s)) y++; return y; }
    private void lock(){ for(int r=0;r<cur.s.length;r++) for(int x=0;x<cur.s[r].length;x++) if(cur.s[r][x]!=0){int yy=cur.y+r; if(yy>=0) board[yy][cur.x+x]=cur.type;} clear(); applyGarbage(); spawn(); }
    private void clear(){ int n=0; for(int y=R-1;y>=0;y--){ boolean full=true; for(int x=0;x<C;x++) if(board[y][x]==0){full=false;break;} if(full){ for(int yy=y;yy>0;yy--) board[yy]=board[yy-1].clone(); board[0]=new int[C]; n++; y++; }} if(n>0){ score += new int[]{0,100,300,500,800}[n]*level; lines += n; level=lines/10+1; dropMs=Math.max(90,1000-(level-1)*80); if(!solo && p2p!=null && n>=2) p2p.sendGarbage(n-1); }}
    private void applyGarbage(){ while(pendingGarbage>0){ for(int y=0;y<R-1;y++) board[y]=board[y+1].clone(); int hole=rnd.nextInt(C); board[R-1]=new int[C]; for(int x=0;x<C;x++) board[R-1][x]=(x==hole)?0:7; pendingGarbage--; }}
    private void hold(){ if(!canHold||cur==null||over)return; int t=cur.type; if(hold==0){hold=t; spawn();} else {cur=new Piece(hold); cur.x=3; cur.y=0; hold=t;} canHold=false; }

    private void save(boolean toast) { if(!solo||cur==null||over)return; try{ JSONObject o=new JSONObject(); o.put("board", arr(board)); o.put("cur", cur.json()); o.put("next", next.json()); o.put("hold", hold); o.put("score", score); o.put("lines", lines); o.put("level", level); o.put("drop", dropMs); sp.edit().putString("save", o.toString()).apply(); }catch(Exception ignored){} }
    private void load(){ try{ String s=sp.getString("save", null); if(s==null)return; JSONObject o=new JSONObject(s); board=board(o.getJSONArray("board")); cur=new Piece(o.getJSONObject("cur")); next=new Piece(o.getJSONObject("next")); hold=o.optInt("hold"); score=o.optInt("score"); lines=o.optInt("lines"); level=o.optInt("level",1); dropMs=o.optLong("drop",1000); over=false; paused=false; settings=false; menu=false; }catch(Exception ignored){} }
    private JSONArray arr(int[][] b)throws Exception{ JSONArray a=new JSONArray(); for(int y=0;y<R;y++){JSONArray row=new JSONArray(); for(int x=0;x<C;x++) row.put(b[y][x]); a.put(row);} return a; }
    private int[][] board(JSONArray a)throws Exception{ int[][] b=new int[R][C]; for(int y=0;y<R;y++){JSONArray row=a.getJSONArray(y); for(int x=0;x<C;x++) b[y][x]=row.getInt(x);} return b; }

    private interface TextDone { void apply(String text); }
    private static class Btn { String text; int action; RectF r; Btn(String t,int a,RectF rr){text=t;action=a;r=rr;} }
    private static class Piece { int type,x=3,y=0; int[][] s; Piece(int t){type=t; s=copy(SHAPES[t]);} Piece(JSONObject o)throws Exception{type=o.getInt("type");x=o.getInt("x");y=o.getInt("y");JSONArray a=o.getJSONArray("s");s=new int[a.length()][a.length()];for(int r=0;r<a.length();r++){JSONArray row=a.getJSONArray(r);for(int c=0;c<row.length();c++)s[r][c]=row.getInt(c);}} JSONObject json()throws Exception{JSONObject o=new JSONObject();o.put("type",type);o.put("x",x);o.put("y",y);JSONArray a=new JSONArray();for(int[] rr:s){JSONArray row=new JSONArray();for(int v:rr)row.put(v);a.put(row);}o.put("s",a);return o;} static int[][] copy(int[][] m){int[][] n=new int[m.length][m.length];for(int i=0;i<m.length;i++)n[i]=m[i].clone();return n;} }
}
