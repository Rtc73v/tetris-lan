package com.echo.tetrislan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.echo.tetrislan.net.ProtocolMessage;

public class P2pTransport {
    public interface Listener {
        void onRoomFound(String room, String host, String name, boolean hostRole);
        void onPeer(String host, String name, int score, int lines, int level, boolean over, int kos, int badges, String board, String via);
        void onReady(String host, String name, boolean ready);
        void onChat(String host, String name, String text);
        void onStart(String host, long seed, long startAt);
        void onGarbage(String fromName, int rows);
        void onKO(String host, String targetName, String killerName);
        void onLeave(String host, String name);
        void onKick(String host, String name, String targetName, String reason);
        void onDisband(String host, String name);
        void onDisconnect(String host);
        void onSurrender(String host, String name);
        void onReturnLobby(String host, String name);
        void onBotState(String host, String botName, int score, int lines, int level, boolean over, int kos, int badges, String board);
        void onReconnect(String host, String name);
        void onError(String message);
    }

    private static final int UDP_PORT = 39731;
    private static final int TCP_PORT = 39732;
    private final Listener listener;
    private final String room;
    private final String pass;
    private final String name;
    private final String playerId;
    private boolean hostRole;
    private final Map<String, Peer> peers = new HashMap<>();
    private final Set<String> connecting = new HashSet<>();
    private final Set<String> localAddresses = new HashSet<>();
    private volatile boolean running;
    private DatagramSocket udpSocket;
    private ServerSocket tcpServer;
    private Thread udpThread;
    private Thread tcpThread;
    private int score;
    private int lines;
    private int level = 1;
    private boolean over;
    private int kos;
    private int badges;

