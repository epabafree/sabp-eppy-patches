package app.morphe.extension.sabp.read;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.sabp.EppyBook;
import app.morphe.extension.sabp.EppyPlayer;

/**
 * Read-along for one audiobook: the paired EPUB/PDF, its extracted text, and the alignment
 * between audio time and text. Pairings, text and anchors are cached in the app's private storage
 * so they survive restarts. Nothing leaves the device.
 */
public final class ReadAlong {
    public enum State {NONE, LOADING, ESTIMATED, MATCHING, SYNCED, EXACT, ERROR}

    public interface Listener {
        void onReadAlongChanged();
    }

    private static ReadAlong instance;

    public static ReadAlong get(Context c) {
        if (instance == null) instance = new ReadAlong(c.getApplicationContext());
        return instance;
    }

    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    // Weak so screens that were rebuilt (theme change, rotation) don't leak.
    private final java.util.Set<Listener> listeners = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private final SpeechSync speech;

    private String folderUri;
    private String docUri;
    private String docName;
    private BookText text;
    private final Alignment alignment = new Alignment();
    private State state = State.NONE;
    private String message = "";
    private long lastAutoSync;
    private int failures;
    private final ArrayList<double[]> pendingSamples = new ArrayList<>();

    private ReadAlong(Context c) {
        this.c = c;
        this.speech = new SpeechSync(c);
    }

