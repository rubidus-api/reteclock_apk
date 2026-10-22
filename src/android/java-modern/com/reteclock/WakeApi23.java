package com.reteclock;

import android.content.Context;
import android.os.PowerManager;

/**
 * Whether battery optimisation leaves this app alone, on Android 6.0 and up (R145): what Timer
 * settings says beside the button to those settings. Touched only behind a
 * {@code Build.VERSION.SDK_INT >= 23} check; see {@link WakeApi21}.
 */
final class WakeApi23 {

    private WakeApi23() {
    }

    static boolean ignoringBatteryOptimizations(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
    }
}
