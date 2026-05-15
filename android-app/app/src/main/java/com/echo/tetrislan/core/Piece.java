package com.echo.tetrislan.core;

import org.json.JSONArray;
import org.json.JSONObject;

public class Piece {
    public static final int[][][] SHAPES = {
        {},
        {{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},
        {{1,1},{1,1}},
        {{0,1,0},{1,1,1},{0,0,0}},
        {{0,1,1},{1,1,0},{0,0,0}},
        {{1,1,0},{0,1,1},{0,0,0}},
        {{1,0,0},{1,1,1},{0,0,0}},
        {{0,0,1},{1,1,1},{0,0,0}}
    };

    public int type, x = 3, y = 0, rot = 0;
    public boolean spin = false, mini = false;
    public int[][] s;

    public Piece(int t) {
        type = t;
        s = copy(SHAPES[t]);
        rot = 0;
    }

    public Piece(JSONObject o) throws Exception {
        type = o.getInt("type");
        x = o.getInt("x");
        y = o.getInt("y");
        rot = o.optInt("rot", 0);
        JSONArray a = o.getJSONArray("s");
        s = new int[a.length()][a.length()];
        for (int r = 0; r < a.length(); r++) {
            JSONArray row = a.getJSONArray(r);
            for (int c = 0; c < row.length(); c++) s[r][c] = row.getInt(c);
        }
    }

    public JSONObject json() throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("x", x);
        o.put("y", y);
        o.put("rot", rot);
        o.put("spin", spin);
        o.put("mini", mini);
        JSONArray a = new JSONArray();
        for (int[] rr : s) {
            JSONArray row = new JSONArray();
            for (int v : rr) row.put(v);
            a.put(row);
        }
        o.put("s", a);
        return o;
    }

    public static int[][] copy(int[][] m) {
        int[][] n = new int[m.length][m.length];
        for (int i = 0; i < m.length; i++) n[i] = m[i].clone();
        return n;
    }
}
