package com.reteclock;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.reteclock.core.SoundClip;

import java.io.File;
import java.io.IOException;

/**
 * Plays one imported sound: the stretch of it that was asked for, once or over and over, and stops
 * it kindly when somebody touches the screen.
 *
 * One player, one sound. A bell that rings while another is ringing replaces it, and the preview on
 * the settings screen replaces whatever the last press started. Two sounds at once on a phone this
 * app is built for is a stutter, and nothing here is worth a stutter.
 *
 * <p><b>Preparing is asynchronous.</b> {@link MediaPlayer#prepare} reads and decodes a header, and
 * on a 2012 phone with a file off a slow card that is long enough to lose a frame of the clock.
 * {@code prepareAsync} hands that to the platform's own thread and the sound starts on the callback.
 * The one place a synchronous prepare is used is {@link #playable}, which runs at import — a moment
 * where the user is already waiting for a file to be copied.
 *
 * <p><b>Stopping is a fade, not a cut.</b> A bell cut off mid-sample clicks, and the click is louder
 * than the bell. The touch that stops one therefore ramps the volume down over
 * {@link #FADE_MS} and releases at the bottom.
 *
 * <p>Everything here runs on the thread that made it, which is the main one: {@code MediaPlayer}'s
 * callbacks arrive on the Looper that created it, and the fade is a Handler.
 */
final class SoundPlayer {

    private static final String TAG = "reteclock";

    /** How long the fade a touch starts takes, and the step it takes it in. */
    static final int FADE_MS = 600;
    private static final int FADE_STEP_MS = 40;

    /** The volume a sound plays at, matching the beeps the timer already makes. */
    private static final float VOLUME = 0.9f;

    private static final int MSG_FADE = 1;
    private static final int MSG_END_POINT = 2;

