package com.reteclock;

import android.content.Context;

import com.reteclock.core.Tones;

import java.io.File;

/**
 * One cue, made audible: the sound the user chose for it, or the beep the app has always made.
 *
 * Written once and used from both screens that run the timer — the clock and the screensaver —
 * because the rule is a rule about the app, not about a screen:
 *
 * <ul>
 *   <li>The phone's own ringer switch wins over everything. On silent, nothing at all — not a
 *       sound, not a buzz; on vibrate, no sound. See {@link PhoneQuiet}.</li>
 *   <li>Then the timer's own vibrate or silent setting: a sound is a sound, and somebody who asked
 *       the timer not to make any does not want a song instead of a beep. Nothing is spoken in
 *       those modes either — see {@link #canSpeak}.</li>
 *   <li>A named sound is played when it is still there.</li>
 *   <li>Anything else falls back to the built-in pattern. A cue that went silent because a file had
 *       been deleted would be a timer that quietly stopped working, which is the one failure a timer
 *       must not have.</li>
 * </ul>
 */
final class CueSound {

    private CueSound() {
    }

    static void play(Context context, SoundPlayer player, TimerSounds tones, String name,
            Tones.Note[] fallback) {
        int mode = Settings.timerAlert(context);
        if (mode != Settings.ALERT_SOUND) {
            // Vibrate buzzes the built-in pattern; silent does nothing. Either way the sound the
            // user chose is not played: they asked this timer not to make any.
            tones.play(fallback, mode);
            return;
        }
        if (!PhoneQuiet.soundAllowed(context)) {
            return;
        }
        File file = name == null || name.isEmpty() ? null : Settings.sounds(context).file(name);
        if (file == null) {
            tones.play(fallback, mode);
            return;
        }
        player.play(file, Settings.soundClips(context).of(name));
    }

    /**
     * Whether the timer may speak an interval's message.
     *
     * The same rule as the sounds, said once rather than at each screen that runs the timer: the
     * timer set to vibrate or to silent does not talk, and neither does a phone whose ringer is
     * switched off. Speech is a sound like any other.
     */
    static boolean canSpeak(Context context) {
        return Settings.timerAlert(context) == Settings.ALERT_SOUND
                && PhoneQuiet.soundAllowed(context);
    }
}
