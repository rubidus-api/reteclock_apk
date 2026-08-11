package com.reteclock;

import android.graphics.Bitmap;

import com.reteclock.core.FramePack;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * A prepared picture, open for playing: the frames come off the disk already the right size and
 * already in the pixel format the screen wants, so showing one costs a read and a blit.
 *
 * One frame is held in memory at a time — the same bitmap, refilled — so an animation of eighty
 * frames costs no more than an animation of one. The read is a seek and a block of bytes, which is
 * what an old phone can afford at twenty-five frames a second; decoding a GIF frame and scaling it
 * to the screen in software, which is what this replaces, is what it could not.
 */
final class PreparedImage {

    private final FramePack pack;
    private final RandomAccessFile file;
    private final Bitmap frame;
    private final ByteBuffer pixels;
    /** Which frame the bitmap currently holds; -1 until the first read. */
    private int loaded = -1;

    private PreparedImage(FramePack pack, RandomAccessFile file, Bitmap frame) {
        this.pack = pack;
        this.file = file;
        this.frame = frame;
        this.pixels = ByteBuffer.allocate(pack.frameBytes());
    }

    /** Opens a prepared file, or returns null for one that is missing, stale or not ours. */
    static PreparedImage open(File path) {
        if (path == null || !path.isFile()) {
            return null;
        }
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(path, "r");
            byte[] fixed = new byte[FramePack.fixedHeaderBytes()];
            file.readFully(fixed);
            int count = readCount(fixed);
            if (count <= 0 || count > 4096) {
                file.close();
                return null;
            }
            byte[] header = new byte[FramePack.headerBytes(count)];
            System.arraycopy(fixed, 0, header, 0, fixed.length);
            file.readFully(header, fixed.length, header.length - fixed.length);
            FramePack pack = FramePack.parse(header);
            if (pack == null
                    || file.length() < pack.frameOffset(pack.frameCount())) {
                file.close();
                return null;
            }
            Bitmap frame = Bitmap.createBitmap(pack.width(), pack.height(),
                    pack.format() == FramePack.WITH_ALPHA
                            ? Bitmap.Config.ARGB_8888
                            : Bitmap.Config.RGB_565);
            return new PreparedImage(pack, file, frame);
        } catch (IOException e) {
            close(file);
            return null;
        } catch (RuntimeException e) {
            close(file);
            return null;
        } catch (OutOfMemoryError e) {
            close(file);
            return null;
        }
    }

    /** The frame count out of the fixed part of the header, without trusting the rest of it yet. */
    private static int readCount(byte[] fixed) {
        return ((fixed[20] & 0xFF) << 24) | ((fixed[21] & 0xFF) << 16)
                | ((fixed[22] & 0xFF) << 8) | (fixed[23] & 0xFF);
    }

    boolean animated() {
        return pack.frameCount() > 1;
    }

    /** How long one play-through lasts; zero for a still, which is what Slideshow expects. */
    int durationMs() {
        return animated() ? pack.durationMs() : 0;
    }

    int width() {
        return pack.width();
    }

    int height() {
        return pack.height();
    }

    /** How many frames it holds, and how large they are — what the settings screen reports. */
    int frameCount() {
        return pack.frameCount();
    }

    /**
     * The frame showing at this point in the animation, refilling the bitmap only when the frame
     * has actually changed — a GIF holding a pose for half a second costs one read, not twelve.
     * Returns the last frame it managed to read if a read fails, rather than nothing at all.
     */
    Bitmap frame(long frameMs) {
        int wanted = pack.frameAt(frameMs);
        if (wanted == loaded) {
            return frame;
        }
        try {
            file.seek(pack.frameOffset(wanted));
            file.readFully(pixels.array());
            pixels.rewind();
            frame.copyPixelsFromBuffer(pixels);
            loaded = wanted;
        } catch (IOException e) {
            // A file that goes away underneath us leaves the picture as it was.
        } catch (RuntimeException e) {
        }
        return loaded < 0 ? null : frame;
    }

    void release() {
        close(file);
    }

    private static void close(RandomAccessFile file) {
        if (file == null) {
            return;
        }
        try {
            file.close();
        } catch (IOException e) {
        }
    }
}
