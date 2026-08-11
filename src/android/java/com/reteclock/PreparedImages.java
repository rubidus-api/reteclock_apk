package com.reteclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;

import com.reteclock.core.FramePack;
import com.reteclock.core.ImageLimits;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Bakes what the user imported into what the clock can afford to play.
 *
 * The clock's job is to draw a frame every so often; decoding a picture is the one thing in its way
 * that can take longer than the eye allows. So the decoding happens here instead — once, in the
 * settings screen, on a worker thread — and what it leaves behind is a {@link FramePack}: frames
 * already the size the screen wants, already in the screen's own pixel format. Playing it is a read
 * and a blit.
 *
 * This is also what lets a GIF too large to play live be played at all: it is not rejected, it is
 * made smaller. Its frames are sampled, identical ones folded together, and the whole thing scaled
 * until it fits a disk budget.
 *
 * Transparency is kept where it is real. Nearly every animated GIF in the world *declares* a
 * transparent colour — that is how encoders store "this pixel did not change since the last frame" —
 * but `Movie` composes the frames, so the picture that reaches the screen is usually solid. Judging
 * by the declaration alone excluded almost every real animation from being prepared, which is
 * exactly the bug this class was written to prevent. So the composed frames are looked at instead,
 * and only a picture with holes in what it actually draws is stored with an alpha channel.
 */
final class PreparedImages {

    /** Where the baked files live; one per image, named after the screen they were made for. */
    private static final String DIR = "prepared";

    /** How much disk one animation may take. Frames are raw, so this is also its whole cost. */
    private static final long BUDGET_BYTES = 8L * 1024 * 1024;

    /**
     * The largest a prepared frame is ever made, whatever the screen.
     *
     * A background lives behind big digits and an animation is read from disk frame by frame, so
     * past this there is nothing to gain and a great deal to pay: on a tablet, screen-sized frames
     * left room for two of them inside the budget, and the animation came out cut short.
     */
    private static final int MAX_FRAME_EDGE = 960;

    /** How often the animation is sampled while baking. Faster than any GIF worth the name. */
    private static final int STEP_MS = 40;

    /** A guard against a broken duration turning into an endless bake. */
    private static final int MAX_FRAMES = 512;

    /** How large the throwaway frame used to look for holes is, and how many moments it looks at. */
    private static final int PROBE_EDGE = 96;
    private static final int PROBE_SAMPLES = 6;

    private PreparedImages() {
    }

    static File dir(Context context) {
        return new File(context.getFilesDir(), DIR);
    }

    /**
     * The prepared file for one image on this screen. The screen's larger edge is in the name, so a
     * device that changes resolution — or a pack copied from elsewhere — is rebuilt rather than
     * played at the wrong size.
     */
    static File packFor(Context context, String imageName, int screenEdge) {
        return new File(dir(context), imageName + "." + screenEdge + ".pack");
    }

