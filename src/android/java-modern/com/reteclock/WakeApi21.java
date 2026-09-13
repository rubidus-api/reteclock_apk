package com.reteclock;

import android.app.AlarmManager;
import android.app.PendingIntent;

/**
 * The call an alarm clock is meant to use, on Android 5.0 and up (RFC-0012).
 *
 * <p>Compiled against a modern Android, in {@code src/android/java-modern}, and touched only behind a
 * {@code Build.VERSION.SDK_INT >= 21} check: see {@link NativeAnimation} for why a class is split out
 * rather than reached by reflection. One class per API level, so that no class names a type newer
 * than the check that guards it.
 *
 * <p>{@code setAlarmClock} is exempt from Doze, and the lock screen and status bar show the next
 * alarm from it, opening {@code show} when it is touched.
 */
final class WakeApi21 {

    private WakeApi21() {
    }

    static void setAlarmClock(AlarmManager alarms, long epochMillis, PendingIntent operation,
            PendingIntent show) {
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(epochMillis, show), operation);
    }
}
