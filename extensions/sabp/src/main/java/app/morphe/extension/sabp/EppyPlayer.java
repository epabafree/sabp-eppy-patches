package app.morphe.extension.sabp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live playback state and library for the Eppy UI.
 *
 * <p>Playback state comes from SABP's own media session (the same one the notification, lock
 * screen, Android Auto and Wear OS read). Commands go through PlayerService intent actions.
 */
public final class EppyPlayer {

    public interface Listener {
        /** Playback state, position or metadata changed. */
        void onPlaybackChanged();

        /** The library lists or covers changed. */
        void onLibraryChanged();
    }

    public static final int STATE_STARTED = 0;
    public static final int STATE_NEW = 1;
    public static final int STATE_FINISHED = 2;
    private static final String[] STATE_NAMES = {"Started", "New", "Finished"};

    public static final class Book {
        public final String folderUri;
        public final String title;
        public final int state;
        public Bitmap cover;

        Book(String folderUri, String title, int state) {
            this.folderUri = folderUri;
            this.title = title;
            this.state = state;
        }
    }

    private static EppyPlayer instance;

    public static EppyPlayer get(Context context) {
        if (instance == null) instance = new EppyPlayer(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final List<List<Book>> shelves = new ArrayList<>();

    private MediaController controller;
    private boolean bound;
    private boolean libraryLoading;
    private long libraryLoadedAt;

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            dispatchPlayback();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            dispatchPlayback();
        }

        @Override
        public void onSessionDestroyed() {
            controller = null;
            dispatchPlayback();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            attach();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            detachController();
        }
    };

