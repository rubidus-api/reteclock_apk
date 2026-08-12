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
 * upwards. A square wave rather than a sine, because it is what a beeping appliance sounds like and
 * because it costs one comparison a sample rather than a call into the maths library — this runs on
 * phones where that difference is visible.
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
            buzz(pattern);
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
            int period = Math.max(1, RATE / Math.max(1, note.hz));
            short high = (short) (Short.MAX_VALUE * VOLUME);
            for (int i = 0; i < on && at < samples.length; i++, at++) {
                // A square wave, softened at both ends so it does not click.
                short value = (i % period) * 2 < period ? high : (short) -high;
                samples[at] = (short) (value * fade(i, on));
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
    private static float fade(int at, int length) {
        int ramp = Math.min(length / 4, RATE / 400);
        if (ramp <= 0) {
            return 1f;
        }
        if (at < ramp) {
            return (float) at / ramp;
        }
        if (at > length - ramp) {
            return (float) (length - at) / ramp;
        }
        return 1f;
    }
}
