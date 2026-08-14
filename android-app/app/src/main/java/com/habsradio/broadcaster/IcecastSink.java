package com.habsradio.broadcaster;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLSocketFactory;

/** Persistent source connection for Icecast/AzuraCast/Zeno and SHOUTcast-compatible servers. */
public final class IcecastSink implements AutoCloseable {
    private final StreamTarget target;
    private Socket socket;
    private BufferedOutputStream out;
    private BufferedInputStream in;
    private volatile boolean connected;

    public IcecastSink(StreamTarget target) { this.target = target; }

    public synchronized void connect() throws IOException {
        close();
        Socket raw;
        if (target.tls) {
            raw = SSLSocketFactory.getDefault().createSocket();
        } else {
            raw = new Socket();
        }
        raw.connect(new InetSocketAddress(target.host, target.port), 12000);
        raw.setSoTimeout(12000);
        raw.setKeepAlive(true);
        raw.setTcpNoDelay(true);
        socket = raw;
        out = new BufferedOutputStream(raw.getOutputStream(), 32 * 1024);
        in = new BufferedInputStream(raw.getInputStream(), 8 * 1024);

        if (isShoutcast()) connectShoutcast();
        else connectIcecast();
        socket.setSoTimeout(0);
        connected = true;
    }

    private boolean isShoutcast() {
        String s = target.type == null ? "" : target.type.toLowerCase();
        return s.contains("shoutcast");
    }

    private void connectIcecast() throws IOException {
        String mount = target.mount == null || target.mount.isEmpty() ? "/stream" : target.mount;
        if (!mount.startsWith("/")) mount = "/" + mount;
        String user = target.username == null || target.username.isEmpty() ? "source" : target.username;
        String pass = target.password == null ? "" : target.password;
        String auth = Base64.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String req = "PUT " + mount + " HTTP/1.1\r\n" +
                "Host: " + target.host + ":" + target.port + "\r\n" +
                "Authorization: Basic " + auth + "\r\n" +
                "User-Agent: HABS-Broadcaster-Android/1.0\r\n" +
                "Content-Type: audio/mpeg\r\n" +
                "Ice-Name: " + safeHeader(target.name) + "\r\n" +
                "Ice-Public: 0\r\n" +
                "Ice-Bitrate: " + target.bitrate + "\r\n" +
                "Connection: keep-alive\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        String head = readHeader();
        if (!(head.contains(" 200 ") || head.startsWith("HTTP/1.0 200") || head.startsWith("HTTP/1.1 200") || head.contains(" 201 ") || head.contains(" 204 "))) {
            throw new IOException(friendlyHttpError(head));
        }
    }

    private void connectShoutcast() throws IOException {
        String pass = target.password == null ? "" : target.password;
        String user = target.username == null ? "" : target.username;
        // SHOUTcast 2 compatible source logins commonly accept username:password:#SID.
        String login = user.isEmpty() || "source".equalsIgnoreCase(user) ? pass : user + ":" + pass;
        String mount = target.mount == null ? "" : target.mount.replace("/", "").trim();
        if (!mount.isEmpty() && mount.matches("\\d+")) login += ":#" + mount;
        String req = login + "\r\n" +
                "icy-name:" + safeHeader(target.name) + "\r\n" +
                "icy-pub:0\r\n" +
                "icy-br:" + target.bitrate + "\r\n" +
                "content-type:audio/mpeg\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        byte[] tmp = new byte[128];
        int n = in.read(tmp);
        String response = n <= 0 ? "" : new String(tmp, 0, n, StandardCharsets.ISO_8859_1);
        if (!(response.startsWith("OK2") || response.startsWith("OK") || response.contains("icy-caps"))) {
            throw new IOException("SHOUTcast rejected the source login: " + compact(response));
        }
    }

    public synchronized void write(byte[] data) throws IOException {
        if (!connected || out == null) throw new IOException("Stream is not connected");
        out.write(data);
        out.flush();
    }

    public boolean isConnected() { return connected && socket != null && socket.isConnected() && !socket.isClosed(); }

    private String readHeader() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        while (b.size() < 16 * 1024) {
            int v = in.read();
            if (v < 0) break;
            b.write(v);
            if (state == 0 && v == '\r') state = 1;
            else if (state == 1 && v == '\n') state = 2;
            else if (state == 2 && v == '\r') state = 3;
            else if (state == 3 && v == '\n') break;
            else state = (v == '\r') ? 1 : 0;
        }
        return b.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static String friendlyHttpError(String h) {
        String c = compact(h);
        if (c.contains("403") && c.toLowerCase().contains("mountpoint")) return "Mountpoint is already in use";
        if (c.contains("401") || c.contains("403")) return "Streaming credentials were rejected";
        return "Streaming server rejected the source: " + c;
    }

    private static String safeHeader(String s) { return s == null ? "HABS Radio" : s.replace("\r", " ").replace("\n", " "); }
    private static String compact(String s) {
        if (s == null) return "no response";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }

    @Override public synchronized void close() {
        connected = false;
        try { if (out != null) out.close(); } catch (Exception ignored) { }
        try { if (in != null) in.close(); } catch (Exception ignored) { }
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        out = null; in = null; socket = null;
    }
}
