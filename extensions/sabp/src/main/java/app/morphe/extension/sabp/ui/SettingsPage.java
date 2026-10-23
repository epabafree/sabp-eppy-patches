package app.morphe.extension.sabp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import app.morphe.extension.sabp.EppyBridge;
import app.morphe.extension.sabp.EppyTheme;

/** Eppy UI settings plus shortcuts into SABP's own settings and tools. */
public final class SettingsPage {
    private final Host host;
    private final Context c;
    private final EppyTheme t;
    public final ScrollView root;
    private final LinearLayout col;

    private static final int[] SEEDS = {0xFFE8735A, 0xFFF2B8B5, 0xFFD0BCFF, 0xFF8AB4F8, 0xFF7DD3C0, 0xFFA8C97F, 0xFFF5C26B, 0xFFB0A8A0};

    public SettingsPage(Host host) {
        this.host = host;
        this.c = host.activity();
        this.t = host.theme();
        root = new ScrollView(c);
        root.setFillViewport(true);
        col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 140));
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        build();
    }

    private SharedPreferences prefs() {
        return EppyTheme.prefs(c);
    }

    private void build() {
        TextView h = Ui.text(c, t, "Settings", 30, 600, t.onSurface);
        h.setAccessibilityHeading(true);
        h.setMinHeight(Ui.dp(c, 56));
        h.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(h);

        LinearLayout look = group("Appearance");
        look.addView(label("Style"));
        look.addView(skinPicker());
        look.addView(nameField());
        if (!EppyTheme.SKIN_MATERIAL.equals(t.skin)) {
            TextView note = Ui.text(c, t, "Theme and accent below apply to the Material You style. Other styles use their own fixed palette.", 13, 400, t.onSurfaceVariant);
            note.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
            look.addView(note);
        }
        look.addView(label("Theme"));
        look.addView(segmented(new String[]{"System", "Light", "Dark", "Black"},
                new String[]{EppyTheme.MODE_SYSTEM, EppyTheme.MODE_LIGHT, EppyTheme.MODE_DARK, EppyTheme.MODE_BLACK},
                t.mode, "mode"));
        look.addView(label("Accent colour"));
        look.addView(segmented(new String[]{"Wallpaper", "Book cover", "Custom"},
                new String[]{EppyTheme.ACCENT_WALLPAPER, EppyTheme.ACCENT_COVER, EppyTheme.ACCENT_CUSTOM},
                t.accentSource, "accent"));
        if (EppyTheme.ACCENT_CUSTOM.equals(t.accentSource) || android.os.Build.VERSION.SDK_INT < 31) {
            look.addView(swatches());
        }
        look.addView(toggle("High contrast", "Stronger text and outlines", "highContrast", t.highContrast));
        look.addView(toggle("Waveform seek bar", "A static waveform instead of the slider track", "waveform", t.waveformSeekBar));

        LinearLayout motion = group("Motion and feedback");
        motion.addView(toggle("Reduce motion", "Skip transitions and shape animations", "reduceMotion", t.reduceMotion));
        motion.addView(toggle("Haptic feedback", "Vibrate lightly on taps", "haptics", t.haptics));

        LinearLayout sabp = group("Smart AudioBook Player");
        sabp.addView(link(EppyIcon.TUNE, "Player settings", "Playback, sleep timer, skip, headset buttons", EppyBridge.SETTINGS_ACTIVITY));
        sabp.addView(link(EppyIcon.FOLDER, "Library manager", "Folders, finished books, covers, files", EppyBridge.LIBRARY_ACTIVITY));
        sabp.addView(link(EppyIcon.CHART, "Listening statistics", "Time listened per day and book", EppyBridge.STATS_ACTIVITY));
        sabp.addView(link(EppyIcon.HISTORY, "History", "Recent position changes, undo jumps", EppyBridge.HISTORY_ACTIVITY));
        sabp.addView(link(EppyIcon.QUEUE, "Book queue", "Books to play next", EppyBridge.QUEUE_ACTIVITY));
        sabp.addView(link(EppyIcon.CLASSIC, "Classic player", "The original Smart AudioBook Player screen", EppyBridge.PLAYER_ACTIVITY));

        LinearLayout keys = group("Keyboard shortcuts");
        TextView k = Ui.text(c, t,
                "Space or K  Play / pause\nJ or ←  Rewind     L or →  Fast forward\nN  Next file     P  Previous file\nB  Add bookmark     S  Sleep timer\nCtrl+F  Search     1–4  Switch tabs     Esc  Back",
                14, 400, t.onSurfaceVariant);
        k.setLineSpacing(Ui.dp(c, 6), 1f);
        k.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 16));
        keys.addView(k);

        LinearLayout credits = group("Credits and open source");
        credit(credits, "Smart AudioBook Player", "The app this interface runs on. Playback, library, bookmarks and settings are all SABP's own. Eppy only adds a new interface on top.");
        credit(credits, "Hooman's Morphe Patches · github.com/arandomhooman/hoomans-morphe-patches",
                "GPL-3.0. Eppy is designed to be used together with Hooman's SABP patch, and it's tested on Hooman's SABP 11.7.8 build. Not affiliated with or endorsed by Hooman.");
        credit(credits, "Morphe patches template · github.com/MorpheApp", "GPL-3.0. Project and build setup. Eppy patches are for use with Morphe and aren't made by the Morphe project.");
        credit(credits, "Outfit font · github.com/Outfitio/Outfit-Fonts", "© 2021 The Outfit Project Authors · SIL Open Font License 1.1");
        credit(credits, "Plus Jakarta Sans font · github.com/tokotype/PlusJakartaSans", "© 2020 The Plus Jakarta Sans Project Authors · SIL Open Font License 1.1");
        credit(credits, "Storyteller · gitlab.com/storyteller-platform/storyteller",
                "MIT. Not bundled. Read along can use the narration timings (EPUB 3 Media Overlays) in \"readaloud\" EPUBs that Storyteller creates, for exact sync.");
        credit(credits, "Android speech recognition and PdfRenderer", "Built-in Android APIs. They match narration to text and read PDF text on the device. Nothing is uploaded.");

        TextView about = Ui.text(c, t, "Eppy UI · patches for Smart AudioBook Player, for use with Morphe", 12, 400, t.onSurfaceVariant);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, Ui.dp(c, 24), 0, 0);
        col.addView(about);
    }

    private LinearLayout group(String title) {
        TextView tv = Ui.text(c, t, title, 14, 600, t.primary);
        tv.setAccessibilityHeading(true);
        tv.setPadding(Ui.dp(c, 4), Ui.dp(c, 24), 0, Ui.dp(c, 8));
        col.addView(tv);
        LinearLayout box = Ui.column(c);
        box.setBackground(Ui.rounded(t.surfaceContainer, Ui.dp(c, 24)));
        box.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        col.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** Style cards: a miniature of each reference design (background, accent, card). */
    private View skinPicker() {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 12));
        int[][] sw = {
                {0xFF3B3346, 0xFFD0BCFF, 0xFF4A4458},
                {0xFF0E0E10, 0xFFEE6A50, 0xFF222226},
                {0xFF2C2350, 0xFFD5C8FA, 0xFF3A3166},
                {0xFFF5F1FB, 0xFF8B2BE2, 0xFFFFFFFF},
                {0xFFF2ECE3, 0xFFD9493E, 0xFFFBF8F3},
                {0xFFF3F3F3, 0xFFF4C430, 0xFF151515}};
        for (int i = 0; i < EppyTheme.SKINS.length; i++) {
            final String skin = EppyTheme.SKINS[i];
            boolean sel = skin.equals(t.skin);
            LinearLayout card = Ui.column(c);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout mini = new FrameLayout(c);
            mini.setBackground(Ui.outlined(sw[i][0], sel ? t.primary : t.outlineVariant, Ui.dp(c, sel ? 3 : 1), Ui.dp(c, 16)));
            View cardV = new View(c);
            cardV.setBackground(Ui.rounded(sw[i][2], Ui.dp(c, 6)));
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 30), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            cl.topMargin = Ui.dp(c, 12);
            mini.addView(cardV, cl);
            View dot = new View(c);
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            d.setColor(sw[i][1]);
            dot.setBackground(d);
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            dl.bottomMargin = Ui.dp(c, 10);
            mini.addView(dot, dl);
            card.addView(mini, new LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 96)));
            TextView name = Ui.text(c, t, EppyTheme.SKIN_NAMES[i], 12, sel ? 650 : 500, sel ? t.onSurface : t.onSurfaceVariant);
            name.setPadding(0, Ui.dp(c, 6), 0, 0);
            card.addView(name);
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription(EppyTheme.SKIN_NAMES[i] + " style" + (sel ? ", selected" : ""));
            card.setOnClickListener(v -> {
                Ui.haptic(v, t);
                prefs().edit().putString("skin", skin).apply();
                host.rebuild();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(c, 10);
            row.addView(card, lp);
        }
        hs.addView(row);
        return hs;
    }

    private View nameField() {
        android.widget.EditText e = new android.widget.EditText(c);
        e.setHint("Your name (for the greeting)");
        e.setText(t.userName);
        e.setSingleLine(true);
        e.setTextColor(t.onSurface);
        e.setHintTextColor(t.onSurfaceVariant);
        e.setTypeface(EppyTheme.font(c, 400));
        e.setBackground(Ui.rounded(t.surfaceContainerHigh, Ui.dp(c, 16)));
        e.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener((v, a, ev) -> {
            prefs().edit().putString("name", v.getText().toString().trim()).apply();
            host.rebuild();
            return true;
        });
        e.setOnFocusChangeListener((v, has) -> {
            if (!has) prefs().edit().putString("name", e.getText().toString().trim()).apply();
        });
        LinearLayout wrap = Ui.column(c);
        wrap.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        wrap.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));
        return wrap;
    }

    private void credit(LinearLayout box, String title, String body) {
        LinearLayout item = Ui.column(c);
        item.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        item.addView(Ui.text(c, t, title, 14, 600, t.onSurface));
        TextView b = Ui.text(c, t, body, 13, 400, t.onSurfaceVariant);
        b.setPadding(0, Ui.dp(c, 4), 0, 0);
        b.setLineSpacing(Ui.dp(c, 2), 1f);
        item.addView(b);
        item.setFocusable(true);
        item.setContentDescription(title + ". " + body);
        box.addView(item);
    }

    private TextView label(String s) {
        TextView tv = Ui.text(c, t, s, 16, 500, t.onSurface);
        tv.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 8));
        return tv;
    }

    private View segmented(String[] labels, String[] values, String current, String key) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        float r = Ui.dp(c, 20);
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            boolean sel = value.equals(current);
            TextView seg = Ui.text(c, t, labels[i], 13, sel ? 600 : 500, sel ? t.onSecondaryContainer : t.onSurface);
            seg.setGravity(Gravity.CENTER);
            seg.setMaxLines(1);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(sel ? t.secondaryContainer : 0);
            bg.setStroke(Ui.dp(c, 1), t.outline);
            float left = i == 0 ? r : 0, right = i == labels.length - 1 ? r : 0;
            bg.setCornerRadii(new float[]{left, left, right, right, right, right, left, left});
            seg.setBackground(Ui.ripple(t, bg, 0));
            seg.setClickable(true);
            seg.setFocusable(true);
            seg.setContentDescription(labels[i] + (sel ? ", selected" : ""));
            seg.setOnClickListener(v -> {
                Ui.haptic(v, t);
                prefs().edit().putString(key, value).apply();
                host.rebuild();
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(c, 44), 1);
            if (i > 0) lp.leftMargin = -Ui.dp(c, 1);
            row.addView(seg, lp);
        }
        return row;
    }

    private View swatches() {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 12));
        int current = t.customSeed;
        for (int seed : SEEDS) {
            FrameLayout sw = new FrameLayout(c);
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            d.setColor(seed);
            if ((seed | 0xFF000000) == (current | 0xFF000000)) d.setStroke(Ui.dp(c, 3), t.onSurface);
            sw.setBackground(d);
            sw.setClickable(true);
            sw.setFocusable(true);
            sw.setContentDescription(String.format("Accent colour #%06X", seed & 0xFFFFFF));
            sw.setOnClickListener(v -> {
                Ui.haptic(v, t);
                prefs().edit().putInt("seed", seed).putString("accent", EppyTheme.ACCENT_CUSTOM).apply();
                host.rebuild();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36));
            lp.rightMargin = Ui.dp(c, 10);
            row.addView(sw, lp);
        }
        return row;
    }

    private View toggle(String title, String summary, String key, boolean value) {
        LinearLayout row = Ui.row(c);
        row.setMinimumHeight(Ui.dp(c, 64));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        LinearLayout texts = Ui.column(c);
        texts.addView(Ui.text(c, t, title, 16, 500, t.onSurface));
        TextView s = Ui.text(c, t, summary, 13, 400, t.onSurfaceVariant);
        s.setPadding(0, Ui.dp(c, 4), 0, 0);
        texts.addView(s);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(c);
        sw.setChecked(value);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{t.onPrimary, t.outline}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{t.primary, t.surfaceContainerHighest}));
        sw.setContentDescription(title);
        sw.setOnCheckedChangeListener((b, checked) -> {
            Ui.haptic(b, t);
            prefs().edit().putBoolean(key, checked).apply();
            host.rebuild();
        });
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        row.setBackground(Ui.ripple(t, null, 0));
        return row;
    }

    private View link(int icon, String title, String summary, String activity) {
        LinearLayout row = Ui.row(c);
        row.setMinimumHeight(Ui.dp(c, 64));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new EppyIcon(icon, t.onSurfaceVariant));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        il.rightMargin = Ui.dp(c, 16);
        row.addView(iv, il);
        LinearLayout texts = Ui.column(c);
        texts.addView(Ui.text(c, t, title, 16, 500, t.onSurface));
        TextView s = Ui.text(c, t, summary, 13, 400, t.onSurfaceVariant);
        s.setPadding(0, Ui.dp(c, 4), 0, 0);
        texts.addView(s);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setBackground(Ui.ripple(t, null, 0));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title);
        row.setOnClickListener(v -> host.openSabp(activity));
        return row;
    }
}
