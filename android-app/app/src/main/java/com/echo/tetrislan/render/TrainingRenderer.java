package com.echo.tetrislan.render;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.echo.tetrislan.core.Piece;

public class TrainingRenderer {
    private final Paint p;

    public TrainingRenderer(Paint p) {
        this.p = p;
    }

    public void drawTrainingDemo(Canvas c, long now, LayoutState ls, Theme theme,
                                 Piece trainDemoStartPiece, Piece trainDemoPiece,
                                 int trainDemoStartX, int trainDemoStartY,
                                 int trainDemoTargetX, int trainDemoTargetY,
                                 int trainDemoRotDir, int trainTech) {
        if (trainDemoStartPiece == null) return;
        long cycle = 5600L;
        long t = now % cycle;
        float x = trainDemoStartX, y = trainDemoStartY;
        int[][] shape = trainDemoStartPiece.s;
        String step = "1 起手";
        if (t >= 1400 && t < 2800) {
            float k = (t - 1400) / 1400f;
            x = lerp(trainDemoStartX, trainDemoTargetX, k);
            y = lerp(trainDemoStartY, trainDemoTargetY, k);
            step = trainDemoMoveText(trainDemoTargetX, trainDemoStartX, trainDemoTargetY, trainDemoStartY);
        } else if (t >= 2800 && t < 4200) {
            x = trainDemoTargetX;
            y = trainDemoTargetY;
            float k = (t - 2800) / 1400f;
            shape = k < 0.55f ? trainDemoStartPiece.s : trainDemoPiece.s;
            step = trainDemoRotDir > 0 ? "3 顺旋入位" : "3 逆旋入位";
        } else if (t >= 4200) {
            x = trainDemoTargetX;
            y = trainDemoTargetY;
            shape = trainDemoPiece.s;
            step = "4 速降锁定";
        }
        drawDemoPieceAt(c, trainDemoPiece.type, trainDemoPiece.s, trainDemoTargetX, trainDemoTargetY, 0.20f, true, p, ls, theme);
        drawDemoPieceAt(c, trainDemoPiece.type, shape, x, y, 0.36f, true, p, ls, theme);
        drawDemoArrow(c, trainDemoStartX, trainDemoStartY, trainDemoTargetX, trainDemoTargetY, p, ls);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.LEFT); p.setTextSize(24); p.setColor(theme.score);
        c.drawText(step, ls.bx + 8, ls.by + ls.bh - 12, p);
        p.setColor(theme.text); p.setTextSize(20);
        c.drawText(trainDemoInputText(trainTech), ls.bx + 112, ls.by + ls.bh - 12, p);
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * Math.max(0f, Math.min(1f, k));
    }

    private static String trainDemoMoveText(int trainDemoTargetX, int trainDemoStartX, int trainDemoTargetY, int trainDemoStartY) {
        int dx = trainDemoTargetX - trainDemoStartX;
        int dy = trainDemoTargetY - trainDemoStartY;
        if (dx > 0 && dy > 0) return "2 右移" + dx + "格+下落";
        if (dx < 0 && dy > 0) return "2 左移" + (-dx) + "格+下落";
        if (dx > 0) return "2 右移 " + dx + " 格";
        if (dx < 0) return "2 左移 " + (-dx) + " 格";
        if (dy > 0) return "2 正常下落";
        return "2 对齐槽口";
    }

    public static String trainDemoInputText(int trainTech) {
        switch (trainTech) {
            case 0: return "Mini: 下落到小口 -> 逆旋";
            case 1: return "单: 右移到槽口 -> 顺旋";
            case 2: return "双: 左移到深槽 -> 顺旋";
        }
        return "操作: 下落 -> 旋转 -> 锁定";
    }

    private static void drawDemoArrow(Canvas c, float sx, float sy, float tx, float ty, Paint p, LayoutState ls) {
        float x1 = ls.bx + (sx + 1.5f) * ls.cell, y1 = ls.by + (sy + 1.5f) * ls.cell;
        float x2 = ls.bx + (tx + 1.5f) * ls.cell, y2 = ls.by + (ty + 1.5f) * ls.cell;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3, ls.cell * 0.12f)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(ColorUtil.applyAlpha(0xFFFFFFFF, 0.55f));
        c.drawLine(x1, y1, x2, y2, p);
        p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
        c.drawCircle(x2, y2, Math.max(4, ls.cell * 0.16f), p);
    }

    private static void drawDemoPieceAt(Canvas c, int type, int[][] shape, float px, float py, float alpha, boolean outline, Paint p, LayoutState ls, Theme theme) {
        float flash = 0.65f + 0.25f * (float)Math.sin(System.currentTimeMillis() / 180.0);
        int col = theme.colors[type];
        p.setStyle(Paint.Style.FILL); p.setColor(ColorUtil.applyAlpha(col, alpha * flash));
        for (int r = 0; r < shape.length; r++) for (int x = 0; x < shape[r].length; x++) if (shape[r][x] != 0) {
            float l = ls.bx + (px + x) * ls.cell, t = ls.by + (py + r) * ls.cell;
            c.drawRect(l + 2, t + 2, l + ls.cell - 2, t + ls.cell - 2, p);
        }
        if (outline) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3, ls.cell * 0.14f)); p.setColor(ColorUtil.applyAlpha(0xFFFFFFFF, 0.85f * flash));
            for (int r = 0; r < shape.length; r++) for (int x = 0; x < shape[r].length; x++) if (shape[r][x] != 0) {
                float l = ls.bx + (px + x) * ls.cell, t = ls.by + (py + r) * ls.cell;
                c.drawRect(l + 2, t + 2, l + ls.cell - 2, t + ls.cell - 2, p);
            }
        }
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        c.drawCircle(ls.bx + (px + shape[0].length / 2f) * ls.cell, ls.by + (py + shape.length / 2f) * ls.cell, ls.cell * 0.13f, p);
    }
}
