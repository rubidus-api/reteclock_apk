package com.reteclock.core;

/**
 * The sentence a tap says, written by the user with fields in braces: "{ampm} {hour}시 {min}분",
 * "It is {hour} {min} on {day}, {month} {date}".
 *
 * <p>The fixed styles of {@link SpokenTime} hand the engine a time as the clock writes it, and an
 * engine that reads it character by character says "zero one colon zero five" (issue #62). Here the
 * user says what is spoken, in their own language, and every number goes in bare — "1", "5", never
 * "01" or "05" — because a bare number is the one thing every engine reads as a number.
 *
 * <p>Names are said in full and in the voice's own language where the glue can supply them (Android's
 * locale data for the chosen voice); the user may replace any of them, per calendar, and switch
 * their own names off again without losing them.
 *
 * <p>A field this class does not know is said as written, braces and all, so a typo is heard rather
 * than silently dropped. Runs of spaces collapse to one, so a marker that a 24-hour clock does not
 * have leaves no gap.
 *
 * Pure Java: no android.*, no formatting classes.
 */
public final class SpokenTemplate {

    private SpokenTemplate() {
    }

    /** What an empty sentence means: the time as two bare numbers and the half of the day. */
    public static final String DEFAULT = "{hour} {min} {ampm}";

    /** The fields understood, in the order the settings page lists them. */
    public static final String[] FIELDS = {
        "hour", "min", "sec", "ampm", "year", "month", "date", "day",
    };

    /** Full English weekday names, for when the voice's language gives none. Sunday first. */
    private static final String[] ENGLISH_WEEKDAYS = {
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
    };

    /** One moment, already in the calendar the clock is showing. */
    public static final class Moment {
        public final int hour24;
        public final int minute;
        public final int second;
        public final int weekday;
        public final CalendarDate date;

        Moment(int hour24, int minute, int second, int weekday, CalendarDate date) {
            this.hour24 = hour24;
            this.minute = minute;
            this.second = second;
            this.weekday = weekday;
            this.date = date;
        }
    }

    /**
     * The moment at this local day and time, in this calendar. The Hijri offset moves the date the
     * same way it moves the clock's own (it is only applied to the tabular Islamic calendar there).
     */
    public static Moment moment(int jdn, int hour24, int minute, int second, int system,
            int hijriOffsetDays) {
        int shown = system == Calendars.ISLAMIC ? jdn + hijriOffsetDays : jdn;
        return new Moment(floorMod(hour24, 24), floorMod(minute, 60), floorMod(second, 60),
                CivilTime.weekday(jdn), Calendars.dateOf(system, shown));
    }

    /** The words the fields are said with: the clock's conventions and the names in force. */
    public static final class Words {
        final boolean hour12;
        final String am;
        final String pm;
        final String[] gregorianMonths;
        final String[] weekdays;
        final CustomNames own;
        final boolean ownOn;
        final int nameStyle;

        private Words(boolean hour12, String am, String pm, String[] gregorianMonths,
                String[] weekdays, CustomNames own, boolean ownOn, int nameStyle) {
            this.hour12 = hour12;
            this.am = am;
            this.pm = pm;
            this.gregorianMonths = gregorianMonths;
            this.weekdays = weekdays;
            this.own = own == null ? CustomNames.NONE : own;
            this.ownOn = ownOn;
            this.nameStyle = nameStyle;
        }

        /**
         * @param hour12          whether the clock in force reads in twelve hours
         * @param markers         the user's AM and PM words, which win where they set any
         * @param voiceAm         the voice language's word for the morning ("AM", "오전")
         * @param voicePm         and for the afternoon
         * @param gregorianMonths the voice language's twelve month names, or null
         * @param weekdays        the voice language's seven weekday names, Sunday first, or null
         * @param own             the user's own spoken names for the calendar in force
         * @param ownOn           whether those are said; off keeps them but says the built-in ones
         * @param nameStyle       the calendar's naming the clock uses, for calendars other than
         *                        the Gregorian one
         */
        public static Words of(boolean hour12, CustomMarkers markers, String voiceAm,
                String voicePm, String[] gregorianMonths, String[] weekdays, CustomNames own,
                boolean ownOn, int nameStyle) {
            CustomMarkers m = markers == null ? CustomMarkers.NONE : markers;
            String am = m.amEntry().length() > 0 ? m.amEntry() : orEmpty(voiceAm);
            String pm = m.pmEntry().length() > 0 ? m.pmEntry() : orEmpty(voicePm);
            return new Words(hour12, am, pm,
                    gregorianMonths != null && gregorianMonths.length >= 12 ? gregorianMonths
                            : null,
                    weekdays != null && weekdays.length >= 7 ? weekdays : null,
                    own, ownOn, nameStyle);
        }
    }

