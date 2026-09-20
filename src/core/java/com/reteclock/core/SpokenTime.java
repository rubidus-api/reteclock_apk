package com.reteclock.core;

/**
 * The time of day as a sentence to be spoken aloud (issue #57).
 *
 * A tap on the clock face can have the phone say what time it is, which is the one reading a person
 * in the dark can take without their glasses. What is spoken is deliberately <em>not</em> what is
 * drawn: the face may be padded to "06" for a tidy pair of digits, may be showing no marker at all
 * because the layout is short of room, and may be blinking its colon — none of which a voice can
 * say. So this builds the reading from the hour and the minute themselves, and leaves the sentence
 * around it ("It's %s.") to the Android strings, where it can be translated.
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
}
