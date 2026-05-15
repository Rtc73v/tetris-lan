package com.echo.tetrislan.core;

public class GameClock {
    public static long elapsed(long now, long modeStartAt, long pausedTotalMs, boolean paused, long pauseStartedAt) {
        return (now - modeStartAt) - pausedTotalMs - (paused ? (now - pauseStartedAt) : 0);
    }
}
