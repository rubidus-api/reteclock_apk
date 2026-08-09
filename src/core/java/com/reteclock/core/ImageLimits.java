package com.reteclock.core;

/**
 * The ceilings above which a picture is shown rather than played.
 *
 * Playing a GIF costs on every frame: the file is held whole while it decodes, the frames live in
 * memory, and each one is scaled onto the whole screen in software. A still costs once — it is
 * downsampled at decode time and pre-rendered to screen size — so a picture too big to play is
 * still perfectly good to look at, and its first frame is what gets shown.
 *
 * Deliberately mean, because the floor of this app is a phone from 2012 whose whole Dalvik heap is
 * smaller than one careless GIF. {@link FrameBudget} catches what these ceilings let through.
 */
public final class ImageLimits {

    /** How large an animated file may be. Beyond this, the first frame is the picture. */
    public static final long MAX_ANIMATED_BYTES = 6L * 1024 * 1024;

    /**
     * How many pixels one animated frame may hold. A 1280×720 phone screen's worth, and a little
     * over, so a picture made for the screen plays and a camera-sized one does not.
     */
    public static final long MAX_ANIMATED_PIXELS = 1200L * 1000L;

    private ImageLimits() {
    }

    /**
     * How far to shrink an animation's frame before it is drawn into the offscreen buffer the
     * hardware then stretches onto the screen.
     *
     * A picture smaller than the screen is left alone: enlarging it in software, only for the
     * hardware to enlarge it again, would be work for nothing. A picture larger than the screen is
     * rendered smaller — the screen cannot show the extra pixels, and every one of them is paid for
     * on each frame. The factor covers the screen on both axes, so no fit mode is left short.
     */
    public static float frameScale(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            return 1f;
        }
        float scale = Math.max((float) dstWidth / srcWidth, (float) dstHeight / srcHeight);
        if (scale >= 1f) {
            return 1f;
        }
        // Never so small that a dimension rounds away to nothing.
        return Math.max(scale, 1f / Math.max(srcWidth, srcHeight));
    }

    /** Whether an image of this size may be played rather than shown as its first frame. */
    public static boolean playable(long bytes, int width, int height) {
        if (bytes <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        if (bytes > MAX_ANIMATED_BYTES) {
            return false;
        }
        return (long) width * (long) height <= MAX_ANIMATED_PIXELS;
    }
}
