package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The voices to choose from, as one list: each language on each installed engine that has it
 * (T113).
 *
 * The settings screen asks every engine which languages it has installed and hands the answers
 * here. What comes back is one row per language per engine — "Korean · Samsung TTS", "English (US)
 * · Google" — so a choice is one tap that sets both, rather than an engine first and then a
 * language that may turn out not to be there.
 *
 * Pure Java: no android.*.
 */
public final class VoiceOptions {

    /** One voice: which engine, what it is called, and the language as a {@link VoiceLocale} tag. */
    public static final class Option {
        public final String engine;
        public final String engineLabel;
        public final String tag;

        Option(String engine, String engineLabel, String tag) {
            this.engine = engine;
            this.engineLabel = engineLabel;
            this.tag = tag;
        }
    }

    private VoiceOptions() {
    }

    /**
     * The rows, by language and then by engine name. Each engine's list is cleaned the way
     * {@link VoiceLocale#offered} cleans it; an engine that said nothing, or could not be asked
     * (null), adds nothing.
     */
    public static List<Option> combine(List<String> engines, List<String> labels,
            List<List<String>> tagsPerEngine) {
        List<Option> out = new ArrayList<Option>();
        int n = Math.min(engines.size(), Math.min(labels.size(), tagsPerEngine.size()));
        for (int i = 0; i < n; i++) {
            List<String> tags = tagsPerEngine.get(i);
            if (tags == null) {
                continue;
            }
            for (String tag : VoiceLocale.offered(tags)) {
                out.add(new Option(engines.get(i), labels.get(i), tag));
            }
        }
        Collections.sort(out, new Comparator<Option>() {
            @Override
            public int compare(Option a, Option b) {
                int c = a.tag.compareTo(b.tag);
                if (c != 0) {
                    return c;
                }
                c = a.engineLabel.compareToIgnoreCase(b.engineLabel);
                return c != 0 ? c : a.engine.compareTo(b.engine);
            }
        });
        return Collections.unmodifiableList(out);
    }

    /**
     * Which row the stored choice is: its index; -1 for nothing chosen (the phone's default voice,
     * the row above the list); -2 for a choice no row carries any more.
     *
     * @param engine        the stored engine, "" for the phone's default
     * @param tag           the stored language tag, "" for the phone's language
     * @param defaultEngine the phone's default engine, which "" stands for
     */
    public static int chosen(List<Option> rows, String engine, String tag, String defaultEngine) {
        String e = engine == null ? "" : engine;
        String t = tag == null ? "" : tag;
        if (e.isEmpty() && t.isEmpty()) {
            return -1;
        }
        String wanted = e.isEmpty() ? (defaultEngine == null ? "" : defaultEngine) : e;
        for (int i = 0; i < rows.size(); i++) {
            Option o = rows.get(i);
            if (o.tag.equals(t) && o.engine.equals(wanted)) {
                return i;
            }
        }
        return -2;
    }
}
