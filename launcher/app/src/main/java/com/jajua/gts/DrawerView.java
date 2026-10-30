package com.jajua.gts;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/** Full-screen app grid shown over the home screen (no extra activity, so it opens instantly). */
final class DrawerView extends LinearLayout {
    interface Callbacks {
        void onAppClick(AppCatalog.App app);

        void onAppLongClick(AppCatalog.App app);

        void onClose();
    }

    private final Context c;
    private final GridView grid;
    private final TextView count;
    private List<AppCatalog.App> apps = Collections.emptyList();
    private final boolean originalIcons;

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                LinearLayout cell = new LinearLayout(c);
                cell.setOrientation(VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(Ui.u(6), Ui.u(14), Ui.u(6), Ui.u(10));
                cell.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Ui.u(156)));
                h = new Holder();
                // Same graphite tile as the dock buttons; inside, the launcher's line icon, the
                // app's initial, or (if chosen in Ajustes) the original icon.
                h.tile = new android.widget.FrameLayout(c);
                h.tile.setBackground(Ui.panel(18));
                h.icon = new ImageView(c);
                h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.tile.addView(h.icon, new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
                h.letter = Ui.text(c, 34, Ui.light, Palette.current.text);
                h.letter.setGravity(Gravity.CENTER);
                h.tile.addView(h.letter, new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
                cell.addView(h.tile, new LayoutParams(Ui.u(86), Ui.u(86)));
                h.label = Ui.text(c, 15, Ui.regular, Palette.current.text);
                h.label.setSingleLine(false);
                h.label.setMaxLines(2);
                h.label.setGravity(Gravity.CENTER_HORIZONTAL);
                LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                tl.topMargin = Ui.u(10);
                cell.addView(h.label, tl);
                cell.setTag(h);
                convert = cell;
            } else {
                h = (Holder) convert.getTag();
            }
            AppCatalog.App app = apps.get(position);
            int glyph = originalIcons ? 0 : AppGlyphs.forApp(app.pkg, app.label);
            if (originalIcons) {
                h.tile.setBackground(null);
                h.icon.setPadding(0, 0, 0, 0);
                h.icon.setImageDrawable(app.icon);
                h.letter.setVisibility(GONE);
            } else if (glyph != 0) {
                h.tile.setBackground(Ui.panel(18));
                int pad = Ui.u(22);
                h.icon.setPadding(pad, pad, pad, pad);
                h.icon.setImageDrawable(Ui.icon(c, glyph, Palette.current.text));
                h.letter.setVisibility(GONE);
            } else {
                h.tile.setBackground(Ui.panel(18));
                h.icon.setImageDrawable(null);
                h.letter.setVisibility(VISIBLE);
                h.letter.setText(app.label.isEmpty() ? "?" : app.label.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            }
            h.label.setText(app.label);
            convert.setContentDescription(app.label);
            return convert;
        }
    };

    private static final class Holder {
        android.widget.FrameLayout tile;
        TextView letter;
        ImageView icon;
        TextView label;
    }

    DrawerView(Context c, Callbacks callbacks) {
        super(c);
        this.c = c;
        this.originalIcons = Prefs.get(c).originalIcons();
        Palette p = Palette.current;
        setOrientation(VERTICAL);
        setBackgroundColor(p.bg);
        setClickable(true); // Swallow touches so nothing behind the drawer reacts.

        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout home = new LinearLayout(c);
        home.setGravity(Gravity.CENTER_VERTICAL);
        home.setBackground(Ui.rounded(Ui.withAlpha(0xFFFFFFFF, 0.07f), 22));
        home.setPadding(Ui.u(14), Ui.u(8), Ui.u(20), Ui.u(8));
        ImageView hi = new ImageView(c);
        hi.setImageDrawable(Ui.icon(c, R.drawable.ic_home, p.text));
        home.addView(hi, new LayoutParams(Ui.u(26), Ui.u(26)));
        TextView ht = Ui.text(c, 17, Ui.medium, p.text);
        ht.setText("Inicio");
        ht.setPadding(Ui.u(10), 0, 0, 0);
        home.addView(ht);
        home.setContentDescription("Volver al inicio");
        home.setOnClickListener(v -> callbacks.onClose());
        Ui.pressable(home);
        header.addView(home);

        TextView title = Ui.text(c, 22, Ui.medium, p.text);
        title.setText("Aplicaciones");
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        count = Ui.text(c, 15, Ui.regular, p.subtext);
        count.setMinWidth(Ui.u(120));
        count.setGravity(Gravity.END);
        header.addView(count);
        LayoutParams hl = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.u(64));
        hl.setMargins(Ui.u(24), Ui.u(10), Ui.u(28), 0);
        addView(header, hl);

        grid = new GridView(c);
        grid.setNumColumns(7);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.u(6));
        grid.setHorizontalSpacing(Ui.u(6));
        grid.setSelector(Ui.rounded(Ui.withAlpha(Palette.accent, 0.18f), 14));
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(true);
        grid.setOverScrollMode(OVER_SCROLL_NEVER);
        grid.setClipToPadding(false);
        grid.setPadding(Ui.u(24), Ui.u(6), Ui.u(24), Ui.u(24));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> callbacks.onAppClick(apps.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            callbacks.onAppLongClick(apps.get(position));
            return true;
        });
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    void setApps(List<AppCatalog.App> list) {
        apps = list;
        count.setText(list.size() == 1 ? "1 app" : list.size() + " apps");
        adapter.notifyDataSetChanged();
    }

    void scrollToTop() {
        grid.setSelection(0);
    }
}
