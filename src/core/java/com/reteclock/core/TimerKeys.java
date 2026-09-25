package com.reteclock.core;

/**
 * Keys the user chooses for the timer (issue #63).
 *
 * <p>{@link KeyRoute} gives the timer the keys every remote has — centre, Enter and the media keys.
 * Other devices have other keys within reach: an e-reader's two page buttons, a keyboard's space
 * bar, a headset's button, a volume rocker on a phone standing in a dock. Which of them a person
 * wants is theirs to say, so this is a switch and two lists of key codes: the keys that start and
 * pause the timer, and the keys that stop it. While the switch is on these are asked before the
 * built-in keys; a key the user chose means what they chose, even where the built-in rule had
 * another use for it.
 *
 * <p>A physical keyboard and a device's own buttons arrive the same way, as key codes, so both can
 * be chosen. Home, the power key and the app switcher never reach an app; Back is refused, because a
 * clock that swallowed Back is a clock nobody can leave; and a modifier on its own (Shift, Ctrl,
 * Alt ...) is refused because it is half of another key.
 *
 * <p>Pure Java: codes are {@code android.view.KeyEvent} values, named here.
 */
public final class TimerKeys {

    /** A chosen key held down: taken, so the platform does nothing with it, and so do we. */
    public static final int SWALLOW = 99;

    /** The two page keys issue #63 names, which is where a reader's buttons usually land. */
    public static final String DEFAULT_START_PAUSE = "92,93";

    private static final int[] NONE = new int[0];

    /** Keys an app never gets, or must never take. */
    private static final int[] REFUSED = {
        0,   // UNKNOWN
        3,   // HOME
        4,   // BACK
        26,  // POWER
        6,   // ENDCALL
        187, // APP_SWITCH
        57, 58,   // ALT_LEFT, ALT_RIGHT
        59, 60,   // SHIFT_LEFT, SHIFT_RIGHT
        113, 114, // CTRL_LEFT, CTRL_RIGHT
        117, 118, // META_LEFT, META_RIGHT
        119,      // FUNCTION
        63,       // SYM
        115, 116, 143, // CAPS_LOCK, SCROLL_LOCK, NUM_LOCK
    };

    private final boolean on;
    private final int[] startPause;
    private final int[] stop;

    private TimerKeys(boolean on, int[] startPause, int[] stop) {
        this.on = on;
        this.startPause = startPause;
        this.stop = stop;
    }

    /**
     * @param on             the switch
     * @param startPauseText the stored list, or null when nothing was ever stored (the defaults)
     * @param stopText       the stored list, or null
     */
    public static TimerKeys of(boolean on, String startPauseText, String stopText) {
        int[] sp = parse(startPauseText == null ? DEFAULT_START_PAUSE : startPauseText);
        int[] st = parse(stopText == null ? "" : stopText);
        // One action per key: where a file names a key twice, start/pause keeps it.
        return new TimerKeys(on, sp, minus(st, sp));
    }

    public boolean on() {
        return on;
    }

    public int[] startPause() {
        return startPause.clone();
    }

    public int[] stop() {
        return stop.clone();
    }

    public String startPauseText() {
        return text(startPause);
    }

    public String stopText() {
        return text(stop);
    }

    /** Whether this key is one of the user's while the switch is on. */
    public boolean takes(int keyCode) {
        return on && (contains(startPause, keyCode) || contains(stop, keyCode));
    }

    /**
     * What a key does: {@link KeyRoute#TIMER_START}, {@link KeyRoute#TIMER_PAUSE},
     * {@link KeyRoute#TIMER_STOP}, {@link #SWALLOW} for a chosen key's repeats, or
     * {@link KeyRoute#NOTHING} when it is not one of the user's (the built-in rule then answers).
     *
     * @param repeatCount  the platform's count of repeats while the key is held; 0 for the press
     * @param timerRunning whether something is counting just now
     */
    public int route(int keyCode, int repeatCount, boolean timerRunning) {
        if (!takes(keyCode)) {
            return KeyRoute.NOTHING;
        }
        if (repeatCount > 0) {
            return SWALLOW;
        }
        if (contains(stop, keyCode)) {
            return KeyRoute.TIMER_STOP;
        }
        return timerRunning ? KeyRoute.TIMER_PAUSE : KeyRoute.TIMER_START;
    }

    /** The same keys with this one starting and pausing (and no longer stopping). */
    public TimerKeys withStartPause(int keyCode) {
        if (!assignable(keyCode)) {
            return this;
        }
        return new TimerKeys(on, plus(startPause, keyCode), minus(stop, new int[] {keyCode}));
    }

    /** The same keys with this one stopping (and no longer starting). */
    public TimerKeys withStop(int keyCode) {
        if (!assignable(keyCode)) {
            return this;
        }
        return new TimerKeys(on, minus(startPause, new int[] {keyCode}), plus(stop, keyCode));
    }

    /** The same keys without this one. */
    public TimerKeys without(int keyCode) {
        int[] gone = {keyCode};
        return new TimerKeys(on, minus(startPause, gone), minus(stop, gone));
    }

