package com.reteclock;

/**
 * The platform's own name for a key code, which Android has from 3.1 (API 12).
 *
 * Reached only behind {@code Build.VERSION.SDK_INT >= 12}, like the {@code *Api21} classes: below
 * that the class is never loaded, and the core's own table names the keys a person is likely to
 * press.
 */
final class KeyNamesApi12 {

    private KeyNamesApi12() {
    }

    static String of(int keyCode) {
        try {
            return android.view.KeyEvent.keyCodeToString(keyCode);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
