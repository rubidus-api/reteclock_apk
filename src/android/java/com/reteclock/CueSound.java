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
 *   <li>Vibrate or silent wins over everything. A sound is a sound, and somebody who asked the timer
 *       not to make any does not want a song instead of a beep.</li>
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
            tones.play(fallback, mode);
            return;
        }
        File file = name == null || name.isEmpty() ? null : Settings.sounds(context).file(name);
        if (file == null) {
            tones.play(fallback, mode);
            return;
        }
        player.play(file, Settings.soundClips(context).of(name));
    }
}
