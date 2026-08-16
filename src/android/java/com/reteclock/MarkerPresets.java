package com.reteclock;

import android.content.Context;

import com.reteclock.core.ClockOptions;
import com.reteclock.core.CustomMarkers;

/**
 * The ways different places actually write a twelve-hour clock, as a list to pick from.
 *
 * Four markers and two conventions is six decisions, and almost nobody wants an arrangement of
 * their own — they want the one their country uses. Each entry here is a real answer somebody
 * settled on, and picking one fills in all six at once.
 *
 * The first is the default this app ships with, which is what nearly every phone does and what the
 * United States means by a clock: AM and PM, noon as 12 PM, midnight as 12 AM. Its own standards
 * body calls those two ambiguous — that is why the alternatives below it exist — but it is what a
 * clock is expected to say, so it is the default and the list is also the way back to it.
 */
final class MarkerPresets {

    /** How many there are, which the chooser needs before it asks for any of them. */
    static final int COUNT = 7;

    private MarkerPresets() {
    }

    /** The name of one, with the reading it produces at noon and midnight beside it. */
    static String label(Context context, int which) {
        switch (which) {
            case 1:
                return context.getString(R.string.marker_preset_philippines);
            case 2:
                return context.getString(R.string.marker_preset_japan);
            case 3:
                return context.getString(R.string.marker_preset_korea);
            case 4:
                return context.getString(R.string.marker_preset_china);
            case 5:
                return context.getString(R.string.marker_preset_written);
            case 6:
                return context.getString(R.string.marker_preset_midnight_24h);
            default:
                return context.getString(R.string.marker_preset_default);
        }
    }

    /** Writes the four markers and the two conventions that go with one of them. */
    static void apply(Context context, int which) {
        CustomMarkers marks;
        int noon = ClockOptions.NOON_PM;
        int midnight = ClockOptions.MIDNIGHT_AM;
        switch (which) {
            case 1:
                // The Philippines: NN and MN, the two markers that cannot be misread.
                marks = CustomMarkers.NONE;
                noon = ClockOptions.NOON_NN;
                midnight = ClockOptions.MIDNIGHT_MN;
                break;
            case 2:
                // Japan: 午前 and 午後, and the twelfth hour written zero.
                marks = CustomMarkers.of("午前", "午後", "", "");
                noon = ClockOptions.NOON_ZERO;
                midnight = ClockOptions.MIDNIGHT_ZERO;
                break;
            case 3:
                // Korea: 오전 and 오후, with noon and midnight left to the ordinary markers.
                marks = CustomMarkers.of("오전", "오후", "", "");
                break;
            case 4:
                // China: 上午 and 下午.
                marks = CustomMarkers.of("上午", "下午", "", "");
                break;
            case 5:
                // Britain and the United States in writing: the words spelled out at the two
                // hours that cannot be written with AM or PM.
                marks = CustomMarkers.of("", "", "noon", "midnight");
                break;
            case 6:
                // Much of Europe and East Asia: a twelve-hour clock all day, except midnight,
                // which is written 00:00 and needs no marker at all.
                marks = CustomMarkers.NONE;
                midnight = ClockOptions.MIDNIGHT_24H;
                break;
            default:
                // The default, and the way back from any of the above.
                marks = CustomMarkers.NONE;
                break;
        }
        Settings.setMarkers(context, marks);
        Settings.setNoonStyle(context, noon);
        Settings.setMidnightStyle(context, midnight);
    }
}
