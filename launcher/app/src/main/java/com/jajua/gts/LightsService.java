package com.jajua.gts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the ambient lights connected while the feature is on, so changing the accent colour reaches
 * them instantly even when the launcher is in the background. A foreground service is the only way
 * Android lets a BLE link stay up reliably; the notification is minimal and silent.
 *
 * <p>Started/stopped from {@link #sync(Context)} whenever the lights settings change.
 */
public final class LightsService extends Service {
    private static final String CHANNEL = "gts-lights";
    private static final int ID = 42;

    /** Start the service when the lights are linked and a controller is chosen, else stop it. */
    static void sync(Context c) {
        Prefs p = Prefs.get(c);
        boolean on = p.lightsLinkColor() && p.lightsAddress() != null;
        Intent i = new Intent(c, LightsService.class);
        if (on) {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } else {
            c.stopService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Luces ambientales",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AmbientLights al = AmbientLights.get(this);
        // Without the Bluetooth permission a connected-device foreground service isn't allowed.
        if (!al.canConnect()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startAsForeground(notification());
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        al.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        AmbientLights.get(this).stop();
        super.onDestroy();
    }

    private Notification notification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_lantern)
                .setContentTitle("Luces ambientales")
                .setContentText("Conectadas al color del launcher")
                .setOngoing(true)
                .build();
    }

    /** Foreground type must be given on API 29+; it is declared in the manifest too. */
    private void startAsForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(ID, n);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
