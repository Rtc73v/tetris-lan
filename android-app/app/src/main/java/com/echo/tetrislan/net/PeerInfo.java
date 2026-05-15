package com.echo.tetrislan.net;

public class PeerInfo {
    public String name;
    public int score, lines, level, kos, badges;
    public boolean over;
    public long lastUpdateMs;
    public String via;
    public boolean disconnected = false;
    public long disconnectedAt = 0;
    public int[][] board = null;
    public PeerInfo(String name) { this.name = name; }
}
