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
 * Vehicle speed for the info panel.
 *
 * Sources:
 *  - CANBUS: the head unit's com.tw.carinfoservice (see CarInfo), the same source as the stock
 *    theme. "Automática" uses it whenever the service exists.
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
    static final String CANBUS = "canbus";
    /** CANBUS when the head unit has the service, otherwise off. Default. */
    static final String AUTO = "auto";

    static final String UNIT_AUTO = "auto";
    static final String UNIT_KMH = "kmh";
    static final String UNIT_MPH = "mph";

    private static final long GPS_TIMEOUT_MS = 4000;
    private static final long BROADCAST_TIMEOUT_MS = 10000;
    /** Backup poll of the CANBUS speed, in case a change notice is missed. */
    private static final long CANBUS_POLL_MS = 1500;
    /** GPS speeds below this are noise from a parked car. */
    private static final float GPS_STILL_KMH = 5f;

    String mode = OFF;
    /** Current speed in km/h, or null when there is no fresh reading. */
    Float kmh;
    /** What the number means / why it is missing. */
    String caption = "Sin fuente configurada";
    /** Show miles per hour instead of km/h (the value in kmh stays in km/h). */
    boolean mph;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean inMs;
    private String extra;
    private CarInfo car;
    private String unitPref = UNIT_AUTO;

    /** Follows the system's km/mi switch live when the unit is "Automática". */
    private final android.database.ContentObserver unitObserver = new android.database.ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            boolean next = CarInfo.systemUsesMiles(context);
            if (next == mph) return;
            mph = next;
            notifyListener();
        }
    };

    /** AUTO mode: the two sources, combined by combine(). */
    private Integer canKmh;
    private Float gpsKmh;
    private boolean gpsOn;
    private final Runnable gpsExpire = () -> {
        gpsKmh = null;
        combine();
    };

    private final Runnable expire = () -> {
        kmh = null;
        notifyListener();
    };

    private final LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            Float v = l.hasSpeed() ? l.getSpeed() * 3.6f : null;
            if (AUTO.equals(mode)) {
                main.removeCallbacks(gpsExpire);
                gpsKmh = v;
                if (v != null) main.postDelayed(gpsExpire, GPS_TIMEOUT_MS);
                combine();
            } else {
                publish(v, GPS_TIMEOUT_MS);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {
            if (AUTO.equals(mode)) {
                gpsKmh = null;
                combine();
            } else {
                publish(null, 0);
            }
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
        boolean canbus = CarInfo.installed(context);
        prefs.migrateSpeedMode(canbus);
        mode = prefs.speedMode();
        boolean gpsAllowed = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        unitPref = prefs.speedUnit();
        mph = UNIT_MPH.equals(unitPref) || (UNIT_AUTO.equals(unitPref) && CarInfo.systemUsesMiles(context));
        if (UNIT_AUTO.equals(unitPref)) {
            try {
                context.getContentResolver().registerContentObserver(
                        android.provider.Settings.System.getUriFor("SPEED_UNIT"), false, unitObserver);
            } catch (RuntimeException ignored) {
                // The unit is still read once above.
            }
        }
        kmh = null;
        if (AUTO.equals(mode)) {
            // The CANBUS service of this TS10 answers 0 km/h even while driving on some cars, and the
            // stock theme asks for location permission: it uses the GPS. So: the car's own value when
            // it reports movement, otherwise the GPS, otherwise the car's 0.
            canKmh = null;
            gpsKmh = null;
            if (canbus) {
                car = new CarInfo(context, c -> {
                    canKmh = c.speedKmh;
                    logCanbus(c);
                    combine();
                });
                car.start(CANBUS_POLL_MS);
            }
            if (gpsAllowed) {
                try {
                    LocationManager lm = context.getSystemService(LocationManager.class);
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener, Looper.getMainLooper());
                    gpsOn = true;
                } catch (RuntimeException ex) {
                    gpsOn = false;
                }
            }
            MediaLog.add(context, "velocidad: inicio visible · automática · CANBUS "
                    + (car != null && car.bound() ? "enlazando" : "no") + " · GPS "
                    + (gpsOn ? "activo" : gpsAllowed ? "no disponible" : "sin permiso") + " · unidad " + (mph ? "mph" : "km/h"));
            combine();
            return;
        }
        if (CANBUS.equals(mode)) {
            if (!canbus) {
                caption = "Servicio CANBUS no disponible";
            } else {
                caption = "Datos del vehículo";
                car = new CarInfo(context, c -> {
                    kmh = c.speedKmh == null ? null : c.speedKmh.floatValue();
                    if (!c.connected) caption = "Conectando con el CANBUS…";
                    else caption = "Datos del vehículo";
                    logCanbus(c);
                    notifyListener();
                });
                car.start(CANBUS_POLL_MS);
                if (!car.bound()) caption = "No se pudo conectar con el CANBUS";
                MediaLog.add(context, "velocidad: inicio visible · CANBUS " + (car.bound() ? "enlazando" : "sin enlace")
                        + " · unidad " + (mph ? "mph" : "km/h"));
            }
        } else if (GPS.equals(mode)) {
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
        if (CANBUS.equals(mode) || AUTO.equals(mode)) MediaLog.add(context, "velocidad: inicio oculto (lectura en pausa)");
        running = false;
        main.removeCallbacks(expire);
        main.removeCallbacks(gpsExpire);
        try {
            context.getContentResolver().unregisterContentObserver(unitObserver);
        } catch (RuntimeException ignored) {
            // Never registered.
        }
        if (car != null) {
            car.stop();
            car = null;
        }
        if (GPS.equals(mode) || gpsOn) {
            gpsOn = false;
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

    private long lastLog;
    private boolean lastConnected;
    private Integer lastLogged;

    /**
     * Writes CANBUS speed readings to the on-device log so a drive can be checked afterwards in the
     * diagnostic: every connect/disconnect, stop/start (0 crossing), and otherwise one line per 5 s.
     */
    private void logCanbus(CarInfo c) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (c.connected != lastConnected) {
            lastConnected = c.connected;
            MediaLog.add(context, "velocidad: CANBUS " + (c.connected ? "conectado" : "desconectado"));
        }
        Integer v = c.speedKmh;
        if (v == null ? lastLogged == null : v.equals(lastLogged)) return;
        boolean zeroCross = v == null || lastLogged == null || (v == 0) != (lastLogged == 0);
        if (!zeroCross && now - lastLog < 5000) return;
        lastLog = now;
        lastLogged = v;
        MediaLog.add(context, "velocidad: " + (v == null ? "sin lectura válida" : v + " km/h"));
    }

    private long lastGpsLog;

    /** AUTO: picks what to show from the CANBUS and GPS readings. */
    private void combine() {
        if (gpsKmh != null) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastGpsLog >= 5000) {
                lastGpsLog = now;
                MediaLog.add(context, "velocidad: GPS " + Math.round(gpsKmh) + " km/h · CANBUS "
                        + (canKmh == null ? "sin dato" : canKmh + " km/h"));
            }
        }
        if (canKmh != null && canKmh > 0) {
            kmh = canKmh.floatValue();
            caption = "Datos del vehículo";
        } else if (gpsKmh != null) {
            // Standing still the receiver wanders between 0 and ~4 km/h (seen on the TS10); show 0.
            kmh = gpsKmh < GPS_STILL_KMH ? 0f : gpsKmh;
            caption = "GPS";
        } else if (canKmh != null) {
            kmh = 0f;
            caption = "Datos del vehículo";
        } else {
            kmh = null;
            boolean gpsAllowed = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            caption = !gpsAllowed && car == null ? "Falta permiso de ubicación"
                    : gpsOn ? "Buscando señal GPS…" : "Sin señal";
        }
        notifyListener();
    }

    /** The number to show, already in the chosen unit, or null. */
    Integer display() {
        if (kmh == null) return null;
        return Math.round(mph ? kmh * 0.621371f : kmh);
    }

    String unitLabel() {
        return mph ? "mph" : "km/h";
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
