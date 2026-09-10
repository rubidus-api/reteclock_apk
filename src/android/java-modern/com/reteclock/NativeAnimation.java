package com.reteclock;

import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;

/**
 * The picture played by the platform itself, on Android 9 and up — RFC-0011's top step.
 *
 * <p><b>This class is compiled against a modern Android, and the rest of the app is not.</b> It
 * lives in {@code src/android/java-modern}, which {@code scripts/build.sh} compiles first, against
 * android-34; everything else is still compiled against android-19, so a call to an API the floor
 * does not have is still refused there by the compiler. That guard is the reason for the split, and
 * the split is why nothing here is reached by reflection: the floor code names these methods
 * directly, and their signatures are written in types Android has had since the first release, so a
 * compiler that has never heard of {@code ImageDecoder} can still read them.
 *
 * <p><b>The rule that makes it safe.</b> Nothing may touch this class below API 28. A class is
 * loaded when it is first used, and a class that is never loaded is never verified — so the
 * {@code Build.VERSION} check at the call site is not a nicety, it is what keeps a KitKat phone
 * from meeting a type its runtime cannot resolve. There is exactly one caller, and it checks.
 *
 * <p>What this buys: no baking at all, frame timing the platform gets right, hardware-accelerated
 * drawing, and animated WebP — which plays here for the first time, since {@link android.graphics
 * .Movie}, the floor's animation, only ever knew GIF.
 */
final class NativeAnimation {

    private NativeAnimation() {
    }

    /**
     * Opens a picture for the platform to play, or returns null for one it will not have.
     *
     * Null rather than an exception because that is what the caller already does with every other
     * unreadable picture: a background that goes bad is a black screen, not a crash.
     */
    static Drawable open(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file));
            if (drawable instanceof AnimatedImageDrawable) {
                AnimatedImageDrawable moving = (AnimatedImageDrawable) drawable;
                // Left to repeat for ever: a background is a background, and the slideshow decides
                // when this picture's turn is over.
                moving.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                moving.start();
            }
            return drawable;
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            // A picture too large for this phone is a picture this phone does not show. The clock
            // carries on, which is the whole of what matters here.
            return null;
        }
    }

    /** Whether this is one of the moving ones, which is what decides the frame rate. */
    static boolean isAnimated(Drawable drawable) {
        return drawable instanceof AnimatedImageDrawable;
    }

    /** Stops it, so a picture leaving the screen stops costing anything. */
    static void stop(Drawable drawable) {
        if (drawable instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) drawable).stop();
        }
    }
}
