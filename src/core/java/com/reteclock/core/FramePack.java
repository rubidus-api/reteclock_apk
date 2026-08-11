package com.reteclock.core;

/**
 * The layout of a prepared picture: the file the clock actually plays.
 *
 * Everything the user imports is baked, once, into this one shape — a still is a pack of one frame,
 * an animation a pack of many — so that showing a picture costs a read and a blit and never a
 * decode. That matters on the hardware this app is for: playing a GIF live meant decoding a frame
 * and scaling it to the screen in software twenty-five times a second, which a 2012 phone cannot
 * do while also answering a touch.
 *
 * Pixels are RGB_565 where the picture is opaque — half the memory and half the bus traffic of
 * ARGB, and a GIF has at most 256 colours to lose — and ARGB_8888 where it genuinely has holes, so
 * the background colour the user chose still shows through them. Frames are stored at whatever size
 * fits the budget; the hardware stretches them to the screen when they are drawn, so a background
 * never needs more pixels than the screen has.
 *
 * <pre>
 *   0  'R' 'C' 'F' 'P'
 *   4  version (2)
 *   8  pixel format (0 opaque / 1 with alpha)
 *  12  width
 *  16  height
 *  20  frameCount
 *  24  frameCount cumulative end times, in milliseconds
 *      frameCount frames of width*height*bytesPerPixel
 * </pre>
 *
 * All integers are big-endian. This class is the arithmetic only — the reading and writing of
 * bitmaps belongs to the Android layer.
 */
public final class FramePack {

    /** Pixels with no alpha channel: RGB_565, two bytes each. */
    public static final int OPAQUE = 0;
    /** Pixels that keep their holes: ARGB_8888, four bytes each. */
    public static final int WITH_ALPHA = 1;

    private static final int VERSION = 2;
    private static final int FIXED_HEADER = 24;

    private final int format;
    private final int width;
    private final int height;
    private final int[] frameEnds;

    private FramePack(int format, int width, int height, int[] frameEnds) {
        this.format = format;
        this.width = width;
        this.height = height;
        this.frameEnds = frameEnds;
    }

    /** How many bytes one pixel takes in this format. */
    public static int bytesPerPixel(int format) {
        return format == WITH_ALPHA ? 4 : 2;
    }

    /** The header for a pack of this format and size, with these cumulative frame end times. */
    public static byte[] header(int width, int height, int format, int[] frameEnds) {
        byte[] out = new byte[FIXED_HEADER + 4 * frameEnds.length];
        out[0] = 'R';
        out[1] = 'C';
        out[2] = 'F';
        out[3] = 'P';
        putInt(out, 4, VERSION);
        putInt(out, 8, format);
        putInt(out, 12, width);
        putInt(out, 16, height);
        putInt(out, 20, frameEnds.length);
        for (int i = 0; i < frameEnds.length; i++) {
            putInt(out, FIXED_HEADER + 4 * i, frameEnds[i]);
        }
        return out;
    }

    /**
     * Reads a header, or returns null for anything that is not one of ours — a file from an older
     * version, a truncated write, a picture that was never a pack. The caller then rebuilds it.
     */
    public static FramePack parse(byte[] bytes) {
        if (bytes == null || bytes.length < FIXED_HEADER) {
            return null;
        }
        if (bytes[0] != 'R' || bytes[1] != 'C' || bytes[2] != 'F' || bytes[3] != 'P') {
            return null;
        }
        if (getInt(bytes, 4) != VERSION) {
            return null;
        }
        int format = getInt(bytes, 8);
        int width = getInt(bytes, 12);
        int height = getInt(bytes, 16);
        int count = getInt(bytes, 20);
        if (width <= 0 || height <= 0 || count <= 0) {
            return null;
        }
        if (format != OPAQUE && format != WITH_ALPHA) {
            return null;
        }
        if (bytes.length < FIXED_HEADER + 4 * count) {
            return null;
        }
        int[] ends = new int[count];
        int previous = 0;
        for (int i = 0; i < count; i++) {
            ends[i] = getInt(bytes, FIXED_HEADER + 4 * i);
            if (ends[i] <= previous) {
                return null;
            }
            previous = ends[i];
        }
        return new FramePack(format, width, height, ends);
    }

    /** How many bytes to read before the first frame's pixels begin. */
    public static int headerBytes(int frameCount) {
        return FIXED_HEADER + 4 * frameCount;
    }

    /** The fixed part of the header — enough to learn how long the whole header is. */
    public static int fixedHeaderBytes() {
        return FIXED_HEADER;
    }

    /** {@link #OPAQUE} or {@link #WITH_ALPHA}. */
    public int format() {
        return format;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int frameCount() {
        return frameEnds.length;
    }

    /** How long the whole animation lasts; a still pack's single frame carries its own length. */
    public int durationMs() {
        return frameEnds[frameEnds.length - 1];
    }

    public int[] frameEnds() {
        return frameEnds.clone();
    }

    public int headerBytes() {
        return headerBytes(frameEnds.length);
    }

    /** One frame's pixels, in bytes. */
    public int frameBytes() {
        return width * height * bytesPerPixel(format);
    }

    /** Where one frame's pixels begin in the file. */
    public long frameOffset(int index) {
        return (long) headerBytes() + (long) index * (long) frameBytes();
    }

    /**
     * Which frame is showing at this point in the animation. Times before the beginning show the
     * first frame and times past the end hold the last, which is what a slideshow's play-through
     * wants — it holds the final frame until the show moves on.
     */
    public int frameAt(long timeMs) {
        if (timeMs <= 0) {
            return 0;
        }
        for (int i = 0; i < frameEnds.length; i++) {
            if (timeMs < frameEnds[i]) {
                return i;
            }
        }
        return frameEnds.length - 1;
    }

    /**
     * How far to shrink a picture so that all its frames together fit a memory budget.
     *
     * Returns 1 when it already fits — a picture is never enlarged here, since the hardware does
     * that for free when the frame is drawn. Otherwise the area is scaled down to fit and then
     * nudged until the rounded dimensions really do fit, so the caller can trust the answer.
     */
    public static float planScale(int width, int height, int frames, int format,
            long budgetBytes) {
        if (width <= 0 || height <= 0 || frames <= 0 || budgetBytes <= 0) {
            return 1f;
        }
        long needed = (long) width * (long) height * bytesPerPixel(format) * (long) frames;
        if (needed <= budgetBytes) {
            return 1f;
        }
        float scale = (float) Math.sqrt((double) budgetBytes / (double) needed);
        // Rounding can push a hair over the budget; step down until it does not.
        while (scale > 0.001f && bytesAt(width, height, frames, format, scale) > budgetBytes) {
            scale *= 0.95f;
        }
        return Math.max(scale, 0.001f);
    }

    private static long bytesAt(int width, int height, int frames, int format, float scale) {
        long w = Math.max(1, Math.round(width * scale));
        long h = Math.max(1, Math.round(height * scale));
        return w * h * bytesPerPixel(format) * frames;
    }

    private static void putInt(byte[] out, int at, int value) {
        out[at] = (byte) (value >>> 24);
        out[at + 1] = (byte) (value >>> 16);
        out[at + 2] = (byte) (value >>> 8);
        out[at + 3] = (byte) value;
    }

    private static int getInt(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24)
                | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8)
                | (bytes[at + 3] & 0xFF);
    }
}
