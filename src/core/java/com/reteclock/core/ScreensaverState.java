package com.reteclock.core;

/**
 * Reads, out of the system's own screensaver setting, whether this app is the one chosen.
 *
 * The platform stores the choice as one or more component names, and not always in the same shape:
 * a plain {@code package/class}, sometimes wrapped as {@code ComponentInfo{package/class}}, and on
 * versions that allowed several, separated by commas. The parsing is fiddly enough to be worth
 * testing on a JVM, and it is all this class does — the settings screen supplies both strings.
 */
public final class ScreensaverState {

    private ScreensaverState() {
    }

    /**
     * Whether {@code ours} — a flattened {@code package/class} — is among the chosen screensavers.
     *
     * Whole entries are compared, never substrings: an app whose class name merely begins with
     * ours must not be mistaken for it.
     */
    public static boolean isChosen(String stored, String ours) {
        if (stored == null || ours == null || ours.isEmpty()) {
            return false;
        }
        for (String entry : stored.split(",")) {
            if (unwrap(entry).equals(ours)) {
                return true;
            }
        }
        return false;
    }

    /** One entry, without its surrounding spaces and without any {@code ComponentInfo{…}} wrapper. */
    private static String unwrap(String entry) {
        String name = entry.trim();
        if (name.startsWith("ComponentInfo{") && name.endsWith("}")) {
            name = name.substring("ComponentInfo{".length(), name.length() - 1).trim();
        }
        return name;
    }
}
