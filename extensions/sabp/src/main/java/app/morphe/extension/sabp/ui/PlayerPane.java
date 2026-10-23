package app.morphe.extension.sabp.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.morphe.extension.sabp.EppyBridge;
import app.morphe.extension.sabp.EppyPlayer;
import app.morphe.extension.sabp.EppyTheme;

/**
 * The full "Now Playing" screen: large cover with a soft accent glow, expressive title, thick
 * seek slider, big morphing play button, and chips for secondary controls.
 * Used as an overlay on phones and as a persistent side pane on large screens.
 */
public final class PlayerPane {
    private final Host host;
    private final Context c;
    private final EppyTheme t;
    private final boolean embedded;
    public final View root;

    private final ImageView cover;
    private final TextView title, chapter, elapsed, remaining, bookLine, speedLabel;
    private final EppySlider slider;
    private final PlayButton play;
    private final FrameLayout coverFrame;
    private Bitmap lastCover;
    private String lastChapter;
    private boolean lastPlaying;
    private boolean sleepOn;
    private LinearLayout sleepChip;
    private final LinearLayout chipRow;

    public PlayerPane(Host host, boolean embedded) {
        this.host = host;
        this.c = host.activity();
        this.t = host.theme();
        this.embedded = embedded;

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(t.surface);
        LinearLayout col = Ui.column(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 24));
        scroll.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root = scroll;

        // Top app bar.
        LinearLayout bar = Ui.row(c);
        if (!embedded) {
            bar.addView(Ui.iconButton(c, t, EppyIcon.CHEVRON_DOWN, t.onSurface, 0, "Collapse player", v -> host.closePlayer()));
        } else {
            bar.addView(Ui.space(c, 48, 48));
        }
        TextView label = Ui.text(c, t, "Now Playing", 16, 600, t.onSurface);
        label.setGravity(Gravity.CENTER);
        bar.addView(label, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(Ui.iconButton(c, t, EppyIcon.MORE, t.onSurface, 0, "More options", v -> host.showMoreSheet()));
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Cover with radial accent glow behind it.
        coverFrame = new FrameLayout(c);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{EppyTheme.withAlpha(t.primary, t.dark ? 0.38f : 0.28f), EppyTheme.withAlpha(t.primary, 0f)});
        coverFrame.setBackground(glow);
        cover = Ui.cover(c, t, null, 32);
        cover.setElevation(Ui.dp(c, 8));
        cover.setContentDescription("Book cover");
        coverFrame.addView(cover);
        col.addView(coverFrame);

