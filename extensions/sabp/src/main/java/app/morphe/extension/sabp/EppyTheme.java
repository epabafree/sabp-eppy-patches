package app.morphe.extension.sabp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;

import java.util.HashMap;

/**
 * Material 3 colour roles, typography and user preferences for the Eppy UI.
 *
 * <p>Colours come from the wallpaper (Material You system palettes, Android 12+), from the playing
 * book's cover, or from a fixed seed colour. Roles are always checked for readable contrast, so
 * artwork never makes text unreadable.
 */
public final class EppyTheme {
    public static final String PREFS = "eppy_ui";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_BLACK = "black";
    public static final String ACCENT_WALLPAPER = "wallpaper";
    public static final String ACCENT_COVER = "cover";
    public static final String ACCENT_CUSTOM = "custom";

    // Colour roles (ARGB).
    public int primary, onPrimary, primaryContainer, onPrimaryContainer;
    public int secondaryContainer, onSecondaryContainer;
    public int tertiary;
    public int surface, surfaceDim, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;
    public int onSurface, onSurfaceVariant, outline, outlineVariant, scrim;
    public boolean dark;

    // Preferences.
    public String mode;
    public String accentSource;
    public int customSeed;
    public boolean reduceMotion;
    public boolean haptics;
    public boolean highContrast;
    public boolean waveformSeekBar;

    private static Typeface baseFont;
    private static final HashMap<Integer, Typeface> fontCache = new HashMap<>();

    private EppyTheme() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Builds the theme for the current preferences; seed is the cover accent or 0. */
    public static EppyTheme resolve(Context c, int coverSeed) {
        EppyTheme t = new EppyTheme();
        SharedPreferences p = prefs(c);
        t.mode = p.getString("mode", configString(c, "eppy_default_theme", MODE_SYSTEM));
        t.accentSource = p.getString("accent", configString(c, "eppy_default_accent", ACCENT_WALLPAPER));
        t.customSeed = p.getInt("seed", parseColor(configString(c, "eppy_custom_accent", ""), 0xFFE8735A));
        t.reduceMotion = p.getBoolean("reduceMotion", systemReducedMotion(c));
        t.haptics = p.getBoolean("haptics", true);
        t.highContrast = p.getBoolean("highContrast", false);
        t.waveformSeekBar = p.getBoolean("waveform", false);

        boolean systemDark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        t.dark = MODE_DARK.equals(t.mode) || MODE_BLACK.equals(t.mode) || (MODE_SYSTEM.equals(t.mode) && systemDark);
        boolean black = MODE_BLACK.equals(t.mode);

        boolean dynamic = Build.VERSION.SDK_INT >= 31;
        int seed;
        if (ACCENT_COVER.equals(t.accentSource) && coverSeed != 0) {
            seed = coverSeed;
            dynamic = false;
        } else if (ACCENT_CUSTOM.equals(t.accentSource) || !dynamic) {
            seed = t.customSeed;
            dynamic = false;
        } else {
            seed = 0;
        }

        if (dynamic) t.fromSystem(c, black);
        else t.fromSeed(seed, black);
        t.enforceContrast();
        return t;
    }

