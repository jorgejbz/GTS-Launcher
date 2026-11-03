package com.jajua.gts;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The launcher's own ambient-lights panel, opened from the dock button instead of the Magic Lantern
 * app: power, colour and brightness for the RGB controller over BLE (see {@link AmbientLights}).
 *
 * <p>Colour and the launcher accent are one and the same: choosing a colour here recolours the whole
 * interface and sends it to the lights at once, and choosing the accent in Ajustes does the same.
 */
public final class LightsActivity extends Activity {
    private static final int REQ_LIGHTS = 3;
    private static final int REQ_MIC = 4;
    private static final int REQ_BT_ENABLE = 5;

    // Saturated hues the RGB strip reproduces faithfully, so the swatch matches the light. Pastels
    // and "warm/cool white" are left out on purpose (an RGB LED can't render them like the screen);
    // the Blanco section drives true white instead.
    private static final int[] SWATCHES = {
            0xFFFF0000, 0xFFFF6B00, 0xFFFF9500, 0xFFFFD400,
            0xFFAEFF00, 0xFF35FF00, 0xFF00E676, 0xFF00E5CC,
            0xFF00C8FF, 0xFF2D9BFF, 0xFF2D5BFF, 0xFF6B2DFF,
            0xFFB02DFF, 0xFFFF2DD0, 0xFFFF2D6B, 0xFFFFFFFF};

