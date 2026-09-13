package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

/**
 * What a phone that has restarted and not yet been unlocked allows, on Android 7.0 and up
 * (RFC-0012, F2).
 *
 * <p>Until the first unlock, only device-protected storage can be read. The wake bells keep a short
 * list of their next rings there, so a ring due before the unlock can still be armed and rung — with
 * the built-in sound, because the user's own sound files are in credential-protected storage and
 * cannot be opened yet. Touched only behind {@code SDK_INT >= 24}.
 */
final class WakeApi24 {

    private WakeApi24() {
    }

    static SharedPreferences lockedPrefs(Context context, String name) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    static boolean isUnlocked(Context context) {
        UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
        return users == null || users.isUserUnlocked();
    }
}
