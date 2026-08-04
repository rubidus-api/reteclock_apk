package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import com.reteclock.core.ClockDefaults;
import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.FontLibrary;
import com.reteclock.core.ImageFit;
import com.reteclock.core.SlideOrder;

/** The stored settings, and the bridge to the pure-Java {@link ClockOptions}. */
public final class Settings {

    public static final String PREFS = "reteclock";

    public static final String KEY_START_WHEN_CHARGING = "start_when_charging";
    public static final String KEY_SHOW_SECONDS = "show_seconds";
    public static final String KEY_DATE_STYLE = "date_style";
    public static final String KEY_FONT = "font";
    public static final String KEY_BOLD = "text_bold";
    public static final String KEY_ITALIC = "text_italic";
    public static final String KEY_UNDERLINE = "text_underline";
    public static final String KEY_HINT_SEEN = "hint_seen";
    public static final String KEY_BACKGROUND_FIT = "background_fit";
    public static final String KEY_BACKGROUND_STILL_SECONDS = "background_still_seconds";
    public static final String KEY_BACKGROUND_FADE = "background_fade";
    public static final String KEY_BACKGROUND_ORDER_MODE = "background_order_mode";
    public static final String KEY_BACKGROUND_ORDER = "background_order";
    public static final String KEY_FOREGROUND = "foreground";
    public static final String KEY_TIME_PERCENT_WIDE = "time_percent_wide";
    public static final String KEY_TIME_PERCENT_TALL = "time_percent_tall";

    /** How long a still background image shows before the slideshow moves on. */
    public static final int DEFAULT_STILL_SECONDS = 10;

    /** Where imported fonts live, inside the app's own storage: no permission needed to read it. */
    private static final String FONT_DIR = "fonts";
    /** Where the background images live, next to the fonts and stored the same way. */
    private static final String BACKGROUND_DIR = "background";
    /** Where the image that fills the text lives. */
    private static final String FOREGROUND_DIR = "foreground";

    private Settings() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean startWhenCharging(Context context) {
        return prefs(context).getBoolean(KEY_START_WHEN_CHARGING, true);
    }

