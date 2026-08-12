package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The sayings shown under the clock, and which of them is shown when.
 *
 * One a day, the same one all day: a clock on a shelf that changed its saying every time you
 * glanced at it would be a thing that moves, and a clock ought not to be. Touching it asks for
 * another, at random, until the day turns over and the day's own saying comes back.
 *
 * The text is a resource file — one saying per line, the words and then who said them — and it is
 * read here rather than parsed in the view so that the awkward lines can be tested: an empty file,
 * a line with no author, a comment.
 */
public final class Quotes {

    /** One saying and who said it. */
    public static final class Saying {
        public final String words;
        public final String author;

        public Saying(String words, String author) {
            this.words = words == null ? "" : words;
            this.author = author == null ? "" : author;
        }

        /** As it is drawn: the saying, then a dash, then the name. */
        @Override
        public String toString() {
            return author.isEmpty() ? words : words + "  — " + author;
        }
    }

    private Quotes() {
    }

    /**
     * Reads the file: one saying per line as words, a tab, and the author.
     *
     * Lines beginning with a hash are the file's own provenance and are not sayings. A line without
     * a tab is kept as a saying nobody is credited for rather than dropped — better an anonymous
     * line than a hole in the collection.
     */
    public static List<Saying> parse(String text) {
        List<Saying> out = new ArrayList<Saying>();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                continue;
            }
            int tab = trimmed.indexOf('\t');
            if (tab < 0) {
                out.add(new Saying(trimmed, ""));
            } else {
                out.add(new Saying(trimmed.substring(0, tab).trim(),
                        trimmed.substring(tab + 1).trim()));
            }
        }
        return out;
    }

    /**
     * Which saying belongs to a given day.
     *
     * The day number — days since the epoch — is spread across the collection by a multiplier
     * rather than used directly, so consecutive days are not consecutive sayings and the book is
     * not read in order over a year. The same day always gives the same answer, on every device.
     */
    public static int forDay(long dayNumber, int count) {
        if (count <= 0) {
            return -1;
        }
        long spread = dayNumber * 2654435761L;
        int at = (int) (Math.abs(spread) % count);
        return at;
    }

    /**
     * Another saying, chosen from the rest.
     *
     * "Random" here has to mean "not the one already on the screen": a shuffle that can land on
     * what is showing looks broken to somebody who has just touched it to change it.
     */
    public static int another(int current, int count, double roll) {
        if (count <= 0) {
            return -1;
        }
        if (count == 1) {
            return 0;
        }
        double safeRoll = roll < 0 ? 0 : roll >= 1 ? 0.999999 : roll;
        int step = 1 + (int) (safeRoll * (count - 1));
        return (current + step) % count;
    }

    /**
     * Days since the epoch, in the device's own timezone offset.
     *
     * The division floors rather than truncating — written out because `Math.floorDiv` is Java 8's
     * and the oldest phones this runs on have no such method — so that the day before the epoch is
     * -1 and not 0, and the day does not last twice as long once in 1970.
     */
    public static long dayNumber(long epochMillis, int zoneOffsetMillis) {
        long local = epochMillis + zoneOffsetMillis;
        long day = local / 86_400_000L;
        return local < 0 && local % 86_400_000L != 0 ? day - 1 : day;
    }
}
