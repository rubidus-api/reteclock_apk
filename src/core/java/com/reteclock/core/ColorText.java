package com.reteclock.core;

/**
 * A colour as it is typed and as it is shown: `#RRGGBB`, `#AARRGGBB`, `#RGB`, or nothing at all.
 *
 * The palette covers the colours somebody picks in a hurry; this covers the one they already have
 * in mind. Written by hand rather than with `Color.parseColor`, which lives in the framework and so
 * cannot be reached from the JVM tests where everything in this package is checked — and which
 * throws on bad input, where a text field wants an answer instead.
 *
 * Nothing at all is a real answer here: an interval may leave one of its two colours empty, and the
 * bar then shows whatever is behind it rather than a colour. That is {@link #NONE} — transparent
 * black, which is what a zero alpha means everywhere in Android.
 */
public final class ColorText {

    /** No colour: the bar shows the clock behind it. */
    public static final int NONE = 0x00000000;

    private ColorText() {
    }

    /** Whether a colour would draw anything at all. */
    public static boolean isNone(int color) {
        return (color >>> 24) == 0;
    }

    /**
     * A colour as `#RRGGBB`, or `#AARRGGBB` when it is partly transparent, or the empty string when
     * it is not there at all — the same forms {@link #parse} accepts, so what is shown can be typed
     * back in.
     */
    public static String of(int color) {
        if (isNone(color)) {
            return "";
        }
        int alpha = color >>> 24;
        StringBuilder out = new StringBuilder(9).append('#');
        if (alpha != 0xFF) {
            two(out, alpha);
        }
        two(out, color >> 16 & 0xFF);
        two(out, color >> 8 & 0xFF);
        two(out, color & 0xFF);
        return out.toString();
    }

    /**
     * A typed colour, or {@code fallback} for anything that is not one.
     *
     * The leading hash is optional, case does not matter, and space around it is ignored — this is
     * a text field, and refusing ` #FFF ` for its spaces would be pedantry. Three digits are the
     * shorthand the web uses, each doubled: `#f0a` is `#FF00AA`. Six digits are opaque. Eight carry
     * their own alpha. Empty text is no colour, which is a choice rather than a mistake.
     */
    public static int parse(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String at = text.trim();
        if (at.startsWith("#")) {
            at = at.substring(1).trim();
        }
        if (at.isEmpty()) {
            return NONE;
        }
        long value = 0L;
        for (int i = 0; i < at.length(); i++) {
            int digit = digit(at.charAt(i));
            if (digit < 0) {
                return fallback;
            }
            value = value * 16L + digit;
        }
        switch (at.length()) {
            case 3:
                int r = (int) (value >> 8 & 0xF);
                int g = (int) (value >> 4 & 0xF);
                int b = (int) (value & 0xF);
                return 0xFF000000 | r << 20 | r << 16 | g << 12 | g << 8 | b << 4 | b;
            case 6:
                return (int) (0xFF000000L | value);
            case 8:
                return (int) value;
            default:
                return fallback;
        }
    }

    private static int digit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static void two(StringBuilder out, int value) {
        String hex = Integer.toHexString(value & 0xFF).toUpperCase(java.util.Locale.US);
        if (hex.length() < 2) {
            out.append('0');
        }
        out.append(hex);
    }
}