    private Prefs prefs;
    private LinearLayout list;
    private final Handler ui = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Keep the link up while the panel is open so commands take effect immediately.
        if (prefs.lightsAddress() != null) LightsService.sync(this);
        build();
        ui.postDelayed(statusTick, 1200);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(statusTick);
        // Don't keep the microphone open once the panel isn't in front.
        AmbientLights.get(this).stopMusic();
        super.onPause();
    }

    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            if (statusView != null) statusView.setText(statusText());
            ui.postDelayed(this, 1500);
        }
    };

    private TextView statusView;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(this);
    }

    private void build() {
        Ui.init(this, true);
        Ui.hideSystemBars(this);
        Palette.load(prefs);
        Palette p = Palette.current;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);

        // Header.
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
        title.setText(prefs.lightsLabel());
        title.setPadding(Ui.u(10), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(68)));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.u(44), 0, Ui.u(44), Ui.u(44));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        boolean hasDev = prefs.lightsAddress() != null;

        // Connection line (quiet, like a status caption).
        statusLine();

        if (!hasDev) {
            note("Elige el controlador de tus luces para empezar. Mantén el Bluetooth encendido, las luces "
                    + "cerca y cierra las apps que las estén usando.");
            linkRow("Buscar controlador", this::pickLights);
            linkRow("Abrir la app original", this::openOriginalApp);
            setContentView(root);
            return;
        }

        boolean on = prefs.lightsOn();

        // Power — the one big control.
        powerTile(on);

        // Colour: a true preview of what the strip shows, then the swatches.
        caption("Color");
        heroColor();
        swatches();
        linkRow("Más colores…", () ->
                ColorPicker.show(this, prefs.accentColor(), prefs.glow(), (color, glow) -> {
                    prefs.setAccent(color, glow);
                    prefs.setLightsColorMode();
                    AmbientLights.get(this).pushAccentIfLinked();
                    build();
                }));

        // White (warm/cold).
        caption("Blanco");
        whiteControls();

        // Brightness.
        caption("Brillo");
        brightnessBar();

        // Dynamic effects — a single swipeable row, not a wall of buttons.
        caption("Estilos");
        chipScroller(Lights.EFFECTS, Prefs.MODE_EFFECT.equals(prefs.lightsMode()) ? prefs.lightsEffect() : -1, it -> {
            prefs.setLightsOn(true);
            prefs.setLightsEffect(it.id);
            AmbientLights al = AmbientLights.get(this);
            al.setMode(it.id);
            al.setModeSpeed(prefs.lightsSpeed());
            build();
        });
        speedBar();

        // Scenes.
        caption("Escenas");
        chipScroller(Lights.SCENES, Prefs.MODE_SCENE.equals(prefs.lightsMode()) ? prefs.lightsScene() : -1, it -> {
            prefs.setLightsOn(true);
            prefs.setLightsScene(it.id);
            AmbientLights.get(this).setScene(it.id);
            build();
        });

        // Music (reacts to sound from the microphone).
        caption("Música");
        boolean music = AmbientLights.get(this).isMusic();
        togglePanel(music ? "Música activada" : "Reaccionar al sonido",
                music ? "Las luces siguen el ritmo · toca para detener"
                        : "Laten con el sonido del ambiente (micrófono)", music, this::toggleMusic);
        if (music) {
            boolean rainbow = prefs.lightsMusicRainbow();
            togglePanel(rainbow ? "Color: arcoíris" : "Color: tu acento",
                    rainbow ? "Recorre el arcoíris con el ritmo" : "Late con tu color actual", rainbow,
                    () -> {
                        prefs.setLightsMusicRainbow(!rainbow);
                        AmbientLights.get(this).setMusicRainbow(!rainbow);
                        build();
                    });
            sensitivityBar();
        }

        setContentView(root);
    }

    private String statusText() {
        if (prefs.lightsAddress() == null) return "Ninguno · toca para buscar";
        AmbientLights al = AmbientLights.get(this);
        if (al.bluetoothOff()) return "Bluetooth apagado · enciéndelo";
        String name = prefs.lightsDeviceName();
        if (name.isEmpty()) name = "Controlador";
        return (al.isReady() ? "Conectado · " : "Conectando… · ") + name;
    }

    private void swatches() {
        int cols = 8;
        int current = prefs.accentColor() | 0xFF000000;
        LinearLayout row = null;
        for (int i = 0; i < SWATCHES.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = Ui.u(10);
                list.addView(row, rlp);
            }
            final int color = SWATCHES[i] | 0xFF000000;
            // A fixed-size round swatch centred in an equal-width cell, so it stays a true circle
            // and evenly spaced on any screen width (the head unit is wide: 1280x720).
            LinearLayout cell = new LinearLayout(this);
            cell.setGravity(Gravity.CENTER);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            if (color == current) g.setStroke(Ui.u(3), Palette.current.text);
            dot.setBackground(g);
            dot.setLayoutParams(new LinearLayout.LayoutParams(Ui.u(46), Ui.u(46)));
            Ui.pressable(dot);
            dot.setOnClickListener(v -> applyColor(color));
            cell.addView(dot);
            row.addView(cell);
        }
        // Keep the last row's cells the same width if it isn't full.
        int rem = SWATCHES.length % cols;
        if (rem != 0 && row != null) {
            for (int k = rem; k < cols; k++) {
                View filler = new View(this);
                filler.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
                row.addView(filler);
            }
        }
    }

    /** Apply a solid colour to the interface accent and the lights, turning them on if needed. */
    private void applyColor(int color) {
        prefs.setAccent(color, prefs.glow());
        prefs.setLightsColorMode();
        AmbientLights al = AmbientLights.get(this);
        if (!prefs.lightsOn()) {
            prefs.setLightsOn(true);
            al.setOn(true);
        }
        al.pushAccentIfLinked();
        build();
    }

    /** Warm/cold white, with three quick presets and a slider. */
    private void whiteControls() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.u(8);
        list.addView(row, rlp);
        int[] colds = {0, 50, 100};
        String[] labels = {"Cálido", "Neutro", "Frío"};
        for (int i = 0; i < colds.length; i++) {
            final int cold = colds[i];
            TextView chip = Ui.text(this, 16, Ui.medium, Palette.current.text);
            chip.setText(labels[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(Ui.panel(14));
            chip.setPadding(Ui.u(12), Ui.u(14), Ui.u(12), Ui.u(14));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMargins(Ui.u(6), 0, Ui.u(6), 0);
            chip.setLayoutParams(clp);
            Ui.pressable(chip);
            chip.setOnClickListener(v -> applyWhite(cold));
            row.addView(chip);
        }
        slider("Tono del blanco (cálido → frío)", 50, this::applyWhite);
    }

    private void applyWhite(int cold) {
        AmbientLights al = AmbientLights.get(this);
        if (!prefs.lightsOn()) {
            prefs.setLightsOn(true);
            al.setOn(true);
        }
        al.setWhite(cold);
    }

    private void brightnessBar() {
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(prefs.lightsBrightness());
        bar.getProgressDrawable().setColorFilter(Palette.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        bar.getThumb().setColorFilter(Palette.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser) {
                    prefs.setLightsBrightness(value);
                    AmbientLights.get(LightsActivity.this).setBrightness(value);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.u(8), Ui.u(10), Ui.u(8), Ui.u(10));
        list.addView(bar, lp);
    }

    private void speedBar() {
        slider("Velocidad del efecto", prefs.lightsSpeed(), value -> {
            prefs.setLightsSpeed(value);
            AmbientLights.get(this).setModeSpeed(prefs.lightsSpeed());
        });
    }

    private void sensitivityBar() {
        slider("Sensibilidad del micrófono", prefs.lightsMusicSensitivity(), value -> {
            prefs.setLightsMusicSensitivity(Math.max(1, value));
            AmbientLights.get(this).setMusicSensitivity(prefs.lightsMusicSensitivity());
        });
    }

    /** A labelled 0..100 seek bar in the accent colour. */
    private void slider(String label, int initial, java.util.function.IntConsumer onChange) {
        TextView t = Ui.text(this, 14, Ui.regular, Palette.current.subtext);
        t.setText(label);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.setMargins(Ui.u(8), Ui.u(12), Ui.u(8), 0);
        list.addView(t, ll);
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(initial);
        bar.getProgressDrawable().setColorFilter(Palette.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        bar.getThumb().setColorFilter(Palette.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser) onChange.accept(value);
            }

            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.u(8), Ui.u(2), Ui.u(8), Ui.u(6));
        list.addView(bar, lp);
    }

    /** A single swipeable row of chips; the active one is highlighted in the accent. */
    private void chipScroller(Lights.Item[] items, int selectedId, java.util.function.Consumer<Lights.Item> onPick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout rowL = new LinearLayout(this);
        rowL.setOrientation(LinearLayout.HORIZONTAL);
        for (final Lights.Item it : items) {
            TextView chip = Ui.text(this, 16, Ui.medium, Palette.current.text);
            chip.setText(it.name);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(it.id == selectedId ? accentPanel(14) : Ui.panel(14));
            chip.setPadding(Ui.u(18), Ui.u(14), Ui.u(18), Ui.u(14));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = Ui.u(10);
            chip.setLayoutParams(clp);
            Ui.pressable(chip);
            chip.setOnClickListener(v -> onPick.accept(it));
            rowL.addView(chip);
        }
        hs.addView(rowL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.u(8);
        list.addView(hs, lp);
    }

    // --- Launcher-style building blocks ----------------------------------------------------

    /** A faintly accent-tinted rounded panel, for the "on"/selected state. */
    private android.graphics.drawable.GradientDrawable accentPanel(float radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Ui.withAlpha(Palette.accent, 0.14f));
        g.setCornerRadius(Ui.uf(radius));
        g.setStroke(Math.max(1, Ui.u(1)), Ui.withAlpha(Palette.accent, 0.5f));
        return g;
    }

    /** Quiet connection line with a status dot, tappable to manage the controller. */
    private void statusLine() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.u(6), Ui.u(10), Ui.u(6), Ui.u(10));
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(AmbientLights.get(this).isReady() ? 0xFF35C759 : Palette.current.subtext);
        dot.setBackground(g);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.u(10), Ui.u(10));
        dl.rightMargin = Ui.u(10);
        r.addView(dot, dl);
        statusView = Ui.text(this, 15, Ui.regular, Palette.current.subtext);
        statusView.setText(statusText());
        r.addView(statusView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (prefs.lightsAddress() != null) {
            TextView change = Ui.text(this, 15, Ui.medium, Palette.accent);
            change.setText("Cambiar");
            r.addView(change);
        }
        Ui.pressable(r);
        r.setOnClickListener(v -> pickLights());
        list.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** The one big control: a panel that lights up in the accent when on. */
    private void powerTile(boolean on) {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackground(on ? accentPanel(18) : Ui.panel(18));
        t.setPadding(Ui.u(22), Ui.u(20), Ui.u(22), Ui.u(20));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(Ui.icon(this, R.drawable.ic_lantern, on ? Palette.accent : Palette.current.subtext));
        t.addView(ic, new LinearLayout.LayoutParams(Ui.u(40), Ui.u(40)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.u(16), 0, 0, 0);
        TextView title = Ui.text(this, 20, Ui.medium, p().text);
        title.setText(on ? "Encendidas" : "Apagadas");
        col.addView(title);
        TextView sub = Ui.text(this, 14, Ui.regular, p().subtext);
        sub.setText(on ? "Toca para apagar" : "Toca para encender");
        sub.setPadding(0, Ui.u(3), 0, 0);
        col.addView(sub);
        t.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.pressable(t);
        t.setOnClickListener(v -> {
            boolean next = !prefs.lightsOn();
            prefs.setLightsOn(next);
            AmbientLights al = AmbientLights.get(this);
            al.setOn(next);
            if (next) al.pushAccentIfLinked();
            build();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.u(12);
        list.addView(t, lp);
    }

    /** An honest preview of the exact colour sent to the strip (so the screen matches the light). */
    private void heroColor() {
        View hero = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(prefs.accentColor() | 0xFF000000);
        g.setCornerRadius(Ui.uf(16));
        g.setStroke(Math.max(1, Ui.u(1)), Ui.withAlpha(0xFFFFFFFF, 0.16f));
        hero.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(60));
        lp.topMargin = Ui.u(8);
        lp.bottomMargin = Ui.u(2);
        list.addView(hero, lp);
    }

    /** A toggle shown as a panel, accent-tinted when active. */
    private void togglePanel(String title, String sub, boolean active, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackground(active ? accentPanel(16) : Ui.panel(16));
        r.setPadding(Ui.u(20), Ui.u(16), Ui.u(20), Ui.u(16));
        TextView t = Ui.text(this, 18, Ui.medium, p().text);
        t.setText(title);
        r.addView(t);
        TextView s = Ui.text(this, 14, Ui.regular, p().subtext);
        s.setText(sub);
        s.setPadding(0, Ui.u(3), 0, 0);
        r.addView(s);
        Ui.pressable(r);
        r.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.u(10);
        list.addView(r, lp);
    }

    /** A quiet accent-coloured text link. */
    private void linkRow(String label, Runnable action) {
        TextView t = Ui.text(this, 16, Ui.medium, Palette.accent);
        t.setText(label);
        t.setPadding(Ui.u(6), Ui.u(14), Ui.u(6), Ui.u(14));
        Ui.pressable(t);
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.u(4);
        list.addView(t, lp);
    }

    private Palette p() {
        return Palette.current;
    }

    private void toggleMusic() {
        AmbientLights al = AmbientLights.get(this);
        if (al.isMusic()) {
            al.stopMusic();
            build();
            return;
        }
        if (!al.canRecord()) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        prefs.setLightsOn(true);
        al.setOn(true);
        al.startMusic();
        build();
    }

    // --- Pick / scan controller -----------------------------------------------------------

    private void pickLights() {
        AmbientLights al = AmbientLights.get(this);
        if (!al.canScan()) {
            ensurePermissions();
            return;
        }
        if (al.bluetoothOff()) {
            enableBluetooth();
            return;
        }
        if (prefs.lightsAddress() != null) {
            // Already have one: offer to change or forget.
            Pickers.dialog(this).setTitle(prefs.lightsDeviceName().isEmpty() ? "Controlador" : prefs.lightsDeviceName())
                    .setItems(new String[] {"Buscar otro controlador", "Olvidar este controlador"}, (d, w) -> {
                        if (w == 0) scanAndPick();
                        else {
                            prefs.setLightsDevice(null, null);
                            LightsService.sync(this);
                            build();
                        }
                    })
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        scanAndPick();
    }

    private void scanAndPick() {
        AmbientLights al = AmbientLights.get(this);
        // Permissions first (needed to even ask the system to turn Bluetooth on from Android 12).
        if (!al.canScan()) {
            ensurePermissions();
            return;
        }
        // The LED controller uses Android's own Bluetooth (not the stereo's phone/hands-free module),
        // which is often off on a head unit. Ask the system to turn it on instead of just warning.
        if (al.bluetoothOff()) {
            enableBluetooth();
            return;
        }
        android.app.AlertDialog searching = Pickers.dialog(this).setTitle("Buscando controladores…")
                .setMessage("Asegúrate de que las luces estén encendidas y cerca.")
                .setNegativeButton("Cancelar", (d, w) -> al.stopScan()).create();
        searching.show();
        final java.util.LinkedHashMap<String, String> latest = new java.util.LinkedHashMap<>();
        al.scan(devices -> {
            latest.clear();
            latest.putAll(devices);
        });
        ui.postDelayed(() -> {
            if (isFinishing()) return;
            al.stopScan();
            searching.dismiss();
            if (latest.isEmpty()) {
                Pickers.dialog(this).setTitle("Sin resultados")
                        .setMessage("No se encontró ningún controlador. Revisa que las luces estén encendidas, el "
                                + "Bluetooth activo y que ninguna otra app las tenga conectadas.")
                        .setPositiveButton("Reintentar", (d, w) -> scanAndPick())
                        .setNegativeButton("Cerrar", null).show();
                return;
            }
            String[] addrs = latest.keySet().toArray(new String[0]);
            String[] names = latest.values().toArray(new String[0]);
            Pickers.dialog(this).setTitle("Elige el controlador").setItems(names, (d, w) -> {
                prefs.setLightsDevice(addrs[w], names[w]);
                LightsService.sync(this);
                AmbientLights.get(this).pushAccentIfLinked();
                build();
            }).setNegativeButton("Cancelar", null).show();
        }, 8500);
    }

    /** Ask the system to turn Android's Bluetooth on, then continue scanning. */
    private void enableBluetooth() {
        try {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_BT_ENABLE);
        } catch (Exception e) {
            Toast.makeText(this, "Enciende el Bluetooth de Android para controlar las luces "
                    + "(es distinto al Bluetooth del estéreo para el teléfono).", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BT_ENABLE) {
            if (!AmbientLights.get(this).bluetoothOff()) {
                ui.postDelayed(this::scanAndPick, 600); // give the adapter a moment to come up
            } else {
                Toast.makeText(this, "El Bluetooth sigue apagado; no se pueden buscar las luces.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void ensurePermissions() {
        java.util.List<String> req = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            req.add(Manifest.permission.BLUETOOTH_SCAN);
            req.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            req.add(Manifest.permission.ACCESS_FINE_LOCATION);
            req.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        requestPermissions(req.toArray(new String[0]), REQ_LIGHTS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        boolean granted = results.length > 0;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
        if (requestCode == REQ_LIGHTS && granted) scanAndPick();
        else if (requestCode == REQ_LIGHTS) {
            Toast.makeText(this, "Sin el permiso de Bluetooth no se pueden controlar las luces.", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_MIC && granted) {
            prefs.setLightsOn(true);
            AmbientLights al = AmbientLights.get(this);
            al.setOn(true);
            al.startMusic();
        } else if (requestCode == REQ_MIC) {
            Toast.makeText(this, "Sin el permiso de micrófono la música no puede reaccionar al sonido.", Toast.LENGTH_LONG).show();
        }
        build();
    }

    private void openOriginalApp() {
        Targets.Target t = Targets.resolve(this, Role.LANTERN);
        if (t != null) Targets.launch(this, t);
        else Toast.makeText(this, "No hay ninguna app de luces instalada.", Toast.LENGTH_LONG).show();
    }

    // --- Caption / note helpers ------------------------------------------------------------

    private void caption(String s) {
        TextView t = Ui.caption(this, s);
        t.setTextColor(Palette.accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.u(6), Ui.u(24), 0, Ui.u(8));
        list.addView(t, lp);
    }

    private void note(String s) {
        TextView t = Ui.text(this, 14, Ui.regular, Palette.current.subtext);
        t.setText(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.u(6), Ui.u(12), Ui.u(6), 0);
        list.addView(t, lp);
    }
}
