package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * When the system should be asked to wake the phone for a bell (RFC-0012).
 *
 * <p>Arithmetic only. A bell means a minute of the clock's own local time (RFC-0004), and the
 * system scheduler takes an instant, so the work here is turning one into the other under whatever
 * offset and summer-time rule the clock counts in — asked <em>at the instant being tried</em>, not at
 * now, so a bell on the far side of a summer-time change lands on the right side of it.
 *
 * <p>Two local minutes are not one instant each, and both are decided here rather than left to luck:
 * a minute the spring change skips rings at the first minute that exists, and a minute the autumn
 * change repeats rings at the first of the two. An alarm that silently does not happen on one Sunday
 * a year is the failure this feature exists to avoid.
 *
 * <p>The app holds exactly one wake-up with the system — the next one — so there is never a stale
 * one to find. {@link #nextAt} is that one; {@link #upcoming} is a short list kept where a phone that
 * has restarted and not yet been unlocked can still read it.
 */
public final class WakeSchedule {

    /**
     * How late a ring may be delivered and still be rung.
     *
     * A phone that was off at seven and is switched on at ten past still wakes its owner; one that
     * comes back at noon does not play a morning alarm into the afternoon. Past this it is recorded
     * as late and left silent.
     */
    public static final long LATE_LIMIT_MS = 30 * 60_000L;

    private static final long MS_PER_MINUTE = 60_000L;

    private WakeSchedule() {
    }

    /** The clock's offset from UTC at an instant, in minutes — the app's own time base. */
    public interface TimeBase {
        int offsetMinutesAt(long epochMillis);
    }

    /** One ring to come: which bell, and the instant. */
    public static final class Next {
        public final long epochMillis;
        public final Bell bell;

        public Next(long epochMillis, Bell bell) {
            this.epochMillis = epochMillis;
            this.bell = bell;
        }
    }

    /**
     * The instant a local minute ({@link Bells#stamp}) happens.
     *
     * The earliest instant that reads as that minute; when no instant does — the spring change
     * skipped it — the first instant that reads as a later one.
     */
    public static long epochOf(long stamp, TimeBase base) {
        long asIfUtc = (stamp - CivilTime.JDN_UNIX_EPOCH * 1440L) * MS_PER_MINUTE;
        // Every offset in force within a day and a quarter either side. Offsets reach fourteen hours
        // and change at most a couple of times a year, so this sees every one that could apply.
        List<Long> tried = new ArrayList<Long>();
        long exact = Long.MAX_VALUE;
        long below = Long.MIN_VALUE;
        long above = Long.MAX_VALUE;
        for (int k = -5; k <= 5; k++) {
            int offset = base.offsetMinutesAt(asIfUtc + k * 6L * 60 * MS_PER_MINUTE);
            long candidate = asIfUtc - offset * MS_PER_MINUTE;
            if (tried.contains(Long.valueOf(candidate))) {
                continue;
            }
            tried.add(Long.valueOf(candidate));
            long reads = localStamp(candidate, base);
            if (reads == stamp) {
                exact = Math.min(exact, candidate);
            } else if (reads < stamp) {
                below = Math.max(below, candidate);
            } else {
                above = Math.min(above, candidate);
            }
        }
        if (exact != Long.MAX_VALUE) {
            return exact;
        }
        if (below == Long.MIN_VALUE || above == Long.MAX_VALUE || below > above) {
            // Nothing sensible to search between; the plain answer at the offset in force.
            return asIfUtc - base.offsetMinutesAt(asIfUtc) * MS_PER_MINUTE;
        }
        // A gap: the first whole minute, between the two, whose local reading is past the stamp.
        long lo = below / MS_PER_MINUTE;
        long hi = above / MS_PER_MINUTE;
        while (hi - lo > 1) {
            long mid = lo + (hi - lo) / 2;
            if (localStamp(mid * MS_PER_MINUTE, base) > stamp) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi * MS_PER_MINUTE;
    }

    private static long localStamp(long epochMillis, TimeBase base) {
        return Bells.stampOf(epochMillis, base.offsetMinutesAt(epochMillis));
    }

    /**
     * The next ring strictly after {@code afterEpochMillis} of any live bell that wakes the phone,
     * or null when there is none.
     *
     * Two at one instant: the first in the list, which is the rule the clock's tick already follows.
     */
    public static Next nextAt(Bells bells, long afterEpochMillis, TimeBase base) {
        if (bells == null || bells.size() == 0) {
            return null;
        }
        List<Bell> list = bells.list();
        long afterStamp = localStamp(afterEpochMillis, base);
        long firstDay = floorDay(afterStamp) - 1;
        long bestAt = Long.MAX_VALUE;
        Bell best = null;
        // Eight days on: a bell that rings at all rings within a week, and the day either side
        // covers an instant whose local day is not the one its minute belongs to.
        for (long day = firstDay; day <= firstDay + 9; day++) {
            int weekday = CivilTime.weekday((int) day);
            for (int i = 0; i < list.size(); i++) {
                Bell bell = list.get(i);
                if (!bell.wake || !bell.isLive() || !bell.ringsOn(weekday)) {
                    continue;
                }
                long at = epochOf(Bells.stamp((int) day, bell.minuteOfDay), base);
                if (at > afterEpochMillis && at < bestAt) {
                    bestAt = at;
                    best = bell;
                }
            }
        }
        return best == null ? null : new Next(bestAt, best);
    }

    /** The next {@code count} rings in order, each strictly after the one before. */
    public static List<Next> upcoming(Bells bells, long afterEpochMillis, TimeBase base, int count) {
        List<Next> out = new ArrayList<Next>();
        long cursor = afterEpochMillis;
        while (out.size() < count) {
            Next next = nextAt(bells, cursor, base);
            if (next == null) {
                break;
            }
            out.add(next);
            cursor = next.epochMillis;
        }
        return out;
    }

    /**
     * Where to start looking when arming: as far back as a late ring is still worth ringing, but
     * never at or before the last ring that was already dealt with.
     *
     * A last ring far in the future means the clock was set back a long way; it is then forgotten
     * rather than holding every bell silent until the clock catches up with it.
     */
    public static long armFrom(long nowEpochMillis, long lastHandledEpochMillis) {
        long reach = nowEpochMillis - LATE_LIMIT_MS;
        if (lastHandledEpochMillis > nowEpochMillis + LATE_LIMIT_MS) {
            return reach;
        }
        return Math.max(reach, lastHandledEpochMillis);
    }

    /** Whether a ring due at one instant and delivered at another should still be rung. */
    public static boolean worthRinging(long dueEpochMillis, long deliveredEpochMillis) {
        return deliveredEpochMillis - dueEpochMillis <= LATE_LIMIT_MS;
    }

    // ---- the list a locked phone can read ---------------------------------------------------

    /** One line per ring: the instant, a bar, and the bell as it is stored. */
    public static String mirrorText(List<Next> rings) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rings.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(rings.get(i).epochMillis).append('|').append(Bells.lineOf(rings.get(i).bell));
        }
        return out.toString();
    }

    /** Reads {@link #mirrorText}; an unreadable line costs only itself. */
    public static List<Next> parseMirror(String text) {
        List<Next> out = new ArrayList<Next>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            int bar = lines[i].indexOf('|');
            if (bar <= 0) {
                continue;
            }
            try {
                long at = Long.parseLong(lines[i].substring(0, bar).trim());
                Bells one = Bells.parse(lines[i].substring(bar + 1));
                if (one.size() == 1) {
                    out.add(new Next(at, one.list().get(0)));
                }
            } catch (NumberFormatException e) {
                // skipped
            }
        }
        return out;
    }

    /** The first ring in the list strictly after an instant, or null. */
    public static Next firstAfter(List<Next> rings, long afterEpochMillis) {
        for (int i = 0; i < rings.size(); i++) {
            if (rings.get(i).epochMillis > afterEpochMillis) {
                return rings.get(i);
            }
        }
        return null;
    }

    private static long floorDay(long stamp) {
        long q = stamp / 1440L;
        return stamp % 1440L != 0 && stamp < 0 ? q - 1 : q;
    }
}
