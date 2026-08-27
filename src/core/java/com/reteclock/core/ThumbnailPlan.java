package com.reteclock.core;

/**
 * How large a preview picture is made, and how much of the original has to be read to make one.
 *
 * A picture is chosen by looking at it, and looking at it must not cost what showing it costs: a
 * settings screen listing twenty photographs cannot decode twenty photographs. So a thumbnail is
 * decoded straight to a small size — every second, fourth, eighth pixel — and kept.
 *
 * Pure arithmetic, no android.* imports, because the sampling rule is the part worth checking: a
 * step chosen one power of two too high gives a blurred preview of a sharp picture, and one too low
 * decodes a camera photograph in full on a phone from 2011. See RFC-0007.
 */
public final class ThumbnailPlan {

    /** The longest edge a thumbnail is made at. Larger than any row it is shown in, twice over. */
    public static final int MAX_EDGE = 320;

    /** How many times the original is stepped over on each axis: 1, 2, 4, 8 … */
    public final int sample;
    /** What the decoded picture will measure. */
    public final int width;
    public final int height;

    private ThumbnailPlan(int sample, int width, int height) {
        this.sample = sample;
        this.width = width;
        this.height = height;
    }

    /** The plan for a picture of this size, or null when it has no size to speak of. */
    public static ThumbnailPlan of(int sourceWidth, int sourceHeight) {
        return of(sourceWidth, sourceHeight, MAX_EDGE);
    }

    /**
     * The plan for a picture of this size at a given ceiling.
     *
     * The step is a power of two because that is the only step a decoder is obliged to honour: ask
     * for three and it may give two, and a preview whose size is a guess is a preview that cannot be
     * laid out. It is the largest step that still leaves the longer edge at or above the ceiling, so
     * the thumbnail is never smaller than asked for — the picture is scaled the rest of the way when
     * it is drawn, which costs nothing and keeps the edges honest.
     */
    public static ThumbnailPlan of(int sourceWidth, int sourceHeight, int maxEdge) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null;
        }
        int ceiling = maxEdge < 1 ? 1 : maxEdge;
        int sample = 1;
        while (sourceWidth / (sample * 2) >= ceiling && sourceHeight / (sample * 2) >= ceiling) {
            sample *= 2;
        }
        return new ThumbnailPlan(sample,
                Math.max(1, sourceWidth / sample), Math.max(1, sourceHeight / sample));
    }

    /** Whether a picture this size is already small enough to be its own thumbnail. */
    public boolean isWholePicture() {
        return sample == 1;
    }
}