    private SharedPreferences prefs() {
        return c.getSharedPreferences("eppy_readalong", Context.MODE_PRIVATE);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        main.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) l.onReadAlongChanged();
        });
    }

    public State state() {
        return state;
    }

    public String message() {
        return message;
    }

    public BookText text() {
        return text;
    }

    public Alignment alignment() {
        return alignment;
    }

    public String docName() {
        return docName;
    }

    public String docUri() {
        return docUri;
    }

    /** Speech matching is compiled in (patch option) and the device can do it. */
    public boolean speechSupported() {
        return !"off".equals(app.morphe.extension.sabp.EppyTheme.configString(c, "eppy_speech", "on")) && SpeechSync.supported(c);
    }

    public boolean speechPermitted() {
        return c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean canAutoMatch() {
        return autoSyncEnabled() && speechSupported() && speechPermitted();
    }

    /** Runs the initial tracking pass again (e.g. right after the permission was granted). */
    public void retryTracking() {
        failures = 0;
        if (text != null && !text.pagesOnly && !alignment.hasOverlay() && alignment.anchorCount(Alignment.SRC_SPEECH) < 3) startInitialTracking();
    }

    public boolean autoSyncEnabled() {
        return prefs().getBoolean("autoSync", true);
    }

    public void setAutoSync(boolean on) {
        prefs().edit().putBoolean("autoSync", on).apply();
        changed();
    }

    // ---- pairing ----------------------------------------------------------------------------------

    /** Switches to the given audiobook (no-op if it's already loaded). */
    public void openBook(String folder) {
        if (folder == null || folder.equals(folderUri)) return;
        folderUri = folder;
        text = null;
        alignment.clear(-1);
        docUri = prefs().getString("doc:" + folder, null);
        docName = prefs().getString("name:" + folder, null);
        failures = 0;
        pendingSamples.clear();
        if (docUri == null) {
            setState(State.NONE, "");
            return;
        }
        load(false);
    }

    public boolean hasDocument() {
        return docUri != null;
    }

    /** Pairs a picked EPUB/PDF with the current audiobook. */
    public void pair(Uri uri) {
        if (folderUri == null) return;
        try {
            c.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        docUri = uri.toString();
        docName = displayName(uri);
        prefs().edit().putString("doc:" + folderUri, docUri).putString("name:" + folderUri, docName).apply();
        anchorFile().delete();
        alignment.clear(-1);
        failures = 0;
        load(true);
    }

    public void unpair() {
        if (folderUri == null) return;
        prefs().edit().remove("doc:" + folderUri).remove("name:" + folderUri).apply();
        anchorFile().delete();
        docUri = null;
        docName = null;
        text = null;
        alignment.clear(-1);
        setState(State.NONE, "");
    }

    private String displayName(Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Throwable ignored) {
        }
        return uri.getLastPathSegment();
    }

    private void load(boolean fresh) {
        final String doc = docUri, name = docName, folder = folderUri;
        setState(State.LOADING, fresh ? "Reading " + (name != null ? name : "document") + "…" : "Opening text…");
        new Thread(() -> {
            BookText t = null;
            String error = null;
            File cache = textCache(doc);
            try {
                if (!fresh && cache.exists()) t = BookText.fromJson(new JSONObject(read(cache)));
            } catch (Throwable ignored) {
            }
            if (t == null) {
                try {
                    t = DocExtractor.extract(c, Uri.parse(doc), name);
                    write(cache, t.toJson().toString());
                } catch (SecurityException se) {
                    error = "Lost access to the file. Pair it again.";
                } catch (Throwable e) {
                    error = "Couldn't read this file: " + e.getMessage();
                }
            }
            final BookText result = t;
            final String err = error;
            main.post(() -> {
                if (!folder.equals(folderUri)) return;
                if (result == null) {
                    setState(State.ERROR, err);
                    return;
                }
                if (!result.pagesOnly && result.wordCount() < 200) {
                    text = null;
                    setState(State.ERROR, "This file has almost no extractable text (" + result.wordCount()
                            + " words). Scanned PDFs need OCR first; an EPUB of the same book works best.");
                    return;
                }
                text = result;
                alignment.clear(-1);
                alignment.setBounds(Math.max(1, result.pagesOnly ? result.paras.size() : result.wordCount()), bookSeconds());
                if (!result.overlays.isEmpty()) alignment.setOverlays(result.overlays);
                try {
                    if (anchorFile().exists()) alignment.fromJson(new JSONObject(read(anchorFile())).optJSONArray("anchors"));
                } catch (Throwable ignored) {
                }
                updateStateFromAnchors();
                if (fresh && !result.pagesOnly && !alignment.hasOverlay()) startInitialTracking();
            });
        }, "eppy-readalong").start();
    }

    private double bookSeconds() {
        EppyBook b = EppyPlayer.get(c).book();
        return b != null && b.total > 0 ? b.total : 0;
    }

    private void updateStateFromAnchors() {
        if (text == null) return;
        if (alignment.hasOverlay()) {
            setState(State.EXACT, "Synced from the book's narration timings");
        } else if (alignment.anchorCount(-1) >= 2) {
            setState(State.SYNCED, "Matched at " + alignment.anchorCount(-1) + " points · in between is estimated");
        } else if (alignment.anchorCount(-1) == 1) {
            setState(State.SYNCED, "Matched at 1 point · estimated elsewhere");
        } else {
            setState(State.ESTIMATED, text.pagesOnly
                    ? "Following by page (estimated). Tap the page you hear to sync."
                    : canAutoMatch() ? "Estimated position. Matching runs as you listen."
                    : speechSupported() && !speechPermitted() ? "Estimated position. Tap Match now to allow speech matching."
                    : "Estimated position. Long-press the paragraph you hear to sync.");
        }
    }

    private void setState(State s, String msg) {
        state = s;
        message = msg != null ? msg : "";
        changed();
    }

    // ---- positions ----------------------------------------------------------------------------

    /** Unit index (word, or page for page-only PDFs) at a book time. */
    public int unitAt(double bookSeconds) {
        if (text == null) return 0;
        alignment.setBounds(text.pagesOnly ? text.paras.size() : text.wordCount(), bookSeconds());
        return alignment.wordAt(bookSeconds);
    }

    public double timeOfUnit(int unit) {
        return alignment.timeOf(unit);
    }

    /** "I'm hearing this now": manual correction. */
    public void syncHere(int unit, double bookSeconds) {
        if (text == null || bookSeconds < 0) return;
        alignment.add(new Alignment.Anchor(bookSeconds, unit, Alignment.SRC_MANUAL, 1f));
        saveAnchors();
        updateStateFromAnchors();
    }

    public void clearMatches() {
        alignment.clear(Alignment.SRC_SPEECH);
        alignment.clear(Alignment.SRC_MANUAL);
        saveAnchors();
        updateStateFromAnchors();
    }

    // ---- automatic matching ---------------------------------------------------------------------

    /**
     * The "small tracking" pass after pairing: samples a few short clips spread through the book
     * and locates each one in the text, so the estimate is corrected everywhere, not just where
     * you are listening.
     */
    private void startInitialTracking() {
        if (!canAutoMatch()) return;
        EppyBook b = EppyPlayer.get(c).book();
        if (b == null || b.total <= 0) return;
        pendingSamples.clear();
        double now = EppyPlayer.get(c).bookPositionSeconds();
        if (now > 0) pendingSamples.add(new double[]{now});
        for (double f : new double[]{0.08, 0.3, 0.55, 0.8}) pendingSamples.add(new double[]{b.total * f});
        runNextSample();
    }

    /** Called from the UI every few seconds while playing: adds a fresh anchor every ~90 s. */
    public void tick(double bookSeconds, boolean playing) {
        if (text == null || text.pagesOnly || !playing || bookSeconds < 0) return;
        if (!canAutoMatch() || speech.isBusy() || failures >= 4) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAutoSync < 90_000) return;
        lastAutoSync = now;
        pendingSamples.add(new double[]{Math.max(0, bookSeconds - 12)});
        runNextSample();
    }

    /** Runs one sample right now (from the "Match now" button). */
    public void matchNow(double bookSeconds) {
        if (text == null || bookSeconds < 0) return;
        if (!speechSupported()) {
            setState(state, "Speech matching isn't available here (needs Android 13+ with an offline speech model, and the patch option on). Long-press the paragraph you hear instead.");
            return;
        }
        if (!speechPermitted()) {
            setState(state, "Allow speech access to match automatically.");
            return;
        }
        failures = 0;
        pendingSamples.add(0, new double[]{Math.max(0, bookSeconds - 12)});
        runNextSample();
    }

    private void runNextSample() {
        if (speech.isBusy() || pendingSamples.isEmpty() || text == null) return;
        double at = pendingSamples.remove(0)[0];
        EppyBook b = EppyPlayer.get(c).book();
        if (b == null) return;
        Object[] loc = b.locate((int) at);
        if (loc == null) return;
        Uri audio;
        try {
            audio = SpeechSync.fileUri(b.folderUri, (String) loc[0]);
        } catch (Throwable t) {
            return;
        }
        final double clipStart = at;
        final int inFile = (Integer) loc[1];
        final State before = state;
        setState(State.MATCHING, "Listening to a few seconds and looking for them in the text…");
        String lang = text.language != null && !text.language.isEmpty() ? text.language : null;
        speech.transcribe(audio, inFile, 12, lang, new SpeechSync.Callback() {
            @Override
            public void onTranscript(List<String> words) {
                int near = alignment.anchorCount(-1) > 0 ? alignment.wordAt(clipStart) : -1;
                Alignment.Match m = Alignment.locate(text, words, near, 4000);
                if (m == null && near >= 0) m = Alignment.locate(text, words, -1, 0);
                if (m != null) {
                    alignment.add(new Alignment.Anchor(clipStart, m.word, Alignment.SRC_SPEECH, m.confidence));
                    saveAnchors();
                    failures = 0;
                } else {
                    failures++;
                }
                updateStateFromAnchors();
                if (m == null && before != State.MATCHING) {
                    message = words.size() < 4 ? "Couldn't hear enough words in that clip (music or silence?)."
                            : "Heard \"" + join(words, 6) + "…\" but couldn't find it in the text. Different edition?";
                    changed();
                }
                main.postDelayed(ReadAlong.this::runNextSample, 500);
            }

            @Override
            public void onError(String reason) {
                failures++;
                updateStateFromAnchors();
                message = reason;
                changed();
                if (failures < 3) main.postDelayed(ReadAlong.this::runNextSample, 1000);
            }
        });
    }

    private static String join(List<String> w, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(n, w.size()); i++) {
            if (i > 0) b.append(' ');
            b.append(w.get(i));
        }
        return b.toString();
    }

    // ---- storage ------------------------------------------------------------------------------------

    private File dir() {
        File d = new File(c.getFilesDir(), "eppy_readalong");
        d.mkdirs();
        return d;
    }

    private File textCache(String doc) {
        return new File(dir(), "text_" + Integer.toHexString(doc.hashCode()) + ".json");
    }

    private File anchorFile() {
        return new File(dir(), "anchors_" + Integer.toHexString(String.valueOf(folderUri).hashCode()) + ".json");
    }

    private void saveAnchors() {
        try {
            JSONObject o = new JSONObject();
            o.put("doc", docUri);
            o.put("anchors", alignment.toJson());
            write(anchorFile(), o.toString());
        } catch (Throwable ignored) {
        }
    }

    private static String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    private static void write(File f, String s) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }
}
