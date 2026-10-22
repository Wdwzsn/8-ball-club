package com.pool8.net;

import com.pool8.core.Shot;
import com.pool8.rules.Match;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

/**
 * 主机权威的局域网/互联网双人对战。
 *
 * 协议：主机负责规则与最终判定；客户端把击球参数发给主机，主机应用后广播
 * “本杆起始局面”，双方各自用同一套确定性物理引擎推演动画；一杆结束后主机
 * 再广播权威结果快照，客户端据其对账，彻底避免浮点漂移。
 */
public final class NetGame implements Closeable {
    public static final int DEFAULT_PORT = 24680;
    public static final int VERSION = 1;

    public interface Listener {
        void onStart(int myIndex, long seed, String[] names);
        void onShot(Shot shot, Snapshot pre);
        void onResult(Snapshot post);
        void onDisconnect(String reason);
        void onStatus(String status);
    }

    private static final byte C_HELLO = 1;
    private static final byte C_START = 2;
    private static final byte C_SHOT = 3;
    private static final byte C_RESULT = 4;
    private static final byte C_BYE = 5;

    private final boolean host;
    private final Listener listener;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private ServerSocket server;
    private volatile boolean closed;
    private int myIndex;
    private String peerName = "";
    private String status = "";
    public long seed = System.nanoTime();

    private NetGame(boolean host, Listener l) {
        this.host = host;
        this.listener = l;
    }

    public boolean isHost() { return host; }
    public int myIndex() { return myIndex; }
    public int remoteIndex() { return 1 - myIndex; }
    public String peerName() { return peerName; }
    public String status() { return status; }
    public boolean connected() { return out != null && !closed; }

    /** 创建房间（异步等待对手）。 */
    public static NetGame host(int port, String myName, Listener l) {
        NetGame g = new NetGame(true, l);
        g.myIndex = 0;
        Thread t = new Thread(() -> g.runHost(port, myName), "pool-host");
        t.setDaemon(true);
        t.start();
        return g;
    }

    /** 加入房间（异步连接）。 */
    public static NetGame join(String addr, int port, String myName, Listener l) {
        NetGame g = new NetGame(false, l);
        g.myIndex = 1;
        Thread t = new Thread(() -> g.runClient(addr, port, myName), "pool-client");
        t.setDaemon(true);
        t.start();
        return g;
    }

    private void setStatus(String s) {
        status = s;
        listener.onStatus(s);
    }

