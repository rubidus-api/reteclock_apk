package com.reteclock;

import android.content.Context;

import com.reteclock.core.Bell;
import com.reteclock.core.Bells;
import com.reteclock.core.Snooze;
import com.reteclock.core.Tones;

import android.media.AudioManager;
import android.os.SystemClock;

import com.reteclock.core.WakeLog;
import com.reteclock.core.WakePutOff;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
 * <p><b>A bell can now be put off</b> ({@link com.reteclock.core.Snooze}), and that promise is kept
 * the only way this design can keep one: on the same tick, in the same window. It therefore lives
 * exactly as long as the screen does — {@link #stop()} drops it — which is the honest scope of a
 * clock that is not running when nobody is looking at it. Only a caller that can show the card sets
 * a {@link Ringing}; the screensaver sets none and rings as it always did, because Android does not
 * hand a Daydream its touches and a card there could not be pressed.
 *
 * <p>The first tick after the screen appears sets the mark and rings nothing: what fell while the
 * settings were open was missed, and a chime for a moment that has gone is worse than silence.
 */
final class BellRinger {

    private final Context context;
    private final SoundPlayer player = new SoundPlayer();
    private final TimerSounds tones;

    /** The silence between one pass of the built-in chime and the next. */
    private static final int CHIME_GAP_MS = 350;

    /** The last local minute this looked at; nothing before it will ever be rung. */
    private long lastStamp = Long.MIN_VALUE;
    /** Read once and kept, rather than parsed out of the preferences every second. */
    private Bells bells = Bells.NONE;
    private boolean on;
    /** Asked, before every ring, whether a timer is counting; a timer has the right of way. */
    private Busy busy;
    /** The one bell that has been put off, or null. One, because a card answers one bell. */
    private Snooze snooze;
    /** Told when a bell that can be put off is sounding, so a card can be shown. May be null. */
    private Ringing ringing;

    // ---- bells that wake the phone (RFC-0012) -------------------------------------------------
    //
    // Everything below is idle unless *Alarms (experimental)* is switched on. It is an extension laid
    // over the bells above, and it is the side that gives way to them.

    /** Every ringer alive in this process, so a wake ring can ask whether any of them is sounding. */
    private static final List<WeakReference<BellRinger>> LIVE =
            new ArrayList<WeakReference<BellRinger>>();

    /** Read with the bells: whether the tick leaves wake bells to the system. */
    private boolean wakeOn;
    /** A wake bell handed to this screen, waiting for a bell of its own to finish. */
    private Bell pendingWake;
    private long pendingDue;
    private long pendingSince;
    /** The wake bell this screen is ringing now, or null. */
    private Bell wakeBell;
    private long wakeDue;
    /** Told when a wake bell starts ringing here, so its card can be shown. */
    private WakeRinging wakeRinging;

    /** What the clock screen shows for a wake bell: the card, answered through {@link #endWake}. */
    interface WakeRinging {
        void wakeIsRinging(Bell bell);
    }

    void setWakeRinging(WakeRinging wakeRinging) {
        this.wakeRinging = wakeRinging;
    }

    /** Whether one of this process's ringers is sounding one of the clock's own bells. */
    static boolean anySounding() {
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            BellRinger ringer = LIVE.get(i).get();
            if (ringer == null) {
                LIVE.remove(i);
            } else if (ringer.wakeBell == null && ringer.player.isPlaying()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A wake bell has arrived while this screen is showing: this screen rings it.
     *
     * If one of the clock's own bells is sounding, the wake bell waits for it to end — for up to a
     * minute — rather than cutting it off. The tick below asks again every second.
     */
    void takeWake(Bell bell, long due) {
        pendingWake = bell;
        pendingDue = due;
        pendingSince = SystemClock.uptimeMillis();
        serveWake();
    }

    /** Whether a wake bell is ringing on this screen — what the timer's cues are silent for (F4). */
    boolean wakeActive() {
        return wakeBell != null;
    }

    private void serveWake() {
        if (pendingWake == null || wakeBell != null) {
            return;
        }
        if (player.isPlaying()
                && SystemClock.uptimeMillis() - pendingSince < WakeBells.WAIT_FOR_BELL_MS) {
            return;
        }
        Bell bell = pendingWake;
        pendingWake = null;
        player.stopNow();
        wakeBell = bell;
        wakeDue = pendingDue;
        WakeBells.record(context, WakeLog.RANG, wakeDue, bell.label);
        if (bell.sound.isEmpty()) {
            tones.playAlarm(Tones.repeated(Tones.CHIME, bell.repeats, CHIME_GAP_MS));
        } else {
            File file = Settings.sounds(context).file(bell.sound);
            if (file == null) {
                tones.playAlarm(Tones.CHIME);
            } else {
                player.setStream(AudioManager.STREAM_ALARM);
                player.play(file, Settings.soundClips(context).of(bell.sound), bell.repeats);
            }
        }
        if (wakeRinging != null) {
            wakeRinging.wakeIsRinging(bell);
        }
    }

    /** The wake bell on this screen has been answered, one way or another. */
    void endWake(String kind) {
        if (wakeBell == null) {
            return;
        }
        Bell answered = wakeBell;
        wakeBell = null;
        player.stopNow();
        player.setStream(AudioManager.STREAM_MUSIC);
        WakeBells.record(context, kind, wakeDue, answered.label);
        if (WakeLog.PUT_OFF.equals(kind)) {
            Settings.setWakePutOff(context, WakePutOff.of(answered, System.currentTimeMillis()));
        }
        WakeBells.rearm(context);
    }

    /** What the screen knows and the bells do not: whether the timer is running just now. */
    interface Busy {
        boolean timerIsRunning();
    }

    void setBusy(Busy busy) {
        this.busy = busy;
    }

    /** What the screen offers that a sound cannot: the two answers to a bell. */
    interface Ringing {
        /** A bell that can be put off is sounding now. */
        void bellIsRinging(Bell bell);
    }

    /**
     * Sets who is told about a ringing bell — the clock screen, and nobody else.
     *
     * A caller that sets none gets what this always did: the bell sounds and a touch stops it.
     */
    void setRinging(Ringing ringing) {
        this.ringing = ringing;
    }

    /**
     * Rings this bell again in its own number of minutes, counted from now.
     *
     * Asking twice does not queue two rings: the one promise moves. It is dropped by {@link #stop()}
     * and by {@link #reload()}, because a promise the clock cannot keep should not outlive the
     * screen that made it.
     */
    void putOff(Bell bell, long nowMs) {
        silence();
        snooze = Snooze.of(bell, stampNow(nowMs));
    }

    /**
     * That is the end of this bell: the sound stops, and what it was owed is dropped.
     *
     * <p>Only what <em>it</em> was owed. Two bells can be in play at once — one put off at seven,
     * another ringing at five past — and stopping the second must not quietly cancel the first.
     * The promise is named after the bell that made it, and only that bell can end it.
     */
    void stopRinging(Bell bell) {
        silence();
        if (snooze != null && bell != null && snooze.bell == bell) {
            snooze = null;
        }
    }

    /** Whether a bell is waiting to ring again — what the screen shows a mark for. */
    boolean hasSnooze() {
        return snooze != null;
    }

    BellRinger(Context context) {
        this.context = context.getApplicationContext();
        this.tones = new TimerSounds(context);
        LIVE.add(new WeakReference<BellRinger>(this));
        reload();
    }

    /** Reads the bells again — after the settings have been visited, and when the screen returns. */
    void reload() {
        bells = Settings.bells(context);
        on = Settings.bellsOn(context);
        wakeOn = Settings.wakeOn(context);
        // Whatever fell while somebody was editing the bells is not rung at them on the way back.
        lastStamp = Long.MIN_VALUE;
        // The bell that was put off may not exist any more, and its minutes may have changed. The
        // promise was made about a bell as it was; it is dropped rather than guessed at.
        snooze = null;
    }

    /** One second of the clock's own tick. */
    void tick(long nowMs) {
        // A wake bell handed to this screen is served first, whatever the bells below are doing.
        serveWake();
        if (!on || bells.size() == 0) {
            lastStamp = Long.MIN_VALUE;
            snooze = null;
            return;
        }
        long stamp = Bells.stampOf(nowMs, Settings.offsetMinutes(context, nowMs));
        if (lastStamp == Long.MIN_VALUE) {
            lastStamp = stamp;
            return;
        }
        // With the experiment on, a bell that wakes the phone is the system's to ring, not the tick's.
        List<Bell> due = bells.due(lastStamp, stamp, wakeOn);
        // A bell that was put off is due in the same window, judged by the same arithmetic. It is
        // taken first: it is the one the person in the room has already been asked about once.
        Snooze waiting = snooze;
        boolean putOffIsDue = waiting != null && waiting.isDue(lastStamp, stamp);
        lastStamp = stamp;
        // A timer counting on the same screen wins. Two sounds at once is a noise, and of the two
        // the timer is the one somebody is waiting on — they started it a minute ago and are
        // listening for its end. The bell is not delayed until the timer is done: it is a chime at
        // a moment, and the moment passes, the same way one missed while the clock was off screen
        // does.
        // A wake bell ringing — here or in the service — is the same kind of right of way: the
        // moment passes for a bell that merely fell now, and a put-off is kept for the next quiet
        // second, exactly as for a timer.
        if ((busy != null && busy.timerIsRunning()) || wakeBell != null || pendingWake != null
                || WakeRingService.isRinging()) {
            // A bell that merely fell now is passed over — the moment has gone. A put-off is not:
            // somebody was asked and answered "ring again", and dropping that silently would be
            // the app breaking a promise it made on screen a few minutes ago. It is kept, and the
            // next quiet second rings it — within the catch-up window, which is where every other
            // late ring in this app also gives up.
            return;
        }
        if (putOffIsDue) {
            snooze = null;
            ring(waiting.bell);
            return;
        }
        if (!due.isEmpty()) {
            // Two bells set to the same minute is somebody's arrangement, not an error, but two
            // sounds at once is a noise. The first one rings.
            ring(due.get(0));
        }
    }

    /** Where the bells count time: local, at the app's own offset, summer time folded in. */
    private long stampNow(long nowMs) {
        return Bells.stampOf(nowMs, Settings.offsetMinutes(context, nowMs));
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
        // Asked alongside the sound, and only once the phone has been found willing to make one:
        // the card is an answer to a noise, so it must not appear on a silenced phone.
        if (ringing != null && bell.canSnooze()) {
            ringing.bellIsRinging(bell);
        }
        if (bell.sound.isEmpty()) {
            // The chime is repeated as one pattern rather than as several sounds started in turn:
            // the spacing is then exact, and one stop stops all of it.
            tones.play(Tones.repeated(Tones.CHIME, bell.repeats, CHIME_GAP_MS),
                    Settings.ALERT_SOUND);
            return;
        }
        File file = Settings.sounds(context).file(bell.sound);
        if (file == null) {
            // The file was deleted after the bell was set. The bell still means something.
            tones.play(Tones.CHIME, Settings.ALERT_SOUND);
            return;
        }
        player.setStream(AudioManager.STREAM_MUSIC);
        player.play(file, Settings.soundClips(context).of(bell.sound), bell.repeats);
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

    /**
     * The screen is going away: stop at once rather than fade into a window nobody is at.
     *
     * The put-off goes too. Nothing outside this tick can deliver it, so keeping it would be
     * keeping a promise the app has no way to honour — and a bell that rang half an hour later,
     * once the clock happened to come back, would be worse than one that did not ring at all.
     */
    void stop() {
        // A wake bell ringing on a screen that is going away has been left: that is its answer.
        // One still waiting for a bell to finish goes to the service, which rings it without a
        // screen.
        if (wakeBell != null) {
            endWake(WakeLog.STOPPED);
        }
        if (pendingWake != null) {
            WakeRingService.startWith(context, pendingWake, pendingDue);
            pendingWake = null;
        }
        player.stopNow();
        lastStamp = Long.MIN_VALUE;
        snooze = null;
    }
}
