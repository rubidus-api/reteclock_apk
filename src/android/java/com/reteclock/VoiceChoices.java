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

    /** The answer about voices: every language on every installed engine, in one list. */
    interface Catalogue {
        void found(List<com.reteclock.core.VoiceOptions.Option> rows, String defaultEngine);
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
     * Asks every installed engine which languages it has installed, one engine after another on a
     * worker thread, and answers on the main thread with one list. Where engines cannot be named
     * (below API 14) only the phone's default is asked, and its rows carry the engine "".
     */
    static void catalogue(Context context, final Catalogue answer) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> pkgs = new ArrayList<String>();
                List<String> labels = new ArrayList<String>();
                String defaultEngine = "";
                if (enginesChoosable()) {
                    for (Engine e : engines(app)) {
                        pkgs.add(e.pkg);
                        labels.add(e.label);
                    }
                }
                if (pkgs.isEmpty()) {
                    pkgs.add("");
                    labels.add("");
                }
                List<List<String>> tags = new ArrayList<List<String>>();
                for (String pkg : pkgs) {
                    String[] engineInUse = new String[1];
                    tags.add(askEngine(app, pkg, engineInUse));
                    if (defaultEngine.isEmpty() && pkg.isEmpty() && engineInUse[0] != null) {
                        defaultEngine = engineInUse[0];
                    }
                }
                if (defaultEngine.isEmpty()) {
                    defaultEngine = phoneDefaultEngine(app);
                }
                final List<com.reteclock.core.VoiceOptions.Option> rows =
                        com.reteclock.core.VoiceOptions.combine(pkgs, labels, tags);
                final String def = defaultEngine;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        answer.found(rows, def);
                    }
                });
            }
        }).start();
    }

    /** The engine the phone uses when none is named; "" when it will not say. */
    static String phoneDefaultEngine(Context context) {
        try {
            String name = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), "tts_default_synth");
            return name == null ? "" : name;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Starts one engine, waits for it, asks it, and lets it go. Null when it would not start in
     * time. Called on a worker thread; the engine's callback arrives on the main thread.
     */
    private static List<String> askEngine(Context context, String pkg, String[] engineInUse) {
        final java.util.concurrent.CountDownLatch started =
                new java.util.concurrent.CountDownLatch(1);
        final int[] status = {TextToSpeech.ERROR};
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int s) {
                status[0] = s;
                started.countDown();
            }
        };
        TextToSpeech tts;
        try {
            tts = enginesChoosable() && !pkg.isEmpty()
                    ? new TextToSpeech(context, listener, pkg)
                    : new TextToSpeech(context, listener);
        } catch (RuntimeException e) {
            return null;
        }
        List<String> out = null;
        try {
            if (started.await(8, java.util.concurrent.TimeUnit.SECONDS)
                    && status[0] == TextToSpeech.SUCCESS) {
                if (android.os.Build.VERSION.SDK_INT >= 14) {
                    try {
                        engineInUse[0] = tts.getDefaultEngine();
                    } catch (RuntimeException e) {
                        engineInUse[0] = null;
                    }
                }
                out = installed(tts);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                tts.shutdown();
            } catch (RuntimeException e) {
            }
        }
        return out;
    }

    /**
     * The languages this engine has installed. From Android 5.0 its own voices say so, marked when
     * not installed; below that, or when an engine gives no list, each locale is asked in turn.
     */
    private static List<String> installed(TextToSpeech tts) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                List<Locale> locales = VoicesApi21.installed(tts);
                if (locales != null) {
                    List<String> tags = new ArrayList<String>();
                    for (Locale l : locales) {
                        tags.add(VoiceLocale.tag(l.getLanguage(), l.getCountry()));
                    }
                    android.util.Log.d("reteclock", "voices from the engine's own list: "
                            + tags.size());
                    return tags;
                }
            } catch (RuntimeException e) {
                // An engine whose list breaks is asked the old way.
            }
        }
        List<String> asked = ask(tts);
        android.util.Log.d("reteclock", "voices asked locale by locale: " + asked.size());
        return asked;
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
