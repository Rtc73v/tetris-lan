package com.echo.tetrislan.net;

public class DiscoveredRoom {
    public String room, host, name;
    public long lastSeenMs;
    public DiscoveredRoom(String r, String h, String n) { room=r; host=h; name=n; lastSeenMs=System.currentTimeMillis(); }
}
