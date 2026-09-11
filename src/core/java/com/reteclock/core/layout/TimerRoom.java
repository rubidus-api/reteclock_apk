package com.reteclock.core.layout;

import java.util.List;

/**
 * How much of the clock the timer's strip takes, and from whom (RFC-0005 D9, issue #52).
 *
 * <p>There are two kinds of arrangement on this screen and they answer this question differently.
 *
 * <p><b>The app's own arrangement is moved over.</b> The app placed those digits and can place them
 * somewhere else, so switching the timer on shrinks the room the clock draws in and the strip sits
 * in the gap. That is what D9 describes and it has not changed.
 *
 * <p><b>A layout somebody drew is never moved.</b> Every box in it was placed against the whole
 * screen in the editor, and a layout that carries its own pictures was drawn against that same
 * whole screen. Shrinking it on the clock slides the digits off the picture they were arranged on,
 * and switching the timer on is not a thing the editor showed happening — the two disagreed, and
 * issue #52 is a photograph of the disagreement. So the room stays the screen, and the strip goes
 * where the layout says: into the edge it reserved with a timer box, or, if it reserved none, over
 * the top.
 *
 * <p>Room reserved by a timer box is taken out <em>inside</em> the layout, by {@link BoxPlan}, in
 * the editor and on the clock alike. Taking it out here as well is what made the clock disagree
 * with the editor twice over for a layout that had done the right thing.
 *
 * <p>A drawn layout with nothing in it is the app's arrangement — the clock falls back to it — and
 * is treated as such here, or the digits the app places would run under the strip.
 *
 * <p>One case this cannot see: a drawn layout that composes to nothing at all, or throws, is
 * replaced by the app's arrangement inside the view, which needs glyph measurements this does not
 * have. Such a layout gets no room made for it. It is rare, it is the same layout that was already
 * failing, and unpicking it here would mean measuring text twice to answer a question about edges.
 */
public final class TimerRoom {

    /** What the clock's own text must keep clear of, in pixels, per side. */
    public final int insetLeft;
    public final int insetTop;
    public final int insetRight;
    public final int insetBottom;

    /** Where the strip goes — left, top, width, height — or null when there is no timer. */
    public final float[] strip;

    private TimerRoom(int left, int top, int right, int bottom, float[] strip) {
        this.insetLeft = left;
        this.insetTop = top;
        this.insetRight = right;
        this.insetBottom = bottom;
        this.strip = strip;
    }

    /**
     * @param drawn        whether the clock is drawing a layout somebody made, rather than the
     *                     app's own arrangement
     * @param boxes        that layout's boxes, or null when there is no drawn layout
     * @param fallbackEdge where the app puts the strip when the layout has not said
     * @param thickness    how thick the app's own strip is, in pixels
     * @param timerOn      whether the timer is showing at all
     */
    public static TimerRoom of(boolean drawn, List<LayoutBox> boxes, int screenW, int screenH,
            int fallbackEdge, int thickness, boolean timerOn) {
        if (!timerOn) {
            return new TimerRoom(0, 0, 0, 0, null);
        }
        if (drawn && boxes != null && !boxes.isEmpty()) {
            float[] own = BoxPlan.timerStripOn(boxes, screenW, screenH);
            return new TimerRoom(0, 0, 0, 0,
                    own != null ? own : band(fallbackEdge, thickness, screenW, screenH));
        }
        return new TimerRoom(
                fallbackEdge == Strips.LEFT ? thickness : 0,
                fallbackEdge == Strips.TOP ? thickness : 0,
                fallbackEdge == Strips.RIGHT ? thickness : 0,
                fallbackEdge == Strips.BOTTOM ? thickness : 0,
                band(fallbackEdge, thickness, screenW, screenH));
    }

    /** The whole of one edge, at this thickness. */
    private static float[] band(int edge, int thickness, int screenW, int screenH) {
        if (edge == Strips.LEFT) {
            return new float[] {0f, 0f, thickness, screenH};
        }
        if (edge == Strips.RIGHT) {
            return new float[] {screenW - thickness, 0f, thickness, screenH};
        }
        if (edge == Strips.BOTTOM) {
            return new float[] {0f, screenH - thickness, screenW, thickness};
        }
        return new float[] {0f, 0f, screenW, thickness};
    }
}
