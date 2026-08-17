package com.reteclock;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Vibrator;

import com.reteclock.core.Tones;

/**
 * The noises the timer makes, and the buzz that stands in for them.
 *
 * The tones are computed here, a few thousand samples at a time, and handed to an
 * {@link AudioTrack}: no files, no decoder, nothing to ship, and the same result from Android 2.3
 * upwards. It was a square wave at first — one comparison a sample, and the sound of a beeping
 * appliance — but a square wave carries every odd harmonic at full strength, which is exactly what
 * makes a cheap alarm grating. This is a fundamental with a quiet second and third above it and a
 * softened attack: the same notes, played by something one does not mind hearing. The cost is a
 * table lookup a sample, which a 2012 phone can afford for a second of audio on a worker thread.
 *
 * Playing happens on a worker thread. A beep that took its 200 ms on the drawing thread would be
 * 200 ms the clock did not have.
 */
final class TimerSounds {

    private static final int RATE = 22050;
    /** Loud enough to hear across a room, quiet enough not to startle at a bedside. */
    private static final float VOLUME = 0.35f;

    private final Context context;
    private final Vibrator vibrator;

    TimerSounds(Context context) {
        this.context = context.getApplicationContext();
        Vibrator found = null;
        try {
            found = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (RuntimeException e) {
            // A device without a vibrator simply has none; the mode then means silence.
        }
        vibrator = found;
    }

    /**
     * Plays one pattern in whichever way the settings ask for.
     *
     * @param pattern one of the sets in {@link Tones}
     * @param mode {@link Settings#ALERT_SOUND}, {@code ALERT_VIBRATE} or {@code ALERT_SILENT}
     */
    void play(final Tones.Note[] pattern, int mode) {
        if (pattern == null || pattern.length == 0 || mode == Settings.ALERT_SILENT) {
            return;
        }
        if (mode == Settings.ALERT_VIBRATE) {
            // A phone switched to silent is not asking to be buzzed either.
            if (PhoneQuiet.vibrationAllowed(context)) {
                buzz(pattern);
            }
            return;
        }
        // The ringer switch wins over this app's own setting. Sounds go out on the music stream,
        // which the platform does not silence for us, so it is silenced here.
        if (!PhoneQuiet.soundAllowed(context)) {
            return;
        }
        final Tones.Note[] notes = pattern;
        Thread player = new Thread(new Runnable() {
            @Override
            public void run() {
                sound(notes);
            }
        }, "reteclock-tone");
        player.setPriority(Thread.NORM_PRIORITY - 1);
        player.start();
    }

    /** The pattern as a vibration: each note becomes a pulse of its own length. */
    private void buzz(Tones.Note[] pattern) {
        if (vibrator == null) {
            return;
        }
        long[] timings = new long[pattern.length * 2];
        for (int i = 0; i < pattern.length; i++) {
            timings[i * 2] = i == 0 ? 0L : pattern[i - 1].offMs;
            timings[i * 2 + 1] = pattern[i].onMs;
        }
        try {
            vibrator.vibrate(timings, -1);
        } catch (RuntimeException e) {
            // Some devices refuse without the permission they were granted; never worth a crash.
        }
    }

    /** Builds the whole pattern as one buffer and plays it once. */
    private void sound(Tones.Note[] pattern) {
        int frames = 0;
        for (Tones.Note note : pattern) {
            frames += (note.onMs + note.offMs) * RATE / 1000;
        }
        if (frames <= 0) {
            return;
        }
        short[] samples = new short[frames];
        int at = 0;
        for (Tones.Note note : pattern) {
            int on = note.onMs * RATE / 1000;
            int off = note.offMs * RATE / 1000;
            float step = 2f * (float) Math.PI * Math.max(1, note.hz) / RATE;
            float peak = Short.MAX_VALUE * VOLUME;
            for (int i = 0; i < on && at < samples.length; i++, at++) {
                float phase = step * i;
                // The fundamental, with a little of the two harmonics above it: enough to give the
                // note a body, far short of the buzz a square wave has.
                float wave = (float) (Math.sin(phase)
                        + 0.28 * Math.sin(2 * phase)
                        + 0.12 * Math.sin(3 * phase)) / 1.4f;
                samples[at] = (short) (peak * wave * fade(i, on));
            }
            at += off;
        }

        AudioTrack track = null;
        try {
            int minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(minimum, samples.length * 2);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STATIC);
            track.write(samples, 0, samples.length);
            track.play();
            // Static tracks must not be released until they have finished sounding.
            Thread.sleep(Tones.lengthMs(pattern) + 60L);
        } catch (IllegalStateException e) {
            // A device with the audio system busy elsewhere: the beep is lost, nothing else is.
        } catch (IllegalArgumentException e) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (IllegalStateException e) {
                }
                track.release();
            }
        }
    }

    /** A few milliseconds of ramp at each end of a beep, which is what stops the click. */
    /**
     * The shape of one note: a quick rise, and a fall long enough that it ends rather than stops.
     *
     * Without it every note begins and ends with a click, which is the loudest part of a short beep
     * and the reason cheap alarms sound cheap.
     */
    private static float fade(int at, int length) {
        int rise = Math.max(1, Math.min(length / 8, RATE / 500));
        int fall = Math.max(1, Math.min(length / 3, RATE / 40));
        if (at < rise) {
            return (float) at / rise;
        }
        if (at > length - fall) {
            return (float) (length - at) / fall;
        }
        return 1f;
    }
}
