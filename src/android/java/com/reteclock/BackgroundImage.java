package com.reteclock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

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
 *
 * <p>On Android 9 and up, and only at the top quality step, there is a fourth kind: the platform
 * plays the file itself ({@link NativeAnimation}). Nothing is baked, the frame timing is the
 * platform's, and an animated WebP moves — which it cannot on any other path here. See RFC-0011.
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
    /** The baked file, when there is one; then neither of the two above is used. */
    private final PreparedImage prepared;
    /** The platform's own drawable, at the top step on API 28+; then none of the three is used. */
    private final Drawable live;
    /**
     * Where a live animation's frame is rendered before it goes on screen.
     *
     * Drawing a Movie straight onto the screen would force the whole view onto a software layer —
     * every pixel of it rasterised by the processor, every frame — and the frame would be scaled up
     * to the screen in software on the way. Into a small bitmap instead, and the hardware does the
     * enlarging when that bitmap is drawn. On the phones this app is for that is the difference
     * between an animation and a still.
     */
    private Bitmap frameBuffer;
    private Canvas frameCanvas;
    private float frameScale = 1f;

    private BackgroundImage(Movie movie, Bitmap bitmap, PreparedImage prepared) {
        this(movie, bitmap, prepared, null);
    }

    private BackgroundImage(Movie movie, Bitmap bitmap, PreparedImage prepared, Drawable live) {
        this.movie = movie;
        this.bitmap = bitmap;
        this.prepared = prepared;
        this.live = live;
    }

    /**
     * The picture for one slide: the baked file if it is there, and the file itself if it is not.
     *
     * Baking happens in the settings screen, so the clock never waits for it; an image imported a
     * moment ago, or one carrying transparency, simply takes the live path.
     */
    static BackgroundImage open(File source, File pack) {
        PreparedImage ready = PreparedImage.open(pack);
        if (ready != null) {
            return new BackgroundImage(null, null, ready);
        }
        return load(source);
    }

    /**
     * The picture for one slide, knowing which quality step is in force.
     *
     * At the top step there is nothing baked to open and nothing to bake: the file is handed to the
     * platform. Everything below it is the ordinary path, unchanged — which is what makes this safe
     * to add, since a phone that never raises the step never comes down this branch.
     *
     * The {@code SDK_INT} test is not a nicety. {@link NativeAnimation} is compiled against a modern
     * Android, and a class is verified when it is first loaded; below API 28 this must not be
     * touched at all, which is why the check is here and not inside it.
     */
    static BackgroundImage open(File source, File pack, int step) {
        if (com.reteclock.core.ImageQuality.playsOriginal(step, android.os.Build.VERSION.SDK_INT)) {
            Drawable drawable = NativeAnimation.open(source);
            if (drawable != null) {
                // Said out loud, for the same reason the stood-down animation is: from the outside
                // this path and the one below it look identical until the picture fails to move.
                android.util.Log.i("reteclock", "native picture: " + source.getName()
                        + (NativeAnimation.isAnimated(drawable) ? " (moving)" : " (still)"));
                return new BackgroundImage(null, null, null, drawable);
            }
            android.util.Log.i("reteclock", "native picture refused: " + source.getName());
            // The platform would not have it. Fall through rather than show nothing: the ordinary
            // path reads more formats badly than this one reads well.
        }
        return open(source, pack);
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
            // A GIF beyond what this device can redraw twenty-five times a second is a still too:
            // BitmapFactory below decodes its first frame, downsampled, and the clock keeps up.
            if (movie != null && movie.duration() > 0
                    && com.reteclock.core.ImageLimits.playable(
                            bytes.length, movie.width(), movie.height())) {
                return new BackgroundImage(movie, null, null);
            }
        }
        Bitmap still = decodeStill(bytes);
        return still == null ? null : new BackgroundImage(null, still, null);
    }

    /** Whether the picture moves, which is what decides the frame rate. */
    boolean animated() {
        if (live != null) {
            return NativeAnimation.isAnimated(live);
        }
        return prepared != null ? prepared.animated() : movie != null;
    }

    /** How long one play-through lasts, or 0 for a still — which is what Slideshow expects. */
    int durationMs() {
        if (prepared != null) {
            return prepared.durationMs();
        }
        return movie != null ? movie.duration() : 0;
    }

    /**
     * The picture for wrapping in a shader: the still itself, or the frame a prepared still holds.
     * Null when this is an animation, whose frames the caller asks for one at a time.
     */
    Bitmap still() {
        if (live != null) {
            // There is no bitmap to hand out: the platform holds the pixels. A picture played this
            // way is not offered as a text fill, which is the only thing that asks.
            return null;
        }
        if (prepared != null) {
            return prepared.animated() ? null : prepared.frame(0L);
        }
        return bitmap;
    }

    int width() {
        if (live != null) {
            return live.getIntrinsicWidth();
        }
        if (prepared != null) {
            return prepared.width();
        }
        return movie != null ? movie.width() : bitmap.getWidth();
    }

    int height() {
        if (live != null) {
            return live.getIntrinsicHeight();
        }
        if (prepared != null) {
            return prepared.height();
        }
        return movie != null ? movie.height() : bitmap.getHeight();
    }

    /** Lets go of whatever the picture was holding open; the slideshow calls this on the way out. */
    void release() {
        if (live != null) {
            NativeAnimation.stop(live);
        }
        if (prepared != null) {
            prepared.release();
        }
        frameBuffer = null;
        frameCanvas = null;
    }

    /**
     * Draws the image at the canvas origin at its own size; the caller has already translated and
     * scaled the canvas into place. An animated image shows the frame at {@code frameMs} — the
     * caller decides the timeline: a slideshow plays through once and holds the last frame, a
     * looping image passes the elapsed time modulo the duration.
     */
    void draw(Canvas canvas, long frameMs, Paint paint) {
        if (live != null) {
            // The platform keeps this one's clock: it advances by how long it has been since it was
            // last drawn, so frameMs is not passed on. Drawing it often enough is the caller's job,
            // and the caller already redraws an animated background on the pacer's schedule.
            live.setBounds(0, 0, live.getIntrinsicWidth(), live.getIntrinsicHeight());
            live.draw(canvas);
            return;
        }
        if (prepared != null) {
            Bitmap frame = prepared.frame(frameMs);
            if (frame != null) {
                canvas.drawBitmap(frame, 0f, 0f, paint);
            }
            return;
        }
        if (movie == null) {
            canvas.drawBitmap(bitmap, 0f, 0f, paint);
            return;
        }
        int duration = movie.duration();
        int at = (int) Math.min(Math.max(frameMs, 0L), duration - 1L);
        movie.setTime(at);
        if (frameBuffer == null) {
            // No room for the buffer: draw straight onto whatever canvas this is. On a hardware
            // canvas a Movie draws nothing, which is why the view keeps its software-layer
            // fallback for exactly this case.
            movie.draw(canvas, 0f, 0f);
            return;
        }
        frameBuffer.eraseColor(0xFF000000);
        movie.draw(frameCanvas, 0f, 0f, paint);
        canvas.save();
        canvas.scale(1f / frameScale, 1f / frameScale);
        canvas.drawBitmap(frameBuffer, 0f, 0f, paint);
        canvas.restore();
    }

    /**
     * Makes the offscreen buffer a live animation is rendered into — the cheap path — and says
     * whether it could be had. Called once, when the slide comes on screen, because the answer
     * decides whether the view needs a software layer, which is settled before any drawing.
     *
     * The buffer is at the movie's own size or smaller: a picture larger than the screen is
     * rendered smaller, since the screen cannot show the extra pixels and every one of them would
     * be paid for on every frame.
     */
    boolean prepareFrames(int viewWidth, int viewHeight) {
        // The platform's drawable needs no buffer of ours and draws to a hardware canvas happily,
        // which is most of the point of it.
        if (live != null) {
            return true;
        }
        if (prepared != null || movie == null) {
            return true;
        }
        if (frameBuffer != null) {
            return true;
        }
        int width = movie.width();
        int height = movie.height();
        frameScale = com.reteclock.core.ImageLimits.frameScale(width, height,
                Math.max(viewWidth, 1), Math.max(viewHeight, 1));
        int bufferWidth = Math.max(1, Math.round(width * frameScale));
        int bufferHeight = Math.max(1, Math.round(height * frameScale));
        try {
            frameBuffer = Bitmap.createBitmap(bufferWidth, bufferHeight, Bitmap.Config.RGB_565);
        } catch (OutOfMemoryError e) {
            frameBuffer = null;
            return false;
        }
        frameCanvas = new Canvas(frameBuffer);
        frameCanvas.scale(frameScale, frameScale);
        return true;
    }

    /**
     * Whether this picture must be drawn on a software canvas — only a live Movie that could not
     * get an offscreen buffer, which is the one case where the Movie draws onto the canvas itself.
     */
    boolean needsSoftwareCanvas() {
        return prepared == null && movie != null && frameBuffer == null;
    }

    /**
     * The bitmap holding this moment's frame, for a caller that wants to sample it rather than
     * have it drawn — the text fill, whose glyphs are a window onto the picture.
     *
     * The same bitmap object comes back every time and its contents change, so a shader made over
     * it once stays correct. Null when there is no frame to be had, and for a still, which has
     * {@link #still()} instead.
     */
    Bitmap frameBitmap(long frameMs) {
        if (prepared != null) {
            return prepared.frame(frameMs);
        }
        if (movie == null || frameBuffer == null) {
            return null;
        }
        int duration = movie.duration();
        int at = (int) Math.min(Math.max(frameMs, 0L), duration - 1L);
        movie.setTime(at);
        frameBuffer.eraseColor(0x00000000);
        movie.draw(frameCanvas, 0f, 0f, null);
        return frameBuffer;
    }

    /** How many frames a prepared picture holds; zero when it was not prepared. */
    int preparedFrames() {
        return prepared != null ? prepared.frameCount() : 0;
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
