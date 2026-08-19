package com.reteclock.core;

/**
 * The two colours a clock takes from the system's own light or dark theme (issue #33).
 *
 * Not a palette and not a guess at one: dark means white writing on black and light means black
 * writing on white, which is what the phone's own screens do and the whole of what was asked for.
 * The pair is always the strongest contrast there is, so the clock stays readable across a room in
 * either theme — which is the reason somebody stands an old phone up in the first place.
 *
 * <p>Pure Java, so the mapping is testable; which theme is in force is the Android layer's question.
 */
public final class ThemeColors {

    /** Dark theme: what the writing is drawn in. */
    public static final int NIGHT_TEXT = 0xFFFFFFFF;
    /** And what it is drawn on. */
    public static final int NIGHT_BACKGROUND = 0xFF000000;
    /** Light theme: dark writing on white — not pure black, which glares on a white field. */
    public static final int DAY_TEXT = 0xFF121212;
    public static final int DAY_BACKGROUND = 0xFFFFFFFF;

    private ThemeColors() {
    }

    public static int textFor(boolean night) {
        return night ? NIGHT_TEXT : DAY_TEXT;
    }

    public static int backgroundFor(boolean night) {
        return night ? NIGHT_BACKGROUND : DAY_BACKGROUND;
    }
}
