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
    /** An interval's message, to be spoken. */
    public static final int SPEAK = 5;

    /** How many seconds of counting lead into every ending. */
    public static final int COUNT_IN_SECONDS = 3;

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
        // Unclamped at the bottom so a window opening before the run can catch its first cue, and
        // clamped at the top so windows after the end have nothing left to find.
        long from = run.rawElapsedAt(fromMs);
        long to = Math.min(run.rawElapsedAt(toMs), run.totalMs());
        if (to <= from) {
            return out;
        }

        TimerPreset preset = run.preset();
        // The preset's own beginning, counted in the same way an interval's end is.
        add(out, from, to, new Cue(START, 0, 0L));
        if (!preset.intervals.isEmpty() && !preset.intervals.get(0).message.isEmpty()) {
            add(out, from, to, new Cue(SPEAK, 0, 0L));
        }

        long cursor = 0L;
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
            } else if (!preset.intervals.get(i + 1).message.isEmpty()) {
                // The next interval's message belongs to the moment this one ends.
                add(out, from, to, new Cue(SPEAK, i + 1, end));
            }
            cursor = end;
        }

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
        return out;
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
