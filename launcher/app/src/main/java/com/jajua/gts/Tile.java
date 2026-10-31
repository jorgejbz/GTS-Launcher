package com.jajua.gts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * One of the tall main shortcuts. Drawn directly on the canvas: a graphite panel, a faint accent
 * glow at the bottom (static, computed once per size) and a line icon. Pressing lights an accent bar.
 * An optional "?" badge in the corner opens connection help instead of the app.
 */
final class Tile extends View {
    private final Drawable icon;
    private String label;
    private String sub = "";
    private boolean available = true;
    private Runnable onHelp;

    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focus = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint badgePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF help = new RectF();
    private boolean helpTouch;

    Tile(Context c, int iconRes, String label) {
        super(c);
        Palette p = Palette.current;
        this.icon = Ui.icon(c, iconRes, p.text);
        this.label = label;
        setContentDescription(label);
        setClickable(true);
        setFocusable(true);

        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1, Ui.u(1)));
        bar.setColor(Palette.accent);
        focus.setStyle(Paint.Style.STROKE);
        focus.setStrokeWidth(Math.max(2, Ui.u(2)));
        badge.setStyle(Paint.Style.STROKE);
        badge.setStrokeWidth(Math.max(1, Ui.u(1.5f)));
        badge.setColor(p.subtext);

        labelPaint.setColor(p.text);
        labelPaint.setTypeface(Ui.medium);
        labelPaint.setTextSize(Ui.tf(25));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setColor(p.subtext);
        subPaint.setTypeface(Ui.regular);
        subPaint.setTextSize(Ui.tf(15));
        subPaint.setTextAlign(Paint.Align.CENTER);
        badgePaint.setColor(p.subtext);
        badgePaint.setTypeface(Ui.medium);
        badgePaint.setTextSize(Ui.tf(15));
        badgePaint.setTextAlign(Paint.Align.CENTER);
    }

    void setOnHelp(Runnable r) {
        onHelp = r;
        invalidate();
    }

    void setSub(String s, boolean available) {
        String next = s == null ? "" : s;
        if (next.equals(sub) && available == this.available) return;
        sub = next;
        this.available = available;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        Palette p = Palette.current;
        rect.set(0, 0, w, h);
        panel.setShader(new LinearGradient(0, 0, 0, h, p.panelTop, p.panelBottom, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(w / 2f, h * 1.05f, Math.max(1, w * 0.95f),
                Ui.withAlpha(Palette.accent, Palette.glowAlpha(Palette.glow)), 0x00000000, Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(0, 0, 0, h, Ui.withAlpha(0xFFFFFFFF, 0.16f),
                Ui.withAlpha(0xFFFFFFFF, 0.04f), Shader.TileMode.CLAMP));
        help.set(w - Ui.uf(48), 0, w, Ui.uf(48));
        help.inset(Ui.uf(10), Ui.uf(10));
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = Ui.uf(16);
        float w = getWidth();
        float h = getHeight();
        c.drawRoundRect(rect, r, r, panel);
        boolean lit = isPressed() && !helpTouch;
        glow.setAlpha(lit ? 255 : 150);
        c.drawRoundRect(rect, r, r, glow);
        c.drawRoundRect(rect, r, r, edge);
        if (isFocused()) {
            // Rotary knob / D-pad focus: thin accent outline.
            focus.setColor(Palette.accent);
            c.drawRoundRect(Ui.uf(1), Ui.uf(1), w - Ui.uf(1), h - Ui.uf(1), r, r, focus);
        }

        int size = (int) Math.min(Ui.uf(92), h * 0.2f);
        int cx = Math.round(w / 2f);
        int iy = Math.round(h * 0.40f) - size / 2;
        icon.setBounds(cx - size / 2, iy, cx + size / 2, iy + size);
        icon.setAlpha(available ? 255 : 110);
        icon.draw(c);

        float ly = h * 0.40f + size / 2f + Ui.uf(46);
        CharSequence l = TextUtils.ellipsize(label, labelPaint, w - Ui.uf(20), TextUtils.TruncateAt.END);
        c.drawText(l, 0, l.length(), w / 2f, ly, labelPaint);
        if (!sub.isEmpty()) {
            CharSequence s = TextUtils.ellipsize(sub, subPaint, w - Ui.uf(24), TextUtils.TruncateAt.END);
            c.drawText(s, 0, s.length(), w / 2f, ly + Ui.uf(28), subPaint);
        }

        if (lit) {
            bar.setAlpha(255);
            float bw = w * 0.36f;
            c.drawRoundRect(w / 2f - bw / 2f, h - Ui.uf(7), w / 2f + bw / 2f, h - Ui.uf(3),
                    Ui.uf(2), Ui.uf(2), bar);
        }
        if (onHelp != null) {
            badge.setColor(helpTouch && isPressed() ? Palette.accent : Palette.current.subtext);
            c.drawCircle(help.centerX(), help.centerY(), help.width() / 2f, badge);
            c.drawText("?", help.centerX(), help.centerY() - (badgePaint.ascent() + badgePaint.descent()) / 2f, badgePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                RectF hit = new RectF(help);
                hit.inset(-Ui.uf(10), -Ui.uf(10));
                helpTouch = onHelp != null && hit.contains(e.getX(), e.getY());
                setPressed(true);
                if (!helpTouch) animate().scaleX(0.975f).scaleY(0.975f).setDuration(90).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getX() < 0 || e.getY() < 0 || e.getX() > getWidth() || e.getY() > getHeight()) release();
                return true;
            case MotionEvent.ACTION_UP:
                boolean wasPressed = isPressed();
                boolean wasHelp = helpTouch;
                release();
                if (wasPressed) {
                    if (wasHelp) onHelp.run();
                    else performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
            default:
                return true;
        }
    }

    private void release() {
        setPressed(false);
        animate().scaleX(1f).scaleY(1f).setDuration(140).start();
        invalidate();
        // helpTouch stays until the next DOWN so the final frame draws correctly.
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            helpTouch = false;
            return performClick();
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        invalidate();
    }

}