    /** The name a month is said by, with the user's own name first while it is switched on. */
    public static String monthName(CalendarDate date, Words words) {
        String built = date.system == Calendars.GREGORIAN && words.gregorianMonths != null
                && date.month >= 1 && date.month <= 12
                && words.gregorianMonths[date.month - 1] != null
                && words.gregorianMonths[date.month - 1].length() > 0
                ? words.gregorianMonths[date.month - 1]
                : Calendars.monthNameFull(date.system, date.year, date.month, words.nameStyle);
        return words.ownOn ? words.own.month(date.month, built) : built;
    }

    /** The name a weekday is said by, 0 for Sunday. */
    public static String weekdayName(int weekday, Words words) {
        int i = floorMod(weekday, 7);
        String built = words.weekdays != null && words.weekdays[i] != null
                && words.weekdays[i].length() > 0 ? words.weekdays[i] : ENGLISH_WEEKDAYS[i];
        return words.ownOn ? words.own.weekday(i, built) : built;
    }

    /** The built-in name alone, which is what an empty field on the settings page shows. */
    public static String builtMonthName(CalendarDate date, Words words) {
        return monthName(date, new Words(words.hour12, words.am, words.pm, words.gregorianMonths,
                words.weekdays, CustomNames.NONE, false, words.nameStyle));
    }

    /** And for a weekday. */
    public static String builtWeekdayName(int weekday, Words words) {
        return weekdayName(weekday, new Words(words.hour12, words.am, words.pm,
                words.gregorianMonths, words.weekdays, CustomNames.NONE, false, words.nameStyle));
    }

    /** The sentence with every known field filled in; blank means {@link #DEFAULT}. */
    public static String render(String template, Moment at, Words words) {
        String text = template == null || template.trim().length() == 0 ? DEFAULT : template;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                int close = text.indexOf('}', i + 1);
                if (close > i) {
                    String value = field(text.substring(i + 1, close), at, words);
                    if (value != null) {
                        out.append(value);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return tidy(out);
    }

    /** One field's value, or null for a name this class does not know. */
    private static String field(String name, Moment at, Words words) {
        String key = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.equals("hour")) {
            if (!words.hour12) {
                return String.valueOf(at.hour24);
            }
            int shown = at.hour24 % 12;
            return String.valueOf(shown == 0 ? 12 : shown);
        }
        if (key.equals("min")) {
            return String.valueOf(at.minute);
        }
        if (key.equals("sec")) {
            return String.valueOf(at.second);
        }
        if (key.equals("ampm")) {
            return words.hour12 ? (at.hour24 < 12 ? words.am : words.pm) : "";
        }
        if (key.equals("year")) {
            return String.valueOf(at.date.year);
        }
        if (key.equals("month")) {
            return monthName(at.date, words);
        }
        if (key.equals("date")) {
            return String.valueOf(at.date.day);
        }
        if (key.equals("day")) {
            return weekdayName(at.weekday, words);
        }
        return null;
    }

    /** Runs of spaces to one, none at either end, and none left before a closing mark. */
    private static String tidy(CharSequence raw) {
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                space = out.length() > 0;
                continue;
            }
            if (space && !closing(c)) {
                out.append(' ');
            }
            space = false;
            out.append(c);
        }
        return out.toString();
    }

    private static boolean closing(char c) {
        return c == '.' || c == ',' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int floorMod(int value, int modulus) {
        int r = value % modulus;
        return r < 0 ? r + modulus : r;
    }
}