    /** The longer edge of this screen: one pack serves both orientations. */
    static int screenEdge(Context context) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.max(metrics.widthPixels, metrics.heightPixels);
    }

    /** How much disk one image's prepared file takes, or zero when it has none. */
    static long preparedBytes(Context context, String imageName, int screenEdge) {
        File pack = packFor(context, imageName, screenEdge);
        return pack.isFile() ? pack.length() : 0L;
    }

    /**
     * What all the prepared files take together.
     *
     * Frames are raw, so this is real disk and worth showing: it is the price paid for a clock that
     * never decodes while it draws, and the user should be able to see it.
     */
    static long preparedBytes(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) {
            return 0L;
        }
        long total = 0L;
        for (File file : files) {
            total += file.length();
        }
        return total;
    }

    /**
     * What is inside one prepared file — how many frames, at what size — read from its header
     * alone, without opening the picture. Null when there is no prepared file.
     *
     * The settings screen shows this, because it is the answer to "why does my animation look
     * wrong": a picture reduced to two frames, or to a quarter of its size, says so here.
     */
    static String describe(File pack) {
        if (pack == null || !pack.isFile()) {
            return null;
        }
        java.io.RandomAccessFile file = null;
        try {
            file = new java.io.RandomAccessFile(pack, "r");
            byte[] fixed = new byte[FramePack.fixedHeaderBytes()];
            file.readFully(fixed);
            int count = ((fixed[20] & 0xFF) << 24) | ((fixed[21] & 0xFF) << 16)
                    | ((fixed[22] & 0xFF) << 8) | (fixed[23] & 0xFF);
            if (count <= 0 || count > 4096) {
                return null;
            }
            byte[] header = new byte[FramePack.headerBytes(count)];
            System.arraycopy(fixed, 0, header, 0, fixed.length);
            file.readFully(header, fixed.length, header.length - fixed.length);
            FramePack parsed = FramePack.parse(header);
            if (parsed == null) {
                return null;
            }
            return parsed.frameCount() + "f " + parsed.width() + "\u00d7" + parsed.height();
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Makes sure every image in the pool has a current prepared file, and that nothing else is
     * left in the directory. Slow — it decodes everything that has changed — so it belongs on a
     * worker thread. Returns how many were baked.
     */
    static int prepareAll(Context context) {
        int edge = screenEdge(context);
        File dir = dir(context);
        dir.mkdirs();
        com.reteclock.core.FontLibrary pool = Settings.images(context);
        com.reteclock.core.ImageRoles.Lists roles = Settings.roles(context);
        Set<String> inUse = new HashSet<String>(roles.background);
        inUse.addAll(roles.text);
        Set<String> wanted = new HashSet<String>();
        int baked = 0;
        for (com.reteclock.core.FontLibrary.Entry entry : pool.list()) {
            // Only what a show actually uses. An image kept on hand is not worth the disk, and
            // baking it would make importing a batch slower for nothing.
            if (!inUse.contains(entry.name)) {
                continue;
            }
            File source = pool.file(entry.name);
            if (source == null) {
                continue;
            }
            File pack = packFor(context, entry.name, edge);
            wanted.add(pack.getName());
            if (pack.isFile() && pack.lastModified() >= source.lastModified()) {
                continue;
            }
            if (prepare(source, pack, edge)) {
                baked++;
            }
        }
        File[] existing = dir.listFiles();
        if (existing != null) {
            for (File file : existing) {
                if (!wanted.contains(file.getName())) {
                    // An image that was deleted or renamed, or a pack for another screen size.
                    file.delete();
                }
            }
        }
        return baked;
    }

    /**
     * Bakes one image. Writes to a temporary file and renames it into place, so a bake interrupted
     * half way through never leaves something the clock would try to play.
     */
    static boolean prepare(File source, File pack, int screenEdge) {
        File temp = new File(pack.getPath() + ".tmp");
        temp.delete();
        boolean made = false;
        try {
            byte[] bytes = readAll(source);
            if (bytes == null) {
                return false;
            }
            int edge = Math.min(screenEdge, MAX_FRAME_EDGE);
            made = BackgroundImage.isGif(bytes) ? bakeAnimation(bytes, temp, edge) : false;
            if (!made) {
                made = bakeStill(bytes, temp, edge);
            }
            if (!made) {
                return false;
            }
            pack.delete();
            return temp.renameTo(pack);
        } catch (RuntimeException e) {
            return false;
        } catch (OutOfMemoryError e) {
            return false;
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    /**
     * Bakes the animation, in at most two passes.
     *
     * The first walks it at the size the screen can show. If everything fits the budget, that is the
     * best quality available and the job is done. If it does not, the walk has at least counted how
     * many frames there really are — which no estimate can know beforehand, since identical frames
     * are folded together — and the second pass uses that count to pick the largest size that fits.
     * Guessing the count instead, from the duration, costs a great deal of resolution: a two-second
     * GIF sampled every 40 ms looks like fifty frames and is usually twenty.
     */
    private static boolean bakeAnimation(byte[] bytes, File temp, int screenEdge) {
        Movie movie = decodeMovie(bytes);
        if (movie == null || movie.duration() <= 0
                || movie.width() <= 0 || movie.height() <= 0) {
            return false;
        }
        float toScreen = ImageLimits.frameScale(movie.width(), movie.height(),
                screenEdge, screenEdge);
        // A first look decides the pixel format, since it decides how much a frame costs. It is
        // done small and thrown away: what it answers is only "does the composed picture have
        // holes", and a hole survives being made smaller.
        int format = composedFormat(movie, bytes);

        Walk walk = walk(movie, temp, toScreen, format);
        if (walk == null) {
            return false;
        }
        if (!walk.wholeThing) {
            float toBudget = FramePack.planScale(Math.round(movie.width() * toScreen),
                    Math.round(movie.height() * toScreen), walk.frames.size(), format,
                    BUDGET_BYTES);
            walk = walk(movie, temp, toScreen * toBudget, format);
            if (walk == null) {
                return false;
            }
        }
        if (walk.frames.isEmpty()) {
            return false;
        }
        // Whatever the sampling reached, the last frame holds to the end of the animation.
        walk.frames.set(walk.frames.size() - 1,
                Math.max(movie.duration(), walk.frames.get(walk.frames.size() - 1)));
        return finish(temp, walk.width, walk.height, format, walk.frames);
    }

    /**
     * Whether the animation, once its frames are composed, actually has holes.
     *
     * Almost every animated GIF declares a transparent colour: that is how an encoder says "this
     * pixel is unchanged since the last frame". `Movie` composes those frames, so what reaches the
     * screen is usually solid, and treating the declaration as transparency kept nearly every real
     * animation out of the prepared files altogether. This looks at the composed result instead —
     * at a small size, over a handful of moments spread through the animation.
     */
    private static int composedFormat(Movie movie, byte[] bytes) {
        // Two cheap ways of being told there might be holes: the platform's own answer, and the
        // file's declaration. Either is enough to look properly; neither is trusted on its own —
        // the declaration is nearly always there, and a platform that answered wrongly the other
        // way would fill the holes in without a word.
        boolean maybe = !movie.isOpaque() || com.reteclock.core.GifInfo.hasTransparency(bytes);
        return maybe && probeAlpha(movie) ? FramePack.WITH_ALPHA : FramePack.OPAQUE;
    }

    /** Renders a few composed frames small and looks for a pixel that is not fully opaque. */
    private static boolean probeAlpha(Movie movie) {
        int duration = Math.max(movie.duration(), 1);
        float scale = ImageLimits.frameScale(movie.width(), movie.height(), PROBE_EDGE, PROBE_EDGE);
        int width = Math.max(1, Math.round(movie.width() * scale));
        int height = Math.max(1, Math.round(movie.height() * scale));
        Bitmap frame = null;
        try {
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            canvas.scale((float) width / movie.width(), (float) height / movie.height());
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            int[] row = new int[width];
            for (int i = 0; i < PROBE_SAMPLES; i++) {
                int at = (int) ((long) duration * i / PROBE_SAMPLES);
                frame.eraseColor(0x00000000);
                movie.setTime(at);
                movie.draw(canvas, 0f, 0f, paint);
                for (int y = 0; y < height; y++) {
                    frame.getPixels(row, 0, width, 0, y, width, 1);
                    for (int x = 0; x < width; x++) {
                        if ((row[x] >>> 24) != 0xFF) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (RuntimeException e) {
            // Unsure: keeping the alpha channel costs resolution but never looks wrong.
            return true;
        } catch (OutOfMemoryError e) {
            return true;
        } finally {
            if (frame != null) {
                frame.recycle();
            }
        }
    }

    /** What one pass over an animation produced. */
    private static final class Walk {
        final int width;
        final int height;
        final java.util.ArrayList<Integer> frames;
        /** Whether the whole animation fitted, or the budget cut it short. */
        final boolean wholeThing;

        Walk(int width, int height, java.util.ArrayList<Integer> frames, boolean wholeThing) {
            this.width = width;
            this.height = height;
            this.frames = frames;
            this.wholeThing = wholeThing;
        }
    }

    /**
     * One pass: renders the animation at this scale, keeping the frames that differ from the one
     * before them and folding the repeats into the previous frame's time. A GIF that holds a pose
     * for half a second becomes one frame lasting half a second — smaller on disk, and one read
     * rather than twelve when it plays.
     */
    private static Walk walk(Movie movie, File temp, float scale, int format) {
        int duration = movie.duration();
        int width = Math.max(1, Math.round(movie.width() * scale));
        int height = Math.max(1, Math.round(movie.height() * scale));
        long frameBytes = (long) width * height * FramePack.bytesPerPixel(format);
        int room = (int) Math.max(1, Math.min(MAX_FRAMES, BUDGET_BYTES / frameBytes));
        boolean alpha = format == FramePack.WITH_ALPHA;

        Bitmap frame = null;
        OutputStream data = null;
        java.util.ArrayList<Integer> ends = new java.util.ArrayList<Integer>();
        boolean wholeThing = true;
        try {
            frame = Bitmap.createBitmap(width, height,
                    alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(frame);
            canvas.scale((float) width / movie.width(), (float) height / movie.height());
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            ByteBuffer buffer = ByteBuffer.allocate((int) frameBytes);
            byte[] previous = null;
            data = new BufferedOutputStream(new FileOutputStream(temp), 32 * 1024);

            for (int at = 0; at < duration; at += STEP_MS) {
                // An opaque pack has nothing behind it, so black is as good a floor as any; one
                // that keeps its holes must start from nothing at all.
                frame.eraseColor(alpha ? 0x00000000 : 0xFF000000);
                movie.setTime(at);
                movie.draw(canvas, 0f, 0f, paint);
                buffer.rewind();
                frame.copyPixelsToBuffer(buffer);
                byte[] pixels = buffer.array();
                int end = Math.min(duration, at + STEP_MS);
                if (previous != null && java.util.Arrays.equals(previous, pixels)) {
                    ends.set(ends.size() - 1, end);
                    continue;
                }
                if (ends.size() >= room) {
                    // Out of budget at this size. Keep walking to learn the true frame count, but
                    // write nothing more; the caller will come back at a size that fits.
                    wholeThing = false;
                    ends.add(end);
                    previous = pixels.clone();
                    continue;
                }
                data.write(pixels);
                ends.add(end);
                previous = pixels.clone();
            }
            data.flush();
        } catch (IOException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        } finally {
            closeQuietly(data);
            if (frame != null) {
                frame.recycle();
            }
        }
        return new Walk(width, height, ends, wholeThing);
    }

    /** A still is a pack of one frame: decoded once, at the size the screen can show, and no more. */
    private static boolean bakeStill(byte[] bytes, File temp, int screenEdge) {
        boolean alpha = hasAlpha(bytes);
        Bitmap still = decodeStill(bytes, screenEdge, alpha);
        if (still == null) {
            return false;
        }
        int format = alpha ? FramePack.WITH_ALPHA : FramePack.OPAQUE;
        OutputStream data = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(still.getWidth() * still.getHeight()
                    * FramePack.bytesPerPixel(format));
            still.copyPixelsToBuffer(buffer);
            data = new BufferedOutputStream(new FileOutputStream(temp), 32 * 1024);
            data.write(buffer.array());
            data.flush();
        } catch (IOException e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        } catch (OutOfMemoryError e) {
            return false;
        } finally {
            closeQuietly(data);
        }
        java.util.ArrayList<Integer> ends = new java.util.ArrayList<Integer>();
        ends.add(1);
        boolean ok = finish(temp, still.getWidth(), still.getHeight(), format, ends);
        still.recycle();
        return ok;
    }

    /**
     * Puts the header in front of the pixels already written. The data was streamed to disk before
     * the frame times were known — they are only known once the walk is over — so the finished file
     * is the header followed by that data.
     */
    private static boolean finish(File temp, int width, int height, int format,
            java.util.List<Integer> ends) {
        int[] array = new int[ends.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = ends.get(i);
        }
        File body = new File(temp.getPath() + ".body");
        if (!temp.renameTo(body)) {
            return false;
        }
        OutputStream out = null;
        java.io.InputStream in = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(temp), 32 * 1024);
            out.write(FramePack.header(width, height, format, array));
            in = new java.io.BufferedInputStream(new java.io.FileInputStream(body), 32 * 1024);
            byte[] chunk = new byte[32 * 1024];
            int read;
            while ((read = in.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(out);
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            body.delete();
        }
    }

    /** Whether a decoded picture has an alpha channel, for the formats that report one. */
    private static boolean hasAlpha(byte[] bytes) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 16;
            Bitmap probe = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (probe == null) {
                return false;
            }
            boolean alpha = probe.hasAlpha();
            probe.recycle();
            return alpha;
        } catch (RuntimeException e) {
            return false;
        } catch (OutOfMemoryError e) {
            return true;
        }
    }

    private static Movie decodeMovie(byte[] bytes) {
        try {
            return Movie.decodeByteArray(bytes, 0, bytes.length);
        } catch (RuntimeException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** The still decode, downsampled so its longer edge is no larger than the screen's. */
    private static Bitmap decodeStill(byte[] bytes, int screenEdge, boolean alpha) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            options.inPreferredConfig = alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            int edge = Math.max(bounds.outWidth, bounds.outHeight);
            while (edge / options.inSampleSize > screenEdge) {
                options.inSampleSize *= 2;
            }
            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            Bitmap.Config wanted = alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            if (decoded == null || decoded.getConfig() == wanted) {
                return decoded;
            }
            // A decoder that ignored the hint: copy into the format the pack stores.
            Bitmap converted = decoded.copy(wanted, false);
            decoded.recycle();
            return converted;
        } catch (RuntimeException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static byte[] readAll(File file) {
        if (file == null || !file.isFile() || file.length() > BackgroundImage.MAX_IMAGE_BYTES) {
            return null;
        }
        java.io.InputStream in = null;
        try {
            byte[] bytes = new byte[(int) file.length()];
            in = new java.io.FileInputStream(file);
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    return null;
                }
                offset += read;
            }
            return bytes;
        } catch (IOException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static void closeQuietly(OutputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException e) {
        }
    }
}
