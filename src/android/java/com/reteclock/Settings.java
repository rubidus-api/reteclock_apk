package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import com.reteclock.core.ClockDefaults;
import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.FontLibrary;
import com.reteclock.core.ImageFit;
import com.reteclock.core.ImageRoles;
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
    public static final String KEY_TEXT_COLOR = "text_color";
    public static final String KEY_BACKGROUND_COLOR = "background_color";
    public static final String KEY_RUN_UNFINISHED = "run_unfinished";
    public static final String KEY_SAFE_NOTICE = "safe_notice";
    public static final String KEY_DIRECT_START = "direct_start";
    public static final String KEY_TIMER_ON = "timer_on";
    public static final String KEY_TIMER_PRESETS = "timer_presets";
    public static final String KEY_TIMER_CHOSEN = "timer_chosen";
    public static final String KEY_TIMER_ALERT = "timer_alert";

    /** How the timer makes itself heard. */
    public static final int ALERT_SOUND = 0;
    public static final int ALERT_VIBRATE = 1;
    public static final int ALERT_SILENT = 2;

    /** How long a still background image shows before the slideshow moves on. */
    public static final int DEFAULT_STILL_SECONDS = 10;

    public static final String KEY_POOL_BACKGROUND = "pool_background";
    public static final String KEY_POOL_TEXT = "pool_text";
    public static final String KEY_POOL_MIGRATED = "pool_migrated";

    /** Where imported fonts live, inside the app's own storage: no permission needed to read it. */
    private static final String FONT_DIR = "fonts";
    /** Where every image lives — one pool; the two role lists say which serves where. */
    private static final String POOL_DIR = "images";
    /** The pre-0.5 homes of the images, read once by the migration and then left empty. */
    private static final String OLD_BACKGROUND_DIR = "background";
    private static final String OLD_FOREGROUND_DIR = "foreground";

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
     * The one pool every image lives in. The font library is a plain file store with safe names,
     * which is exactly what images need too; only the directory differs. The two role lists —
     * {@link #roles} — say which image serves as a background and which fills the text;
     * everything else is held.
     */
    public static FontLibrary images(Context context) {
        migratePool(context);
        return new FontLibrary(new File(context.getFilesDir(), POOL_DIR));
    }

    /**
     * Moves the pre-0.5 background and text images into the pool, once, keeping their roles.
     *
     * The files are renamed in, not copied, so their stored dates survive; a name that collides
     * across the two old directories steps aside, and the user's arrangement follows the new
     * name. Nothing here runs again once the flag is set.
     */
    private static void migratePool(Context context) {
        if (prefs(context).getBoolean(KEY_POOL_MIGRATED, false)) {
            return;
        }
        FontLibrary pool = new FontLibrary(new File(context.getFilesDir(), POOL_DIR));
        java.util.List<String> background = new java.util.ArrayList<String>();
        java.util.List<String> text = new java.util.ArrayList<String>();
        java.util.List<String> arrangement = backgroundCustomOrder(context);

        File oldBackground = new File(context.getFilesDir(), OLD_BACKGROUND_DIR);
        for (FontLibrary.Entry entry : new FontLibrary(oldBackground).list()) {
            try {
                String stored = pool.absorb(new File(oldBackground, entry.name));
                if (stored != null) {
                    background.add(stored);
                    int at = arrangement.indexOf(entry.name);
                    if (at >= 0 && !stored.equals(entry.name)) {
                        arrangement.set(at, stored);
                    }
                }
            } catch (java.io.IOException e) {
                // A file that cannot move stays behind; better a lost slide than a crash.
            }
        }
        File oldForeground = new File(context.getFilesDir(), OLD_FOREGROUND_DIR);
        for (FontLibrary.Entry entry : new FontLibrary(oldForeground).list()) {
            try {
                String stored = pool.absorb(new File(oldForeground, entry.name));
                if (stored != null) {
                    text.add(stored);
                }
            } catch (java.io.IOException e) {
            }
        }
        oldBackground.delete();
        oldForeground.delete();

        saveRoles(context, new ImageRoles.Lists(background, text));
        setBackgroundCustomOrder(context, arrangement);
        prefs(context).edit().putBoolean(KEY_POOL_MIGRATED, true)
                .remove(KEY_FOREGROUND).commit();
    }

    /** The two role lists: who is a background, who fills the text; the rest of the pool is held. */
    public static ImageRoles.Lists roles(Context context) {
        return new ImageRoles.Lists(names(context, KEY_POOL_BACKGROUND),
                names(context, KEY_POOL_TEXT));
    }

    public static void saveRoles(Context context, ImageRoles.Lists lists) {
        prefs(context).edit()
                .putString(KEY_POOL_BACKGROUND, joined(lists.background))
                .putString(KEY_POOL_TEXT, joined(lists.text))
                .commit();
    }

    private static java.util.List<String> names(Context context, String key) {
        String stored = prefs(context).getString(key, "");
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String name : stored.split("\n")) {
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out;
    }

    private static String joined(java.util.List<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(name);
        }
        return out.toString();
    }

    /** The pool in the order the settings screen shows and the shows play. */
    public static java.util.List<FontLibrary.Entry> orderedImages(Context context) {
        return SlideOrder.apply(images(context).list(),
                backgroundOrderMode(context), backgroundCustomOrder(context));
    }

    /** The files serving one role, in pool order. */
    public static java.util.List<File> filesFor(Context context, java.util.List<String> role) {
        FontLibrary pool = images(context);
        java.util.List<File> out = new java.util.ArrayList<File>();
        for (FontLibrary.Entry entry : ImageRoles.filter(orderedImages(context), role)) {
            File file = pool.file(entry.name);
            if (file != null) {
                out.add(file);
            }
        }
        return out;
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

    /**
     * The mark a running clock leaves behind.
     *
     * Written when the clock starts and cleared once it has drawn happily for a while — or as soon
     * as it is put aside, which is proof enough that it was answering. A start that finds the mark
     * still there knows the run before it never got that far: killed, or hung on something it was
     * asked to draw. See {@link com.reteclock.core.SafeStart}.
     */
    public static boolean runUnfinished(Context context) {
        return prefs(context).getBoolean(KEY_RUN_UNFINISHED, false);
    }

    public static void setRunUnfinished(Context context, boolean unfinished) {
        prefs(context).edit().putBoolean(KEY_RUN_UNFINISHED, unfinished).commit();
    }

    /** Whether a safe run has happened that the settings screen has not yet reported. */
    public static boolean safeNotice(Context context) {
        return prefs(context).getBoolean(KEY_SAFE_NOTICE, false);
    }

    public static void setSafeNotice(Context context, boolean pending) {
        prefs(context).edit().putBoolean(KEY_SAFE_NOTICE, pending).commit();
    }

    /**
     * Whether the home-screen button opens the clock straight away.
     *
     * Off by default: the button opens the settings, with the clock one press away. That is the way
     * back in when an imported picture or font has made the clock unusable — the one thing a
     * full-screen clock with no buttons cannot otherwise offer.
     */
    public static boolean directStart(Context context) {
        return prefs(context).getBoolean(KEY_DIRECT_START, false);
    }

    public static void setDirectStart(Context context, boolean direct) {
        prefs(context).edit().putBoolean(KEY_DIRECT_START, direct).commit();
    }

    /**
     * Whether the timer shows on the clock at all.
     *
     * Off by default, and off means off: the clock draws exactly what it drew before the timer
     * existed, and costs exactly what it cost.
     */
    public static boolean timerOn(Context context) {
        return prefs(context).getBoolean(KEY_TIMER_ON, false);
    }

    public static void setTimerOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_TIMER_ON, on).commit();
    }

    /**
     * The presets, as the core writes them.
     *
     * A first run finds the starter set rather than an empty list: a timer with no presets offers
     * no way to understand what a preset is for.
     */
    public static java.util.List<com.reteclock.core.TimerPreset> timerPresets(Context context) {
        String stored = prefs(context).getString(KEY_TIMER_PRESETS, null);
        if (stored == null) {
            return com.reteclock.core.TimerPresets.starter();
        }
        return com.reteclock.core.TimerPresets.parse(stored);
    }

    public static void setTimerPresets(Context context,
            java.util.List<com.reteclock.core.TimerPreset> presets) {
        prefs(context).edit()
                .putString(KEY_TIMER_PRESETS, com.reteclock.core.TimerPresets.toText(presets))
                .commit();
    }

    /** Which preset the hourglass last chose, by position; clamped to what exists. */
    public static int timerChosen(Context context) {
        int at = prefs(context).getInt(KEY_TIMER_CHOSEN, 0);
        int count = timerPresets(context).size();
        if (count == 0) {
            return 0;
        }
        return at < 0 ? 0 : at >= count ? count - 1 : at;
    }

    public static void setTimerChosen(Context context, int index) {
        prefs(context).edit().putInt(KEY_TIMER_CHOSEN, Math.max(0, index)).commit();
    }

    /** Sound, vibrate or silent — one setting covering every noise the timer makes. */
    public static int timerAlert(Context context) {
        return prefs(context).getInt(KEY_TIMER_ALERT, ALERT_SOUND);
    }

    public static void setTimerAlert(Context context, int mode) {
        prefs(context).edit().putInt(KEY_TIMER_ALERT, mode).commit();
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

    /** The colour a settings key holds; ClockColors resolves the pair before drawing. */
    public static int color(Context context, String key) {
        int fallback = KEY_TEXT_COLOR.equals(key)
                ? com.reteclock.core.ClockColors.DEFAULT_TEXT
                : com.reteclock.core.ClockColors.DEFAULT_BACKGROUND;
        return prefs(context).getInt(key, fallback);
    }

    public static void setColor(Context context, String key, int color) {
        prefs(context).edit().putInt(key, color).commit();
    }

    /** The display options the clock draws with. */
    public static ClockOptions options(Context context) {
        return new ClockOptions(showSeconds(context), dateStyle(context),
                timePercent(context, KEY_TIME_PERCENT_WIDE) / 100f,
                timePercent(context, KEY_TIME_PERCENT_TALL) / 100f);
    }
}
