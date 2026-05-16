package com.echo.tetrislan.modes;

import com.echo.tetrislan.TetrisView;

import com.echo.tetrislan.core.GameMode;

public class SoloModeController {
    private final TetrisView tv;

    public SoloModeController(TetrisView tv) {
        this.tv = tv;
    }

    public void openSoloMode(int mode, int speed) {
        tv.solo = true;
        tv.gameMode = mode;
        tv.classicSpeed = (mode == GameMode.MODE_CLASSIC) ? speed : 0;
        if (mode == GameMode.MODE_TRAINING) { tv.trainTech = 0; tv.menuPage = 4; return; }
        tv.pendingStartMode = mode;
        tv.menuPage = 3;
    }

    public void startMode(int mode) {
        tv.gameMode = mode;
        tv.soloStage = 1;
        tv.start();
    }
}
