package com.jajua.gts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The launcher's own line icon for an installed app, so the drawer looks like one system instead
 * of a mix of vendor icons. Exact packages come from the TS10 audit; keywords cover apps installed
 * later. Returns 0 when nothing fits, and the drawer then shows the app's initial in the same style.
 */
final class AppGlyphs {
    private static final Map<String, Integer> BY_PACKAGE = new HashMap<>();
    private static final Object[][] KEYWORDS = {
        {"radio", R.drawable.ic_radio}, {"bluetooth", R.drawable.ic_bluetooth}, {"música", R.drawable.ic_music}, {"musica", R.drawable.ic_music},
        {"music", R.drawable.ic_music}, {"spotify", R.drawable.ic_music}, {"video", R.drawable.ic_app_video}, {"youtube", R.drawable.ic_app_video},
        {"tv", R.drawable.ic_app_video}, {"map", R.drawable.ic_app_map}, {"navi", R.drawable.ic_app_map}, {"waze", R.drawable.ic_app_map},
        {"gps", R.drawable.ic_app_satellite}, {"ajustes", R.drawable.ic_settings}, {"settings", R.drawable.ic_settings},
        {"config", R.drawable.ic_settings}, {"archivo", R.drawable.ic_app_folder}, {"file", R.drawable.ic_app_folder},
        {"zip", R.drawable.ic_app_folder}, {"chrome", R.drawable.ic_app_globe}, {"browser", R.drawable.ic_app_globe},
        {"navegador", R.drawable.ic_app_globe}, {"clima", R.drawable.ic_app_weather}, {"weather", R.drawable.ic_app_weather},
        {"store", R.drawable.ic_app_store}, {"tienda", R.drawable.ic_app_store}, {"market", R.drawable.ic_app_store},
        {"foto", R.drawable.ic_app_image}, {"gallery", R.drawable.ic_app_image}, {"galer", R.drawable.ic_app_image}, {"pic", R.drawable.ic_app_image},
        {"cámara", R.drawable.ic_app_camera}, {"camara", R.drawable.ic_app_camera}, {"camera", R.drawable.ic_app_camera},
        {"voice", R.drawable.ic_app_mic}, {"voz", R.drawable.ic_app_mic}, {"assistant", R.drawable.ic_app_mic}, {"eq", R.drawable.ic_app_equalizer},
        {"dsp", R.drawable.ic_app_equalizer}, {"volante", R.drawable.ic_steering}, {"lantern", R.drawable.ic_lantern}, {"led", R.drawable.ic_lantern},
        {"tlink", R.drawable.ic_app_link}, {"carplay", R.drawable.ic_app_link}, {"car", R.drawable.ic_app_car}, {"auto", R.drawable.ic_app_car},
        {"calendar", R.drawable.ic_app_calendar}, {"calendario", R.drawable.ic_app_calendar},
        {"clock", R.drawable.ic_time}, {"reloj", R.drawable.ic_time}, {"contact", R.drawable.ic_app_person},
        {"phone", R.drawable.ic_phone}, {"teléfono", R.drawable.ic_phone}, {"dialer", R.drawable.ic_phone},
        {"mail", R.drawable.ic_app_mail}, {"correo", R.drawable.ic_app_mail}, {"messag", R.drawable.ic_app_chat},
        {"mensaj", R.drawable.ic_app_chat}, {"whatsapp", R.drawable.ic_app_chat}, {"telegram", R.drawable.ic_app_chat},
        {"google", R.drawable.ic_app_search}, {"search", R.drawable.ic_app_search}, {"buscar", R.drawable.ic_app_search},
    };

    static {
        put("com.tw.radio", R.drawable.ic_radio);
        put("com.tw.bt", R.drawable.ic_bluetooth);
        put("com.tw.music", R.drawable.ic_music);
        put("com.tw.video", R.drawable.ic_app_video);
        put("com.tw.auxin", R.drawable.ic_cable);
        put("com.tw.eq", R.drawable.ic_app_equalizer);
        put("com.tw.keypad", R.drawable.ic_steering);
        put("com.tw.twfileexplore", R.drawable.ic_app_folder);
        put("com.tw.bootanimation", R.drawable.ic_app_film);
        put("com.tw.reverse", R.drawable.ic_app_camera);
        put("com.zjinnova.zlink", R.drawable.ic_app_link);
        put("wl.smartled.rgb", R.drawable.ic_lantern);
        put("com.dofun.carsetting", R.drawable.ic_app_car);
        put("com.dofun.carassistant.car", R.drawable.ic_app_car);
        put("com.dofun.variety", R.drawable.ic_app_palette);
        put("com.dofun.market", R.drawable.ic_app_store);
        put("com.dofun.dofunweather.main", R.drawable.ic_app_weather);
        put("com.dofun.overseasvoice", R.drawable.ic_app_mic);
        put("com.android.vending", R.drawable.ic_app_store);
        put("com.android.chrome", R.drawable.ic_app_globe);
        put("com.android.settings", R.drawable.ic_settings);
        put("com.android.documentsui", R.drawable.ic_app_folder);
        put("com.google.android.googlequicksearchbox", R.drawable.ic_app_search);
        put("com.google.android.apps.maps", R.drawable.ic_app_map);
        put("com.google.android.youtube", R.drawable.ic_app_video);
        put("com.sygic.incar", R.drawable.ic_app_map);
        put("com.alensw.PicFolder", R.drawable.ic_app_image);
        put("com.androits.gps.test.pro", R.drawable.ic_app_satellite);
    }

    private AppGlyphs() {}

    private static void put(String pkg, int res) {
        BY_PACKAGE.put(pkg, res);
    }

    private static final Map<String, Integer> CACHE = new HashMap<>();

    static int forApp(String pkg, String label) {
        Integer exact = BY_PACKAGE.get(pkg);
        if (exact != null) return exact;
        Integer cached = CACHE.get(pkg + "|" + label);
        if (cached != null) return cached;
        int found = byKeyword(pkg, label);
        CACHE.put(pkg + "|" + label, found);
        return found;
    }

    private static int byKeyword(String pkg, String label) {
        String l = label.toLowerCase(Locale.ROOT);
        String p = pkg.toLowerCase(Locale.ROOT);
        for (Object[] k : KEYWORDS) {
            String word = (String) k[0];
            if (matches(l, word) || matches(p, word)) return (Integer) k[1];
        }
        return 0;
    }

    /** Short keywords ("tv", "eq", "car") only count as whole words. */
    private static boolean matches(String haystack, String word) {
        if (word.length() > 3) return haystack.contains(word);
        return java.util.regex.Pattern.compile("(^|[^a-z])" + word + "([^a-z]|$)").matcher(haystack).find();
    }
}
