package com.jajua.gts;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The launcher's own settings. Every row does something real; nothing exposes risky Android options. */
public final class SettingsActivity extends Activity {
    private Prefs prefs;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        if (!AppCatalog.loaded()) AppCatalog.reload(this, this::build);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        Ui.init(this);
        Palette.load(prefs);
        Palette p = Palette.current;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.u(22), Ui.u(8), Ui.u(22), Ui.u(8));
        ImageView back = new ImageView(this);
        back.setImageDrawable(Ui.icon(this, R.drawable.ic_back, p.text));
        back.setPadding(Ui.u(12), Ui.u(12), Ui.u(12), Ui.u(12));
        back.setContentDescription("Volver");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.u(52), Ui.u(52)));
        TextView title = Ui.text(this, 22, Ui.medium, p.text);
        title.setText("Ajustes del launcher");
        title.setPadding(Ui.u(10), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ver = Ui.text(this, 16, Ui.medium, p.text);
        ver.setText("Versión " + installedVersion());
        ver.setPadding(Ui.u(16), Ui.u(8), Ui.u(16), Ui.u(8));
        ver.setBackground(Ui.rounded(Ui.withAlpha(0xFFFFFFFF, 0.08f), 18));
        ver.setOnClickListener(v -> versionTapped());
        header.addView(ver);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(68)));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.u(120), 0, Ui.u(120), Ui.u(40));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        section("Actualización");
        row("Versión instalada", getString(R.string.app_name) + " " + installedVersion(), null);
        githubRow = row("Buscar actualizaciones", githubStatus, this::checkGitHub);
        if (prefs.devMode()) {
            updateRow = row("Actualizar desde la PC", updateStatus, () -> {
                if (prefs.pcAddress().isEmpty()) Toast.makeText(this, "Primero escribe la dirección de la PC.", Toast.LENGTH_LONG).show();
                else PcLink.installUpdate(this, prefs.pcAddress());
            });
            row("Dirección de la PC", prefs.pcAddress().isEmpty() ? "Sin configurar (ej. 192.168.0.20:8765)" : prefs.pcAddress(),
                    () -> ask("IP y puerto de la PC", prefs.pcAddress(), prefs::setPcAddress));
            note("Modo desarrollador: enlace por Wi‑Fi con scripts/servidor-pc.py para enviar diagnósticos y "
                    + "probar versiones sin ADB ni root. Toca 7 veces la versión para ocultarlo.");
        }

        section("Botón de luces");
        row("Mostrar en el inicio", prefs.lightsShown() ? "Sí" : "No (oculto)", () -> {
            prefs.setLightsShown(!prefs.lightsShown());
            build();
        });
        row("Nombre del botón", prefs.lightsLabel(), () ->
                Pickers.dialog(this).setTitle("Nombre del botón").setItems(Prefs.LIGHT_LABELS, (d, w) -> {
                    prefs.setLightsLabel(w);
                    build();
                }).show());
        Targets.Target lights = AppCatalog.loaded() ? Targets.resolve(this, Role.LANTERN) : null;
        row("App que abre", lights == null ? "Ninguna detectada · toca para elegir"
                : lights.label + (lights.manual ? "" : " · automático"), () -> editRole(Role.LANTERN));
        note("Para cualquier app de luces (interiores, de chasis, LED…). Si tu auto no tiene, oculta el botón.");

        section("Accesos");
        for (Role role : Role.values()) {
            if (role == Role.LANTERN) continue; // Configured in "Botón de luces".
            Targets.Target t = AppCatalog.loaded() ? Targets.resolve(this, role) : null;
            String value;
            if (!AppCatalog.loaded()) value = "Cargando…";
            else if (t == null) value = "No encontrada";
            else value = t.label + (t.manual ? "" : " · automático")
                    + (t.component != null ? " · " + t.component.getShortClassName() : "");
            row(role.title, value, () -> editRole(role));
        }

        section("Apariencia");
        row("Tema oscuro", p.name, () -> {
            String[] names = new String[Palette.ALL.length];
            for (int i = 0; i < names.length; i++) names[i] = Palette.ALL[i].name;
            Pickers.dialog(this).setTitle("Tema oscuro").setItems(names, (d, w) -> {
                prefs.setTheme(Palette.ALL[w].key);
                build();
            }).show();
        });
        row("Color de acento", ColorPicker.name(Palette.accent) + " · resplandor " + Palette.glow + " %", () ->
                ColorPicker.show(this, Palette.accent, Palette.glow, (color, glow) -> {
                    prefs.setAccent(color, glow);
                    build();
                }));
        row("Iconos del cajón de aplicaciones", prefs.originalIcons() ? "Originales de cada app" : "Estilo del launcher", () -> {
            prefs.setOriginalIcons(!prefs.originalIcons());
            build();
        });
        row("Restaurar apariencia predeterminada", "Grafito con acento rojo", () -> {
            prefs.resetVisual();
            build();
        });

        section("Velocidad");
        String mode = prefs.speedMode();
        boolean canbus = CarInfo.installed(this);
        String modeName = SpeedMonitor.CANBUS.equals(mode) ? "Datos del vehículo (CANBUS)"
                : SpeedMonitor.GPS.equals(mode) ? "GPS (aproximada)"
                : SpeedMonitor.BROADCAST.equals(mode) ? "Datos de otra app"
                : SpeedMonitor.OFF.equals(mode) ? "Desactivada"
                : canbus ? "Automática · datos del vehículo o GPS" : "Automática · GPS";
        row("Fuente de la velocidad", modeName, this::pickSpeedMode);
        if (SpeedMonitor.AUTO.equals(mode) || SpeedMonitor.GPS.equals(mode)) {
            boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            row("Permiso de ubicación (GPS)", loc ? "Concedido" : "Sin permiso · toca para concederlo", loc ? null : () ->
                    requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 1));
        }
        String unit = prefs.speedUnit();
        String unitName = SpeedMonitor.UNIT_KMH.equals(unit) ? "km/h"
                : SpeedMonitor.UNIT_MPH.equals(unit) ? "mph"
                : "Como el sistema · ahora " + (CarInfo.systemUsesMiles(this) ? "mph" : "km/h");
        row("Unidad", unitName, () -> {
            String[] names = {"Como el sistema (recomendado)", "Kilómetros por hora (km/h)", "Millas por hora (mph)"};
            String[] units = {SpeedMonitor.UNIT_AUTO, SpeedMonitor.UNIT_KMH, SpeedMonitor.UNIT_MPH};
            Pickers.dialog(this).setTitle("Unidad de velocidad").setItems(names, (d, w) -> {
                prefs.setSpeedUnit(units[w]);
                build();
            }).show();
        });
        if (SpeedMonitor.BROADCAST.equals(mode)) {
            row("Acción de la difusión", prefs.speedAction().isEmpty() ? "Sin configurar" : prefs.speedAction(),
                    () -> ask("Acción (intent action)", prefs.speedAction(), v ->
                            prefs.setSpeed(mode, v, prefs.speedExtra(), prefs.speedInMs())));
            row("Nombre del dato", prefs.speedExtra().isEmpty() ? "Sin configurar" : prefs.speedExtra(),
                    () -> ask("Nombre del extra con la velocidad", prefs.speedExtra(), v ->
                            prefs.setSpeed(mode, prefs.speedAction(), v, prefs.speedInMs())));
            row("Unidad recibida", prefs.speedInMs() ? "m/s (se convierte a km/h)" : "km/h", () -> {
                prefs.setSpeed(mode, prefs.speedAction(), prefs.speedExtra(), !prefs.speedInMs());
                build();
            });
        }
        note("«Automática» usa la velocidad del auto (CANBUS) cuando la informa y, si no, la del GPS, "
                + "como el tema original; con la unidad del sistema (km o millas). "
                + "Es solo informativa: no sustituye al velocímetro.");

        section("Multimedia");
        boolean access = MediaMonitor.hasAccess(this);
        row("Información de reproducción", access ? "Activada" : "Desactivada · toca para activar", () ->
                Targets.start(this, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Acceso a notificaciones"));
        note("Permite mostrar canción, artista y controles de la app que esté sonando. "
                + "Busca «" + getString(R.string.app_name) + "» en la lista y actívalo.");

        section("Ayuda de conexión");
        row("Apple CarPlay", "Pasos para conectar el iPhone", () ->
                HelpDialog.show(this, HelpDialog.Kind.CARPLAY, null, null));
        row("Android Auto", "Pasos para conectar el teléfono", () ->
                HelpDialog.show(this, HelpDialog.Kind.ANDROID_AUTO, null, null));
        row("Bluetooth Audio", "Emparejar el teléfono", () ->
                HelpDialog.show(this, HelpDialog.Kind.BLUETOOTH, null, null));

        section("Sistema");
        row("Launcher predeterminado", "Elegir GTS Launcher o volver al original", this::openHomeSettings);
        row("Diagnóstico del sistema", "Analiza apps, permisos y fuentes de datos", () ->
                startActivity(new Intent(this, AuditActivity.class)));
        row("Ajustes de Android", "Configuración general del equipo", () ->
                Targets.start(this, new Intent(Settings.ACTION_SETTINGS), "Ajustes"));

        setContentView(root);
        if (prefs.devMode()) checkPcVersion();
    }

    private TextView githubRow;
    private String githubStatus = "Consulta la última versión publicada en GitHub";
    private int versionTaps;

    private void versionTapped() {
        if (++versionTaps < 7) return;
        versionTaps = 0;
        prefs.setDevMode(!prefs.devMode());
        Toast.makeText(this, prefs.devMode() ? "Modo desarrollador activado" : "Modo desarrollador desactivado",
                Toast.LENGTH_SHORT).show();
        build();
    }

    private void checkGitHub() {
        githubStatus = "Buscando…";
        if (githubRow != null) githubRow.setText(githubStatus);
        String mine = installedVersion();
        GitHubUpdate.latest(release -> {
            if (release == null) {
                githubStatus = "No se pudo consultar GitHub (¿sin internet?)";
            } else if (PcLink.newer(release.version, mine)) {
                githubStatus = "Nueva versión " + release.version + " disponible";
                Pickers.dialog(this).setTitle("Actualización disponible")
                        .setMessage("Hay una versión nueva (" + release.version + "). Tienes la " + mine
                                + ". ¿Descargarla e instalarla? Android pedirá confirmar.")
                        .setPositiveButton("Instalar", (d, w) -> PcLink.installFromUrl(this, release.apkUrl))
                        .setNegativeButton("Ahora no", null).show();
            } else {
                githubStatus = "Ya tienes la última versión (" + mine + ")";
            }
            if (githubRow != null) githubRow.setText(githubStatus);
        });
    }

    private TextView updateRow;
    private String updateStatus = "Buscando versión en la PC…";

    private String installedVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    /** Asks the PC which version it is serving, so a pending update is impossible to miss. */
    private void checkPcVersion() {
        if (prefs.pcAddress().isEmpty()) {
            setUpdateStatus("Configura la dirección de la PC");
            return;
        }
        String mine = installedVersion();
        PcLink.fetchVersion(prefs.pcAddress(), remote -> {
            if (remote == null) setUpdateStatus("PC no disponible (¿servidor apagado o sin Wi‑Fi?)");
            else if (PcLink.newer(remote, mine)) setUpdateStatus("Nueva versión " + remote + " disponible · toca para instalar");
            else setUpdateStatus("Ya tienes la última versión (" + mine + ")");
        });
    }

    private void setUpdateStatus(String text) {
        updateStatus = text;
        if (updateRow != null) updateRow.setText(text);
    }

    private void editRole(Role role) {
        Targets.Target t = Targets.resolve(this, role);
        java.util.List<String> options = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        options.add("Elegir aplicación");
        actions.add(() -> Pickers.pickApp(this, role.title, app -> {
            prefs.setTarget(role, app == null ? null : app.pkg, null);
            build();
        }));
        if (t != null) {
            options.add("Elegir pantalla específica de " + t.label);
            actions.add(() -> Pickers.pickActivity(this, t.pkg, cn -> {
                prefs.setTarget(role, t.pkg, cn == null ? null : cn.flattenToString());
                build();
            }));
            options.add("Probar ahora");
            actions.add(() -> Targets.launch(this, t));
        }
        if (prefs.targetPackage(role) != null) {
            options.add("Volver a la detección automática");
            actions.add(() -> {
                prefs.setTarget(role, null, null);
                build();
            });
        }
        Pickers.dialog(this).setTitle(role.title)
                .setItems(options.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .setNegativeButton("Cancelar", null).show();
    }

    private void pickSpeedMode() {
        String[] names = {"Automática (recomendado)", "Datos del vehículo (CANBUS)", "GPS (aproximada)",
                "Datos de otra app (difusión)", "Desactivada"};
        String[] modes = {SpeedMonitor.AUTO, SpeedMonitor.CANBUS, SpeedMonitor.GPS, SpeedMonitor.BROADCAST, SpeedMonitor.OFF};
        Pickers.dialog(this).setTitle("Fuente de la velocidad").setItems(names, (d, w) -> {
            prefs.setSpeed(modes[w], prefs.speedAction(), prefs.speedExtra(), prefs.speedInMs());
            if (SpeedMonitor.GPS.equals(modes[w])
                    && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
            build();
        }).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (results.length > 0 && results[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Sin permiso de ubicación la velocidad GPS no se puede mostrar.", Toast.LENGTH_LONG).show();
        }
        build();
    }

    private void openHomeSettings() {
        Intent home = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (home.resolveActivity(getPackageManager()) == null) {
            Pickers.dialog(this).setTitle("Launcher predeterminado")
                    .setMessage("Este firmware no tiene la pantalla estándar para elegir el launcher. Pulsa el botón "
                            + "Inicio: si Android pregunta qué app usar, elige GTS Launcher y «Siempre». Para volver al "
                            + "original, haz lo mismo eligiendo el launcher del sistema, o borra los valores "
                            + "predeterminados de GTS Launcher en Ajustes › Apps.")
                    .setPositiveButton("Entendido", null).show();
            return;
        }
        Targets.start(this, home, "Apps predeterminadas");
    }

    private void ask(String title, String current, java.util.function.Consumer<String> onValue) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setSingleLine(true);
        e.setText(current);
        e.setSelection(e.getText().length());
        Pickers.dialog(this).setTitle(title).setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    onValue.accept(e.getText().toString().trim());
                    build();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void section(String name) {
        TextView t = Ui.caption(this, name);
        t.setTextColor(Palette.accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.u(6), Ui.u(26), 0, Ui.u(10));
        list.addView(t, lp);
    }

    private TextView row(String title, String value, Runnable action) {
        Palette p = Palette.current;
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackground(Ui.panel(12));
        r.setPadding(Ui.u(22), Ui.u(16), Ui.u(22), Ui.u(16));
        TextView t = Ui.text(this, 18, Ui.regular, p.text);
        t.setText(title);
        r.addView(t);
        TextView v = Ui.text(this, 14, Ui.regular, p.subtext);
        v.setText(value);
        v.setPadding(0, Ui.u(6), 0, 0);
        r.addView(v);
        if (action != null) {
            r.setOnClickListener(x -> action.run());
            r.setFocusable(true);
            Ui.pressable(r);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.u(8);
        list.addView(r, lp);
        return v;
    }

    private void note(String text) {
        TextView t = Ui.text(this, 13, Ui.regular, Palette.current.dim);
        t.setSingleLine(false);
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.u(6), Ui.u(4), Ui.u(6), 0);
        list.addView(t, lp);
    }
}