    private final Runnable serviceListener = () -> main.post(this::attach);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (EppyBridge.INTENT_BOOK_CHANGED.equals(action)) {
                refreshLibrary(true);
            }
            dispatchPlayback();
        }
    };

    private EppyPlayer(Context app) {
        this.app = app;
        for (int i = 0; i < 3; i++) shelves.add(new ArrayList<>());
        EppyBridge.addServiceListener(serviceListener);
    }

    // ---- lifecycle --------------------------------------------------------------------------

    /** Called from the UI's onStart. Starts and binds PlayerService the same way PlayerActivity does. */
    public void start() {
        if (!bound) {
            try {
                Intent intent = EppyBridge.serviceIntent(app, null);
                app.startForegroundService(intent);
                bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            } catch (Throwable ignored) {
            }
            IntentFilter filter = new IntentFilter();
            filter.addAction(EppyBridge.INTENT_BOOK_CHANGED);
            filter.addAction(EppyBridge.INTENT_FILE_CHANGED);
            filter.addAction(EppyBridge.INTENT_SLEEP_CHANGED);
            try {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } catch (Throwable ignored) {
            }
        }
        attach();
        refreshLibrary(false);
    }

    /** Called from the UI's onStop. */
    public void stop() {
        if (bound) {
            try {
                app.unbindService(connection);
            } catch (Throwable ignored) {
            }
            try {
                app.unregisterReceiver(receiver);
            } catch (Throwable ignored) {
            }
            bound = false;
        }
        detachController();
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void dispatchPlayback() {
        main.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) l.onPlaybackChanged();
        });
    }

    private void dispatchLibrary() {
        main.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) l.onLibraryChanged();
        });
    }

    // ---- media session ------------------------------------------------------------------------

    private void attach() {
        if (controller != null) return;
        Service service = EppyBridge.getPlayerService();
        if (service == null) return;
        MediaSession.Token token = findSessionToken(service);
        if (token == null) return;
        try {
            controller = new MediaController(app, token);
            controller.registerCallback(controllerCallback, main);
            dispatchPlayback();
            refreshLibrary(false);
        } catch (Throwable ignored) {
            controller = null;
        }
    }

    private void detachController() {
        if (controller != null) {
            try {
                controller.unregisterCallback(controllerCallback);
            } catch (Throwable ignored) {
            }
            controller = null;
        }
    }

    /**
     * Finds PlayerService's session token without relying on obfuscated names: the no-arg method
     * returning androidx MediaSessionCompat.Token (a Parcelable whose class name is kept), parcelled
     * to recover the framework MediaSession.Token it wraps.
     */
    private static MediaSession.Token findSessionToken(Service service) {
        try {
            for (Method m : service.getClass().getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                Class<?> type = m.getReturnType();
                if (type == MediaSession.Token.class) {
                    m.setAccessible(true);
                    return (MediaSession.Token) m.invoke(service);
                }
                if (type.getName().endsWith("MediaSessionCompat$Token") && Parcelable.class.isAssignableFrom(type)) {
                    m.setAccessible(true);
                    Object compat = m.invoke(service);
                    if (compat == null) return null;
                    Parcel parcel = Parcel.obtain();
                    try {
                        ((Parcelable) compat).writeToParcel(parcel, 0);
                        parcel.setDataPosition(0);
                        Object inner = parcel.readParcelable(MediaSession.Token.class.getClassLoader());
                        if (inner instanceof MediaSession.Token) return (MediaSession.Token) inner;
                    } finally {
                        parcel.recycle();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public boolean hasSession() {
        return controller != null && controller.getMetadata() != null;
    }

    public boolean isPlaying() {
        PlaybackState s = controller != null ? controller.getPlaybackState() : null;
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Book (folder) name. SABP puts it in ALBUM. */
    public String bookTitle() {
        MediaMetadata m = controller != null ? controller.getMetadata() : null;
        if (m == null) return null;
        String album = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
        if (album != null && !album.trim().isEmpty()) return album.trim();
        return m.getString(MediaMetadata.METADATA_KEY_TITLE);
    }

    /** Current chapter or file name. */
    public String chapterTitle() {
        MediaMetadata m = controller != null ? controller.getMetadata() : null;
        return m != null ? m.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
    }

    public Bitmap cover() {
        MediaMetadata m = controller != null ? controller.getMetadata() : null;
        if (m == null) return null;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        return b;
    }

    /** Duration of the current chapter/file in ms. */
    public long durationMs() {
        MediaMetadata m = controller != null ? controller.getMetadata() : null;
        return m != null ? Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION)) : 0;
    }

    /** Position in the current chapter/file in ms, extrapolated while playing. */
    public long positionMs() {
        PlaybackState s = controller != null ? controller.getPlaybackState() : null;
        if (s == null) return 0;
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            long elapsed = SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
            pos += (long) (elapsed * s.getPlaybackSpeed());
        }
        long d = durationMs();
        if (d > 0) pos = Math.min(pos, d);
        return Math.max(0, pos);
    }

    public float speed() {
        PlaybackState s = controller != null ? controller.getPlaybackState() : null;
        float sp = s != null ? s.getPlaybackSpeed() : 1f;
        return sp > 0 ? sp : 1f;
    }

    /** Book-level progress 0..1 from SABP's BookData caches, or -1 when unknown. */
    public float bookProgress() {
        Object bookData = currentBookData();
        if (bookData == null) return -1;
        int current = intField(bookData, "mCurrentPlaybackTimeCache");
        int total = intField(bookData, "mTotalPlaybackTimeCache");
        if (total <= 0) return -1;
        return Math.max(0f, Math.min(1f, current / (float) total));
    }

    /** Remaining time of the whole book in seconds, or -1. */
    public int bookRemainingSeconds() {
        Object bookData = currentBookData();
        if (bookData == null) return -1;
        int current = intField(bookData, "mCurrentPlaybackTimeCache");
        int total = intField(bookData, "mTotalPlaybackTimeCache");
        return total > 0 ? Math.max(0, total - current) : -1;
    }

    public String currentFolderUri() {
        Object bookData = currentBookData();
        if (bookData == null) return null;
        Object v = field(bookData, "mFolderUri");
        return v instanceof String ? (String) v : null;
    }

    private Object currentBookData() {
        Service service = EppyBridge.getPlayerService();
        if (service == null) return null;
        try {
            for (Field f : service.getClass().getDeclaredFields()) {
                if (f.getType().getName().equals(EppyBridge.SABP_PACKAGE + ".BookData")) {
                    f.setAccessible(true);
                    return f.get(service);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object field(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int intField(Object o, String name) {
        Object v = field(o, name);
        return v instanceof Integer ? (Integer) v : 0;
    }

    // ---- commands -------------------------------------------------------------------------

    public void playPause() {
        EppyBridge.send(app, EppyBridge.ACTION_PLAY_PAUSE);
    }

    public void command(String action) {
        EppyBridge.send(app, action);
    }

    public void playBook(Book book) {
        EppyBridge.playBook(app, book.folderUri);
    }

    public void seekTo(long ms) {
        Service service = EppyBridge.getPlayerService();
        if (service == null || !EppyBridge.isLinked()) return;
        main.post(() -> {
            try {
                EppyBridge.seekTracking(service, true);
                EppyBridge.seekToSeconds(service, (int) (ms / 1000));
                EppyBridge.seekTracking(service, false);
            } catch (Throwable ignored) {
            }
            dispatchPlayback();
        });
    }

    // ---- whole-book position, chapters, jumping ---------------------------------------------

    private EppyBook book;
    private long bookReadAt;
    private int lastFilePos = -1;
    private long lastFilePosAt;

    /** The current book's files/chapters, refreshed at most every 2 s or when the file changes. */
    public EppyBook book() {
        long now = SystemClock.elapsedRealtime();
        if (book == null || now - bookReadAt > 2000) {
            try {
                EppyBook b = EppyBook.current();
                if (b != null || book == null) book = b;
            } catch (Throwable ignored) {
            }
            bookReadAt = now;
        }
        return book;
    }

    public void invalidateBook() {
        bookReadAt = 0;
    }

    /** Live position in the current file (seconds, fractional while playing), or -1. */
    public double filePositionSeconds() {
        Service service = EppyBridge.getPlayerService();
        if (service == null) return -1;
        int p;
        try {
            p = EppyBridge.filePositionSeconds(service);
        } catch (Throwable t) {
            p = -1;
        }
        if (p < 0) return -1;
        long now = SystemClock.elapsedRealtime();
        if (p != lastFilePos) {
            lastFilePos = p;
            lastFilePosAt = now;
        }
        double frac = isPlaying() ? Math.min(0.99, (now - lastFilePosAt) / 1000.0 * speed()) : 0;
        return p + frac;
    }

    /** Live position in the whole book (seconds), or -1 when the book layout is unknown. */
    public double bookPositionSeconds() {
        EppyBook b = book();
        double fp = filePositionSeconds();
        if (b == null || fp < 0) return -1;
        EppyBook.FileItem f = b.file(b.currentFile);
        if (f == null || f.start < 0) return -1;
        return f.start + fp;
    }

    /** Jumps to a file and position, like SABP's bookmarks and chapter list do. */
    public boolean jumpTo(String fileName, int seconds) {
        Service service = EppyBridge.getPlayerService();
        if (service == null || fileName == null || !EppyBridge.isLinked()) return false;
        main.post(() -> {
            try {
                EppyBridge.jumpTo(service, fileName, Math.max(0, seconds), true);
            } catch (Throwable ignored) {
            }
            invalidateBook();
            dispatchPlayback();
        });
        return true;
    }

    /** Jumps to a whole-book position. */
    public boolean jumpToBook(double seconds) {
        EppyBook b = book();
        if (b == null) return false;
        Object[] loc = b.locate((int) Math.round(seconds));
        return loc != null && jumpTo((String) loc[0], (Integer) loc[1]);
    }

    public Context context() {
        return app;
    }

    public boolean canSeek() {
        return EppyBridge.isLinked() && EppyBridge.getPlayerService() != null;
    }

    // ---- library ------------------------------------------------------------------------------

    public List<Book> shelf(int state) {
        return shelves.get(state);
    }

    public List<Book> allBooks() {
        ArrayList<Book> all = new ArrayList<>();
        for (List<Book> s : shelves) all.addAll(s);
        return all;
    }

    public boolean libraryEmpty() {
        for (List<Book> s : shelves) if (!s.isEmpty()) return false;
        return true;
    }

    public boolean isLibraryLoading() {
        return libraryLoading;
    }

    /** Reloads the book lists through PlayerService (main thread), then covers off the main thread. */
    public void refreshLibrary(boolean force) {
        if (libraryLoading) return;
        if (!force && SystemClock.elapsedRealtime() - libraryLoadedAt < 30_000 && !libraryEmpty()) return;
        Service service = EppyBridge.getPlayerService();
        if (service == null || !EppyBridge.isLinked()) return;
        libraryLoading = true;
        main.post(() -> {
            final ArrayList<List<Book>> fresh = new ArrayList<>();
            final ArrayList<ArrayList<?>> keys = new ArrayList<>();
            try {
                for (int state = 0; state < 3; state++) {
                    Object stateEnum = bookStateEnum(STATE_NAMES[state]);
                    ArrayList<?> paths = stateEnum != null ? EppyBridge.booksForState(service, stateEnum) : null;
                    ArrayList<Book> books = new ArrayList<>();
                    if (paths != null) {
                        for (Object p : paths) {
                            Object uri = field(p, "mFolderUri");
                            Object name = field(p, "mFolderName");
                            if (uri instanceof String) {
                                books.add(new Book((String) uri, name instanceof String ? (String) name : "", state));
                            }
                        }
                    }
                    fresh.add(books);
                    ArrayList<?> k = null;
                    try {
                        if (paths != null && !paths.isEmpty()) k = EppyBridge.coverKeys(service, paths);
                    } catch (Throwable ignored) {
                    }
                    keys.add(k);
                }
            } catch (Throwable t) {
                libraryLoading = false;
                return;
            }
            // Keep covers we already have while the new ones load.
            for (int s = 0; s < 3; s++) {
                for (Book b : fresh.get(s)) {
                    Book old = find(b.folderUri);
                    if (old != null) b.cover = old.cover;
                }
            }
            for (int s = 0; s < 3; s++) {
                shelves.get(s).clear();
                shelves.get(s).addAll(fresh.get(s));
            }
            libraryLoadedAt = SystemClock.elapsedRealtime();
            dispatchLibrary();
            io.execute(() -> {
                for (int s = 0; s < 3; s++) {
                    ArrayList<?> k = keys.get(s);
                    if (k == null) continue;
                    try {
                        ArrayList<?> covers = EppyBridge.loadCovers(app, k);
                        if (covers == null) continue;
                        List<Book> books = fresh.get(s);
                        for (int i = 0; i < books.size() && i < covers.size(); i++) {
                            Object c = covers.get(i);
                            if (c instanceof Bitmap) books.get(i).cover = (Bitmap) c;
                        }
                        dispatchLibrary();
                    } catch (Throwable ignored) {
                    }
                }
                libraryLoading = false;
            });
        });
    }

    public Book find(String folderUri) {
        if (folderUri == null) return null;
        for (List<Book> s : shelves) for (Book b : s) if (folderUri.equals(b.folderUri)) return b;
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object bookStateEnum(String name) {
        try {
            Class c = Class.forName(EppyBridge.BOOK_STATE_CLASS, false, app.getClassLoader());
            return Enum.valueOf(c, name);
        } catch (Throwable t) {
            return null;
        }
    }
}