    public P2pTransport(String room, String pass, String name, String playerId, boolean hostRole, Listener listener) {
        this.room = safe(room, "ROOM");
        this.pass = safe(pass, "1234");
        this.name = safe(name, "P1");
        this.playerId = safe(playerId, "pid");
        this.hostRole = hostRole;
        this.listener = listener;
        localAddresses.add("127.0.0.1");
        localAddresses.add("0.0.0.0");
        localAddresses.add("::1");
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                Enumeration<InetAddress> addrs = nis.nextElement().getInetAddresses();
                while (addrs.hasMoreElements()) {
                    localAddresses.add(addrs.nextElement().getHostAddress());
                }
            }
        } catch (Exception ignored) {}
    }

    public void start() {
        if (running) return;
        running = true;
        udpThread = new Thread(this::udpLoop, "tetris-p2p-udp");
        tcpThread = new Thread(this::tcpLoop, "tetris-p2p-tcp");
        udpThread.start();
        tcpThread.start();
    }

    public void stop() {
        running = false;
        if (udpSocket != null) udpSocket.close();
        if (tcpServer != null) {
            try { tcpServer.close(); } catch (Exception ignored) {}
        }
        synchronized (peers) {
            for (Peer p : peers.values()) p.close();
            peers.clear();
        }
    }

    public String room() { return room; }

    public void publishState(int score, int lines, int level, boolean over, int kos, int badges, String board) {
        this.score = score;
        this.lines = lines;
        this.level = level;
        this.over = over;
        this.kos = kos;
        this.badges = badges;
        sendReliable(statePacket(board));
    }

    public void publishBotState(String botName, int score, int lines, int level, boolean over, int kos, int badges, String board) {
        sendReliable(base(ProtocolMessage.BOT_STATE, botName) + "|" + score + "|" + lines + "|" + level + "|" + (over ? 1 : 0) + "|" + kos + "|" + badges + "|" + (board == null ? "" : board));
    }

    public void sendReady(boolean ready) {
        sendReliable(base(ProtocolMessage.READY) + "|" + (ready ? 1 : 0) + "|0|0|0|" + playerId);
    }

    public void sendChat(String text) {
        sendReliable(base(ProtocolMessage.CHAT) + "|" + esc(text) + "|0|0|0|" + playerId);
    }

    public void sendStart(long seed, long startAt) {
        sendReliable(base(ProtocolMessage.START) + "|" + seed + "|" + startAt + "|0|0|" + playerId);
    }

    public void sendStart(long seed) {
        sendStart(seed, System.currentTimeMillis() + 3000);
    }

    public void sendGarbage(int rows) {
        if (rows <= 0) return;
        sendReliable(base(ProtocolMessage.GARBAGE) + "|" + rows + "|0|0|0|" + playerId);
    }

    public void sendKO(String targetName, String killerName) {
        sendReliable(base(ProtocolMessage.KO) + "|" + esc(targetName) + "|" + esc(killerName) + "|0|0|" + playerId);
    }

    public void sendLeave() {
        sendReliable(base(ProtocolMessage.LEAVE) + "|0|0|0|0|" + playerId);
    }

    public void sendSurrender() {
        sendReliable(base(ProtocolMessage.SURRENDER) + "|0|0|0|0|" + playerId);
    }

    public void sendReturnLobby() {
        sendReliable(base(ProtocolMessage.RETURN_LOBBY) + "|0|0|0|0|" + playerId);
    }

    public void sendKick(String targetHost, String targetName, String reason) {
        sendReliable(base(ProtocolMessage.KICK) + "|" + esc(targetName) + "|" + esc(reason) + "|0|0|" + playerId);
        synchronized (peers) {
            Peer p = peers.get(targetHost);
            if (p != null) { p.close(); peers.remove(targetHost); }
        }
    }

    public void sendDisband() {
        sendReliable(base(ProtocolMessage.DISBAND) + "|0|0|0|0|" + playerId);
    }

    public void setHostRole(boolean hostRole) {
        this.hostRole = hostRole;
    }

    public Set<String> getLocalAddresses() {
        return localAddresses;
    }

    private void sendReliable(String msg) {
        sendUdp("255.255.255.255", msg);
        synchronized (peers) {
            for (Peer p : peers.values()) p.send(msg);
        }
    }

    private void udpLoop() {
        try {
            udpSocket = new DatagramSocket(UDP_PORT);
            udpSocket.setBroadcast(true);
            udpSocket.setSoTimeout(500);
            long last = 0;
            byte[] buf = new byte[4096];
            while (running) {
                long now = System.currentTimeMillis();
                if (now - last > 1000) {
                    sendUdp("255.255.255.255", helloPacket());
                    last = now;
                }
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);
                    handleMessage(packet.getAddress().getHostAddress(), new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8), "udp");
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception e) {
            if (running) listener.onError("UDP " + e.getMessage());
        }
    }

    private void tcpLoop() {
        try {
            tcpServer = new ServerSocket(TCP_PORT);
            tcpServer.setSoTimeout(500);
            while (running) {
                try {
                    Socket socket = tcpServer.accept();
                    startPeer(socket, socket.getInetAddress().getHostAddress());
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception e) {
            if (running) listener.onError("TCP " + e.getMessage());
        }
    }

    private void handleMessage(String host, String msg, String via) {
        if (localAddresses.contains(host)) return;
        String[] p = split(msg);
        if (p == null) return;
        if (ProtocolMessage.HELLO.equals(p[3])) listener.onRoomFound(p[1], host, p[4], parseInt(p[7]) == 1);
        if (!validRoom(p)) return;
        if (ProtocolMessage.HELLO.equals(p[3])) {
            connectTcp(host);
            return;
        }
        if (ProtocolMessage.STATE.equals(p[3])) notifyPeer(host, p, via);
        if (ProtocolMessage.READY.equals(p[3])) listener.onReady(host, p[4], parseInt(p[5]) == 1);
        if (ProtocolMessage.CHAT.equals(p[3])) listener.onChat(host, p[4], unesc(p[5]));
        if (ProtocolMessage.START.equals(p[3])) listener.onStart(host, parseLong(p[5]), parseLong(p[6]));
        if (ProtocolMessage.GARBAGE.equals(p[3])) listener.onGarbage(p[4], parseInt(p[5]));
        if (ProtocolMessage.KO.equals(p[3])) listener.onKO(host, unesc(p[5]), unesc(p[6]));
        if (ProtocolMessage.LEAVE.equals(p[3])) { listener.onLeave(host, p[4]); return; }
        if (ProtocolMessage.KICK.equals(p[3])) { listener.onKick(host, p[4], unesc(p[5]), unesc(p[6])); return; }
        if (ProtocolMessage.DISBAND.equals(p[3])) { listener.onDisband(host, p[4]); return; }
        if (ProtocolMessage.SURRENDER.equals(p[3])) { listener.onSurrender(host, p[4]); return; }
        if (ProtocolMessage.RETURN_LOBBY.equals(p[3])) { listener.onReturnLobby(host, p[4]); return; }
        if (ProtocolMessage.RECONNECT.equals(p[3])) { listener.onReconnect(host, p[4]); return; }
        if (ProtocolMessage.BOT_STATE.equals(p[3])) notifyBot(host, p, via);
    }

    private void connectTcp(String host) {
        synchronized (peers) { if (peers.containsKey(host)) return; }
        synchronized (connecting) { if (!connecting.add(host)) return; }
        new Thread(() -> {
            try {
                Socket socket = new Socket(host, TCP_PORT);
                startPeer(socket, host);
            } catch (Exception e) {
            } finally {
                synchronized (connecting) { connecting.remove(host); }
            }
        }, "tetris-p2p-connect").start();
    }

    private void startPeer(Socket socket, String host) {
        try {
            Peer peer = new Peer(socket, host);
            synchronized (peers) { peers.put(host, peer); }
            peer.send(helloPacket());
            peer.send(statePacket());
            new Thread(peer::readLoop, "tetris-p2p-peer").start();
        } catch (Exception e) {
            listener.onError("Peer " + e.getMessage());
        }
    }

    private void sendUdp(String host, String message) {
        new Thread(() -> {
            try {
                byte[] data = message.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(host), UDP_PORT);
                DatagramSocket out = new DatagramSocket();
                out.setBroadcast(true);
                out.send(packet);
                out.close();
            } catch (Exception e) {
                listener.onError("Send " + e.getMessage());
            }
        }, "tetris-p2p-udp-send").start();
    }

    private String helloPacket() {
        return base(ProtocolMessage.HELLO) + "|0|0|" + (hostRole ? 1 : 0) + "|0|" + playerId;
    }

    private String statePacket(String board) {
        return base(ProtocolMessage.STATE) + "|" + score + "|" + lines + "|" + level + "|" + (over ? 1 : 0) + "|" + kos + "|" + badges + "|" + (board == null ? "" : board) + "|" + playerId;
    }

    private String statePacket() {
        return statePacket("");
    }

    private String botStatePacket(String overrideName) {
        return base(ProtocolMessage.BOT_STATE, overrideName) + "|" + score + "|" + lines + "|" + level + "|" + (over ? 1 : 0) + "|" + kos + "|" + badges;
    }

    private String base(String type) {
        return base(type, name);
    }

    private String base(String type, String senderName) {
        return "TG1|" + room + "|" + pass + "|" + type + "|" + senderName;
    }

    private String[] split(String msg) {
        String[] p = msg.split("\\|", -1);
        if (p.length < 8 || !ProtocolMessage.PROTOCOL_VERSION.equals(p[0])) return null;
        return p;
    }

    private boolean validRoom(String[] p) {
        return room.equals(p[1]) && pass.equals(p[2]);
    }

    private void notifyPeer(String host, String[] p, String via) {
        boolean over = p.length > 8 && parseInt(p[8]) == 1;
        int kos = p.length > 9 ? parseInt(p[9]) : 0;
        int badges = p.length > 10 ? parseInt(p[10]) : 0;
        String board = p.length > 11 ? p[11] : "";
        listener.onPeer(host, p[4], parseInt(p[5]), parseInt(p[6]), parseInt(p[7]), over, kos, badges, board, via);
    }

    private void notifyBot(String host, String[] p, String via) {
        boolean over = p.length > 8 && parseInt(p[8]) == 1;
        int kos = p.length > 9 ? parseInt(p[9]) : 0;
        int badges = p.length > 10 ? parseInt(p[10]) : 0;
        String board = p.length > 11 ? p[11] : "";
        listener.onBotState(host, p[4], parseInt(p[5]), parseInt(p[6]), parseInt(p[7]), over, kos, badges, board);
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private static String safe(String value, String fallback) {
        if (value == null) return fallback;
        String v = value.trim().replace("|", "");
        return v.isEmpty() ? fallback : v;
    }

    private static String esc(String value) {
        return safe(value, "").replace("%", "%25").replace("\n", " ").replace("|", "%7C");
    }

    private static String unesc(String value) {
        return value.replace("%7C", "|").replace("%25", "%");
    }

    private class Peer {
        private final Socket socket;
        private final String host;
        private final BufferedReader in;
        private final BufferedWriter out;

        Peer(Socket socket, String host) throws Exception {
            this.socket = socket;
            this.host = host;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String msg) {
            try {
                out.write(msg);
                out.write('\n');
                out.flush();
            } catch (Exception e) {
                close();
            }
        }

        void readLoop() {
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                    handleMessage(host, line, "tcp");
                }
            } catch (Exception ignored) {
            } finally {
                close();
                synchronized (peers) { peers.remove(host); }
                listener.onDisconnect(host);
            }
        }

        void close() {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