    public static void setStartWhenCharging(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_START_WHEN_CHARGING, enabled).commit();
    }

    public static boolean showSeconds(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_SECONDS, true);
    }

    public static void setShowSeconds(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SHOW_SECONDS, enabled).commit();
    }

    public static int dateStyle(Context context) {
        return prefs(context).getInt(KEY_DATE_STYLE, ClockOptions.DATE_STYLE_NAME);
    }

    public static void setDateStyle(Context context, int style) {
        prefs(context).edit().putInt(KEY_DATE_STYLE, style).commit();
    }

    /** The fonts the user has imported. */
    public static FontLibrary fonts(Context context) {
        return new FontLibrary(new File(context.getFilesDir(), FONT_DIR));
    }

    /**
     * The store holding the background images. The font library is a plain file store with safe
     * names, which is exactly what images need too; only the directory differs. Everything in it
     * is the slideshow, in name order — there is no separate list of what is chosen.
     */
    public static FontLibrary backgrounds(Context context) {
        return new FontLibrary(new File(context.getFilesDir(), BACKGROUND_DIR));
    }

    /** The store holding the image that fills the text, one at most. */
    public static FontLibrary foregrounds(Context context) {
        return new FontLibrary(new File(context.getFilesDir(), FOREGROUND_DIR));
    }

    /** The stored name of the text-fill image, or "" for plain white text. */
    public static String foregroundName(Context context) {
        return prefs(context).getString(KEY_FOREGROUND, "");
    }

    public static void setForegroundName(Context context, String name) {
        prefs(context).edit().putString(KEY_FOREGROUND, name == null ? "" : name).commit();
    }

    /**
     * The text-fill image file, or null when none is set — or when the setting names a file that
     * has since gone away, so a lost image means white text rather than a crash.
     */
    public static File foregroundFile(Context context) {
        return foregrounds(context).file(foregroundName(context));
    }

    /** How long a still background shows before the slideshow moves on. */
    public static int backgroundStillSeconds(Context context) {
        return prefs(context).getInt(KEY_BACKGROUND_STILL_SECONDS, DEFAULT_STILL_SECONDS);
    }

    public static void setBackgroundStillSeconds(Context context, int seconds) {
        prefs(context).edit().putInt(KEY_BACKGROUND_STILL_SECONDS, seconds).commit();
    }

    /** How the background images are ordered; a {@link SlideOrder} mode. */
    public static int backgroundOrderMode(Context context) {
        return prefs(context).getInt(KEY_BACKGROUND_ORDER_MODE, SlideOrder.NAME_ASC);
    }

    public static void setBackgroundOrderMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_BACKGROUND_ORDER_MODE, mode).commit();
    }

    /**
     * The user's own arrangement, one name per line. Newline is safe as a separator: stored names
     * come out of the library's sanitiser, which never lets one through.
     */
    public static java.util.List<String> backgroundCustomOrder(Context context) {
        String stored = prefs(context).getString(KEY_BACKGROUND_ORDER, "");
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String name : stored.split("\n")) {
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out;
    }

    public static void setBackgroundCustomOrder(Context context, java.util.List<String> names) {
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(name);
        }
        prefs(context).edit().putString(KEY_BACKGROUND_ORDER, joined.toString()).commit();
    }

    /**
     * The background images in the order they show and list — the one place both the slideshow
     * and the settings screen read, so they cannot disagree.
     */
    public static java.util.List<FontLibrary.Entry> orderedBackgrounds(Context context) {
        return SlideOrder.apply(backgrounds(context).list(),
                backgroundOrderMode(context), backgroundCustomOrder(context));
    }

    /** Whether one background cross-fades into the next, or just becomes it. */
    public static boolean backgroundFade(Context context) {
        return prefs(context).getBoolean(KEY_BACKGROUND_FADE, true);
    }

    public static void setBackgroundFade(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND_FADE, enabled).commit();
    }

    /** How the background images are fitted to the screen; an {@link ImageFit} mode. */
    public static int backgroundFit(Context context) {
        return prefs(context).getInt(KEY_BACKGROUND_FIT, ImageFit.COVER);
    }

    public static void setBackgroundFit(Context context, int mode) {
        prefs(context).edit().putInt(KEY_BACKGROUND_FIT, mode).commit();
    }

    /**
     * Whether the user has been to the settings screen.
     *
     * The clock hints that a long press opens it, and stops once they have found it — a hint nobody
     * needs any more is just something in the way.
     */
    public static boolean hintSeen(Context context) {
        return prefs(context).getBoolean(KEY_HINT_SEEN, false);
    }

    public static void setHintSeen(Context context) {
        prefs(context).edit().putBoolean(KEY_HINT_SEEN, true).commit();
    }

    /** The fields that can each carry their own font, in the order the settings screen lists them. */
    public static final String[] FONT_ROLES = {
        ClockLayout.ROLE_HOUR,
        ClockLayout.ROLE_MINUTE,
        ClockLayout.ROLE_SECOND,
        ClockLayout.ROLE_WEEKDAY,
        ClockLayout.ROLE_MONTH_DAY,
        ClockLayout.ROLE_YEAR,
    };

    /**
     * The font chosen for one field, or "" for the system font.
     *
     * Before fonts were per-field there was a single choice under KEY_FONT. It is still read as the
     * starting value for every field, so nobody's setting is lost by upgrading.
     */
    public static String fontNameFor(Context context, String role) {
        return prefs(context).getString(KEY_FONT + "_" + role, fontName(context));
    }

    public static void setFontNameFor(Context context, String role, String name) {
        prefs(context).edit().putString(KEY_FONT + "_" + role, name == null ? "" : name).commit();
    }

    /**
     * That field's font file, or null to draw it with the system font.
     *
     * Null also when the setting names a font that has since been deleted, so removing a font in
     * use leaves a working clock rather than a blank one.
     */
    public static File fontFileFor(Context context, String role) {
        return fonts(context).file(fontNameFor(context, role));
    }

    /** The font every field started from, kept only so an existing setting still means something. */
    public static String fontName(Context context) {
        return prefs(context).getString(KEY_FONT, "");
    }

    public static void setFontName(Context context, String name) {
        prefs(context).edit().putString(KEY_FONT, name == null ? "" : name).commit();
    }

    /**
     * A decoration for one field.
     *
     * Before decorations were per-field there was a single flag for the whole clock. It is still
     * read as the starting value for every field, so nobody's setting is lost by upgrading — the
     * same arrangement the fonts use.
     */
    public static boolean decoration(Context context, String key, String role) {
        return prefs(context).getBoolean(key + "_" + role, defaultDecoration(context, key, role));
    }

    /**
     * What a field's decoration is before anyone has touched it.
     *
     * The hour and the minute start bold — see {@link ClockDefaults} — and that ignores the old
     * single flag, because in the versions that had it those two were drawn bold regardless of what
     * it said. Everything else falls back to that flag, so a decoration set before there were
     * per-field ones still means something.
     */
    private static boolean defaultDecoration(Context context, String key, String role) {
        if (KEY_BOLD.equals(key) && ClockDefaults.boldByDefault(role)) {
            return true;
        }
        return prefs(context).getBoolean(key, false);
    }

    public static void setDecoration(Context context, String key, String role, boolean enabled) {
        prefs(context).edit().putBoolean(key + "_" + role, enabled).commit();
    }

    public static boolean bold(Context context, String role) {
        return decoration(context, KEY_BOLD, role);
    }

    public static boolean italic(Context context, String role) {
        return decoration(context, KEY_ITALIC, role);
    }

    public static boolean underline(Context context, String role) {
        return decoration(context, KEY_UNDERLINE, role);
    }

    /**
     * The time's share of the screen, in whole percent — of the width when the screen is wide, of
     * the content height when it is tall. ClockOptions clamps, so a wild stored value cannot
     * break the layout.
     */
    public static int timePercent(Context context, String key) {
        int fallback = KEY_TIME_PERCENT_WIDE.equals(key)
                ? Math.round(ClockOptions.DEFAULT_TIME_FRACTION_WIDE * 100f)
                : Math.round(ClockOptions.DEFAULT_TIME_FRACTION_TALL * 100f);
        return prefs(context).getInt(key, fallback);
    }

    public static void setTimePercent(Context context, String key, int percent) {
        prefs(context).edit().putInt(key, percent).commit();
    }

    /** The display options the clock draws with. */
    public static ClockOptions options(Context context) {
        return new ClockOptions(showSeconds(context), dateStyle(context),
                timePercent(context, KEY_TIME_PERCENT_WIDE) / 100f,
                timePercent(context, KEY_TIME_PERCENT_TALL) / 100f);
    }
}
