package com.reteclock.core;

/**
 * Where and how large the background image is drawn.
 *
 * Pure geometry, no android.* imports, so the modes are unit tested on a JVM. The Android layer
 * applies the result as a canvas translate followed by a scale, and draws the image at the origin.
 *
 * The image is always centred on any axis its size does not pin: a fit kept to the width sits in
 * the vertical middle, a contained image sits in the middle of both.
 */
public final class ImageFit {

    /** Fill the screen exactly, letting the aspect ratio go. */
    public static final int STRETCH = 0;
    /** Keep the aspect ratio and match the screen's width; the height falls where it falls. */
    public static final int FIT_WIDTH = 1;
    /** Keep the aspect ratio and match the screen's height; the width falls where it falls. */
    public static final int FIT_HEIGHT = 2;
    /** Keep the aspect ratio and show the whole image, leaving bare screen on one axis. */
    public static final int CONTAIN = 3;
    /** Keep the aspect ratio and cover the whole screen, cropping the image on one axis. */
    public static final int COVER = 4;
    /** No scaling at all: the image at its own size, in the middle. */
    public static final int CENTER = 5;

    /** One drawing of the image: translate by (dx, dy), scale by (scaleX, scaleY), draw at 0,0. */
    public static final class Placement {
        public final float scaleX;
        public final float scaleY;
        public final float dx;
        public final float dy;

        Placement(float scaleX, float scaleY, float dx, float dy) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private ImageFit() {
    }

    /**
     * The placement for one image on one screen, or null when either has no size — a view not yet
     * laid out, an image that failed to decode — in which case nothing should be drawn.
     *
     * An unrecognised mode places like {@link #COVER}: a stored setting from some future version
     * still produces a full screen rather than a crash or a blank.
     */
    public static Placement of(int viewW, int viewH, int imgW, int imgH, int mode) {
        if (viewW <= 0 || viewH <= 0 || imgW <= 0 || imgH <= 0) {
            return null;
        }
        float toWidth = viewW / (float) imgW;
        float toHeight = viewH / (float) imgH;

        float scaleX;
        float scaleY;
        switch (mode) {
            case STRETCH:
                scaleX = toWidth;
                scaleY = toHeight;
                break;
            case FIT_WIDTH:
                scaleX = toWidth;
                scaleY = toWidth;
                break;
            case FIT_HEIGHT:
                scaleX = toHeight;
                scaleY = toHeight;
                break;
            case CONTAIN:
                scaleX = Math.min(toWidth, toHeight);
                scaleY = scaleX;
                break;
            case CENTER:
                scaleX = 1f;
                scaleY = 1f;
                break;
            case COVER:
            default:
                scaleX = Math.max(toWidth, toHeight);
                scaleY = scaleX;
                break;
        }
        float dx = (viewW - imgW * scaleX) / 2f;
        float dy = (viewH - imgH * scaleY) / 2f;
        return new Placement(scaleX, scaleY, dx, dy);
    }
}