    private static boolean systemReducedMotion(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Reads a string the patch writes into the app's resources (patch options). */
    static String configString(Context c, String name, String fallback) {
        try {
            int id = c.getResources().getIdentifier(name, "string", c.getPackageName());
            if (id != 0) {
                String v = c.getString(id);
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    static int parseColor(String s, int fallback) {
        try {
            if (s == null) return fallback;
            s = s.replace("\\", "").replace("\"", "").trim();
            if (s.isEmpty()) return fallback;
            if (!s.startsWith("#")) s = "#" + s;
            return Color.parseColor(s) | 0xFF000000;
        } catch (Throwable t) {
            return fallback;
        }
    }

    // ---- palettes -----------------------------------------------------------------------------

    private void fromSystem(Context c, boolean black) {
        if (dark) {
            primary = sys(c, android.R.color.system_accent1_200);
            onPrimary = sys(c, android.R.color.system_accent1_800);
            primaryContainer = sys(c, android.R.color.system_accent1_700);
            onPrimaryContainer = sys(c, android.R.color.system_accent1_100);
            secondaryContainer = sys(c, android.R.color.system_accent2_700);
            onSecondaryContainer = sys(c, android.R.color.system_accent2_100);
            tertiary = sys(c, android.R.color.system_accent3_200);
            surface = black ? Color.BLACK : sys(c, android.R.color.system_neutral1_900);
            surfaceDim = black ? Color.BLACK : blend(sys(c, android.R.color.system_neutral1_900), Color.BLACK, 0.25f);
            surfaceContainerLow = black ? 0xFF0B0B0C : blend(surface, sys(c, android.R.color.system_neutral1_800), 0.35f);
            surfaceContainer = black ? 0xFF141416 : blend(surface, sys(c, android.R.color.system_neutral1_800), 0.6f);
            surfaceContainerHigh = black ? 0xFF1C1C1F : sys(c, android.R.color.system_neutral1_800);
            surfaceContainerHighest = black ? 0xFF26262A : blend(sys(c, android.R.color.system_neutral1_800), sys(c, android.R.color.system_neutral1_700), 0.4f);
            onSurface = sys(c, android.R.color.system_neutral1_100);
            onSurfaceVariant = sys(c, android.R.color.system_neutral2_200);
            outline = sys(c, android.R.color.system_neutral2_400);
            outlineVariant = sys(c, android.R.color.system_neutral2_700);
        } else {
            primary = sys(c, android.R.color.system_accent1_600);
            onPrimary = sys(c, android.R.color.system_accent1_0);
            primaryContainer = sys(c, android.R.color.system_accent1_100);
            onPrimaryContainer = sys(c, android.R.color.system_accent1_900);
            secondaryContainer = sys(c, android.R.color.system_accent2_100);
            onSecondaryContainer = sys(c, android.R.color.system_accent2_900);
            tertiary = sys(c, android.R.color.system_accent3_600);
            surface = sys(c, android.R.color.system_neutral1_10);
            surfaceDim = sys(c, android.R.color.system_neutral1_100);
            surfaceContainerLow = blend(sys(c, android.R.color.system_neutral1_10), sys(c, android.R.color.system_neutral1_50), 0.5f);
            surfaceContainer = sys(c, android.R.color.system_neutral1_50);
            surfaceContainerHigh = blend(sys(c, android.R.color.system_neutral1_50), sys(c, android.R.color.system_neutral1_100), 0.5f);
            surfaceContainerHighest = sys(c, android.R.color.system_neutral1_100);
            onSurface = sys(c, android.R.color.system_neutral1_900);
            onSurfaceVariant = sys(c, android.R.color.system_neutral2_700);
            outline = sys(c, android.R.color.system_neutral2_500);
            outlineVariant = sys(c, android.R.color.system_neutral2_200);
        }
        scrim = 0x99000000;
    }

    private static int sys(Context c, int id) {
        return c.getColor(id);
    }

    /** Approximates the Material 3 tonal palettes from a seed colour using HSL tones. */
    private void fromSeed(int seed, boolean black) {
        float[] hsl = toHsl(seed);
        float h = hsl[0];
        float s = Math.max(0.35f, Math.min(0.85f, hsl[1]));
        float sN = Math.min(0.10f, s * 0.12f); // neutral: barely tinted
        float sN2 = Math.min(0.18f, s * 0.22f);
        float h2 = h;
        float h3 = (h + 60f) % 360f;
        if (dark) {
            primary = tone(h, s, 80);
            onPrimary = tone(h, s, 20);
            primaryContainer = tone(h, s * 0.9f, 30);
            onPrimaryContainer = tone(h, s, 90);
            secondaryContainer = tone(h2, s * 0.35f, 30);
            onSecondaryContainer = tone(h2, s * 0.35f, 90);
            tertiary = tone(h3, s * 0.6f, 80);
            surface = black ? Color.BLACK : tone(h, sN, 6);
            surfaceDim = black ? Color.BLACK : tone(h, sN, 4);
            surfaceContainerLow = black ? 0xFF0B0B0C : tone(h, sN, 10);
            surfaceContainer = black ? 0xFF141416 : tone(h, sN, 12);
            surfaceContainerHigh = black ? 0xFF1C1C1F : tone(h, sN, 17);
            surfaceContainerHighest = black ? 0xFF26262A : tone(h, sN, 22);
            onSurface = tone(h, sN, 90);
            onSurfaceVariant = tone(h, sN2, 80);
            outline = tone(h, sN2, 60);
            outlineVariant = tone(h, sN2, 30);
        } else {
            primary = tone(h, s, 40);
            onPrimary = Color.WHITE;
            primaryContainer = tone(h, s, 90);
            onPrimaryContainer = tone(h, s, 10);
            secondaryContainer = tone(h2, s * 0.35f, 90);
            onSecondaryContainer = tone(h2, s * 0.35f, 10);
            tertiary = tone(h3, s * 0.6f, 40);
            surface = tone(h, sN, 98);
            surfaceDim = tone(h, sN, 87);
            surfaceContainerLow = tone(h, sN, 96);
            surfaceContainer = tone(h, sN, 94);
            surfaceContainerHigh = tone(h, sN, 92);
            surfaceContainerHighest = tone(h, sN, 90);
            onSurface = tone(h, sN, 10);
            onSurfaceVariant = tone(h, sN2, 30);
            outline = tone(h, sN2, 50);
            outlineVariant = tone(h, sN2, 80);
        }
        scrim = 0x99000000;
    }

    private void enforceContrast() {
        // Text on primary must be readable (4.5:1); primary on surfaces must be visible (3:1).
        if (contrast(onPrimary, primary) < 4.5) {
            onPrimary = contrast(Color.BLACK, primary) > contrast(Color.WHITE, primary) ? Color.BLACK : Color.WHITE;
        }
        int guard = 0;
        while (contrast(primary, surface) < 3.0 && guard++ < 12) {
            primary = dark ? blend(primary, Color.WHITE, 0.12f) : blend(primary, Color.BLACK, 0.12f);
        }
        if (contrast(onPrimaryContainer, primaryContainer) < 4.5) {
            onPrimaryContainer = dark ? Color.WHITE : Color.BLACK;
        }
        if (highContrast) {
            onSurfaceVariant = onSurface;
            outline = blend(outline, onSurface, 0.6f);
            outlineVariant = blend(outlineVariant, onSurface, 0.4f);
        }
        if (contrast(onSurfaceVariant, surface) < 4.5) onSurfaceVariant = blend(onSurfaceVariant, onSurface, 0.5f);
    }

    // ---- cover accent ---------------------------------------------------------------------------

    /**
     * Picks a vivid, representative colour from a cover: hues are binned and weighted by
     * saturation and mid-lightness, so a big black or white area does not win. Returns 0 if the
     * cover has no usable colour (grey-scale art), in which case the default accent stays.
     */
    public static int seedFromCover(Bitmap cover) {
        if (cover == null) return 0;
        try {
            Bitmap small = Bitmap.createScaledBitmap(cover, 40, 40, true);
            float[] weight = new float[36];
            float[] sumS = new float[36];
            float[] sumL = new float[36];
            float[] hsl = new float[3];
            for (int y = 0; y < small.getHeight(); y++) {
                for (int x = 0; x < small.getWidth(); x++) {
                    int px = small.getPixel(x, y);
                    if (Color.alpha(px) < 200) continue;
                    hsl = toHsl(px);
                    float sat = hsl[1];
                    float light = hsl[2];
                    if (sat < 0.25f || light < 0.15f || light > 0.88f) continue;
                    float w = sat * (1f - Math.abs(light - 0.5f) * 1.6f);
                    int bin = Math.min(35, (int) (hsl[0] / 10f));
                    weight[bin] += w;
                    sumS[bin] += sat * w;
                    sumL[bin] += light * w;
                }
            }
            if (small != cover) small.recycle();
            int best = -1;
            float bestW = 0;
            for (int i = 0; i < 36; i++) {
                float w = weight[i] + (i > 0 ? weight[i - 1] : weight[35]) * 0.5f + (i < 35 ? weight[i + 1] : weight[0]) * 0.5f;
                if (w > bestW) {
                    bestW = w;
                    best = i;
                }
            }
            if (best < 0 || bestW < 4f) return 0;
            float s = sumS[best] / weight[best];
            float l = sumL[best] / weight[best];
            return fromHsl(best * 10f + 5f, s, l);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ---- typography ---------------------------------------------------------------------------

    /** Outfit (bundled by the patch) at a given weight, falling back to the system sans-serif. */
    public static Typeface font(Context c, int weight) {
        Typeface cached = fontCache.get(weight);
        if (cached != null) return cached;
        Typeface t = null;
        try {
            t = new Typeface.Builder(c.getAssets(), "fonts/eppy_outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .setWeight(weight)
                    .build();
        } catch (Throwable ignored) {
        }
        if (t == null) {
            if (baseFont == null) baseFont = Typeface.create("sans-serif", Typeface.NORMAL);
            t = Typeface.create(baseFont, weight, false);
        }
        fontCache.put(weight, t);
        return t;
    }

    // ---- colour maths -------------------------------------------------------------------------

    public static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        int al = (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
        return Color.argb(al, r, g, bl);
    }

    public static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24);
    }

    static double luminance(int c) {
        double r = channel(Color.red(c) / 255.0);
        double g = channel(Color.green(c) / 255.0);
        double b = channel(Color.blue(c) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static double contrast(int a, int b) {
        double la = luminance(a) + 0.05;
        double lb = luminance(b) + 0.05;
        return la > lb ? la / lb : lb / la;
    }

    private static int tone(float h, float s, int lightness) {
        return fromHsl(h, s, lightness / 100f);
    }

    static float[] toHsl(int color) {
        float r = Color.red(color) / 255f, g = Color.green(color) / 255f, b = Color.blue(color) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float h = 0f, s = 0f;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = ((g - b) / d + (g < b ? 6f : 0f));
            else if (max == g) h = ((b - r) / d + 2f);
            else h = ((r - g) / d + 4f);
            h *= 60f;
        }
        return new float[]{h, s, l};
    }

    static int fromHsl(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }
}
