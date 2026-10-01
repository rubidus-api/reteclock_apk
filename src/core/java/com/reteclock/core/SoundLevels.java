package com.reteclock.core;

/**
 * How loud each kind of sound this app makes is, and whether it buzzes, in each of the phone's
 * three modes — ringing, vibrate and silent.
 *
 * <p>The phone's ringer switch says which of the three applies; what each one does is the user's to
 * choose, kind by kind. A level is a share of the loudness the sound always had — 100 is that
 * loudness, and nothing is made louder: the files already play at nine tenths of full, and a level
 * only some kinds could reach would be a level that lied. A mute is its own switch beside the
 * level, so switching it off brings back the level that was there.
 *
 * <p><b>Nothing set is what the app always did</b>, with one change the owner asked for
 * (2026-10-01): on a vibrating phone the timer buzzes, where it used to do nothing at all. Ringing,
 * everything plays; on vibrate and on silent nothing does, except the wake bells, which are an
 * alarm and ring whatever the switch says. The timer's old sound / vibrate / silent choice is no
 * longer shown, but it is not thrown away either: it is read as the default of the timer rows, so a
 * phone whose timer was set to vibrate goes on vibrating.
 *
 * <p>*Mute all* is one more switch over every tab and changes no row. It silences every sound but
 * the wake bells — an alarm a switch meant for the evening's beeps had silenced is a missed morning —
 * and it stops no vibration: a buzz is not a sound.
 */
public final class SoundLevels {

    /** The timer's beeps and the sounds chosen for its cues. */
    public static final int TIMER_CUES = 0;
    /** The interval messages the timer speaks. */
    public static final int TIMER_MESSAGES = 1;
    /** The time said when the clock is tapped. */
    public static final int SPOKEN_TIME = 2;
    /** Bells that ring on the clock's screen. */
    public static final int BELLS = 3;
    /** Bells that wake the phone, on the alarm stream. */
    public static final int WAKE_BELLS = 4;
    public static final int COUNT = 5;

    /** The phone's ringer switch: ringing, vibrate, silent. */
    public static final int RING = 0;
    public static final int VIBRATE = 1;
    public static final int SILENT = 2;
    public static final int MODES = 3;

    /** The timer's old choice, as {@code timer_alert} stores it. */
    public static final int ALERT_SOUND = 0;
    public static final int ALERT_VIBRATE = 1;
    public static final int ALERT_SILENT = 2;

    /** Today's loudness: the level nothing set means. */
    public static final int FULL = 100;

    public static final String KEY_MUTE_ALL = "sound_mute_all";
    private static final String[] KIND_NAMES = {
        "timer", "timer_speech", "spoken_time", "bells", "wake_bells",
    };
    private static final String[] MODE_NAMES = {"ring", "vibrate", "silent"};

    private final int[][] levels;
    private final boolean[][] muted;
    private final boolean[][] buzz;
    private final boolean allMuted;

    private SoundLevels(int[][] levels, boolean[][] muted, boolean[][] buzz, boolean allMuted) {
        this.levels = levels;
        this.muted = muted;
        this.buzz = buzz;
        this.allMuted = allMuted;
    }

    /** What nothing stored means, given the timer's old sound / vibrate / silent choice. */
    public static SoundLevels defaults(int timerAlert) {
        int[][] levels = new int[MODES][COUNT];
        boolean[][] muted = new boolean[MODES][COUNT];
        boolean[][] buzz = new boolean[MODES][COUNT];
        for (int mode = 0; mode < MODES; mode++) {
            for (int kind = 0; kind < COUNT; kind++) {
                levels[mode][kind] = FULL;
                // A quiet phone is quiet, except for an alarm.
                muted[mode][kind] = mode != RING && kind != WAKE_BELLS;
            }
        }
        boolean timerSounds = timerAlert != ALERT_VIBRATE && timerAlert != ALERT_SILENT;
        muted[RING][TIMER_CUES] = !timerSounds;
        muted[RING][TIMER_MESSAGES] = !timerSounds;
        buzz[RING][TIMER_CUES] = timerAlert == ALERT_VIBRATE;
        buzz[VIBRATE][TIMER_CUES] = timerAlert != ALERT_SILENT;
        return new SoundLevels(levels, muted, buzz, false);
    }

