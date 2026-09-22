package com.reteclock.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * The language speech is asked to speak in, as the settings keep it (T111).
 *
 * A tag: "ko-KR", "fa", or "" for the phone's own language. A Locale is not something a settings
 * file can hold, and the file may be edited by hand or carried to another phone, so a tag that
 * cannot be read is taken as the phone's own language rather than as a fault. Two letters or three
 * for the language, two for the country — what Android's text-to-speech works in.
 *
 * Pure Java: no android.*.
 */
public final class VoiceLocale {

    private VoiceLocale() {
    }

    /** The language and the country ("" when none), or null for the phone's own language. */
    public static List<String> parse(String tag) {
        if (tag == null) {
            return null;
        }
        String t = tag.trim().replace('_', '-');
        if (t.isEmpty()) {
            return null;
        }
        String[] parts = t.split("-", -1);
        if (parts.length > 2) {
            return null;
        }
        String language = parts[0].toLowerCase(java.util.Locale.ROOT);
        String country = parts.length == 2 ? parts[1].toUpperCase(java.util.Locale.ROOT) : "";
        if (!letters(language, 2, 3) || (parts.length == 2 && !letters(country, 2, 2))) {
            return null;
        }
        return Arrays.asList(language, country);
    }

    /** The tag for a language and a country; "" when there is no language. */
    public static String tag(String language, String country) {
        if (language == null || language.trim().isEmpty()) {
            return "";
        }
        String l = language.trim().toLowerCase(java.util.Locale.ROOT);
        String c = country == null ? "" : country.trim().toUpperCase(java.util.Locale.ROOT);
        return c.isEmpty() ? l : l + "-" + c;
    }

    /** The tags worth offering: readable, each once, in alphabetical order. */
    public static List<String> offered(List<String> tags) {
        TreeSet<String> out = new TreeSet<String>();
        for (String t : tags) {
            List<String> p = parse(t);
            if (p != null) {
                out.add(tag(p.get(0), p.get(1)));
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(out));
    }

    private static boolean letters(String s, int min, int max) {
        if (s.length() < min || s.length() > max) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return true;
    }
}
