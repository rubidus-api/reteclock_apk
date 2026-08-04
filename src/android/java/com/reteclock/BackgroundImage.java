package com.reteclock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The decoded background image: either an animated GIF, played with {@link Movie}, or a still
 * picture held as a {@link Bitmap}.
 *
 * Movie has been in the framework since API 1 and is the only way to play a GIF without a support
 * library, which keeps the app's floor at API 9. Its one demand is a software canvas — it cannot
 * draw to a hardware-accelerated one — so the view switches itself to a software layer while an
 * animated background is up.
 *
 * A GIF with a single frame, and any GIF Movie fails to read, falls through to BitmapFactory,
 * which decodes the first frame; a still image is still a background.
 */
final class BackgroundImage {

    /** Anything larger is a suspicious thing to call a picture on an old phone. */
    static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

    /**
     * Still images are downsampled until they fit inside this many pixels on each axis. Big enough
     * for any screen this app meets, small enough that a camera photo cannot exhaust a 2011 heap.
     */
    private static final int MAX_STILL_EDGE = 2048;

    private final Movie movie;
    private final Bitmap bitmap;

    private BackgroundImage(Movie movie, Bitmap bitmap) {
        this.movie = movie;
        this.bitmap = bitmap;
    }

    /**
     * Decodes this file, or returns null for anything unreadable — the second line of defence,
     * like the fonts have: the settings screen refuses non-images on import, and a file that goes
     * bad afterwards means a black clock, not a crash.
     */
    static BackgroundImage load(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_IMAGE_BYTES) {
            return null;
        }
        byte[] bytes = readAll(file);
        if (bytes == null) {
            return null;
        }
        if (isGif(bytes)) {
            Movie movie = decodeMovie(bytes);
            // A one-frame GIF has no duration; Movie would play nothing, so draw it as a still.
            if (movie != null && movie.duration() > 0
                    && movie.width() > 0 && movie.height() > 0) {
                return new BackgroundImage(movie, null);
            }
        }
        Bitmap still = decodeStill(bytes);
        return still == null ? null : new BackgroundImage(null, still);
    }

    /** Whether the picture moves, which is what decides the frame rate and the layer type. */
    boolean animated() {
        return movie != null;
    }

    /** How long one play-through lasts, or 0 for a still — which is what Slideshow expects. */
    int durationMs() {
        return movie != null ? movie.duration() : 0;
    }

    /** The still picture, for wrapping in a shader; null when this is an animation. */
    Bitmap still() {
        return bitmap;
    }

    int width() {
        return movie != null ? movie.width() : bitmap.getWidth();
    }

    int height() {
        return movie != null ? movie.height() : bitmap.getHeight();
    }

    /**
     * Draws the image at the canvas origin at its own size; the caller has already translated and
     * scaled the canvas into place. An animated image shows the frame at {@code frameMs} — the
     * caller decides the timeline: a slideshow plays through once and holds the last frame, a
     * looping image passes the elapsed time modulo the duration.
     */
    void draw(Canvas canvas, long frameMs, Paint paint) {
        if (movie != null) {
            int duration = movie.duration();
            int at = (int) Math.min(Math.max(frameMs, 0L), duration - 1L);
            movie.setTime(at);
            movie.draw(canvas, 0f, 0f);
        } else {
            canvas.drawBitmap(bitmap, 0f, 0f, paint);
        }
    }

    /** GIF87a or GIF89a, by the only part of the name that matters. */
    static boolean isGif(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8';
    }

    private static Movie decodeMovie(byte[] bytes) {
        try {
            return Movie.decodeByteArray(bytes, 0, bytes.length);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The still decode, downsampled in powers of two until it fits {@link #MAX_STILL_EDGE}. The
     * bounds pass costs no memory and tells us how far to shrink before any pixels are allocated.
     */
    private static Bitmap decodeStill(byte[] bytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            int edge = Math.max(bounds.outWidth, bounds.outHeight);
            while (edge / options.inSampleSize > MAX_STILL_EDGE) {
                options.inSampleSize *= 2;
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (RuntimeException e) {
            return null;
        } catch (OutOfMemoryError e) {
            // A picture too big for this device's heap is unreadable here, same as a broken one.
            return null;
        }
    }

    private static byte[] readAll(File file) {
        try {
            long length = file.length();
            byte[] bytes = new byte[(int) length];
            InputStream in = new FileInputStream(file);
            try {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = in.read(bytes, offset, bytes.length - offset);
                    if (read < 0) {
                        return null;
                    }
                    offset += read;
                }
            } finally {
                in.close();
            }
            return bytes;
        } catch (IOException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
