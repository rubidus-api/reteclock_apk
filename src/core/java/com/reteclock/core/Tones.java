package com.reteclock.core;

/**
 * The timer's sounds, written down as notes rather than recorded as audio.
 *
 * Nothing is shipped: the device makes these itself, a few hundred samples at a time. That keeps
 * the APK small, needs no decoder on a 2012 phone, sounds identical from Android 2.3 upwards, and
 * cannot carry a licence problem — which the finishing melody otherwise would.
 *
 * The melody is the theme of the fourth movement of Schubert's *Trout* Quintet — his own song *Die
 * Forelle* — which is the tune Samsung's washing machines play when the wash is done. Schubert died
 * in 1828, so the music is long out of copyright. What is *not* free is any particular recording of
 * it, which is why this is the notes rather than the sound: the pitches and lengths below are read
 * from the score, and the phone plays them itself.
 *
 * The phrase is the theme's opening, stopping at the end of its first line — the whole theme is
 * some twenty seconds and nobody wants that from a kitchen timer.
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

    // The pitches the timer speaks in, all of them from the D major the melody is in, so a beep and
    // the tune that follows it are in the same world rather than two.
    private static final int A4 = 440;
    private static final int B4 = 494;
    private static final int CIS5 = 554;
    private static final int D5 = 587;
    private static final int E5 = 659;
    private static final int FIS5 = 740;
    private static final int A5 = 880;

    /** The warning an interval asks for: three quick notes, high but not shrill. */
    public static final Note[] PRE_ALARM = {
        new Note(A5, 70, 60),
        new Note(A5, 70, 60),
        new Note(A5, 70, 0),
    };

    /** One of the three counted seconds before an ending: low, like a clock's pip. */
    public static final Note[] TICK = {
        new Note(A4, 110, 0),
    };

    /** The ending itself, landing on the boundary: a fifth above the count, so it reads as arrival. */
    public static final Note[] END = {
        new Note(E5, 130, 30),
        new Note(A5, 240, 0),
    };

    public static final String FINISH_NAME = "Die Forelle (Schubert, 1817)";

    /**
     * The opening line of the theme, in D major, from the score:
     *
     * <pre>
     *   \partial 8  a8 | d8. d16 fis8 fis8 | d4( a) | a8.. a32 e'16.( d32 cis16. b32) | a4.
     * </pre>
     *
     * An eighth note is 230 ms here — brisker than the Andantino it is marked, because this is a
     * chime and not a performance — and the four staccato notes are played short with the silence
     * after them, which is what the dots mean. Pitches are equal temperament to the nearest hertz.
     */
    public static final Note[] FINISH = {
        new Note(A4, 200, 30),    // a8       — the upbeat
        new Note(D5, 180, 165),   // d8.      staccato
        new Note(D5, 80, 35),     // d16      staccato
        new Note(FIS5, 150, 80),  // fis8     staccato
        new Note(FIS5, 150, 80),  // fis8     staccato
        new Note(D5, 450, 10),    // d4       slurred down to
        new Note(A4, 440, 20),    // a4
        new Note(A4, 380, 22),    // a8..
        new Note(A4, 45, 12),     // a32
        new Note(E5, 155, 17),    // e16.
        new Note(D5, 45, 12),     // d32
        new Note(CIS5, 155, 17),  // cis16.
        new Note(B4, 45, 12),     // b32
        new Note(A4, 660, 0),     // a4.
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
