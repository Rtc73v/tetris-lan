package com.echo.tetrislan.modes;

import com.echo.tetrislan.core.Piece;

public final class TrainingModeController {
    public static String trainTechName(int trainTech) {
        switch (trainTech) {
            case 0: return "Mini";
            case 1: return "Single";
            case 2: return "Double";
            default: return "Mini";
        }
    }

    public static boolean trainSuccess(int trainTech, int spinType, int cleared, boolean onTarget) {
        switch (trainTech) {
            case 0: return onTarget && spinType >= 1 && cleared == 1;
            case 1: return onTarget && spinType >= 1 && cleared == 1;
            case 2: return onTarget && spinType >= 1 && cleared == 2;
            default: return false;
        }
    }

    public static String trainFailReason(int trainTech, boolean lastActionWasRotate, int spinType, int cleared, boolean onTarget) {
        if (!lastActionWasRotate) return "失败: 最后一步必须旋转";
        if (!onTarget) return "失败: 没有转进目标槽";
        if (spinType < 1) return "失败: T槽角位不对";
        if (trainTech == 2 && cleared != 2) return "失败: 需要消2行";
        if ((trainTech == 0 || trainTech == 1) && cleared != 1) return "失败: 需要消1行";
        return "失败: 位置不对";
    }

    public static boolean trainingTargetMatched(Piece cur, Piece trainDemoPiece, int targetX, int targetY) {
        if (cur == null || trainDemoPiece == null) return true;
        if (cur.type != trainDemoPiece.type || cur.x != targetX || cur.y != targetY || cur.rot != trainDemoPiece.rot) return false;
        for (int r = 0; r < cur.s.length; r++) {
            for (int x = 0; x < cur.s[r].length; x++) {
                boolean a = cur.s[r][x] != 0;
                boolean b = r < trainDemoPiece.s.length && x < trainDemoPiece.s[r].length && trainDemoPiece.s[r][x] != 0;
                if (a != b) return false;
            }
        }
        return true;
    }

    public static Piece trainNextPiece(int trainTech) {
        Piece p;
        switch (trainTech) {
            case 0:
                p = new Piece(3);
                p.s = tShape(0);
                p.rot = 0;
                break;
            case 1: case 2:
                p = new Piece(3);
                p.s = tShape(1);
                p.rot = 1;
                break;
            default:
                p = new Piece(3);
                p.s = tShape(0);
                p.rot = 0;
                break;
        }
        return p;
    }

    public static int[][] tShape(int rot) {
        switch (rot & 3) {
            case 1: return new int[][]{{0,1,0},{0,1,1},{0,1,0}};
            case 2: return new int[][]{{0,0,0},{1,1,1},{0,1,0}};
            case 3: return new int[][]{{0,1,0},{1,1,0},{0,1,0}};
            default: return new int[][]{{0,1,0},{1,1,1},{0,0,0}};
        }
    }

    public static int[][] iVertical() {
        return new int[][]{{0,1,0,0},{0,1,0,0},{0,1,0,0},{0,1,0,0}};
    }
}
