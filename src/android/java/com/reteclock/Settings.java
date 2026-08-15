package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import com.reteclock.core.ClockDefaults;
import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.SummerTime;
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
    public static final String KEY_OUTLINE = "text_outline";
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
    public static final String KEY_STAY_UNLOCKED = "stay_unlocked";
    public static final String KEY_TIMER_HIDDEN = "timer_hidden";
    public static final String KEY_CALENDAR_ON = "calendar_on";
    public static final String KEY_CALENDAR_MONDAY = "calendar_week_monday";
    public static final String KEY_CALENDAR_HEADER = "calendar_header";
    public static final String KEY_CALENDAR_SYSTEM = "calendar_system";
    public static final String KEY_CALENDAR_WEEK_START = "calendar_week_start";
    public static final String KEY_CALENDAR_BADGE = "calendar_gregorian_badge";
    public static final String KEY_HIJRI_OFFSET = "calendar_hijri_offset";
    public static final String KEY_TIME_SOURCE = "time_source";
    public static final String KEY_UTC_OFFSET = "time_utc_offset";
    public static final String KEY_DST_PRESET = "time_dst_preset";
    public static final String KEY_DST_CUSTOM = "time_dst_custom";
    public static final String KEY_HOUR12 = "clock_hour12";
    public static final String KEY_NOON_STYLE = "clock_noon_style";
    public static final String KEY_MIDNIGHT_STYLE = "clock_midnight_style";
    public static final String KEY_QUOTE_ON = "quote_on";
    public static final String KEY_RUN_ORIGIN = "timer_run_origin";
    public static final String KEY_RUN_PAUSED_AT = "timer_run_paused_at";
    public static final String KEY_RUN_PRESET = "timer_run_preset";
    public static final String KEY_VOICE_INIT = "voice_init";
    public static final String KEY_VOICE_LANG = "voice_lang";

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

    /**
     * Every stored setting, for carrying the arrangement off this phone.
     *
     * The judgement of what may travel is not made here — see
     * {@link com.reteclock.core.SettingsText#isPortable} — so that it can be tested away from
     * Android.
     */
    public static java.util.Map<String, ?> all(Context context) {
        return prefs(context).getAll();
    }

    /** An editor for writing a carried arrangement back in one commit. */
    public static SharedPreferences.Editor edit(Context context) {
        return prefs(context).edit();
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

    /**
     * Whether the clock keeps the lock screen away and stays up indefinitely.
     *
     * Off by default: showing over a lock screen is not something to do to somebody without being
     * asked. On, the clock behaves the way it already does when the charger starts it — the screen
     * stays on, the keyguard is dismissed, and nothing takes the screen away from it.
     */
    public static boolean stayUnlocked(Context context) {
        return prefs(context).getBoolean(KEY_STAY_UNLOCKED, false);
    }

    public static void setStayUnlocked(Context context, boolean stay) {
        prefs(context).edit().putBoolean(KEY_STAY_UNLOCKED, stay).commit();
    }

    /**
     * The running timer, as three numbers, so it survives leaving the clock and coming back — and
     * so the screensaver can show what the clock started. See {@link com.reteclock.core.TimerMemory}.
     */
    /** Whether the strip is put away, leaving only the hourglass on the clock face. */
    public static boolean timerHidden(Context context) {
        return prefs(context).getBoolean(KEY_TIMER_HIDDEN, false);
    }

    public static void setTimerHidden(Context context, boolean hidden) {
        prefs(context).edit().putBoolean(KEY_TIMER_HIDDEN, hidden).commit();
    }

    public static void rememberRun(Context context, String identity, long originMs,
            long pausedAtMs) {
        prefs(context).edit()
                .putString(KEY_RUN_PRESET, identity)
                .putLong(KEY_RUN_ORIGIN, originMs)
                .putLong(KEY_RUN_PAUSED_AT, pausedAtMs)
                .commit();
    }

    public static void forgetRun(Context context) {
        prefs(context).edit()
                .putLong(KEY_RUN_ORIGIN, com.reteclock.core.TimerMemory.NONE)
                .putString(KEY_RUN_PRESET, "")
                .commit();
    }

    public static long runOrigin(Context context) {
        return prefs(context).getLong(KEY_RUN_ORIGIN, com.reteclock.core.TimerMemory.NONE);
    }

    /** Which preset the stored run belongs to; a run is only taken up by that same preset. */
    public static String runPreset(Context context) {
        return prefs(context).getString(KEY_RUN_PRESET, "");
    }

    public static long runPausedAt(Context context) {
        return prefs(context).getLong(KEY_RUN_PAUSED_AT, -1L);
    }

    /**
     * What the speech engine turned out to be capable of, learned the last time one was started.
     *
     * Written by the timer's voice and read by the settings screen, which is the only place that
     * can tell the user their typed message will never be heard on this device.
     */
    public static void rememberVoice(Context context, int init, int language) {
        prefs(context).edit()
                .putInt(KEY_VOICE_INIT, init)
                .putInt(KEY_VOICE_LANG, language)
                .commit();
    }

    /** {@link com.reteclock.core.VoiceState}'s summary for this device. */
    public static int voiceSummary(Context context) {
        return com.reteclock.core.VoiceState.summary(TimerVoice.engineInstalled(context),
                prefs(context).getInt(KEY_VOICE_INIT, com.reteclock.core.VoiceState.INIT_UNKNOWN),
                prefs(context).getInt(KEY_VOICE_LANG, com.reteclock.core.VoiceState.LANG_UNKNOWN));
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
        ClockLayout.ROLE_QUOTE,
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

    /** Whether the field is drawn with an outline in the opposite of the text colour. */
    public static boolean outline(Context context, String role) {
        return decoration(context, KEY_OUTLINE, role);
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

    /** Whether a saying is shown along the bottom. */
    public static boolean quoteOn(Context context) {
        return prefs(context).getBoolean(KEY_QUOTE_ON, false);
    }

    public static void setQuoteOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_QUOTE_ON, on).commit();
    }

    /** Whether a month's grid is drawn under the time. */
    public static boolean calendarOn(Context context) {
        return prefs(context).getBoolean(KEY_CALENDAR_ON, false);
    }

    public static void setCalendarOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_CALENDAR_ON, on).commit();
    }

    /** Whether the week is taken to begin on Monday rather than Sunday. */
    public static boolean calendarWeekStartsMonday(Context context) {
        return prefs(context).getBoolean(KEY_CALENDAR_MONDAY, false);
    }

    public static void setCalendarWeekStartsMonday(Context context, boolean monday) {
        prefs(context).edit().putBoolean(KEY_CALENDAR_MONDAY, monday).commit();
    }

    /** `Aug 2026` or `2026-08`; see {@link com.reteclock.core.MonthGrid}. */
    public static int calendarHeaderStyle(Context context) {
        return prefs(context).getInt(KEY_CALENDAR_HEADER, com.reteclock.core.MonthGrid.HEADER_NAME);
    }

    public static void setCalendarHeaderStyle(Context context, int style) {
        prefs(context).edit().putInt(KEY_CALENDAR_HEADER, style).commit();
    }

    /** The display options the clock draws with. */
    /** The clock takes its local time from the phone, as it always has. */
    public static final int TIME_SOURCE_PHONE = 0;
    /** Or from an offset the user set, because the phone's zone rules are out of date (RFC-0004). */
    public static final int TIME_SOURCE_MANUAL = 1;

    /** Which calendar the dates are counted in. */
    public static int calendarSystem(Context context) {
        return prefs(context).getInt(KEY_CALENDAR_SYSTEM, com.reteclock.core.Calendars.GREGORIAN);
    }

    public static void setCalendarSystem(Context context, int system) {
        prefs(context).edit().putInt(KEY_CALENDAR_SYSTEM, system).commit();
    }

    /**
     * The day the week is taken to begin on: 0 for Sunday, 1 for Monday, 6 for Saturday.
     *
     * Migrated from the old boolean the first time it is asked for, because a phone upgrading must
     * not have its week quietly rearranged.
     */
    public static int calendarWeekStart(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(KEY_CALENDAR_WEEK_START)) {
            return prefs.getBoolean(KEY_CALENDAR_MONDAY, false) ? 1 : 0;
        }
        return prefs.getInt(KEY_CALENDAR_WEEK_START, 0);
    }

    public static void setCalendarWeekStart(Context context, int weekStart) {
        prefs(context).edit().putInt(KEY_CALENDAR_WEEK_START, weekStart).commit();
    }

    /**
     * Which spelling of the month names, for one calendar.
     *
     * Stored per calendar rather than once, because "style 1" means a different thing in each: the
     * Coptic liturgical names, the Malay Hijri months, India's own spellings. One number shared
     * between them would carry a choice from one calendar to another that never made it.
     */
    public static int calendarNameStyle(Context context, int system) {
        return prefs(context).getInt(KEY_CALENDAR_SYSTEM + "_names_" + system, 0);
    }

    public static void setCalendarNameStyle(Context context, int system, int style) {
        prefs(context).edit().putInt(KEY_CALENDAR_SYSTEM + "_names_" + system, style).commit();
    }

    /** Whether the clock reads 1 to 12 with AM or PM rather than 0 to 23 (issue #24). */
    public static boolean hour12(Context context) {
        return prefs(context).getBoolean(KEY_HOUR12, false);
    }

    public static void setHour12(Context context, boolean twelve) {
        prefs(context).edit().putBoolean(KEY_HOUR12, twelve).commit();
    }

    /** How noon is written on a twelve-hour clock, and how midnight is — two questions. */
    public static int noonStyle(Context context) {
        return prefs(context).getInt(KEY_NOON_STYLE, ClockOptions.NOON_PM);
    }

    public static void setNoonStyle(Context context, int style) {
        prefs(context).edit().putInt(KEY_NOON_STYLE, style).commit();
    }

    public static int midnightStyle(Context context) {
        return prefs(context).getInt(KEY_MIDNIGHT_STYLE, ClockOptions.MIDNIGHT_AM);
    }

    public static void setMidnightStyle(Context context, int style) {
        prefs(context).edit().putInt(KEY_MIDNIGHT_STYLE, style).commit();
    }

    /** Whether the weekdays are drawn in English or in the calendar's own names. */
    public static int calendarWeekdayStyle(Context context, int system) {
        return prefs(context).getInt(KEY_CALENDAR_SYSTEM + "_weekdays_" + system, 0);
    }

    public static void setCalendarWeekdayStyle(Context context, int system, int style) {
        prefs(context).edit().putInt(KEY_CALENDAR_SYSTEM + "_weekdays_" + system, style).commit();
    }

    /** Whether the Gregorian month and day are shown as a small inverted badge. */
    public static boolean gregorianBadge(Context context) {
        return prefs(context).getBoolean(KEY_CALENDAR_BADGE, false);
    }

    public static void setGregorianBadge(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_CALENDAR_BADGE, on).commit();
    }

    /** What the user shifted the Islamic date by to match their own community: -2..+2 days. */
    public static int hijriOffset(Context context) {
        return prefs(context).getInt(KEY_HIJRI_OFFSET, 0);
    }

    public static void setHijriOffset(Context context, int days) {
        prefs(context).edit().putInt(KEY_HIJRI_OFFSET, days).commit();
    }

    /** Whether the clock follows the phone's zone or an offset of its own. */
    public static int timeSource(Context context) {
        return prefs(context).getInt(KEY_TIME_SOURCE, TIME_SOURCE_PHONE);
    }

    public static void setTimeSource(Context context, int source) {
        prefs(context).edit().putInt(KEY_TIME_SOURCE, source).commit();
    }

    /** Minutes east of UTC, when the clock is not following the phone. */
    public static int utcOffsetMinutes(Context context) {
        return prefs(context).getInt(KEY_UTC_OFFSET, phoneOffsetMinutes());
    }

    public static void setUtcOffsetMinutes(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_UTC_OFFSET, minutes).commit();
    }

    /** Which summer-time rule applies to that offset. */
    public static int summerTimePreset(Context context) {
        return prefs(context).getInt(KEY_DST_PRESET, SummerTime.PRESET_NONE);
    }

    public static void setSummerTimePreset(Context context, int preset) {
        prefs(context).edit().putInt(KEY_DST_PRESET, preset).commit();
    }

    /**
     * A summer-time rule the user stated, as nine numbers, or null.
     *
     * Stored as one string because a rule is meaningless in pieces: a half-written one that
     * survived a crash would be a clock an hour out for half the year.
     */
    public static SummerTime customSummerTime(Context context) {
        String stored = prefs(context).getString(KEY_DST_CUSTOM, "");
        String[] parts = stored.split(",");
        if (parts.length != 9) {
            return null;
        }
        try {
            int[] n = new int[9];
            for (int i = 0; i < 9; i++) {
                n[i] = Integer.parseInt(parts[i].trim());
            }
            return SummerTime.custom(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void setCustomSummerTime(Context context, int startMonth, int startWeekday,
            int startOrdinal, int startMinutes, int endMonth, int endWeekday, int endOrdinal,
            int endMinutes, int amount) {
        prefs(context).edit().putString(KEY_DST_CUSTOM, startMonth + "," + startWeekday + ","
                + startOrdinal + "," + startMinutes + "," + endMonth + "," + endWeekday + ","
                + endOrdinal + "," + endMinutes + "," + amount).commit();
    }

    /** The rule in force: a preset, the user's own, or none. */
    public static SummerTime summerTimeRule(Context context) {
        int preset = summerTimePreset(context);
        if (preset == SummerTime.PRESET_CUSTOM) {
            return customSummerTime(context);
        }
        return SummerTime.preset(preset);
    }

    /** What the phone's own zone is on right now, which is the sensible starting offset. */
    public static int phoneOffsetMinutes() {
        long now = System.currentTimeMillis();
        return java.util.TimeZone.getDefault().getOffset(now) / 60000;
    }

    /**
     * The offset the clock should read an instant at.
     *
     * Following the phone means asking the platform, which is right on a phone whose time zone
     * database is current. The manual path is arithmetic this project owns, and is the only answer
     * available on an Android 4.4 device in a country that has changed its rules since (RFC-0004).
     */
    public static int offsetMinutes(Context context, long epochMillis) {
        if (timeSource(context) == TIME_SOURCE_PHONE) {
            return java.util.TimeZone.getDefault().getOffset(epochMillis) / 60000;
        }
        return SummerTime.offsetAt(epochMillis, utcOffsetMinutes(context), summerTimeRule(context));
    }

    public static ClockOptions options(Context context) {
        return new ClockOptions(showSeconds(context), dateStyle(context),
                timePercent(context, KEY_TIME_PERCENT_WIDE) / 100f,
                timePercent(context, KEY_TIME_PERCENT_TALL) / 100f,
                calendarOn(context), quoteOn(context),
                calendarSystem(context), gregorianBadge(context), hijriOffset(context),
                calendarNameStyle(context, calendarSystem(context)),
                calendarWeekdayStyle(context, calendarSystem(context)),
                hour12(context), noonStyle(context), midnightStyle(context));
    }
}
