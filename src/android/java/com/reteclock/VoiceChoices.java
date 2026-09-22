package com.reteclock;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import com.reteclock.core.VoiceLocale;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the settings screen offers for speech: the engines installed on this phone, and the
 * languages the chosen one can actually speak (T111).
 *
 * The engines come from the package manager, so the list is there the moment the page opens and
 * needs no engine started. The languages have to be asked of the engine itself: it is started with
 * the chosen engine, every locale the platform knows is put to it on a worker thread, and only the
 * ones it answers for with its voice data present are offered. On an old phone that is a few
 * hundred quick questions — a second or two — which is why the page shows that it is asking.
 */
final class VoiceChoices {

    /** One installed engine: its package, which is what is stored, and its name, which is shown. */
    static final class Engine {
        final String pkg;
        final String label;

        Engine(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    /** The answer about languages: the tags, or null when the engine would not start. */
    interface Languages {
        void found(List<String> tags);
    }

    private VoiceChoices() {
    }

    /** Whether an engine can be named at all: the constructor that takes one is API 14. */
    static boolean enginesChoosable() {
        return android.os.Build.VERSION.SDK_INT >= 14;
    }

    /** The speech engines installed on this phone, by name. */
    static List<Engine> engines(Context context) {
        List<Engine> out = new ArrayList<Engine>();
        try {
            PackageManager pm = context.getPackageManager();
            // The action's constant is API 14; the action itself is as old as speech on Android.
            List<ResolveInfo> found = pm.queryIntentServices(
                    new Intent("android.intent.action.TTS_SERVICE"), 0);
            for (ResolveInfo info : found) {
                if (info.serviceInfo == null) {
                    continue;
                }
                String pkg = info.serviceInfo.packageName;
                boolean seen = false;
                for (Engine e : out) {
                    seen |= e.pkg.equals(pkg);
                }
                if (!seen) {
                    CharSequence label = info.loadLabel(pm);
                    out.add(new Engine(pkg, label == null ? pkg : label.toString()));
                }
            }
        } catch (RuntimeException e) {
            // No list is the phone's default, which is still a choice.
        }
        return out;
    }

    /** The name of an engine by its package, or the package itself when it has gone. */
    static String engineLabel(Context context, String pkg) {
        for (Engine e : engines(context)) {
            if (e.pkg.equals(pkg)) {
                return e.label;
            }
        }
        return pkg;
    }

    /** A tag as the phone would name it in its own language: "Korean (South Korea)". */
    static String languageLabel(String tag) {
        List<String> parts = VoiceLocale.parse(tag);
        if (parts == null) {
            return tag;
        }
        String name = new Locale(parts.get(0), parts.get(1)).getDisplayName();
        return name == null || name.isEmpty() ? tag : name + "  ·  " + tag;
    }

    /**
     * Asks the engine which languages it can speak, and answers on the main thread. The engine is
     * started for this alone and let go afterwards.
     */
    static void languages(Context context, String engine, final Languages answer) {
        final Handler main = new Handler(Looper.getMainLooper());
        final TextToSpeech[] box = new TextToSpeech[1];
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(final int status) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        List<String> tags = null;
                        TextToSpeech tts = box[0];
                        // The constructor may call back before it has returned; wait for it.
                        for (int i = 0; tts == null && i < 50; i++) {
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException e) {
                                break;
                            }
                            tts = box[0];
                        }
                        if (status == TextToSpeech.SUCCESS && tts != null) {
                            tags = ask(tts);
                        }
                        if (tts != null) {
                            try {
                                tts.shutdown();
                            } catch (RuntimeException e) {
                            }
                        }
                        final List<String> found = tags;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                answer.found(found);
                            }
                        });
                    }
                }).start();
            }
        };
        try {
            if (enginesChoosable() && engine != null && !engine.isEmpty()) {
                box[0] = new TextToSpeech(context.getApplicationContext(), listener, engine);
            } else {
                box[0] = new TextToSpeech(context.getApplicationContext(), listener);
            }
        } catch (RuntimeException e) {
            answer.found(null);
        }
    }

    /** Every locale the platform knows, put to the engine; the ones it can speak, as tags. */
    private static List<String> ask(TextToSpeech tts) {
        List<String> tags = new ArrayList<String>();
        java.util.Set<String> asked = new java.util.HashSet<String>();
        for (Locale l : Locale.getAvailableLocales()) {
            String tag = VoiceLocale.tag(l.getLanguage(), l.getCountry());
            if (tag.isEmpty() || !asked.add(tag) || VoiceLocale.parse(tag) == null) {
                continue;
            }
            try {
                int r = tts.isLanguageAvailable(new Locale(l.getLanguage(), l.getCountry()));
                // A country is offered only when the engine has that country's voice, not merely
                // the language; a bare language when it has the language at all. Missing voice
                // data counts as not there: the phone would say nothing.
                boolean ok = l.getCountry().isEmpty()
                        ? r >= TextToSpeech.LANG_AVAILABLE
                        : r >= TextToSpeech.LANG_COUNTRY_AVAILABLE;
                if (ok) {
                    tags.add(tag);
                }
            } catch (RuntimeException e) {
                // One locale an engine chokes on is one locale fewer, not no list.
            }
        }
        return VoiceLocale.offered(tags);
    }
}
