package com.reteclock.core;

/**
 * Whether this device can actually speak, and what to tell the user when it cannot.
 *
 * Three ways for speech to be missing on an old phone, and only the first is obvious:
 *
 * <ol>
 *   <li>no speech engine installed at all — common on AOSP builds and on phones that never had
 *       Google's own applications;</li>
 *   <li>an engine that refuses to start;</li>
 *   <li>an engine that starts perfectly and has no voice data. This one reports success, accepts
 *       the text, and says nothing — so a user who typed a message is left wondering.</li>
 * </ol>
 *
 * All three end the same way: the beeps, the count-in, the finishing melody and the screen flash
 * are unaffected, and only the spoken message is lost. The point of this class is that the settings
 * screen can say so rather than leaving it a mystery.
 */
public final class VoiceState {

    /** Not asked yet: the engine starts asynchronously. */
    public static final int INIT_UNKNOWN = 0;
    public static final int INIT_OK = 1;
    public static final int INIT_FAILED = 2;

    public static final int LANG_UNKNOWN = 0;
    public static final int LANG_OK = 1;
    /** The engine is there but has no data for the language. */
    public static final int LANG_MISSING = 2;
    public static final int LANG_UNSUPPORTED = 3;

    /** What the settings screen has to say about it. */
    public static final int UNKNOWN = 0;
    public static final int READY = 1;
    /** Nothing to speak with: no engine, or one that will not start. */
    public static final int ABSENT = 2;
    /** An engine, but no voice: the case that looks like success and is not. */
    public static final int NO_VOICE = 3;

    private VoiceState() {
    }

    /** Whether a message would actually be heard. */
    public static boolean usable(boolean engineInstalled, int init, int language) {
        return engineInstalled && init == INIT_OK && language == LANG_OK;
    }

    /** Which of the four things to say, given what is known so far. */
    public static int summary(boolean engineInstalled, int init, int language) {
        if (!engineInstalled || init == INIT_FAILED) {
            return ABSENT;
        }
        if (language == LANG_MISSING || language == LANG_UNSUPPORTED) {
            return NO_VOICE;
        }
        if (init == INIT_OK && language == LANG_OK) {
            return READY;
        }
        return UNKNOWN;
    }
}
