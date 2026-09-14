package com.reteclock;

import android.content.Context;

import com.reteclock.core.SleepMode;

/**
 * Says, once a second, whether the clock has fallen asleep or woken (issue #54, RFC-0014).
 *
 * <p>The schedule and the last press are read when the screen appears and kept. In between, the
 * work is one comparison against the next stamp at which the answer can change — the schedule's
 * next edge or the press running out — so a clock with no schedule and no press pays nothing but
 * that comparison.
 */
final class SleepWatch {

    private final Context context;
    private boolean asleep;
    private long nextChangeStamp = SleepMode.FOREVER;

    SleepWatch(Context context) {
        this.context = context;
        reload();
    }

    /** Reads the schedule and the press again, and takes the state in force now as the start. */
    void reload() {
        long now = System.currentTimeMillis();
        long stamp = Settings.sleepStamp(context, now);
        SleepMode mode = Settings.sleepMode(context);
        int press = Settings.sleepPress(context);
        long until = Settings.sleepPressUntil(context);
        asleep = mode.asleep(stamp, press, until);
        nextChangeStamp = mode.nextChange(stamp, press, until);
    }

    /** Whether the clock is asleep, as of the last reload. */
    boolean asleep() {
        return asleep;
    }

    /** Whether asleep has changed since the last time this was asked. */
    boolean changed(long nowMs) {
        if (nextChangeStamp == SleepMode.FOREVER) {
            return false;
        }
        long stamp = Settings.sleepStamp(context, nowMs);
        // A clock set back past the last edge is asked again too, rather than waiting for a stamp
        // that is now a day away.
        if (stamp < nextChangeStamp && stamp > nextChangeStamp - 8L * 24 * 60) {
            return false;
        }
        boolean before = asleep;
        reload();
        return asleep != before;
    }
}
