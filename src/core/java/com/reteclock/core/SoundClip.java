package com.reteclock.core;

/**
 * How one stored sound is played: from where, to where, and whether it starts again.
 *
 * A person who imports a song wants four seconds of it, not four minutes, and they want to say so
 * once — not at every place the sound is used. So the clip belongs to the file, and every cue that
 * names the file gets the same four seconds.
 *
 * <p>Points are in tenths of a second because that is the resolution the settings screen offers, and
 * because a tenth is the smallest step that is worth typing: at a hundredth nobody can hear which
 * one they picked, and the field grows a digit that is always wrong.
 *
 * <p>Immutable, and it makes its own arguments safe: these values are written into a text file the
 * user can carry between phones and edit by hand.
 */
public final class SoundClip {

    /** An end point of zero means "play to the end of the file", whatever length that is. */
    public static final int TO_THE_END = 0;

    /** Nothing is clipped past this: an hour is longer than any cue and longer than most files. */
    public static final int MAX_TENTHS = 36000;

    /** The stored file this describes. */
    public final String name;
    /** Where playing begins, in tenths of a second from the start of the file. */
    public final int startTenths;
    /** Where it stops, in tenths; {@link #TO_THE_END} for the end of the file. */
    public final int endTenths;
    /** Whether it starts again when it reaches the end point. */
    public final boolean loops;

    public SoundClip(String name, int startTenths, int endTenths, boolean loops) {
        this.name = name == null ? "" : name;
        this.startTenths = clamp(startTenths);
        int end = clamp(endTenths);
        // An end at or before the start would be a clip with nothing in it, and a silent alarm is
        // indistinguishable from a broken one. Anything that does not describe a stretch of sound
        // means the whole file.
        this.endTenths = end <= this.startTenths ? TO_THE_END : end;
        this.loops = loops;
    }

    /** The plain clip for a file nobody has adjusted: all of it, once. */
    public static SoundClip whole(String name) {
        return new SoundClip(name, 0, TO_THE_END, false);
    }

    /** Whether this is that plain clip, which is the one worth not writing down. */
    public boolean isWhole() {
        return startTenths == 0 && endTenths == TO_THE_END && !loops;
    }

    public long startMs() {
        return startTenths * 100L;
    }

    /** Where to stop, or -1 for "when the file ends", which only the player can know. */
    public long endMs() {
        return endTenths == TO_THE_END ? -1L : endTenths * 100L;
    }

    /** How long one pass lasts, or -1 when that depends on the file. */
    public long lengthMs() {
        return endTenths == TO_THE_END ? -1L : (endTenths - startTenths) * 100L;
    }

    public SoundClip withStart(int tenths) {
        return new SoundClip(name, tenths, endTenths, loops);
    }

    public SoundClip withEnd(int tenths) {
        return new SoundClip(name, startTenths, tenths, loops);
    }

    public SoundClip withLoops(boolean repeating) {
        return new SoundClip(name, startTenths, endTenths, repeating);
    }

    public SoundClip withName(String newName) {
        return new SoundClip(newName, startTenths, endTenths, loops);
    }

    /** Tenths as a person writes them: "0.0", "12.5". */
    public static String tenthsText(int tenths) {
        int safe = clamp(tenths);
        return (safe / 10) + "." + (safe % 10);
    }

    /** And back, from anything a person can type into the field. Rubbish becomes zero. */
    public static int parseTenths(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        boolean negative = trimmed.startsWith("-");
        if (negative) {
            return 0;               // a point before the beginning is the beginning
        }
        int dot = trimmed.indexOf('.');
        if (dot < 0) {
            dot = trimmed.indexOf(',');     // a comma is a decimal point in most of the world
        }
        String whole = dot < 0 ? trimmed : trimmed.substring(0, dot);
        String fraction = dot < 0 ? "" : trimmed.substring(dot + 1);
        long seconds = digits(whole);
        int tenth = 0;
        if (!fraction.isEmpty()) {
            char first = fraction.charAt(0);
            if (first >= '0' && first <= '9') {
                tenth = first - '0';
            }
        }
        long tenths = seconds * 10 + tenth;
        return clamp(tenths > MAX_TENTHS ? MAX_TENTHS : (int) tenths);
    }

    private static long digits(String text) {
        long value = 0;
        for (int i = 0; i < text.length() && value <= MAX_TENTHS; i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
            }
        }
        return value;
    }

    private static int clamp(int tenths) {
        if (tenths < 0) {
            return 0;
        }
        return tenths > MAX_TENTHS ? MAX_TENTHS : tenths;
    }
}
