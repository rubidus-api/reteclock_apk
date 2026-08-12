package com.reteclock;

import android.content.Context;
import android.speech.tts.TextToSpeech;

/**
 * The interval's message, spoken.
 *
 * Speech is optional in every sense. The engine is only created when a preset actually has
 * something to say and the timer is in its sound mode; a device with no engine loses the messages
 * and nothing else; and because initialising takes a moment, a message asked for before the engine
 * is ready waits for it — but only briefly, since a message for an interval that has already ended
 * would be spoken over the next one.
 */
final class TimerVoice {

    /** After this long, a message that could not be spoken is dropped rather than queued. */
    private static final long STALE_MS = 5000L;

    private final Context context;
    private TextToSpeech engine;
    private boolean ready;
    private boolean broken;
    /** What was asked for before the engine was ready, and when. */
    private String pending;
    private long pendingAtMs;

    TimerVoice(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Says this, when it can. Empty says nothing and starts no engine. */
    void say(String text, long nowMs) {
        if (text == null || text.isEmpty() || broken) {
            return;
        }
        if (ready) {
            speak(text);
            return;
        }
        pending = text;
        pendingAtMs = nowMs;
        start();
    }

    private void start() {
        if (engine != null) {
            return;
        }
        try {
            engine = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status != TextToSpeech.SUCCESS) {
                        broken = true;
                        return;
                    }
                    ready = true;
                    String waiting = pending;
                    pending = null;
                    if (waiting != null
                            && android.os.SystemClock.elapsedRealtime() - pendingAtMs < STALE_MS) {
                        speak(waiting);
                    }
                }
            });
        } catch (RuntimeException e) {
            broken = true;
        }
    }

    @SuppressWarnings("deprecation")
    private void speak(String text) {
        try {
            // The two-argument form is what exists from API 4; its replacement arrived at API 21
            // and this app compiles against 19.
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        } catch (RuntimeException e) {
            broken = true;
        }
    }

    /** Lets the engine go; the timer does this when it stops. */
    void release() {
        if (engine == null) {
            return;
        }
        try {
            engine.stop();
            engine.shutdown();
        } catch (RuntimeException e) {
        }
        engine = null;
        ready = false;
        pending = null;
    }
}
