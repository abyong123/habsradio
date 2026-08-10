package com.habsradio.broadcaster;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ActivationClient {
    public static final String BASE="https://sanctuarychatphilippines.com/backend/api/v1/";
    public static final class Session {
        public final String token,username,displayName,status,expiresAt;
        Session(String t,String u,String d,String s,String e){token=t;username=u;displayName=d;status=s;expiresAt=e;}
    }
    private final Context context; private final String deviceId;
    public ActivationClient(Context c){context=c.getApplicationContext();String saved=context.getSharedPreferences("habs_activation",Context.MODE_PRIVATE).getString("device_id","");if(saved==null||saved.length()<20){String id=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID);saved="habs-android-"+((id==null||id.isEmpty())?UUID.randomUUID():id);context.getSharedPreferences("habs_activation",Context.MODE_PRIVATE).edit().putString("device_id",saved).apply();}deviceId=saved;}
    public Session login(String username,String password)throws Exception{JSONObject b=new JSONObject();b.put("username",username.trim());b.put("password",password);b.put("device_id",deviceId);b.put("device_name",Build.MANUFACTURER+" "+Build.MODEL);b.put("app_version","android-1.1.0-parity");JSONObject r=post("login.php",null,b);if(!r.optBoolean("ok"))throw new Exception(r.optString("error","Activation rejected"));JSONObject u=r.optJSONObject("user");if(u==null)throw new Exception("Activation response did not include a user");String st=u.optString("status","");if(!"active".equalsIgnoreCase(st))throw new Exception("This account is not active");return new Session(r.optString("token",""),u.optString("username",username),u.optString("display_name",u.optString("username",username)),st,r.optString("expires_at",""));}
    public boolean validate(Session s)throws Exception{if(s==null||s.token.isEmpty())return false;JSONObject b=new JSONObject();b.put("device_id",deviceId);b.put("app_version","android-1.1.0-parity");JSONObject r=post("validate.php",s.token,b);JSONObject u=r.optJSONObject("user");return r.optBoolean("ok")&&u!=null&&"active".equalsIgnoreCase(u.optString("status"));}
    public void logout(Session s){if(s==null||s.token.isEmpty())return;try{JSONObject b=new JSONObject();b.put("device_id",deviceId);post("logout.php",s.token,b);}catch(Exception ignored){}}
    private JSONObject post(String path,String bearer,JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","HABS-Broadcaster-Android/1.1.0");if(bearer!=null&&!bearer.isEmpty())c.setRequestProperty("Authorization","Bearer "+bearer);try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();StringBuilder sb=new StringBuilder();if(in!=null)try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}JSONObject r;try{r=new JSONObject(sb.length()==0?"{}":sb.toString());}catch(Exception e){throw new Exception("Activation server returned an invalid response (HTTP "+code+")");}if(code<200||code>=300)throw new Exception(r.optString("error","Activation server returned HTTP "+code));return r;}
}