    /** No keys at all, the switch left as it is. */
    public TimerKeys cleared() {
        return new TimerKeys(on, NONE, NONE);
    }

    /** Whether a key may be chosen. */
    public static boolean assignable(int keyCode) {
        if (keyCode <= 0) {
            return false;
        }
        return !contains(REFUSED, keyCode);
    }

    /**
     * A key's name for a person: "Page Up", "Space", "A", "F5".
     *
     * @param platformName what {@code KeyEvent.keyCodeToString} said (API 12+), or null below it;
     *                     used for a code the table here does not name
     */
    public static String name(int keyCode, String platformName) {
        String known = table(keyCode);
        if (known != null) {
            return known;
        }
        if (platformName != null && platformName.startsWith("KEYCODE_")
                && platformName.length() > 8) {
            return words(platformName.substring(8));
        }
        return "Key " + keyCode;
    }

    // ---- the names -----------------------------------------------------------------------------

    private static String table(int code) {
        if (code >= 7 && code <= 16) {
            return String.valueOf(code - 7);
        }
        if (code >= 29 && code <= 54) {
            return String.valueOf((char) ('A' + code - 29));
        }
        if (code >= 131 && code <= 142) {
            return "F" + (code - 130);
        }
        if (code >= 144 && code <= 153) {
            return "Numpad " + (code - 144);
        }
        switch (code) {
            case 5: return "Call";
            case 17: return "*";
            case 18: return "#";
            case 19: return "Up";
            case 20: return "Down";
            case 21: return "Left";
            case 22: return "Right";
            case 23: return "Centre";
            case 24: return "Volume Up";
            case 25: return "Volume Down";
            case 27: return "Camera";
            case 55: return ",";
            case 56: return ".";
            case 61: return "Tab";
            case 62: return "Space";
            case 66: return "Enter";
            case 67: return "Backspace";
            case 68: return "`";
            case 69: return "-";
            case 70: return "=";
            case 71: return "[";
            case 72: return "]";
            case 73: return "\\";
            case 74: return ";";
            case 75: return "'";
            case 76: return "/";
            case 77: return "@";
            case 79: return "Headset Button";
            case 80: return "Camera Focus";
            case 81: return "+";
            case 82: return "Menu";
            case 84: return "Search";
            case 85: return "Play/Pause";
            case 86: return "Stop";
            case 87: return "Next";
            case 88: return "Previous";
            case 89: return "Rewind";
            case 90: return "Fast Forward";
            case 91: return "Mute";
            case 92: return "Page Up";
            case 93: return "Page Down";
            case 111: return "Escape";
            case 112: return "Delete";
            case 120: return "Print Screen";
            case 121: return "Break";
            case 122: return "Home (keyboard)";
            case 123: return "End";
            case 124: return "Insert";
            case 126: return "Play";
            case 127: return "Pause";
            case 160: return "Numpad Enter";
            case 164: return "Volume Mute";
            default: return null;
        }
    }

    /** "BUTTON_THUMBL" to "Button Thumbl". */
    private static String words(String upper) {
        StringBuilder out = new StringBuilder();
        boolean start = true;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '_') {
                out.append(' ');
                start = true;
                continue;
            }
            out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = false;
        }
        return out.toString();
    }

    // ---- lists of codes ------------------------------------------------------------------------

    /** "92,93" to {92, 93}: numbers only, no duplicates, nothing refused, rough text forgiven. */
    private static int[] parse(String text) {
        int[] out = new int[0];
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == ',') {
                String part = text.substring(start, i).trim();
                start = i + 1;
                if (part.length() == 0 || part.length() > 6) {
                    continue;
                }
                int code = 0;
                boolean digits = true;
                for (int j = 0; j < part.length(); j++) {
                    char c = part.charAt(j);
                    if (c < '0' || c > '9') {
                        digits = false;
                        break;
                    }
                    code = code * 10 + (c - '0');
                }
                if (digits && assignable(code)) {
                    out = plus(out, code);
                }
            }
        }
        return out;
    }

    private static String text(int[] codes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(codes[i]);
        }
        return out.toString();
    }

    private static boolean contains(int[] codes, int code) {
        for (int i = 0; i < codes.length; i++) {
            if (codes[i] == code) {
                return true;
            }
        }
        return false;
    }

    private static int[] plus(int[] codes, int code) {
        if (contains(codes, code)) {
            return codes;
        }
        int[] out = new int[codes.length + 1];
        System.arraycopy(codes, 0, out, 0, codes.length);
        out[codes.length] = code;
        return out;
    }

    private static int[] minus(int[] codes, int[] gone) {
        int kept = 0;
        for (int i = 0; i < codes.length; i++) {
            if (!contains(gone, codes[i])) {
                kept++;
            }
        }
        int[] out = new int[kept];
        int at = 0;
        for (int i = 0; i < codes.length; i++) {
            if (!contains(gone, codes[i])) {
                out[at++] = codes[i];
            }
        }
        return out;
    }
}
