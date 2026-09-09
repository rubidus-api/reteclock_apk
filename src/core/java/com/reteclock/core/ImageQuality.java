package com.reteclock.core;

/**
 * How good an animated background is allowed to look, and what that costs (RFC-0011).
 *
 * The clock bakes animations down until they are cheap to play, because its floor is a phone from
 * 2012 that cannot decode a frame and scale it to the screen twenty-five times a second while also
 * answering a touch. On a modern phone that trade is made for nobody: the picture is worse and the
 * phone is idle.
 *
 * So it is a choice, in three steps, for the whole app — not per picture, because the question is
 * about the phone rather than about one photograph.
 *
 * <ul>
 *   <li>{@link #KIND} — what the app has always done, and the default. Frames capped at 960 pixels,
 *       stored as raw pixels, eight megabytes to an animation.</li>
 *   <li>{@link #BETTER} — frames as large as the screen, stored **encoded** (a small image file
 *       each, WebP where the platform can write one), with a budget large enough to keep them all.
 *       A tenth to a thirtieth of the size, paid for with a decode whenever the frame changes.</li>
 *   <li>{@link #ORIGINAL} — the file itself, played by the platform's own animated decoder. No
 *       baking, no ceiling, and animated WebP for the first time. Needs Android 9.</li>
 * </ul>
 *
 * **A step is not chosen, it is earned.** The screen that offers these runs a trial first and writes
 * the setting only if the trial survives — see {@link ImageTrial}. Nothing here knows about that;
 * this is only the arithmetic of what each step means.
 */
public final class ImageQuality {

    /** Today's behaviour, and the default. */
    public static final int KIND = 0;
    /** Screen-sized frames, encoded, with room for all of them. */
    public static final int BETTER = 1;
    /** The file itself, played by the platform. */
    public static final int ORIGINAL = 2;

    /** The lowest step, which every phone can do and which nothing has to earn. */
    public static final int FLOOR = KIND;

    /** `Bitmap.compress(WEBP, …)` arrived here; below it, {@link #BETTER} has no way to store. */
    public static final int WEBP_SINCE = 14;
    /** `ImageDecoder` and `AnimatedImageDrawable` arrived here. */
    public static final int NATIVE_ANIMATION_SINCE = 28;

    private ImageQuality() {
    }

    /** A step, made safe: anything unrecognised is the floor rather than a guess. */
    public static int of(int step) {
        return step == BETTER || step == ORIGINAL ? step : FLOOR;
    }

    /**
     * The highest step this <em>build</em> performs, whatever the phone is capable of.
     *
     * The top step means "the platform plays the file itself" — the {@code ImageDecoder} path, which
     * is written down (RFC-0011, step 4) and not written. A step whose label promises something no
     * copy of this app does is worse than one a phone is too old for: with the second there is at
     * least something to wait for on the phone's side. So it is not offered, a setting that names it
     * is brought down like one the platform cannot do, and it returns by raising this constant when
     * the path lands.
     */
    public static final int IMPLEMENTED_UP_TO = BETTER;

    /** Whether this build performs this step at all. */
    public static boolean exists(int step) {
        return of(step) <= IMPLEMENTED_UP_TO;
    }

    /** The highest step this Android can do at all. Above it, a step is not offered. */
    public static int highestOn(int sdk) {
        if (sdk >= NATIVE_ANIMATION_SINCE) {
            return ORIGINAL;
        }
        return sdk >= WEBP_SINCE ? BETTER : KIND;
    }

    /** Whether this Android can do this step. */
    public static boolean offeredOn(int step, int sdk) {
        return of(step) <= highestOn(sdk);
    }

    /**
     * The step this phone should be left at when one it cannot do is asked for.
     *
     * Two different refusals, deliberately answered in one place: the phone may be too old for a
     * step ({@link #highestOn}), and this build may not perform it at all ({@link #exists}). The
     * second is why a setting exported by another version cannot leave the clock pointed at
     * something nothing here does.
     */
    public static int allowedOn(int step, int sdk) {
        int wanted = of(step);
        if (!exists(wanted)) {
            wanted = IMPLEMENTED_UP_TO;
        }
        int highest = highestOn(sdk);
        return wanted <= highest ? wanted : highest;
    }

    /** One step down — what a start that did not survive falls back to (RFC-0011). */
    public static int demoted(int step) {
        int at = of(step);
        return at <= FLOOR ? FLOOR : at - 1;
    }

    /**
     * How large a baked frame's long edge may be.
     *
     * {@link #KIND} keeps the 960 the app has always used — past that a background gains nothing an
     * eye can see and costs a great deal of bus traffic on an old phone. The higher steps take the
     * screen's own size, which is the most that can ever be shown.
     */
    public static int frameEdge(int step, int screenEdge) {
        int edge = Math.max(1, screenEdge);
        return of(step) == KIND ? Math.min(edge, 960) : edge;
    }

    /**
     * How much disc one animation may take.
     *
     * The number rises with the step because what it buys rises with it: eight megabytes of raw
     * pixels is eight frames, and eight megabytes of encoded ones is a few hundred.
     */
    public static long budgetBytes(int step) {
        switch (of(step)) {
            case ORIGINAL:
                return 128L * 1024 * 1024;
            case BETTER:
                return 48L * 1024 * 1024;
            default:
                return 8L * 1024 * 1024;
        }
    }

    /** How often the animation is sampled while baking, in milliseconds. */
    public static int sampleMs(int step) {
        return of(step) == KIND ? 40 : 20;
    }

    /** Whether frames are stored as image files rather than as raw pixels. */
    public static boolean encodesFrames(int step) {
        return of(step) != KIND;
    }

    /** Whether the platform plays the original file and nothing is baked at all. */
    public static boolean playsOriginal(int step, int sdk) {
        return of(step) == ORIGINAL && sdk >= NATIVE_ANIMATION_SINCE;
    }

    /**
     * The mark a prepared file carries so that a pack baked at one step is never played at another.
     *
     * The name already carries the screen it was made for; without the step beside it, changing the
     * setting would leave every pack in place and play the old one — a bug with no symptom but a
     * picture that quietly refuses to improve.
     */
    public static String tag(int step) {
        switch (of(step)) {
            case ORIGINAL:
                return "orig";
            case BETTER:
                return "better";
            default:
                return "kind";
        }
    }
}
