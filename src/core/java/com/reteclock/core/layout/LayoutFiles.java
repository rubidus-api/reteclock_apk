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

    /**
     * The folder a layout's own pictures live in, inside `layouts/` (RFC-0010, D2).
     *
     * The two shelves may hold layouts of the same name — an upright one and a sideways one, often
     * a pair — so which way up it is is part of the folder's name. The suffix is a readable word
     * rather than a code, because the point of the folder is that a person can open the zip and
     * know what they are looking at.
     */
    public static String folderName(String presetName, boolean landscape) {
        String cleaned = clean(presetName);
        String base = cleaned.isEmpty() ? FALLBACK : cleaned;
        // " - sideways" rather than "(sideways)": a package entry has to pass SafeName, and
        // SafeName allows letters, digits, spaces, dashes, underscores and dots — and nothing else.
        // Brackets are refused, which is how every sideways layout came to be exported under the
        // fallback name and two of them collided.
        return landscape ? base + SIDEWAYS : base;
    }

    /** What a sideways layout's folder is called, so the caller does not have to know. */
    public static final String SIDEWAYS = " - sideways";

    /**
     * The same folder name with a number on the end, for when two layouts want one folder.
     *
     * The number goes on the *cleaned* name rather than on the layout's own, because a name that
     * cleans away to nothing cleans away to nothing however many numbers are added to it — which is
     * an endless search for a free name, and was one.
     */
    public static String folderName(String presetName, boolean landscape, int n) {
        String base = folderName(presetName, landscape);
        return n <= 1 ? base : base + " " + n;
    }

    /** The file the arrangement itself is kept in, inside that folder. */
    public static final String PRESET_FILE = "preset.txt";

    /**
     * The folder and file of a package entry two levels deep — `layouts/<folder>/<file>` — or null
     * when the entry is not one of those.
     *
     * Null for everything that could go wrong: another folder at the top, a directory, one level
     * only (which is the older flat shape and read elsewhere), three levels, and any path that
     * tries to climb out with `..`. A zip is something somebody else may have made.
     *
     * @return {folder, file}, or null
     */
    public static String[] entryInFolder(String path) {
        if (path == null) {
            return null;
        }
        // Not SafeName.insideFolder: that one refuses anything with a second slash in it, which is
        // exactly what this shape is. The top folder is checked the same way it checks it, and then
        // the rest is split by hand — and every piece is put through SafeName below, which is where
        // "..", path separators and invisible characters are turned away.
        String cleaned = path.replace('\\', '/');
        int top = cleaned.indexOf('/');
        if (top <= 0) {
            return null;
        }
        String folderAtTop = cleaned.substring(0, top).toLowerCase(java.util.Locale.US);
        boolean ours = false;
        for (int i = 0; i < FOLDERS.length; i++) {
            ours = ours || FOLDERS[i].equals(folderAtTop);
        }
        if (!ours) {
            return null;
        }
        String inside = cleaned.substring(top + 1);
        if (inside.isEmpty() || inside.endsWith("/")) {
            return null;
        }
        int slash = inside.indexOf('/');
        if (slash <= 0 || slash == inside.length() - 1) {
            return null;
        }
        String folder = inside.substring(0, slash);
        String file = inside.substring(slash + 1);
        if (file.indexOf('/') >= 0) {
            return null;                  // deeper than a layout's own folder
        }
        if (SafeName.complaint(folder) != null || SafeName.complaint(file) != null) {
            return null;
        }
        return new String[] {folder, file};
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
