package com.echo.tetrislan;

public class InputController {
    private final TetrisView tv;

    public InputController(TetrisView tv) {
        this.tv = tv;
    }

    public void input(long now) {
        if (tv.leftHeld && !tv.rightHeld) repeatHorizontal(-1, now, tv.leftStart);
        else if (tv.rightHeld && !tv.leftHeld) repeatHorizontal(1, now, tv.rightStart);
        if (tv.softHeld) {
            long held = now - tv.softStart;
            long interval = Math.max(14, tv.softMs - held / 12);
            if (now - tv.softAt > interval) { if (tv.move(0, 1)) tv.score++; tv.softAt = now; }
        }
    }

    private void repeatHorizontal(int dir, long now, long startedAt) {
        if (now - startedAt <= tv.dasMs) return;
        if (tv.arrMs == 0) { while (tv.move(dir, 0)); return; }
        if (now - tv.arrAt > tv.arrMs) { tv.move(dir, 0); tv.arrAt = now; }
    }
}
