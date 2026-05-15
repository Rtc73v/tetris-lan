package com.echo.tetrislan.render;

public class FxParticle {
    public float x, y, vx, vy, size;
    public int color;
    public long born = System.currentTimeMillis();
    public FxParticle(float x, float y, float vx, float vy, int color, float size) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = color; this.size = size;
    }
}
