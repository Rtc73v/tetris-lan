package com.echo.tetrislan.render;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.List;

public class FxRenderer {
    private final Paint p;

    public FxRenderer(Paint p) {
        this.p = p;
    }

    public void drawParticles(Canvas c, List<FxParticle> particles, long now) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            FxParticle f = particles.get(i);
            float life = (now - f.born) / 650f;
            if (life >= 1f) { particles.remove(i); continue; }
            p.setColor(ColorUtil.applyAlpha(f.color, 1f - life));
            c.drawCircle(f.x + f.vx * life, f.y + f.vy * life, f.size * (1f - life * .35f), p);
        }
    }

    public void drawFxOverlay(Canvas c, int w, int h, Theme theme, long now, long flashUntil, long fxUntil, String fxText) {
        if (now < flashUntil) {
            p.setColor(ColorUtil.applyAlpha(theme.blockFlash, .16f));
            c.drawRect(0, 0, w, h, p);
        }
        if (now < fxUntil && !fxText.isEmpty()) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(theme.fxText);
            p.setTextSize(48);
            c.drawText(fxText, w / 2f, h * .42f, p);
        }
    }
}
