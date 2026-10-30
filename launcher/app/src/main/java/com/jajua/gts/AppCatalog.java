package com.jajua.gts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Launchable apps from PackageManager, loaded off the main thread and cached until a package changes. */
final class AppCatalog {
    interface Listener {
        void onAppsChanged();
    }

    static final class App {
        final String pkg;
        final ComponentName component;
        final String label;
        final Drawable icon;
        final boolean system;

        App(String pkg, ComponentName component, String label, Drawable icon, boolean system) {
            this.pkg = pkg;
            this.component = component;
            this.label = label;
            this.icon = icon;
            this.system = system;
        }

        Intent launchIntent() {
            return new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile List<App> apps = Collections.emptyList();
    private static volatile boolean loaded;
    /** Bumped on every reload, so caches keyed on it go stale when packages change. */
    private static volatile int generation;

    private AppCatalog() {}

    static int generation() {
        return generation;
    }

    static void reload(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            List<App> list = load(app);
            main.post(() -> {
                apps = list;
                loaded = true;
                generation++;
                listener.onAppsChanged();
            });
        });
    }

    static boolean loaded() {
        return loaded;
    }

    static List<App> apps() {
        return apps;
    }

    static App find(String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    /** Best keyword match for a role, or null. */
    static App detect(Role role) {
        App best = null;
        int bestScore = 0;
        for (App a : apps) {
            int s = role.score(a.label, a.pkg);
            if (s > bestScore) {
                best = a;
                bestScore = s;
            }
        }
        return best;
    }

    private static List<App> load(Context context) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> list = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self) || seen.contains(pkg)) continue;
            seen.add(pkg);
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (RuntimeException ex) {
                icon = pm.getDefaultActivityIcon();
            }
            list.add(new App(pkg, new ComponentName(pkg, ri.activityInfo.name),
                    String.valueOf(ri.loadLabel(pm)), icon, system));
        }
        // The TS10 firmware gives Android's Settings no launcher icon; keep it reachable anyway.
        if (!seen.contains("com.android.settings")) {
            ResolveInfo settings = pm.resolveActivity(new Intent(android.provider.Settings.ACTION_SETTINGS), 0);
            if (settings != null && settings.activityInfo.exported) {
                ApplicationInfo ai = settings.activityInfo.applicationInfo;
                list.add(new App(ai.packageName, new ComponentName(ai.packageName, settings.activityInfo.name),
                        "Ajustes de Android", settings.loadIcon(pm), true));
            }
        }

        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(list, (a, b) -> collator.compare(a.label, b.label));
        return list;
    }
}
