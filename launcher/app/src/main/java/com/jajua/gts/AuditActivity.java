package com.jajua.gts;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Runs the audit in the background and lets the user save it to Descargas to share it. */
public final class AuditActivity extends Activity {
    private TextView body;
    private String report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        Palette.load(Prefs.get(this));
        Palette p = Palette.current;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setPadding(Ui.u(30), Ui.u(14), Ui.u(30), Ui.u(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, 22, Ui.medium, p.text);
        title.setText("Diagnóstico del sistema");
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(HelpDialog.button(this, "Volver", false, this::finish));
        header.addView(HelpDialog.button(this, "Guardar", false, this::save));
        if (Prefs.get(this).devMode()) {
            header.addView(HelpDialog.button(this, "Apps a la PC", false, this::sendApps));
            header.addView(HelpDialog.button(this, "Enviar a la PC", true, this::send));
        }
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        body = new TextView(this);
        body.setTextColor(p.subtext);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.tf(13));
        body.setTextIsSelectable(true);
        body.setText("Analizando el sistema…");
        scroll.addView(body);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.topMargin = Ui.u(14);
        root.addView(scroll, sl);
        setContentView(root);

        Handler main = new Handler(Looper.getMainLooper());
        Runnable work = () -> new Thread(() -> {
            String r;
            try {
                r = Audit.run(getApplicationContext());
            } catch (RuntimeException ex) {
                r = "El diagnóstico falló: " + ex;
            }
            String done = r;
            main.post(() -> {
                report = done;
                body.setText(done);
            });
        }, "audit").start();
        if (AppCatalog.loaded()) work.run();
        else AppCatalog.reload(this, work::run);
    }

    private void send() {
        if (report == null) return;
        Prefs prefs = Prefs.get(this);
        android.widget.EditText e = new android.widget.EditText(this);
        e.setSingleLine(true);
        e.setText(prefs.pcAddress());
        e.setHint("ej. 192.168.0.20:8765");
        e.setSelection(e.getText().length());
        Pickers.dialog(this).setTitle("IP y puerto de la PC").setView(e)
                .setPositiveButton("Enviar", (d, w) -> {
                    String address = e.getText().toString().trim();
                    prefs.setPcAddress(address);
                    PcLink.sendReport(this, address, report);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    /** Vendor apps that may hold the vehicle-speed source, found by the audit on the TS10. */
    private static final String[] ANALYSIS_CANDIDATES = {
        "com.tw.carinfoservice", "com.dofun.variety", "com.tw.core", "com.tw.service", "com.tw.radio", "com.tw.bt",
        "com.zjinnova.zlink", "com.android.systemui", "com.tw.service.xt",
    };

    private void sendApps() {
        Prefs prefs = Prefs.get(this);
        if (prefs.pcAddress().isEmpty()) {
            Toast.makeText(this, "Primero envía el diagnóstico para guardar la dirección de la PC.", Toast.LENGTH_LONG).show();
            return;
        }
        java.util.List<String> pkgs = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (String pkg : ANALYSIS_CANDIDATES) {
            try {
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                long mb = new java.io.File(ai.sourceDir).length() >> 20;
                pkgs.add(pkg);
                labels.add(getPackageManager().getApplicationLabel(ai) + " · " + pkg + " · " + Math.max(1, mb) + " MB");
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                // Not on this unit.
            }
        }
        boolean[] checked = new boolean[pkgs.size()];
        java.util.Arrays.fill(checked, true);
        Pickers.dialog(this).setTitle("Enviar apps a la PC para análisis")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("Enviar", (d, w) -> {
                    java.util.List<String> chosen = new java.util.ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(pkgs.get(i));
                    Toast.makeText(this, "Enviando " + chosen.size() + " apps…", Toast.LENGTH_SHORT).show();
                    PcLink.sendApks(this, prefs.pcAddress(), chosen);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    @android.annotation.SuppressLint("NewApi") // Android 8–9 return early with a file in the app's folder.
    private void save() {
        if (report == null) return;
        String name = "gts-diagnostico-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date()) + ".txt";
        if (android.os.Build.VERSION.SDK_INT < 29) {
            // Before Android 10 there is no MediaStore.Downloads; the app's own Download folder needs no permission.
            java.io.File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            java.io.File f = new java.io.File(dir, name);
            try (OutputStream out = new java.io.FileOutputStream(f)) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Guardado en " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(this, "No se pudo guardar: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        ContentResolver cr = getContentResolver();
        try {
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new java.io.IOException("insert");
            try (OutputStream out = cr.openOutputStream(uri)) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Guardado en Descargas/" + name, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "No se pudo guardar: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
