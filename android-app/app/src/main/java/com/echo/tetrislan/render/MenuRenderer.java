package com.echo.tetrislan.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import com.echo.tetrislan.net.DiscoveredRoom;
import java.util.List;
import java.util.Map;

public class MenuRenderer {
    private final Paint p;
    public MenuRenderer(Paint p) { this.p = p; }

    public void drawMenuButton(Canvas c, String text, float l, float t, float r, float b, boolean on, Theme theme) {
        p.setStyle(Paint.Style.FILL); p.setColor(on ? theme.btnOn : theme.btn);
        c.drawRoundRect(new RectF(l,t,r,b), 28, 28, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(on ? theme.btnOn : theme.boardStroke);
        c.drawRoundRect(new RectF(l+2,t+2,r-2,b-2), 26, 26, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? Color.BLACK : theme.text); p.setTextSize(38); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, (l+r)/2, (t+b)/2 + 13, p);
    }

    public void drawMenu(Canvas c, int w, int h, Theme theme, String version, int menuPage,
                         String modeName, boolean hasSave,
                         String roomName, String playerName, String p2pStatus, String lastRoomName,
                         boolean isHost, boolean selfReady,
                         int readyCount, int playerCount, int botCount, boolean canHostStart,
                         Object p2p, boolean p2pDiscovery,
                         List<DiscoveredRoom> foundRooms,
                         Map<String, String> peerNames,
                         Map<String, Boolean> readyPeers,
                         List<String> chat,
                         long pendingStartAt, int maxPlayers) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(theme.text); p.setTextSize(56); c.drawText("俄罗斯方块 " + version, w/2f, h*0.16f, p);
        if (menuPage == 0) {
            drawMenuButton(c, "单人模式", w*0.14f, h*0.30f, w*0.86f, h*0.39f, false, theme);
            drawMenuButton(c, "多人模式", w*0.14f, h*0.43f, w*0.86f, h*0.52f, false, theme);
            p.setColor(theme.textMuted); p.setTextSize(28);
            c.drawText("先选模式，再开局/读取/保存", w/2f, h*0.60f, p);
            return;
        }
        if (menuPage == 3) {
            p.setColor(theme.score); p.setTextSize(36); c.drawText(modeName, w/2f, h*0.20f, p);
            drawMenuButton(c, "新游戏", w*0.14f, h*0.30f, w*0.86f, h*0.39f, false, theme);
            drawMenuButton(c, "读取存档", w*0.14f, h*0.43f, w*0.86f, h*0.52f, false, theme);
            drawMenuButton(c, "返回模式选择", w*0.14f, h*0.56f, w*0.86f, h*0.65f, false, theme);
            p.setColor(theme.textMuted); p.setTextSize(24);
            c.drawText(hasSave ? "当前模式已有独立存档" : "当前模式暂无存档", w/2f, h*0.73f, p);
            return;
        }
        if (menuPage == 4) {
            p.setColor(theme.score); p.setTextSize(36); c.drawText("T-Spin 专项训练", w/2f, h*0.20f, p);
            float btnH = h * 0.070f, gap = h * 0.018f, sy = h * 0.32f;
            String[] techs = {"T-Spin Mini", "T-Spin 单", "T-Spin 双"};
            String[] descs = {"左向Mini: 逆旋小口", "TSS: 右移顺旋", "TSD: 左移顺旋深槽"};
            for (int i = 0; i < techs.length; i++) {
                float ty = sy + i * (btnH + gap);
                drawMenuButton(c, techs[i], w*0.14f, ty, w*0.86f, ty + btnH, false, theme);
                p.setColor(theme.textMuted); p.setTextSize(18); p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(descs[i], w*0.82f, ty + btnH * 0.68f, p);
                p.setTextAlign(Paint.Align.CENTER);
            }
            float backY = sy + techs.length * (btnH + gap) + gap * 2;
            drawMenuButton(c, "返回模式选择", w*0.14f, backY, w*0.86f, backY + btnH, false, theme);
            p.setColor(theme.textMuted); p.setTextSize(20);
            c.drawText("游戏中会循环演示：起手 → 移动 → 旋转入位 → 速降", w/2f, backY + btnH + h*0.035f, p);
            return;
        }
        boolean multi = menuPage == 2;
        p.setColor(theme.score); p.setTextSize(36); c.drawText(multi ? "多人大厅" : "单人模式", w/2f, h*0.20f, p);
        if (!multi) {
            float btnH = h * 0.058f, gap = h * 0.010f, sy = h * 0.28f;
            drawMenuButton(c, "经典 普通", w*0.08f, sy,               w*0.46f, sy+btnH, false, theme);
            drawMenuButton(c, "经典 高速", w*0.54f, sy,               w*0.92f, sy+btnH, false, theme);
            drawMenuButton(c, "冲刺40行",  w*0.08f, sy+btnH+gap,      w*0.46f, sy+2*btnH+gap, false, theme);
            drawMenuButton(c, "限时得分",  w*0.54f, sy+btnH+gap,      w*0.92f, sy+2*btnH+gap, false, theme);
            drawMenuButton(c, "马拉松",    w*0.08f, sy+2*(btnH+gap),  w*0.46f, sy+3*btnH+2*gap, false, theme);
            drawMenuButton(c, "隐形模式",  w*0.54f, sy+2*(btnH+gap),  w*0.92f, sy+3*btnH+2*gap, false, theme);
            drawMenuButton(c, "挖掘挑战",  w*0.08f, sy+3*(btnH+gap),  w*0.46f, sy+4*btnH+3*gap, false, theme);
            drawMenuButton(c, "技巧训练",  w*0.54f, sy+3*(btnH+gap),  w*0.92f, sy+4*btnH+3*gap, false, theme);
            float by = sy + 4*(btnH+gap) + gap*2;
            drawMenuButton(c, "返回主菜单", w*0.14f, by,               w*0.86f, by+btnH, false, theme);
            p.setColor(theme.textMuted); p.setTextSize(20);
            float descY = by + btnH + h*0.025f;
            c.drawText("经典普通:可存档  经典高速:原生存模式  冲刺:竞速40行  限时:2分钟得分", w/2f, descY, p);
            c.drawText("马拉松:150行  隐形:动态显形+边缘/空缺提示  挖掘:清除垃圾行  训练:技巧练习", w/2f, descY + h*0.028f, p);
            return;
        }
        if (p2p == null || p2pDiscovery) {
            drawMenuButton(c, "创建房间", w*0.08f, h*0.31f, w*0.46f, h*0.39f, false, theme);
            drawMenuButton(c, "输入房号", w*0.54f, h*0.31f, w*0.92f, h*0.39f, false, theme);
            if (lastRoomName != null) {
                drawMenuButton(c, "重连上一局", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false, theme);
                drawMenuButton(c, "修改名称", w*0.54f, h*0.42f, w*0.92f, h*0.50f, false, theme);
            } else {
                drawMenuButton(c, "修改名称", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false, theme);
            }
            drawMenuButton(c, "返回主菜单", w*0.15f, h*0.53f, w*0.85f, h*0.61f, false, theme);
        } else {
            if (isHost) {
                drawMenuButton(c, canHostStart ? "开始游戏" : "等待准备", w*0.08f, h*0.31f, w*0.46f, h*0.39f, canHostStart, theme);
            } else {
                drawMenuButton(c, selfReady ? "取消准备" : "准备", w*0.08f, h*0.31f, w*0.46f, h*0.39f, selfReady, theme);
            }
            drawMenuButton(c, "聊天", w*0.54f, h*0.31f, w*0.92f, h*0.39f, false, theme);
            drawMenuButton(c, "修改名称", w*0.08f, h*0.42f, w*0.46f, h*0.50f, false, theme);
            drawMenuButton(c, "退出房间", w*0.54f, h*0.42f, w*0.92f, h*0.50f, false, theme);
            if (isHost) {
                if (playerCount < maxPlayers) {
                    drawMenuButton(c, "+电脑", w*0.08f, h*0.53f, w*0.46f, h*0.61f, false, theme);
                }
                if (botCount > 0) {
                    drawMenuButton(c, "-电脑", w*0.54f, h*0.53f, w*0.92f, h*0.61f, false, theme);
                } else if (!peerNames.isEmpty()) {
                    drawMenuButton(c, "踢人", w*0.54f, h*0.53f, w*0.92f, h*0.61f, false, theme);
                }
            }
        }
        p.setColor(theme.text); p.setTextSize(28);
        c.drawText("房间 " + roomName + "  准备 " + readyCount + "/" + playerCount + "  2-" + maxPlayers + "人", w/2f, h*0.68f, p);
        if (p2p != null && !p2pDiscovery) {
            p.setColor(theme.score); p.setTextSize(28);
            c.drawText("玩家: " + playerName + (isHost ? "[房主]" : ""), w/2f, h*0.72f, p);
            int py = 0;
            for (Map.Entry<String, String> e : peerNames.entrySet()) {
                String name = e.getValue();
                boolean isBot = name.startsWith("BOT_");
                String tag = isBot ? " [电脑]" : (readyPeers.getOrDefault(e.getKey(), false) ? " [已准备]" : "");
                c.drawText(name + tag, w/2f, h*(0.755f + 0.035f*py), p);
                py++;
            }
        } else {
            p.setColor(theme.textMuted); p.setTextSize(24);
            c.drawText("玩家: " + playerName, w/2f, h*0.56f, p);
            int nf = foundRooms.size();
            p.setColor(theme.textMuted); p.setTextSize(22);
            if (nf == 0) {
                c.drawText("正在搜索附近的房间...", w/2f, h*0.60f, p);
            } else {
                c.drawText("—— 附近的房间 ——", w/2f, h*0.59f, p);
                float cardY = h*0.612f, cardH = h*0.058f, cardGap = h*0.008f, cardL = w*0.06f, cardR = w*0.94f;
                p.setTextAlign(Paint.Align.LEFT);
                int limit = Math.min(nf, 4);
                for (int i = 0; i < limit; i++) {
                    DiscoveredRoom dr = foundRooms.get(i);
                    float cy = cardY + i * (cardH + cardGap);
                    p.setColor(theme.btn); p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(new RectF(cardL, cy, cardR, cy+cardH), 12, 12, p);
                    p.setColor(theme.boardStroke); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1);
                    c.drawRoundRect(new RectF(cardL+1, cy+1, cardR-1, cy+cardH-1), 11, 11, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(theme.score);
                    c.drawRoundRect(new RectF(cardL, cy, cardL+4, cy+cardH), 2, 2, p);
                    p.setColor(theme.text); p.setTextSize(24);
                    c.drawText(dr.room, cardL+14, cy+16, p);
                    p.setColor(theme.textMuted); p.setTextSize(18);
                    c.drawText("房主: " + dr.name, cardL+14, cy+cardH-8, p);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setColor(theme.score); p.setTextSize(26);
                    c.drawText("→", cardR-28, cy+cardH/2+9, p);
                    p.setTextAlign(Paint.Align.LEFT);
                }
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(theme.textMuted); p.setTextSize(18);
                c.drawText("发现 " + nf + " 个房间 · 点击房间加入", w/2f, cardY + limit*(cardH+cardGap) + h*0.010f, p);
            }
        }
        p.setColor(theme.textMuted); p.setTextSize(24);
        c.drawText(p2pStatus, w/2f, h*0.84f, p);
        int start = Math.max(0, chat.size() - 4);
        for (int i=start;i<chat.size();i++) c.drawText(chat.get(i), w/2f, h*(0.875f + 0.035f*(i-start)), p);
        if (pendingStartAt > 0) {
            long left = Math.max(0, (pendingStartAt - System.currentTimeMillis() + 999) / 1000);
            p.setColor(theme.score); p.setTextSize(42); c.drawText("倒计时 " + left, w/2f, h*0.96f, p);
        }
    }
}
