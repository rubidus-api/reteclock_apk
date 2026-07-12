package com.reteclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Opens the clock when the charger is connected, unless the user turned that off.
 *
 * ACTION_POWER_CONNECTED is still delivered to manifest receivers on modern Android, but starting
 * an activity from the background is blocked from Android 10 on. On those devices the clock is
 * started from the launcher or used as a Daydream instead, so a failure here is not an error.
 */
public class PowerConnectionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
            return;
        }
        if (!Settings.startWhenCharging(context)) {
            return;
        }
        Intent clock = new Intent(context, ClockActivity.class);
        clock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        clock.putExtra(ClockActivity.EXTRA_DOCK, true);
        try {
            context.startActivity(clock);
        } catch (RuntimeException ignored) {
            // Background activity start is not allowed on this platform version.
        }
    }
}
