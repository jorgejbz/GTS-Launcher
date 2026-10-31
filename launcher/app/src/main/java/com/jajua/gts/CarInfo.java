package com.jajua.gts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client of the head unit's CANBUS data service, com.tw.carinfoservice (Topway). This is where
 * the stock theme (com.dofun.variety) gets the vehicle speed. The APK ships its own AIDL files
 * (com/tw/carinfoservice/CarServiceAidl.aidl and CarServiceCallBack.aidl), so the transaction
 * codes below are simply their declaration order, checked against the service's stub.
 *
 * Speed arrives in km/h as an int (onInstantaneous_Speed, callback code 15), and is also polled
 * with getInstantaneous_Speed (code 18, int[]{value or -1, unit}). The system's unit choice is the
 * global setting Settings.System "SPEED_UNIT" (0 = km, otherwise miles): the service itself uses
 * it to convert distances with 0.621371.
 *
 * Only the read calls of the AIDL are used. Nothing is ever written to the MCU from here.
 */
final class CarInfo {
    interface Listener {
        void onCarData(CarInfo c);
    }

    static final String PACKAGE = "com.tw.carinfoservice";
    private static final String BIND_ACTION = "com.tw.carinfoservice.CarService.Bind";
    private static final String DESCRIPTOR = "com.tw.carinfoservice.CarServiceAidl";
    private static final String CALLBACK_DESCRIPTOR = "com.tw.carinfoservice.CarServiceCallBack";

    private static final int REGISTER = 1;
    private static final int UNREGISTER = 2;
    static final int GET_SPEED = 18;
    private static final int CB_SPEED = 15;

    /**
     * Callback methods (CarServiceCallBack.aidl), index = transaction code. Second element is the
     * argument list: F float, I int, Z boolean, S String, B Bundle, R 16 radar ints.
     */
    static final String[][] CALLBACKS = {
        null,
        {"Voltaje eléctrico", "FS"},
        {"Faros encendidos", "Z"},
        {"Tiempo de conducción", "I"},
        {"Kilometraje total", "FS"},
        {"Autonomía restante", "FS"},
        {"Consumo medio", "FS"},
        {"Consumo instantáneo", "FS"},
        {"Marcha (0 D · 1 N · 2 R · 3 P · 4 S)", "I"},
        {"Cinturón del conductor", "Z"},
        {"Cinturón del copiloto", "Z"},
        {"Freno de mano", "Z"},
        {"Direccional izquierda", "Z"},
        {"Direccional derecha", "Z"},
        {"Intermitentes (hazard)", "Z"},
        {"Velocidad (km/h)", "I"},
        {"Revoluciones del motor (rpm)", "I"},
        {"Temperatura del refrigerante", "IS"},
        {"Temperatura exterior", "FS"},
        {"Temperatura del aceite", "FS"},
        {"Nivel de combustible", "FS"},
        {"Carga de batería (eléctrico)", "F"},
        {"Estado de carga (eléctrico)", "I"},
        {"Alerta de voltaje", "Z"},
        {"Reversa", "Z"},
        {"Puerta delantera izquierda", "Z"},
        {"Puerta delantera derecha", "Z"},
        {"Cajuela", "Z"},
        {"Extensión (onExpand)", "B"},
        {"Voltaje original del auto", "FS"},
        {"Temperatura de la transmisión", "FS"},
        {"Temperatura de admisión", "FS"},
        {"Presión de admisión", "FS"},
        {"Turbo", "FS"},
        {"Modo de manejo", "I"},
        {"Señal OFF", "Z"},
        {"Señal ON", "Z"},
        {"Presión llanta delantera izq.", "FS"},
        {"Presión llanta delantera der.", "FS"},
        {"Presión llanta trasera izq.", "FS"},
        {"Presión llanta trasera der.", "FS"},
        {"Acelerador", "FS"},
        {"Ángulo del volante", "FS"},
        {"Carga del motor", "FS"},
        {"Inclinación adelante/atrás", "FS"},
        {"Inclinación lateral", "FS"},
        {"Kilómetros para el servicio", "FS"},
        {"Tiempo para el servicio", "S"},
        {"Alerta de combustible bajo", "Z"},
        {"Distancia recorrida", "IS"},
        {"Alerta llanta delantera izq.", "II"},
        {"Alerta llanta delantera der.", "II"},
        {"Alerta llanta trasera izq.", "II"},
        {"Alerta llanta trasera der.", "II"},
        {"Temperatura llanta delantera izq.", "IS"},
        {"Temperatura llanta delantera der.", "IS"},
        {"Temperatura llanta trasera izq.", "IS"},
        {"Temperatura llanta trasera der.", "IS"},
        {"Alerta de líquido limpiaparabrisas", "Z"},
        {"Sensores de estacionamiento (16 zonas)", "R"},
        {"Trayectoria de reversa (240 = recto)", "I"},
        {"Direccionales (1 der · 2 izq · 3 hazard)", "I"},
        {"Consumo medio de este viaje", "FS"},
        {"Extensión (onExtendedInterface)", "B"},
        {"Kilómetros de este viaje", "FS"},
        {"Puerta trasera izquierda", "Z"},
        {"Puerta trasera derecha", "Z"},
    };

