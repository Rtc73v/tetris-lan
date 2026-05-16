package com.echo.tetrislan.core;

public class BoardCodec {
    public static String encode(int[][] board, int rows, int cols) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                sb.append(board[y][x]);
            }
        }
        return sb.toString();
    }

    public static int[][] decode(String s, int rows, int cols) {
        int[][] board = new int[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                board[y][x] = s.charAt(y * cols + x) - '0';
            }
        }
        return board;
    }
}
