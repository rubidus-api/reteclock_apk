package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * How much of the phone the timer log may have, and what goes when it has had it.
 *
 * Two numbers the user sets, in whole megabytes on one line: the **ceiling**, past which the log is
 * trimmed, and the **floor** it is trimmed down to. Whole megabytes because this is a decision
 * about how much of a phone to give away, and nobody makes that decision to a fraction.
 *
 * The default is 3 MB down to 2 MB. The ceiling may be 2 MB at the least and 100 MB at the most;
 * the floor may be 1 MB at the least and 99 MB at the most, and is always kept below the ceiling —
 * a floor at or above the ceiling would trim on every run and never leave room to fill.
 *
 * The open file is rotated at 100 KB, and rotated files are zipped: this is repetitive short text
 * and it packs down to a fraction, so a megabyte holds far more runs than it looks like it should.
 *
 * The arithmetic lives here, away from the files, so it can be asked without a disc. Whole files
 * are deleted rather than rows: a row lives inside a zip, and unpacking, rewriting and repacking to
 * reclaim a few dozen bytes is a great deal of work to postpone a decision that has to be made
 * anyway. See RFC-0006.
 */
public final class TimerLogSpace {

    /** The open file is closed and packed away once it passes this. */
    public static final long ROTATE_BYTES = 100L * 1024L;
    private static final long MB = 1024L * 1024L;

    public static final int DEFAULT_CEILING_MB = 3;
    public static final int DEFAULT_FLOOR_MB = 2;
    public static final int MIN_CEILING_MB = 2;
    public static final int MAX_CEILING_MB = 100;
    public static final int MIN_FLOOR_MB = 1;
    public static final int MAX_FLOOR_MB = 99;

    private TimerLogSpace() {
    }

    /** Whether a file of this size has earned being packed away. */
    public static boolean shouldRotate(long openFileBytes) {
        return openFileBytes >= ROTATE_BYTES;
    }

    /** A ceiling as it will actually be used: whole megabytes, between 2 and 100. */
    public static int ceilingMb(int wanted) {
        if (wanted < MIN_CEILING_MB) {
            return MIN_CEILING_MB;
        }
        return wanted > MAX_CEILING_MB ? MAX_CEILING_MB : wanted;
    }

    /**
     * A floor as it will actually be used: whole megabytes, between 1 and 99, and always at least
     * one megabyte below the ceiling it is measured against.
     *
     * The ceiling wins the argument. Somebody typing the two numbers types them one at a time, so
     * for a moment the floor is above the ceiling — and the answer to that has to be a number the
     * log can work with rather than a refusal in the middle of typing.
     */
    public static int floorMb(int wantedFloor, int wantedCeiling) {
        int ceiling = ceilingMb(wantedCeiling);
        int floor = wantedFloor < MIN_FLOOR_MB ? MIN_FLOOR_MB
                : wantedFloor > MAX_FLOOR_MB ? MAX_FLOOR_MB : wantedFloor;
        return floor >= ceiling ? ceiling - 1 : floor;
    }

    public static long bytesOf(int megabytes) {
        return (long) megabytes * MB;
    }

    /**
     * How many of the kept files, from the oldest, to delete.
     *
     * The list handed in is oldest first — the caller sorts it, because the caller is the one who
     * knows what the names mean. Trimming starts only once the ceiling is passed, and then goes all
     * the way to the floor: a trim that stopped at the ceiling would run again on the very next run,
     * and the log would spend its life balanced on its own limit, deleting something every few runs.
     *
     * The open file is not among these. It is what is being written to, and deleting it would throw
     * away the run that caused the trim.
     *
     * @param sizes      the kept files' sizes, oldest first
     * @param ceilingMb  the ceiling in whole megabytes, as the user set it
     * @param floorMb    the floor in whole megabytes, as the user set it
     */
    public static int deleteCount(List<Long> sizes, int ceilingMb, int floorMb) {
        long ceiling = bytesOf(ceilingMb(ceilingMb));
        long floor = bytesOf(floorMb(floorMb, ceilingMb));
        long total = 0L;
        for (int i = 0; i < sizes.size(); i++) {
            total += Math.max(0L, sizes.get(i));
        }
        if (total <= ceiling) {
            return 0;
        }
        int deleted = 0;
        for (int i = 0; i < sizes.size() && total > floor; i++) {
            total -= Math.max(0L, sizes.get(i));
            deleted++;
        }
        return deleted;
    }

    /** The same answer as the files themselves, for a caller holding names beside sizes. */
    public static <T> List<T> toDelete(List<T> oldestFirst, List<Long> sizes, int ceilingMb,
            int floorMb) {
        List<T> out = new ArrayList<T>();
        int count = deleteCount(sizes, ceilingMb, floorMb);
        for (int i = 0; i < count && i < oldestFirst.size(); i++) {
            out.add(oldestFirst.get(i));
        }
        return out;
    }
}
