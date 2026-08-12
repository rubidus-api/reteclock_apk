package com.reteclock.core;

/**
 * Where everything in the timer strip sits.
 *
 * The strip is one shape used twice: in landscape it runs up the left of the screen, in portrait
 * across the top. Everything is measured along a single axis — {@code along} — whose zero is where
 * the bar starts filling: the bottom of the screen in landscape, the left in portrait. The view
 * maps that to y counting upwards, or to x counting rightwards.
 *
 * **Both orientations read the same way**, which is what the owner asked for after seeing them
 * side by side: from the near end, the bar, then play, pause, stop and the hourglass. So there is
 * no orientation branch here at all — one set of numbers, mapped twice, which is also why they
 * cannot drift apart. All values are pixels within the strip.
 */
public final class TimerBar {

    /**
     * Play, pause, stop, hourglass — in the order they are laid out beyond the bar, play nearest
     * to it and the hourglass at the far end.
     */
    public static final int CONTROL_PLAY = 0;
    public static final int CONTROL_PAUSE = 1;
    public static final int CONTROL_STOP = 2;
    public static final int CONTROL_HOURGLASS = 3;
    private static final int CONTROLS = 4;

    /**
     * How much of the strip's breadth the bar occupies.
     *
     * Most of it, because the readouts are written *inside* the bar rather than beside it. Text in
     * a lane alongside was the first arrangement, and it was forever either touching the bar or
     * running off the end of the strip; inside, it cannot do either.
     */
    private static final float BAR_SHARE = 0.66f;
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
    /** How wide a digit is, as a fraction of the text size, in the font the clock draws with. */
    private static final float DIGIT_WIDTH = 0.55f;

    /** How much of the bar's thickness the text fills; the rest is the air around it. */
    private static final float TEXT_SHARE = 0.62f;

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

    /** How broad the strip is, across the bar. */
    public float breadth() {
        return breadth;
    }

    /**
     * The bar's band across the strip: near edge and far edge, with the band centred.
     *
     * "Near" is the side the moving readout rides on, "far" the side the fixed two are written on.
     * Everything is expressed here so that nothing has to guess an offset — the readouts used to be
     * placed by adding a fraction of the text size to the middle, which put them over the bar on
     * one screen and off the end of the strip on another.
     */
    public float barNear() {
        return (breadth - thickness()) / 2f;
    }

    public float barFar() {
        return (breadth + thickness()) / 2f;
    }

    /** The middle of the bar, across it — where the readouts are written. */
    public float barMiddle() {
        return breadth / 2f;
    }

    /** The middle of the bar along its length, where the elapsed time is written. */
    public float midAt() {
        return (barStart() + barEnd()) / 2f;
    }

    /**
     * How large the readouts are: they sit inside the bar, so its thickness is the ceiling — and
     * three of them have to fit along it side by side, which on a short bar is the tighter limit.
     */
    public float textSize(int characters) {
        float byThickness = thickness() * TEXT_SHARE;
        int chars = characters < 1 ? 1 : characters;
        // A digit is about 0.55 of the text size wide, and a tenth of the bar is left as air
        // between the readouts. Counting the characters actually being drawn rather than assuming
        // the longest possible ones is what keeps the text large on a short bar.
        float byLength = (barEnd() - barStart()) * 0.9f / (chars * DIGIT_WIDTH);
        return Math.min(byThickness, byLength);
    }

    /** The size for the widest case: three readouts written in full. */
    public float textSize() {
        return textSize(30);
    }

    /** The middle of one control, along the strip: play nearest the bar, the hourglass furthest. */
    public float controlCenter(int index) {
        int at = index < 0 ? 0 : index >= CONTROLS ? CONTROLS - 1 : index;
        return length - controlSize * (CONTROLS - at - 0.5f);
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

    /** Where the bar begins: the near end — the bottom in landscape, the left in portrait. */
    public float barStart() {
        return 0f;
    }

    /** Where the bar ends: short of the controls, beside which the remaining time is written. */
    public float barEnd() {
        return Math.max(length - controlSize * CONTROLS, 0f);
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
