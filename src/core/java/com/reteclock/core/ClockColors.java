package com.reteclock.core;

/**
 * The text and background colours, and the one rule about them: they can never be the same,
 * because a clock in its own background colour is no clock at all.
 *
 * The settings screen refuses the choice outright; this is the second line of defence, for a
 * stored pair that somehow ended up equal — the text flips to whichever of black and white stays
 * visible on that background. Pure Java, unit tested.
 */
public final class ClockColors {

    public static final int DEFAULT_TEXT = 0xFFFFFFFF;
    public static final int DEFAULT_BACKGROUND = 0xFF000000;

    private ClockColors() {
    }

    /** A colour as it is drawn: fully opaque. A translucent clock is a broken setting. */
    public static int opaque(int color) {
        return color | 0xFF000000;
    }

    /** Whether two colours draw the same, alpha aside — because drawing forces it opaque. */
    public static boolean same(int a, int b) {
        return opaque(a) == opaque(b);
    }

    /**
     * The colour the text is actually drawn in: the chosen one, unless it equals the background —
     * then black or white, whichever the background's perceived brightness keeps visible.
     */
    public static int resolveText(int text, int background) {
        if (!same(text, background)) {
            return opaque(text);
        }
        return luma(background) >= 128 ? 0xFF000000 : 0xFFFFFFFF;
    }

    /** Perceived brightness, 0..255 — the eye weighs green far above blue. */
    private static int luma(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (299 * r + 587 * g + 114 * b) / 1000;
    }

    /**
     * The opposite of a colour: each channel turned inside out, opacity kept.
     *
     * What an outline is drawn in. Against white writing it is black and against black writing it
     * is white, so the outline is always the thing the text is not — which is the only way one
     * rule can make writing legible over a picture nobody has seen.
     */
    public static int opposite(int color) {
        return (color & 0xFF000000) | (~color & 0x00FFFFFF);
    }
}
