package com.echo.tetrislan.net;

import com.echo.tetrislan.core.Piece;
import java.util.Random;

public class BotPlayer {
    public String name;
    public String hostKey;
    public int[][] board = new int[20][10];
    public Piece cur;
    public Piece next;
    public int hold = 0;
    public int score = 0, lines = 0, level = 1;
    public boolean over = false;
    public long thinkUntil = 0;
    public int dropDelay = 600;
    public int actionSpeed = 3; // 1-10, higher = faster
    public int iq = 5; // 1-10
    public long lastTick = 0;
    public int pendingGarbage = 0;
    public int combo = -1, b2b = 0, badges = 0, kos = 0;
    public String lastAttacker = "";
    public long garbageDueAt = 0;
    public boolean canHold = true;
    public int bagIndex = 7;
    public int[] bag = new int[7];
    public boolean isBot = true;
    public Random rnd;

    public BotPlayer(String name, String hostKey, long seed) {
        this.name = name; this.hostKey = hostKey;
        this.rnd = new Random(seed ^ name.hashCode());
        fillBag();
    }

    public void fillBag() {
        for (int i = 0; i < 7; i++) bag[i] = i + 1;
        for (int i = 6; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = bag[i]; bag[i] = bag[j]; bag[j] = t;
        }
        bagIndex = 0;
    }

    public Piece randomPiece() {
        if (bagIndex >= 7) fillBag();
        return new Piece(bag[bagIndex++]);
    }

    public static class BotDecision {
        public int x, rot;
        public boolean hardDrop;
        public double score = 0;
        public BotDecision(int x, int rot, boolean hardDrop) { this.x = x; this.rot = rot; this.hardDrop = hardDrop; }
    }
}
