package com.jajua.gts;

/** The dark interface variants plus the accent colour. Read once per build of the UI. */
final class Palette {
    final String key;
    final String name;
    final int bg;
    final int panelTop;
    final int panelBottom;
    final int edge;
    final int text;
    final int subtext;
    final int dim;

    private Palette(String key, String name, int bg, int panelTop, int panelBottom, int edge,
            int text, int subtext, int dim) {
        this.key = key;
        this.name = name;
        this.bg = bg;
        this.panelTop = panelTop;
        this.panelBottom = panelBottom;
        this.edge = edge;
        this.text = text;
        this.subtext = subtext;
        this.dim = dim;
    }

    static final Palette GRAFITO = new Palette("grafito", "Grafito",
            0xFF0C0D10, 0xFF24272D, 0xFF131519, 0x26FFFFFF, 0xFFF2F3F5, 0xFF9DA3AD, 0xFF5A5F68);
    static final Palette NEGRO = new Palette("negro", "Negro",
            0xFF000000, 0xFF18191C, 0xFF09090B, 0x1FFFFFFF, 0xFFF2F3F5, 0xFF969BA4, 0xFF4F535A);
    static final Palette ACERO = new Palette("acero", "Acero",
            0xFF090D12, 0xFF202833, 0xFF10151C, 0x29FFFFFF, 0xFFF0F3F7, 0xFF97A2B0, 0xFF55606D);
    static final Palette[] ALL = {GRAFITO, NEGRO, ACERO};

    static final String[] ACCENT_NAMES = {"Rojo", "Rojo oscuro", "Plata"};
    static final int[] ACCENTS = {0xFFE60012, 0xFFA8122A, 0xFFC9CED6};

    static Palette current = GRAFITO;
    static int accent = ACCENTS[0];

    static void load(Prefs prefs) {
        current = GRAFITO;
        for (Palette p : ALL) if (p.key.equals(prefs.theme())) current = p;
        int a = prefs.accent();
        accent = ACCENTS[a >= 0 && a < ACCENTS.length ? a : 0];
    }
}
