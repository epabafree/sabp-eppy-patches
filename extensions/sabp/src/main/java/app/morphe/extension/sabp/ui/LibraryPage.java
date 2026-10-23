package app.morphe.extension.sabp.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.sabp.EppyBridge;
import app.morphe.extension.sabp.EppyPlayer;
import app.morphe.extension.sabp.EppyTheme;

/** Library: segmented filter, sort, and an adaptive cover grid. */
public final class LibraryPage {
    private static final String[] FILTERS = {"All", "Listening", "New", "Finished"};
    private final Host host;
    private final HomePage cards;
    private final Context c;
    private final EppyTheme t;
    public final ScrollView root;
    private final LinearLayout grid;
    private final LinearLayout segments;
    private int filter;
    private boolean sortAz;
    private int widthPx;

    public LibraryPage(Host host, HomePage cards) {
        this.host = host;
        this.cards = cards;
        this.c = host.activity();
        this.t = host.theme();
        root = new ScrollView(c);
        root.setFillViewport(true);
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 140));
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = Ui.row(c);
        TextView h = Ui.text(c, t, "Library", 30, 600, t.onSurface);
        h.setAccessibilityHeading(true);
        header.addView(h, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(Ui.iconButton(c, t, EppyIcon.FOLDER, t.onSurface, t.surfaceContainerHigh, "Manage library folders",
                v -> host.openSabp(EppyBridge.LIBRARY_ACTIVITY)));
        col.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 56)));

        segments = Ui.row(c);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 48));
        sl.topMargin = Ui.dp(c, 16);
        col.addView(segments, sl);

        LinearLayout sortRow = Ui.row(c);
        sortRow.setPadding(0, Ui.dp(c, 12), 0, 0);
        col.addView(sortRow);
        sortRow.addView(Ui.chip(c, t, EppyIcon.HISTORY, "Recent", !sortAz, v -> {
            sortAz = false;
            rebuildAll(sortRow);
        }));
        sortRow.addView(Ui.space(c, 8, 1));
        sortRow.addView(Ui.chip(c, t, EppyIcon.LIST, "A–Z", sortAz, v -> {
            sortAz = true;
            rebuildAll(sortRow);
        }));

        grid = Ui.column(c);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = Ui.dp(c, 16);
        col.addView(grid, gl);
        buildSegments();
        rebuild();
    }

    private void rebuildAll(LinearLayout sortRow) {
        sortRow.removeAllViews();
        sortRow.addView(Ui.chip(c, t, EppyIcon.HISTORY, "Recent", !sortAz, v -> {
            sortAz = false;
            rebuildAll(sortRow);
        }));
        sortRow.addView(Ui.space(c, 8, 1));
        sortRow.addView(Ui.chip(c, t, EppyIcon.LIST, "A–Z", sortAz, v -> {
            sortAz = true;
            rebuildAll(sortRow);
        }));
        rebuild();
    }

    /** Material 3 segmented button: connected outlined segments, selected one filled. */
    private void buildSegments() {
        segments.removeAllViews();
        float r = Ui.dp(c, 24);
        for (int i = 0; i < FILTERS.length; i++) {
            final int idx = i;
            boolean sel = i == filter;
            TextView seg = Ui.text(c, t, FILTERS[i], 14, sel ? 600 : 500, sel ? t.onSecondaryContainer : t.onSurface);
            seg.setGravity(Gravity.CENTER);
            seg.setMaxLines(1);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(sel ? t.secondaryContainer : 0);
            bg.setStroke(Ui.dp(c, 1), t.outline);
            float left = i == 0 ? r : 0, right = i == FILTERS.length - 1 ? r : 0;
            bg.setCornerRadii(new float[]{left, left, right, right, right, right, left, left});
            seg.setBackground(Ui.ripple(t, bg, 0));
            seg.setClickable(true);
            seg.setFocusable(true);
            seg.setContentDescription(FILTERS[i] + (sel ? ", selected" : ""));
            seg.setOnClickListener(v -> {
                Ui.haptic(v, t);
                filter = idx;
                buildSegments();
                rebuild();
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i > 0) lp.leftMargin = -Ui.dp(c, 1);
            segments.addView(seg, lp);
        }
    }

    public void setWidth(int px) {
        if (px != widthPx) {
            widthPx = px;
            rebuild();
        }
    }

    public void rebuild() {
        grid.removeAllViews();
        EppyPlayer p = host.player();
        List<EppyPlayer.Book> books = new ArrayList<>();
        switch (filter) {
            case 1:
                books.addAll(p.shelf(EppyPlayer.STATE_STARTED));
                break;
            case 2:
                books.addAll(p.shelf(EppyPlayer.STATE_NEW));
                break;
            case 3:
                books.addAll(p.shelf(EppyPlayer.STATE_FINISHED));
                break;
            default:
                books.addAll(p.allBooks());
        }
        if (sortAz) Collections.sort(books, (a, b) -> a.title.compareToIgnoreCase(b.title));

        if (books.isEmpty()) {
            TextView e = Ui.text(c, t, p.isLibraryLoading() ? "Loading…" : "No books here yet", 16, 400, t.onSurfaceVariant);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, Ui.dp(c, 48), 0, Ui.dp(c, 16));
            grid.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (p.libraryEmpty()) {
                TextView b = cards.filledButton("Set up library", v -> host.openSabp(EppyBridge.LIBRARY_ACTIVITY));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 48));
                bl.gravity = Gravity.CENTER_HORIZONTAL;
                grid.addView(b, bl);
            }
            return;
        }

        int avail = widthPx > 0 ? widthPx - Ui.dp(c, 40) : c.getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 40);
        int gap = Ui.dp(c, 14);
        int cols = Math.max(2, Math.min(8, (avail + gap) / (Ui.dp(c, 150) + gap)));
        int cell = (avail - gap * (cols - 1)) / cols;
        int cellDp = Math.round(cell / c.getResources().getDisplayMetrics().density);
        LinearLayout row = null;
        for (int i = 0; i < books.size(); i++) {
            if (i % cols == 0) {
                row = Ui.row(c);
                row.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.bottomMargin = Ui.dp(c, 18);
                grid.addView(row, rl);
            }
            View card = cards.bookCard(books.get(i), cellDp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cell, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % cols != cols - 1) lp.rightMargin = gap;
            row.addView(card, lp);
        }
    }
}
