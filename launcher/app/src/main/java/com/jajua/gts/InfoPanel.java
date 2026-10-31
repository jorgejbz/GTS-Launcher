package com.jajua.gts;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Right-hand vehicle panel: speed, time and what is playing. Purely a view; HomeActivity feeds it. */
final class InfoPanel extends LinearLayout {
    private final TextView speed;
    private final TextView speedCaption;
    private final TextView speedUnit;
    private final TextView time;
    private final TextView date;
    private final TextView title;
    private final TextView artist;
    private final TextView source;
    private final LinearLayout controls;
    private final ImageView play;
    private final ImageView prev;
    private final ImageView next;
    private final Context c;

    InfoPanel(Context c, Runnable onPrev, Runnable onPlayPause, Runnable onNext, Runnable onMediaTap) {
        super(c);
        this.c = c;
        Palette p = Palette.current;
        setOrientation(VERTICAL);
        setBackground(Ui.panel(16));
        setPadding(Ui.u(24), Ui.u(22), Ui.u(24), Ui.u(20));

        addView(Ui.caption(c, "Velocidad"));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.BOTTOM);
        speed = Ui.text(c, 62, Ui.light, p.text);
        speed.setText("--");
        speedUnit = Ui.text(c, 19, Ui.regular, p.subtext);
        speedUnit.setText(" km/h");
        speedUnit.setPadding(0, 0, 0, Ui.u(8));
        row.addView(speed);
        row.addView(speedUnit);
        addView(row, lp(Ui.u(6)));
        speedCaption = Ui.text(c, 13, Ui.regular, p.dim);
        addView(speedCaption, lp(Ui.u(4)));

        addView(divider(), dividerLp());

        addView(Ui.caption(c, "Hora"));
        time = Ui.text(c, 42, Ui.light, p.text);
        addView(time, lp(Ui.u(6)));
        date = Ui.text(c, 15, Ui.regular, p.subtext);
        addView(date, lp(Ui.u(6)));

        addView(divider(), dividerLp());

        addView(Ui.caption(c, "Reproducción"));
        title = Ui.text(c, 19, Ui.medium, p.text);
        addView(title, lp(Ui.u(10)));
        artist = Ui.text(c, 15, Ui.regular, p.subtext);
        addView(artist, lp(Ui.u(5)));
        source = Ui.text(c, 13, Ui.regular, p.dim);
        addView(source, lp(Ui.u(5)));
        for (TextView t : new TextView[] {title, artist, source}) t.setOnClickListener(v -> onMediaTap.run());

        View spacer = new View(c);
        addView(spacer, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        controls = new LinearLayout(c);
        controls.setGravity(Gravity.CENTER);
        prev = control(R.drawable.ic_prev, "Anterior", onPrev);
        controls.addView(prev);
        play = control(R.drawable.ic_play, "Reproducir o pausar", onPlayPause);
        controls.addView(play);
        next = control(R.drawable.ic_next, "Siguiente", onNext);
        controls.addView(next);
        addView(controls, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void setSpeed(Integer value, String unit, String caption) {
        speed.setText(value == null ? "--" : String.valueOf(value));
        speedUnit.setText(" " + unit);
        speedCaption.setText(caption);
    }

    /** ampm: "AM"/"PM" for a 12-hour clock, drawn smaller after the time; null for 24 h. */
    void setTime(String t, String ampm, String d) {
        if (ampm == null) {
            time.setText(t);
        } else {
            android.text.SpannableString s = new android.text.SpannableString(t + " " + ampm);
            int start = t.length() + 1;
            s.setSpan(new android.text.style.RelativeSizeSpan(0.45f), start, s.length(), 0);
            s.setSpan(new android.text.style.ForegroundColorSpan(Palette.current.subtext), start, s.length(), 0);
            time.setText(s);
        }
        date.setText(d);
    }

    /**
     * What is playing and which buttons really work for that source. Buttons that the current
     * source cannot obey are hidden instead of shown dead.
     */
    /** playing: null when the source cannot tell (e.g. CarPlay), shown as a neutral play/pause icon. */
    void show(String songTitle, String songArtist, String sourceText, boolean skip, boolean playPause, Boolean playing) {
        title.setText(songTitle);
        artist.setText(songArtist != null ? songArtist : "");
        source.setText(sourceText != null ? sourceText : "");
        prev.setVisibility(skip ? VISIBLE : GONE);
        next.setVisibility(skip ? VISIBLE : GONE);
        play.setVisibility(playPause ? VISIBLE : GONE);
        int icon = playing == null ? R.drawable.ic_play_pause : playing ? R.drawable.ic_pause : R.drawable.ic_play;
        play.setImageDrawable(Ui.icon(c, icon, Palette.current.text));
        play.setContentDescription(playing == null ? "Reproducir o pausar" : playing ? "Pausar" : "Reproducir");
        controls.setVisibility(skip || playPause ? VISIBLE : INVISIBLE);
    }

    void setIdle(boolean access) {
        show(access ? "Nada en reproducción" : "Sin datos de reproducción",
                access ? "" : "Actívala en Ajustes › Multimedia", "", false, false, false);
    }

    private ImageView control(int res, String desc, Runnable action) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(Ui.icon(c, res, Palette.current.text));
        v.setContentDescription(desc);
        // Large enough to hit while driving.
        int pad = Ui.u(18);
        v.setPadding(pad, pad, pad, pad);
        v.setBackground(Ui.rounded(Ui.withAlpha(0xFFFFFFFF, 0.08f), 36));
        v.setOnClickListener(x -> action.run());
        Ui.pressable(v);
        LayoutParams lp = new LayoutParams(Ui.u(72), Ui.u(72));
        lp.setMargins(Ui.u(8), 0, Ui.u(8), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private View divider() {
        View d = new View(c);
        d.setBackgroundColor(Palette.current.edge);
        return d;
    }

    private static LayoutParams dividerLp() {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.u(1)));
        lp.setMargins(0, Ui.u(18), 0, Ui.u(16));
        return lp;
    }

    private static LayoutParams lp(int top) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }
}
