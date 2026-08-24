package com.reteclock.core.layout;

import com.reteclock.core.SafeName;

/**
 * What a preset is called inside the Import/Export package (RFC-0005, D8).
 *
 * The package is a plain zip that somebody may build by hand, so the rule is one sentence: a preset
 * is a `.txt` file directly under `layouts/`. Singular is accepted when reading, as it is for the
 * fonts and the sounds, because a person typing a folder name will type either.
 *
 * The naming lives here rather than in the class that writes the zip because two of its jobs are
 * safety questions, and safety questions belong where they can be tested: a preset's *name* is the
 * user's words and may hold anything at all, while a file name may not; and an entry arriving from
 * somebody else's zip may try to climb out of the folder it claims to be in.
 */
public final class LayoutFiles {

    /** Where presets live in the package. */
    public static final String FOLDER = "layouts/";

    /** What is accepted when reading. Plural and singular, as elsewhere in the package. */
    private static final String[] FOLDERS = {"layouts", "layout"};

    private static final String SUFFIX = ".txt";

    /** What a name is called when nothing usable is left of it. */
    private static final String FALLBACK = "layout";

    private LayoutFiles() {
    }

    /**
     * The file name for a preset called this.
     *
     * A preset's name is the user's own words — spaces, other scripts, punctuation, whatever they
     * typed — and most of that is a perfectly good file name. What is not is replaced rather than
     * refused: this is a name the app made up for a file, not something the user has to get right.
     */
    public static String fileName(String presetName) {
        String cleaned = clean(presetName);
        return (cleaned.isEmpty() ? FALLBACK : cleaned) + SUFFIX;
    }

    /**
     * The file name inside a package entry, or null when this entry is not a preset.
     *
     * Null covers every way an entry can fail to be one: another folder, no folder, a directory, a
     * path with more than one level, and a path that tries to climb out of `layouts/`.
     */
    public static String entryName(String path) {
        String inside = SafeName.insideFolder(path == null ? null : path.replace('\\', '/'),
                FOLDERS);
        if (inside == null || inside.isEmpty()) {
            return null;
        }
        if (SafeName.complaint(inside) != null) {
            return null;                  // ".." and its relatives are refused here
        }
        return inside;
    }

    /** The name to show for a file called this — the file name without its folder or its suffix. */
    public static String presetName(String path) {
        String name = path == null ? "" : path.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(SUFFIX)) {
            name = name.substring(0, name.length() - SUFFIX.length());
        }
        return name;
    }

    /**
     * A name with everything a file system minds taken out.
     *
     * Slashes and the characters Windows refuses become spaces rather than being dropped, so two
     * different names do not collapse into one. Leading and trailing spaces and dots go, because a
     * name that begins or ends with one is refused by {@link SafeName} and looks like nothing on
     * screen.
     */
    private static String clean(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || c < 0x20) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        String cleaned = trimDotsAndSpaces(out.toString());
        // A name made entirely of things a file name may not hold, or of nothing that draws, is
        // given the fallback rather than a file the user cannot see the name of.
        if (cleaned.isEmpty() || SafeName.complaint(cleaned + SUFFIX) != null) {
            return "";
        }
        return cleaned;
    }

    private static String trimDotsAndSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == ' ' || value.charAt(start) == '.')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '.')) {
            end--;
        }
        return value.substring(start, end);
    }
}
