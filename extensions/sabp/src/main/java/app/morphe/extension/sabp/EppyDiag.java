package app.morphe.extension.sabp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Diagnostics for eppy, without logging book text or audio:
 * <ul>
 *     <li>an operation log (last 300 lines) with what eppy was doing, shown in Settings → Diagnostics;</li>
 *     <li>a crash recorder that saves the stack trace of an uncaught exception and then hands the
 *     crash to the previous handler unchanged;</li>
 *     <li>job markers: a long operation (read-along extraction, decoding, recognition, matching)
 *     writes a marker before it starts and clears it when done. A marker still present at the
 *     next start means that operation died with the process, so read-along turns automatic work
 *     off instead of repeating it — no crash loops.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class EppyDiag {
    private static final String TAG = "eppy";
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static Context app;
    private static boolean installed;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    private EppyDiag() {
    }

    /** Injected at the start of SABPApplication.onCreate (and called by eppy itself as a fallback). */
    public static void install(Application application) {
        if (installed) return;
        installed = true;
        app = application.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                recordCrash(thread, error);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
        log("app", "started · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · " + Build.MANUFACTURER + " " + Build.MODEL);
    }

    public static void ensure(Context c) {
        if (app == null && c != null) {
            app = c.getApplicationContext();
            if (app instanceof Application) install((Application) app);
        }
    }

    private static SharedPreferences prefs() {
        return app.getSharedPreferences("eppy_diag", Context.MODE_PRIVATE);
    }

    public static synchronized void log(String area, String message) {
        String line = FMT.format(new Date()) + "  [" + area + "] " + message;
        Log.i(TAG, line);
        lines.addLast(line);
        while (lines.size() > 300) lines.removeFirst();
    }

    public static synchronized String logText() {
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(l).append('\n');
        return b.toString();
    }

    private static void recordCrash(Thread thread, Throwable error) {
        if (app == null) return;
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        String job = prefs().getString("job", null);
        String report = "Crash at " + FMT.format(new Date()) + " on thread " + thread.getName()
                + (job != null ? "\nWhile running: " + job : "")
                + "\nApp " + safeVersion() + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + " · " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n\n" + sw
                + "\n--- recent eppy log ---\n" + logText();
        try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), "eppy_last_crash.txt"))) {
            out.write(report.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
        boolean ours = sw.toString().contains("app.morphe.extension.sabp");
        prefs().edit().putBoolean("crashPending", true).putBoolean("crashInEppy", ours).commit();
    }

    private static String safeVersion() {
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    /** The last recorded crash report, or null. */
    public static String lastCrash() {
        if (app == null) return null;
        File f = new File(app.getFilesDir(), "eppy_last_crash.txt");
        if (!f.exists()) return null;
        try {
            byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
            return new String(b, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clearCrash() {
        if (app == null) return;
        new File(app.getFilesDir(), "eppy_last_crash.txt").delete();
        prefs().edit().remove("crashPending").remove("crashInEppy").apply();
    }

    // ---- job markers ------------------------------------------------------------------------

    /** Marks a long operation as running (written synchronously so it survives a crash). */
    public static void jobStart(String job) {
        log("job", "start: " + job);
        if (app != null) prefs().edit().putString("job", job).commit();
    }

    public static void jobEnd(String job, String outcome) {
        log("job", "end: " + job + " → " + outcome);
        if (app != null) prefs().edit().remove("job").apply();
    }

    /**
     * Called once at start-up. Returns the name of a job that was running when the process died
     * last time (and clears the marker), or null.
     */
    public static String takeInterruptedJob() {
        if (app == null) return null;
        String job = prefs().getString("job", null);
        if (job != null) {
            prefs().edit().remove("job").commit();
            log("job", "previous run ended during: " + job);
        }
        return job;
    }
}
