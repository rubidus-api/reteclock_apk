package com.reteclock.core;

/**
 * What a GIF says about itself, read straight from its bytes.
 *
 * Only one question so far, and it matters: does any frame use a transparent colour? A picture that
 * does cannot be baked into a {@link FramePack}, whose pixels have no alpha — the colour showing
 * through the holes is the user's background setting, which they can change after the baking.
 *
 * Asking the platform decoder instead does not work: on Android 4.4 a transparent GIF comes back
 * from `BitmapFactory` composited onto opaque black, reporting no alpha at all, so the holes would
 * be baked shut without anyone noticing.
 *
 * The walk is deliberately timid. Anything malformed, truncated or unfamiliar answers "no
 * transparency", which merely means the picture takes the ordinary path.
 */
public final class GifInfo {

    private GifInfo() {
    }

    /** Whether this is a GIF at all, by the only part of the name that matters. */
    public static boolean isGif(byte[] bytes) {
        return bytes != null && bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8';
    }

    /**
     * Whether any frame declares a transparent colour.
     *
     * A GIF carries that in a Graphic Control Extension — one flag bit ahead of each image — so the
     * answer needs the block structure but never the pixels.
     */
    public static boolean hasTransparency(byte[] bytes) {
        if (!isGif(bytes) || bytes.length < 13) {
            return false;
        }
        int at = 6;
        int packed = bytes[at + 4] & 0xFF;
        at += 7;
        if ((packed & 0x80) != 0) {
            at += colourTableBytes(packed);
        }
        while (at < bytes.length) {
            int block = bytes[at] & 0xFF;
            at++;
            if (block == 0x3B) {
                return false;
            }
            if (block == 0x21) {
                if (at >= bytes.length) {
                    return false;
                }
                int label = bytes[at] & 0xFF;
                at++;
                if (label == 0xF9) {
                    // Graphic control: size, flags, delay, transparent index, terminator.
                    if (at + 1 >= bytes.length) {
                        return false;
                    }
                    int size = bytes[at] & 0xFF;
                    if (size >= 1 && (bytes[at + 1] & 0x01) != 0) {
                        return true;
                    }
                }
                at = skipSubBlocks(bytes, at);
            } else if (block == 0x2C) {
                if (at + 8 >= bytes.length) {
                    return false;
                }
                int imagePacked = bytes[at + 8] & 0xFF;
                at += 9;
                if ((imagePacked & 0x80) != 0) {
                    at += colourTableBytes(imagePacked);
                }
                // The LZW minimum code size, then the image's own sub-blocks.
                at++;
                at = skipSubBlocks(bytes, at);
            } else {
                // Something this walk does not know: stop rather than guess.
                return false;
            }
            if (at <= 0) {
                return false;
            }
        }
        return false;
    }

    /** A colour table holds three bytes for each of its entries, and the count is in the flags. */
    private static int colourTableBytes(int packed) {
        return 3 * (1 << ((packed & 0x07) + 1));
    }

    /**
     * Steps over a chain of length-prefixed sub-blocks, ending at the zero-length one. Returns -1
     * when the chain runs off the end of the data, which stops the walk.
     */
    private static int skipSubBlocks(byte[] bytes, int at) {
        while (at < bytes.length) {
            int size = bytes[at] & 0xFF;
            at++;
            if (size == 0) {
                return at;
            }
            at += size;
        }
        return -1;
    }
}
