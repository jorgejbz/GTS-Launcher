package com.jajua.gts;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

/** Resolves which app each shortcut opens and starts it, explaining failures in plain Spanish. */
final class Targets {
    static final class Target {
        final Role role;
        final String pkg;
        /** A specific activity the user picked, or null for the app's normal entry point. */
        final ComponentName component;
        final String label;
        final Drawable icon;
        final boolean manual;

        Target(Role role, String pkg, ComponentName component, String label, Drawable icon, boolean manual) {
            this.role = role;
            this.pkg = pkg;
            this.component = component;
            this.label = label;
            this.icon = icon;
            this.manual = manual;
        }
    }

    private Targets() {}

    /** Null when nothing is assigned and nothing installed matches. */
    static Target resolve(Context c, Role role) {
        Prefs prefs = Prefs.get(c);
        String pkg = prefs.targetPackage(role);
        if (pkg != null) {
            String comp = prefs.targetComponent(role);
            ComponentName cn = comp != null ? ComponentName.unflattenFromString(comp) : null;
            AppCatalog.App app = AppCatalog.find(pkg);
            if (app != null) return new Target(role, pkg, cn, app.label, app.icon, true);
            // Assigned app without a launcher icon (some vendor apps): still usable through its component.
            try {
                PackageManager pm = c.getPackageManager();
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                return new Target(role, pkg, cn, String.valueOf(pm.getApplicationLabel(ai)),
                        pm.getApplicationIcon(ai), true);
            } catch (PackageManager.NameNotFoundException ex) {
                return null; // Uninstalled since it was chosen.
            }
        }
        AppCatalog.App app = AppCatalog.detect(role);
        return app == null ? null
                : new Target(role, app.pkg, preferred(c, role, app.pkg), app.label, app.icon, false);
    }

    /** The role's dedicated screen inside the detected app, if that app exports one. */
    private static ComponentName preferred(Context c, Role role, String pkg) {
        String suffix = role.preferredActivity();
        if (suffix == null) return null;
        String key = role.name() + "/" + pkg + "/" + AppCatalog.generation();
        if (preferredCache.containsKey(key)) return preferredCache.get(key);
        ComponentName found = findPreferred(c, pkg, suffix);
        preferredCache.put(key, found);
        return found;
    }

    // Also read from the audit's background thread.
    private static final java.util.Map<String, ComponentName> preferredCache =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    private static ComponentName findPreferred(Context c, String pkg, String suffix) {
        try {
            android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi.activities == null) return null;
            for (android.content.pm.ActivityInfo ai : pi.activities) {
                if (ai.exported && ai.enabled && ai.name.endsWith(suffix)) return new ComponentName(pkg, ai.name);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Fall back to the main screen.
        }
        return null;
    }

    static Intent intentFor(Context c, Target t) {
        if (t.component != null) {
            return new Intent(Intent.ACTION_MAIN).setComponent(t.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        AppCatalog.App app = AppCatalog.find(t.pkg);
        if (app != null) return app.launchIntent();
        Intent i = c.getPackageManager().getLaunchIntentForPackage(t.pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    static boolean launch(Activity a, Target t) {
        Intent i = intentFor(a, t);
        if (i == null) {
            Toast.makeText(a, t.label + " no tiene una pantalla que se pueda abrir. Elige otra actividad en Ajustes.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return start(a, i, t.label);
    }

    static boolean start(Activity a, Intent i, String label) {
        try {
            a.startActivity(i);
            return true;
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(a, "No se encontró la pantalla de " + label + ". Puede que la app se haya actualizado; revísala en Ajustes.",
                    Toast.LENGTH_LONG).show();
        } catch (SecurityException ex) {
            Toast.makeText(a, label + " no permite que otro launcher abra esa pantalla (restricción del fabricante).",
                    Toast.LENGTH_LONG).show();
        }
        return false;
    }
}
