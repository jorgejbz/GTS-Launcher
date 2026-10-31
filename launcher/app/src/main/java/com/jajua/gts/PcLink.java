package com.jajua.gts;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Wi-Fi link to the development PC (scripts/servidor-pc.py), used because the TS10 has no ADB
 * without root. It only talks to the address the user typed, on the local network:
 *  - POST /reporte  sends the diagnostic text.
 *  - GET  /gts.apk  downloads a new build and hands it to Android's installer, which asks the
 *    user to confirm. Nothing is installed silently.
 */
final class PcLink {
    private static final Handler main = new Handler(Looper.getMainLooper());

    private PcLink() {}

    static String base(String address) {
        String a = address.trim();
        if (!a.startsWith("http://")) a = "http://" + a;
        if (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        return a;
    }

    static void sendReport(Activity a, String address, String report) {
        new Thread(() -> {
            String msg;
            try {
                HttpURLConnection c = open(base(address) + "/reporte");
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(report.getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                c.disconnect();
                msg = code == 200 ? "Diagnóstico enviado a la PC." : "La PC respondió con error " + code + ".";
            } catch (Exception ex) {
                msg = "No se pudo conectar con la PC: " + ex.getMessage();
            }
            toast(a, msg);
        }, "pc-report").start();
    }

    /**
     * Sends the installed APK files of the given apps to the PC, for offline analysis of how the
     * vendor apps get data such as vehicle speed. Only reads the APKs; changes nothing on them.
     */
    static void sendApks(Activity a, String address, java.util.List<String> packages) {
        new Thread(() -> {
            int ok = 0;
            String error = null;
            for (String pkg : packages) {
                try {
                    android.content.pm.ApplicationInfo ai = a.getPackageManager().getApplicationInfo(pkg, 0);
                    java.util.List<String> files = new java.util.ArrayList<>();
                    files.add(ai.sourceDir);
                    if (ai.splitSourceDirs != null) java.util.Collections.addAll(files, ai.splitSourceDirs);
                    for (String path : files) {
                        java.io.File f = new java.io.File(path);
                        String name = pkg + (files.size() > 1 ? "__" + f.getName() : ".apk");
                        HttpURLConnection c = open(base(address) + "/apk?nombre=" + java.net.URLEncoder.encode(name, "UTF-8"));
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setFixedLengthStreamingMode(f.length());
                        try (InputStream in = new java.io.FileInputStream(f); OutputStream out = c.getOutputStream()) {
                            byte[] buf = new byte[64 * 1024];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        if (c.getResponseCode() != 200) throw new java.io.IOException("la PC respondió " + c.getResponseCode());
                        c.disconnect();
                    }
                    ok++;
                } catch (Exception ex) {
                    error = pkg + ": " + ex.getMessage();
                }
            }
            toast(a, error == null ? ok + " apps enviadas a la PC." : "Enviadas " + ok + "; falló " + error);
        }, "pc-apks").start();
    }

    /** Version the PC is serving (from GET /version), or null when the PC cannot be reached. */
    static void fetchVersion(String address, java.util.function.Consumer<String> onResult) {
        new Thread(() -> {
            String v = null;
            try {
                HttpURLConnection c = open(base(address) + "/version");
                c.setConnectTimeout(2500);
                if (c.getResponseCode() == 200) {
                    try (InputStream in = c.getInputStream()) {
                        byte[] buf = new byte[64];
                        int n = in.read(buf);
                        if (n > 0) v = new String(buf, 0, n, StandardCharsets.UTF_8).trim();
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {
                v = null;
            }
            String result = v == null || v.isEmpty() ? null : v;
            main.post(() -> onResult.accept(result));
        }, "pc-version").start();
    }

    /**
     * True when dotted version a (e.g. 0.12.0) is greater than b. A pre-release suffix ("-dev")
     * counts as older than the same numbers without it, so 1.1.0 is offered over 1.1.0-dev.
     */
    static boolean newer(String a, String b) {
        String[] x = a.split("-", 2)[0].split("\\."), y = b.split("-", 2)[0].split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0;
            int q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p > q;
        }
        return !a.contains("-") && b.contains("-");
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    static void installUpdate(Activity a, String address) {
        installFromUrl(a, base(address) + "/gts.apk");
    }

    /**
     * Downloads an APK of this app and hands it to Android's installer, which asks the user to
     * confirm. Used for the PC link and for GitHub releases.
     */
    static void installFromUrl(Activity a, String url) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            toast(a, "Permite «Instalar apps desconocidas» para GTS Launcher y vuelve a intentarlo.");
            Targets.start(a, new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + a.getPackageName())), "Instalar apps desconocidas");
            return;
        }
        toast(a, "Descargando actualización…");
        new Thread(() -> {
            PackageInstaller.Session session = null;
            try {
                HttpURLConnection c = open(url);
                c.setReadTimeout(60000);
                if (c.getResponseCode() != 200) throw new java.io.IOException("el servidor respondió " + c.getResponseCode());
                long size = c.getContentLengthLong();
                PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(a.getPackageName());
                int id = pi.createSession(params);
                session = pi.openSession(id);
                long total = 0;
                try (InputStream in = c.getInputStream();
                        OutputStream out = session.openWrite("gts.apk", 0, size)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                    session.fsync(out);
                } catch (java.io.IOException ex) {
                    throw new java.io.IOException("descarga incompleta (" + total + " de " + size + " bytes)", ex);
                }
                c.disconnect();
                Intent status = new Intent(a, InstallReceiver.class);
                // Mutable so the installer can attach its result (the flag exists from Android 12).
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                session.commit(PendingIntent.getBroadcast(a, id, status, flags).getIntentSender());
                session.close();
            } catch (Exception ex) {
                if (session != null) session.abandon();
                android.util.Log.w("GTS", "update failed", ex);
                toast(a, "No se pudo actualizar: " + ex.getMessage());
            }
        }, "app-update").start();
    }

    private static HttpURLConnection open(String url) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Connection", "close");
        return c;
    }

    private static void toast(Activity a, String msg) {
        main.post(() -> Toast.makeText(a.getApplicationContext(), msg, Toast.LENGTH_LONG).show());
    }
}
