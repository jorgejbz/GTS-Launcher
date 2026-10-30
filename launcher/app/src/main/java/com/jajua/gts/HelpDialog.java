package com.jajua.gts;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Short, icon-led connection help. The steps are the general procedure for a TLink-style
 * phone-link app; they are marked as unverified until tested on this TS10.
 */
final class HelpDialog {
    enum Kind {
        CARPLAY("Conectar Apple CarPlay", new int[] {R.drawable.ic_bluetooth, R.drawable.ic_carplay, R.drawable.ic_phone, R.drawable.ic_check, R.drawable.ic_time},
                new String[] {
                    "En el iPhone, activa Bluetooth y Wi‑Fi (o conéctalo por cable USB si tu instalación lo usa).",
                    "Abre la app de enlace del teléfono de tu pantalla (TLink5, ZLink, AutoKit…) desde el inicio.",
                    "En el iPhone, elige esta pantalla cuando aparezca en Bluetooth o en Ajustes › General › CarPlay.",
                    "Acepta «Usar CarPlay» y los permisos que pida el iPhone.",
                    "Espera unos segundos a que inicie CarPlay. Si ya estaba emparejado, se reconecta solo al encender el auto.",
                }),
        ANDROID_AUTO("Conectar Android Auto", new int[] {R.drawable.ic_bluetooth, R.drawable.ic_android_auto, R.drawable.ic_phone, R.drawable.ic_check, R.drawable.ic_time},
                new String[] {
                    "En el teléfono, activa Bluetooth y Wi‑Fi para la conexión inalámbrica (o usa cable USB).",
                    "Abre la app de enlace del teléfono de tu pantalla (TLink5, ZLink, AutoKit…) desde el inicio.",
                    "En el teléfono, empareja con el nombre que muestra la pantalla y sigue sus instrucciones.",
                    "Acepta los permisos de Android Auto en el teléfono.",
                    "Espera a que inicie Android Auto. Un teléfono ya emparejado se reconecta solo.",
                }),
        BLUETOOTH("Emparejar el teléfono por Bluetooth", new int[] {R.drawable.ic_bluetooth, R.drawable.ic_phone, R.drawable.ic_check, R.drawable.ic_music},
                new String[] {
                    "Abre la app Bluetooth de la pantalla y anota el nombre del equipo que aparece.",
                    "En el teléfono, ve a Ajustes › Bluetooth y elige ese nombre.",
                    "Confirma el código en ambos equipos y permite el acceso a audio (y contactos si quieres llamadas).",
                    "Reproduce música en el teléfono: sonará por el estéreo. El emparejamiento se recuerda.",
                });

        final String title;
        final int[] icons;
        final String[] steps;

        Kind(String title, int[] icons, String[] steps) {
            this.title = title;
            this.icons = icons;
            this.steps = steps;
        }
    }

    private HelpDialog() {}

    /** openLabel/open may be null when there is no app to open. */
    static void show(Activity a, Kind kind, String openLabel, Runnable open) {
        Palette p = Palette.current;
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.panel(18));
        box.setPadding(Ui.u(34), Ui.u(28), Ui.u(34), Ui.u(24));

        TextView title = Ui.text(a, 24, Ui.medium, p.text);
        title.setText(kind.title);
        box.addView(title);

        for (int i = 0; i < kind.steps.length; i++) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView n = Ui.text(a, 15, Ui.medium, Palette.accent);
            n.setText(String.valueOf(i + 1));
            n.setMinWidth(Ui.u(22));
            row.addView(n);
            ImageView ic = new ImageView(a);
            ic.setImageDrawable(Ui.icon(a, kind.icons[i], p.text));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.u(28), Ui.u(28));
            il.setMargins(Ui.u(4), 0, Ui.u(16), 0);
            row.addView(ic, il);
            TextView t = Ui.text(a, 17, Ui.regular, p.text);
            t.setSingleLine(false);
            t.setMaxLines(3);
            t.setText(kind.steps[i]);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = Ui.u(i == 0 ? 22 : 14);
            box.addView(row, rl);
        }

        TextView note = Ui.text(a, 13, Ui.regular, p.dim);
        note.setSingleLine(false);
        note.setText("Pasos generales: se ajustarán cuando se comprueben en esta pantalla.");
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = Ui.u(18);
        box.addView(note, nl);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setGravity(Gravity.END);
        buttons.addView(button(a, "Cerrar", false, d::dismiss));
        if (open != null) {
            buttons.addView(button(a, openLabel, true, () -> {
                d.dismiss();
                open.run();
            }));
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.u(20);
        box.addView(buttons, bl);

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(Ui.u(760), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.7f);
        }
        d.show();
    }

    static TextView button(Activity a, String label, boolean primary, Runnable action) {
        TextView b = Ui.text(a, 17, Ui.medium, primary ? 0xFFFFFFFF : Palette.current.text);
        b.setText(label);
        // A single-line TextView measured with includeFontPadding off clipped the last glyph.
        b.setSingleLine(false);
        b.setMaxLines(1);
        b.setEllipsize(null);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.u(26), Ui.u(14), Ui.u(26), Ui.u(14));
        b.setBackground(Ui.rounded(primary ? Palette.accent : Ui.withAlpha(0xFFFFFFFF, 0.08f), 24));
        b.setOnClickListener(v -> action.run());
        Ui.pressable(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.u(12);
        b.setLayoutParams(lp);
        return b;
    }
}
