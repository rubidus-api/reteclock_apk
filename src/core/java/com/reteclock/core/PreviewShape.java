package com.reteclock.core;

/**
 * The two little screens a background preview is shown on.
 *
 * The question a preview answers is not "what does this picture look like" — the person choosing it
 * has seen it — but "what will be left of it on my screen". That question has two answers on a phone
 * that turns, and the interesting one is usually the second: a tall picture on a wide screen is
 * either stretched out of shape or shown from the middle outwards, whichever fit was asked for, and
 * neither is obvious until it is drawn.
 *
 * So both are always drawn, side by side, at the phone's own proportions rather than at some
 * standard rectangle. See RFC-0007.
 */
public final class PreviewShape {

    /** The gap between the two little screens, as a share of the taller one's height. */
    private static final float GAP_SHARE = 0.08f;

    /** x, y, width, height of the upright screen and of the sideways one, in that order. */
    public final float[] portrait;
    public final float[] landscape;
    /** How much room the pair takes together. */
    public final float width;
    public final float height;

    private PreviewShape(float[] portrait, float[] landscape, float width, float height) {
        this.portrait = portrait;
        this.landscape = landscape;
        this.width = width;
        this.height = height;
    }

    /**
     * The pair, drawn to fit inside a box this tall, for a phone whose screen measures this.
     *
     * Both little screens are the same height — the height given — so the pair reads as one thing
     * rather than as two pictures of different sizes; their widths come from the phone's own
     * proportions, which is why the sideways one is the wider of the two.
     */
    public static PreviewShape of(float height, int screenWidth, int screenHeight) {
        float tall = Math.max(height, 1f);
        int shorter = Math.min(Math.max(screenWidth, 1), Math.max(screenHeight, 1));
        int longer = Math.max(Math.max(screenWidth, 1), Math.max(screenHeight, 1));
        float portraitWidth = tall * shorter / (float) longer;
        float landscapeWidth = tall * longer / (float) shorter;
        float gap = tall * GAP_SHARE;
        float[] up = {0f, 0f, portraitWidth, tall};
        float[] over = {portraitWidth + gap, 0f, landscapeWidth, tall};
        return new PreviewShape(up, over, portraitWidth + gap + landscapeWidth, tall);
    }

    /**
     * The same pair scaled to fit a box of a given width, so a row can hand over the room it has
     * rather than being told how much it needs.
     */
    public static PreviewShape inside(float boxWidth, float boxHeight, int screenWidth,
            int screenHeight) {
        PreviewShape wanted = of(boxHeight, screenWidth, screenHeight);
        if (wanted.width <= boxWidth || boxWidth <= 0f) {
            return wanted;
        }
        return of(boxHeight * boxWidth / wanted.width, screenWidth, screenHeight);
    }
}
