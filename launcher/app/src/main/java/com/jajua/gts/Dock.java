package com.jajua.gts;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Bottom dock, like the two small buttons under the main panels of the reference unit:
 * the lights app (name and visibility set in Ajustes), the app drawer and settings. What is playing lives only in the
 * vehicle panel. Home/back/recents are left to the TS10's own navigation so nothing is duplicated.
 */
final class Dock extends LinearLayout {
    private final Context c;

    Dock(Context c, Runnable onLights, Runnable onApps, Runnable onSettings) {
        super(c);
        this.c = c;
        setGravity(Gravity.CENTER);
        Prefs prefs = Prefs.get(c);
        if (prefs.lightsShown()) addView(button(R.drawable.ic_lantern, prefs.lightsLabel(), onLights));
        addView(button(R.drawable.ic_apps, "Aplicaciones", onApps));
        addView(button(R.drawable.ic_settings, "Ajustes", onSettings));
    }

    private LinearLayout button(int res, String label, Runnable action) {
        Palette p = Palette.current;
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(Ui.icon(c, res, p.text));
        int pad = Ui.u(14);
        icon.setPadding(pad, pad, pad, pad);
        icon.setBackground(Ui.panel(14));
        b.addView(icon, new LayoutParams(Ui.u(58), Ui.u(58)));
        TextView t = Ui.text(c, 14, Ui.regular, p.subtext);
        t.setText(label);
        LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.u(8);
        b.addView(t, tl);
        b.setContentDescription(label);
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        Ui.pressable(b);
        LayoutParams lp = new LayoutParams(Ui.u(150), LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
    }
}
