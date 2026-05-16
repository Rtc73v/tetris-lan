package com.echo.tetrislan.modes;

public final class InvisibleModeController {
    public static long invisibleRevealMs(int level, long dropMs, int[][] board, int boardRows) {
        long base = 3200 - (level - 1) * 220L;
        long speedBonus = Math.max(0, (long)((700 - dropMs) * 0.9f));
        long reveal = base + speedBonus;
        int stack = maxStackHeight(board, boardRows);
        if (stack >= 17) reveal += 1200;
        else if (stack >= 15) reveal += 800;
        else if (stack >= 12) reveal += 400;
        return Math.max(1000, Math.min(5000, reveal));
    }

    public static int maxStackHeight(int[][] board, int boardRows) {
        int cols = board[0].length;
        for (int y = 0; y < boardRows; y++) {
            for (int x = 0; x < cols; x++) {
                if (board[y][x] != 0) return boardRows - y;
            }
        }
        return 0;
    }
}
