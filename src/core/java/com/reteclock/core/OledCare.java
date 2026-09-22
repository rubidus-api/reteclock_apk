package com.reteclock.core;

/**
 * OLED care: the one switch that makes the clock as easy on an OLED panel as it can be.
 *
 * An OLED pixel ages with the current it carries and the time it carries it, and a clock is the
 * worst case there is: the same bright shapes on the same pixels for months. What helps is less
 * light, on fewer pixels, for less time, moved about — so while this is on the clock draws on black,
 * in one dim warm colour, in the lightest face the system has, with no pictures behind or inside
 * the writing, the colon blinking and the drift always on. The layout is left exactly as it is.
 *
 * None of it is written into the user's settings. The clock works out what is in force, the same
 * way sleep mode does, so turning this off brings back every colour, font and picture as it was.
 *
 * The fade is the gentle part rather than the protective one: around each minute's edge the writing
 * dims and comes back, and that edge is when the digits change and the drift takes its step — so
 * the step happens while the text is faint and is not seen as a jump. A few seconds a minute of
 * dimmer text is a few per cent less wear, no more; the colours and the empty background are what
 * count.
 *
 * Pure Java: no android.* imports.
 */
public final class OledCare {

    /** Nothing lit behind the writing. */
    public static final int BACKGROUND_COLOR = 0xFF000000;

    /**
     * A dim warm grey: about a fifth of white's luminance, still above the 4.5:1 contrast ordinary
     * text is asked for, with red highest and blue lowest because the blue subpixel is the one that
     * wears first.
     */
    public static final int TEXT_COLOR = 0xFF877860;

    /** How long the writing takes to dim before the minute, and to come back after it. */
    public static final long FADE_MS = 1200L;

    /** How faint it gets on the minute itself: a quarter, never nothing. */
    public static final int FLOOR_ALPHA = 64;

    private static final long MINUTE_MS = 60000L;

    private OledCare() {
    }

    /** The writing's alpha at this wall-clock instant, 0–255. */
    public static int textAlpha(long nowMs) {
        long distance = distanceToMinute(nowMs);
        if (distance >= FADE_MS) {
            return 255;
        }
        return FLOOR_ALPHA + (int) ((255 - FLOOR_ALPHA) * distance / FADE_MS);
    }

    /** Whether the writing is dimmed at this instant, which is when the clock wants frames. */
    public static boolean fading(long nowMs) {
        return distanceToMinute(nowMs) < FADE_MS;
    }

    /** How long until the next fade begins; zero while one is under way. */
    public static long millisToNextFade(long nowMs) {
        if (fading(nowMs)) {
            return 0L;
        }
        return MINUTE_MS - FADE_MS - intoMinute(nowMs);
    }

    /** How far this instant is from the nearest minute's edge. */
    private static long distanceToMinute(long nowMs) {
        long into = intoMinute(nowMs);
        return Math.min(into, MINUTE_MS - into);
    }

    /** Milliseconds past the minute. Not Math.floorMod, which is API 24. */
    private static long intoMinute(long nowMs) {
        long into = nowMs % MINUTE_MS;
        return into < 0 ? into + MINUTE_MS : into;
    }
}
