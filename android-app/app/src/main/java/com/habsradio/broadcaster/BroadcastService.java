package com.habsradio.broadcaster;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;

import org.json.JSONArray;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Android foreground broadcast engine.
 * Captures permitted device playback using AudioPlaybackCapture, optionally mixes microphone,
 * encodes one MP3 stream per unique bitrate and fans each encoded stream to its enabled outputs.
 */
public final class BroadcastService extends Service {
    public static final String ACTION_START = "com.habsradio.broadcaster.START";
    public static final String ACTION_STOP = "com.habsradio.broadcaster.STOP";
    public static final String ACTION_STATUS = "com.habsradio.broadcaster.STATUS";
    public static final String EXTRA_PROJECTION_CODE = "projection_code";
    public static final String EXTRA_PROJECTION_DATA = "projection_data";
    public static final String EXTRA_TARGETS_JSON = "targets_json";
    public static final String EXTRA_MIC_ENABLED = "mic_enabled";
    public static final String EXTRA_MIC_GAIN = "mic_gain";

    private static final String CHANNEL_ID = "habs_broadcast";
    private static final int NOTIFICATION_ID = 19081;

    private volatile boolean running;
    private Thread engineThread;
    private Thread micThread;
    private MediaProjection projection;
    private AudioRecord playbackRecord;
    private AudioRecord micRecord;
    private final Object micLock = new Object();
    private short[] latestMic = new short[0];
    private float micGain = 0.65f;
    private final List<OutputWorker> outputs = Collections.synchronizedList(new ArrayList<>());

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "HABS Broadcast", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps the HABS radio broadcast running");
            nm.createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopBroadcast("Broadcast stopped");
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action) || running) return START_NOT_STICKY;

        boolean micEnabled = intent.getBooleanExtra(EXTRA_MIC_ENABLED, true);
        micGain = intent.getFloatExtra(EXTRA_MIC_GAIN, 0.65f);
        if (Build.VERSION.SDK_INT >= 29) {
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (micEnabled && Build.VERSION.SDK_INT >= 30) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NOTIFICATION_ID, notification("Connecting…"), types);
        } else {
            startForeground(NOTIFICATION_ID, notification("Connecting…"));
        }

        int resultCode = intent.getIntExtra(EXTRA_PROJECTION_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
        String targetsJson = intent.getStringExtra(EXTRA_TARGETS_JSON);
        if (resultCode != Activity.RESULT_OK || data == null) {
            status("OFF_AIR", "Android did not grant device-audio capture");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            JSONArray arr = new JSONArray(targetsJson == null ? "[]" : targetsJson);
            List<StreamTarget> targets = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                StreamTarget t = StreamTarget.fromJson(arr.getJSONObject(i));
                if (t.enabled && !t.host.trim().isEmpty()) targets.add(t);
            }
            if (targets.isEmpty()) throw new IllegalArgumentException("No enabled streaming destination is configured");
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(resultCode, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopBroadcast("Android stopped device-audio capture"); }
            }, new Handler(Looper.getMainLooper()));
            startEngine(targets, micEnabled);
        } catch (Exception e) {
            status("OFF_AIR", friendly(e));
            stopBroadcast(friendly(e));
        }
        return START_NOT_STICKY;
    }

    private void startEngine(List<StreamTarget> targets, boolean micEnabled) throws Exception {
        int sampleRate = chooseSampleRate();
        int min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = sampleRate / 5 * 4;
        int bufferBytes = Math.max(min * 2, 16384);

        AudioPlaybackCaptureConfiguration captureConfig = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        AudioFormat playbackFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
        playbackRecord = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setAudioFormat(playbackFormat)
                .setBufferSizeInBytes(bufferBytes)
                .build();
        if (playbackRecord.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Device audio capture could not initialize");

        if (micEnabled) startMicrophone(sampleRate);

        outputs.clear();
        for (StreamTarget t : targets) {
            OutputWorker w = new OutputWorker(t);
            outputs.add(w);
            w.start();
        }

        final Map<Integer, Mp3Encoder> encoders = new HashMap<>();
        for (StreamTarget t : targets) encoders.computeIfAbsent(t.bitrate, b -> new Mp3Encoder(sampleRate, 2, b));

        running = true;
        playbackRecord.startRecording();
        status("CONNECTING", "Capturing Android audio and connecting outputs…");
        engineThread = new Thread(() -> {
            short[] pcm = new short[4096]; // stereo, 2048 frames
            long lastMeter = 0;
            try {
                while (running) {
                    int n = playbackRecord.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                    if (n <= 0) continue;
                    int frames = n / 2;
                    mixMic(pcm, frames);
                    float peak = peak(pcm, n);
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastMeter > 350) {
                        lastMeter = now;
                        status(anyConnected() ? "ON_AIR" : "CONNECTING", "Program level " + dbText(peak));
                    }
                    for (Map.Entry<Integer, Mp3Encoder> entry : encoders.entrySet()) {
                        byte[] mp3 = entry.getValue().encode(pcm, frames);
                        if (mp3.length == 0) continue;
                        synchronized (outputs) {
                            for (OutputWorker w : outputs) if (w.target.bitrate == entry.getKey()) w.offer(mp3);
                        }
                    }
                }
            } catch (Throwable e) {
                if (running) status("ERROR", friendly(e));
            } finally {
                for (Mp3Encoder e : encoders.values()) {
                    try {
                        byte[] tail = e.flush();
                        if (tail.length > 0) synchronized (outputs) { for (OutputWorker w : outputs) w.offer(tail); }
                    } catch (Exception ignored) { }
                    try { e.close(); } catch (Exception ignored) { }
                }
                stopBroadcast("Broadcast stopped");
            }
        }, "HABS-AudioEngine");
        engineThread.start();
    }

    private int chooseSampleRate() {
        int[] rates = {44100, 48000};
        for (int r : rates) {
            int n = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (n > 0) return r;
        }
        return 48000;
    }

    private void startMicrophone(int sampleRate) {
        try {
            int min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return;
            micRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 8192));
            if (micRecord.getState() != AudioRecord.STATE_INITIALIZED) { micRecord.release(); micRecord = null; return; }
            micRecord.startRecording();
            micThread = new Thread(() -> {
                short[] buf = new short[2048];
                while (running || (engineThread == null && micRecord != null)) {
                    int n = micRecord.read(buf, 0, buf.length, AudioRecord.READ_BLOCKING);
                    if (n > 0) {
                        short[] copy = Arrays.copyOf(buf, n);
                        synchronized (micLock) { latestMic = copy; }
                    }
                    if (!running && engineThread != null) break;
                }
            }, "HABS-Microphone");
            micThread.start();
        } catch (Throwable ignored) { micRecord = null; }
    }

    private void mixMic(short[] stereo, int frames) {
        short[] mic;
        synchronized (micLock) { mic = latestMic; }
        if (mic == null || mic.length == 0 || micGain <= 0.001f) return;
        int count = Math.min(frames, mic.length);
        int micStart = Math.max(0, mic.length - count);
        for (int i = 0; i < count; i++) {
            int m = (int) (mic[micStart + i] * micGain);
            int p = i * 2;
            stereo[p] = clip(stereo[p] + m);
            stereo[p + 1] = clip(stereo[p + 1] + m);
        }
    }

    private static short clip(int v) { return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v)); }
    private static float peak(short[] b, int n) {
        int p = 0; for (int i = 0; i < n; i++) p = Math.max(p, Math.abs((int)b[i]));
        return p / 32768f;
    }
    private static String dbText(float x) { return x <= 0.00001f ? "-∞ dBFS" : String.format(Locale.US, "%.1f dBFS", 20.0 * Math.log10(x)); }

    private boolean anyConnected() {
        synchronized (outputs) { for (OutputWorker w : outputs) if (w.connected) return true; }
        return false;
    }

    private synchronized void stopBroadcast(String reason) {
        if (!running && engineThread == null && playbackRecord == null && projection == null) return;
        running = false;
        try { if (playbackRecord != null) playbackRecord.stop(); } catch (Exception ignored) { }
        try { if (playbackRecord != null) playbackRecord.release(); } catch (Exception ignored) { }
        playbackRecord = null;
        try { if (micRecord != null) micRecord.stop(); } catch (Exception ignored) { }
        try { if (micRecord != null) micRecord.release(); } catch (Exception ignored) { }
        micRecord = null;
        synchronized (outputs) { for (OutputWorker w : outputs) w.shutdown(); outputs.clear(); }
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        projection = null;
        engineThread = null;
        status("OFF_AIR", reason == null ? "Broadcast stopped" : reason);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() { stopBroadcast("Broadcast service closed"); super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }

    private Notification notification(String text) {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.habsradio.broadcaster.R.drawable.ic_habs)
                .setContentTitle("HABS Broadcaster")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void status(String state, String message) {
        Intent i = new Intent(ACTION_STATUS).setPackage(getPackageName());
        i.putExtra("state", state); i.putExtra("message", message);
        sendBroadcast(i);
        try {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, notification(state.replace('_',' ') + " • " + message));
        } catch (Exception ignored) { }
    }

    private static String friendly(Throwable e) {
        if (e == null) return "Unknown broadcast error";
        String s = e.getMessage(); return s == null || s.trim().isEmpty() ? e.getClass().getSimpleName() : s;
    }

    private final class OutputWorker extends Thread {
        final StreamTarget target;
        final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(48);
        volatile boolean alive = true;
        volatile boolean connected;
        IcecastSink sink;
        long retryAt;
        int failures;

        OutputWorker(StreamTarget target) { super("HABS-Output-" + target.name); this.target = target; setDaemon(true); }
        void offer(byte[] chunk) {
            if (!alive) return;
            if (!queue.offer(chunk)) { queue.poll(); queue.offer(chunk); } // keep live audio, discard oldest backlog
        }
        @Override public void run() {
            while (alive) {
                try {
                    if (sink == null || !sink.isConnected()) {
                        connected = false;
                        long wait = retryAt - SystemClock.elapsedRealtime();
                        if (wait > 0) Thread.sleep(Math.min(wait, 1000));
                        if (!alive) break;
                        sink = new IcecastSink(target);
                        sink.connect();
                        connected = true; failures = 0; retryAt = 0;
                        status("ON_AIR", target.name + " connected");
                    }
                    byte[] data = queue.poll(2, TimeUnit.SECONDS);
                    if (data != null) sink.write(data);
                } catch (Throwable e) {
                    connected = false;
                    try { if (sink != null) sink.close(); } catch (Exception ignored) { }
                    sink = null;
                    failures++;
                    long delay = Math.min(30000, 2000L << Math.min(4, failures - 1));
                    retryAt = SystemClock.elapsedRealtime() + delay;
                    status(anyConnected() ? "ON_AIR" : "CONNECTING", target.name + ": " + friendly(e) + " • retrying");
                }
            }
            connected = false;
            try { if (sink != null) sink.close(); } catch (Exception ignored) { }
        }
        void shutdown() { alive = false; interrupt(); try { if (sink != null) sink.close(); } catch (Exception ignored) {} }
    }
}
