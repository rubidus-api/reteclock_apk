package com.reteclock;

import android.content.Context;

import com.reteclock.core.Bell;
import com.reteclock.core.Bells;

/**
 * How a bell's time is written on a screen (issue #55, RFC-0015).
 *
 * A bell at a set time is "07:00". A bell that follows the sun is "Sunrise +15 min", and where a day
 * is meant — today, for a bell ringing now — the minute it works out to that day comes first:
 * "06:12 · Sunrise". Without a place set, it says so rather than showing a time it will never ring
 * at.
 */
final class BellTime {

    private BellTime() {
    }

    /** The bell as set: its time, or which sun event and how far from it. */
    static String rule(Context context, Bell bell) {
        if (!bell.followsSun()) {
            return clock(bell.minuteOfDay);
        }
        String event = named(context, bell.sun);
        if (bell.sunOffsetMinutes == 0) {
            return event;
        }
        return event + " " + (bell.sunOffsetMinutes > 0 ? "+" : "−")
                + Math.abs(bell.sunOffsetMinutes) + " " + context.getString(R.string.sun_bell_min);
    }

    /** The time it rings today, then the rule for a bell that follows the sun. */
    static String today(Context context, Bell bell) {
        if (!bell.followsSun()) {
            return clock(bell.minuteOfDay);
        }
        long now = System.currentTimeMillis();
        int jdn = (int) (Bells.stampOf(now, Settings.offsetMinutes(context, now)) / 1440L);
        int minute = bell.minuteOn(jdn, Settings.sunClock(context));
        String rule = rule(context, bell);
        if (minute < 0) {
            return rule + " · " + context.getString(Settings.sunClock(context).isSet()
                    ? R.string.sun_bell_no_event : R.string.sun_bell_no_place);
        }
        return clock(minute) + " · " + rule;
    }

    /** What each of the sun's events is called on screen (issue #56). */
    static int eventName(int event) {
        switch (event) {
            case Bell.AT_DAWN: return R.string.sun_bell_dawn;
            case Bell.AT_NOON: return R.string.sun_bell_noon;
            case Bell.AT_AFTERNOON_SHADOW: return R.string.sun_bell_afternoon;
            case Bell.AT_SUNSET: return R.string.sun_bell_sunset;
            case Bell.AT_DUSK: return R.string.sun_bell_dusk;
            case Bell.AT_NIGHT_MIDDLE: return R.string.sun_bell_night_middle;
            case com.reteclock.core.SunTimes.EVENING: return R.string.sun_bell_evening;
            default: return R.string.sun_bell_sunrise;
        }
    }

    /**
     * The event's name, and the name the chosen set gives it beside it when the user asked for
     * that (issue #56) — "Dusk · Isha".
     */
    static String named(Context context, int event) {
        String own = context.getString(eventName(event));
        if (!Settings.sunShowNames(context)) {
            return own;
        }
        String theirs = com.reteclock.core.SunNames.of(Settings.sunMethod(context), event);
        return theirs.isEmpty() ? own : own + " \u00B7 " + theirs;
    }

    static String clock(int minuteOfDay) {
        int h = minuteOfDay / 60;
        int m = minuteOfDay % 60;
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }
}
