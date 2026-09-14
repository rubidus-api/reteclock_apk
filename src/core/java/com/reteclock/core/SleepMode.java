package com.reteclock.core;

/**
 * When the clock is asleep, and what asleep means (issue #54, RFC-0014).
 *
 * <p>Asleep is a handful of things at once — a darker screen, no background, the timer put away —
 * and it comes about two ways: by itself, inside a window on the days that are ticked, or by the
 * sleep button. This class answers the one question the screen asks every second: is it asleep
 * now? The settings themselves are never changed by sleeping; what is in force is worked out, the
 * same way a slide decides which layout shows without touching the chosen one (RFC-0013). So waking
 * gives back exactly what was there — a timer the user had hidden stays hidden.
 *
 * <p>Time is the bells' local stamp, {@code jdn * 1440 + minuteOfDay} ({@link Bells#stampOf}), so a
 * window follows the clock's own offset and summer time just as a bell does.
 *
 * <p><b>A window belongs to the day it starts.</b> "Sunday 22:00 to 06:30" runs into Monday
 * morning; Monday 03:00 is asleep because Sunday is ticked, whatever Monday says.
 *
 * <p><b>A press lasts until the schedule's next edge.</b> Pressed awake at 02:00 inside a window,
 * the clock stays awake until the window ends at 06:30 and then follows the schedule again; pressed
 * asleep on an afternoon, it sleeps until the next window starts or ends. So a press never leaves
 * the clock stuck in a state the schedule would have undone, and never needs pressing twice to get
 * the schedule back. With no schedule, a press lasts until the next press.
 *
 * <p>Pure Java, so the rules are tested without a device.
 */
public final class SleepMode {

    /** No press is in force. */
    public static final int PRESS_NONE = -1;
    public static final int PRESS_AWAKE = 0;
    public static final int PRESS_ASLEEP = 1;

    /** A press with no edge to end it: no schedule, or none that ever opens. */
    public static final long FOREVER = Long.MAX_VALUE;

    /** Brightness left to the phone while asleep. */
    public static final int BRIGHTNESS_UNCHANGED = -1;
    /** The brightness choices, in per cent; {@link #BRIGHTNESS_UNCHANGED} first. */
    public static final int[] BRIGHTNESS_CHOICES = {BRIGHTNESS_UNCHANGED, 1, 5, 10, 25, 50};
    public static final int DEFAULT_BRIGHTNESS = 1;

    /** Whether the window opens by itself at all. */
    public final boolean scheduled;
    /** The days a window starts on, a bit per weekday, Sunday bit 0 as in {@link Bell}. */
    public final int days;
    public final int startMinute;
    public final int endMinute;

    public SleepMode(boolean scheduled, int days, int startMinute, int endMinute) {
        this.scheduled = scheduled;
        this.days = days & Bell.EVERY_DAY;
        this.startMinute = minuteOf(startMinute);
        this.endMinute = minuteOf(endMinute);
    }

    /** Off, every day, 22:00 to 07:00: what the page offers before anything is chosen. */
    public static final SleepMode DEFAULT = new SleepMode(false, Bell.EVERY_DAY, 22 * 60, 7 * 60);

    private static int minuteOf(int minute) {
        int m = minute % Bell.MINUTES_A_DAY;
        return m < 0 ? m + Bell.MINUTES_A_DAY : m;
    }

    /** How long a window lasts, in minutes; zero when start and end are the same minute. */
    public int windowMinutes() {
        int length = endMinute - startMinute;
        return length < 0 ? length + Bell.MINUTES_A_DAY : length;
    }

    /** Whether a window could ever open: switched on, a day ticked, and a length to it. */
    public boolean opens() {
        return scheduled && days != 0 && windowMinutes() > 0;
    }

    public boolean startsOn(int weekday) {
        return (days & (1 << weekday)) != 0;
    }

    /** Whether the schedule alone says asleep at this stamp. */
    public boolean inWindow(long stamp) {
        if (!opens()) {
            return false;
        }
        long day = dayOf(stamp);
        // Today's window, or yesterday's still running past midnight.
        for (long d = day - 1; d <= day; d++) {
            if (!startsOn(CivilTime.weekday((int) d))) {
                continue;
            }
            long start = d * Bell.MINUTES_A_DAY + startMinute;
            if (stamp >= start && stamp < start + windowMinutes()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first stamp after this one at which a window opens or closes, or {@link #FOREVER}.
     *
     * Eight days is far enough: a ticked day recurs within seven, and a window that opened the day
     * before closes within one.
     */
    public long nextEdge(long stamp) {
        if (!opens()) {
            return FOREVER;
        }
        long best = FOREVER;
        long day = dayOf(stamp);
        for (long d = day - 1; d <= day + 8; d++) {
            if (!startsOn(CivilTime.weekday((int) d))) {
                continue;
            }
            long start = d * Bell.MINUTES_A_DAY + startMinute;
            long end = start + windowMinutes();
            if (start > stamp && start < best) {
                best = start;
            }
            if (end > stamp && end < best) {
                best = end;
            }
        }
        return best;
    }

    /**
     * Whether the clock is asleep at this stamp, given the last press and the edge it lasts until.
     */
    public boolean asleep(long stamp, int press, long pressUntil) {
        if (press != PRESS_NONE && stamp < pressUntil) {
            return press == PRESS_ASLEEP;
        }
        return inWindow(stamp);
    }

    /**
     * The first stamp after this one at which {@link #asleep} can change its answer: the press
     * running out, or the schedule's next edge. {@link #FOREVER} when nothing will change it.
     */
    public long nextChange(long stamp, int press, long pressUntil) {
        long edge = nextEdge(stamp);
        if (press != PRESS_NONE && stamp < pressUntil) {
            return Math.min(pressUntil, edge);
        }
        return edge;
    }

    /** How long a press made at this stamp lasts: until the schedule's next edge. */
    public long pressUntil(long stamp) {
        return nextEdge(stamp);
    }

    /**
     * A brightness choice made safe: one of {@link #BRIGHTNESS_CHOICES}, or the default for
     * anything else a hand-edited package might carry.
     */
    public static int brightnessChoice(int percent) {
        for (int choice : BRIGHTNESS_CHOICES) {
            if (choice == percent) {
                return percent;
            }
        }
        return DEFAULT_BRIGHTNESS;
    }

    /** The window's {@code screenBrightness} for a choice: 0..1, or -1 for the phone's own. */
    public static float windowBrightness(int percent) {
        return percent == BRIGHTNESS_UNCHANGED ? ScreenDim.FOLLOW_SYSTEM : percent / 100f;
    }

    /** Floor division by a day; {@code Math.floorDiv} is API 24. */
    private static long dayOf(long stamp) {
        long q = stamp / Bell.MINUTES_A_DAY;
        return stamp % Bell.MINUTES_A_DAY != 0 && stamp < 0 ? q - 1 : q;
    }
}
