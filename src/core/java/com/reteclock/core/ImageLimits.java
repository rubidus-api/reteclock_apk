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
