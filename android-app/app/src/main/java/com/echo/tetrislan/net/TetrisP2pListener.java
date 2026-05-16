package com.echo.tetrislan.net;

import com.echo.tetrislan.TetrisView;
import com.echo.tetrislan.ai.BotPlayer;

import java.util.Iterator;
import java.util.Map;

public class TetrisP2pListener implements P2pTransport.Listener {
    private final TetrisView view;

    public TetrisP2pListener(TetrisView view) {
        this.view = view;
    }

    @Override public void onRoomFound(String room, String host, String name, boolean hostRole) {
        if (view.reconnectCheckHost != null && view.reconnectCheckHost.equals(host)) {
            view.reconnectCheckHost = null; view.reconnectCheckUntil = 0;
            view.addChat("系统: 找到目标房间，正在加入");
        }
        if (view.roomName.equals(room) && hostRole && !view.isHost) {
            view.roomHost = host; view.lastRoomHost = host;
            view.sp.edit().putString("last_room_host", view.lastRoomHost).apply();
        }
        if (!view.roomName.equals(room) && hostRole) {
            boolean found = false;
            long now = System.currentTimeMillis();
            Iterator<DiscoveredRoom> it = view.foundRooms.iterator();
            while (it.hasNext()) {
                DiscoveredRoom dr = it.next();
                if (now - dr.lastSeenMs > 30000) { it.remove(); continue; }
                if (dr.room.equals(room) && dr.host.equals(host)) {
                    dr.name = name; dr.lastSeenMs = now;
                    found = true;
                }
            }
            if (!found) view.foundRooms.add(new DiscoveredRoom(room, host, name));
        }
    }

    @Override public void onPeer(String host, String name, int score, int lines, int level, boolean over, int kos, int badges, String board, String via) {
        view.lastAnyPeerUpdate = System.currentTimeMillis();
        if (view.networkFrozen) { view.networkFrozen = false; view.addChat("系统: 网络恢复，游戏继续"); }
        if (!view.acceptPeer(host, name)) return;
        PeerInfo pi = view.peerInfos.get(host);
        if (pi == null) { pi = new PeerInfo(name); view.peerInfos.put(host, pi); }
        pi.name = name; pi.score = score; pi.lines = lines; pi.level = level;
        pi.over = over; pi.kos = kos; pi.badges = badges;
        pi.lastUpdateMs = System.currentTimeMillis(); pi.via = via;
        pi.disconnected = false; pi.disconnectedAt = 0;
        if (board != null && !board.isEmpty()) pi.board = TetrisView.decodeBoard(board);
        view.peerNames.put(host, name);
        view.p2pStatus = "已连接 " + view.playerCount() + "/" + view.MAX_PLAYERS;
    }

    @Override public void onReady(String host, String name, boolean ready) {
        if (!view.acceptPeer(host, name)) return;
        view.peerNames.put(host, name); view.readyPeers.put(host, ready);
        view.addChat("系统: " + name + (ready ? " 已准备" : " 取消准备"));
        // maybeCountdown is empty now
    }

    @Override public void onChat(String host, String name, String text) {
        if (view.acceptPeer(host, name)) view.addChat(name + ": " + text);
    }

    @Override public void onStart(String host, long seed, long startAt) {
        if (!view.fromRoomHost(host)) return;
        view.pendingStartSeed = seed; view.pendingStartAt = startAt; view.p2pStatus = "3秒后开始";
    }

    @Override public void onGarbage(String fromName, int rows) {
        view.pendingGarbage += rows; view.lastAttacker = fromName;
        view.garbageDueAt = System.currentTimeMillis() + 1800;
        view.p2pStatus = fromName + " 送了 " + rows + " 行";
        view.tone(view.sGarbage); view.fx("WARNING +" + rows, true);
    }

    @Override public void onKO(String host, String targetName, String killerName) {
        if (killerName.equals(view.playerName)) view.kos++;
        for (PeerInfo pi : view.peerInfos.values()) { if (pi.name.equals(killerName)) { pi.kos++; break; } }
        for (BotPlayer bot : view.bots.values()) { if (bot.name.equals(killerName)) { bot.kos++; break; } }
    }

