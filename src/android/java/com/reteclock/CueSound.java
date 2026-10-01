package com.reteclock;

import android.content.Context;

import com.reteclock.core.SoundLevels;
import com.reteclock.core.Tones;

import java.io.File;

/**
 * One cue, made audible: the sound the user chose for it, or the beep the app has always made.
 *
 * Written once and used from both screens that run the timer — the clock and the screensaver —
 * because the rule is a rule about the app, not about a screen:
 *
 * <ul>
 *   <li>The phone's ringer switch picks the tab of the Volume card that applies (R129), and that
 *       tab's *Timer sounds* row says what happens: a sound at its level, a buzz, both or neither.
 *       By default a ringing phone sounds, a vibrating one buzzes and a silent one does nothing.
 *       See {@link SoundLevels}.</li>
 *   <li>A named sound is played when it is still there.</li>
 *   <li>Anything else falls back to the built-in pattern. A cue that went silent because a file had
 *       been deleted would be a timer that quietly stopped working, which is the one failure a timer
 *       must not have. A muted row is not that: it plays nothing, not the beep instead.</li>
 * </ul>
 */
final class CueSound {

    private CueSound() {
    }

    /** A built-in pattern: a tick, the warning before the end, the end itself. */
    static void cue(Context context, TimerSounds tones, Tones.Note[] pattern) {
        SoundLevels levels = Settings.soundLevels(context);
        int mode = Settings.soundMode(context);
        tones.play(pattern, levels.gain(mode, SoundLevels.TIMER_CUES),
                levels.buzzes(mode, SoundLevels.TIMER_CUES));
    }

    static void play(Context context, SoundPlayer player, TimerSounds tones, String name,
            Tones.Note[] fallback) {
        SoundLevels levels = Settings.soundLevels(context);
        int mode = Settings.soundMode(context);
        float gain = levels.gain(mode, SoundLevels.TIMER_CUES);
        boolean buzz = levels.buzzes(mode, SoundLevels.TIMER_CUES);
        File file = gain <= 0f || name == null || name.isEmpty()
                ? null : Settings.sounds(context).file(name);
        if (file == null) {
            tones.play(fallback, gain, buzz);
            return;
        }
        // A file cannot be felt; the buzz is the cue's own pattern, beside the sound.
        tones.play(fallback, 0f, buzz);
        player.setGain(gain);
        player.play(file, Settings.soundClips(context).of(name));
    }

    /**
     * Whether the timer may speak an interval's message: its row on the Volume card, in the tab the
     * phone's switch picks, is not silenced. Speech is a sound like any other.
     */
    static boolean canSpeak(Context context) {
        return Settings.soundGain(context, SoundLevels.TIMER_MESSAGES) > 0f;
    }
}
