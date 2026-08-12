package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import com.reteclock.core.SettingsText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Carrying the settings off this phone and back onto another.
 *
 * The format and the judgement of what travels live in {@link SettingsText}, where they can be
 * tested; this is the part that has to touch `SharedPreferences` and the file store, and it is kept
 * as thin as that division allows.
 */
final class SettingsPortability {

    /** Settings whose value is a list of image or font names, one per line. */
    private static final String[] NAME_LISTS = {
        Settings.KEY_POOL_BACKGROUND, Settings.KEY_POOL_TEXT, Settings.KEY_BACKGROUND_ORDER,
    };

    /** What came of an import, so the user can be told rather than left guessing. */
    static final class Result {
        final int applied;
        final int droppedNames;
        final boolean readable;

        Result(int applied, int droppedNames, boolean readable) {
            this.applied = applied;
            this.droppedNames = droppedNames;
            this.readable = readable;
        }
    }

    private SettingsPortability() {
    }

    /**
     * Every setting worth carrying, as text.
     *
     * Sorted by name so that two exports of the same arrangement are the same file — which is what
     * makes it possible to see, by comparing them, what actually changed.
     */
    static String export(Context context) {
        Map<String, ?> all = new TreeMap<String, Object>(Settings.all(context));
        List<SettingsText.Entry> entries = new ArrayList<SettingsText.Entry>();
        for (Map.Entry<String, ?> setting : all.entrySet()) {
            Object value = setting.getValue();
            char type = value instanceof Boolean ? 'b'
                    : value instanceof Integer ? 'i'
                    : value instanceof Long ? 'l'
                    : value instanceof String ? 's'
                    : 0;
            if (type == 0) {
                // A set of strings, or something a later version stores; not carried rather than
                // carried wrongly.
                continue;
            }
            entries.add(new SettingsText.Entry(setting.getKey(), type, String.valueOf(value)));
        }
        return SettingsText.write(entries);
    }

    /**
     * Applies an exported arrangement to this phone.
     *
     * Names of images and fonts that did not come with it are dropped — see
     * {@link SettingsText#keepPresent} — so the clock is never left pointing at a file that is not
     * there. Everything else is written as it stands.
     */
    static Result importFrom(Context context, String text) {
        List<SettingsText.Entry> entries = SettingsText.read(text);
        if (entries.isEmpty()) {
            return new Result(0, 0, SettingsText.looksLikeSettings(text));
        }

        List<String> images = namesOf(Settings.images(context));
        List<String> fonts = namesOf(Settings.fonts(context));

        SharedPreferences.Editor editor = Settings.edit(context);
        int applied = 0;
        int dropped = 0;
        for (SettingsText.Entry entry : entries) {
            String value = entry.value;
            if (entry.type == 's' && isNameList(entry.key)) {
                List<String> stored = lines(value);
                List<String> kept = SettingsText.keepPresent(stored, images);
                dropped += stored.size() - kept.size();
                value = join(kept);
            } else if (entry.type == 's' && Settings.KEY_FONT.equals(entry.key)
                    && !value.isEmpty() && !fonts.contains(value)) {
                // The clock falls back to its built-in face on its own; naming a font that is not
                // here would only be a setting nobody can see the effect of.
                dropped++;
                continue;
            }

            switch (entry.type) {
                case 'b':
                    editor.putBoolean(entry.key, Boolean.parseBoolean(value));
                    break;
                case 'i':
                    editor.putInt(entry.key, (int) number(value));
                    break;
                case 'l':
                    editor.putLong(entry.key, number(value));
                    break;
                default:
                    editor.putString(entry.key, value);
                    break;
            }
            applied++;
        }
        // A run belonging to this phone is stopped: the arrangement it was running under has just
        // been replaced underneath it.
        editor.putLong(Settings.KEY_RUN_ORIGIN, com.reteclock.core.TimerMemory.NONE);
        editor.putString(Settings.KEY_RUN_PRESET, "");
        editor.commit();
        return new Result(applied, dropped, true);
    }

    /** What a file store actually holds, by name. */
    private static List<String> namesOf(com.reteclock.core.FontLibrary library) {
        List<String> out = new ArrayList<String>();
        for (com.reteclock.core.FontLibrary.Entry entry : library.list()) {
            out.add(entry.name);
        }
        return out;
    }

    private static boolean isNameList(String key) {
        return Arrays.asList(NAME_LISTS).contains(key);
    }

    private static long number(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static List<String> lines(String value) {
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String line : value.split("\n", -1)) {
            if (!line.trim().isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String join(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(name);
        }
        return out.toString();
    }
}
