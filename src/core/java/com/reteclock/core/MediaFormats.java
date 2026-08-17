package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which sound files this phone can be expected to play, and what to call them.
 *
 * There are two different questions here and they have two different answers.
 *
 * <p>"What may I bring?" has to be answered <em>before</em> anything is imported, on a screen, in a
 * sentence. That is this table: a format, the names it goes by, and the API level its decoder
 * arrived in. It is a promise about the platform, so it is written down and tested rather than
 * probed — probing would mean shipping a sample of every format and decoding it on a phone that is
 * being asked to draw a clock.
 *
 * <p>"Will <em>this</em> file play?" is not a question about the platform at all. A file with the
 * right extension can still be a picture somebody renamed, or an MP3 with a broken header, or a
 * variant this particular manufacturer's decoder does not take. That question is answered by the
 * device's own decoder at import — the same way {@code usableFont} lets the platform decide what a
 * font is — and this class deliberately does not try to answer it.
 *
 * <p>The floors are conservative. Where the documentation says a format arrived in Android 1.0 but
 * the container was only listed later, the later number is used: a format claimed and then refused
 * is worse than one that quietly works anyway, because the file the user is refused is a file they
 * can hear playing in another app.
 */
public final class MediaFormats {

    /** One playable format. */
    public static final class Format {
        /** What the screen calls it. */
        public final String label;
        /** The API level its decoder can be relied on from. */
        public final int minApi;
        /** Lower-case extensions, without the dot, that usually carry it. */
        public final String[] extensions;

        Format(String label, int minApi, String[] extensions) {
            this.label = label;
            this.minApi = minApi;
            this.extensions = extensions;
        }

        /** Whether a phone at this API level can be expected to play it. */
        public boolean playableAt(int sdkInt) {
            return sdkInt >= minApi;
        }
    }

    /**
     * The formats, in the order the screen lists them: the certain one first, then the rest of what
     * every supported phone has, then the two that depend on how new the phone is.
     */
    private static final Format[] ALL = {
        new Format("MP3", 9, new String[] {"mp3"}),
        new Format("M4A / MP4 (AAC)", 9, new String[] {"m4a", "mp4", "aac", "3g2"}),
        new Format("OGG (Vorbis)", 9, new String[] {"ogg", "oga"}),
        new Format("3GP", 9, new String[] {"3gp"}),
        new Format("AMR", 9, new String[] {"amr"}),
        new Format("MIDI", 9, new String[] {"mid", "midi", "xmf", "rtttl", "ota", "imy"}),
        new Format("WAV (PCM)", 9, new String[] {"wav", "wave"}),
        new Format("FLAC", 16, new String[] {"flac"}),
        new Format("Opus", 21, new String[] {"opus"}),
    };

    private MediaFormats() {
    }

    /** Every format this class knows, whatever phone is asking. */
    public static Format[] all() {
        Format[] copy = new Format[ALL.length];
        System.arraycopy(ALL, 0, copy, 0, ALL.length);
        return copy;
    }

    /** The ones a phone at this API level can be expected to play, in listing order. */
    public static List<Format> playableAt(int sdkInt) {
        List<Format> out = new ArrayList<Format>();
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].playableAt(sdkInt)) {
                out.add(ALL[i]);
            }
        }
        return out;
    }

    /** The same list as one line for the screen: "MP3, M4A / MP4 (AAC), OGG (Vorbis), …". */
    public static String labelsAt(int sdkInt) {
        StringBuilder out = new StringBuilder();
        List<Format> playable = playableAt(sdkInt);
        for (int i = 0; i < playable.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(playable.get(i).label);
        }
        return out.toString();
    }

    /**
     * The format a file name suggests, or null when the name suggests none.
     *
     * A guess, and used as one: it decides which files the picker offers to show and what the list
     * calls a file. Nothing is refused on the strength of it — the decoder does that.
     */
    public static Format guess(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.US);
        for (int i = 0; i < ALL.length; i++) {
            for (int j = 0; j < ALL[i].extensions.length; j++) {
                if (ALL[i].extensions[j].equals(extension)) {
                    return ALL[i];
                }
            }
        }
        return null;
    }

    /** Whether the name looks like a sound file at all, for sorting the picker's offer. */
    public static boolean looksLikeSound(String fileName) {
        return guess(fileName) != null;
    }
}
