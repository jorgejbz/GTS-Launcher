package com.jajua.gts;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Vehicle speed for the info panel. Off by default: on the TS10 the source of the stock launcher's
 * km/h is not known yet, so the panel shows "--" instead of an invented value.
 *
 * Sources:
 *  - GPS: the satellite receiver's own speed, opt-in and always labelled "aproximada".
 *  - Broadcast: a speed value sent by another app (e.g. a CANBUS app) as an intent extra.
 *    Action and extra name are configured once the audit identifies them.
 * A reading older than the timeout is cleared, never shown as current.
 */
final class SpeedMonitor {
    interface Listener {
        void onSpeedChanged(SpeedMonitor s);
    }

    static final String OFF = "off";
    static final String GPS = "gps";
    static final String BROADCAST = "broadcast";

    private static final long GPS_TIMEOUT_MS = 4000;
    private static final long BROADCAST_TIMEOUT_MS = 10000;

    String mode = OFF;
    /** Current speed in km/h, or null when there is no fresh reading. */
    Float kmh;
    /** What the number means / why it is missing. */
    String caption = "Sin fuente configurada";

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean inMs;
    private String extra;

    private final Runnable expire = () -> {
        kmh = null;
        notifyListener();
    };

    private final LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            publish(l.hasSpeed() ? l.getSpeed() * 3.6f : null, GPS_TIMEOUT_MS);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {
            publish(null, 0);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            Float v = number(intent.getExtras() == null ? null : intent.getExtras().get(extra));
            if (v != null && inMs) v *= 3.6f;
            publish(v, BROADCAST_TIMEOUT_MS);
        }
    };

    SpeedMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        Prefs prefs = Prefs.get(context);
        mode = prefs.speedMode();
        kmh = null;
        if (GPS.equals(mode)) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                caption = "Falta permiso de ubicación";
            } else {
                caption = "GPS · aproximada";
                try {
                    LocationManager lm = context.getSystemService(LocationManager.class);
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener, Looper.getMainLooper());
                } catch (RuntimeException ex) {
                    caption = "GPS no disponible";
                }
            }
        } else if (BROADCAST.equals(mode) && !prefs.speedAction().isEmpty() && !prefs.speedExtra().isEmpty()) {
            extra = prefs.speedExtra();
            inMs = prefs.speedInMs();
            caption = "Datos del vehículo";
            context.registerReceiver(receiver, new IntentFilter(prefs.speedAction()), Context.RECEIVER_EXPORTED);
        } else {
            mode = OFF;
            caption = "Sin fuente configurada";
        }
        notifyListener();
    }

    void stop() {
        if (!running) return;
        running = false;
        main.removeCallbacks(expire);
        if (GPS.equals(mode)) {
            try {
                context.getSystemService(LocationManager.class).removeUpdates(gpsListener);
            } catch (RuntimeException ignored) {
                // Never registered.
            }
        } else if (BROADCAST.equals(mode)) {
            try {
                context.unregisterReceiver(receiver);
            } catch (RuntimeException ignored) {
                // Never registered.
            }
        }
        kmh = null;
    }

    private void publish(Float value, long timeoutMs) {
        main.removeCallbacks(expire);
        kmh = value == null || value < 0 || value > 400 ? null : value;
        if (kmh != null && timeoutMs > 0) main.postDelayed(expire, timeoutMs);
        notifyListener();
    }

    private void notifyListener() {
        if (running) listener.onSpeedChanged(this);
    }

    private static Float number(Object o) {
        if (o instanceof Number) return ((Number) o).floatValue();
        if (o instanceof String) {
            try {
                return Float.parseFloat(((String) o).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
