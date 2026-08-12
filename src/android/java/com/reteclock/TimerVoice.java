package com.reteclock;

import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;

import com.reteclock.core.VoiceState;

/**
 * The interval's message, spoken.
 *
 * Speech is optional in every sense. The engine is only created when a preset actually has
 * something to say and the timer is in its sound mode; a device with no engine loses the messages
 * and nothing else; and because initialising takes a moment, a message asked for before the engine
 * is ready waits for it — but only briefly, since a message for an interval that has already ended
 * would be spoken over the next one.
 *
 * Three ways for an old phone to have no speech, and the awkward one is an engine with no voice
 * data: it starts, it accepts the text, and nothing is said. The language is therefore checked as
 * well as the initialisation, and what is learned is written down so the settings screen can tell
 * the user rather than leaving them to wonder. See {@link VoiceState}.
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
                        Settings.rememberVoice(context, VoiceState.INIT_FAILED,
                                VoiceState.LANG_UNKNOWN);
                        return;
                    }
                    // An engine can start and still have nothing to say it with; asking the
                    // language is the only way to tell that apart from working speech.
                    int language = language();
                    Settings.rememberVoice(context, VoiceState.INIT_OK, language);
                    if (language != VoiceState.LANG_OK) {
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

    /** What the engine says about the language it would speak in. */
    private int language() {
        try {
            int result = engine.setLanguage(java.util.Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA) {
                return VoiceState.LANG_MISSING;
            }
            if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return VoiceState.LANG_UNSUPPORTED;
            }
            return VoiceState.LANG_OK;
        } catch (RuntimeException e) {
            return VoiceState.LANG_UNSUPPORTED;
        }
    }

    /**
     * Whether any speech engine is installed at all, asked of the package manager so the answer
     * needs no engine to be started and is available the moment the settings screen opens.
     */
    static boolean engineInstalled(Context context) {
        try {
            // The action's constant is API 14; the action itself is as old as speech on Android.
            Intent intent = new Intent("android.intent.action.TTS_SERVICE");
            return !context.getPackageManager().queryIntentServices(intent, 0).isEmpty();
        } catch (RuntimeException e) {
            return false;
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