    /** The tab for one of {@code AudioManager}'s ringer modes; the ordinary one when unknown. */
    public static int modeOfRinger(int ringerMode) {
        switch (ringerMode) {
            case 0: return SILENT;
            case 1: return VIBRATE;
            default: return RING;
        }
    }

    /** The stored key for a row's level, e.g. {@code sound_level_ring_bells}. */
    public static String levelKey(int mode, int kind) {
        return "sound_level_" + MODE_NAMES[mode] + "_" + KIND_NAMES[kind];
    }

    /** The stored key for a row's mute, e.g. {@code sound_mute_silent_bells}. */
    public static String muteKey(int mode, int kind) {
        return "sound_mute_" + MODE_NAMES[mode] + "_" + KIND_NAMES[kind];
    }

    /** The stored key for whether a row buzzes, e.g. {@code sound_buzz_vibrate_timer}. */
    public static String buzzKey(int mode, int kind) {
        return "sound_buzz_" + MODE_NAMES[mode] + "_" + KIND_NAMES[kind];
    }

    /** Whether *Mute all* silences this kind: everything except the wake bells. */
    public static boolean obeysMuteAll(int kind) {
        return kind != WAKE_BELLS;
    }

    /** Whether this kind can be a buzz at all: words cannot. */
    public static boolean canBuzz(int kind) {
        return kind != TIMER_MESSAGES && kind != SPOKEN_TIME;
    }

    public static int clampLevel(int level) {
        return level < 0 ? 0 : level > FULL ? FULL : level;
    }

    public SoundLevels withLevel(int mode, int kind, int level) {
        int[][] next = copy(levels);
        next[mode][kind] = clampLevel(level);
        return new SoundLevels(next, muted, buzz, allMuted);
    }

    public SoundLevels withMuted(int mode, int kind, boolean on) {
        boolean[][] next = copy(muted);
        next[mode][kind] = on;
        return new SoundLevels(levels, next, buzz, allMuted);
    }

    public SoundLevels withBuzz(int mode, int kind, boolean on) {
        boolean[][] next = copy(buzz);
        next[mode][kind] = on;
        return new SoundLevels(levels, muted, next, allMuted);
    }

    public SoundLevels withAllMuted(boolean on) {
        return new SoundLevels(levels, muted, buzz, on);
    }

    public int level(int mode, int kind) {
        return levels[mode][kind];
    }

    public boolean muted(int mode, int kind) {
        return muted[mode][kind];
    }

    /** The switch as stored, which for a spoken kind means nothing — see {@link #buzzes}. */
    public boolean buzz(int mode, int kind) {
        return buzz[mode][kind];
    }

    public boolean allMuted() {
        return allMuted;
    }

    /** Whether this kind makes any sound in this mode. */
    public boolean audible(int mode, int kind) {
        return !muted[mode][kind] && levels[mode][kind] > 0 && !(allMuted && obeysMuteAll(kind));
    }

    /** The share of its usual loudness this kind plays at in this mode: 0 when silenced. */
    public float gain(int mode, int kind) {
        return audible(mode, kind) ? levels[mode][kind] / (float) FULL : 0f;
    }

    /** Whether this kind buzzes the phone in this mode. */
    public boolean buzzes(int mode, int kind) {
        return canBuzz(kind) && buzz[mode][kind];
    }

    /**
     * Whether this row makes a sound on a phone that was switched to vibrate or silent — which the
     * screen says out loud, because somebody silenced that phone. The wake bells are left out: an
     * alarm ringing through the switch is what they are for.
     */
    public boolean soundsOnQuietPhone(int mode, int kind) {
        return mode != RING && kind != WAKE_BELLS && audible(mode, kind);
    }

    private static int[][] copy(int[][] from) {
        int[][] out = new int[from.length][];
        for (int i = 0; i < from.length; i++) {
            out[i] = from[i].clone();
        }
        return out;
    }

    private static boolean[][] copy(boolean[][] from) {
        boolean[][] out = new boolean[from.length][];
        for (int i = 0; i < from.length; i++) {
            out[i] = from[i].clone();
        }
        return out;
    }
}
