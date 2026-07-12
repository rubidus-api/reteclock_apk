package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import com.reteclock.core.ClockOptions;

/** The stored settings, and the bridge to the pure-Java {@link ClockOptions}. */
public final class Settings {

    public static final String PREFS = "reteclock";

    public static final String KEY_START_WHEN_CHARGING = "start_when_charging";
    public static final String KEY_SHOW_SECONDS = "show_seconds";
    public static final String KEY_DATE_STYLE = "date_style";

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

    /** The display options the clock draws with. */
    public static ClockOptions options(Context context) {
        return new ClockOptions(showSeconds(context), dateStyle(context));
    }
}
