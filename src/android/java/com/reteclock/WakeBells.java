package com.reteclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.reteclock.core.Bell;
import com.reteclock.core.Bells;
import com.reteclock.core.WakeLog;
import com.reteclock.core.WakePutOff;
import com.reteclock.core.WakeSchedule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bells that wake the phone (RFC-0012): the switch, the one wake-up held with the system, and the
 * record of what each ring did.
 *
 * <p><b>Off means 0.40.1.</b> With *Alarms (experimental)* switched off — the default — the receiver
 * and the service are disabled components, nothing is scheduled, no notification channel exists and
 * every bell is an ordinary bell. {@link #reconcile} is what makes the components agree with the
 * switch, and it is asked whenever the app starts, the switch is pressed, or settings are imported.
 *
 * <p><b>One wake-up, the next one.</b> After a ring ends, a bell is edited, the clock's time base
 * changes, the phone restarts or its clock is set, {@link #rearm} works out the next ring again and
 * replaces the one held. The system never holds a queue of this app's alarms, so there is never a
 * stale one.
 *
 * <p><b>The existing bells come first.</b> This is an extension laid over the clock's own bells, and
 * it is the side that gives way: a clock on screen rings a wake bell through its own
 * {@link BellRinger} (see {@link Host}) rather than having a window opened over it, which would
 * pause the clock and stop its bells; and a wake ring that arrives while one of those bells is
 * sounding waits for it to finish, for up to a minute.
 */
final class WakeBells {

    static final String ACTION_WAKE = "com.reteclock.action.WAKE";
    static final String EXTRA_DUE = "due";
    static final String EXTRA_LOCKED = "locked";

    static final String CHANNEL = "wake_bells";

    /** How long a wake ring waits for a bell already sounding on the clock to finish. */
    static final long WAIT_FOR_BELL_MS = 60_000L;

    /** The rings kept where a restarted, still-locked phone can read them (F2). */
    private static final int MIRROR_COUNT = 16;
    private static final String LOCKED_PREFS = "reteclock_wake_locked";
    private static final String LOCKED_MIRROR = "mirror";
    private static final String LOCKED_LAST = "last";
    private static final String LOCKED_LOG = "log";

    private static final String LOG_FILE = "wake-log.txt";

    private static final int REQUEST_WAKE = 1;
    private static final int REQUEST_SHOW = 2;

    private WakeBells() {
    }

    // ---- the clock on screen ------------------------------------------------------------------

    /** A clock screen that is showing and will ring a wake bell itself. */
    interface Host {
        void takeWake(Bell bell, long dueEpochMillis);
    }

    private static Host host;

    /** Called by the clock as it comes to the front; there is at most one, and it is on screen. */
    static void setHost(Host showing) {
        host = showing;
    }

    /** Called as it goes; only the host that set itself can clear itself. */
    static void clearHost(Host going) {
        if (host == going) {
            host = null;
        }
    }

    static Host host() {
        return host;
    }

    // ---- the switch ---------------------------------------------------------------------------

    static boolean on(Context context) {
        return Settings.wakeOn(context);
    }

    /** The one switch: stores it, then makes everything else agree with it. */
    static void setSwitch(Context context, boolean on) {
        Settings.setWakeOn(context, on);
        reconcile(context);
    }

    /**
     * Makes the components, the channel and the held wake-up agree with the switch.
     *
     * Safe to ask at any time and as often as wanted: a component already in the right state is not
     * written again.
     */
    static void reconcile(Context context) {
        boolean on = Settings.wakeOn(context);
        setComponent(context, WakeReceiver.class, on);
        setComponent(context, WakeRingService.class, on);
        setComponent(context, WakeCardActivity.class, on);
        if (on) {
            if (Build.VERSION.SDK_INT >= 26) {
                WakeApi26.createChannel(context, CHANNEL,
                        context.getString(R.string.wake_channel_name),
                        context.getString(R.string.wake_channel_description));
            }
            rearm(context);
        } else {
            // Off touches nothing it does not have to: a phone that never switched this on keeps
            // the same settings file, no record with the alarm service, and no protected storage.
            cancel(context);
            if (Settings.wakePutOff(context) != null) {
                Settings.setWakePutOff(context, null);
            }
            if (Settings.wakeArmed(context) != 0L) {
                Settings.setWakeArmed(context, 0L);
            }
            clearLocked(context);
        }
    }

    private static void setComponent(Context context, Class<?> type, boolean enabled) {
        PackageManager packages = context.getPackageManager();
        ComponentName name = new ComponentName(context, type);
        int wanted = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        try {
            int now = packages.getComponentEnabledSetting(name);
            // The manifest ships these disabled, so "as the manifest says" already means off.
            boolean same = now == wanted || (!enabled
                    && now == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
            if (!same) {
                packages.setComponentEnabledSetting(name, wanted, PackageManager.DONT_KILL_APP);
            }
        } catch (RuntimeException e) {
            // A package manager that refuses is a phone on which the experiment cannot run.
        }
    }

    /** Asked by every setter whose value changes when a bell rings; nothing happens when off. */
    static void changed(Context context) {
        if (Settings.wakeOn(context)) {
            rearm(context);
        }
    }

    // ---- the wake-up held with the system -------------------------------------------------------

    static WakeSchedule.TimeBase timeBase(final Context context) {
        return new WakeSchedule.TimeBase() {
            @Override
            public int offsetMinutesAt(long epochMillis) {
                return Settings.offsetMinutes(context, epochMillis);
            }
        };
    }

    /** The next ring to come, bell or put-off, or null — what the page shows as *next wake*. */
    static WakeSchedule.Next next(Context context) {
        if (!Settings.wakeOn(context) || !Settings.bellsOn(context)) {
            return null;
        }
        long now = System.currentTimeMillis();
        long last = lastHandled(context);
        Bells bells = Settings.bells(context);
        WakeSchedule.Next next =
                WakeSchedule.nextAt(bells, WakeSchedule.armFrom(now, last), timeBase(context));
        WakePutOff off = Settings.wakePutOff(context);
        if (off != null && off.dueEpochMillis > last
                && (next == null || off.dueEpochMillis <= next.epochMillis)) {
            return new WakeSchedule.Next(off.dueEpochMillis, off.bell);
        }
        return next;
    }

    /** Works out the next ring and holds it with the system, replacing whatever was held. */
    static void rearm(Context context) {
        if (!Settings.wakeOn(context)) {
            cancel(context);
            return;
        }
        long now = System.currentTimeMillis();
        noteMissed(context, now);
        WakeSchedule.Next next = next(context);
        if (next == null) {
            cancel(context);
            Settings.setWakeArmed(context, 0L);
            writeLocked(context, new ArrayList<WakeSchedule.Next>());
            return;
        }
        arm(context, next.epochMillis, false);
        Settings.setWakeArmed(context, next.epochMillis);
        writeLocked(context, WakeSchedule.upcoming(Settings.bells(context),
                WakeSchedule.armFrom(now, lastHandled(context)), timeBase(context), MIRROR_COUNT));
    }

    /**
     * After a restart, before the first unlock: arms the next ring from the list kept in
     * device-protected storage, because nothing else can be read yet (F2).
     */
    static void rearmLocked(Context context) {
        if (Build.VERSION.SDK_INT < 24) {
            return;
        }
        SharedPreferences locked = WakeApi24.lockedPrefs(context, LOCKED_PREFS);
        List<WakeSchedule.Next> rings =
                WakeSchedule.parseMirror(locked.getString(LOCKED_MIRROR, ""));
        long from = WakeSchedule.armFrom(System.currentTimeMillis(),
                locked.getLong(LOCKED_LAST, 0L));
        WakeSchedule.Next next = WakeSchedule.firstAfter(rings, from);
        if (next == null) {
            cancel(context);
            return;
        }
        arm(context, next.epochMillis, true);
    }

    private static void arm(Context context, long epochMillis, boolean locked) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        Intent wake = new Intent(context, WakeReceiver.class);
        wake.setAction(ACTION_WAKE);
        wake.putExtra(EXTRA_DUE, epochMillis);
        wake.putExtra(EXTRA_LOCKED, locked);
        PendingIntent operation = PendingIntent.getBroadcast(context, REQUEST_WAKE, wake,
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 21) {
            PendingIntent show = PendingIntent.getActivity(context, REQUEST_SHOW,
                    new Intent(context, AlarmSettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            WakeApi21.setAlarmClock(alarms, epochMillis, operation, show);
        } else if (Build.VERSION.SDK_INT >= 19) {
            alarms.setExact(AlarmManager.RTC_WAKEUP, epochMillis, operation);
        } else {
            // Exact on these versions; set() only became inexact at API 19.
            alarms.set(AlarmManager.RTC_WAKEUP, epochMillis, operation);
        }
    }

    static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        Intent wake = new Intent(context, WakeReceiver.class);
        wake.setAction(ACTION_WAKE);
        // NO_CREATE: asking to cancel must not itself leave a pending intent with the system.
        PendingIntent held = PendingIntent.getBroadcast(context, REQUEST_WAKE, wake,
                PendingIntent.FLAG_NO_CREATE);
        if (held != null) {
            alarms.cancel(held);
            held.cancel();
        }
    }

    // ---- what a delivered wake-up is for --------------------------------------------------------

    /**
     * The bell a wake-up due at this instant is for, or null when there is none any more.
     *
     * A put-off is used up by being asked about, and one whose bell was edited or deleted in the
     * meantime is recorded as dropped rather than rung.
     */
    static Bell resolve(Context context, long due) {
        Bells bells = Settings.bells(context);
        WakePutOff off = Settings.wakePutOff(context);
        if (off != null && off.dueEpochMillis == due) {
            Settings.setWakePutOff(context, null);
            Bell still = off.stillIn(bells);
            if (still == null || !still.wake) {
                record(context, WakeLog.DROPPED, due, off.bell.label);
                return null;
            }
            return still;
        }
        if (!Settings.bellsOn(context)) {
            return null;
        }
        WakeSchedule.Next next = WakeSchedule.nextAt(bells, due - 1L, timeBase(context));
        return next != null && next.epochMillis == due ? next.bell : null;
    }

    /** The bell a locked phone's list says is due at this instant, with no put-off offered. */
    static Bell resolveLocked(Context context, long due) {
        if (Build.VERSION.SDK_INT < 24) {
            return null;
        }
        List<WakeSchedule.Next> rings = WakeSchedule.parseMirror(
                WakeApi24.lockedPrefs(context, LOCKED_PREFS).getString(LOCKED_MIRROR, ""));
        for (WakeSchedule.Next ring : rings) {
            if (ring.epochMillis == due) {
                // A put-off cannot be written down on a locked phone, so none is offered there.
                return ring.bell.withSnooze(0);
            }
        }
        return null;
    }

    /** Marks a ring as dealt with, so no later arming can deliver it twice. */
    static void handled(Context context, long due, boolean locked) {
        if (!locked) {
            Settings.setWakeLast(context, Math.max(Settings.wakeLast(context), due));
        }
        if (Build.VERSION.SDK_INT >= 24) {
            SharedPreferences store = WakeApi24.lockedPrefs(context, LOCKED_PREFS);
            store.edit().putLong(LOCKED_LAST, Math.max(store.getLong(LOCKED_LAST, 0L), due))
                    .commit();
        }
    }

    private static long lastHandled(Context context) {
        long last = Settings.wakeLast(context);
        if (Build.VERSION.SDK_INT >= 24) {
            last = Math.max(last,
                    WakeApi24.lockedPrefs(context, LOCKED_PREFS).getLong(LOCKED_LAST, 0L));
        }
        return last;
    }

    static boolean isUnlocked(Context context) {
        return Build.VERSION.SDK_INT < 24 || WakeApi24.isUnlocked(context);
    }

    private static void writeLocked(Context context, List<WakeSchedule.Next> rings) {
        if (Build.VERSION.SDK_INT < 24) {
            return;
        }
        WakeApi24.lockedPrefs(context, LOCKED_PREFS).edit()
                .putString(LOCKED_MIRROR, WakeSchedule.mirrorText(rings))
                .putLong(LOCKED_LAST, lastHandled(context))
                .commit();
    }

    private static void clearLocked(Context context) {
        if (Build.VERSION.SDK_INT < 24) {
            return;
        }
        SharedPreferences store = WakeApi24.lockedPrefs(context, LOCKED_PREFS);
        if (!store.getAll().isEmpty()) {
            store.edit().clear().commit();
        }
    }

    // ---- the record ---------------------------------------------------------------------------

    /** Adds a line to the record. On a locked phone it waits in protected storage until unlock. */
    static void record(Context context, String kind, long due, String label) {
        WakeLog.Entry entry = new WakeLog.Entry(kind, due, System.currentTimeMillis(), label);
        if (!isUnlocked(context)) {
            SharedPreferences store = WakeApi24.lockedPrefs(context, LOCKED_PREFS);
            store.edit().putString(LOCKED_LOG, store.getString(LOCKED_LOG, "") + entry.line() + "\n")
                    .commit();
            return;
        }
        String held = "";
        if (Build.VERSION.SDK_INT >= 24) {
            SharedPreferences store = WakeApi24.lockedPrefs(context, LOCKED_PREFS);
            held = store.getString(LOCKED_LOG, "");
            if (!held.isEmpty()) {
                store.edit().remove(LOCKED_LOG).commit();
            }
        }
        write(context, WakeLog.trimmed(read(context) + held + entry.line() + "\n"));
        android.util.Log.i("reteclock", "wake: " + entry.line());
    }

    static List<WakeLog.Entry> entries(Context context) {
        return WakeLog.parseAll(read(context));
    }

    /** Deletes the record. */
    static void clearRecord(Context context) {
        new File(context.getFilesDir(), LOG_FILE).delete();
    }

    /** A wake-up that was held, is past its time and was never heard from, is recorded as missed. */
    private static void noteMissed(Context context, long now) {
        long armed = Settings.wakeArmed(context);
        if (armed > 0 && WakeLog.isMissed(armed, now, entries(context))) {
            record(context, WakeLog.MISSED, armed, "");
            Settings.setWakeArmed(context, 0L);
        }
    }

    private static String read(Context context) {
        File file = new File(context.getFilesDir(), LOG_FILE);
        if (!file.isFile()) {
            return "";
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] bytes = new byte[(int) Math.min(file.length(), 1 << 20)];
            int at = 0;
            while (at < bytes.length) {
                int n = in.read(bytes, at, bytes.length - at);
                if (n < 0) {
                    break;
                }
                at += n;
            }
            return new String(bytes, 0, at, "UTF-8");
        } catch (IOException e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void write(Context context, String text) {
        OutputStream out = null;
        try {
            out = new FileOutputStream(new File(context.getFilesDir(), LOG_FILE));
            out.write(text.getBytes("UTF-8"));
        } catch (IOException e) {
            // A record that cannot be written is a record lost, never a ring lost.
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
