package com.jajua.gts;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The functions the launcher has a dedicated shortcut for. Package names on the TS10 are not
 * assumed: each role is matched against the label and package of the installed apps, strongest
 * keyword first, and the user can always override the choice in Ajustes.
 */
enum Role {
    CARPLAY("Apple CarPlay", R.drawable.ic_carplay,
            new String[] {"tlink", "carplay", "zlink", "carlink", "autokit"}, new String[] {}),
    ANDROID_AUTO("Android Auto", R.drawable.ic_android_auto,
            new String[] {"tlink", "android auto", "androidauto", "zlink", "carlink", "autokit"},
            // The phone-side Android Auto app does nothing on a head unit.
            new String[] {"gearhead"}),
    RADIO("Radio", R.drawable.ic_radio,
            new String[] {"radio", "收音机", "fm"}, new String[] {"internet radio", "podcast"}),
    BT_AUDIO("Bluetooth Audio", R.drawable.ic_bluetooth,
            new String[] {"bt music", "bluetooth music", "música bt", "musica bt", "bt audio", "a2dp", "bluetooth", "蓝牙", "bt"},
            new String[] {"lantern", "magic"}),
    // Shown as what it does; the app itself is Magic Lantern (wl.smartled.rgb).
    LANTERN("Luces interiores", R.drawable.ic_lantern,
            new String[] {"lantern", "mágico", "magico", "luces", "lights", "led", "ambient", "rgb"}, new String[] {}),
    STEERING("Mandos al volante", R.drawable.ic_steering,
            new String[] {"volante", "steering", "swc", "wheel", "方向盘"}, new String[] {}),
    MUSIC("Música", R.drawable.ic_music,
            new String[] {"música", "musica", "music", "音乐"},
            new String[] {"bluetooth", "bt", "youtube", "spotify", "radio"});

    final String title;
    final int icon;
    private final String[] keywords;
    private final String[] excludes;

    /**
     * Exported activity (class-name suffix) to open instead of the app's main screen. Found by the
     * on-device audit: TLINK5 (com.zjinnova.zlink 5.3.36) has separate CarPlay and Android Auto
     * screens. Only used when the app really has such an exported activity.
     */
    String preferredActivity() {
        switch (this) {
            case CARPLAY:
                return ".CarPlayActivity";
            case ANDROID_AUTO:
                return ".AutoActivity";
            default:
                return null;
        }
    }

    Role(String title, int icon, String[] keywords, String[] excludes) {
        this.title = title;
        this.icon = icon;
        this.keywords = keywords;
        this.excludes = excludes;
    }

    /** 0 = no match. Label matches beat package matches; earlier keywords beat later ones. */
    int score(String label, String pkg) {
        String l = label.toLowerCase(Locale.ROOT);
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String x : excludes) if (contains(l, x)) return 0;
        for (int i = 0; i < keywords.length; i++) {
            if (contains(l, keywords[i])) return 200 - i * 10;
        }
        for (int i = 0; i < keywords.length; i++) {
            if (contains(p, keywords[i])) return 100 - i * 10;
        }
        return 0;
    }

    /** Short keywords ("bt", "fm") only count as whole words, so "com.abtest" is not Bluetooth. */
    private static boolean contains(String haystack, String word) {
        if (word.length() > 3) return haystack.contains(word);
        return Pattern.compile("(^|[^a-z])" + Pattern.quote(word) + "([^a-z]|$)").matcher(haystack).find();
    }
}
