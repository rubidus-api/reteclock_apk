package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

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

    /** The chosen font's file name, or "" for the system font. */
    public static String fontName(Context context) {
        return prefs(context).getString(KEY_FONT, "");
    }

    public static void setFontName(Context context, String name) {
        prefs(context).edit().putString(KEY_FONT, name == null ? "" : name).commit();
    }

    /**
     * The chosen font's file, or null to draw with the system font.
     *
     * Null also when the setting names a font that has since been deleted, so removing the font in
     * use leaves a working clock rather than a blank one.
     */
    public static File fontFile(Context context) {
        return fonts(context).file(fontName(context));
    }

    public static boolean bold(Context context) {
        return prefs(context).getBoolean(KEY_BOLD, false);
    }

    public static void setBold(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BOLD, enabled).commit();
    }

    public static boolean italic(Context context) {
        return prefs(context).getBoolean(KEY_ITALIC, false);
    }

    public static void setItalic(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ITALIC, enabled).commit();
    }

    public static boolean underline(Context context) {
        return prefs(context).getBoolean(KEY_UNDERLINE, false);
    }

    public static void setUnderline(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_UNDERLINE, enabled).commit();
    }

    /** The display options the clock draws with. */
    public static ClockOptions options(Context context) {
        return new ClockOptions(showSeconds(context), dateStyle(context));
    }
}