    private void runHost(int port, String myName) {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            setStatus("等待对手连接 · 端口 " + port);
            socket = server.accept();
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            byte type = in.readByte();
            if (type != C_HELLO) throw new IOException("协议错误");
            int ver = in.readInt();
            if (ver != VERSION) throw new IOException("版本不一致");
            peerName = in.readUTF();
            String[] names = {myName, peerName};
            sendStart(names);
            listener.onStart(0, seed, names);
            setStatus("已连接：" + peerName);
            readLoop();
        } catch (IOException e) {
            if (!closed) fail("连接失败：" + e.getMessage());
        }
    }

    private void runClient(String addr, int port, String myName) {
        try {
            setStatus("正在连接 " + addr + ":" + port + " …");
            socket = new Socket();
            socket.connect(new InetSocketAddress(addr, port), 8000);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10000);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.writeByte(C_HELLO);
            out.writeInt(VERSION);
            out.writeUTF(myName);
            out.flush();
            byte type = in.readByte();
            if (type != C_START) throw new IOException("未收到开局信息");
            long seed = in.readLong();
            socket.setSoTimeout(0);
            String n0 = in.readUTF();
            String n1 = in.readUTF();
            String[] names = {n0, n1};
            listener.onStart(1, seed, names);
            setStatus("已连接：" + n0);
            readLoop();
        } catch (SocketTimeoutException e) {
            if (!closed) fail("连接超时");
        } catch (IOException e) {
            if (!closed) fail("无法连接：" + e.getMessage());
        }
    }

    private void sendStart(String[] names) throws IOException {
        out.writeByte(C_START);
        out.writeLong(seed);
        out.writeUTF(names[0]);
        out.writeUTF(names[1]);
        out.flush();
    }

    private void readLoop() throws IOException {
        while (!closed) {
            byte type;
            try {
                type = in.readByte();
            } catch (IOException eof) {
                break;
            }
            switch (type) {
                case C_SHOT: {
                    Shot s = readShot(in);
                    Snapshot pre = Snapshot.read(in);
                    listener.onShot(s, pre);
                    break;
                }
                case C_RESULT: {
                    Snapshot post = Snapshot.read(in);
                    listener.onResult(post);
                    break;
                }
                case C_BYE:
                    throw new IOException("对手离开了房间");
                default:
                    throw new IOException("未知协议包 " + type);
            }
        }
        if (!closed) fail("对手断开连接");
    }

    private static void writeShot(Shot s, DataOutputStream o) throws IOException {
        o.writeDouble(s.x);
        o.writeDouble(s.y);
        o.writeDouble(s.angle);
        o.writeDouble(s.power);
        o.writeDouble(s.spinX);
        o.writeDouble(s.spinY);
        o.writeInt(s.calledPocket);
    }

    private static Shot readShot(DataInputStream i) throws IOException {
        return new Shot(i.readDouble(), i.readDouble(), i.readDouble(), i.readDouble(),
                i.readDouble(), i.readDouble(), i.readInt());
    }

    /** 客户端把击球请求发给主机（附带自己的局面前快照，便于主机校验）。 */
    public synchronized void sendShotToHost(Shot s, Match m) {
        if (out == null) return;
        try {
            out.writeByte(C_SHOT);
            writeShot(s, out);
            Snapshot.of(m).write(out);
            out.flush();
        } catch (IOException e) {
            fail("发送失败：" + e.getMessage());
        }
    }

    /** 主机广播“本杆开始”：母球杆法 + 起始局面。 */
    public synchronized void broadcastShot(Shot s, Match m) {
        if (out == null) return;
        try {
            out.writeByte(C_SHOT);
            writeShot(s, out);
            Snapshot.of(m).write(out);
            out.flush();
        } catch (IOException e) {
            fail("发送失败：" + e.getMessage());
        }
    }

    /** 主机广播规则结算后的权威局面。 */
    public synchronized void sendResult(Match m) {
        if (out == null) return;
        try {
            out.writeByte(C_RESULT);
            Snapshot.of(m).write(out);
            out.flush();
        } catch (IOException e) {
            fail("发送失败：" + e.getMessage());
        }
    }

    private void fail(String why) {
        if (closed) return;
        closed = true;
        listener.onDisconnect(why);
    }

    @Override
    public void close() {
        closed = true;
        try {
            if (out != null) {
                out.writeByte(C_BYE);
                out.flush();
            }
        } catch (IOException ignored) {}
        closeQuietly(socket);
        closeQuietly(server);
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) {}
    }

    /** 本机 IPv4 地址，用于告知好友。 */
    public static String localAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nis = java.net.NetworkInterface.getNetworkInterfaces();
            String fallback = "127.0.0.1";
            while (nis.hasMoreElements()) {
                java.net.NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    java.net.InetAddress a = as.nextElement();
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
            return fallback;
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) throws Exception {
        // 简单连通性测试：主机 + 客户端 + 一次击球
        final Object lock = new Object();
        final int[] done = {0};
        Listener l = new Listener() {
            public void onStart(int idx, long seed, String[] names) {
                synchronized (lock) { done[0]++; lock.notifyAll(); }
                System.out.println("onStart " + idx + " " + seed);
            }
            public void onShot(Shot s, Snapshot pre) {
                System.out.println("onShot " + s);
                synchronized (lock) { done[0]++; lock.notifyAll(); }
            }
            public void onResult(Snapshot post) {
                System.out.println("onResult winner=" + post.winner);
                synchronized (lock) { done[0]++; lock.notifyAll(); }
            }
            public void onDisconnect(String why) { System.out.println("disc " + why); }
            public void onStatus(String s) { System.out.println("status " + s); }
        };
        NetGame h = host(24777, "房主", l);
        Thread.sleep(300);
        NetGame c = join("127.0.0.1", 24777, "客人", l);
        Thread.sleep(800);
        synchronized (lock) { if (done[0] < 3) lock.wait(3000); }
        System.out.println("测试完成 events=" + done[0]);
        h.close();
        c.close();
    }
}
