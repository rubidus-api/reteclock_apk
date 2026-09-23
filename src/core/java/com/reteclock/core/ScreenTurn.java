package com.reteclock.core;

/**
 * Which way up the clock asks to be shown (issue #61, T114).
 *
 * Until now the clock took whatever the phone gave it, which is right for a phone: it has a
 * rotation sensor and a lock of its own. A device without one — the reporter's e-reader, hung on a
 * wall — can never be turned into landscape, so the way up has to be something the app can be told.
 *
 * Four answers, and the first is what every clock had before: leave it to the system. "Follow
 * sensor" is for a device whose system rotation is locked but whose sensor works; landscape and
 * portrait are fixed, which is what a clock on a wall wants.
 *
 * {@link #requested} answers in Android's own numbers, so the glue has nothing to decide. They are
 * fixed by a test, because a wrong one turns a screen the wrong way on a device nobody here has.
 *
 * Pure Java: no android.*.
 */
public final class ScreenTurn {

    /** Whatever the phone decides, which is what the clock always did. */
    public static final int SYSTEM = 0;
    /** Turn with the device, even where the system's own rotation is locked. */
    public static final int SENSOR = 1;
    /** Always lying down. */
    public static final int LANDSCAPE = 2;
    /** Always standing up. */
    public static final int PORTRAIT = 3;

    public static final int COUNT = 4;

    private ScreenTurn() {
    }

    /** The mode a stored number means; anything else is the system's own choice. */
    public static int of(int stored) {
        return stored < 0 || stored >= COUNT ? SYSTEM : stored;
    }

    /**
     * What to ask Android for: the values of {@code ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED},
     * {@code _SENSOR}, {@code _LANDSCAPE} and {@code _PORTRAIT}, all of them API 1.
     */
    public static int requested(int mode) {
        switch (of(mode)) {
            case SENSOR:
                return 4;
            case LANDSCAPE:
                return 0;
            case PORTRAIT:
                return 1;
            default:
                return -1;
        }
    }
}
