package com.reteclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.reteclock.core.Bell;
import com.reteclock.core.WakeLog;
import com.reteclock.core.WakeSchedule;

/**
 * Where the system reaches the app for bells that wake the phone (RFC-0012).
 *
 * <p>Shipped disabled; {@link WakeBells#reconcile} enables it only while *Alarms (experimental)* is
 * switched on, so with the switch off a restart does not start the app at all.
 *
 * <p>It does two things and nothing else: a wake-up that has arrived is handed to whoever rings it,
 * and every broadcast that can make the held wake-up wrong — a restart, the clock or zone being
 * changed, the app being updated — has it worked out again.
 */
public class WakeReceiver extends BroadcastReceiver {

    private static final String LOCKED_BOOT = "android.intent.action.LOCKED_BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        boolean unlocked = WakeBells.isUnlocked(context);
        if (WakeBells.ACTION_WAKE.equals(action)) {
            arrived(context, intent.getLongExtra(WakeBells.EXTRA_DUE, 0L), unlocked);
            return;
        }
        if (LOCKED_BOOT.equals(action) && !unlocked) {
            WakeBells.rearmLocked(context);
            return;
        }
        if (unlocked) {
            // BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, and a locked boot that
            // turned out to be unlocked already.
            WakeBells.rearm(context);
        }
    }

    private static void arrived(Context context, long due, boolean unlocked) {
        if (due <= 0) {
            return;
        }
        if (!unlocked) {
            WakeRingService.start(context, due, true);
            return;
        }
        if (!Settings.wakeOn(context)) {
            return;
        }
        WakeBells.Host showing = WakeBells.host();
        if (showing == null) {
            WakeRingService.start(context, due, false);
            return;
        }
        // The clock is on screen: it rings the bell itself, over its own face, with its own player.
        // Opening a window over it would pause it and stop whatever bell it is sounding.
        Bell bell = WakeBells.resolve(context, due);
        if (bell == null) {
            WakeBells.rearm(context);
            return;
        }
        WakeBells.handled(context, due, false);
        if (!WakeSchedule.worthRinging(due, System.currentTimeMillis())) {
            WakeBells.record(context, WakeLog.LATE, due, bell.label);
            WakeBells.rearm(context);
            return;
        }
        showing.takeWake(bell, due);
    }
}
