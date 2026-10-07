package com.reteclock;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/**
 * The clipboard as it has been since API 11, kept in a class of its own so that below it the class
 * is never loaded — the same arrangement as {@link KeyNamesApi12}.
 */
final class ClipApi11 {

    private ClipApi11() {
    }

    static boolean copy(Context context, String text) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("ReteClock", text));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
