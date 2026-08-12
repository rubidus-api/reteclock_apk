package com.reteclock.core;

/**
 * Where everything in the timer strip sits.
 *
 * The strip is one shape used twice: in landscape it runs up the left of the screen with its
 * controls at the bottom; in portrait it runs across the top with its controls at the right.
 *
 * Rather than write that twice, everything is measured along a single axis — {@code along} — whose
 * zero is where the bar starts filling: the bottom of the screen in landscape, the left in
 * portrait. The view maps that to y counting upwards, or to x counting rightwards. The arithmetic
 * cannot then drift between the two orientations, because there is only one of it.
 *
 * The hourglass is the outermost control in both: lowest in landscape, rightmost in portrait, with
 * stop, pause and play following it inwards. All values are pixels within the strip.
 */
public final class TimerBar {

    /** Hourglass, stop, pause, play — in the order they are laid out from the near end. */
    public static final int CONTROL_HOURGLASS = 0;
    public static final int CONTROL_STOP = 1;
    public static final int CONTROL_PAUSE = 2;
    public static final int CONTROL_PLAY = 3;
    private static final int CONTROLS = 4;

    /** How much of the strip's breadth the bar itself occupies; the rest is where text sits. */
    private static final float BAR_SHARE = 0.34f;
    /**
     * How much of the strip's *length* the four controls may take between them.
     *
     * Sized only by the strip's breadth, they ate seven tenths of a short strip and left the bar a
     * stub with its two readouts printed on top of each other — which is exactly how it looked on
     * the emulator before this line existed.
     */
    private static final float CONTROLS_SHARE = 0.32f;
    /** Keeps the moving readout from hanging off either end. */
    private static final float RIDING_INSET = 0.06f;

    private final float length;
    private final float breadth;
    private final boolean horizontal;
    private final float controlSize;

    private TimerBar(float length, float breadth, boolean horizontal, float controlSize) {
        this.length = length;
        this.breadth = breadth;
        this.horizontal = horizontal;
        this.controlSize = controlSize;
    }

    /**
     * A strip of this size. {@code horizontal} is the portrait arrangement — across the top — and
     * false is the landscape one, up the left.
     */
    public static TimerBar of(int width, int height, boolean horizontal) {
        float length = horizontal ? width : height;
        float breadth = horizontal ? height : width;
        // Square controls, at most as large as the strip is broad — and between them never more
        // than a third of its length, because the bar is what the strip is for.
        float control = Math.min(breadth, length * CONTROLS_SHARE / CONTROLS);
        return new TimerBar(Math.max(length, 1f), Math.max(breadth, 1f), horizontal,
                Math.max(control, 1f));
    }

    /** Whether this is the portrait arrangement, laid out along x. */
    public boolean isHorizontal() {
        return horizontal;
    }

    public int controlCount() {
        return CONTROLS;
    }

    /** How thick the bar is drawn, across the strip. */
    public float thickness() {
        return breadth * BAR_SHARE;
    }

    /** The middle of one control, along the strip. */
    public float controlCenter(int index) {
        int at = index < 0 ? 0 : index >= CONTROLS ? CONTROLS - 1 : index;
        float center = controlSize * (at + 0.5f);
        // Landscape counts up from the bottom, where the controls are; portrait counts in from the
        // right, where they are instead.
        return horizontal ? length - center : center;
    }

    /** Which control a touch at this point along the strip is on, or -1 for none. */
    public int controlAt(float along) {
        for (int i = 0; i < CONTROLS; i++) {
            if (Math.abs(along - controlCenter(i)) <= controlSize / 2f) {
                return i;
            }
        }
        return -1;
    }

    /** Where the bar begins: the empty end, from which it fills. */
    public float barStart() {
        // Landscape: above the controls. Portrait: hard against the left edge.
        return horizontal ? 0f : controlSize * CONTROLS;
    }

    /** Where the bar ends: the full end, beside which the remaining time is written. */
    public float barEnd() {
        // Landscape: the top of the screen. Portrait: short of the controls on the right.
        float end = horizontal ? length - controlSize * CONTROLS : length;
        return Math.max(end, barStart());
    }

    /** How far along the bar a progress of 0..1 reaches. */
    public float fillAt(float progress) {
        float t = progress < 0f ? 0f : progress > 1f ? 1f : progress;
        return barStart() + (barEnd() - barStart()) * t;
    }

    /** Where the whole preset's length is written: at the empty end of the bar. */
    public float totalAt() {
        return barStart();
    }

    /** Where the time left is written: at the full end of the bar. */
    public float remainingAt() {
        return barEnd();
    }

    /**
     * Where the elapsed time rides: on the edge between filled and unfilled, kept far enough from
     * either end to stay on the bar.
     */
    public float elapsedAt(float progress) {
        float span = barEnd() - barStart();
        float inset = span * RIDING_INSET;
        float at = fillAt(progress);
        float low = barStart() + inset;
        float high = barEnd() - inset;
        if (low > high) {
            return fillAt(0.5f);
        }
        return at < low ? low : at > high ? high : at;
    }
}
