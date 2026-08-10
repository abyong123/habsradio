package com.habsradio.broadcaster;

import org.json.*;import java.util.*;

public final class BroadcastState{
 public static final class S{public String id,name,state="OFF AIR",error="";public boolean desired,connected;public long bytes;S(StreamTarget t){id=t.id;name=t.name;}}
 private static final LinkedHashMap<String,S> streams=new LinkedHashMap<>();private static boolean captureActive=false,connecting=false;private static String lastError="",programDb="-∞ dBFS";private static long startedAt=0;
 public static synchronized void begin(List<StreamTarget> ts){streams.clear();for(StreamTarget t:ts){S s=new S(t);s.desired=true;s.state="CONNECTING";streams.put(t.id,s);}captureActive=false;connecting=true;lastError="";programDb="-∞ dBFS";startedAt=System.currentTimeMillis();}
 public static synchronized void capture(boolean v){captureActive=v;}
 public static synchronized void level(String db){programDb=db;}
 public static synchronized void connected(StreamTarget t){S s=streams.get(t.id);if(s!=null){s.connected=true;s.desired=true;s.state="ON AIR";s.error="";}connecting=countConnected()==0&&countDesired()>0;}
 public static synchronized void disconnected(StreamTarget t,String err){S s=streams.get(t.id);if(s!=null){s.connected=false;s.state=s.desired?"CONNECTING":"OFF AIR";s.error=err==null?"":err;}if(err!=null&&!err.isEmpty())lastError=err;connecting=countConnected()==0&&countDesired()>0;}
 public static synchronized void bytes(StreamTarget t,int n){S s=streams.get(t.id);if(s!=null)s.bytes+=n;}
 public static synchronized void stop(String reason){for(S s:streams.values()){s.connected=false;s.desired=false;s.state="OFF AIR";}captureActive=false;connecting=false;if(reason!=null&&!reason.isEmpty()&&!"Broadcast stopped".equals(reason))lastError=reason;}
 public static synchronized void reset(){streams.clear();captureActive=false;connecting=false;lastError="";programDb="-∞ dBFS";startedAt=0;}
 private static int countConnected(){int n=0;for(S s:streams.values())if(s.connected)n++;return n;}private static int countDesired(){int n=0;for(S s:streams.values())if(s.desired)n++;return n;}
 public static synchronized JSONObject json(){JSONObject o=new JSONObject();JSONArray a=new JSONArray();long bytes=0;for(S s:streams.values()){JSONObject x=new JSONObject();try{x.put("id",s.id);x.put("name",s.name);x.put("desired",s.desired);x.put("connected",s.connected);x.put("state",s.state);x.put("bytes_sent",s.bytes);x.put("last_error",s.error);x.put("last_error_code",s.error.isEmpty()?"":"android_stream_error");}catch(Exception ignored){}a.put(x);bytes+=s.bytes;}try{o.put("streams",a);o.put("connected_count",countConnected());o.put("desired_count",countDesired());o.put("on_air",countConnected()>0);o.put("connected",countConnected()>0);o.put("connecting",connecting);o.put("capture_active",captureActive);o.put("bytes_sent",bytes);o.put("last_error",lastError);o.put("program_db",programDb);o.put("started_at",startedAt);}catch(Exception ignored){}return o;}
}
