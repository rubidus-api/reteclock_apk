package com.reteclock.core;

/**
 * A way of typing a number, not a time zone database.
 *
 * RFC-0004 refuses to ship zone rules: they are hundreds of kilobytes and a promise to keep them
 * current, which is the promise the platform broke on the phones this app is built for. But picking
 * a number out of nothing is unfriendly, so here is a short list of places and the offset each one
 * is normally on. Choosing *Seoul* stores 540, and from then on the clock knows only 540.
 *
 * What this list deliberately does **not** carry:
 *
 * - **No history.** It is what each place is on now, not what it was on in 1955.
 * - **No summer-time rules.** Each entry only *suggests* a rule as a starting point, because that
 *   is the part that goes stale — and a stale suggestion is a wrong default the user can see and
 *   change, not a wrong clock they cannot.
 *
 * Every offset in use in the world appears here, including the quarter-hour ones, because a picker
 * that rounded to whole hours would fail several hundred million people.
 */
public final class Places {

    /** The places, in order of offset, west to east. */
    public static final String[] NAMES = {
        "Midway", "Pago Pago", "Honolulu", "Marquesas", "Anchorage", "Los Angeles", "Phoenix",
        "Denver", "Mexico City", "Chicago", "Bogota", "New York", "Caracas", "Santiago", "Halifax",
        "St John's", "Buenos Aires", "Sao Paulo", "Fernando de Noronha", "Azores", "Reykjavik",
        "Accra", "London", "Lagos", "Casablanca", "Paris", "Cairo", "Johannesburg", "Athens",
        "Nairobi", "Riyadh", "Moscow", "Istanbul", "Tehran", "Dubai", "Kabul", "Karachi", "Delhi",
        "Colombo", "Kathmandu", "Dhaka", "Yangon", "Bangkok", "Hanoi", "Jakarta", "Beijing",
        "Singapore", "Taipei", "Perth", "Eucla", "Seoul", "Tokyo", "Darwin", "Adelaide",
        "Brisbane", "Sydney", "Lord Howe Island", "Noumea", "Fiji", "Auckland", "Chatham Islands",
        "Nuku'alofa", "Kiritimati",
    };

    /** Minutes east of UTC, in the same order. */
    public static final int[] OFFSETS = {
        -660, -660, -600, -570, -540, -480, -420, -420, -360, -360, -300, -300, -240, -240, -240,
        -210, -180, -180, -120, -60, 0, 0, 0, 60, 60, 60, 120, 120, 120, 180, 180, 180, 180, 210,
        240, 270, 300, 330, 330, 345, 360, 390, 420, 420, 420, 480, 480, 480, 480, 525, 540, 540,
        570, 570, 600, 600, 630, 660, 720, 720, 765, 780, 840,
    };

    /** The summer-time rule each place is a good starting guess for. A guess, not a promise. */
    public static final int[] SUGGESTED_RULES = {
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NORTH_AMERICA, SummerTime.PRESET_NORTH_AMERICA,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NORTH_AMERICA, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NORTH_AMERICA, SummerTime.PRESET_NONE, SummerTime.PRESET_NORTH_AMERICA,
        SummerTime.PRESET_NONE, SummerTime.PRESET_SOUTHERN, SummerTime.PRESET_NORTH_AMERICA,
        SummerTime.PRESET_NORTH_AMERICA, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_EUROPE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_EUROPE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_EUROPE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_EUROPE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_SOUTHERN,
        SummerTime.PRESET_NONE, SummerTime.PRESET_SOUTHERN, SummerTime.PRESET_CUSTOM,
        SummerTime.PRESET_NONE, SummerTime.PRESET_NONE, SummerTime.PRESET_SOUTHERN,
        SummerTime.PRESET_SOUTHERN, SummerTime.PRESET_NONE, SummerTime.PRESET_NONE,
    };

    private Places() {
    }

    /** How many places there are. */
    public static int count() {
        return NAMES.length;
    }

    /** `Seoul  +9:00`, which is how the picker lists them. */
    public static String label(int index) {
        return NAMES[index] + "   " + offsetText(OFFSETS[index]);
    }

    /** `+9:00`, `-3:30`, `UTC`. */
    public static String offsetText(int minutes) {
        if (minutes == 0) {
            return "UTC";
        }
        int size = minutes < 0 ? -minutes : minutes;
        int hours = size / 60;
        int rest = size % 60;
        return (minutes < 0 ? "-" : "+") + hours + ":" + (rest < 10 ? "0" + rest : "" + rest);
    }

    /** The place nearest to an offset, so a picker can open where the user already is. */
    public static int nearest(int offsetMinutes) {
        int best = 0;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < OFFSETS.length; i++) {
            int gap = OFFSETS[i] - offsetMinutes;
            if (gap < 0) {
                gap = -gap;
            }
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }
}
