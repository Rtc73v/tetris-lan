package com.echo.tetrislan.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.echo.tetrislan.core.Piece;

public class BoardRenderer {
    private final Paint p;

    public BoardRenderer(Paint p) {
        this.p = p;
    }

    public static void block(Canvas c, Paint p, LayoutState ls, int x, int y, int type, float alpha, Theme theme) {
        p.setColor(ColorUtil.applyAlpha(theme.colors[type], alpha));
        float l = ls.bx + x * ls.cell, t = ls.by + y * ls.cell;
        c.drawRect(l + 1, t + 1, l + ls.cell - 2, t + ls.cell - 2, p);
        p.setColor(ColorUtil.applyAlpha(theme.blockFlash, alpha * .22f));
        c.drawRect(l + 2, t + 2, l + ls.cell - 3, Math.max(t + 3, t + ls.cell * .28f), p);
    }

    public static void drawPiece(Canvas c, Paint p, LayoutState ls, Piece pc, int yy, float alpha, Theme theme) {
        for (int r = 0; r < pc.s.length; r++) {
            for (int x = 0; x < pc.s[r].length; x++) {
                if (pc.s[r][x] != 0) block(c, p, ls, pc.x + x, yy + r, pc.type, alpha, theme);
            }
        }
    }

    public void drawBoard(Canvas c, LayoutState ls, Theme theme, int[][] board, Piece cur, int ghostY,
                          boolean invisible, boolean over, long now,
                          long invisibleFlashUntil, long invisiblePreviewUntil, long invisibleDangerUntil,
                          long invisibleNearUntil, int invisibleNearCY, int invisibleNearCX, int level,
                          int pendingGarbage, long garbageDueAt,
                          int score, int lines) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(theme.board);
        c.drawRoundRect(new RectF(ls.bx, ls.by, ls.bx + ls.bw, ls.by + ls.bh), 8, 8, p);

        boolean showAll = !invisible || over || now < invisibleFlashUntil || now < invisiblePreviewUntil || now < invisibleDangerUntil;
        boolean showNear = invisible && !over && !showAll && now < invisibleNearUntil && invisibleNearCY >= 0;

        if (showAll) {
            for (int y = 0; y < board.length; y++) {
                for (int x = 0; x < board[y].length; x++) {
                    if (board[y][x] != 0) block(c, p, ls, x, y, board[y][x], 1f, theme);
                }
            }
        } else if (showNear) {
            int range = invisibleNearRange(level);
            for (int y = 0; y < board.length; y++) {
                for (int x = 0; x < board[y].length; x++) {
                    if (board[y][x] != 0) {
                        int dy = Math.abs(y - invisibleNearCY);
                        int dx = Math.abs(x - invisibleNearCX);
                        if (dy <= range && dx <= range + 1) {
                            float dist = (dy + dx * 0.7f) / (range + 1);
                            float alpha = Math.max(0.15f, 1f - dist);
                            block(c, p, ls, x, y, board[y][x], alpha, theme);
                        }
                    }
                }
            }
        }

        if (cur != null) {
            drawPiece(c, p, ls, cur, ghostY, 0.28f, theme);
            drawPiece(c, p, ls, cur, cur.y, 1f, theme);
        }

        // garbage preview
        if (pendingGarbage > 0 && garbageDueAt > 0 && now < garbageDueAt) {
            int rows = Math.min(pendingGarbage, 20);
            float progress = 1f - (garbageDueAt - now) / 1800f;
            int alpha = 60 + (int)(40 * progress);
            p.setColor((alpha << 24) | 0x00ff4444);
            for (int i = 0; i < rows; i++) {
                c.drawRect(ls.bx, ls.by + ls.bh - ls.cell * (i + 1), ls.bx + ls.bw, ls.by + ls.bh - ls.cell * i, p);
            }
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(theme.boardStroke);
        c.drawRect(ls.bx, ls.by, ls.bx + ls.bw, ls.by + ls.bh, p);
        p.setStyle(Paint.Style.FILL);

        // HUD below board
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(36);
        p.setColor(theme.text);
        float by2 = ls.by + ls.bh + 32;
        c.drawText("分", ls.bx, by2, p);
        p.setColor(theme.score);
        c.drawText(String.valueOf(score), ls.bx + 52, by2, p);
        p.setColor(theme.text);
        c.drawText("级", ls.bx + 160, by2, p);
        p.setColor(theme.score);
        c.drawText(String.valueOf(level), ls.bx + 202, by2, p);
        p.setColor(theme.text);
        c.drawText("行", ls.bx + 290, by2, p);
        p.setColor(theme.score);
        c.drawText(String.valueOf(lines), ls.bx + 332, by2, p);
    }

    private static int invisibleNearRange(int lvl) {
        if (lvl <= 3) return 4;
        if (lvl <= 5) return 3;
        return 2;
    }
}
