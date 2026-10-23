package app.morphe.extension.sabp.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.sabp.EppyTheme;

/**
 * YouTube Music–style player sheet: a tab strip peeks at the bottom of Now Playing and drags (or
 * taps) up into a tall sheet with Chapters, Read along, Bookmarks, Details and Up next.
 * Opening or closing it never touches playback.
 */
public final class PlayerSheet {
    public static final int TAB_CHAPTERS = 0, TAB_READ = 1, TAB_BOOKMARKS = 2, TAB_DETAILS = 3, TAB_UP_NEXT = 4;
    private static final String[] TABS = {"Chapters", "Read along", "Bookmarks", "Details", "Up next"};

    public interface Tab {
        View view();

        /** Called when the tab becomes visible or playback changes (full) or every tick (!full). */
        void refresh(boolean full);
    }

    private final Context c;
    private final EppyTheme t;
    private final Host host;
    public final FrameLayout root;     // sized to the whole player; sheet slides inside it
    private final LinearLayout sheet;
    private final LinearLayout tabStrip;
    private final FrameLayout content;
    private final Tab[] tabs = new Tab[TABS.length];
    private int current = TAB_CHAPTERS;
    private boolean expanded;
    private final int peek;
    private float downY, startTranslation;
    private boolean dragging;
    private VelocityTracker velocity;

    public PlayerSheet(Host host, EppyTheme t) {
        this.host = host;
        this.c = host.activity();
        this.t = t;
        peek = Ui.dp(c, 76);

        root = new FrameLayout(c);
        root.setClipChildren(true);

        sheet = Ui.column(c);
        float r = Ui.dp(c, 28);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(t.surfaceContainerLow);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setElevation(Ui.dp(c, 8));
        sheet.setClickable(true);

        LinearLayout header = Ui.column(c);
        header.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 6));
        View handle = new View(c);
        handle.setBackground(Ui.rounded(t.outline, Ui.dp(c, 2)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = Ui.dp(c, 8);
        header.addView(handle, hl);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        tabStrip = Ui.row(c);
        tabStrip.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        hs.addView(tabStrip);
        header.addView(hs);
        header.setContentDescription("Player sheet. Drag up or pick a tab.");
        header.setOnTouchListener(this::onHeaderTouch);
        sheet.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, peek));

        content = new FrameLayout(c);
        // The sheet is as tall as the player and slides down by topOffset when open.
        content.setPadding(0, 0, 0, Ui.dp(c, 56));
        sheet.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sheet.setVisibility(View.INVISIBLE); // shown after the first layout puts it in the peek position
        root.addOnLayoutChangeListener((v, l, tp, rt, b, ol, ot, or, ob) -> {
            if (b - tp != ob - ot) {
                snap(expanded, false);
                sheet.setVisibility(View.VISIBLE);
            }
        });
        buildTabs();
    }

    public void setTab(int index, Tab tab) {
        tabs[index] = tab;
    }

    public int peekHeight() {
        return peek;
    }

    public boolean isExpanded() {
        return expanded;
    }

    private void buildTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean sel = expanded && i == current;
            TextView tv = Ui.text(c, t, TABS[i], 14, sel ? 650 : 500, sel ? t.onSecondaryContainer : t.onSurfaceVariant);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
            tv.setMinHeight(Ui.dp(c, 40));
            tv.setBackground(Ui.ripple(t, Ui.rounded(sel ? t.secondaryContainer : 0, Ui.dp(c, 20)), Ui.dp(c, 20)));
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setContentDescription(TABS[i] + (sel ? ", selected" : ""));
            tv.setOnClickListener(v -> {
                Ui.haptic(v, t);
                show(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40));
            lp.rightMargin = Ui.dp(c, 4);
            tabStrip.addView(tv, lp);
        }
    }

    /** Opens the sheet on a tab. */
    public void show(int index) {
        boolean switching = index != current || content.getChildCount() == 0;
        current = index;
        if (switching) {
            content.removeAllViews();
            Tab tab = tabs[index];
            if (tab != null) {
                View v = tab.view();
                if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
                content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                tab.refresh(true);
            }
        }
        snap(true, true);
    }

    public void collapse() {
        snap(false, true);
    }

    private void snap(boolean open, boolean animate) {
        boolean changed = open != expanded;
        expanded = open;
        float target = open ? topOffset() : collapsedOffset();
        if (!animate || t.reduceMotion || root.getHeight() == 0) {
            sheet.animate().cancel();
            sheet.setTranslationY(target);
        } else {
            sheet.animate().translationY(target).setDuration(280).setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        }
        if (changed) {
            buildTabs();
            sheet.announceForAccessibility(open ? TABS[current] + " opened" : "Sheet closed");
        }
    }

    private float collapsedOffset() {
        return Math.max(0, root.getHeight() - peek);
    }

    private float topOffset() {
        return Ui.dp(c, 56);
    }

    private boolean onHeaderTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getRawY();
                startTranslation = sheet.getTranslationY();
                dragging = false;
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(e);
                float dy = e.getRawY() - downY;
                if (!dragging && Math.abs(dy) > Ui.dp(c, 6)) {
                    dragging = true;
                    if (content.getChildCount() == 0) show(current);
                    sheet.animate().cancel();
                }
                if (dragging) {
                    float y = Math.max(topOffset(), Math.min(collapsedOffset(), startTranslation + dy));
                    sheet.setTranslationY(y);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float vy = 0;
                if (velocity != null) {
                    velocity.addMovement(e);
                    velocity.computeCurrentVelocity(1000);
                    vy = velocity.getYVelocity();
                    velocity.recycle();
                    velocity = null;
                }
                if (!dragging) {
                    if (!expanded) show(current);
                    else collapse();
                    return true;
                }
                float mid = (topOffset() + collapsedOffset()) / 2f;
                boolean open = Math.abs(vy) > Ui.dp(c, 400) ? vy < 0 : sheet.getTranslationY() < mid;
                if (open) show(current);
                else collapse();
                return true;
            }
            default:
                return false;
        }
    }

    /** Back press: collapse if open. */
    public boolean onBack() {
        if (expanded) {
            collapse();
            return true;
        }
        return false;
    }

    public void refresh(boolean full) {
        if (expanded && tabs[current] != null) tabs[current].refresh(full);
    }

    public int currentTab() {
        return current;
    }
}
