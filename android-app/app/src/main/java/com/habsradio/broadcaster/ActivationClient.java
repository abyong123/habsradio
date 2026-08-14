package com.habsradio.broadcaster;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ActivationClient {
    public static final String BASE = "https://sanctuarychatphilippines.com/backend/api/v1/";

    public static final class Session {
        public final String token;
        public final String username;
        public final String displayName;
        public final String status;
        public final String expiresAt;

        Session(String token, String username, String displayName, String status, String expiresAt) {
            this.token = token;
            this.username = username;
            this.displayName = displayName;
            this.status = status;
            this.expiresAt = expiresAt;
        }
    }

    private final Context context;
    private final String deviceId;

    public ActivationClient(Context context) {
        this.context = context.getApplicationContext();
        String saved = context.getSharedPreferences("habs_activation", Context.MODE_PRIVATE)
                .getString("device_id", "");
        if (saved == null || saved.length() < 20) {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            saved = "habs-android-" + (androidId == null || androidId.isEmpty() ? UUID.randomUUID().toString() : androidId);
            context.getSharedPreferences("habs_activation", Context.MODE_PRIVATE)
                    .edit().putString("device_id", saved).apply();
        }
        deviceId = saved;
    }

    public String getDeviceId() { return deviceId; }

    public Session login(String username, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", username.trim());
        body.put("password", password);
        body.put("device_id", deviceId);
        body.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
        body.put("app_version", "android-1.0.0-alpha");
        JSONObject res = post("login.php", null, body);
        if (!res.optBoolean("ok")) throw new Exception(res.optString("error", "Activation rejected"));
        JSONObject u = res.optJSONObject("user");
        if (u == null) throw new Exception("Activation response did not include a user");
        String status = u.optString("status", "");
        if (!"active".equalsIgnoreCase(status)) throw new Exception("This account is not active");
        return new Session(
                res.optString("token", ""),
                u.optString("username", username),
                u.optString("display_name", u.optString("username", username)),
                status,
                res.optString("expires_at", "")
        );
    }

    public boolean validate(Session session) throws Exception {
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("app_version", "android-1.0.0-alpha");
        JSONObject res = post("validate.php", session.token, body);
        JSONObject user = res.optJSONObject("user");
        return res.optBoolean("ok") && user != null && "active".equalsIgnoreCase(user.optString("status"));
    }

    public void logout(Session session) {
        if (session == null || session.token == null || session.token.isEmpty()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("device_id", deviceId);
            post("logout.php", session.token, body);
        } catch (Exception ignored) { }
    }

    private JSONObject post(String path, String bearer, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "HABS-Broadcaster-Android/1.0.0-alpha");
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(payload); }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        JSONObject res;
        try { res = new JSONObject(sb.length() == 0 ? "{}" : sb.toString()); }
        catch (Exception e) { throw new Exception("Activation server returned an invalid response (HTTP " + code + ")"); }
        if (code < 200 || code >= 300) throw new Exception(res.optString("error", "Activation server returned HTTP " + code));
        return res;
    }
}
