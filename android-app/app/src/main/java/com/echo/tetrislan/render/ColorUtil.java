package com.echo.tetrislan.render;

public class ColorUtil {
    public static int applyAlpha(int color, float alpha) {
        return (Math.round(255 * alpha) << 24) | (color & 0x00ffffff);
    }
}
