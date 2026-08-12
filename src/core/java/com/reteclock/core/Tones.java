package com.reteclock.core;

/**
 * The timer's sounds, written down as notes rather than recorded as audio.
 *
 * Nothing is shipped: the device makes these itself, a few hundred samples at a time. That keeps
 * the APK small, needs no decoder on a 2012 phone, sounds identical from Android 2.3 upwards, and
 * cannot carry a licence problem — which the finishing melody otherwise would.
 *
 * The melody is the opening of Beethoven's *Für Elise*, the tune half the washing machines in the
 * world play. Beethoven died in 1827, so it is long out of copyright; a modern jingle would not be.
 */
public final class Tones {

    /** One beep: a pitch, how long it sounds, and how long of nothing follows it. */
    public static final class Note {
        public final int hz;
        public final int onMs;
        public final int offMs;

        public Note(int hz, int onMs, int offMs) {
            this.hz = hz;
            this.onMs = onMs;
            this.offMs = offMs;
        }
    }

    /** The warning an interval asks for: three quick beeps, high and impatient. */
    public static final Note[] PRE_ALARM = {
        new Note(2000, 60, 40),
        new Note(2000, 60, 40),
        new Note(2000, 60, 40),
    };

    /** One of the three counted seconds before an ending: low, like a clock's pip. */
    public static final Note[] TICK = {
        new Note(800, 120, 0),
    };

    /** The ending itself, landing on the boundary: higher, and longer, so it reads as arrival. */
    public static final Note[] END = {
        new Note(1600, 220, 0),
    };

    public static final String FINISH_NAME = "Für Elise (Beethoven, 1810)";

    /**
     * The opening of Für Elise: E D# E D# E B D C A. Pitches in equal temperament, near enough
     * that a square-wave beep sounds like the tune everybody knows.
     */
    public static final Note[] FINISH = {
        new Note(659, 140, 20),   // E5
        new Note(622, 140, 20),   // D#5
        new Note(659, 140, 20),   // E5
        new Note(622, 140, 20),   // D#5
        new Note(659, 140, 20),   // E5
        new Note(494, 140, 20),   // B4
        new Note(587, 140, 20),   // D5
        new Note(523, 140, 20),   // C5
        new Note(440, 320, 80),   // A4
    };

    private Tones() {
    }

    /** How long a pattern takes from its first sound to the end of its last silence. */
    public static int lengthMs(Note[] pattern) {
        int total = 0;
        for (Note note : pattern) {
            total += note.onMs + note.offMs;
        }
        return total;
    }
}
