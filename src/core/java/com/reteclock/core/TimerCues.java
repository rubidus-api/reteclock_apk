package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What the timer should say, and exactly when.
 *
 * The caller asks "what falls between the last time I looked and now" and plays whatever comes
 * back. That is the whole design, and it is deliberate: a countdown towards the next beep loses its
 * place the moment a tick is late, and on the hardware this app is for a tick is late whenever
 * anything else happens. A window cannot lose a cue and cannot play one twice, however irregularly
 * it is asked.
 *
 * The times are the preset's own — measured from its start — so they do not move when the run is
 * paused, and a paused run is simply not asked.
 */
public final class TimerCues {

    /** The preset is beginning: counted in, like the end of an interval. */
    public static final int START = 0;
    /** The warning the interval asked for, so many seconds before its end. */
    public static final int PRE_ALARM = 1;
    /** One of the three low beeps in the last three seconds. */
    public static final int TICK = 2;
    /** The high beep landing exactly on an interval's end. */
    public static final int END = 3;
    /** The melody when the whole preset is done. */
    public static final int FINISH = 4;
    /**
     * An interval's beginning: its message spoken, its own sound played, or both.
     *
     * Named for what it did when it was the only thing it did. A sound was added beside the message
     * later, and the cue means the same as it always did — "this interval is starting, say so" —
     * so it kept its name rather than growing a second kind that falls on the same instant.
     */
    public static final int SPEAK = 5;

    /** How many seconds of counting lead into every ending. */
    public static final int COUNT_IN_SECONDS = 3;

    /**
     * How long the timer counts you in before it actually starts.
     *
     * Pressing play and having the clock already be running is no use to somebody timing something
     * they have to do with their hands. Three low beeps, one a second, and the high one lands on
     * the moment the preset truly begins — the same shape as the count into an ending, which is the
     * point: you learn one rhythm and it means the same thing in both places.
     */
    public static final int LEAD_IN_SECONDS = 3;

    /** The same, in milliseconds: what the start of a run is pushed into the future by. */
    public static final long LEAD_IN_MS = LEAD_IN_SECONDS * 1000L;

    /** One thing to play, and when it was due. */
    public static final class Cue {
        public final int kind;
        /** Which interval it belongs to; for {@link #FINISH} the last one. */
        public final int interval;
        /** When it falls, in milliseconds from the start of the preset. */
        public final long atMs;

        Cue(int kind, int interval, long atMs) {
            this.kind = kind;
            this.interval = interval;
            this.atMs = atMs;
        }
    }

    private TimerCues() {
    }

    /**
     * The cues due after {@code fromMs} and up to and including {@code toMs}, in the order they
     * should be played. Both are wall-clock times of the same kind the run is asked about.
     *
     * Half-open at the start and closed at the end, so consecutive windows tile the timeline
     * exactly once: a cue on a boundary belongs to the window that has just reached it.
     */
    public static List<Cue> between(TimerRun run, long fromMs, long toMs) {
        List<Cue> out = new ArrayList<Cue>();
        if (run == null || run.isPaused() || toMs <= fromMs) {
            return out;
        }
        TimerPreset preset = run.preset();
        long total = run.totalMs();
        // Unclamped at the bottom so a window opening before the run can catch its first cue. A
        // preset that runs once is clamped at its end; one that repeats is not, and its cues are
        // laid out again for every pass the window touches.
        long from = run.rawElapsedAt(fromMs);
        long to = run.rawElapsedAt(toMs);
        if (!preset.loops || total <= 0L) {
            to = Math.min(to, total);
        }
        if (to <= from) {
            return out;
        }

        long firstPass = preset.loops && total > 0L ? Math.max(0L, from / total) : 0L;
        long lastPass = preset.loops && total > 0L ? to / total : 0L;
        // A window that somehow spans a great many passes is walked only over its last few: the
        // cues before those are long gone, and laying out thousands of them helps nobody.
        firstPass = Math.max(firstPass, lastPass - MAX_PASSES_AT_ONCE);

        for (long pass = firstPass; pass <= lastPass; pass++) {
            layOut(out, preset, pass * total, from, to);
        }

        sort(out);
        return out;
    }

    /** How many passes of a repeating preset one window will lay out before giving up on the rest. */
    private static final int MAX_PASSES_AT_ONCE = 4;

    /** One pass of the preset, its cues offset to where that pass begins. */
    private static void layOut(List<Cue> out, TimerPreset preset, long offset, long from,
            long to) {
        // The count into the very beginning. Only the first pass gets one: when a preset repeats,
        // the count into the last interval's ending is already the count into the next pass, and
        // beeping twice for one moment would be worse than not beeping at all.
        if (offset == 0L) {
            for (int back = LEAD_IN_SECONDS; back >= 1; back--) {
                add(out, from, to, new Cue(TICK, 0, offset - back * 1000L));
            }
        }
        // The preset's own beginning, landing on zero the way an interval's end lands on its own.
        add(out, from, to, new Cue(START, 0, offset));
        if (!preset.intervals.isEmpty() && announces(preset.intervals.get(0))) {
            add(out, from, to, new Cue(SPEAK, 0, offset));
        }

        long cursor = offset;
        for (int i = 0; i < preset.intervals.size(); i++) {
            TimerInterval interval = preset.intervals.get(i);
            long end = cursor + interval.lengthMs;

            if (interval.preAlarmSeconds > 0) {
                add(out, from, to, new Cue(PRE_ALARM, i, end - interval.preAlarmSeconds * 1000L));
            }
            for (int back = COUNT_IN_SECONDS; back >= 1; back--) {
                long at = end - back * 1000L;
                if (at > cursor) {
                    add(out, from, to, new Cue(TICK, i, at));
                }
            }
            add(out, from, to, new Cue(END, i, end));

            boolean last = i == preset.intervals.size() - 1;
            if (last) {
                add(out, from, to, new Cue(FINISH, i, end));
            } else if (announces(preset.intervals.get(i + 1))) {
                // The next interval's beginning belongs to the moment this one ends.
                add(out, from, to, new Cue(SPEAK, i + 1, end));
            }
            cursor = end;
        }
    }

    /** Whether an interval has anything to say or play at its own beginning. */
    private static boolean announces(TimerInterval interval) {
        return !interval.message.isEmpty() || !interval.startSound.isEmpty();
    }

    /** In the order they should be played, and on the same instant the ending comes first. */
    private static void sort(List<Cue> out) {
        Collections.sort(out, new Comparator<Cue>() {
            @Override
            public int compare(Cue a, Cue b) {
                if (a.atMs != b.atMs) {
                    return a.atMs < b.atMs ? -1 : 1;
                }
                // On the same instant: the ending sounds before what follows it.
                return a.kind - b.kind;
            }
        });
    }

    /**
     * Keeps a cue when it falls inside the window: open at the start, closed at the end, so
     * consecutive windows tile the timeline and every cue belongs to exactly one of them.
     */
    private static void add(List<Cue> out, long from, long to, Cue cue) {
        if (cue.atMs > from && cue.atMs <= to) {
            out.add(cue);
        }
    }
}
