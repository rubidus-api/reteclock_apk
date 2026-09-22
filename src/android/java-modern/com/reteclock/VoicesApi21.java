package com.reteclock;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The voices an engine has installed, on Android 5.0 and up (T113).
 *
 * <p>Compiled against a modern Android, in {@code src/android/java-modern}, and touched only behind a
 * {@code Build.VERSION.SDK_INT >= 21} check: see {@link NativeAnimation}. Below 5.0 an engine can
 * only be asked language by language, and some engines answer "yes" for a language whose voice
 * data they would first have to download. From 5.0 each voice says so itself: a voice marked not
 * installed, or one that needs the network, is left out — a clock speaks where there may be no
 * connection.
 */
final class VoicesApi21 {

    private VoicesApi21() {
    }

    /** The locales of the installed, offline voices; null when the engine gives no list. */
    static List<Locale> installed(TextToSpeech tts) {
        Set<Voice> voices = tts.getVoices();
        if (voices == null) {
            return null;
        }
        List<Locale> out = new ArrayList<Locale>();
        for (Voice voice : voices) {
            if (voice == null || voice.getLocale() == null) {
                continue;
            }
            Set<String> features = voice.getFeatures();
            if (features != null
                    && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                continue;
            }
            if (voice.isNetworkConnectionRequired()) {
                continue;
            }
            out.add(voice.getLocale());
        }
        return out;
    }
}
