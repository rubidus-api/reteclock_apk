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

        // The pomodoro as everybody states it: twenty-five minutes of work, five of rest, round
        // and round until you stop it. Nothing is spoken — somebody working does not want a voice
        // every half hour, and the beeps already say what happened.
        List<TimerInterval> pomodoro = new ArrayList<TimerInterval>();
        pomodoro.add(new TimerInterval("Work", 25 * 60_000L,
                0xFF4DB6AC, 0xFFEF5350, "", 60));
        pomodoro.add(new TimerInterval("Break", 5 * 60_000L,
                0xFF64B5F6, 0xFFFFB300, "", 30));
        out.add(new TimerPreset("Pomodoro", pomodoro, true));

        // Tabata as Izumi Tabata's protocol is universally given: eight rounds of twenty seconds
        // of work and ten of rest, four minutes in all. Each round says which one it is, because
        // somebody halfway through burpees is not in a position to look at the screen.
        String[] ordinals = {"First", "Second", "Third", "Fourth",
            "Fifth", "Sixth", "Seventh", "Eighth"};
        List<TimerInterval> tabata = new ArrayList<TimerInterval>();
        for (int round = 0; round < ordinals.length; round++) {
            tabata.add(new TimerInterval(ordinals[round] + " set", 20_000L,
                    0xFFEF5350, 0xFF4DB6AC, ordinals[round] + " set", 0));
            tabata.add(new TimerInterval(ordinals[round] + " rest", 10_000L,
                    0xFF64B5F6, 0xFFAED581, ordinals[round] + " rest", 0));
        }
        out.add(new TimerPreset("Tabata", tabata));

        List<TimerInterval> tea = new ArrayList<TimerInterval>();
        tea.add(new TimerInterval("Steep", 3 * 60_000L,
                0xFFAED581, 0xFFFF8A65, "", 30));
        out.add(new TimerPreset("Tea", tea));

        List<TimerInterval> ramen = new ArrayList<TimerInterval>();
        ramen.add(new TimerInterval("Boil", 4 * 60_000L + 30_000L,
                0xFFFFB300, 0xFFEF5350, "A meal just for me", 30));
        out.add(new TimerPreset("Ramen", ramen));

        List<TimerInterval> spaghetti = new ArrayList<TimerInterval>();
        spaghetti.add(new TimerInterval("Boil", 10 * 60_000L,
                0xFFAED581, 0xFFFF8A65, "For your health and your wallet", 60));
        out.add(new TimerPreset("Spaghetti", spaghetti));

        return out;
    }
}
