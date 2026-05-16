package com.echo.tetrislan.core;

public class Rules {
    public static int checkTSpin(int[][] board, int R, int C, Piece cur, boolean lastActionWasRotate) {
        if (cur == null || cur.type != 3 || !lastActionWasRotate) return 0;
        int cx = cur.x + 1, cy = cur.y + 1, n = 0;
        int[][] pts = {{cx-1,cy-1},{cx+1,cy-1},{cx-1,cy+1},{cx+1,cy+1}};
        for (int[] q : pts) {
            int x = q[0], y = q[1];
            if (x < 0 || x >= C || y >= R || (y >= 0 && board[y][x] != 0)) n++;
        }
        if (n < 3) return 0;
        return n == 4 ? 2 : 1;
    }

    public static int garbageFor(int n, int spinType, int b2b, int combo, int badges) {
        int g = 0;
        if (spinType == 2) g = n == 1 ? 2 : n == 2 ? 4 : 6;
        else if (spinType == 1) g = n == 1 ? 0 : n == 2 ? 1 : 2;
        else if (n == 2) g = 1;
        else if (n == 3) g = 2;
        else if (n == 4) g = 4;
        if (b2b > 1 && (spinType >= 1 || n == 4)) g++;
        if (combo > 1) g += (combo < 4 ? 1 : combo < 6 ? 2 : 3);
        g += badges / 2;
        return Math.min(g, 4);
    }
}
