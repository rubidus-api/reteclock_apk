package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole library of presets: how it is stored, and what is there before anybody has made one.
 *
 * One preset per line — a preset's own format never contains a newline, because it escapes any it
 * is given — so the library is as simple as the name lists the images already use.
 */
public final class TimerPresets {

    private TimerPresets() {
    }

    public static String toText(List<TimerPreset> presets) {
        StringBuilder out = new StringBuilder();
        for (TimerPreset preset : presets) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(preset.toText());
        }
        return out.toString();
    }

    /** Reads the library, skipping anything unreadable rather than losing the rest with it. */
    public static List<TimerPreset> parse(String text) {
        List<TimerPreset> out = new ArrayList<TimerPreset>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            TimerPreset preset = TimerPreset.parse(line);
            if (preset != null && !preset.intervals.isEmpty()) {
                out.add(preset);
            }
        }
        return out;
    }

    /**
     * What a new install finds: the pomodoro everybody means by the word, and a short one for tea.
     *
     * A timer with no presets would offer an empty list and no way to understand what a preset is
     * for, so there is something to start, and something to copy the shape of.
     */
    public static List<TimerPreset> starter() {
        List<TimerPreset> out = new ArrayList<TimerPreset>();

        List<TimerInterval> pomodoro = new ArrayList<TimerInterval>();
        pomodoro.add(new TimerInterval("Work", 25 * 60_000L,
                0xFF4DB6AC, 0xFFEF5350, "Time to work", 60));
        pomodoro.add(new TimerInterval("Break", 5 * 60_000L,
                0xFF64B5F6, 0xFFFFB300, "Take a break", 30));
        out.add(new TimerPreset("Pomodoro", pomodoro));

        List<TimerInterval> tea = new ArrayList<TimerInterval>();
        tea.add(new TimerInterval("Steep", 3 * 60_000L,
                0xFFAED581, 0xFFFF8A65, "", 30));
        out.add(new TimerPreset("Tea", tea));

        return out;
    }
}
