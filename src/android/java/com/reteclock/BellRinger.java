package com.reteclock;

import android.content.Context;

import com.reteclock.core.Bell;
import com.reteclock.core.Bells;
import com.reteclock.core.Tones;

import java.io.File;
import java.util.List;

/**
 * Rings the bells while the clock is on screen, and stops one when the screen is touched.
 *
 * Asked once a second — by the clock's own tick, which is already running — and answering with what
 * fell since the last time it was asked. That is {@link Bells}'s window, and it is why a late tick
 * cannot lose a bell or ring one twice.
 *
 * <p><b>What this deliberately is not.</b> There is no {@code AlarmManager}, no boot receiver and no
 * service, so a bell rings when the clock is showing and not otherwise. This app is a dock clock
 * that is on screen when it matters, and the alternative is a background alarm with a wake lock, a
 * notification, and a set of failure modes that have to be right at four in the morning. That is a
 * different feature, and it is written down as one.
 *
 * <p>The first tick after the screen appears sets the mark and rings nothing: what fell while the
 * settings were open was missed, and a chime for a moment that has gone is worse than silence.
 */
final class BellRinger {

    private final Context context;
    private final SoundPlayer player = new SoundPlayer();
    private final TimerSounds tones;

    /** The last local minute this looked at; nothing before it will ever be rung. */
    private long lastStamp = Long.MIN_VALUE;
    /** Read once and kept, rather than parsed out of the preferences every second. */
    private Bells bells = Bells.NONE;
    private boolean on;

    BellRinger(Context context) {
        this.context = context.getApplicationContext();
        this.tones = new TimerSounds(context);
        reload();
    }

    /** Reads the bells again — after the settings have been visited, and when the screen returns. */
    void reload() {
        bells = Settings.bells(context);
        on = Settings.bellsOn(context);
        // Whatever fell while somebody was editing the bells is not rung at them on the way back.
        lastStamp = Long.MIN_VALUE;
    }

    /** One second of the clock's own tick. */
    void tick(long nowMs) {
        if (!on || bells.size() == 0) {
            lastStamp = Long.MIN_VALUE;
            return;
        }
        long stamp = Bells.stampOf(nowMs, Settings.offsetMinutes(context, nowMs));
        if (lastStamp == Long.MIN_VALUE) {
            lastStamp = stamp;
            return;
        }
        List<Bell> due = bells.due(lastStamp, stamp);
        lastStamp = stamp;
        if (!due.isEmpty()) {
            // Two bells set to the same minute is somebody's arrangement, not an error, but two
            // sounds at once is a noise. The first one rings.
            ring(due.get(0));
        }
    }

    /**
     * Rings one bell.
     *
     * The chime is the built-in pattern, played the way every other beep in this app is played, and
     * a touch does not fade it: it is a second and a half already handed to the platform and there
     * is nothing left to stop. A bell naming a file goes through the player, which can be faded.
     */
    private void ring(Bell bell) {
        // A bell does not answer to the timer's vibrate or silent setting — it is a different
        // feature, set separately — but it does answer to the phone's own ringer switch.
        if (!PhoneQuiet.soundAllowed(context)) {
            return;
        }
        if (bell.sound.isEmpty()) {
            tones.play(Tones.CHIME, Settings.ALERT_SOUND);
            return;
        }
        File file = Settings.sounds(context).file(bell.sound);
        if (file == null) {
            // The file was deleted after the bell was set. The bell still means something.
            tones.play(Tones.CHIME, Settings.ALERT_SOUND);
            return;
        }
        player.play(file, Settings.soundClips(context).of(bell.sound));
    }

    /** Whether a bell is sounding right now — which is what makes a touch mean "stop". */
    boolean isRinging() {
        return player.isPlaying();
    }

    /**
     * Fades out whatever is ringing, and says whether there was anything to fade.
     *
     * The answer is what the caller uses to decide that the touch has been spent: a touch that
     * silences a bell does nothing else, or somebody reaching to stop a song opens a menu with it.
     */
    boolean silence() {
        if (!player.isPlaying()) {
            return false;
        }
        player.fadeOutAndStop();
        return true;
    }

    /** The screen is going away: stop at once rather than fade into a window nobody is at. */
    void stop() {
        player.stopNow();
        lastStamp = Long.MIN_VALUE;
    }
}
