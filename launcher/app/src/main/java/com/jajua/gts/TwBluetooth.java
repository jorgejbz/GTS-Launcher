package com.jajua.gts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Song info from the TS10's own Bluetooth app (com.tw.bt). That app publishes no Android media
 * session; instead it answers a voice-assistant protocol found in its code (com.tw.bt
 * TW_THEME_Dev.20240827):
 *   query  com.unisound.intent.action.GET_ID3_INFO
 *   reply  com.unisound.intent.action.SEND_ID3_INFO  extras musicTitle, musicArtist (String),
 *          musicProgress, musicDuration (int, ms)
 * It keeps answering with the last song even after playback stopped (seen on the TS10: the same
 * progress in two reports minutes apart), so a song only counts as playing while its progress
 * keeps moving between queries. Read-only: nothing here touches the Bluetooth connection.
 */
final class TwBluetooth {
    interface Listener {
        void onBluetoothSongChanged(TwBluetooth b);
    }

    static final String PACKAGE = "com.tw.bt";
    private static final String ACTION_QUERY = "com.unisound.intent.action.GET_ID3_INFO";
    private static final String ACTION_ID3 = "com.unisound.intent.action.SEND_ID3_INFO";
    /** Re-query interval while the home screen is visible: one tiny broadcast. */
    private static final long POLL_MS = 1000;
    /** Without progress for this long, the song is treated as not playing. */
    private static final long LIVE_MS = 3000;
    /** After a play/pause command the expected state is kept this long while the phone catches up. */
    private static final long ASSUME_MS = 3500;

    String title;
    String artist;
    /** elapsedRealtime of the last time the reported song changed (0 = never seen change). */
    long changedAt;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;
    private int lastProgress = -1;
    private long lastAdvance;
    private boolean wasLive;
    private long assumeUntil;
    private long lastReplyAt;
    private boolean assumed;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            query();
            // Also notices the moment a song stops advancing.
            checkLive();
            main.postDelayed(this, POLL_MS);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String t = clean(intent.getStringExtra("musicTitle"));
            String a = clean(intent.getStringExtra("musicArtist"));
            int progress = intent.getIntExtra("musicProgress", -1);
            boolean sameSong = t != null && t.equals(title);
            long now = SystemClock.elapsedRealtime();
            if (now < assumeUntil) {
                // Right after a play/pause command the phone still reports a position or two from
                // before it; trusting them made a pause flip back to "playing" for a few seconds.
            } else if (t != null && ((sameSong && lastProgress >= 0 && progress != lastProgress) || (!sameSong && title != null))) {
                // Progress moved, or the track changed while we were watching.
                lastAdvance = now;
            } else if (sameSong && progress == lastProgress && now - lastReplyAt >= 800) {
                // Two replies at least a second apart with the same position: paused. Reacts in ~2 s
                // instead of waiting for LIVE_MS to run out.
                lastAdvance = 0;
            }
            lastReplyAt = now;
            lastProgress = progress;
            boolean changed = t == null ? title != null : !t.equals(title);
            if (changed && title != null) changedAt = now;
            title = t;
            artist = a;
            if (changed) {
                MediaLog.add(context, "bt: canción «" + t + "» · " + a);
                listener.onBluetoothSongChanged(TwBluetooth.this);
            }
            checkLive();
        }
    };

    TwBluetooth(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getApplicationInfo(PACKAGE, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    void start() {
        if (running || !installed(context)) return;
        running = true;
        lastProgress = -1;
        lastAdvance = 0;
        wasLive = false;
        assumeUntil = 0;
        context.registerReceiver(receiver, new IntentFilter(ACTION_ID3), Context.RECEIVER_EXPORTED);
        main.post(poll);
    }

    void stop() {
        if (!running) return;
        running = false;
        main.removeCallbacks(poll);
        context.unregisterReceiver(receiver);
    }

    /** A song whose progress moved recently, i.e. really playing now. */
    boolean isLive() {
        long now = SystemClock.elapsedRealtime();
        if (now < assumeUntil) return title != null && assumed;
        return title != null && lastAdvance > 0 && now - lastAdvance < LIVE_MS;
    }

    /**
     * Right after a play/pause command: show the expected state at once and keep it while the phone
     * catches up, instead of flipping back on a late progress report.
     */
    void assume(boolean playing) {
        long now = SystemClock.elapsedRealtime();
        MediaLog.add(context, "bt: orden " + (playing ? "reproducir" : "pausar"));
        assumed = playing;
        assumeUntil = now + ASSUME_MS;
        // When the grace period ends, a paused song must not look live from older progress.
        lastAdvance = playing ? now + ASSUME_MS : 0;
        wasLive = playing;
        main.postDelayed(this::checkLive, ASSUME_MS + 50);
    }

    /** Played within the last {@code ms}, so a pause keeps the song (and its play button) on screen. */
    boolean recentlyLive(long ms) {
        return title != null && lastLiveAt > 0 && SystemClock.elapsedRealtime() - lastLiveAt < ms;
    }

    private long lastLiveAt;

    /** Play state reported directly by the head unit (XTService); trusted like a command. */
    void hint(boolean playing) {
        long now = SystemClock.elapsedRealtime();
        assumed = playing;
        assumeUntil = now + ASSUME_MS;
        lastAdvance = playing ? now + ASSUME_MS : 0;
        wasLive = playing;
        if (playing) lastLiveAt = now;
        main.postDelayed(this::checkLive, ASSUME_MS + 50);
    }

    private void checkLive() {
        boolean live = isLive();
        if (live) lastLiveAt = SystemClock.elapsedRealtime();
        if (live != wasLive) {
            wasLive = live;
            MediaLog.add(context, "bt: " + (live ? "avanzando (sonando)" : "sin avance (pausa/detenido)"));
            if (running) listener.onBluetoothSongChanged(this);
        }
    }

    private void query() {
        context.sendBroadcast(new Intent(ACTION_QUERY).setPackage(PACKAGE));
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() || s.equalsIgnoreCase("unknown") || s.equalsIgnoreCase("null") ? null : s;
    }
}
