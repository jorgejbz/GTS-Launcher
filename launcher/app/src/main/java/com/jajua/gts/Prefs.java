package com.jajua.gts;

import android.content.Context;
import android.content.SharedPreferences;

/** All persisted launcher settings. Nothing personal is stored here. */
final class Prefs {
    private static Prefs instance;

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("gts", Context.MODE_PRIVATE);
    }

    static Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c);
        return instance;
    }

    /** Bumped on every visual change so the home screen knows to rebuild itself on resume. */
    int stamp() {
        return sp.getInt("stamp", 0);
    }

    private SharedPreferences.Editor edit() {
        return sp.edit().putInt("stamp", stamp() + 1);
    }

    String theme() {
        return sp.getString("theme", Palette.GRAFITO.key);
    }

    void setTheme(String key) {
        edit().putString("theme", key).apply();
    }

    /** Accent colour (ARGB). Falls back to the 1.0 preset index so an earlier choice survives. */
    int accentColor() {
        if (sp.contains("accent.color")) return sp.getInt("accent.color", Palette.DEFAULT_ACCENT) | 0xFF000000;
        int i = sp.getInt("accent", 0);
        return Palette.LEGACY_ACCENTS[i >= 0 && i < Palette.LEGACY_ACCENTS.length ? i : 0];
    }

    int glow() {
        return sp.getInt("accent.glow", Palette.DEFAULT_GLOW);
    }

    void setAccent(int color, int glow) {
        edit().putInt("accent.color", color | 0xFF000000).putInt("accent.glow", glow).remove("accent").apply();
    }

    void resetVisual() {
        edit().remove("theme").remove("accent").remove("accent.color").remove("accent.glow")
                .remove("icons.original").apply();
    }

    /** Drawer shows the apps' own icons instead of the launcher's line icons. */
    boolean originalIcons() {
        return sp.getBoolean("icons.original", false);
    }

    void setOriginalIcons(boolean original) {
        edit().putBoolean("icons.original", original).apply();
    }

    // --- Which app (and optionally which activity) each shortcut opens. Null = auto-detect.

    String targetPackage(Role r) {
        return sp.getString("role." + r.name() + ".pkg", null);
    }

    String targetComponent(Role r) {
        return sp.getString("role." + r.name() + ".component", null);
    }

    void setTarget(Role r, String pkg, String component) {
        SharedPreferences.Editor e = edit();
        if (pkg == null) e.remove("role." + r.name() + ".pkg");
        else e.putString("role." + r.name() + ".pkg", pkg);
        if (component == null) e.remove("role." + r.name() + ".component");
        else e.putString("role." + r.name() + ".component", component);
        e.apply();
    }

    /** Names offered for the lights button in the dock; index stored in "lights.label". */
    static final String[] LIGHT_LABELS = {"Luces interiores", "Luces de chasis", "Iluminación"};

    String lightsLabel() {
        int i = sp.getInt("lights.label", 0);
        return LIGHT_LABELS[i >= 0 && i < LIGHT_LABELS.length ? i : 0];
    }

    void setLightsLabel(int index) {
        edit().putInt("lights.label", index).apply();
    }

    /** False hides the lights button (for cars without controllable lights). */
    boolean lightsShown() {
        return sp.getBoolean("lights.shown", true);
    }

    void setLightsShown(boolean shown) {
        edit().putBoolean("lights.shown", shown).apply();
    }

    /** Shows the PC link and vendor-APK tools (7 taps on the version in Ajustes). */
    boolean devMode() {
        // Someone already using the PC link keeps it after updating.
        return sp.getBoolean("dev", !sp.getString("pc.address", "").isEmpty());
    }

    void setDevMode(boolean on) {
        sp.edit().putBoolean("dev", on).apply();
    }

    /** "ip:puerto" of the development PC running scripts/servidor-pc.py. */
    String pcAddress() {
        return sp.getString("pc.address", "");
    }

    void setPcAddress(String address) {
        sp.edit().putString("pc.address", address).apply();
    }

    // --- Speed source.

    String speedMode() {
        return sp.getString("speed.mode", SpeedMonitor.AUTO);
    }

    /**
     * 1.1.0: the CANBUS speed source was found. Anyone left on "Desactivada" (the old default), or
     * on the approximate GPS when the car's own data exists, moves once to "Automática".
     */
    void migrateSpeedMode(boolean canbus) {
        if (sp.getBoolean("speed.migrated", false)) return;
        String m = sp.getString("speed.mode", SpeedMonitor.AUTO);
        SharedPreferences.Editor e = sp.edit().putBoolean("speed.migrated", true);
        if (SpeedMonitor.OFF.equals(m) || (canbus && SpeedMonitor.GPS.equals(m))) e.putString("speed.mode", SpeedMonitor.AUTO);
        e.apply();
    }

    boolean locationAsked() {
        return sp.getBoolean("speed.locationAsked", false);
    }

    void setLocationAsked() {
        sp.edit().putBoolean("speed.locationAsked", true).apply();
    }

    /** "auto" follows the system (Settings.System SPEED_UNIT), or forced "kmh" / "mph". */
    String speedUnit() {
        return sp.getString("speed.unit", SpeedMonitor.UNIT_AUTO);
    }

    void setSpeedUnit(String unit) {
        edit().putString("speed.unit", unit).apply();
    }

    String speedAction() {
        return sp.getString("speed.action", "");
    }

    String speedExtra() {
        return sp.getString("speed.extra", "");
    }

    /** True when the broadcast value is in m/s instead of km/h. */
    boolean speedInMs() {
        return sp.getBoolean("speed.ms", false);
    }

    void setSpeed(String mode, String action, String extra, boolean ms) {
        edit().putString("speed.mode", mode).putString("speed.action", action)
                .putString("speed.extra", extra).putBoolean("speed.ms", ms).apply();
    }
}
