package com.echo.tetrislan.ui;

import android.graphics.RectF;
import android.view.MotionEvent;
import com.echo.tetrislan.TetrisView;
import com.echo.tetrislan.net.DiscoveredRoom;

public class TouchRouter {
    private final TetrisView tv;

    public TouchRouter(TetrisView tv) {
        this.tv = tv;
    }

    public boolean onTouchEvent(MotionEvent e) {
        int masked = e.getActionMasked();
        if (masked == MotionEvent.ACTION_DOWN || masked == MotionEvent.ACTION_POINTER_DOWN) {
            int idx = e.getActionIndex();
            float x = e.getX(idx), y = e.getY(idx);
            if (tv.menu) return touchMenu(x, y);
            if (tv.settings) return touchSettings(x, y);
            if (tv.over && !tv.menu && tv.solo && tv.gameMode != 0) {
                if (tv.soloOverRestartBtn != null && tv.soloOverRestartBtn.contains(x, y)) {
                    tv.soloStage = 1;
                    tv.start();
                    return true;
                }
                if (tv.soloOverRetryBtn != null && tv.soloOverRetryBtn.contains(x, y)) {
                    tv.start();
                    return true;
                }
            }
            for (com.echo.tetrislan.ui.Btn b : tv.btns) if (b.r.contains(x, y)) {
                int pointerId = e.getPointerId(idx);
                tv.pointerActions.put(pointerId, b.action);
                tv.activeAction = b.action;
                tv.pressAction(b.action);
                return true;
            }
            return true;
        }
        if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL || masked == MotionEvent.ACTION_POINTER_UP) {
            if (masked == MotionEvent.ACTION_CANCEL || masked == MotionEvent.ACTION_UP) {
                tv.releaseAllActions();
                return true;
            }
            int idx = e.getActionIndex();
            int pointerId = e.getPointerId(idx);
            Integer action = tv.pointerActions.remove(pointerId);
            if (action != null) tv.releaseAction(action);
            return true;
        }
        return true;
    }

    private boolean touchMenu(float x, float y) {
        int w = tv.getWidth(), h = tv.getHeight();
        if (tv.menuPage == 0) {
            if (TouchUtil.hit(x, y, w * .14f, h * .30f, w * .86f, h * .39f)) { tv.solo = true; tv.menuPage = 1; return true; }
            if (TouchUtil.hit(x, y, w * .14f, h * .43f, w * .86f, h * .52f)) { tv.solo = false; tv.gameMode = 0; tv.invisible = false; tv.menuPage = 2; return true; }
            return true;
        }
        if (tv.menuPage == 1) {
            float btnH = h * 0.058f, gap = h * 0.010f, sy = h * 0.28f;
            if (TouchUtil.hit(x, y, w * .08f, sy, w * .46f, sy + btnH)) { tv.soloModeController.openSoloMode(0, 0); return true; }
            if (TouchUtil.hit(x, y, w * .54f, sy, w * .92f, sy + btnH)) { tv.soloModeController.openSoloMode(0, 1); return true; }
            if (TouchUtil.hit(x, y, w * .08f, sy + btnH + gap, w * .46f, sy + 2 * btnH + gap)) { tv.soloModeController.openSoloMode(1, 0); return true; }
            if (TouchUtil.hit(x, y, w * .54f, sy + btnH + gap, w * .92f, sy + 2 * btnH + gap)) { tv.soloModeController.openSoloMode(2, 0); return true; }
            if (TouchUtil.hit(x, y, w * .08f, sy + 2 * (btnH + gap), w * .46f, sy + 3 * btnH + 2 * gap)) { tv.soloModeController.openSoloMode(3, 0); return true; }
            if (TouchUtil.hit(x, y, w * .54f, sy + 2 * (btnH + gap), w * .92f, sy + 3 * btnH + 2 * gap)) { tv.soloModeController.openSoloMode(4, 0); return true; }
            if (TouchUtil.hit(x, y, w * .08f, sy + 3 * (btnH + gap), w * .46f, sy + 4 * btnH + 3 * gap)) { tv.soloModeController.openSoloMode(5, 0); return true; }
            if (TouchUtil.hit(x, y, w * .54f, sy + 3 * (btnH + gap), w * .92f, sy + 4 * btnH + 3 * gap)) { tv.soloModeController.openSoloMode(6, 0); return true; }
            float by = sy + 4 * (btnH + gap) + gap * 2;
            if (TouchUtil.hit(x, y, w * .14f, by, w * .86f, by + btnH)) { tv.menuPage = 0; return true; }
            return true;
        }
        if (tv.menuPage == 4) {
            float btnH = h * 0.070f, gap = h * 0.018f, sy = h * 0.32f;
            for (int i = 0; i < 3; i++) {
                float ty = sy + i * (btnH + gap);
                if (TouchUtil.hit(x, y, w * .14f, ty, w * .86f, ty + btnH)) {
                    tv.trainTech = i; tv.soloModeController.startMode(6); return true;
                }
            }
            float backY = sy + 3 * (btnH + gap) + gap * 2;
            if (TouchUtil.hit(x, y, w * .14f, backY, w * .86f, backY + btnH)) { tv.menuPage = 1; return true; }
            return true;
        }
        if (tv.menuPage == 3) {
            if (TouchUtil.hit(x, y, w * .14f, h * .30f, w * .86f, h * .39f)) { tv.soloModeController.startMode(tv.pendingStartMode); return true; }
            if (TouchUtil.hit(x, y, w * .14f, h * .43f, w * .86f, h * .52f)) { if (tv.saveManager.hasSave()) tv.saveManager.load(); return true; }
            if (TouchUtil.hit(x, y, w * .14f, h * .56f, w * .86f, h * .65f)) { tv.menuPage = 1; return true; }
            return true;
        }
        if (tv.p2p == null || tv.p2pDiscovery) {
            if (TouchUtil.hit(x, y, w * .08f, h * .31f, w * .46f, h * .39f)) { tv.createRoom(); return true; }
            if (TouchUtil.hit(x, y, w * .54f, h * .31f, w * .92f, h * .39f)) { tv.askRoom(); return true; }
            if (tv.lastRoomName != null) {
                if (TouchUtil.hit(x, y, w * .08f, h * .42f, w * .46f, h * .50f)) { tv.reconnectLastRoom(); return true; }
                if (TouchUtil.hit(x, y, w * .54f, h * .42f, w * .92f, h * .50f)) { tv.askName(); return true; }
                if (TouchUtil.hit(x, y, w * .15f, h * .53f, w * .85f, h * .61f)) { tv.stopP2p(); tv.menuPage = 0; return true; }
            } else {
                if (TouchUtil.hit(x, y, w * .08f, h * .42f, w * .46f, h * .50f)) { tv.askName(); return true; }
                if (TouchUtil.hit(x, y, w * .15f, h * .53f, w * .85f, h * .61f)) { tv.stopP2p(); tv.menuPage = 0; return true; }
            }
            int nf = tv.foundRooms.size();
            float cardY = h * 0.612f, cardH = h * 0.058f, cardGap = h * 0.008f, cardL = w * 0.06f, cardR = w * 0.94f;
            int limit = Math.min(nf, 4);
            for (int i = 0; i < limit; i++) {
                float cy = cardY + i * (cardH + cardGap);
                if (TouchUtil.hit(x, y, cardL, cy, cardR, cy + cardH)) {
                    DiscoveredRoom dr = tv.foundRooms.get(i);
                    tv.joinRoom(dr.room, dr.host);
                    return true;
                }
            }
        } else {
            if (TouchUtil.hit(x, y, w * .08f, h * .31f, w * .46f, h * .39f)) {
                if (tv.isHost) { if (tv.canHostStart()) tv.hostStartGame(); return true; }
                else { tv.toggleReady(); return true; }
            }
            if (TouchUtil.hit(x, y, w * .54f, h * .31f, w * .92f, h * .39f)) { tv.askChat(); return true; }
            if (TouchUtil.hit(x, y, w * .08f, h * .42f, w * .46f, h * .50f)) { tv.askName(); return true; }
            if (TouchUtil.hit(x, y, w * .54f, h * .42f, w * .92f, h * .50f)) { tv.leaveRoom(); return true; }
            if (tv.isHost) {
                int bc = tv.botCount();
                if (TouchUtil.hit(x, y, w * .08f, h * .53f, w * .46f, h * .61f)) {
                    if (tv.playerCount() < 3) { tv.addBot(); return true; }
                }
                if (TouchUtil.hit(x, y, w * .54f, h * .53f, w * .92f, h * .61f)) {
                    if (bc > 0) { tv.removeBot(); return true; }
                    else if (!tv.peerNames.isEmpty()) { tv.kickPlayer(); return true; }
                }
            }
        }
        return true;
    }

    private boolean touchSettings(float x, float y) {
        int w = tv.getWidth(), h = tv.getHeight();
        if (tv.confirmQuit) {
            float dw = w * .78f, dh = h * .28f, dy = (h - dh) / 2;
            float btnW = dw * .27f, btnH = dh * .22f, btnY = dy + dh * .72f, gap = dw * .05f;
            float bx1 = w / 2f - btnW * 1.5f - gap, bx2 = w / 2f - btnW / 2f, bx3 = w / 2f + btnW / 2f + gap;
            if (tv.solo && TouchUtil.hit(x, y, bx1, btnY, bx1 + btnW, btnY + btnH)) { tv.saveManager.save(true); tv.confirmQuit = false; tv.goMenu(); return true; }
            if (tv.solo && TouchUtil.hit(x, y, bx2, btnY, bx2 + btnW, btnY + btnH)) { tv.confirmQuit = false; tv.goMenu(); return true; }
            if (tv.solo && TouchUtil.hit(x, y, bx3, btnY, bx3 + btnW, btnY + btnH)) { tv.confirmQuit = false; return true; }
            return true;
        }
        if (tv.solo && TouchUtil.hit(x, y, w * .16f, h * .26f, w * .84f, h * .33f)) { tv.settings = false; tv.setPaused(false); return true; }
        if (tv.solo && TouchUtil.hit(x, y, w * .16f, h * .35f, w * .84f, h * .42f)) { tv.saveManager.load(); tv.settings = false; return true; }
        if (tv.solo && TouchUtil.hit(x, y, w * .16f, h * .44f, w * .84f, h * .51f)) { tv.saveManager.save(true); return true; }
        if (!tv.solo && TouchUtil.hit(x, y, w * .16f, h * .32f, w * .84f, h * .40f)) { tv.settings = false; return true; }
        float rowH = h * 0.068f;
        float sy = h * 0.55f;
        float btnW = w * 0.12f;
        for (int i = 0; i < 3; i++) {
            float rowY = sy + i * rowH;
            if (TouchUtil.hit(x, y, w * .60f, rowY, w * .60f + btnW, rowY + rowH * 0.85f)) {
                if (i == 0) { tv.dasMs = Math.max(0, tv.dasMs - 10); tv.sp.edit().putLong("das_ms", tv.dasMs).apply(); }
                else if (i == 1) { tv.arrMs = Math.max(0, tv.arrMs - 2); tv.sp.edit().putLong("arr_ms", tv.arrMs).apply(); }
                else { tv.softMs = Math.max(10, tv.softMs - 10); tv.sp.edit().putLong("soft_ms", tv.softMs).apply(); }
                return true;
            }
            if (TouchUtil.hit(x, y, w * .74f, rowY, w * .74f + btnW, rowY + rowH * 0.85f)) {
                if (i == 0) { tv.dasMs = Math.min(500, tv.dasMs + 10); tv.sp.edit().putLong("das_ms", tv.dasMs).apply(); }
                else if (i == 1) { tv.arrMs = Math.min(200, tv.arrMs + 2); tv.sp.edit().putLong("arr_ms", tv.arrMs).apply(); }
                else { tv.softMs = Math.min(500, tv.softMs + 10); tv.sp.edit().putLong("soft_ms", tv.softMs).apply(); }
                return true;
            }
        }
        if (TouchUtil.hit(x, y, w * .16f, h * .82f, w * .84f, h * .89f)) {
            if (!tv.solo) { tv.goMenu(); return true; }
            if (!tv.over && !tv.menu) { tv.confirmQuit = true; return true; }
            tv.goMenu(); return true;
        }
        return true;
    }
}
