package com.echo.tetrislan;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TetrisView extends View implements Runnable {
    private static final int C = 10, R = 20;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final SharedPreferences sp;
    private final List<Btn> btns = new ArrayList<>();
    private Thread loop;
    private P2pTransport p2p;
    private String p2pStatus = "P2P未启动";
    private final String roomName = "TETRIS";
    private final String roomPass = "1234";
    private final String playerName = "P" + (100 + rnd.nextInt(900));
    private String peerHost = null, peerName = "-", peerVia = "-";
    private int peerScore = 0, peerLines = 0;
    private int pendingGarbage = 0;
    private long lastP2pSend = 0;
    private boolean running = true, menu = true, over = true, paused = false;
    private boolean solo = true, canHold = true;
    private int[][] board = new int[R][C];
    private Piece cur, next;
    private int hold = 0, score = 0, lines = 0, level = 1;
    private long lastDrop = 0, dropMs = 1000, lastSave = 0;
    private float bx, by, cell, bw, bh;

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
            if (!menu && !over && !paused) {
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
        if (over) drawCenter(c, "游戏结束", "点开始重开");
        if (paused && !over) drawCenter(c, "暂停", "点暂停继续");
    }

    private void drawMenu(Canvas c, int w, int h) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE); p.setTextSize(42); c.drawText("Tetris Native", w/2f, h*0.18f, p);
        drawMenuButton(c, "单人模式", w*0.18f, h*0.28f, w*0.82f, h*0.36f, solo);
        drawMenuButton(c, "创建/加入房间", w*0.18f, h*0.38f, w*0.82f, h*0.46f, !solo);
        drawMenuButton(c, "同步开局", w*0.18f, h*0.52f, w*0.82f, h*0.60f, false);
        drawMenuButton(c, "读取存档", w*0.18f, h*0.62f, w*0.82f, h*0.70f, false);
        drawMenuButton(c, "手动保存", w*0.18f, h*0.72f, w*0.82f, h*0.80f, false);
        p.setColor(0xffaaaaaa); p.setTextSize(24);
        c.drawText(solo ? "单人支持自动/手动保存" : roomName + "/" + roomPass + " " + p2pStatus, w/2f, h*0.87f, p);
    }

    private void drawMenuButton(Canvas c, String text, float l, float t, float r, float b, boolean on) {
        p.setStyle(Paint.Style.FILL); p.setColor(on ? 0xff00e5ff : 0xff1e1e30);
        c.drawRoundRect(new RectF(l,t,r,b), 18, 18, p);
        p.setColor(on ? Color.BLACK : Color.WHITE); p.setTextSize(30); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, (l+r)/2, (t+b)/2 + 10, p);
    }

    private void layoutGame(int w, int h) {
        float top = 54, bottomControls = h - 150;
        bw = Math.min(w * 0.68f, (bottomControls - top) * C / (float)R);
        bh = bw * R / C;
        bx = 10; by = top;
        cell = bw / C;
        btns.clear();
        float bwBtn = Math.max(82, w * 0.28f), bhBtn = bwBtn * 0.55f, gap = 7;
        float y2 = h - bhBtn/2 - 10, y1 = y2 - bhBtn - gap;
        addBtn("旋转", 0, bwBtn/2+8, y1, bwBtn, bhBtn);
        addBtn("速降", 1, w/2f, y1, bwBtn, bhBtn);
        addBtn("逆旋", 2, w-bwBtn/2-8, y1, bwBtn, bhBtn);
        addBtn("左移", 3, bwBtn/2+8, y2, bwBtn, bhBtn);
        addBtn("软降", 4, w/2f, y2, bwBtn, bhBtn);
        addBtn("右移", 5, w-bwBtn/2-8, y2, bwBtn, bhBtn);
        float sideX = bx + bw + 10 + (w - (bx + bw + 10))/2;
        addBtn("暂停", 6, sideX, by + bh - bhBtn*1.7f, bwBtn*.9f, bhBtn);
        addBtn("暂存", 7, sideX, by + bh - bhBtn*.55f, bwBtn*.9f, bhBtn);
    }

    private void addBtn(String text, int action, float cx, float cy, float w, float h) { btns.add(new Btn(text, action, new RectF(cx-w/2, cy-h/2, cx+w/2, cy+h/2))); }

    private void drawTop(Canvas c) {
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(26); p.setColor(Color.WHITE);
        c.drawText("分 ", 10, 35, p); p.setColor(0xff00e5ff); c.drawText(String.valueOf(score), 48, 35, p);
        p.setColor(Color.WHITE); c.drawText("级 ", 145, 35, p); p.setColor(0xff00e5ff); c.drawText(String.valueOf(level), 183, 35, p);
        p.setColor(Color.WHITE); c.drawText("行 ", 245, 35, p); p.setColor(0xff00e5ff); c.drawText(String.valueOf(lines), 283, 35, p);
        p.setColor(0xffaaaaaa); p.setTextSize(20); c.drawText(solo ? "单人" : "P2P", getWidth()-70, 35, p);
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
        float sx = bx + bw + 12;
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(20); p.setColor(0xffaaaaaa);
        c.drawText("下一个", sx+45, by+18, p); mini(c, next, sx, by+28);
        c.drawText("暂存", sx+45, by+138, p); mini(c, hold==0?null:new Piece(hold), sx, by+148);
        c.drawText("对手", sx+45, by+258, p);
        p.setTextSize(17); c.drawText(solo ? "单人模式" : p2pStatus, sx+45, by+292, p);
        if (!solo) c.drawText(peerName + " " + peerScore + "/" + peerLines + " " + peerVia + " G" + pendingGarbage, sx+45, by+318, p);
    }

    private void mini(Canvas c, Piece pc, float x, float y) {
        p.setColor(0xff121220); c.drawRoundRect(new RectF(x,y,x+90,y+90), 8, 8, p);
        if (pc == null) return;
        float z = 20;
        for (int r=0;r<pc.s.length;r++) for (int col=0;col<pc.s[r].length;col++) if (pc.s[r][col] != 0) {
            p.setColor(COLORS[pc.type]); c.drawRect(x+8+col*z, y+8+r*z, x+8+(col+1)*z-2, y+8+(r+1)*z-2, p);
        }
    }

    private void drawBtns(Canvas c) {
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(25);
        for (Btn b: btns) {
            p.setColor(b.action==6 ? 0xffffab40 : 0xcc1e1e30);
            c.drawRoundRect(b.r, 16, 16, p);
            p.setColor(Color.WHITE); c.drawText(b.text, b.r.centerX(), b.r.centerY()+9, p);
        }
    }

    private void drawCenter(Canvas c, String a, String b) {
        p.setTextAlign(Paint.Align.CENTER); p.setColor(0xdd000000); c.drawRoundRect(new RectF(50,getHeight()/2f-90,getWidth()-50,getHeight()/2f+80),20,20,p);
        p.setColor(Color.WHITE); p.setTextSize(38); c.drawText(a, getWidth()/2f, getHeight()/2f-20, p);
        p.setColor(0xffaaaaaa); p.setTextSize(24); c.drawText(b, getWidth()/2f, getHeight()/2f+28, p);
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
        for (Btn b: btns) if (b.r.contains(x,y)) { act(b.action); return true; }
        return true;
    }

    private boolean touchMenu(float x, float y) {
        int w=getWidth(), h=getHeight();
        if (hit(x,y,w*.18f,h*.28f,w*.82f,h*.36f)) { solo=true; return true; }
        if (hit(x,y,w*.18f,h*.38f,w*.82f,h*.46f)) { solo=false; startP2p(); return true; }
        if (hit(x,y,w*.18f,h*.52f,w*.82f,h*.60f)) { if (solo) start(); else syncStart(); return true; }
        if (hit(x,y,w*.18f,h*.62f,w*.82f,h*.70f)) { load(); return true; }
        if (hit(x,y,w*.18f,h*.72f,w*.82f,h*.80f)) { save(true); return true; }
        return true;
    }
    private boolean hit(float x,float y,float l,float t,float r,float b){return x>=l&&x<=r&&y>=t&&y<=b;}

    private void startP2p() {
        if (p2p != null) return;
        p2pStatus = "房间广播中";
        p2p = new P2pTransport(roomName, roomPass, playerName, new P2pTransport.Listener() {
            @Override public void onPeer(String host, String name, int score, int lines, String via) {
                peerHost = host;
                peerName = name;
                peerScore = score;
                peerLines = lines;
                peerVia = via;
                p2pStatus = "已连接 " + host;
            }
            @Override public void onStart(long seed) { start(seed); p2pStatus = "同步开局"; }
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
        start(seed);
        p2p.sendStart(seed);
        p2pStatus = "已发起开局";
    }

    private void act(int a) {
        if (a==6) { paused=!paused; return; }
        if (a==7) { hold(); return; }
        if (over) return;
        if (a==0) rotate(true); else if (a==2) rotate(false); else if (a==3) move(-1,0); else if (a==5) move(1,0); else if (a==4) move(0,1); else if (a==1) { while(move(0,1)); lock(); }
    }

    private void start() { start(System.currentTimeMillis()); }
    private void start(long seed) { rnd.setSeed(seed); board=new int[R][C]; score=0; lines=0; level=1; dropMs=1000; hold=0; pendingGarbage=0; canHold=true; over=false; paused=false; next=randomPiece(); spawn(); lastDrop=System.currentTimeMillis(); menu=false; }
    private Piece randomPiece(){ return new Piece(1+rnd.nextInt(7)); }
    private void spawn(){ cur=next==null?randomPiece():next; next=randomPiece(); cur.x=3; cur.y=0; canHold=true; if(!ok(cur,0,0,cur.s)) over=true; }
    private boolean move(int dx,int dy){ if(cur==null||!ok(cur,dx,dy,cur.s)) return false; cur.x+=dx; cur.y+=dy; return true; }
    private boolean ok(Piece pc,int dx,int dy,int[][] s){ for(int r=0;r<s.length;r++) for(int x=0;x<s[r].length;x++) if(s[r][x]!=0){int xx=pc.x+x+dx, yy=pc.y+r+dy; if(xx<0||xx>=C||yy>=R) return false; if(yy>=0&&board[yy][xx]!=0)return false;} return true; }
    private void rotate(boolean cw){ int[][] ns=rot(cur.s,cw); int[] kicks={0,-1,1,-2,2}; for(int k:kicks) if(ok(cur,k,0,ns)){cur.s=ns;cur.x+=k;return;} }
    private int[][] rot(int[][] s, boolean cw){ int n=s.length; int[][] a=new int[n][n]; for(int r=0;r<n;r++) for(int c=0;c<n;c++) if(cw)a[c][n-1-r]=s[r][c]; else a[n-1-c][r]=s[r][c]; return a; }
    private int ghostY(){ int y=cur.y; while(ok(cur,0,y-cur.y+1,cur.s)) y++; return y; }
    private void lock(){ for(int r=0;r<cur.s.length;r++) for(int x=0;x<cur.s[r].length;x++) if(cur.s[r][x]!=0){int yy=cur.y+r; if(yy>=0) board[yy][cur.x+x]=cur.type;} clear(); applyGarbage(); spawn(); }
    private void clear(){ int n=0; for(int y=R-1;y>=0;y--){ boolean full=true; for(int x=0;x<C;x++) if(board[y][x]==0){full=false;break;} if(full){ for(int yy=y;yy>0;yy--) board[yy]=board[yy-1].clone(); board[0]=new int[C]; n++; y++; }} if(n>0){ score += new int[]{0,100,300,500,800}[n]*level; lines += n; level=lines/10+1; dropMs=Math.max(90,1000-(level-1)*80); if(!solo && p2p!=null && n>=2) p2p.sendGarbage(n-1); }}
    private void applyGarbage(){ while(pendingGarbage>0){ for(int y=0;y<R-1;y++) board[y]=board[y+1].clone(); int hole=rnd.nextInt(C); board[R-1]=new int[C]; for(int x=0;x<C;x++) board[R-1][x]=(x==hole)?0:7; pendingGarbage--; }}
    private void hold(){ if(!canHold||cur==null||over)return; int t=cur.type; if(hold==0){hold=t; spawn();} else {cur=new Piece(hold); cur.x=3; cur.y=0; hold=t;} canHold=false; }

    private void save(boolean toast) { if(!solo||cur==null||over)return; try{ JSONObject o=new JSONObject(); o.put("board", arr(board)); o.put("cur", cur.json()); o.put("next", next.json()); o.put("hold", hold); o.put("score", score); o.put("lines", lines); o.put("level", level); o.put("drop", dropMs); sp.edit().putString("save", o.toString()).apply(); }catch(Exception ignored){} }
    private void load(){ try{ String s=sp.getString("save", null); if(s==null)return; JSONObject o=new JSONObject(s); board=board(o.getJSONArray("board")); cur=new Piece(o.getJSONObject("cur")); next=new Piece(o.getJSONObject("next")); hold=o.optInt("hold"); score=o.optInt("score"); lines=o.optInt("lines"); level=o.optInt("level",1); dropMs=o.optLong("drop",1000); over=false; paused=false; menu=false; }catch(Exception ignored){} }
    private JSONArray arr(int[][] b)throws Exception{ JSONArray a=new JSONArray(); for(int y=0;y<R;y++){JSONArray row=new JSONArray(); for(int x=0;x<C;x++) row.put(b[y][x]); a.put(row);} return a; }
    private int[][] board(JSONArray a)throws Exception{ int[][] b=new int[R][C]; for(int y=0;y<R;y++){JSONArray row=a.getJSONArray(y); for(int x=0;x<C;x++) b[y][x]=row.getInt(x);} return b; }

    private static class Btn { String text; int action; RectF r; Btn(String t,int a,RectF rr){text=t;action=a;r=rr;} }
    private static class Piece { int type,x=3,y=0; int[][] s; Piece(int t){type=t; s=copy(SHAPES[t]);} Piece(JSONObject o)throws Exception{type=o.getInt("type");x=o.getInt("x");y=o.getInt("y");JSONArray a=o.getJSONArray("s");s=new int[a.length()][a.length()];for(int r=0;r<a.length();r++){JSONArray row=a.getJSONArray(r);for(int c=0;c<row.length();c++)s[r][c]=row.getInt(c);}} JSONObject json()throws Exception{JSONObject o=new JSONObject();o.put("type",type);o.put("x",x);o.put("y",y);JSONArray a=new JSONArray();for(int[] rr:s){JSONArray row=new JSONArray();for(int v:rr)row.put(v);a.put(row);}o.put("s",a);return o;} static int[][] copy(int[][] m){int[][] n=new int[m.length][m.length];for(int i=0;i<m.length;i++)n[i]=m[i].clone();return n;} }
}
