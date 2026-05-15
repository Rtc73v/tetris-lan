package com.echo.tetrislan.render;

import android.graphics.Canvas;
import android.graphics.Paint;

public class InvisibleRenderer {
    private static final long INVISIBLE_EDGE_PERIOD_MS = 2200;
    private static final long INVISIBLE_EDGE_ON_MS = 450;
    private static final long INVISIBLE_GAP_PERIOD_MS = 3500;
    private static final long INVISIBLE_GAP_ON_MS = 700;

    public void drawInvisibleGaps(Canvas c, long now, int[][] board, Paint p, LayoutState ls) {
        long phase = now % INVISIBLE_GAP_PERIOD_MS;
        if (phase >= INVISIBLE_GAP_ON_MS) return;
        float gapAlpha = 0.35f + 0.25f * ((float)Math.sin(now / 300.0) * 0.5f + 0.5f);
        int count = 0;
        int R = board.length;
        int C = board[0].length;
        // 空洞
        for (int y = R - 1; y >= 0 && count < 6; y--) {
            for (int x = 0; x < C && count < 6; x++) {
                if (board[y][x] == 0 && y > 0 && board[y - 1][x] != 0) {
                    drawGapHint(c, x, y, gapAlpha, p, ls);
                    count++;
                }
            }
        }
        // 接近消行的缺口（缺 1-2 个块）
        for (int y = R - 1; y >= 0 && count < 6; y--) {
            int empty = 0;
            for (int x = 0; x < C; x++) if (board[y][x] == 0) empty++;
            if (empty > 0 && empty <= 2) {
                for (int x = 0; x < C && count < 6; x++) {
                    if (board[y][x] == 0) {
                        drawGapHint(c, x, y, gapAlpha, p, ls);
                        count++;
                    }
                }
            }
        }
    }

    public void drawInvisibleEdge(Canvas c, long now, int[][] board, Paint p, LayoutState ls, Theme theme) {
        long phase = now % INVISIBLE_EDGE_PERIOD_MS;
        if (phase >= INVISIBLE_EDGE_ON_MS) return;
        float edgeAlpha = 0.20f + 0.45f * ((float)Math.sin(now / 200.0) * 0.5f + 0.5f);
        int R = board.length;
        int C = board[0].length;
        for (int y = 0; y < R; y++) {
            for (int x = 0; x < C; x++) {
                if (board[y][x] != 0 && isEdgeBlock(x, y, board)) {
                    BoardRenderer.block(c, p, ls, x, y, board[y][x], edgeAlpha, theme);
                }
            }
        }
    }

    private void drawGapHint(Canvas c, int x, int y, float alpha, Paint p, LayoutState ls) {
        p.setColor(ColorUtil.applyAlpha(0xff00e5ff, alpha));
        float l = ls.bx + x * ls.cell + ls.cell * 0.35f;
        float t = ls.by + y * ls.cell + ls.cell * 0.35f;
        float r = l + ls.cell * 0.3f;
        float b = t + ls.cell * 0.3f;
        c.drawRect(l, t, r, b, p);
    }

    private boolean isEdgeBlock(int x, int y, int[][] board) {
        if (board[y][x] == 0) return false;
        int R = board.length;
        int C = board[0].length;
        // 每列最高块
        boolean isTopmost = true;
        for (int yy = 0; yy < y; yy++) {
            if (board[yy][x] != 0) { isTopmost = false; break; }
        }
        if (isTopmost) return true;
        // 与空格相邻
        if ((y > 0 && board[y - 1][x] == 0) || (y < R - 1 && board[y + 1][x] == 0) ||
            (x > 0 && board[y][x - 1] == 0) || (x < C - 1 && board[y][x + 1] == 0)) return true;
        // 井口两侧
        if (y < R - 1 && board[y + 1][x] == 0) {
            boolean leftWall = (x == 0) || (board[y][x - 1] != 0);
            boolean rightWall = (x == C - 1) || (board[y][x + 1] != 0);
            if (leftWall && rightWall) return true;
        }
        return false;
    }

    public static int invisibleNearRange(int level) {
        if (level <= 3) return 4;
        if (level <= 5) return 3;
        return 2;
    }
}
