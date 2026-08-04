package com.reteclock.core;

/**
 * When the background slideshow moves on, and which frame of an animated slide is showing.
 *
 * Pure Java, so the timing rules are unit tested on a JVM. The Android layer owns the images; this
 * owns the clock arithmetic: an animated slide plays through exactly once, however long or short
 * that is, and a still slide holds for the time the user chose.
 *
 * Times are elapsed milliseconds from any monotonic clock; only differences matter.
 */
public final class Slideshow {

    /**
     * No slide is shorter than this, whatever the input says. A zero-length slide would advance on
     * every frame, which on a broken duration would turn the clock into a flip-book of decodes.
     */
    public static final long MIN_SLIDE_MS = 1000L;

    private final int count;
    private int index;
    private long startMs;
    private long durationMs;

    /** A show over {@code count} images; there must be at least one. */
    public Slideshow(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("a slideshow needs at least one image");
        }
        this.count = count;
    }

    /**
     * How long one slide is up: an animated image plays through once — its own duration, even when
     * that is longer than the still time — and a still image ({@code animationMs <= 0}) holds for
     * the chosen still time. Never below {@link #MIN_SLIDE_MS}.
     */
    public static long slideDurationMs(long animationMs, long stillMs) {
        long duration = animationMs > 0 ? animationMs : stillMs;
        return Math.max(duration, MIN_SLIDE_MS);
    }

    /**
     * The outgoing image's opacity during a cross-fade: opaque when the fade begins, gone when
     * {@code fadeMs} is up, linear between, clamped at both ends. Drawn over the incoming slide,
     * so as this thins the new image shows through.
     */
    public static int fadeOutAlpha(long sinceMs, long fadeMs) {
        if (fadeMs <= 0 || sinceMs >= fadeMs) {
            return 0;
        }
        if (sinceMs <= 0) {
            return 255;
        }
        return (int) (255L - 255L * sinceMs / fadeMs);
    }

    /** Starts showing one slide now. */
    public void begin(int index, long durationMs, long nowMs) {
        this.index = index;
        this.startMs = nowMs;
        this.durationMs = Math.max(durationMs, MIN_SLIDE_MS);
    }

    /** The slide on screen. */
    public int index() {
        return index;
    }

    /** Whether the current slide's time is up. */
    public boolean due(long nowMs) {
        return nowMs - startMs >= durationMs;
    }

    /** The slide after this one, wrapping — a single image advances to itself. */
    public int next() {
        return (index + 1) % count;
    }

    /**
     * How far into the current slide we are, for choosing an animated slide's frame. Runs from
     * zero and holds at the last instant rather than reading past the end of the animation; a
     * clock that runs backwards still gets the first frame, not a negative time.
     */
    public long frameMs(long nowMs) {
        long frame = nowMs - startMs;
        if (frame < 0) {
            return 0;
        }
        return Math.min(frame, durationMs - 1);
    }
}
