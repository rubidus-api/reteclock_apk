package com.reteclock.core.layout;

/**
 * What a layout may be called.
 *
 * A layout's name is a folder on disc and an entry in a zip — its arrangement and the pictures it
 * carries live under it (RFC-0010) — so a name that is merely *shown* is not what this is. Two
 * layouts whose names differ only in a space, a bracket or an accent are two folders on one system
 * and one folder on another, and the second quietly eats the first.
 *
 * So the rule is the plainest one there is, and it is the one every programmer already knows: a
 * layout is named like a variable in C. A letter first, then letters, digits and underscores.
 * Nothing else at all — no spaces, no punctuation, no other script.
 *
 * **A name beginning with an underscore is reserved**, exactly as it is in C, and for the same
 * reason: the app needs names it can use without ever colliding with one somebody chose. The
 * layouts this app ships with are called `_clock` and `_clock_with_month`, and a name typed by a
 * person may not begin with one.
 *
 * This is a restriction, and it is worth saying why it is worth it: the alternative is a file name
 * that depends on what somebody typed, in a folder shared with names from a package somebody else
 * built, on a file system whose rules are not ours. That is where the unpleasant bugs live.
 */
public final class LayoutName {

    /** What a name is called when nothing usable is left of what was typed. */
    public static final String FALLBACK = "layout";

    private LayoutName() {
    }

    /** Whether this is a name a layout may have at all — the app's own included. */
    public static boolean isValid(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        char first = name.charAt(0);
        if (!isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isLetter(c) && !isDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /** Whether this name belongs to the app rather than to the person using it. */
    public static boolean isReserved(String name) {
        return name != null && name.length() > 0 && name.charAt(0) == '_';
    }

    /** Whether a person may give a layout this name: valid, and not one of the app's own. */
    public static boolean isUsable(String name) {
        return isValid(name) && !isReserved(name);
    }

    /**
     * What is wrong with this name, in words, or null when nothing is.
     *
     * For the screen that asks for one. The three sentences are the three rules, and each says what
     * to do rather than only what is refused.
     */
    public static String complaint(String name) {
        if (name == null || name.trim().length() == 0) {
            return "A layout needs a name.";
        }
        String trimmed = name.trim();
        if (isReserved(trimmed)) {
            return "Names beginning with _ belong to the app's own layouts. Start with a letter.";
        }
        if (!isLetter(trimmed.charAt(0))) {
            return "A name has to start with a letter.";
        }
        if (!isValid(trimmed)) {
            return "A name may hold letters, digits and _ only — no spaces or punctuation.";
        }
        return null;
    }

    /**
     * The nearest usable name to what was typed or what arrived.
     *
     * Used on a layout coming from somewhere this rule did not hold: an older version of this app,
     * or a package somebody built by hand. Spaces and punctuation become underscores rather than
     * vanishing, so two different names stay two different names; anything else that cannot be a
     * name is dropped; a leading underscore is taken off, because the reserved names are the app's.
     */
    public static String clean(String name) {
        if (name == null) {
            return FALLBACK;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (isLetter(c) || isDigit(c)) {
                out.append(c);
            } else if (c == '_' || c == ' ' || c == '-' || c == '.') {
                out.append('_');
            }
            // everything else — other scripts, punctuation, marks — is not a name and is dropped
        }
        // A name has to begin with a letter, so whatever is in front of the first one goes. The
        // digits and underscores it held are not lost: they are simply not a beginning.
        int start = 0;
        while (start < out.length() && !isLetter(out.charAt(start))) {
            start++;
        }
        String cleaned = out.substring(start);
        return cleaned.length() == 0 ? FALLBACK : cleaned;
    }

    /**
     * A name like this one but not equal to any of those, made by numbering.
     *
     * The number joins with an underscore, so the answer is a name by the same rule that made the
     * question — which is what stops "a free name" from being an endless search.
     */
    public static String free(String wanted, java.util.Collection<String> taken) {
        String base = isValid(wanted) ? wanted : clean(wanted);
        if (taken == null || !taken.contains(base)) {
            return base;
        }
        for (int n = 2; n < 10000; n++) {
            String candidate = base + "_" + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
