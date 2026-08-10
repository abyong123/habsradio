package com.habsradio.broadcaster;

import org.json.JSONException;
import org.json.JSONObject;

public final class StreamTarget {
    public String name = "My Station";
    public String type = "Zeno.fm / Icecast";
    public String host = "";
    public int port = 8000;
    public String mount = "/stream";
    public String username = "source";
    public String password = "";
    public boolean tls = false;
    public int bitrate = 128;
    public boolean enabled = true;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name); o.put("type", type); o.put("host", host); o.put("port", port);
        o.put("mount", mount); o.put("username", username); o.put("password", password);
        o.put("tls", tls); o.put("bitrate", bitrate); o.put("enabled", enabled);
        return o;
    }

    public static StreamTarget fromJson(JSONObject o) {
        StreamTarget t = new StreamTarget();
        t.name = o.optString("name", t.name); t.type = o.optString("type", t.type);
        t.host = o.optString("host", ""); t.port = o.optInt("port", 8000);
        t.mount = o.optString("mount", "/stream"); t.username = o.optString("username", "source");
        t.password = o.optString("password", ""); t.tls = o.optBoolean("tls", false);
        t.bitrate = o.optInt("bitrate", 128); t.enabled = o.optBoolean("enabled", true);
        if (!t.mount.startsWith("/")) t.mount = "/" + t.mount;
        return t;
    }
}
