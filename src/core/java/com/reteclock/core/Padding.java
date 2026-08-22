package com.reteclock.core;

/**
 * Which numbers on the clock are written with a leading zero — `08` or `8`.
 *
 * Five questions, one per field: the hour, the minute, the second, the month and the day. Every
 * clock ever built has taken a side on each of them and no side is right; a twelve-hour clock
 * conventionally writes `8:05` while a twenty-four hour one writes `08:05`, and `Jul 5` is as
 * ordinary as `07-05`.
 *
 * <p>That is why the hour and the day are kept <em>twice</em>. The convention belongs to the form
 * rather than to the field: a reader who wants `08:05` on a twelve-hour clock has said nothing
 * about what a twenty-four hour one should do, and somebody who pads `07-05` has said nothing about
 * `Jul 5`. Keeping one flag each would have made switching the clock to twelve hours silently
 * change how the hour is written, which is the kind of surprise this app tries not to have. The
 * settings screen shows one button per field and edits whichever of the pair is in force.
 *
 * <p>The defaults are what this clock has always drawn, so nothing moves for anybody who does not
 * ask: the twenty-four hour hour, the minute, the second, the month and the numeric day are padded;
 * the twelve-hour hour and the day beside a month name are not.
 *
 * <p>Pure Java: no android.*, no java.util.*.
 */
public final class Padding {

    private static final int BIT_HOUR24 = 1;
    private static final int BIT_HOUR12 = 2;
    private static final int BIT_MINUTE = 4;
    private static final int BIT_SECOND = 8;
    private static final int BIT_MONTH = 16;
    private static final int BIT_DAY_NUMERIC = 32;
    private static final int BIT_DAY_NAME = 64;

    /** Every bit this class knows about; anything else in a stored value is not ours. */
    private static final int ALL = BIT_HOUR24 | BIT_HOUR12 | BIT_MINUTE | BIT_SECOND | BIT_MONTH
            | BIT_DAY_NUMERIC | BIT_DAY_NAME;

    /** What the clock has always drawn. */
    public static final int DEFAULT_BITS =
            BIT_HOUR24 | BIT_MINUTE | BIT_SECOND | BIT_MONTH | BIT_DAY_NUMERIC;

    public static final Padding DEFAULTS = new Padding(DEFAULT_BITS);

    private final int bits;

    private Padding(int bits) {
        this.bits = bits & ALL;
    }

    /** Reads a stored value; bits this build does not know about are dropped. */
    public static Padding ofBits(int bits) {
        return bits == DEFAULT_BITS ? DEFAULTS : new Padding(bits);
    }

    /** What to store. */
    public int bits() {
        return bits;
    }

    private boolean has(int bit) {
        return (bits & bit) != 0;
    }

    private Padding with(int bit, boolean on) {
        int next = on ? bits | bit : bits & ~bit;
        return next == bits ? this : new Padding(next);
    }

    /** Whether the hour is padded on the clock in force — the two are separate conventions. */
    public boolean hour(boolean twelveHour) {
        return has(twelveHour ? BIT_HOUR12 : BIT_HOUR24);
    }

    public Padding withHour(boolean twelveHour, boolean padded) {
        return with(twelveHour ? BIT_HOUR12 : BIT_HOUR24, padded);
    }

    public boolean minute() {
        return has(BIT_MINUTE);
    }

    public Padding withMinute(boolean padded) {
        return with(BIT_MINUTE, padded);
    }

    public boolean second() {
        return has(BIT_SECOND);
    }

    public Padding withSecond(boolean padded) {
        return with(BIT_SECOND, padded);
    }

    /**
     * Whether the month is padded, which only a numeric date ever asks: where the month is written
     * as a name there is no number to pad.
     */
    public boolean month() {
        return has(BIT_MONTH);
    }

    public Padding withMonth(boolean padded) {
        return with(BIT_MONTH, padded);
    }

    /** Whether the day is padded in this date style — `07-05` and `Jul 5` are separate habits. */
    public boolean day(int dateStyle) {
        return has(dateStyle == ClockOptions.DATE_STYLE_NAME ? BIT_DAY_NAME : BIT_DAY_NUMERIC);
    }

    public Padding withDay(int dateStyle, boolean padded) {
        return with(dateStyle == ClockOptions.DATE_STYLE_NAME ? BIT_DAY_NAME : BIT_DAY_NUMERIC,
                padded);
    }

    /** Whether the month is a number at all in this style, which is what the screen asks. */
    public static boolean monthIsANumber(int dateStyle) {
        return dateStyle != ClockOptions.DATE_STYLE_NAME;
    }

    /**
     * The number as it is to be written.
     *
     * Only values below ten are ever affected, and only upward: this pads, it never truncates a
     * `10` to a `0`.
     */
    public static String write(int value, boolean padded) {
        if (padded && value >= 0 && value < 10) {
            return "0" + value;
        }
        return Integer.toString(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Padding && ((Padding) other).bits == bits;
    }

    @Override
    public int hashCode() {
        return bits;
    }
}
