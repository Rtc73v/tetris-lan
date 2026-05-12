package com.echo.tetrislan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class P2pTransport {
    public interface Listener {
        void onRoomFound(String room, String host, String name);
        void onPeer(String host, String name, int score, int lines, String via);
        void onReady(String host, String name, boolean ready);
        void onChat(String host, String name, String text);
        void onStart(long seed, long startAt);
        void onGarbage(int rows);
        void onError(String message);
    }

    private static final int UDP_PORT = 39731;
    private static final int TCP_PORT = 39732;
    private final Listener listener;
    private final String room;
    private final String pass;
    private final String name;
    private final Map<String, Peer> peers = new HashMap<>();
    private final Set<String> connecting = new HashSet<>();
    private volatile boolean running;
    private DatagramSocket udpSocket;
    private ServerSocket tcpServer;
    private Thread udpThread;
    private Thread tcpThread;
    private int score;
    private int lines;
    private int level = 1;
    private boolean over;

    public P2pTransport(String room, String pass, String name, Listener listener) {
        this.room = safe(room, "ROOM");
        this.pass = safe(pass, "1234");
        this.name = safe(name, "P1");
        this.listener = listener;
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

    public void publishState(int score, int lines, int level, boolean over) {
        this.score = score;
        this.lines = lines;
        this.level = level;
        this.over = over;
        sendReliable(statePacket());
    }

    public void sendReady(boolean ready) {
        sendReliable(base("READY") + "|" + (ready ? 1 : 0) + "|0|0|0");
    }

    public void sendChat(String text) {
        sendReliable(base("CHAT") + "|" + esc(text) + "|0|0|0");
    }

    public void sendStart(long seed, long startAt) {
        sendReliable(base("START") + "|" + seed + "|" + startAt + "|0|0");
    }

    public void sendStart(long seed) {
        sendStart(seed, System.currentTimeMillis() + 3000);
    }

    public void sendGarbage(int rows) {
        if (rows <= 0) return;
        sendReliable(base("GARBAGE") + "|" + rows + "|0|0|0");
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
                    // Keep responsive to stop().
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
                    // Keep responsive to stop().
                }
            }
        } catch (Exception e) {
            if (running) listener.onError("TCP " + e.getMessage());
        }
    }

    private void handleMessage(String host, String msg, String via) {
        String[] p = split(msg);
        if (p == null) return;
        if ("HELLO".equals(p[3])) listener.onRoomFound(p[1], host, p[4]);
        if (!validRoom(p)) return;
        if ("HELLO".equals(p[3])) {
            connectTcp(host);
            return;
        }
        if ("STATE".equals(p[3])) notifyPeer(host, p, via);
        if ("READY".equals(p[3])) listener.onReady(host, p[4], parseInt(p[5]) == 1);
        if ("CHAT".equals(p[3])) listener.onChat(host, p[4], unesc(p[5]));
        if ("START".equals(p[3])) listener.onStart(parseLong(p[5]), parseLong(p[6]));
        if ("GARBAGE".equals(p[3])) listener.onGarbage(parseInt(p[5]));
    }

    private void connectTcp(String host) {
        synchronized (peers) { if (peers.containsKey(host)) return; }
        synchronized (connecting) { if (!connecting.add(host)) return; }
        new Thread(() -> {
            try {
                Socket socket = new Socket(host, TCP_PORT);
                startPeer(socket, host);
            } catch (Exception e) {
                // UDP fallback remains available.
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
        return base("HELLO") + "|0|0|1|0";
    }

    private String statePacket() {
        return base("STATE") + "|" + score + "|" + lines + "|" + level + "|" + (over ? 1 : 0);
    }

    private String base(String type) {
        return "TG1|" + room + "|" + pass + "|" + type + "|" + name;
    }

    private String[] split(String msg) {
        String[] p = msg.split("\\|", -1);
        if (p.length < 9 || !"TG1".equals(p[0])) return null;
        return p;
    }

    private boolean validRoom(String[] p) {
        return room.equals(p[1]) && pass.equals(p[2]);
    }

    private void notifyPeer(String host, String[] p, String via) {
        listener.onPeer(host, p[4], parseInt(p[5]), parseInt(p[6]), via);
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
            }
        }

        void close() {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
