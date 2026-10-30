package com.jajua.gts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Which phone projection TLINK5 actually has connected. TLINK5 broadcasts action
 * "com.zjinnova.zlink" with String extras "status" (CONNECTED, DISCONNECT, MAIN_PAGE_SHOW,
 * PHONE_CALL_ON, PHONE_CALL_OFF) and "phoneMode"; the head unit's Bluetooth app (com.tw.bt) listens
 * to it and compares phoneMode with "airplay_wired", "android_mirror_wired" and
 * "android_mirror_wireless". CarPlay and Android Auto values are recognised by name and the raw
 * value is logged so they can be confirmed.
 *
 * The connection happens while TLINK5 is in front, so this listens for the launcher's whole
 * lifetime (registered in onCreate), not only while the home screen is visible.
 */
final class TLinkState {
    interface Listener {
        void onTLinkChanged(TLinkState s);
    }

    private static final String ACTION = "com.zjinnova.zlink";

    /** Label of the connected mode ("Apple CarPlay", "Android Auto"…), or null when none. */
    String mode;
    String rawMode;
    String status;
    /** From MAIN_AUDIO_START / MAIN_AUDIO_STOP; null until TLINK5 has said either. */
    Boolean audioPlaying;

    private final Context context;
    private final Listener listener;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String st = intent.getStringExtra("status");
            String pm = intent.getStringExtra("phoneMode");
            MediaLog.add(context, "tlink: status=" + st + " · phoneMode=" + pm);
            if (st != null) status = st;
            if ("MAIN_AUDIO_START".equals(st)) audioPlaying = true;
            if ("MAIN_AUDIO_STOP".equals(st)) audioPlaying = false;
            if ("DISCONNECT".equals(st)) {
                rawMode = null;
                mode = null;
                audioPlaying = null;
            } else if (pm != null && !pm.isEmpty()) {
                rawMode = pm;
                mode = label(pm);
            }
            listener.onTLinkChanged(TLinkState.this);
        }
    };

    TLinkState(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void register() {
        if (registered) return;
        registered = true;
        context.registerReceiver(receiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
    }

    void unregister() {
        if (!registered) return;
        registered = false;
        context.unregisterReceiver(receiver);
    }

    boolean connected() {
        return mode != null;
    }

    boolean isCarPlay() {
        return rawMode != null && rawMode.toLowerCase(java.util.Locale.ROOT).contains("carplay");
    }

    boolean isAndroidAuto() {
        return rawMode != null && rawMode.toLowerCase(java.util.Locale.ROOT).contains("auto");
    }

    /** "inalámbrico" / "por cable" from phoneMode values such as carplay_wireless (seen on the TS10). */
    String linkType() {
        if (rawMode == null) return null;
        String m = rawMode.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("wireless")) return "inalámbrico";
        if (m.contains("wired")) return "por cable";
        return null;
    }

    static String label(String phoneMode) {
        String m = phoneMode.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("carplay")) return "Apple CarPlay";
        if (m.contains("auto")) return "Android Auto";
        if (m.contains("airplay")) return "AirPlay";
        if (m.contains("mirror")) return "Duplicación de pantalla";
        if (m.contains("carlife")) return "CarLife";
        return "Teléfono (" + phoneMode + ")";
    }
}
