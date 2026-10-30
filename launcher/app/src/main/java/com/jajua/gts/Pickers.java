package com.jajua.gts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** List dialogs for choosing which app (or which exported activity of it) a shortcut opens. */
final class Pickers {
    private Pickers() {}

    static AlertDialog.Builder dialog(Activity a) {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    /** onPick receives null for "detección automática". */
    static void pickApp(Activity a, String title, Consumer<AppCatalog.App> onPick) {
        List<AppCatalog.App> apps = new ArrayList<>(AppCatalog.apps());
        List<AppCatalog.App> rows = new ArrayList<>();
        rows.add(null);
        rows.addAll(apps);
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public Object getItem(int i) {
                return rows.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                AppCatalog.App app = rows.get(i);
                return row(a, app == null ? null : app.icon,
                        app == null ? "Detección automática" : app.label,
                        app == null ? "Elegir según el nombre de las apps instaladas" : app.pkg);
            }
        };
        dialog(a).setTitle(title)
                .setAdapter(adapter, (d, which) -> onPick.accept(rows.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Lists the package's exported activities; onPick receives null for "pantalla principal". */
    static void pickActivity(Activity a, String pkg, Consumer<ComponentName> onPick) {
        List<ComponentName> list = new ArrayList<>();
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi.activities != null) {
                for (ActivityInfo ai : pi.activities) {
                    if (ai.exported && ai.enabled) list.add(new ComponentName(pkg, ai.name));
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Shown as an empty list.
        }
        List<String> labels = new ArrayList<>();
        labels.add("Pantalla principal de la app (recomendado)");
        for (ComponentName cn : list) labels.add(cn.getShortClassName());
        dialog(a).setTitle("Pantalla que se abrirá")
                .setItems(labels.toArray(new String[0]), (d, which) -> onPick.accept(which == 0 ? null : list.get(which - 1)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static View row(Activity a, android.graphics.drawable.Drawable icon, String title, String sub) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.u(22), Ui.u(12), Ui.u(22), Ui.u(12));
        ImageView iv = new ImageView(a);
        if (icon != null) iv.setImageDrawable(icon);
        else iv.setImageDrawable(Ui.icon(a, R.drawable.ic_check, Palette.accent));
        r.addView(iv, new LinearLayout.LayoutParams(Ui.u(40), Ui.u(40)));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.u(16), 0, 0, 0);
        TextView t = Ui.text(a, 18, Ui.regular, 0xFFFFFFFF);
        t.setText(title);
        TextView s = Ui.text(a, 13, Ui.regular, 0xFFA0A4AB);
        s.setText(sub);
        s.setPadding(0, Ui.u(4), 0, 0);
        texts.addView(t);
        texts.addView(s);
        r.addView(texts);
        return r;
    }
}
