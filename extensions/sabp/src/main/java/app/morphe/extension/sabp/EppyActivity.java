package app.morphe.extension.sabp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import app.morphe.extension.sabp.ui.EppyIcon;
import app.morphe.extension.sabp.ui.HomePage;
import app.morphe.extension.sabp.ui.Host;
import app.morphe.extension.sabp.ui.LibraryPage;
import app.morphe.extension.sabp.ui.PlayButton;
import app.morphe.extension.sabp.ui.PlayerPane;
import app.morphe.extension.sabp.ui.SettingsPage;
import app.morphe.extension.sabp.ui.Sheet;
import app.morphe.extension.sabp.ui.Ui;

/**
 * The Eppy UI: a Material 3 home, library and player for Smart AudioBook Player.
 *
 * <p>Layout adapts to the window width (not the device model):
 * compact (&lt;600dp) bottom navigation + floating mini-player + full-screen player;
 * medium (600–839dp) navigation rail + mini-player; expanded (≥840dp) navigation rail and a
 * persistent Now Playing pane beside the content (two-pane).
 */
public class EppyActivity extends Activity implements Host, EppyPlayer.Listener {
    public static final int TAB_HOME = 0, TAB_LIBRARY = 1, TAB_BOOKMARKS = 2, TAB_SETTINGS = 3;
    private static final int[] TAB_ICONS = {EppyIcon.HOME, EppyIcon.LIBRARY, EppyIcon.BOOKMARK, EppyIcon.TUNE};
    private static final String[] TAB_LABELS = {"Home", "Library", "Bookmarks", "Settings"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EppyTheme theme;
    private EppyPlayer player;
    private int coverSeed;
    private Bitmap seedSource;

    private FrameLayout root;
    private FrameLayout pages;
    private HomePage home;
    private LibraryPage library;
    private SettingsPage settings;
    private PlayerPane sidePane;
    private PlayerPane overlayPane;
    private View miniPlayer;
    private PlayButton miniPlay;
    private TextView miniTitle, miniSub;
    private ImageView miniCover;
    private View miniProgress;
    private LinearLayout navContainer;

    private int tab = TAB_HOME;
    private boolean playerOpen;
    private int widthClass;
    private String lastBook;
    private boolean lastHadSession;
    private Bitmap lastCover;
    private boolean visible;
    private Object backCallback;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            tick();
            handler.postDelayed(this, playerOpen || sidePane != null ? 500 : 1000);
        }
    };

    // ---- lifecycle ------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        player = EppyPlayer.get(this);
        if (state != null) {
            tab = state.getInt("tab", TAB_HOME);
            playerOpen = state.getBoolean("playerOpen", false);
        } else if (getIntent() != null && getIntent().getBooleanExtra("openPlayer", false)) {
            playerOpen = true;
        }
        Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setNavigationBarContrastEnforced(false);
        build();
        registerBack();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("openPlayer", false)) openPlayer();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putBoolean("playerOpen", playerOpen);
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        player.addListener(this);
        player.start();
        refreshAll(true);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        visible = false;
        handler.removeCallbacks(ticker);
        player.removeListener(this);
        player.stop();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build();
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback cb = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (Sheet.current != null) {
            Sheet.current.dismiss();
            return;
        }
        if (playerOpen && overlayPane != null) {
            closePlayer();
            return;
        }
        if (tab == TAB_HOME && home != null && home.onBack()) return;
        if (tab != TAB_HOME) {
            selectTab(TAB_HOME);
            return;
        }
        finish();
    }

    // ---- building -------------------------------------------------------------------------

    private int widthClass() {
        int w = getResources().getConfiguration().screenWidthDp;
        if (w >= 840) return 2;
        if (w >= 600) return 1;
        return 0;
    }

    private void build() {
        theme = EppyTheme.resolve(this, coverSeed);
        widthClass = widthClass();
        Sheet.current = null;

        root = new FrameLayout(this);
        root.setBackgroundColor(theme.surface);
        getWindow().getDecorView().setBackgroundColor(theme.surface);

        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (widthClass >= 1) {
            navContainer = navRail();
            frame.addView(navContainer, new LinearLayout.LayoutParams(Ui.dp(this, 88), ViewGroup.LayoutParams.MATCH_PARENT));
        }

        LinearLayout contentCol = Ui.column(this);
        pages = new FrameLayout(this);
        contentCol.addView(pages, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (widthClass == 0) {
            navContainer = bottomNav();
            contentCol.addView(navContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        frame.addView(contentCol, Ui.lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        home = new HomePage(this);
        library = new LibraryPage(this, home);
        settings = new SettingsPage(this);
        pages.addView(home.root);
        pages.addView(library.root);
        pages.addView(settings.root);

        sidePane = null;
        overlayPane = null;
        miniPlayer = null;
        if (widthClass == 2) {
            // Two-pane: persistent Now Playing on the right.
            sidePane = new PlayerPane(this, true);
            FrameLayout paneWrap = new FrameLayout(this);
            paneWrap.setBackground(Ui.rounded(theme.surfaceContainerLow, Ui.dp(this, 28)));
            Ui.clip(paneWrap, Ui.dp(this, 28));
            paneWrap.addView(sidePane.root);
            int paneW = Math.min(Ui.dp(this, 440), getResources().getDisplayMetrics().widthPixels * 42 / 100);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(paneW, ViewGroup.LayoutParams.MATCH_PARENT);
            pl.setMargins(0, Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
            frame.addView(paneWrap, pl);
            sidePane.layoutCover(paneW);
            sidePane.root.setBackgroundColor(theme.surfaceContainerLow);
            playerOpen = false;
        } else {
            miniPlayer = buildMiniPlayer();
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64), Gravity.BOTTOM);
            int side = Ui.dp(this, 12);
            ml.setMargins(side + (widthClass == 1 ? Ui.dp(this, 88) : 0), 0, side, (widthClass == 0 ? Ui.dp(this, 80) : 0) + Ui.dp(this, 12));
            if (widthClass == 1) {
                ml.width = Math.min(Ui.dp(this, 560), getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 112));
                ml.gravity = Gravity.BOTTOM | Gravity.END;
            }
            root.addView(miniPlayer, ml);
        }

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            if (navContainer != null && widthClass == 0) {
                // The bottom bar colour runs under the gesture area.
                root.setBackgroundColor(theme.surface);
            }
            return WindowInsets.CONSUMED;
        });

        setContentView(root);
        applySystemBars();
        showTab(tab == TAB_BOOKMARKS ? TAB_HOME : tab);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int contentW = pages.getWidth();
            if (contentW > 0) library.setWidth(contentW);
        });
        if (playerOpen && widthClass < 2) {
            playerOpen = false;
            openPlayer(false);
        }
        refreshAll(true);
    }

    private void applySystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        c.setSystemBarsAppearance(theme.dark ? 0 : light, light);
    }

    /** Material 3 navigation bar: pill indicator behind the selected icon, labels always shown. */
    private LinearLayout bottomNav() {
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(theme.surfaceContainer);
        bar.setMinimumHeight(Ui.dp(this, 80));
        for (int i = 0; i < TAB_LABELS.length; i++) {
            bar.addView(navItem(i, false), Ui.lp(0, Ui.dp(this, 80), 1));
        }
        return bar;
    }

    /** Material 3 navigation rail for medium and expanded windows. */
    private LinearLayout navRail() {
        LinearLayout rail = Ui.column(this);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(theme.surface);
        rail.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        // Rail FAB: jump to the player.
        android.widget.FrameLayout fab = Ui.iconButton(this, theme, EppyIcon.HEADPHONES, theme.onPrimaryContainer, theme.primaryContainer,
                "Now playing", v -> openPlayer());
        fab.setBackground(Ui.ripple(theme, Ui.rounded(theme.primaryContainer, Ui.dp(this, 16)), Ui.dp(this, 16)));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        fl.bottomMargin = Ui.dp(this, 28);
        rail.addView(fab, fl);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 72));
            lp.bottomMargin = Ui.dp(this, 4);
            rail.addView(navItem(i, true), lp);
        }
        return rail;
    }

    private View navItem(int index, boolean rail) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.CENTER);
        boolean sel = index == tab;
        FrameLayout pill = new FrameLayout(this);
        pill.setBackground(Ui.rounded(sel ? theme.secondaryContainer : 0, Ui.dp(this, 16)));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new EppyIcon(TAB_ICONS[index], sel ? theme.onSecondaryContainer : theme.onSurfaceVariant));
        pill.addView(iv, new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER));
        item.addView(pill, new LinearLayout.LayoutParams(Ui.dp(this, rail ? 56 : 64), Ui.dp(this, 32)));
        TextView label = Ui.text(this, theme, TAB_LABELS[index], 12, sel ? 700 : 500, sel ? theme.onSurface : theme.onSurfaceVariant);
        label.setPadding(0, Ui.dp(this, 4), 0, 0);
        item.addView(label);
        item.setClickable(true);
        item.setFocusable(true);
        item.setBackground(Ui.ripple(theme, null, Ui.dp(this, 16)));
        item.setContentDescription(TAB_LABELS[index] + (sel ? ", selected" : ""));
        item.setOnClickListener(v -> {
            Ui.haptic(v, theme);
            selectTab(index);
        });
        return item;
    }

    private View buildMiniPlayer() {
        FrameLayout wrap = new FrameLayout(this);
        float r = Ui.dp(this, 20);
        wrap.setBackground(Ui.ripple(theme, Ui.rounded(theme.surfaceContainerHighest, r), r));
        wrap.setElevation(Ui.dp(this, 6));
        Ui.clip(wrap, r);
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        miniCover = Ui.cover(this, theme, null, 12);
        row.addView(miniCover, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        miniTitle = Ui.singleLine(Ui.text(this, theme, "", 15, 600, theme.onSurface));
        miniSub = Ui.singleLine(Ui.text(this, theme, "", 13, 400, theme.onSurfaceVariant));
        miniSub.setPadding(0, Ui.dp(this, 3), 0, 0);
        texts.addView(miniTitle);
        texts.addView(miniSub);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.iconButton(this, theme, EppyIcon.REWIND, theme.onSurface, 0, "Rewind",
                v -> player.command(EppyBridge.ACTION_REWIND_SMALL)));
        miniPlay = new PlayButton(this);
        miniPlay.apply(theme);
        miniPlay.setOnClickListener(v -> {
            Ui.haptic(v, theme);
            player.playPause();
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        pl.leftMargin = Ui.dp(this, 4);
        row.addView(miniPlay, pl);
        wrap.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        miniProgress = new View(this);
        miniProgress.setBackgroundColor(theme.primary);
        miniProgress.setPivotX(0);
        wrap.addView(miniProgress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3), Gravity.BOTTOM));
        wrap.setClickable(true);
        wrap.setFocusable(true);
        wrap.setContentDescription("Open player");
        wrap.setOnClickListener(v -> openPlayer());
        wrap.setVisibility(View.GONE);
        return wrap;
    }

    // ---- navigation ------------------------------------------------------------------------

    private void showTab(int t) {
        home.root.setVisibility(t == TAB_HOME ? View.VISIBLE : View.GONE);
        library.root.setVisibility(t == TAB_LIBRARY ? View.VISIBLE : View.GONE);
        settings.root.setVisibility(t == TAB_SETTINGS ? View.VISIBLE : View.GONE);
    }

    @Override
    public void selectTab(int t) {
        if (t == TAB_BOOKMARKS) {
            openSabp(EppyBridge.BOOKMARKS_ACTIVITY);
            return;
        }
        if (t == tab) return;
        tab = t;
        // Rebuild the navigation so the indicator moves; content views are kept.
        ViewGroup parent = (ViewGroup) navContainer.getParent();
        int idx = parent.indexOfChild(navContainer);
        ViewGroup.LayoutParams lp = navContainer.getLayoutParams();
        parent.removeView(navContainer);
        navContainer = widthClass == 0 ? bottomNav() : navRail();
        parent.addView(navContainer, idx, lp);
        View page = t == TAB_HOME ? home.root : t == TAB_LIBRARY ? library.root : settings.root;
        showTab(t);
        if (!theme.reduceMotion) {
            page.setAlpha(0f);
            page.setTranslationY(Ui.dp(this, 12));
            page.animate().alpha(1f).translationY(0).setDuration(200).setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
        }
    }

    @Override
    public void openPlayer() {
        openPlayer(true);
    }

    private void openPlayer(boolean animate) {
        if (sidePane != null) {
            sidePane.root.requestFocus();
            return;
        }
        if (playerOpen) return;
        playerOpen = true;
        overlayPane = new PlayerPane(this, false);
        root.addView(overlayPane.root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayPane.layoutCover(getResources().getDisplayMetrics().widthPixels);
        overlayPane.update(true);
        if (miniPlayer != null) miniPlayer.setVisibility(View.GONE);
        if (animate && !theme.reduceMotion) {
            overlayPane.root.setTranslationY(getResources().getDisplayMetrics().heightPixels * 0.3f);
            overlayPane.root.setAlpha(0f);
            overlayPane.root.animate().translationY(0).alpha(1f).setDuration(300)
                    .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    public void closePlayer() {
        if (!playerOpen || overlayPane == null) return;
        playerOpen = false;
        final View v = overlayPane.root;
        overlayPane = null;
        Runnable remove = () -> root.removeView(v);
        if (theme.reduceMotion) remove.run();
        else v.animate().translationY(v.getHeight() * 0.3f).alpha(0f).setDuration(200).withEndAction(remove).start();
        updateMini();
    }

    @Override
    public void openSabp(String activityClass) {
        try {
            Intent i = EppyBridge.activityIntent(this, activityClass);
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Not available in this version", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showMoreSheet() {
        new Sheet(root, theme, "More")
                .item(EppyIcon.BOOKMARK, "Bookmarks", "Jump to or edit saved moments", () -> openSabp(EppyBridge.BOOKMARKS_ACTIVITY))
                .item(EppyIcon.PERSON, "Characters", "Who's who in this book", () -> openSabp(EppyBridge.CHARACTERS_ACTIVITY))
                .item(EppyIcon.HISTORY, "History", "Undo an accidental jump", () -> openSabp(EppyBridge.HISTORY_ACTIVITY))
                .item(EppyIcon.CHART, "Statistics", "Listening time for this book", () -> openSabp(EppyBridge.STATS_ACTIVITY))
                .item(EppyIcon.CLASSIC, "Classic player", "Chapters, equalizer, boost, full speed control", () -> openSabp(EppyBridge.PLAYER_ACTIVITY))
                .item(EppyIcon.TUNE, "Player settings", null, () -> openSabp(EppyBridge.SETTINGS_ACTIVITY))
                .show();
    }

    @Override
    public void playBook(EppyPlayer.Book book, boolean open) {
        if (book == null) return;
        player.playBook(book);
        if (open) openPlayer();
    }

    @Override
    public void rebuild() {
        build();
    }

    // ---- updates ---------------------------------------------------------------------------

    private void refreshAll(boolean full) {
        if (home == null) return;
        boolean hasSession = player.hasSession();
        String book = player.bookTitle();
        boolean bookChanged = hasSession != lastHadSession || (book != null && !book.equals(lastBook));
        lastHadSession = hasSession;
        lastBook = book;
        if (bookChanged || full) home.rebuild();
        if (sidePane != null) sidePane.update(true);
        if (overlayPane != null) overlayPane.update(true);
        updateMini();
        maybeUpdateCoverAccent();
    }

    private void updateMini() {
        if (miniPlayer == null) return;
        boolean show = player.hasSession() && !playerOpen;
        miniPlayer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        miniTitle.setText(player.bookTitle());
        String ch = player.chapterTitle();
        miniSub.setText(ch != null && !ch.equals(player.bookTitle()) ? ch : "");
        Bitmap c = player.cover();
        if (c != lastCover) {
            Ui.setCover(miniCover, theme, c);
            lastCover = c;
        }
        miniPlay.setPlaying(player.isPlaying(), true);
        tickMini();
    }

    private void tickMini() {
        if (miniPlayer == null || miniPlayer.getVisibility() != View.VISIBLE) return;
        long d = player.durationMs();
        miniProgress.setScaleX(d > 0 ? player.positionMs() / (float) d : 0f);
    }

    private void tick() {
        if (overlayPane != null) overlayPane.update(false);
        if (sidePane != null) sidePane.update(false);
        if (home != null && tab == TAB_HOME) home.tick();
        tickMini();
    }

    /** Per-book accent: derive the theme seed from the cover when that option is chosen. */
    private void maybeUpdateCoverAccent() {
        if (theme == null || !EppyTheme.ACCENT_COVER.equals(theme.accentSource)) return;
        final Bitmap c = player.cover();
        if (c == seedSource) return;
        seedSource = c;
        new Thread(() -> {
            int seed = EppyTheme.seedFromCover(c);
            handler.post(() -> {
                if (seed != coverSeed) {
                    coverSeed = seed;
                    if (visible) build();
                }
            });
        }).start();
    }

    @Override
    public void onPlaybackChanged() {
        if (!visible) return;
        refreshAll(false);
    }

    @Override
    public void onLibraryChanged() {
        if (!visible || home == null) return;
        home.rebuild();
        library.rebuild();
    }

    // ---- keyboard ----------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        boolean typing = getCurrentFocus() instanceof EditText;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_ESCAPE) {
            handleBack();
            return true;
        }
        if (e.isCtrlPressed() && k == KeyEvent.KEYCODE_F) {
            selectTab(TAB_HOME);
            home.focusSearch();
            return true;
        }
        if (typing) return super.dispatchKeyEvent(e);
        switch (k) {
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_K:
                player.playPause();
                return true;
            case KeyEvent.KEYCODE_J:
                player.command(EppyBridge.ACTION_REWIND_SMALL);
                return true;
            case KeyEvent.KEYCODE_L:
                player.command(EppyBridge.ACTION_FWD_SMALL);
                return true;
            case KeyEvent.KEYCODE_N:
                player.command(EppyBridge.ACTION_NEXT_FILE);
                return true;
            case KeyEvent.KEYCODE_P:
                player.command(EppyBridge.ACTION_PREV_FILE);
                return true;
            case KeyEvent.KEYCODE_B:
                player.command(EppyBridge.ACTION_ADD_BOOKMARK);
                Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show();
                return true;
            case KeyEvent.KEYCODE_1:
                selectTab(TAB_HOME);
                return true;
            case KeyEvent.KEYCODE_2:
                selectTab(TAB_LIBRARY);
                return true;
            case KeyEvent.KEYCODE_3:
                selectTab(TAB_BOOKMARKS);
                return true;
            case KeyEvent.KEYCODE_4:
                selectTab(TAB_SETTINGS);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!(getCurrentFocus() instanceof app.morphe.extension.sabp.ui.EppySlider) && e.isAltPressed()) {
                    player.command(EppyBridge.ACTION_REWIND_SMALL);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!(getCurrentFocus() instanceof app.morphe.extension.sabp.ui.EppySlider) && e.isAltPressed()) {
                    player.command(EppyBridge.ACTION_FWD_SMALL);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- Host --------------------------------------------------------------------------------

    @Override
    public Activity activity() {
        return this;
    }

    @Override
    public EppyTheme theme() {
        return theme;
    }

    @Override
    public EppyPlayer player() {
        return player;
    }

    @Override
    public ViewGroup overlayRoot() {
        return root;
    }
}
