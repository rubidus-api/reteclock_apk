package com.reteclock;

import android.content.Context;
import android.media.AudioManager;

/**
 * Whether this phone is currently willing to make a noise at all.
 *
 * The app's own sounds go out on the music stream, which the platform does <em>not</em> silence with
 * the ringer switch. That is right for a music player and wrong for a clock: somebody who has put
 * their phone on silent has said what they want, and a clock on the desk is not the app that gets to
 * argue with it.
 *
 * <p>Two states, two answers, because the two switches mean different things:
 *
 * <ul>
 *   <li><b>Silent</b> — nothing. No sound, and no vibration either: the phone has been asked to be
 *       still, and a buzzing desk at three in the morning is exactly what that person switched off.
 *   <li><b>Vibrate</b> — no sound, but vibration is still allowed. That is what the setting is for,
 *       and the timer's own vibrate mode is how it gets used.
 * </ul>
 *
 * <p>Nothing here overrides a user's <em>choice</em> inside this app: the timer's own silent and
 * vibrate settings are separate, and this is the phone's answer laid over them. Where they disagree,
 * the quieter one wins, always.
 */
final class PhoneQuiet {

    private PhoneQuiet() {
    }

    /** Whether a sound may be played at all: only when the ringer is in its ordinary mode. */
    static boolean soundAllowed(Context context) {
        return mode(context) == AudioManager.RINGER_MODE_NORMAL;
    }

    /** Whether the phone may be buzzed: everything except full silence. */
    static boolean vibrationAllowed(Context context) {
        return mode(context) != AudioManager.RINGER_MODE_SILENT;
    }

    /**
     * The ringer mode, or "normal" when the phone will not say.
     *
     * A device with no audio service at all is not a device that has asked for quiet, so the
     * unknown case leaves the app as it was rather than silencing it for a reason nobody chose.
     */
    private static int mode(Context context) {
        try {
            AudioManager audio =
                    (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            return audio == null ? AudioManager.RINGER_MODE_NORMAL : audio.getRingerMode();
        } catch (RuntimeException e) {
            return AudioManager.RINGER_MODE_NORMAL;
        }
    }
}