    /**
     * Read-only getters of CarServiceAidl.aidl: code, reply type (F, I, i = int[], f = float[]),
     * name. closureData / getData / extendedInterface / pauseData / updateApk are left out.
     */
    static final Object[][] GETTERS = {
        {4, "F", "Voltaje eléctrico"}, {5, "I", "Faros"}, {6, "I", "Tiempo de conducción"},
        {7, "i", "Kilometraje total [valor, 0 km · 1 mi]"}, {8, "i", "Autonomía restante [valor, unidad]"},
        {9, "F", "Consumo medio"}, {10, "F", "Consumo instantáneo"}, {11, "I", "Marcha"},
        {12, "I", "Cinturón del conductor"}, {13, "I", "Cinturón del copiloto"}, {14, "I", "Freno de mano"},
        {15, "I", "Direccional izquierda"}, {16, "I", "Direccional derecha"}, {17, "I", "Intermitentes"},
        {18, "i", "Velocidad [km/h, unidad]"}, {19, "I", "Revoluciones del motor"},
        {20, "f", "Temperatura del refrigerante"}, {21, "f", "Temperatura exterior [válido, valor, 0 °C · 1 °F]"},
        {22, "f", "Temperatura del aceite"}, {23, "F", "Nivel de combustible"},
        {24, "F", "Carga de batería (eléctrico)"}, {25, "I", "Estado de carga"}, {26, "I", "Alerta de voltaje"},
        {27, "I", "Reversa"}, {28, "I", "Puertas"}, {29, "F", "Voltaje original del auto"},
        {30, "F", "Temperatura de la transmisión"}, {31, "F", "Temperatura de admisión"},
        {32, "F", "Presión de admisión"}, {33, "I", "Turbo"}, {34, "I", "Modo de manejo"},
        {35, "I", "Señal OFF"}, {36, "I", "Señal ON"}, {37, "f", "Presión de llantas [4 llantas, unidad]"},
        {38, "I", "Acelerador"}, {39, "I", "Ángulo del volante"}, {40, "I", "Carga del motor"},
        {41, "I", "Inclinación adelante/atrás"}, {42, "I", "Inclinación lateral"},
        {43, "i", "Kilómetros para el servicio"}, {44, "I", "Meses para el servicio"},
        {45, "I", "Alerta de combustible bajo"}, {48, "i", "Distancia recorrida"},
        {49, "i", "Alerta llanta delantera izq."}, {50, "i", "Alerta llanta delantera der."},
        {51, "i", "Alerta llanta trasera izq."}, {52, "i", "Alerta llanta trasera der."},
        {53, "f", "Temperatura llanta delantera izq."}, {54, "f", "Temperatura llanta delantera der."},
        {55, "f", "Temperatura llanta trasera izq."}, {56, "f", "Temperatura llanta trasera der."},
        {57, "I", "Alerta de líquido limpiaparabrisas"}, {58, "i", "Sensores de estacionamiento"},
        {59, "I", "Trayectoria de reversa"}, {60, "I", "Direccionales"},
        {61, "F", "Consumo medio de este viaje"},
    };

