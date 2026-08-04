package com.reteclock.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which image serves where.
 *
 * One pool of images; each one is a background, a text fill, or held out of both — never two
 * things at once, because the same picture behind the glyphs and inside them would be invisible.
 * Pure Java, unit tested; the Android layer stores the two lists and hands them here.
 */
public final class ImageRoles {

    /** Held: in the pool, used nowhere. */
    public static final int NONE = 0;
    public static final int BACKGROUND = 1;
    public static final int TEXT = 2;

    /** The two role lists. Immutable from outside; every operation returns a new pair. */
    public static final class Lists {
        public final List<String> background;
        public final List<String> text;

        public Lists(List<String> background, List<String> text) {
            this.background = background;
            this.text = text;
        }
    }

    private ImageRoles() {
    }

    /**
     * These lists with {@code name} serving {@code role}. Assigning a role it already has keeps
     * its place — a no-op must not reshuffle — and any other assignment removes it from both
     * lists first, so exclusivity cannot be broken by any sequence of calls.
     */
    public static Lists assign(Lists lists, String name, int role) {
        if (roleOf(lists, name) == role) {
            return lists;
        }
        List<String> background = without(lists.background, name);
        List<String> text = without(lists.text, name);
        if (role == BACKGROUND) {
            background.add(name);
        } else if (role == TEXT) {
            text.add(name);
        }
        return new Lists(background, text);
    }

    /** Where this image serves. */
    public static int roleOf(Lists lists, String name) {
        if (lists.background.contains(name)) {
            return BACKGROUND;
        }
        if (lists.text.contains(name)) {
            return TEXT;
        }
        return NONE;
    }

    /** These lists with a renamed image keeping its role and its place. */
    public static Lists renamed(Lists lists, String oldName, String newName) {
        return new Lists(replaced(lists.background, oldName, newName),
                replaced(lists.text, oldName, newName));
    }

    /** These lists with a deleted image gone from wherever it served. */
    public static Lists removed(Lists lists, String name) {
        return new Lists(without(lists.background, name), without(lists.text, name));
    }

    /**
     * The pool entries that are in {@code names}, in the pool's own order — the user's sort wins;
     * membership only says who is in. A name whose file is gone is simply absent.
     */
    public static List<FontLibrary.Entry> filter(List<FontLibrary.Entry> pool,
            List<String> names) {
        Set<String> wanted = new HashSet<String>(names);
        List<FontLibrary.Entry> out = new ArrayList<FontLibrary.Entry>();
        for (FontLibrary.Entry entry : pool) {
            if (wanted.contains(entry.name)) {
                out.add(entry);
            }
        }
        return out;
    }

    private static List<String> without(List<String> names, String name) {
        List<String> out = new ArrayList<String>(names);
        out.remove(name);
        return out;
    }

    private static List<String> replaced(List<String> names, String oldName, String newName) {
        List<String> out = new ArrayList<String>(names);
        int at = out.indexOf(oldName);
        if (at >= 0) {
            out.set(at, newName);
        }
        return out;
    }
}
