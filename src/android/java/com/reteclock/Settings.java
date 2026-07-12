package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

/** The app's only stored setting: whether plugging in the charger opens the clock. */
public final class Settings {

    public static final String PREFS = "reteclock";
    public static final String KEY_START_WHEN_CHARGING = "start_when_charging";

    private Settings() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean startWhenCharging(Context context) {
        return prefs(context).getBoolean(KEY_START_WHEN_CHARGING, true);
    }
}
