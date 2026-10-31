package com.jajua.gts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Accent colour chooser: the tint of the glow behind the home shortcuts, the pressed bar, focus
 * outlines and section titles. Made to be used with one finger in a car: a row of ready colours,
 * then three big sliders (tone, intensity, glow), all with a live preview of a shortcut.
 */
final class ColorPicker {
    interface OnApply {
        void apply(int color, int glow);
    }

    static final String[] NAMES = {"Rojo", "Rojo oscuro", "Naranja", "Ámbar", "Amarillo", "Verde", "Turquesa",
            "Cian", "Azul", "Índigo", "Morado", "Rosa", "Plata", "Blanco"};
    static final int[] COLORS = {0xFFE60012, 0xFFA8122A, 0xFFFF6A13, 0xFFFFB000, 0xFFFFE14D, 0xFF2ECC71, 0xFF00C2B8,
            0xFF19B5FE, 0xFF2F6BFF, 0xFF5B4BFF, 0xFF9B4DFF, 0xFFFF3D8B, 0xFFC9CED6, 0xFFF2F3F5};

    private ColorPicker() {}

    /** Preset name, or "Personalizado #RRGGBB". */
    static String name(int color) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == color) return NAMES[i];
        return String.format("Personalizado #%06X", color & 0xFFFFFF);
    }

    static void show(Activity a, int current, int currentGlow, OnApply onApply) {
        Palette p = Palette.current;
        float[] hsv = new float[3];
        Color.colorToHSV(current, hsv);
        final int[] glow = {currentGlow};

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.u(28), Ui.u(18), Ui.u(28), Ui.u(6));

        Preview preview = new Preview(a);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(130)));

        TextView name = Ui.text(a, 15, Ui.regular, p.subtext);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.u(8), 0, Ui.u(4));
        root.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar hue = slider(a, 360, Math.round(hsv[0]));
        SeekBar sat = slider(a, 100, Math.round(hsv[1] * 100));
        SeekBar glowBar = slider(a, 100, glow[0]);

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int color = Color.HSVToColor(hsv);
                preview.set(color, glow[0]);
                name.setText(name(color) + " · resplandor " + glow[0] + " %");
                float[] full = {hsv[0], 1f, Math.max(hsv[2], 0.85f)};
                int[] rainbow = new int[13];
                for (int i = 0; i < rainbow.length; i++) {
                    rainbow[i] = Color.HSVToColor(new float[] {i * 30f, Math.max(hsv[1], 0.6f), Math.max(hsv[2], 0.85f)});
                }
                track(hue, rainbow);
                track(sat, new int[] {Color.HSVToColor(new float[] {hsv[0], 0f, hsv[2]}), Color.HSVToColor(full)});
                track(glowBar, new int[] {Ui.withAlpha(color, 0.05f), color});
            }
        };

        // Ready-made colours: two rows of large dots.
        LinearLayout swatches = new LinearLayout(a);
        swatches.setOrientation(LinearLayout.VERTICAL);
        int perRow = (COLORS.length + 1) / 2;
        LinearLayout rowView = null;
        for (int i = 0; i < COLORS.length; i++) {
            if (i % perRow == 0) {
                rowView = new LinearLayout(a);
                rowView.setGravity(Gravity.CENTER);
                swatches.addView(rowView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            int color = COLORS[i];
            View dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            g.setStroke(Math.max(1, Ui.u(2)), Ui.withAlpha(0xFFFFFFFF, 0.25f));
            dot.setBackground(g);
            dot.setContentDescription(NAMES[i]);
            dot.setOnClickListener(v -> {
                Color.colorToHSV(color, hsv);
                hue.setProgress(Math.round(hsv[0]));
                sat.setProgress(Math.round(hsv[1] * 100));
                refresh.run();
            });
            Ui.pressable(dot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.u(50), Ui.u(50));
            lp.setMargins(Ui.u(8), Ui.u(8), Ui.u(8), Ui.u(8));
            rowView.addView(dot, lp);
        }
        root.addView(swatches);

        root.addView(label(a, "Tono"));
        root.addView(hue, sliderLp());
        root.addView(label(a, "Intensidad del color (a la izquierda: gris / plata)"));
        root.addView(sat, sliderLp());
        root.addView(label(a, "Resplandor detrás de los accesos"));
        root.addView(glowBar, sliderLp());

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (!fromUser) return;
                if (s == hue) hsv[0] = v;
                else if (s == sat) hsv[1] = v / 100f;
                else glow[0] = v;
                refresh.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        };
        hue.setOnSeekBarChangeListener(l);
        sat.setOnSeekBarChangeListener(l);
        glowBar.setOnSeekBarChangeListener(l);
        refresh.run();

        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.addView(root);
        AlertDialog d = Pickers.dialog(a).setTitle("Color de acento").setView(scroll)
                .setPositiveButton("Aplicar", (x, w) -> onApply.apply(Color.HSVToColor(hsv), glow[0]))
                .setNeutralButton("Predeterminado", (x, w) -> onApply.apply(Palette.DEFAULT_ACCENT, Palette.DEFAULT_GLOW))
                .setNegativeButton("Cancelar", null).create();
        d.show();
        // Wide enough for the sliders on a 1280 x 720 screen.
        if (d.getWindow() != null) d.getWindow().setLayout(Ui.u(820), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static TextView label(Context c, String s) {
        TextView t = Ui.caption(c, s);
        t.setPadding(Ui.u(4), Ui.u(16), 0, Ui.u(2));
        return t;
    }

    private static LinearLayout.LayoutParams sliderLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(52));
    }

    private static SeekBar slider(Context c, int max, int value) {
        SeekBar s = new SeekBar(c);
        s.setMax(max);
        s.setProgress(value);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFFFFFFFF);
        thumb.setStroke(Math.max(1, Ui.u(3)), 0xFF000000);
        thumb.setSize(Ui.u(34), Ui.u(34));
        s.setThumb(thumb);
        s.setSplitTrack(false);
        s.setPadding(Ui.u(20), 0, Ui.u(20), 0);
        if (Build.VERSION.SDK_INT >= 29) {
            s.setMinHeight(Ui.u(14));
            s.setMaxHeight(Ui.u(14));
        }
        return s;
    }

    private static void track(SeekBar s, int[] colors) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(Ui.uf(7));
        s.setProgressDrawable(g);
    }

    /** A miniature home shortcut: panel, glow from the bottom, pressed bar and an accent title. */
    private static final class Preview extends View {
        private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int color;
        private int glowLevel;

        Preview(Context c) {
            super(c);
            text.setColor(Palette.current.text);
            text.setTypeface(Ui.medium);
            text.setTextSize(Ui.tf(24));
            text.setTextAlign(Paint.Align.CENTER);
            caption.setTypeface(Ui.condensed);
            caption.setTextSize(Ui.tf(13));
            caption.setLetterSpacing(0.14f);
            caption.setTextAlign(Paint.Align.CENTER);
        }

        void set(int color, int glowLevel) {
            this.color = color;
            this.glowLevel = glowLevel;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            Palette p = Palette.current;
            float w = getWidth();
            float h = getHeight();
            float rad = Ui.uf(16);
            r.set(0, 0, w, h);
            panel.setShader(new LinearGradient(0, 0, 0, h, p.panelTop, p.panelBottom, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, panel);
            glow.setShader(new RadialGradient(w / 2f, h * 1.05f, Math.max(1, w * 0.6f),
                    Ui.withAlpha(color, Palette.glowAlpha(glowLevel)), 0x00000000, Shader.TileMode.CLAMP));
            glow.setAlpha(150);
            c.drawRoundRect(r, rad, rad, glow);
            caption.setColor(color);
            c.drawText("AJUSTES", w / 2f, h * 0.30f, caption);
            c.drawText("Así se verá en el inicio", w / 2f, h * 0.58f, text);
            bar.setColor(color);
            float bw = w * 0.22f;
            c.drawRoundRect(w / 2f - bw / 2f, h - Ui.uf(9), w / 2f + bw / 2f, h - Ui.uf(4), Ui.uf(2), Ui.uf(2), bar);
        }
    }
}