    /** Latest speed in km/h from the vehicle, or null when there is no valid reading. */
    Integer speedKmh;
    /** False until bound, or when the firmware lacks the service. */
    boolean connected;
    /** Last value of every callback received, by callback code, readable for the diagnostic. */
    final Map<Integer, String> received = new TreeMap<>();

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile IBinder remote;
    private boolean bound;
    private long pollMs;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            pollSpeed();
            if (bound && pollMs > 0) main.postDelayed(this, pollMs);
        }
    };

    private final Binder callback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
            if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
            if (code < 1 || code >= CALLBACKS.length) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK_DESCRIPTOR);
            String value;
            int first = 0;
            try {
                StringBuilder sb = new StringBuilder();
                for (char t : CALLBACKS[code][1].toCharArray()) {
                    if (sb.length() > 0) sb.append(' ');
                    switch (t) {
                        case 'F': sb.append(fmt(data.readFloat())); break;
                        case 'I': {
                            int v = data.readInt();
                            if (sb.length() == 0) first = v;
                            sb.append(v);
                            break;
                        }
                        case 'Z': sb.append(data.readInt() != 0 ? "sí" : "no"); break;
                        case 'S': {
                            String s = data.readString();
                            if (s != null && !s.isEmpty()) sb.append(s);
                            break;
                        }
                        case 'B': {
                            Bundle b = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                            sb.append(bundle(b));
                            break;
                        }
                        case 'R': {
                            int[] r = new int[16];
                            for (int i = 0; i < 16; i++) r[i] = data.readInt();
                            sb.append(Arrays.toString(r));
                            break;
                        }
                        default: break;
                    }
                }
                value = sb.toString().trim();
            } catch (RuntimeException ex) {
                value = "(no se pudo leer)";
            }
            if (reply != null) reply.writeNoException();
            final String v = value;
            final int speed = first;
            main.post(() -> {
                received.put(code, v);
                if (code == CB_SPEED) setSpeed(speed);
            });
            return true;
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = service;
            connected = true;
            io.execute(() -> transact(REGISTER, p -> p.writeStrongBinder(callback), null));
            pollSpeed();
            listener.onCarData(CarInfo.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            connected = false;
            speedKmh = null;
            listener.onCarData(CarInfo.this);
        }
    };

    CarInfo(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        callback.attachInterface(new IInterface() {
            @Override
            public IBinder asBinder() {
                return callback;
            }
        }, CALLBACK_DESCRIPTOR);
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    /** pollMs: also asks for the speed this often (0 = callbacks only). */
    void start(long pollMs) {
        if (bound) return;
        this.pollMs = pollMs;
        try {
            bound = context.bindService(new Intent(BIND_ACTION).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ex) {
            bound = false;
        }
        if (bound && pollMs > 0) main.postDelayed(poll, pollMs);
    }

    void stop() {
        main.removeCallbacks(poll);
        if (!bound) return;
        bound = false;
        IBinder r = remote;
        // The service pauses the speed stream on unregister (as when the stock theme leaves); the
        // next speed query from any app turns it back on. closureData is never sent.
        if (r != null) io.execute(() -> transact(r, UNREGISTER, p -> p.writeStrongBinder(callback), null));
        try {
            context.unbindService(connection);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
        remote = null;
        connected = false;
        speedKmh = null;
    }

    boolean bound() {
        return bound;
    }

    private void pollSpeed() {
        io.execute(() -> {
            int[] v = getInts(GET_SPEED);
            if (v != null && v.length > 0) main.post(() -> setSpeed(v[0]));
        });
    }

    private void setSpeed(int raw) {
        Integer next = raw < 0 || raw > 400 ? null : raw;
        if (next == null ? speedKmh == null : next.equals(speedKmh)) return;
        speedKmh = next;
        if (bound) listener.onCarData(this);
    }

    /** Reads every getter once. Blocking: call it off the main thread (the diagnostic does). */
    String dumpGetters() {
        if (remote == null) return "  servicio sin conectar\n";
        StringBuilder sb = new StringBuilder();
        for (Object[] g : GETTERS) {
            int code = (Integer) g[0];
            String type = (String) g[1];
            final String[] out = {"sin respuesta"};
            transact(code, p -> {}, reply -> {
                switch (type) {
                    case "F": out[0] = fmt(reply.readFloat()); break;
                    case "I": out[0] = String.valueOf(reply.readInt()); break;
                    case "i": out[0] = Arrays.toString(reply.createIntArray()); break;
                    case "f": {
                        float[] f = reply.createFloatArray();
                        StringBuilder s = new StringBuilder("[");
                        if (f != null) for (int i = 0; i < f.length; i++) s.append(i == 0 ? "" : ", ").append(fmt(f[i]));
                        out[0] = s.append(']').toString();
                        break;
                    }
                    default: break;
                }
            });
            sb.append("  ").append(code).append(" · ").append(g[2]).append(": ").append(out[0]).append('\n');
        }
        return sb.toString();
    }

    /** Last callback values, one line each, for the diagnostic. */
    String dumpReceived() {
        if (received.isEmpty()) return "  (ningún aviso recibido todavía)\n";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : received.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" · ").append(CALLBACKS[e.getKey()][0]).append(": ")
                    .append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private int[] getInts(int code) {
        final int[][] out = {null};
        transact(code, p -> {}, reply -> out[0] = reply.createIntArray());
        return out[0];
    }

    private interface Args {
        void write(Parcel p);
    }

    private interface Reply {
        void read(Parcel p);
    }

    private void transact(int code, Args args, Reply onReply) {
        IBinder r = remote;
        if (r != null) transact(r, code, args, onReply);
    }

    private static void transact(IBinder r, int code, Args args, Reply onReply) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            args.write(data);
            r.transact(code, data, reply, 0);
            reply.readException();
            if (onReply != null) onReply.read(reply);
        } catch (Exception ignored) {
            // Service went away or does not implement that call; the value stays unknown.
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /** True when the system is set to miles (Settings.System "SPEED_UNIT" != 0), as the stock theme reads it. */
    static boolean systemUsesMiles(Context c) {
        try {
            return android.provider.Settings.System.getInt(c.getContentResolver(), "SPEED_UNIT", 0) != 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String fmt(float f) {
        return f == Math.rint(f) ? String.valueOf((int) f) : String.format(Locale.ROOT, "%.2f", f);
    }

    private static String bundle(Bundle b) {
        if (b == null) return "(vacío)";
        StringBuilder sb = new StringBuilder("{");
        for (String k : b.keySet()) {
            Object v = b.get(k);
            sb.append(sb.length() > 1 ? ", " : "").append(k).append('=')
                    .append(v instanceof int[] ? Arrays.toString((int[]) v)
                            : v instanceof byte[] ? Arrays.toString((byte[]) v) : String.valueOf(v));
        }
        return sb.append('}').toString();
    }
}
