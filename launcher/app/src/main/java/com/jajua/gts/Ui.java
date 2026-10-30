package com.jajua.gts;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * Resolution-independent sizing. Everything is designed on a 1280 x 720 canvas (the TS10 panel)
 * and scaled to the real window, so the layout is the same whatever density the firmware reports.
 */
final class Ui {
    static Typeface regular;
    static Typeface medium;
    static Typeface light;
    static Typeface condensed;

    private static float unit = 1f;
    /** System font size, clamped so a large setting stays readable without breaking the layout. */
    private static float textScale = 1f;

    private Ui() {}

    static void init(Context context) {
        init(context, false);
    }

    /** fullScreen: the window covers the system bars too, so scale to the whole panel. */
    static void init(Context context, boolean fullScreen) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        if (fullScreen) {
            dm = new DisplayMetrics();
            context.getSystemService(android.view.WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
        }
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        unit = Math.min(w / 1280f, h / 720f);
        float fs = context.getResources().getConfiguration().fontScale;
        textScale = Math.max(0.9f, Math.min(1.2f, fs));
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    }

    static int u(float v) {
        return Math.round(v * unit);
    }

    static float uf(float v) {
        return v * unit;
    }

    /** Text size in pixels: layout scale times the (clamped) user font scale. */
    static float tf(float v) {
        return v * unit * textScale;
    }

    static TextView text(Context c, float size, Typeface face, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, tf(size));
        t.setTypeface(face);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    /** Small uppercase caption, the "automotive" label style used across the UI. */
    static TextView caption(Context c, String s) {
        TextView t = text(c, 13, condensed, Palette.current.subtext);
        t.setText(s.toUpperCase(java.util.Locale.ROOT));
        t.setLetterSpacing(0.14f);
        return t;
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(uf(radius));
        return g;
    }

    static GradientDrawable panel(float radius) {
        Palette p = Palette.current;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {p.panelTop, p.panelBottom});
        g.setCornerRadius(uf(radius));
        g.setStroke(Math.max(1, u(1)), p.edge);
        return g;
    }

    static Drawable icon(Context c, int res, int color) {
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        return d;
    }

    static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255) << 24) | (color & 0x00FFFFFF);
    }

    /** Short, subtle press feedback. Does not consume the event, so click listeners still fire. */
    static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
