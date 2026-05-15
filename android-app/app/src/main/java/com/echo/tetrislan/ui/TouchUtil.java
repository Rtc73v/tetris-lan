package com.echo.tetrislan.ui;

public class TouchUtil {
    public static boolean hit(float x, float y, float l, float t, float r, float b) {
        return x >= l && x <= r && y >= t && y <= b;
    }
}
