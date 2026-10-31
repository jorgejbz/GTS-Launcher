package com.jajua.gts;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Thin status strip: what is playing on the left, the screen title in the middle, and on the right
 * only indicators the system can actually report (a missing state is hidden, never faked).
 */
final class TopBar extends LinearLayout {
    private final Context c;
    /** Green low-beam tell-tale, as on the instrument cluster. */
    static final int LIGHTS_GREEN = 0xFF3DDC84;

    private final ImageView lights;
    private final ImageView note;
    private final TextView nowPlaying;
    private final ImageView phone;
    private final ImageView bluetooth;
    private final ImageView wifi;
    private final TextView volts;

    TopBar(Context c, Runnable onMedia, Runnable onBluetooth, Runnable onWifi) {
        super(c);
        this.c = c;
        Palette p = Palette.current;
        setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(c);
        left.setGravity(Gravity.CENTER_VERTICAL);
        lights = new ImageView(c);
        lights.setImageDrawable(Ui.icon(c, R.drawable.ic_headlight, LIGHTS_GREEN));
        lights.setContentDescription("Luces del auto encendidas");
        lights.setPadding(0, Ui.u(14), Ui.u(14), Ui.u(14));
        lights.setVisibility(GONE);
        left.addView(lights, new LayoutParams(Ui.u(26 + 14), LayoutParams.MATCH_PARENT));
        note = icon(R.drawable.ic_music, p.subtext, 18);
        left.addView(note);
        nowPlaying = Ui.text(c, 15, Ui.regular, p.subtext);
        nowPlaying.setPadding(Ui.u(8), 0, Ui.u(12), 0);
        left.addView(nowPlaying);
        left.setOnClickListener(v -> onMedia.run());
        addView(left, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        TextView title = Ui.text(c, 19, Ui.medium, p.text);
        title.setText("Inicio");
        title.setLetterSpacing(0.04f);
        addView(title);

        LinearLayout right = new LinearLayout(c);
        right.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        phone = icon(R.drawable.ic_phone, p.text, 20);
        bluetooth = icon(R.drawable.ic_bluetooth, p.text, 20);
        bluetooth.setOnClickListener(v -> onBluetooth.run());
        wifi = icon(R.drawable.ic_wifi, p.text, 20);
        wifi.setOnClickListener(v -> onWifi.run());
        volts = Ui.text(c, 16, Ui.regular, p.subtext);
        volts.setPadding(0, 0, Ui.u(8), 0);
        volts.setVisibility(GONE);
        right.addView(volts);
        for (ImageView v : new ImageView[] {phone, bluetooth, wifi}) {
            v.setPadding(Ui.u(9), Ui.u(14), Ui.u(9), Ui.u(14));
            right.addView(v, new LayoutParams(Ui.u(38), LayoutParams.MATCH_PARENT));
        }
        // No clock here: the vehicle panel already shows the time on the home screen.
        right.setPadding(0, 0, Ui.u(4), 0);
        addView(right, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    /** null or false hides the indicator (unknown on head units without the MCU signal). */
    void setLights(Boolean on) {
        lights.setVisibility(Boolean.TRUE.equals(on) ? VISIBLE : GONE);
    }

    void setNowPlaying(String text) {
        boolean show = text != null && !text.isEmpty();
        note.setVisibility(show ? VISIBLE : INVISIBLE);
        nowPlaying.setText(show ? text : "");
    }

    void setStatus(StatusMonitor s) {
        Palette p = Palette.current;
        phone.setVisibility(s.phoneConnected ? VISIBLE : GONE);
        phone.setContentDescription(s.phoneName != null ? "Teléfono conectado: " + s.phoneName : "Teléfono conectado");
        // On the TS10 the phone connects through the head unit's own Bluetooth module (com.tw.bt),
        // while Android's adapter stays off. "Off" therefore says nothing about the phone, so the
        // icon only appears when Android's adapter is actually on.
        boolean btOn = Boolean.TRUE.equals(s.bluetoothOn);
        bluetooth.setVisibility(btOn ? VISIBLE : GONE);
        if (btOn) {
            bluetooth.setImageDrawable(Ui.icon(c, R.drawable.ic_bluetooth, p.text));
            bluetooth.setContentDescription("Bluetooth de Android activado");
        }
        wifi.setImageDrawable(Ui.icon(c, s.wifiConnected ? R.drawable.ic_wifi : R.drawable.ic_wifi_off,
                s.wifiConnected ? p.text : p.dim));
        wifi.setContentDescription(s.wifiConnected ? "Wi-Fi conectado" : "Sin Wi-Fi");
        volts.setVisibility(s.volts != null ? VISIBLE : GONE);
        if (s.volts != null) {
            volts.setText(String.format(java.util.Locale.ROOT, "%.1f V", s.volts));
            volts.setContentDescription("Batería del auto " + volts.getText());
        }
    }

    private ImageView icon(int res, int color, float size) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(Ui.icon(c, res, color));
        v.setLayoutParams(new LayoutParams(Ui.u(size), Ui.u(size)));
        return v;
    }
}
