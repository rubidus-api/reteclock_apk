package com.reteclock.core;

/**
 * Whether a file inside an imported package may be unpacked under the name it claims.
 *
 * A package brings fonts and pictures with it, and their names come from another machine — which
 * means they are not names, they are input. Three things can go wrong with input that is treated as
 * a name: it can escape the folder it belongs in, it can be too long for the filesystem to hold,
 * and it can be written so that what a person reads is not what the machine reads. The third is the
 * subtle one: a right-to-left override turns `evilgnp.ttf` into something that reads `ttf.pnglive`,
 * and a stack of combining marks turns one line of a list into a smear across three.
 *
 * So the rule here is a small allow-list rather than a list of things to strip, and it is applied
 * **before the entry is read**, not after: a name that fails is refused whole, and nothing is
 * unpacked under it.
 *
 * What is allowed: letters of any script, digits, `_`, `-`, `.` and the plain space. Combining
 * marks are allowed — plenty of scripts cannot write a word without them — but at most
 * {@link #MAX_MARKS} in a row, which is enough for any real orthography and not enough to build a
 * tower. Everything else is refused, in particular every control and format character, the bidi
 * overrides, the interlinear-annotation (ruby) characters, private-use and unassigned code points,
 * and any space that is not U+0020.
 *
 * Pure Java: no android.*.
 */
public final class SafeName {

    /** The longest a name may be, in UTF-8 bytes. Shorter than any filesystem's limit, on purpose. */
    public static final int MAX_BYTES = 127;

    /** How many combining marks may follow one base character. */
    public static final int MAX_MARKS = 2;

    private SafeName() {
    }

    /** Whether this name may be used as it stands. */
    public static boolean isSafe(String name) {
        return complaint(name) == null;
    }

    /**
     * What is wrong with this name, or null if nothing is.
     *
     * A sentence rather than a code, because it is shown to the person holding the package and the
     * only useful thing to tell them is which file was refused and why.
     */
    public static String complaint(String name) {
        if (name == null || name.length() == 0) {
            return "the name is empty";
        }
        if (utf8Length(name) > MAX_BYTES) {
            return "the name is longer than " + MAX_BYTES + " bytes";
        }
        if (".".equals(name) || "..".equals(name)) {
            return "the name is a folder, not a file";
        }
        if (name.charAt(0) == ' ' || name.charAt(0) == '.'
                || name.charAt(name.length() - 1) == ' ' || name.charAt(name.length() - 1) == '.') {
            return "the name begins or ends with a space or a dot";
        }

        int marks = 0;
        boolean anySolid = false;
        for (int i = 0; i < name.length(); ) {
            int code = name.codePointAt(i);
            i += Character.charCount(code);

            if (code == '/' || code == '\\') {
                return "the name contains a path separator";
            }
            if (code == ' ' || code == '_' || code == '-' || code == '.') {
                // Punctuation is allowed but is not something a mark can sit on, and a name made
                // only of it is not a name.
                marks = 0;
                continue;
            }
            if (isMark(code)) {
                // A mark with nothing to sit on is a mark pretending to be a letter.
                if (!anySolid) {
                    return "the name begins with a combining mark";
                }
                if (++marks > MAX_MARKS) {
                    return "the name stacks more than " + MAX_MARKS + " combining marks";
                }
                continue;
            }
            marks = 0;
            if (Character.isLetterOrDigit(code)) {
                anySolid = true;
                continue;
            }
            return "the name contains " + describe(code);
        }
        if (!anySolid) {
            return "the name has no letters or digits in it";
        }
        return null;
    }

    /**
     * The name an entry in a package claims, with its folder taken off — or null if the entry is
     * not a plain file in the folder it should be in.
     *
     * This is where `../../etc/passwd` and `img/sub/dir/x.png` are turned away: an entry either
     * names one file directly inside the folder or it is not ours.
     */
    public static String insideFolder(String entry, String[] folders) {
        if (entry == null) {
            return null;
        }
        int slash = entry.indexOf('/');
        if (slash < 0 || slash == entry.length() - 1) {
            return null;
        }
        String folder = entry.substring(0, slash).toLowerCase(java.util.Locale.US);
        String rest = entry.substring(slash + 1);
        if (rest.indexOf('/') >= 0 || rest.indexOf('\\') >= 0) {
            return null;
        }
        for (int i = 0; i < folders.length; i++) {
            if (folders[i].equals(folder)) {
                return rest;
            }
        }
        return null;
    }

    private static boolean isMark(int code) {
        int type = Character.getType(code);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /** Enough of a description that somebody can find the character in their own file. */
    private static String describe(int code) {
        int type = Character.getType(code);
        String what;
        if (type == Character.CONTROL) {
            what = "a control character";
        } else if (type == Character.FORMAT) {
            what = "an invisible formatting character";   // bidi overrides live here
        } else if (type == Character.PRIVATE_USE) {
            what = "a private-use character";
        } else if (type == Character.SURROGATE || type == Character.UNASSIGNED) {
            what = "a character no font can draw";
        } else if (type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.SPACE_SEPARATOR) {
            what = "a space that is not a plain space";
        } else if (code >= 0xFFF9 && code <= 0xFFFB) {
            what = "an annotation character";             // ruby: U+FFF9..FFFB
        } else {
            what = "a character that is not a letter, a digit, _, -, . or a space";
        }
        return what + " (U+" + hex(code) + ")";
    }

    private static String hex(int code) {
        String out = Integer.toHexString(code).toUpperCase(java.util.Locale.US);
        while (out.length() < 4) {
            out = "0" + out;
        }
        return out;
    }

    /** How many bytes this is in UTF-8, without building the array. */
    public static int utf8Length(String text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int code = text.codePointAt(i);
            i += Character.charCount(code);
            if (code < 0x80) {
                bytes += 1;
            } else if (code < 0x800) {
                bytes += 2;
            } else if (code < 0x10000) {
                bytes += 3;
            } else {
                bytes += 4;
            }
        }
        return bytes;
    }
}