        title = Ui.text(c, t, "", 26, 650, t.onSurface);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, 20);
        col.addView(title, tl);

        chapter = Ui.singleLine(Ui.text(c, t, "", 15, 400, t.onSurfaceVariant));
        chapter.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(c, 8);
        col.addView(chapter, cl);

        bookLine = Ui.singleLine(Ui.text(c, t, "", 12, 500, t.primary));
        bookLine.setGravity(Gravity.CENTER);
        bookLine.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(c, 6);
        col.addView(bookLine, bl);

        slider = new EppySlider(c);
        slider.apply(t, t.waveformSeekBar);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 56));
        sl.topMargin = Ui.dp(c, 20);
        col.addView(slider, sl);
        slider.setListener(new EppySlider.OnSeekListener() {
            @Override
            public void onSeekPreview(long ms) {
                setTimes(ms, host.player().durationMs());
            }

            @Override
            public void onSeek(long ms) {
                host.player().seekTo(ms);
            }
        });

        LinearLayout times = Ui.row(c);
        elapsed = Ui.text(c, t, "0:00", 13, 500, t.primary);
        remaining = Ui.text(c, t, "", 13, 500, t.onSurfaceVariant);
        remaining.setGravity(Gravity.END);
        times.addView(elapsed, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        times.addView(remaining, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        times.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        col.addView(times, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Transport controls.
        LinearLayout controls = Ui.row(c);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(c, t, EppyIcon.SKIP_PREV, t.onSurface, 0, "Previous file",
                v -> host.player().command(EppyBridge.ACTION_PREV_FILE)));
        controls.addView(Ui.space(c, 8, 1));
        controls.addView(bigIcon(EppyIcon.REWIND, "Rewind", EppyBridge.ACTION_REWIND_SMALL));
        controls.addView(Ui.space(c, 16, 1));
        play = new PlayButton(c);
        play.apply(t);
        play.setOnClickListener(v -> {
            Ui.haptic(v, t);
            host.player().playPause();
            play.setPlaying(!lastPlaying, true);
            lastPlaying = !lastPlaying;
        });
        controls.addView(play, new LinearLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88)));
        controls.addView(Ui.space(c, 16, 1));
        controls.addView(bigIcon(EppyIcon.FORWARD, "Fast forward", EppyBridge.ACTION_FWD_SMALL));
        controls.addView(Ui.space(c, 8, 1));
        controls.addView(Ui.iconButton(c, t, EppyIcon.SKIP_NEXT, t.onSurface, 0, "Next file",
                v -> host.player().command(EppyBridge.ACTION_NEXT_FILE)));
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctl.topMargin = Ui.dp(c, 16);
        col.addView(controls, ctl);

        // Secondary controls as chips.
        HorizontalScrollView chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = Ui.row(c);
        chipScroll.addView(chipRow);
        speedLabel = null;
        LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chl.topMargin = Ui.dp(c, 24);
        chl.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(chipScroll, chl);
        buildChips();

        update(true);
    }

    private View bigIcon(int icon, String desc, String action) {
        FrameLayout b = Ui.iconButton(c, t, icon, t.onSecondaryContainer, t.secondaryContainer, desc,
                v -> host.player().command(action));
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
        View icv = b.getChildAt(0);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28), Gravity.CENTER);
        icv.setLayoutParams(il);
        return b;
    }

    private void buildChips() {
        chipRow.removeAllViews();
        float speed = host.player().speed();
        addChip(Ui.chip(c, t, EppyIcon.SPEED, formatSpeed(speed), false,
                v -> host.player().command(EppyBridge.ACTION_TOGGLE_SPEED)));
        sleepChip = Ui.chip(c, t, EppyIcon.MOON, sleepOn ? "Sleep on" : "Sleep", sleepOn, v -> {
            sleepOn = !sleepOn;
            host.player().command(sleepOn ? EppyBridge.ACTION_ENABLE_SLEEP : EppyBridge.ACTION_DISABLE_SLEEP);
            buildChips();
        });
        addChip(sleepChip);
        addChip(Ui.chip(c, t, EppyIcon.BOOKMARK_ADD, "Bookmark", false, v -> {
            host.player().command(EppyBridge.ACTION_ADD_BOOKMARK);
            v.announceForAccessibility("Bookmark added");
            android.widget.Toast.makeText(c, "Bookmark added", android.widget.Toast.LENGTH_SHORT).show();
        }));
        addChip(Ui.chip(c, t, EppyIcon.BOOKMARK, "Bookmarks", false, v -> host.openSabp(EppyBridge.BOOKMARKS_ACTIVITY)));
        addChip(Ui.chip(c, t, EppyIcon.LIST, "Chapters", false, v -> host.openSabp(EppyBridge.PLAYER_ACTIVITY)));
    }

    private void addChip(View chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(c, 8);
        chipRow.addView(chip, lp);
    }

    private static String formatSpeed(float s) {
        String v = String.format(java.util.Locale.US, "%.2f", s);
        while (v.endsWith("0")) v = v.substring(0, v.length() - 1);
        if (v.endsWith(".")) v = v.substring(0, v.length() - 1);
        return v + "×";
    }

    /** Sizes the cover to the available width (also called on layout changes). */
    public void layoutCover(int availableWidthPx) {
        int max = Ui.dp(c, embedded ? 320 : 360);
        int size = Math.max(Ui.dp(c, 160), Math.min(max, availableWidthPx - Ui.dp(c, 96)));
        int glowPad = Ui.dp(c, 28);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(size + glowPad * 2, size + glowPad * 2);
        fl.topMargin = Ui.dp(c, 4);
        coverFrame.setLayoutParams(fl);
        cover.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
    }

    private void setTimes(long pos, long dur) {
        elapsed.setText(Ui.time(pos));
        remaining.setText(dur > 0 ? "-" + Ui.time(Math.max(0, dur - pos)) : "");
    }

    /** Refreshes from the player. full = also metadata (cheap per-second ticks pass false). */
    public void update(boolean full) {
        EppyPlayer p = host.player();
        long dur = p.durationMs();
        long pos = p.positionMs();
        slider.setProgress(pos, dur);
        slider.setSeekEnabled(p.canSeek() && dur > 0);
        setTimes(pos, dur);
        boolean playing = p.isPlaying();
        if (playing != lastPlaying || full) {
            play.setPlaying(playing, !full);
            if (playing != lastPlaying && !full) root.announceForAccessibility(playing ? "Playing" : "Paused");
            lastPlaying = playing;
        }
        if (!full) return;
        String book = p.bookTitle();
        String ch = p.chapterTitle();
        title.setText(book != null ? book : "Nothing playing");
        if (ch != null && book != null && ch.equals(book)) ch = null;
        chapter.setText(ch != null ? ch : (book == null ? "Pick a book from your library" : ""));
        if (lastChapter != null && ch != null && !ch.equals(lastChapter)) {
            root.announceForAccessibility("Now playing " + ch);
        }
        lastChapter = ch;
        float prog = p.bookProgress();
        int rem = p.bookRemainingSeconds();
        if (prog >= 0) {
            bookLine.setText(Math.round(prog * 100) + "% COMPLETE" + (rem >= 0 ? "  ·  " + Ui.duration(rem).toUpperCase() + " LEFT" : ""));
            bookLine.setVisibility(View.VISIBLE);
        } else {
            bookLine.setVisibility(View.GONE);
        }
        Bitmap b = p.cover();
        if (b != lastCover) {
            Ui.setCover(cover, t, b);
            lastCover = b;
        }
        buildChips();
    }
}
