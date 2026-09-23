package com.reteclock.core;

/**
 * The time of day as a sentence to be spoken aloud (issue #57).
 *
 * A tap on the clock face can have the phone say what time it is, which is the one reading a person
 * in the dark can take without their glasses. What is spoken is deliberately <em>not</em> what is
 * drawn: the face may be padded to "06" for a tidy pair of digits, may be showing no marker at all
 * because the layout is short of room, and may be blinking its colon — none of which a voice can
 * say. So this builds the reading from the hour and the minute themselves.
 *
 * <p>Nothing is put around it. The phone's speech engine reads in its own language, and an English
 * "It's" in front of the time is exactly what a Korean or Persian voice cannot say; "13:30" every
 * engine reads as a time in its own words.
 *
 * Pure Java: no android.*, no formatting classes, no floating point.
 */
public final class SpokenTime {

    private SpokenTime() {
    }

    /**
     * The clock reading a voice should say: "6:21 AM", "18:21", "12:00 PM".
     *
     * <p>The hour is never padded, because a leading zero is read out as a zero by some engines and
     * silently dropped by others; the minute always is, because "6:5" is not a time. On a
     * twelve-hour clock the user's own AM/PM words are used if they set any, since somebody who
     * writes the morning "오전" wants to hear it.
     *
     * @param hour24  the hour of the day, 0..23
     * @param minute  the minute, 0..59
     * @param hour12  whether the clock in force reads in twelve hours
     * @param markers the user's markers; {@link CustomMarkers#NONE} for the built-in AM and PM
     */
    public static String reading(int hour24, int minute, boolean hour12, CustomMarkers markers) {
        int hour = hour24 % 24;
        if (hour < 0) {
            hour += 24;
        }
        int minutes = minute % 60;
        if (minutes < 0) {
            minutes += 60;
        }
        StringBuilder out = new StringBuilder();
        if (hour12) {
            int shown = hour % 12;
            out.append(shown == 0 ? 12 : shown);
        } else {
            out.append(hour);
        }
        out.append(':');
        if (minutes < 10) {
            out.append('0');
        }
        out.append(minutes);
        if (hour12) {
            String mark = (markers == null ? CustomMarkers.NONE : markers)
                    .ordinary(hour < 12 ? "AM" : "PM");
            if (mark.length() > 0) {
                out.append(' ').append(mark);
            }
        }
        return out.toString();
    }

    /** The time read as the clock writes it: "1:05", "13:30". What a tap said until 0.49.0. */
    public static final int STYLE_READING = 0;

    /**
     * The time read as two numbers: "1 5", "13 30" — no colon, no leading zero anywhere.
     *
     * Issue #62: an engine that reads the string character by character says "zero one colon zero
     * five" for 01:05, which is not a time in any language. Given two bare numbers it says "one
     * five", which is what the reporter's Persian voice — and any other — makes sense of.
     */
    public static final int STYLE_PLAIN = 1;

    /** The style a stored number means; anything else is the reading. */
    public static int styleOf(int stored) {
        return stored == STYLE_PLAIN ? STYLE_PLAIN : STYLE_READING;
    }

    /**
     * What is handed to the speech engine when the clock is tapped: the time and nothing else
     * (T111). No sentence around it, because an engine reads in its own language; the markers are
     * the user's own words where they set any.
     */
    public static String utterance(int hour24, int minute, boolean hour12, CustomMarkers markers) {
        return utterance(hour24, minute, hour12, markers, STYLE_READING);
    }

    /** The same, in the style asked for (T115). */
    public static String utterance(int hour24, int minute, boolean hour12, CustomMarkers markers,
            int style) {
        String said = reading(hour24, minute, hour12, markers);
        if (styleOf(style) != STYLE_PLAIN) {
            return said;
        }
        // The reading is "H:MM" and then, at most, a space and the marker. Only the time itself is
        // rewritten; the marker is the user's own word and is left exactly as it is.
        int colon = said.indexOf(':');
        if (colon < 0) {
            return said;
        }
        int end = said.indexOf(' ', colon);
        String rest = end < 0 ? "" : said.substring(end);
        String minutes = end < 0 ? said.substring(colon + 1) : said.substring(colon + 1, end);
        while (minutes.length() > 1 && minutes.charAt(0) == '0') {
            minutes = minutes.substring(1);
        }
        return said.substring(0, colon) + " " + minutes + rest;
    }
}
