package com.echo.tetrislan.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import com.echo.tetrislan.core.Piece;
import com.echo.tetrislan.net.PeerInfo;
import java.util.Map;

public final class HudRenderer {
    private final Paint p;

    public HudRenderer(Paint p) {
        this.p = p;
    }

    public void drawTop(Canvas c, int statusBarH, boolean solo, boolean menu,
                        int gameMode, int soloStage, int trainTech, Theme theme) {
        if (!solo || menu) return;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(28);
        p.setColor(theme.textMuted);
        float sy = statusBarH + 8;
        String stageInfo = "";
        if (gameMode == 1) stageInfo = "S" + soloStage + " 目标" + (40 * soloStage) + "行";
        else if (gameMode == 2) stageInfo = "S" + soloStage + " 目标" + (soloStage * 5000) + "分";
        else if (gameMode == 5) stageInfo = "S" + soloStage + " 目标" + (10 * soloStage) + "行";
        else if (gameMode == 3) stageInfo = "S" + soloStage + " 目标" + (150 * soloStage) + "行";
        else if (gameMode == 6) stageInfo = "练习: " + com.echo.tetrislan.modes.TrainingModeController.trainTechName(trainTech);
        if (!stageInfo.isEmpty()) {
            c.drawText(stageInfo, 12, sy + 100, p);
        }
    }

    public void drawSide(Canvas c, float bx, float bw, float by, int w, boolean solo,
                         Piece next, int hold, String modeName, String modeProgress,
                         Map<String, PeerInfo> peerInfos,
                         int combo, int b2b, int kos, int badges, Theme theme) {
        float sx = bx + bw + 8;
        float sideW = Math.max(110, w - sx - 6);
        float cx = sx + sideW / 2;
        float box = Math.min(160, sideW);
        float y = by + 36;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(42);
        p.setColor(theme.textMuted);
        c.drawText("下一个", cx, y, p);
        mini(c, next, cx - box / 2, y + 16, box, theme);
        y += 16 + box + 20;
        c.drawText("暂存", cx, y, p);
        mini(c, hold == 0 ? null : new Piece(hold), cx - box / 2, y + 16, box, theme);
        y += 16 + box + 24;
        c.drawText(solo ? "模式" : "对手", cx, y, p);
        if (solo) {
            p.setColor(theme.score);
            p.setTextSize(32);
            c.drawText(modeName, cx, y + 38, p);
            p.setColor(theme.text);
            p.setTextSize(24);
            String[] lines = modeProgress.split("\n");
            float lineH = 28;
            for (int i = 0; i < lines.length; i++) {
                c.drawText(lines[i], cx, y + 68 + i * lineH, p);
            }
            y += 68 + lines.length * lineH + 8;
        } else {
            p.setTextSize(22);
            p.setColor(theme.score);
            for (PeerInfo pi : peerInfos.values()) {
                y += 28;
                String net = (System.currentTimeMillis() - pi.lastUpdateMs) > 2000 ? "!" : "";
                String status = pi.over ? "KO" : (pi.score + "/" + pi.lines + " L" + pi.level);
                c.drawText(pi.name + net + " " + status, cx, y, p);
                if (pi.board != null) {
                    y += 6;
                    float miniCell = Math.min(8, (sideW - 20) / 10f);
                    float miniBoardW = miniCell * 10;
                    float miniBoardH = miniCell * 20;
                    float miniX = cx - miniBoardW / 2;
                    p.setColor(theme.board);
                    c.drawRect(miniX, y, miniX + miniBoardW, y + miniBoardH, p);
                    for (int r = 0; r < 20; r++) {
                        for (int col = 0; col < 10; col++) {
                            int val = pi.board[r][col];
                            if (val != 0) {
                                p.setColor(theme.colors[val]);
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
            p.setColor(theme.textMuted);
        }
        y += 50;
        p.setColor(theme.score);
        p.setTextSize(32);
        float statLeft = sx + 10;
        float statRight = sx + sideW - 10;
        float lineH = 34;
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("连击", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.valueOf(Math.max(0, combo)), statRight, y, p);
        y += lineH;
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("B2B", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.valueOf(b2b), statRight, y, p);
        y += lineH;
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("KO", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.valueOf(kos), statRight, y, p);
        y += lineH;
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("徽章", statLeft, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.valueOf(badges), statRight, y, p);
    }

    public void drawRankingOverlay(Canvas c, int w, int h, java.util.List<String> rankingLines) {
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

    public void drawCenter(Canvas c, int w, int h, String a, String b, boolean over,
                           int pendingIRS, boolean pendingIHS) {
        p.setTextAlign(Paint.Align.CENTER); p.setColor(0xdd000000); c.drawRoundRect(new RectF(50,h/2f-110,w-50,h/2f+110),24,24,p);
        p.setColor(Color.WHITE); p.setTextSize(52); c.drawText(a, w/2f, h/2f-28, p);
        p.setColor(0xffaaaaaa); p.setTextSize(34); c.drawText(b, w/2f, h/2f+32, p);
        if (over) {
            p.setColor(0xff888899); p.setTextSize(28);
            String irsTxt = pendingIRS == 0 ? "预旋转: 无 (点旋转/逆旋)" : (pendingIRS > 0 ? "预旋转: 顺时针" : "预旋转: 逆时针");
            String ihsTxt = "预暂存: " + (pendingIHS ? "开 (点暂存切换)" : "关 (点暂存切换)");
            c.drawText(irsTxt, w/2f, h/2f+76, p);
            c.drawText(ihsTxt, w/2f, h/2f+108, p);
        }
    }

    private void mini(Canvas c, Piece pc, float x, float y, float box, Theme theme) {
        p.setColor(theme.board);
        c.drawRoundRect(new RectF(x, y, x + box, y + box), 8, 8, p);
        if (pc == null) return;
        float z = box / 3.5f;
        for (int r = 0; r < pc.s.length; r++) {
            for (int col = 0; col < pc.s[r].length; col++) {
                if (pc.s[r][col] != 0) {
                    p.setColor(theme.colors[pc.type]);
                    c.drawRect(x + 6 + col * z, y + 8 + r * z,
                               x + 6 + (col + 1) * z - 2, y + 8 + (r + 1) * z - 2, p);
                }
            }
        }
    }
}
