package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.FontLibrary;

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

    /** Where imported fonts live, inside the app's own storage: no permission needed to read it. */
    private static final String FONT_DIR = "fonts";

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
        return prefs(context).getBoolean(key + "_" + role, prefs(context).getBoolean(key, false));
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

    /** The display options the clock draws with. */
    public static ClockOptions options(Context context) {
        return new ClockOptions(showSeconds(context), dateStyle(context));
    }
}
