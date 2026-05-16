package com.echo.tetrislan.modes;

import java.util.Random;

public final class DigModeController {
    public static String encodeGarbage(boolean[][] isGarbage) {
        if (isGarbage == null) return "";
        StringBuilder sb = new StringBuilder(200);
        for (int r = 0; r < 20; r++)
            for (int c = 0; c < 10; c++)
                sb.append(isGarbage[r][c] ? '1' : '0');
        return sb.toString();
    }

    public static void decodeGarbage(boolean[][] isGarbage, String s) {
        if (s == null || s.length() < 200) return;
        for (int i = 0; i < 200 && i < s.length(); i++)
            isGarbage[i / 10][i % 10] = s.charAt(i) == '1';
    }

    public static void generateDigBoard(int[][] board, boolean[][] isGarbage, int rows, int cols, Random rnd) {
        int targetLines = 10;
        for (int y = rows - targetLines; y < rows; y++) {
            int hole = rnd.nextInt(cols);
            for (int x = 0; x < cols; x++) {
                board[y][x] = (x == hole) ? 0 : 7;
                isGarbage[y][x] = (x != hole);
            }
        }
    }
}