    @Override public void onLeave(String host, String name) {
        view.peerNames.remove(host); view.readyPeers.remove(host); view.peerInfos.remove(host);
        view.addChat("系统: " + name + " 离开了房间");
        view.p2pStatus = view.p2p == null ? "P2P未启动" : "已连接 " + view.playerCount() + "/" + view.MAX_PLAYERS;
    }

    @Override public void onKick(String host, String name, String targetName, String reason) {
        if (!view.fromRoomHost(host)) return;
        if (targetName.equals(view.playerName)) {
            view.stopP2p(); view.isHost = false;
            view.fx("被踢出", true);
            view.addChat("系统: 你被房主踢出 (" + reason + ")");
            return;
        }
        for (Iterator<Map.Entry<String, String>> it = view.peerNames.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (e.getValue().equals(targetName)) {
                it.remove(); view.readyPeers.remove(e.getKey());
                view.addChat("系统: " + targetName + " 被踢出");
                break;
            }
        }
    }

    @Override public void onDisband(String host, String name) {
        if (!view.fromRoomHost(host)) return;
        view.stopP2p(); view.isHost = false;
        view.fx("房间已解散", true);
        view.addChat("系统: 房主解散了房间");
    }

    @Override public void onDisconnect(String host) {
        PeerInfo pi = view.peerInfos.get(host);
        if (pi != null) {
            pi.disconnected = true;
            pi.disconnectedAt = System.currentTimeMillis();
            view.addChat("系统: " + pi.name + " 断开连接，30秒内可重连");
        } else {
            String name = view.peerNames.get(host);
            if (name != null) view.addChat("系统: " + name + " 断开连接");
        }
        if (view.roomHost != null && view.roomHost.equals(host) && !view.isHost) {
            String newHost = null;
            String newHostName = null;
            for (Map.Entry<String, PeerInfo> e : view.peerInfos.entrySet()) {
                if (e.getValue().disconnected) continue;
                if (e.getValue().isBot) continue;
                newHost = e.getKey();
                newHostName = e.getValue().name;
                break;
            }
            if (newHost != null) {
                view.roomHost = newHost;
                view.lastRoomHost = newHost;
                view.sp.edit().putString("last_room_host", view.lastRoomHost).apply();
                view.addChat("系统: 房主已断开，" + newHostName + " 成为新房主");
                if (view.p2p != null && view.p2p.getLocalAddresses().contains(newHost)) {
                    view.isHost = true;
                    view.p2p.setHostRole(true);
                    view.addChat("系统: 你已成为新房主");
                }
            }
        }
        view.p2pStatus = view.p2p == null ? "P2P未启动" : "已连接 " + view.playerCount() + "/" + view.MAX_PLAYERS;
    }

    @Override public void onSurrender(String host, String name) {
        PeerInfo pi = view.peerInfos.get(host);
        if (pi != null) { pi.over = true; }
        view.addChat("系统: " + name + " 认输");
        view.checkMultiFinish();
    }

    @Override public void onReturnLobby(String host, String name) {
        if (!view.fromRoomHost(host)) return;
        view.returnToRoom();
    }

    @Override public void onBotState(String host, String botName, int score, int lines, int level, boolean over, int kos, int badges, String board) {
        PeerInfo pi = view.peerInfos.get(host);
        if (pi == null) { pi = new PeerInfo(botName); view.peerInfos.put(host, pi); }
        pi.name = botName; pi.score = score; pi.lines = lines; pi.level = level;
        pi.over = over; pi.kos = kos; pi.badges = badges;
        if (board != null && !board.isEmpty()) pi.board = TetrisView.decodeBoard(board);
        pi.lastUpdateMs = System.currentTimeMillis(); pi.via = "bot";
        view.lastAnyPeerUpdate = System.currentTimeMillis();
        view.peerNames.put(host, botName);
    }

    @Override public void onReconnect(String host, String name) {
        PeerInfo pi = view.peerInfos.get(host);
        if (pi != null) {
            pi.disconnected = false;
            pi.disconnectedAt = 0;
        }
        view.addChat("系统: " + name + " 重新连接");
    }

    @Override public void onError(String message) { view.p2pStatus = "P2P错误"; }
}
