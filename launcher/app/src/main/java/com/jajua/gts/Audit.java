package com.jajua.gts;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * On-device audit of the TS10: which apps exist, how each can be launched, which vendor components
 * and permissions might expose radio/CANBUS/speed, and what Android reports for Bluetooth and media.
 * Deliberately never reads IMEI, ICCID, serial numbers, Android ID or MAC addresses, and masks any
 * value that looks like one.
 */
final class Audit {
    /** Package-name/label words that suggest a vehicle or head-unit component worth detailing. */
    private static final Pattern INTERESTING = Pattern.compile(
            "tlink|carplay|zlink|carlink|autokit|radio|fm|bt|blue|music|lantern|magic|can|mcu|car|vehicle|"
                    + "speed|steer|wheel|swc|launcher|home|setting|gps|nav|ts10|tw|syu|fyt|uis|sprd|unisoc|"
                    + "volante|música|musica|key|media|audio|dsp|eq|theme|dofun",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROP_KEYS = Pattern.compile(
            "launcher|home|can|mcu|car|speed|radio|bt|bluetooth|tlink|ts10|theme|ro\\.build\\.|ro\\.product\\.|"
                    + "ro\\.board|ro\\.hardware|ro\\.sf\\.lcd_density|persist\\.sys\\.|sys\\.fyt|ro\\.fota|customer",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROP_SECRET = Pattern.compile(
            "serial|imei|meid|iccid|imsi|mac|bdaddr|addr|ssid|pass|android_id|uuid|deviceid|device_id|\\bsn\\b|"
                    + "\\.sn\\]|token|key\\]|account|phone_number|msisdn",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOKS_SECRET = Pattern.compile(
            "\\b\\d{14,20}\\b|([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}|\\b[0-9a-f]{32}\\b");

    private Audit() {}

    static String run(Context c) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        PackageManager pm = c.getPackageManager();
        sb.append("AUDITORÍA GTS LAUNCHER · ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date())).append('\n');
        sb.append("(Sin IMEI, ICCID, números de serie ni direcciones MAC.)\n");

        h(sb, "Equipo");
        kv(sb, "Fabricante / marca", Build.MANUFACTURER + " / " + Build.BRAND);
        kv(sb, "Modelo / dispositivo", Build.MODEL + " / " + Build.DEVICE + " / " + Build.PRODUCT);
        kv(sb, "Hardware / placa", Build.HARDWARE + " / " + Build.BOARD);
        kv(sb, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        kv(sb, "Compilación", Build.DISPLAY);
        kv(sb, "ABIs", String.join(", ", Build.SUPPORTED_ABIS));
        DisplayMetrics dm = new DisplayMetrics();
        c.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
        kv(sb, "Pantalla", dm.widthPixels + " x " + dm.heightPixels + " px, " + dm.densityDpi + " dpi, fuente x"
                + c.getResources().getConfiguration().fontScale);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        c.getSystemService(ActivityManager.class).getMemoryInfo(mi);
        kv(sb, "RAM total / libre", (mi.totalMem >> 20) + " MB / " + (mi.availMem >> 20) + " MB");

        h(sb, "Launcher de inicio");
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo def = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        kv(sb, "Predeterminado", def == null ? "ninguno"
                : def.activityInfo.packageName + "/" + def.activityInfo.name
                + ("android".equals(def.activityInfo.packageName) ? "  (sin predeterminado: Android pregunta)" : ""));
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            sb.append("  candidato: ").append(ri.loadLabel(pm)).append(" · ").append(ri.activityInfo.packageName)
                    .append('/').append(ri.activityInfo.name).append(system(ri.activityInfo.applicationInfo)).append('\n');
        }
        kv(sb, "Pantalla «Apps predeterminadas»", new Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                .resolveActivity(pm) != null ? "disponible" : "NO disponible en este firmware");
        kv(sb, "Acceso a notificaciones", new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .resolveActivity(pm) != null ? "pantalla disponible" : "pantalla NO disponible");

        h(sb, "Accesos del launcher");
        for (Role r : Role.values()) {
            Targets.Target t = Targets.resolve(c, r);
            kv(sb, r.title, t == null ? "sin app" : t.label + " · " + t.pkg + (t.manual ? " (elegida)" : " (detectada)")
                    + (t.component != null ? " · " + t.component.getClassName() : ""));
        }

        h(sb, "Apps con icono (" + AppCatalog.apps().size() + ")");
        for (AppCatalog.App a : AppCatalog.apps()) {
            sb.append("  ").append(a.label).append(" · ").append(a.pkg).append(" · ").append(a.component.getClassName())
                    .append(version(pm, a.pkg)).append(a.system ? " [sistema]" : "").append('\n');
        }

        List<PackageInfo> all = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS
                | PackageManager.MATCH_DISABLED_COMPONENTS);
        h(sb, "Todos los paquetes (" + all.size() + ")");
        for (PackageInfo pi : all) {
            sb.append("  ").append(pi.packageName).append(" · ").append(label(pm, pi.applicationInfo))
                    .append(system(pi.applicationInfo)).append(pi.applicationInfo.enabled ? "" : " [desactivado]").append('\n');
        }

        h(sb, "Detalle de paquetes relevantes");
        for (PackageInfo pi : all) {
            String lbl = label(pm, pi.applicationInfo);
            if (!INTERESTING.matcher(pi.packageName).find() && !INTERESTING.matcher(lbl).find()) continue;
            if (pi.packageName.startsWith("com.google.android.") && !pi.packageName.contains("projection")) continue;
            sb.append("\n# ").append(lbl).append(" · ").append(pi.packageName).append(" ").append(pi.versionName)
                    .append(system(pi.applicationInfo)).append('\n');
            if (pi.applicationInfo.sharedLibraryFiles != null || pi.sharedUserId != null) {
                sb.append("  sharedUserId: ").append(pi.sharedUserId).append('\n');
            }
            if (pi.activities != null) for (ActivityInfo ai : pi.activities) if (ai.exported) {
                sb.append("  actividad: ").append(ai.name).append(state(pm, ai))
                        .append(ai.permission != null ? "  (permiso " + ai.permission + ")" : "").append('\n');
            }
            if (pi.services != null) for (ServiceInfo si : pi.services) if (si.exported) {
                sb.append("  servicio: ").append(si.name).append(si.permission != null ? "  (permiso " + si.permission + ")" : "").append('\n');
            }
            if (pi.receivers != null) for (ActivityInfo ri : pi.receivers) if (ri.exported) {
                sb.append("  receptor: ").append(ri.name).append(ri.permission != null ? "  (permiso " + ri.permission + ")" : "").append('\n');
            }
            if (pi.providers != null) for (ProviderInfo pr : pi.providers) {
                sb.append("  proveedor: ").append(pr.authority).append(pr.exported ? " (exportado)" : "")
                        .append(pr.readPermission != null ? "  (lectura " + pr.readPermission + ")" : "").append('\n');
            }
            if (pi.permissions != null) for (PermissionInfo perm : pi.permissions) {
                sb.append("  define permiso: ").append(perm.name).append(" · ").append(protection(perm)).append('\n');
            }
            // Vendor permissions and location show where an app gets vehicle data from.
            if (pi.requestedPermissions != null) for (String rp : pi.requestedPermissions) {
                if (!rp.startsWith("android.permission.") || rp.contains("LOCATION")) {
                    sb.append("  usa permiso: ").append(rp).append('\n');
                }
            }
        }

        h(sb, "Fuentes posibles de velocidad");
        sb.append(probeService(c, "com.tw.carinfoservice", "com.tw.carinfoservice.CarService"));
        sb.append(probeClass("android.tw.john.TWUtil"));

        h(sb, "Bluetooth (según Android)");
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) {
            sb.append("  Android no expone un adaptador Bluetooth (el módulo puede estar gestionado por el fabricante).\n");
        } else {
            try {
                kv(sb, "Activado", String.valueOf(bt.isEnabled()));
                int[] ids = {1, 2, 11, 12, 16};
                String[] names = {"HFP (headset)", "A2DP (fuente)", "A2DP sink", "AVRCP controller", "HFP client"};
                for (int i = 0; i < ids.length; i++) kv(sb, names[i], state(bt.getProfileConnectionState(ids[i])));
                kv(sb, "Equipos emparejados", String.valueOf(bt.getBondedDevices().size()));
            } catch (SecurityException ex) {
                sb.append("  Sin permiso para leer el estado.\n");
            }
        }

        h(sb, "Multimedia");
        kv(sb, "Acceso del launcher a sesiones", MediaMonitor.hasAccess(c) ? "concedido" : "no concedido");
        if (MediaMonitor.hasAccess(c)) {
            try {
                android.media.session.MediaSessionManager msm = c.getSystemService(android.media.session.MediaSessionManager.class);
                for (android.media.session.MediaController mc : msm.getActiveSessions(
                        new android.content.ComponentName(c, MediaAccessService.class))) {
                    android.media.session.PlaybackState st = mc.getPlaybackState();
                    sb.append("  sesión: ").append(mc.getPackageName()).append(" · estado ")
                            .append(st == null ? "?" : st.getState()).append(" · metadatos ")
                            .append(mc.getMetadata() == null ? "no" : "sí").append('\n');
                }
            } catch (SecurityException ignored) {
                sb.append("  No se pudieron leer las sesiones.\n");
            }
        }

        sb.append(probeBtSong(c));

        h(sb, "Bus del estéreo (XTService) y voltaje de la MCU");
        sb.append(probeXt(c));
        sb.append(probeVoltage());
        sb.append(probeLights());

        h(sb, "Datos del vehículo (com.tw.carinfoservice)");
        sb.append(probeCarInfo(c));

        h(sb, "Registro multimedia (últimos eventos de la pantalla de inicio)");
        sb.append(MediaLog.read(c));

        h(sb, "Batería y voltaje");
        Intent bat = c.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat == null) {
            sb.append("  Android no informa batería.\n");
        } else {
            kv(sb, "Voltaje (EXTRA_VOLTAGE)", bat.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1) + " mV");
            kv(sb, "Nivel", bat.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) + " / "
                    + bat.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1));
            kv(sb, "Conectado / presente", bat.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) + " / "
                    + bat.getBooleanExtra(android.os.BatteryManager.EXTRA_PRESENT, false));
            if (bat.getExtras() != null) {
                for (String k : bat.getExtras().keySet()) {
                    sb.append("  extra ").append(k).append(" = ").append(bat.getExtras().get(k)).append('\n');
                }
            }
        }
        for (String table : new String[] {"system", "global"}) {
            try (android.database.Cursor cur = c.getContentResolver().query(
                    android.net.Uri.parse("content://settings/" + table), new String[] {"name", "value"}, null, null, null)) {
                while (cur != null && cur.moveToNext()) {
                    String name = cur.getString(0);
                    if (name != null && name.toLowerCase(Locale.ROOT).matches(".*(volt|batt|acc|power|tw_|can_).*")
                            && !PROP_SECRET.matcher(name).find()) {
                        sb.append("  ajuste ").append(table).append(": ").append(name).append(" = ").append(cur.getString(1)).append('\n');
                    }
                }
            } catch (RuntimeException ex) {
                sb.append("  ajustes ").append(table).append(": no legibles\n");
            }
        }

        h(sb, "Ubicación y sensores");
        LocationManager lm = c.getSystemService(LocationManager.class);
        kv(sb, "Proveedores de ubicación", lm == null ? "ninguno" : String.valueOf(lm.getAllProviders()));
        kv(sb, "GPS en hardware", String.valueOf(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)));
        kv(sb, "Función automotriz Android", String.valueOf(pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)));
        SensorManager sm = c.getSystemService(SensorManager.class);
        if (sm != null) for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
            sb.append("  sensor: ").append(s.getName()).append(" · ").append(s.getStringType()).append('\n');
        }

        h(sb, "Propiedades del sistema (filtradas)");
        try {
            Process proc = new ProcessBuilder("getprop").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int end = line.indexOf(']');
                    String key = end > 0 ? line.substring(0, end + 1) : line;
                    if (!PROP_KEYS.matcher(key).find() || PROP_SECRET.matcher(key).find()) continue;
                    sb.append("  ").append(LOOKS_SECRET.matcher(line).replaceAll("[oculto]")).append('\n');
                }
            }
            proc.destroy();
        } catch (Exception ex) {
            sb.append("  getprop no disponible: ").append(ex.getClass().getSimpleName()).append('\n');
        }
        return LOOKS_SECRET.matcher(sb).replaceAll("[oculto]");
    }

    /**
     * Binds to an exported service only to read its AIDL interface name, then unbinds. No calls
     * are made on the interface. Must run off the main thread (waits for the main-thread callback).
     */
    private static String probeService(Context c, String pkg, String cls) {
        StringBuilder out = new StringBuilder("  servicio ").append(cls).append(": ");
        final String[] descriptor = {null};
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        android.content.ServiceConnection conn = new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
                try {
                    descriptor[0] = binder == null ? "(sin binder)" : binder.getInterfaceDescriptor();
                } catch (android.os.RemoteException ex) {
                    descriptor[0] = "(error " + ex.getClass().getSimpleName() + ")";
                }
                done.countDown();
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {}
        };
        try {
            boolean bound = c.bindService(new Intent().setClassName(pkg, cls), conn, Context.BIND_AUTO_CREATE);
            if (!bound) return out.append("no se pudo enlazar\n").toString();
            boolean ok = done.await(3, java.util.concurrent.TimeUnit.SECONDS);
            out.append(ok ? "interfaz " + descriptor[0] : "enlazado, sin respuesta en 3 s").append('\n');
        } catch (SecurityException ex) {
            out.append("requiere permiso: ").append(ex.getMessage()).append('\n');
        } catch (InterruptedException ex) {
            out.append("interrumpido\n");
        } finally {
            try {
                c.unbindService(conn);
            } catch (RuntimeException ignored) {
                // Was never bound.
            }
        }
        return out.toString();
    }

    /** Connects to XTService the way the home screen does and reports the source it announces. */
    private static String probeXt(Context c) {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        TwCommand xt = new TwCommand(c, t -> done.countDown());
        xt.start();
        try {
            done.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            // Reported below.
        }
        String r = "  XTService: " + (xt.connected ? "conectado" : "sin conexión")
                + " · fuente actual " + xt.source + " (1 radio, 3 música USB, 8 Bluetooth, 9 video, otro = app)\n";
        xt.stop();
        return r;
    }

    /**
     * Connects to the CANBUS data service (see CarInfo), listens for 5 s and reads every getter,
     * so the report shows which vehicle data this car really provides.
     */
    private static String probeCarInfo(Context c) {
        if (!CarInfo.installed(c)) return "  servicio no instalado\n";
        StringBuilder sb = new StringBuilder();
        sb.append("  unidad del sistema (SPEED_UNIT): ").append(CarInfo.systemUsesMiles(c) ? "millas" : "km").append('\n');
        final CarInfo[] car = {null};
        final java.util.concurrent.CountDownLatch connected = new java.util.concurrent.CountDownLatch(1);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            car[0] = new CarInfo(c, x -> {
                if (x.connected) connected.countDown();
            });
            car[0].start(0);
            if (!car[0].bound()) connected.countDown();
        });
        try {
            connected.await(3, java.util.concurrent.TimeUnit.SECONDS);
            if (car[0] != null && car[0].connected) Thread.sleep(5000);
        } catch (InterruptedException ignored) {
            // Report whatever arrived.
        }
        if (car[0] == null || !car[0].connected) {
            sb.append("  no se pudo conectar\n");
        } else {
            sb.append("  -- consultas (valor actual; -1 = el auto no lo envía) --\n");
            sb.append(car[0].dumpGetters());
            final String[] got = {null};
            final java.util.concurrent.CountDownLatch read = new java.util.concurrent.CountDownLatch(1);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                got[0] = car[0].dumpReceived();
                read.countDown();
            });
            try {
                read.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Reported as missing.
            }
            sb.append("  -- avisos recibidos en 5 s --\n").append(got[0] == null ? "  (sin datos)\n" : got[0]);
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (car[0] != null) car[0].stop();
        });
        return sb.toString();
    }

    /** Asks the MCU for the illumination (lights) state, see TwLights. */
    private static String probeLights() {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        TwLights l = new TwLights(x -> done.countDown());
        l.start();
        if (l.error == null) {
            try {
                done.await(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Reported below.
            }
        }
        String r = "  luces (MCU 516): " + (l.error != null ? "error " + l.error
                : l.on == null ? "sin respuesta en 3 s" : l.on ? "encendidas" : "apagadas") + "\n";
        l.stop();
        return r;
    }

    /** Reads the MCU voltage the way the status bar does (see TwVoltage). */
    private static String probeVoltage() {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        TwVoltage v = new TwVoltage(x -> done.countDown());
        v.start();
        if (v.error == null) {
            try {
                done.await(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Reported below.
            }
        }
        String r = "  voltaje MCU: " + (v.error != null ? "error " + v.error
                : v.volts != null ? String.format(Locale.ROOT, "%.2f V", v.volts) : "sin respuesta en 3 s") + "\n";
        v.stop();
        return r;
    }

    /** Asks com.tw.bt for the current song (see TwBluetooth) and reports whether it answered. */
    private static String probeBtSong(Context c) {
        if (!TwBluetooth.installed(c)) return "  canción Bluetooth del estéreo: app no instalada\n";
        final Intent[] reply = {null};
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                reply[0] = intent;
                done.countDown();
            }
        };
        c.registerReceiver(r, new android.content.IntentFilter("com.unisound.intent.action.SEND_ID3_INFO"),
                Context.RECEIVER_EXPORTED);
        try {
            c.sendBroadcast(new Intent("com.unisound.intent.action.GET_ID3_INFO").setPackage(TwBluetooth.PACKAGE));
            done.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            // Reported as no answer.
        } finally {
            c.unregisterReceiver(r);
        }
        if (reply[0] == null) return "  canción Bluetooth del estéreo: sin respuesta en 2 s\n";
        Intent i = reply[0];
        return "  canción Bluetooth del estéreo: respondió · título " + (i.getStringExtra("musicTitle") != null ? "sí" : "vacío")
                + " · artista " + (i.getStringExtra("musicArtist") != null ? "sí" : "vacío")
                + " · progreso " + i.getIntExtra("musicProgress", -1) + " / " + i.getIntExtra("musicDuration", -1) + " ms\n";
    }

    /** Reports whether a vendor framework class exists and which public methods it declares. */
    private static String probeClass(String name) {
        StringBuilder out = new StringBuilder("  clase ").append(name).append(": ");
        try {
            Class<?> k = Class.forName(name);
            out.append("presente\n");
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
                out.append("    ").append(m.getReturnType().getSimpleName()).append(' ').append(m.getName()).append('(');
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) out.append(i == 0 ? "" : ", ").append(ps[i].getSimpleName());
                out.append(")\n");
            }
        } catch (ClassNotFoundException ex) {
            out.append("no existe\n");
        } catch (Throwable ex) {
            out.append("no accesible (").append(ex.getClass().getSimpleName()).append(")\n");
        }
        return out.toString();
    }

    /** Apps may enable some screens only at runtime (e.g. once a phone is connected). */
    private static String state(PackageManager pm, ActivityInfo ai) {
        int s = pm.getComponentEnabledSetting(new android.content.ComponentName(ai.packageName, ai.name));
        if (s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return "";
        if (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return ai.enabled ? "" : " [desactivada]";
        return " [desactivada por la app]";
    }

    private static void h(StringBuilder sb, String title) {
        sb.append("\n== ").append(title).append(" ==\n");
    }

    private static void kv(StringBuilder sb, String k, String v) {
        sb.append("  ").append(k).append(": ").append(v).append('\n');
    }

    private static String label(PackageManager pm, ApplicationInfo ai) {
        try {
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (RuntimeException ex) {
            return ai.packageName;
        }
    }

    private static String system(ApplicationInfo ai) {
        if (ai == null) return "";
        if ((ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return " [sistema actualizado]";
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? " [sistema]" : "";
    }

    private static String version(PackageManager pm, String pkg) {
        try {
            return " · v" + pm.getPackageInfo(pkg, 0).versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            return "";
        }
    }

    @SuppressWarnings("deprecation")
    private static String protection(PermissionInfo p) {
        int level = Build.VERSION.SDK_INT >= 28 ? p.getProtection() : p.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
        switch (level) {
            case PermissionInfo.PROTECTION_NORMAL:
                return "normal (cualquier app)";
            case PermissionInfo.PROTECTION_DANGEROUS:
                return "peligroso (se pide al usuario)";
            case PermissionInfo.PROTECTION_SIGNATURE:
                return "firma (solo apps del fabricante)";
            default:
                return "otro (" + level + ")";
        }
    }

    private static String state(int s) {
        switch (s) {
            case 0: return "desconectado";
            case 1: return "conectando";
            case 2: return "CONECTADO";
            case 3: return "desconectando";
            default: return String.valueOf(s);
        }
    }
}
