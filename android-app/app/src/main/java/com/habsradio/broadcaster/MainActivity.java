package com.habsradio.broadcaster;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_AUDIO = 201;
    private static final int REQ_PROJECTION = 202;
    private static final int REQ_MIC = 203;
    private static final int GREEN = Color.rgb(124,255,43);
    private static final int CYAN = Color.rgb(43,220,255);
    private static final int RED = Color.rgb(255,70,88);
    private static final int BG = Color.rgb(2,7,5);
    private static final int PANEL = Color.rgb(7,19,12);
    private static final int LINE = Color.rgb(27,73,40);
    private static final int MUTED = Color.rgb(117,148,126);

    private final ExecutorService net = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ActivationClient activationClient;
    private ActivationClient.Session session;
    private LinearLayout root, content, tabBar;
    private TextView statusText, statusSub, loginError, licensedUser;
    private Button broadcastButton;
    private boolean serviceOnAir, serviceConnecting;
    private final ArrayList<Track> library = new ArrayList<>();
    private final ArrayList<StreamTarget> targets = new ArrayList<>();
    private final ArrayList<StreamEditor> streamEditors = new ArrayList<>();
    private Deck deckA, deckB, activeDeck;
    private SeekBar crossfader;
    private boolean autoDj;
    private boolean autoDjFading;
    private boolean autoDjNextPrepared;
    private int autoDjIndex;
    private int crossfadeMs = 6000;
    private boolean micEnabled = true;
    private BroadcastReceiver statusReceiver;
    private Runnable heartbeat;
    private String currentTab = "STUDIO";

    static final class Track {
        final String uri, title;
        Track(String uri, String title) { this.uri = uri; this.title = title; }
        JSONObject json() { JSONObject o = new JSONObject(); try { o.put("uri", uri); o.put("title", title); } catch(Exception ignored){} return o; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        activationClient = new ActivationClient(this);
        loadLibrary(); loadTargets(); loadPrefs();
        registerStatusReceiver();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 500);
        showLogin();
    }

    @Override protected void onDestroy() {
        stopDecks();
        if (statusReceiver != null) try { unregisterReceiver(statusReceiver); } catch(Exception ignored){}
        ui.removeCallbacksAndMessages(null); net.shutdownNow();
        super.onDestroy();
    }

    private void showLogin() {
        serviceOnAir = serviceConnecting = false;
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(BG);
        LinearLayout outer = column(); outer.setGravity(Gravity.CENTER); outer.setPadding(dp(22), dp(48), dp(22), dp(48)); sc.addView(outer, match());
        LinearLayout card = column(); card.setPadding(dp(24),dp(24),dp(24),dp(24)); card.setBackground(panelBg(GREEN, 22));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Math.min(dp(480), getResources().getDisplayMetrics().widthPixels-dp(40)), -2); outer.addView(card, cp);
        TextView logo = text("◉  HABS BROADCASTER", 22, GREEN, true); card.addView(logo);
        TextView sub = text("ANDROID DJ + RADIO STUDIO", 10, MUTED, true); sub.setLetterSpacing(.18f); card.addView(sub, mt(4));
        View rule = new View(this); rule.setBackgroundColor(LINE); card.addView(rule, new LinearLayout.LayoutParams(-1,dp(1)){{topMargin=dp(22);bottomMargin=dp(22);}});
        card.addView(text("Sign in to continue", 25, Color.WHITE, true));
        EditText user = input("Username", false); card.addView(user, mt(20));
        EditText pass = input("Password", true); card.addView(pass, mt(12));
        loginError = text("", 12, Color.rgb(255,160,170), false); loginError.setVisibility(View.GONE); loginError.setPadding(dp(12),dp(10),dp(12),dp(10)); loginError.setBackground(panelBg(RED,12)); card.addView(loginError, mt(12));
        Button sign = button("SIGN IN & ACTIVATE", GREEN, Color.rgb(4,15,6)); card.addView(sign, new LinearLayout.LayoutParams(-1,dp(52)){{topMargin=dp(16);}});
        sign.setOnClickListener(v -> {
            String u=user.getText().toString().trim(), p=pass.getText().toString();
            if(u.isEmpty()||p.isEmpty()){ loginFail("Enter your username and password."); return; }
            sign.setEnabled(false); sign.setText("SIGNING IN…"); loginError.setVisibility(View.GONE);
            net.execute(() -> {
                try { ActivationClient.Session s=activationClient.login(u,p); ui.post(() -> { session=s; showStudioShell(); startHeartbeat(); }); }
                catch(Exception e){ ui.post(() -> { sign.setEnabled(true); sign.setText("SIGN IN & ACTIVATE"); loginFail(e.getMessage()); }); }
            });
        });
        setContentView(sc);
    }

    private void loginFail(String s){ loginError.setText(s==null?"Login failed":s); loginError.setVisibility(View.VISIBLE); }

    private void showStudioShell() {
        root = column(); root.setBackgroundColor(BG);
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(14),dp(10),dp(14),dp(10)); header.setBackgroundColor(Color.rgb(3,13,8));
        LinearLayout brand=column(); brand.addView(text("HABS BROADCASTER",18,GREEN,true)); brand.addView(text("MOBILE DJ STUDIO",9,MUTED,true));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        licensedUser=text(session.displayName==null?session.username:session.displayName,11,Color.WHITE,true); header.addView(licensedUser);
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));

        tabBar=row(); tabBar.setPadding(dp(8),dp(7),dp(8),dp(7)); tabBar.setBackgroundColor(Color.rgb(4,14,9));
        for(String t:new String[]{"STUDIO","LIBRARY","STREAMS","SETTINGS"}){ Button b=tabButton(t); b.setOnClickListener(v->showTab(t)); tabBar.addView(b,new LinearLayout.LayoutParams(0,dp(42),1)); }
        root.addView(tabBar);

        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); content=column(); content.setPadding(dp(10),dp(10),dp(10),dp(100)); sc.addView(content,match()); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bottom=row(); bottom.setGravity(Gravity.CENTER_VERTICAL); bottom.setPadding(dp(10),dp(8),dp(10),dp(8)); bottom.setBackgroundColor(Color.rgb(3,13,8));
        LinearLayout stat=column(); statusText=text("OFF AIR",15,RED,true); statusSub=text("Ready",10,MUTED,false); stat.addView(statusText);stat.addView(statusSub); bottom.addView(stat,new LinearLayout.LayoutParams(0,-2,1));
        broadcastButton=button("● START BROADCAST",GREEN,Color.rgb(3,18,7)); bottom.addView(broadcastButton,new LinearLayout.LayoutParams(dp(170),dp(48)));
        broadcastButton.setOnClickListener(v->{ if(serviceOnAir||serviceConnecting) stopBroadcast(); else prepareBroadcast(); });
        root.addView(bottom);
        setContentView(root); showTab("STUDIO");
    }

    private void showTab(String name) {
        currentTab=name; content.removeAllViews(); updateTabs();
        if("STUDIO".equals(name)) renderStudio();
        else if("LIBRARY".equals(name)) renderLibrary();
        else if("STREAMS".equals(name)) renderStreams();
        else renderSettings();
    }

    private void updateTabs(){ for(int i=0;i<tabBar.getChildCount();i++){Button b=(Button)tabBar.getChildAt(i); boolean on=b.getText().toString().equals(currentTab); b.setTextColor(on?GREEN:MUTED); b.setBackground(on?panelBg(GREEN,10):transparentBg());} }

    private void renderStudio() {
        TextView title=text("DJ WORKSPACE",20,Color.WHITE,true); content.addView(title);
        TextView hint=text("Deck A/B • AutoDJ • crossfade • Android playback capture",10,MUTED,false); content.addView(hint,mt(2));
        LinearLayout decks = getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?row():column();
        deckA=new Deck("A",GREEN); deckB=new Deck("B",CYAN);
        decks.addView(deckA.view,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?0:-1,-2,getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?1:0); bp.leftMargin=dp(8); if(decks.getOrientation()==LinearLayout.VERTICAL){bp.leftMargin=0;bp.topMargin=dp(8);} decks.addView(deckB.view,bp); content.addView(decks,mt(12));
        content.addView(section("MIXER / CROSSFADER"),mt(10));
        LinearLayout mix=column(); mix.setPadding(dp(14),dp(14),dp(14),dp(14));mix.setBackground(panelBg(GREEN,16));
        LinearLayout labs=row(); TextView la=text("DECK A",11,GREEN,true), lb=text("DECK B",11,CYAN,true); labs.addView(la,new LinearLayout.LayoutParams(0,-2,1)); lb.setGravity(Gravity.END);labs.addView(lb,new LinearLayout.LayoutParams(0,-2,1));mix.addView(labs);
        crossfader=new SeekBar(this);crossfader.setMax(1000);crossfader.setProgress(500);crossfader.getProgressDrawable().setTint(GREEN);crossfader.getThumb().setTint(GREEN);mix.addView(crossfader,mt(8));crossfader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){applyCrossfader(p/1000f);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout auto=row(); Button ad=button(autoDj?"■ STOP AUTODJ":"▶ START AUTODJ",autoDj?RED:GREEN,Color.rgb(3,15,7)); ad.setOnClickListener(v->{if(autoDj)stopAutoDj();else startAutoDj();renderStudioAgain();});auto.addView(ad,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView fade=text("Crossfade: "+(crossfadeMs/1000)+"s",11,Color.WHITE,true);fade.setGravity(Gravity.CENTER);auto.addView(fade,new LinearLayout.LayoutParams(dp(120),dp(48))); mix.addView(auto,mt(10)); content.addView(mix);
        content.addView(section("PROGRAM SIGNAL"),mt(10));
        TextView note=text("Local decks and compatible Android app audio are captured into the program bus after Android grants Device Audio permission. Microphone can be mixed in separately.",11,MUTED,false);note.setPadding(dp(12),dp(12),dp(12),dp(12));note.setBackground(panelBg(CYAN,12));content.addView(note);
        if(autoDj) ui.post(autoDjTick);
    }

    private void renderStudioAgain(){ if("STUDIO".equals(currentTab))showTab("STUDIO"); }

    private void renderLibrary() {
        LinearLayout top=row(); top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("LOCAL MUSIC LIBRARY",20,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));Button add=button("+ ADD MUSIC",GREEN,Color.rgb(3,15,7));top.addView(add,new LinearLayout.LayoutParams(dp(130),dp(44)));add.setOnClickListener(v->pickAudio());content.addView(top);
        TextView h=text("Select one or many audio files from your Android device. HABS keeps permission to those files so they remain available after restart.",10,MUTED,false);content.addView(h,mt(4));
        if(library.isEmpty()){TextView e=text("No tracks yet. Tap + ADD MUSIC.",13,MUTED,false);e.setGravity(Gravity.CENTER);e.setPadding(0,dp(60),0,dp(60));content.addView(e);return;}
        for(int i=0;i<library.size();i++){final int idx=i;Track t=library.get(i);LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(10),dp(8),dp(10));r.setBackground(panelBg(i%2==0?GREEN:CYAN,12));TextView n=text("♫  "+t.title,12,Color.WHITE,true);r.addView(n,new LinearLayout.LayoutParams(0,-2,1));Button a=mini("A");Button b=mini("B");Button q=mini("×");a.setOnClickListener(v->{ensureDecks();deckA.load(t,true,null);showTab("STUDIO");});b.setOnClickListener(v->{ensureDecks();deckB.load(t,true,null);showTab("STUDIO");});q.setOnClickListener(v->{library.remove(idx);saveLibrary();showTab("LIBRARY");});r.addView(a);r.addView(b);r.addView(q);content.addView(r,mt(7));}
    }

    private void renderStreams() {
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("STREAM OUTPUTS",20,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));Button add=button("+ OUTPUT",GREEN,Color.rgb(3,15,7));add.setEnabled(targets.size()<6);add.setOnClickListener(v->{targets.add(new StreamTarget());showTab("STREAMS");});top.addView(add,new LinearLayout.LayoutParams(dp(110),dp(44)));content.addView(top);
        content.addView(text("Broadcast the same HABS program mix to several Icecast/AzuraCast/Zeno or SHOUTcast-compatible destinations at once.",10,MUTED,false),mt(4));
        streamEditors.clear();
        for(int i=0;i<targets.size();i++){StreamEditor ed=new StreamEditor(i,targets.get(i));streamEditors.add(ed);content.addView(ed.card,mt(10));}
        Button save=button("SAVE STREAM SETTINGS",GREEN,Color.rgb(3,15,7));save.setOnClickListener(v->saveEditors());content.addView(save,new LinearLayout.LayoutParams(-1,dp(52)){{topMargin=dp(12);}});
    }

    private void renderSettings() {
        content.addView(text("SETTINGS",20,Color.WHITE,true));
        LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(panelBg(GREEN,16));
        CheckBox mic=new CheckBox(this);mic.setText("Mix microphone into broadcast");mic.setTextColor(Color.WHITE);mic.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));mic.setChecked(micEnabled);mic.setOnCheckedChangeListener((b,c)->{micEnabled=c;savePrefs();});card.addView(mic);
        TextView f=text("AutoDJ crossfade duration",11,MUTED,true);card.addView(f,mt(14));SeekBar s=new SeekBar(this);s.setMax(18);s.setProgress(Math.max(0,crossfadeMs/1000-2));s.getProgressDrawable().setTint(GREEN);s.getThumb().setTint(GREEN);TextView val=text((crossfadeMs/1000)+" seconds",12,GREEN,true);card.addView(s);card.addView(val);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int p,boolean from){crossfadeMs=(p+2)*1000;val.setText((p+2)+" seconds");savePrefs();}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}});
        TextView android=text("Android device-audio capture uses the system MediaProjection permission. Android will show its own consent screen when you start a broadcast. Some third-party apps can explicitly block playback capture.",10,MUTED,false);android.setPadding(0,dp(16),0,0);card.addView(android);content.addView(card,mt(10));
        Button logout=button("SIGN OUT",RED,Color.rgb(24,7,10));logout.setOnClickListener(v->{stopBroadcast();ActivationClient.Session old=session;session=null;net.execute(()->activationClient.logout(old));showLogin();});content.addView(logout,new LinearLayout.LayoutParams(-1,dp(50)){{topMargin=dp(12);}});
    }

    private void pickAudio(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,REQ_AUDIO);}

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==REQ_AUDIO&&result==RESULT_OK&&data!=null){if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)addTrack(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)addTrack(data.getData());saveLibrary();if("LIBRARY".equals(currentTab))showTab("LIBRARY");}else if(request==REQ_PROJECTION){if(result==RESULT_OK&&data!=null)startServiceWithProjection(result,data);else toast("Android device-audio capture was not granted");}}

    private void addTrack(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String title=u.getLastPathSegment();try(android.database.Cursor c=getContentResolver().query(u,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())title=c.getString(0);}catch(Exception ignored){}for(Track t:library)if(t.uri.equals(u.toString()))return;library.add(new Track(u.toString(),title==null?"Audio track":title));}

    private void prepareBroadcast(){saveEditors();if(targets.stream().noneMatch(t->t.enabled&&!t.host.trim().isEmpty())){toast("Add and enable at least one streaming output first");showTab("STREAMS");return;}if(micEnabled&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}requestProjection();}
    private void requestProjection(){MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),REQ_PROJECTION);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_MIC){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)requestProjection();else{micEnabled=false;savePrefs();toast("Microphone denied. Starting without microphone.");requestProjection();}}}

    private void startServiceWithProjection(int result,Intent projectionData){Intent s=new Intent(this,BroadcastService.class);s.setAction(BroadcastService.ACTION_START);s.putExtra(BroadcastService.EXTRA_PROJECTION_CODE,result);s.putExtra(BroadcastService.EXTRA_PROJECTION_DATA,projectionData);s.putExtra(BroadcastService.EXTRA_TARGETS_JSON,targetsJson(true));s.putExtra(BroadcastService.EXTRA_MIC_ENABLED,micEnabled);s.putExtra(BroadcastService.EXTRA_MIC_GAIN,.65f);startForegroundService(s);setStatus("CONNECTING","Starting Android audio engine…");}
    private void stopBroadcast(){Intent s=new Intent(this,BroadcastService.class);s.setAction(BroadcastService.ACTION_STOP);startService(s);}

    private void registerStatusReceiver(){statusReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){setStatus(i.getStringExtra("state"),i.getStringExtra("message"));}};IntentFilter f=new IntentFilter(BroadcastService.ACTION_STATUS);if(Build.VERSION.SDK_INT>=33)registerReceiver(statusReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(statusReceiver,f);}
    private void setStatus(String state,String message){serviceOnAir="ON_AIR".equals(state);serviceConnecting="CONNECTING".equals(state);if(statusText==null)return;if(serviceOnAir){statusText.setText("ON AIR");statusText.setTextColor(GREEN);AlphaAnimation pulse=new AlphaAnimation(.45f,1f);pulse.setDuration(700);pulse.setRepeatMode(AlphaAnimation.REVERSE);pulse.setRepeatCount(AlphaAnimation.INFINITE);statusText.startAnimation(pulse);broadcastButton.setText("■ STOP BROADCAST");broadcastButton.setBackground(panelBg(RED,12));broadcastButton.setTextColor(Color.WHITE);}else if(serviceConnecting){statusText.clearAnimation();statusText.setText("CONNECTING");statusText.setTextColor(Color.rgb(255,199,75));broadcastButton.setText("■ CANCEL");broadcastButton.setBackground(panelBg(RED,12));}else{statusText.clearAnimation();statusText.setText("OFF AIR");statusText.setTextColor(RED);broadcastButton.setText("● START BROADCAST");broadcastButton.setBackground(panelBg(GREEN,12));broadcastButton.setTextColor(Color.rgb(3,15,7));}statusSub.setText(message==null?"Ready":message);}

    private void startHeartbeat(){if(heartbeat!=null)ui.removeCallbacks(heartbeat);heartbeat=new Runnable(){public void run(){ActivationClient.Session s=session;if(s==null)return;net.execute(()->{try{boolean ok=activationClient.validate(s);if(!ok)ui.post(()->activationLost("Your HABS account is no longer active"));else ui.postDelayed(heartbeat,60000);}catch(Exception e){ui.postDelayed(heartbeat,60000);}});}};ui.postDelayed(heartbeat,60000);}
    private void activationLost(String m){stopBroadcast();session=null;toast(m);showLogin();}

    private void startAutoDj(){if(library.isEmpty()){toast("Add local music to the library first");return;}ensureDecks();autoDj=true;autoDjIndex=0;autoDjFading=false;autoDjNextPrepared=false;activeDeck=deckA;crossfader.setProgress(0);deckA.load(library.get(0),true,null);ui.post(autoDjTick);}
    private void stopAutoDj(){autoDj=false;autoDjFading=false;autoDjNextPrepared=false;ui.removeCallbacks(autoDjTick);}
    private final Runnable autoDjTick=new Runnable(){public void run(){if(!autoDj||activeDeck==null)return;try{if(activeDeck.ready&&activeDeck.player!=null&&activeDeck.player.isPlaying()){int remain=activeDeck.duration()-activeDeck.position();Deck next=activeDeck==deckA?deckB:deckA;if(!autoDjNextPrepared&&remain<=crossfadeMs+7000){int nextIdx=(autoDjIndex+1)%library.size();next.load(library.get(nextIdx),false,()->autoDjNextPrepared=true);}if(!autoDjFading&&autoDjNextPrepared&&remain<=crossfadeMs){autoDjFading=true;next.play();animateFade(activeDeck,next);}}}catch(Exception ignored){}ui.postDelayed(this,250);}};
    private void animateFade(Deck old,Deck next){final long start=SystemClock.elapsedRealtime();final boolean toB=next==deckB;Runnable r=new Runnable(){public void run(){if(!autoDj)return;float x=Math.min(1f,(SystemClock.elapsedRealtime()-start)/(float)crossfadeMs);int p=toB?(int)(x*1000):(int)((1-x)*1000);crossfader.setProgress(p);if(x<1f)ui.postDelayed(this,50);else{old.stop();activeDeck=next;autoDjIndex=(autoDjIndex+1)%library.size();autoDjFading=false;autoDjNextPrepared=false;}}};ui.post(r);}
    private void applyCrossfader(float x){if(deckA==null||deckB==null)return;double a=Math.cos(x*Math.PI/2),b=Math.sin(x*Math.PI/2);deckA.setVolume((float)a);deckB.setVolume((float)b);}
    private void ensureDecks(){if(deckA==null||deckB==null){deckA=new Deck("A",GREEN);deckB=new Deck("B",CYAN);}}
    private void stopDecks(){if(deckA!=null)deckA.release();if(deckB!=null)deckB.release();}

    final class Deck {
        final String id; final int accent; final LinearLayout view; final TextView title,time; final Button play,stop; final SeekBar seek;
        MediaPlayer player; Track track; boolean ready; float volume=1f; Runnable preparedCallback;
        Deck(String id,int accent){this.id=id;this.accent=accent;view=column();view.setPadding(dp(12),dp(12),dp(12),dp(12));view.setBackground(panelBg(accent,18));LinearLayout h=row();TextView badge=text("DECK "+id,13,accent,true);h.addView(badge,new LinearLayout.LayoutParams(0,-2,1));time=text("00:00 / 00:00",10,MUTED,true);h.addView(time);view.addView(h);title=text("DROP / LOAD A TRACK",16,Color.WHITE,true);title.setSingleLine(true);view.addView(title,mt(10));seek=new SeekBar(MainActivity.this);seek.setMax(1000);seek.getProgressDrawable().setTint(accent);seek.getThumb().setTint(accent);view.addView(seek,mt(8));LinearLayout controls=row();play=button("▶ PLAY",accent,Color.rgb(3,14,7));stop=button("■ STOP",RED,Color.rgb(24,7,10));controls.addView(play,new LinearLayout.LayoutParams(0,dp(46),1));controls.addView(stop,new LinearLayout.LayoutParams(0,dp(46),1){{leftMargin=dp(7);}});view.addView(controls,mt(8));play.setOnClickListener(v->{if(player==null){toast("Load a track from Library first");return;}if(player.isPlaying())pause();else play();});stop.setOnClickListener(v->stop());seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean from){if(from&&ready&&player!=null)player.seekTo((int)(duration()*p/1000f));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});}
        void load(Track t,boolean autoPlay,Runnable cb){release();track=t;title.setText(t.title);time.setText("Loading…");preparedCallback=cb;try{player=new MediaPlayer();player.setDataSource(MainActivity.this,Uri.parse(t.uri));player.setOnPreparedListener(mp->{ready=true;time.setText(fmt(0)+" / "+fmt(duration()));setVolume(volume);if(preparedCallback!=null)preparedCallback.run();if(autoPlay)play();});player.setOnCompletionListener(mp->{if(!autoDj)stop();});player.setOnErrorListener((mp,w,e)->{toast("Deck "+id+" could not play this track");return true;});player.prepareAsync();}catch(Exception e){toast("Deck "+id+": "+e.getMessage());}}
        void play(){if(ready&&player!=null){player.start();play.setText("Ⅱ PAUSE");ui.post(progressTick);}}
        void pause(){if(player!=null&&player.isPlaying())player.pause();play.setText("▶ PLAY");}
        void stop(){if(player!=null){try{player.pause();player.seekTo(0);}catch(Exception ignored){}}play.setText("▶ PLAY");seek.setProgress(0);}
        void setVolume(float v){volume=v;if(player!=null)try{player.setVolume(v,v);}catch(Exception ignored){}}
        int duration(){return player==null?0:player.getDuration();}int position(){return player==null?0:player.getCurrentPosition();}
        final Runnable progressTick=new Runnable(){public void run(){if(player==null||!ready)return;try{int d=duration(),p=position();seek.setProgress(d<=0?0:(int)(p*1000L/d));time.setText(fmt(p)+" / "+fmt(d));if(player.isPlaying())ui.postDelayed(this,250);else play.setText("▶ PLAY");}catch(Exception ignored){}}};
        void release(){ready=false;ui.removeCallbacks(progressTick);if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}}player=null;}
    }

    final class StreamEditor {
        final int index;final LinearLayout card;final EditText name,host,port,mount,user,pass;final Spinner type,bitrate;final CheckBox enabled,tls;
        StreamEditor(int index,StreamTarget t){this.index=index;card=column();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(panelBg(index%2==0?GREEN:CYAN,16));LinearLayout h=row();h.setGravity(Gravity.CENTER_VERTICAL);h.addView(text("OUTPUT "+(index+1),13,index%2==0?GREEN:CYAN,true),new LinearLayout.LayoutParams(0,-2,1));Button del=mini("DELETE");del.setOnClickListener(v->{targets.remove(index);showTab("STREAMS");});h.addView(del);card.addView(h);enabled=new CheckBox(MainActivity.this);enabled.setText("Enabled");enabled.setTextColor(Color.WHITE);enabled.setChecked(t.enabled);card.addView(enabled);name=input("Station name",false);name.setText(t.name);card.addView(name);type=new Spinner(MainActivity.this);String[] types={"Zeno.fm / Icecast","Icecast / AzuraCast","Icecast TLS","SHOUTcast compatible"};type.setAdapter(spinnerAdapter(types));int pos=0;for(int i=0;i<types.length;i++)if(types[i].equals(t.type))pos=i;type.setSelection(pos);card.addView(type,mt(7));host=input("Server host",false);host.setText(t.host);port=input("Port",false);port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);port.setText(String.valueOf(t.port));mount=input("Mount / SID",false);mount.setText(t.mount);user=input("Username",false);user.setText(t.username);pass=input("Password (not saved)",true);pass.setText(t.password);for(EditText e:new EditText[]{host,port,mount,user,pass})card.addView(e,mt(7));tls=new CheckBox(MainActivity.this);tls.setText("TLS / SSL");tls.setTextColor(Color.WHITE);tls.setChecked(t.tls);card.addView(tls);bitrate=new Spinner(MainActivity.this);String[] br={"64","96","128","160","192","256","320"};bitrate.setAdapter(spinnerAdapter(br));int bp=2;for(int i=0;i<br.length;i++)if(Integer.parseInt(br[i])==t.bitrate)bp=i;bitrate.setSelection(bp);card.addView(bitrate,mt(7));}
        void apply(){StreamTarget t=targets.get(index);t.enabled=enabled.isChecked();t.name=name.getText().toString().trim();t.type=(String)type.getSelectedItem();t.host=host.getText().toString().trim();try{t.port=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){t.port=8000;}t.mount=mount.getText().toString().trim();if(!t.mount.startsWith("/"))t.mount="/"+t.mount;t.username=user.getText().toString().trim();t.password=pass.getText().toString();t.tls=tls.isChecked()||t.type.contains("TLS");t.bitrate=Integer.parseInt((String)bitrate.getSelectedItem());}
    }

    private void saveEditors(){for(StreamEditor e:new ArrayList<>(streamEditors))try{e.apply();}catch(Exception ignored){}saveTargets();toast("Stream settings saved");}
    private String targetsJson(boolean includePasswords){JSONArray a=new JSONArray();for(StreamTarget t:targets)try{JSONObject o=t.toJson();if(!includePasswords)o.put("password","");a.put(o);}catch(Exception ignored){}return a.toString();}
    private void loadTargets(){targets.clear();String s=getSharedPreferences("habs",MODE_PRIVATE).getString("targets","[]");try{JSONArray a=new JSONArray(s);for(int i=0;i<a.length();i++)targets.add(StreamTarget.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}if(targets.isEmpty())targets.add(new StreamTarget());}
    private void saveTargets(){getSharedPreferences("habs",MODE_PRIVATE).edit().putString("targets",targetsJson(false)).apply();}
    private void loadLibrary(){library.clear();String s=getSharedPreferences("habs",MODE_PRIVATE).getString("library","[]");try{JSONArray a=new JSONArray(s);for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);library.add(new Track(o.optString("uri"),o.optString("title","Audio track")));}}catch(Exception ignored){}}
    private void saveLibrary(){JSONArray a=new JSONArray();for(Track t:library)a.put(t.json());getSharedPreferences("habs",MODE_PRIVATE).edit().putString("library",a.toString()).apply();}
    private void loadPrefs(){android.content.SharedPreferences p=getSharedPreferences("habs",MODE_PRIVATE);crossfadeMs=p.getInt("crossfade",6000);micEnabled=p.getBoolean("mic",true);}
    private void savePrefs(){getSharedPreferences("habs",MODE_PRIVATE).edit().putInt("crossfade",crossfadeMs).putBoolean("mic",micEnabled).apply();}

    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView section(String s){TextView t=text(s,10,GREEN,true);t.setLetterSpacing(.12f);return t;}
    private EditText input(String hint,boolean pass){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(69,99,77));e.setTextColor(Color.WHITE);e.setTextSize(13);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(panelBg(GREEN,12));if(pass)e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private Button button(String s,int border,int text){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(text);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(panelBg(border,12));b.setPadding(dp(8),0,dp(8),0);return b;}
    private Button mini(String s){Button b=button(s,LINE,Color.WHITE);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(9),0,dp(9),0);b.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(38)){{leftMargin=dp(5);}});return b;}
    private Button tabButton(String s){Button b=button(s,LINE,MUTED);b.setBackground(transparentBg());return b;}
    private GradientDrawable panelBg(int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.argb(170,Color.red(stroke),Color.green(stroke),Color.blue(stroke)));return g;}
    private GradientDrawable transparentBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.TRANSPARENT);g.setCornerRadius(dp(10));return g;}
    private ArrayAdapter<String> spinnerAdapter(String[] v){ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,v){@Override public View getView(int p,View c,android.view.ViewGroup par){TextView t=(TextView)super.getView(p,c,par);t.setTextColor(Color.WHITE);t.setBackgroundColor(PANEL);return t;}@Override public View getDropDownView(int p,View c,android.view.ViewGroup par){TextView t=(TextView)super.getDropDownView(p,c,par);t.setTextColor(Color.WHITE);t.setBackgroundColor(PANEL);return t;}};return a;}
    private LinearLayout.LayoutParams mt(int n){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(n);return p;}private ViewGroup.LayoutParams match(){return new ViewGroup.LayoutParams(-1,-1);}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}private static String fmt(int ms){int s=Math.max(0,ms/1000);return String.format(Locale.US,"%02d:%02d",s/60,s%60);}private void toast(String s){Toast.makeText(this,s==null?"":s,Toast.LENGTH_LONG).show();}
}