    private MediaPlayer player;
    private SoundClip playing;
    /**
     * How many more times the current sound is to be played (issue: a short chime asked for three
     * times over). One is the ordinary case; stopping — a touch on the screen — throws away
     * whatever is left, which is what "cancel the rest of it" means.
     */
    private int playsLeft = 1;
    private float volume = VOLUME;

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            if (message.what == MSG_FADE) {
                stepFade();
            } else if (message.what == MSG_END_POINT) {
                reachedEndPoint();
            }
        }
    };

    /** Whether a sound is sounding right now — what the screen asks before offering to stop it. */
    boolean isPlaying() {
        return player != null;
    }

    /**
     * Plays this file as the clip describes it, in place of whatever was playing.
     *
     * A file that is missing or that this device cannot decode simply does not play: the caller has
     * already decided a sound is better than a beep here, and a crash is not the other option.
     */
    void play(File file, SoundClip clip) {
        play(file, clip, 1);
    }

    /**
     * Plays this file {@code times} over — the clip, then the clip again, up to the count.
     *
     * A clip that was set to repeat for ever still does: that is what the file was asked to do, and
     * a count cannot mean anything against it. Everything else plays exactly the number of times
     * asked for and then stops itself.
     */
    void play(File file, SoundClip clip, int times) {
        stopNow();
        playsLeft = times < 1 ? 1 : times;
        if (file == null || !file.isFile()) {
            return;
        }
        final SoundClip wanted = clip == null ? SoundClip.whole(file.getName()) : clip;
        MediaPlayer created = new MediaPlayer();
        try {
            created.setAudioStreamType(AudioManager.STREAM_MUSIC);
            created.setDataSource(file.getAbsolutePath());
            created.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer prepared) {
                    begin(prepared, wanted);
                }
            });
            created.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer done) {
                    finished(done, wanted);
                }
            });
            created.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer failed, int what, int extra) {
                    Log.w(TAG, "sound failed: " + what + "/" + extra);
                    stopNow();
                    return true;
                }
            });
            player = created;
            playing = wanted;
            volume = VOLUME;
            created.setVolume(volume, volume);
            created.prepareAsync();
        } catch (IOException e) {
            Log.w(TAG, "cannot open sound " + file.getName());
            release(created);
        } catch (IllegalArgumentException e) {
            release(created);
        } catch (IllegalStateException e) {
            release(created);
        } catch (SecurityException e) {
            release(created);
        }
    }

    private void begin(MediaPlayer prepared, SoundClip clip) {
        if (prepared != player) {
            return;                     // a later press already replaced this one
        }
        try {
            if (clip.startMs() > 0) {
                prepared.seekTo((int) clip.startMs());
            }
            // Looping is the platform's own when the clip runs to the end of the file; a clip with
            // an end point of its own is looped by the message below, which is the only way to
            // repeat a stretch rather than a file.
            prepared.setLooping(clip.loops && clip.endMs() < 0);
            prepared.start();
            scheduleEndPoint(clip);
        } catch (IllegalStateException e) {
            stopNow();
        }
    }

    /** When the clip has an end of its own, come back to it. */
    private void scheduleEndPoint(SoundClip clip) {
        handler.removeMessages(MSG_END_POINT);
        long length = clip.lengthMs();
        if (length > 0) {
            handler.sendEmptyMessageDelayed(MSG_END_POINT, length);
        }
    }

    private void reachedEndPoint() {
        if (player == null || playing == null) {
            return;
        }
        if (!playing.loops && playsLeft <= 1) {
            stopNow();
            return;
        }
        if (!playing.loops) {
            playsLeft--;
        }
        try {
            player.seekTo((int) playing.startMs());
            scheduleEndPoint(playing);
        } catch (IllegalStateException e) {
            stopNow();
        }
    }

    private void finished(MediaPlayer done, SoundClip clip) {
        if (done != player) {
            return;
        }
        // A file shorter than the clip's start, or one that simply ended: repeat if that is what was
        // asked for, otherwise let it go.
        if (clip.loops || playsLeft > 1) {
            try {
                if (!clip.loops) {
                    playsLeft--;
                }
                done.seekTo((int) clip.startMs());
                done.start();
                scheduleEndPoint(clip);
                return;
            } catch (IllegalStateException e) {
                // fall through to stopping
            }
        }
        stopNow();
    }

    /**
     * Fades what is playing down to nothing and lets it go.
     *
     * This is what a touch on the clock does. It is deliberately not a pause: a bell has no state
     * worth keeping, and the next one starts from its own beginning.
     */
    void fadeOutAndStop() {
        if (player == null) {
            return;
        }
        handler.removeMessages(MSG_FADE);
        handler.sendEmptyMessage(MSG_FADE);
    }

    private void stepFade() {
        if (player == null) {
            return;
        }
        volume -= VOLUME * FADE_STEP_MS / FADE_MS;
        if (volume <= 0f) {
            stopNow();
            return;
        }
        try {
            player.setVolume(volume, volume);
        } catch (IllegalStateException e) {
            stopNow();
            return;
        }
        handler.sendEmptyMessageDelayed(MSG_FADE, FADE_STEP_MS);
    }

    /** Stops at once, with no fade: what a screen being left behind does. */
    void stopNow() {
        handler.removeMessages(MSG_FADE);
        handler.removeMessages(MSG_END_POINT);
        MediaPlayer going = player;
        player = null;
        playing = null;
        playsLeft = 1;
        volume = VOLUME;
        release(going);
    }

    private static void release(MediaPlayer going) {
        if (going == null) {
            return;
        }
        try {
            going.stop();
        } catch (IllegalStateException e) {
            // Never prepared, or already stopped; releasing is still the right next step.
        }
        try {
            going.reset();
        } catch (IllegalStateException e) {
        }
        going.release();
    }

    /**
     * Whether this device's own decoder accepts the file — the only honest test there is.
     *
     * An extension is a guess and a header is a claim. This opens the file the way the app will
     * open it when a bell rings, and keeps the answer. Synchronous on purpose: it is called while a
     * file is being imported, where the user is waiting anyway, and where the alternative is finding
     * out at four in the morning that the alarm was a picture somebody renamed.
     */
    static boolean playable(File file) {
        return durationMs(file) != DURATION_UNPLAYABLE;
    }

    /** What {@link #durationMs} answers for a file this device cannot play at all. */
    static final long DURATION_UNPLAYABLE = -1L;
    /** And for one it can play but will not say the length of, which is legal and does happen. */
    static final long DURATION_UNKNOWN = 0L;

    /**
     * How long the file is, for showing beside the clip's start and end points.
     *
     * {@link #DURATION_UNPLAYABLE} when the decoder refused it, {@link #DURATION_UNKNOWN} when it
     * took the file but would not say how long it is — a stream, or a format whose length is not in
     * its header.
     */
    static long durationMs(File file) {
        if (file == null || !file.isFile() || file.length() == 0) {
            return DURATION_UNPLAYABLE;
        }
        MediaPlayer probe = new MediaPlayer();
        try {
            probe.setDataSource(file.getAbsolutePath());
            probe.prepare();
            int length = probe.getDuration();
            return length > 0 ? length : DURATION_UNKNOWN;
        } catch (IOException e) {
            return DURATION_UNPLAYABLE;
        } catch (IllegalArgumentException e) {
            return DURATION_UNPLAYABLE;
        } catch (IllegalStateException e) {
            return DURATION_UNPLAYABLE;
        } catch (SecurityException e) {
            return DURATION_UNPLAYABLE;
        } finally {
            release(probe);
        }
    }
}
