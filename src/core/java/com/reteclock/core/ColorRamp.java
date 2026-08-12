package com.reteclock.core;

/**
 * The colour an interval wears as it runs: its own at the start, its second colour by the end, and
 * the way between them.
 *
 * Straight arithmetic on each channel. A perceptual blend would be more correct and less
 * predictable, and what the user chose is two colours they want to see, not two colours they want
 * interpolated cleverly.
 */
public final class ColorRamp {

    private ColorRamp() {
    }

    /** {@code from} at 0, {@code to} at 1, held at both ends, always opaque. */
    public static int blend(int from, int to, float progress) {
        float t = progress < 0f ? 0f : progress > 1f ? 1f : progress;
        int r = channel(from, 16, to, t);
        int g = channel(from, 8, to, t);
        int b = channel(from, 0, to, t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int shift, int to, float t) {
        int a = (from >>> shift) & 0xFF;
        int b = (to >>> shift) & 0xFF;
        return Math.round(a + (b - a) * t);
    }
}
