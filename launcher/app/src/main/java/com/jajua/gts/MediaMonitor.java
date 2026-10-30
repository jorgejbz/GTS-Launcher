package com.jajua.gts;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * Follows the media session Android reports as playing (any app: Bluetooth audio, radio, music).
 * Title and artist come only from that session's metadata; nothing is guessed.
 */
final class MediaMonitor {
    interface Listener {
        void onMediaChanged(MediaMonitor m);
    }

    /** False until the user enables "información de reproducción" in system settings. */
    boolean access;
    String pkg;
    String title;
    String artist;
    boolean playing;

    private final Context context;
    private final Listener listener;
    private final ComponentName component;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController controller;
    private boolean running;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::select;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            // Another session may have started playing; re-pick so the panel follows the audio.
            refresh();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            read();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    MediaMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.component = new ComponentName(this.context, MediaAccessService.class);
    }

    static boolean hasAccess(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 27) {
            String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            return enabled != null && enabled.contains(new ComponentName(c, MediaAccessService.class).flattenToString());
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, MediaAccessService.class));
    }

    void start() {
        if (running) return;
        running = true;
        access = hasAccess(context);
        if (access) {
            msm = context.getSystemService(MediaSessionManager.class);
            try {
                msm.addOnActiveSessionsChangedListener(sessions, component, main);
            } catch (SecurityException ex) {
                access = false;
            }
        }
        refresh();
    }

    void stop() {
        if (!running) return;
        running = false;
        if (msm != null && access) {
            try {
                msm.removeOnActiveSessionsChangedListener(sessions);
            } catch (RuntimeException ignored) {
                // Already gone.
            }
        }
        swap(null);
    }

    private void refresh() {
        if (!running) return;
        if (!access || msm == null) {
            swap(null);
            read();
            return;
        }
        try {
            select(msm.getActiveSessions(component));
        } catch (SecurityException ex) {
            access = false;
            swap(null);
            read();
        }
    }

    private void select(List<MediaController> list) {
        if (!running) return;
        MediaController pick = null;
        if (list != null) {
            for (MediaController mc : list) {
                PlaybackState s = mc.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    pick = mc;
                    break;
                }
            }
            if (pick == null && !list.isEmpty()) pick = list.get(0);
        }
        swap(pick);
        read();
    }

    private void swap(MediaController next) {
        if (controller != null && next != null && controller.getSessionToken().equals(next.getSessionToken())) return;
        if (controller != null) controller.unregisterCallback(callback);
        controller = next;
        if (controller != null) controller.registerCallback(callback, main);
    }

    private void read() {
        MediaController mc = controller;
        if (mc == null) {
            pkg = null;
            title = null;
            artist = null;
            playing = false;
        } else {
            pkg = mc.getPackageName();
            MediaMetadata md = mc.getMetadata();
            title = md == null ? null : firstNonEmpty(md.getString(MediaMetadata.METADATA_KEY_TITLE),
                    md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
            artist = md == null ? null : firstNonEmpty(md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
            PlaybackState s = mc.getPlaybackState();
            playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
            MediaLog.add(context, "sesión: " + pkg + " · estado " + (s == null ? "?" : s.getState())
                    + (title != null ? " · «" + title + "» · " + artist : " · sin metadatos"));
        }
        if (running) listener.onMediaChanged(this);
    }

    boolean hasSession() {
        return controller != null;
    }

    void playPause() {
        if (controller == null) return;
        if (playing) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    void next() {
        if (controller != null) controller.getTransportControls().skipToNext();
    }

    void previous() {
        if (controller != null) controller.getTransportControls().skipToPrevious();
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }
}
