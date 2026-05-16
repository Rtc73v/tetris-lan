package com.echo.tetrislan.core;

import android.content.SharedPreferences;
import android.widget.Toast;
import com.echo.tetrislan.TetrisView;
import com.echo.tetrislan.modes.DigModeController;
import org.json.JSONArray;
import org.json.JSONObject;

public class SaveManager {
    private final TetrisView tv;
    private final SharedPreferences sp;

    public SaveManager(TetrisView tv, SharedPreferences sp) {
        this.tv = tv;
        this.sp = sp;
    }

    private String saveKey() {
        if (tv.gameMode == 6) return "save_6_" + tv.trainTech;
        return "save_" + tv.gameMode + "_" + (tv.gameMode == 0 ? tv.classicSpeed : 0);
    }

    public boolean hasSave() {
        return sp.contains(saveKey());
    }

    public void save(boolean toast) {
        if (tv.gameMode == 6) {
            if (toast) Toast.makeText(tv.getContext(), "训练模式无法保存", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!tv.solo || tv.cur == null || tv.over) {
            if (toast) Toast.makeText(tv.getContext(), "当前无法保存", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("gameMode", tv.gameMode);
            o.put("board", arr(tv.board));
            o.put("cur", tv.cur.json());
            o.put("next", tv.next.json());
            o.put("hold", tv.hold);
            o.put("score", tv.score); o.put("lines", tv.lines); o.put("level", tv.level);
            o.put("drop", tv.dropMs); o.put("bagIndex", tv.bagIndex);
            o.put("soloStage", tv.soloStage); o.put("classicSpeed", tv.classicSpeed);
            o.put("digCleared", tv.digCleared); o.put("digTargetLines", tv.digTargetLines);
            o.put("invisible", tv.invisible);
            o.put("pendingGarbage", tv.pendingGarbage); o.put("combo", tv.combo); o.put("b2b", tv.b2b);
            o.put("badges", tv.badges); o.put("kos", tv.kos); o.put("canHold", tv.canHold);
            o.put("elapsedMs", GameClock.elapsed(System.currentTimeMillis(), tv.modeStartAt, tv.pausedTotalMs, tv.paused, tv.pauseStartedAt));
            o.put("isGarbage", DigModeController.encodeGarbage(tv.isGarbage));
            JSONArray bagArr = new JSONArray();
            for (int v : tv.bag) bagArr.put(v);
            o.put("bag", bagArr);
            sp.edit().putString(saveKey(), o.toString()).apply();
            if (toast) Toast.makeText(tv.getContext(), tv.modeName() + " 已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            if (toast) Toast.makeText(tv.getContext(), "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    public void load() {
        try {
            String s = sp.getString(saveKey(), null);
            if (s == null) return;
            tv.C = 10; tv.R = 20;
            JSONObject o = new JSONObject(s);
            tv.gameMode = o.optInt("gameMode", tv.gameMode);
            tv.board = board(o.getJSONArray("board"));
            tv.cur = new Piece(o.getJSONObject("cur"));
            tv.next = new Piece(o.getJSONObject("next"));
            tv.hold = o.optInt("hold");
            tv.score = o.optInt("score"); tv.lines = o.optInt("lines"); tv.level = o.optInt("level", 1);
            tv.dropMs = o.optLong("drop", 1000); tv.bagIndex = o.optInt("bagIndex", 7);
            tv.soloStage = o.optInt("soloStage", 1); tv.classicSpeed = o.optInt("classicSpeed", tv.classicSpeed);
            tv.digCleared = o.optInt("digCleared", 0); tv.digTargetLines = o.optInt("digTargetLines", 0);
            tv.invisible = o.optBoolean("invisible", false);
            tv.pendingGarbage = o.optInt("pendingGarbage", 0); tv.combo = o.optInt("combo", -1); tv.b2b = o.optInt("b2b", 0);
            tv.badges = o.optInt("badges", 0); tv.kos = o.optInt("kos", 0); tv.canHold = o.optBoolean("canHold", true);
            DigModeController.decodeGarbage(tv.isGarbage, o.optString("isGarbage", ""));
            JSONArray bagArr = o.optJSONArray("bag");
            if (bagArr != null && bagArr.length() == 7) {
                for (int i = 0; i < 7; i++) tv.bag[i] = bagArr.getInt(i);
            }
            long elapsed = o.optLong("elapsedMs", 0);
            tv.finishText = ""; tv.modeStartAt = System.currentTimeMillis() - elapsed; tv.pausedTotalMs = 0; tv.pauseStartedAt = 0;
            tv.over = false; tv.paused = false; tv.settings = false; tv.menu = false;
        } catch (Exception ignored) {}
    }

    private JSONArray arr(int[][] b) throws Exception {
        JSONArray a = new JSONArray();
        for (int y = 0; y < tv.R; y++) {
            JSONArray row = new JSONArray();
            for (int x = 0; x < tv.C; x++) row.put(b[y][x]);
            a.put(row);
        }
        return a;
    }

    private int[][] board(JSONArray a) throws Exception {
        int[][] b = new int[tv.R][tv.C];
        for (int y = 0; y < tv.R; y++) {
            JSONArray row = a.getJSONArray(y);
            for (int x = 0; x < tv.C; x++) b[y][x] = row.getInt(x);
        }
        return b;
    }
}
