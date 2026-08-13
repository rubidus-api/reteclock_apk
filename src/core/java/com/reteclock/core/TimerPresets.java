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
    /**
     * The presets a phone starts with.
     *
     * Colour follows two rules, so a bar can be read without being labelled.
     *
     * The working interval carries the preset's own hue, drawn from the thing itself — the
     * pomodoro's tomato, the cool blue of a gym clock, tea's leaf, ramen's broth, dried pasta's
     * wheat-brown — and no two presets open in the same one. Rest is green wherever it appears, in
     * every preset, so that one thing is learned once and never has to be read again.
     *
     * Under both, the part still to come wears its colour at full strength and the part already
     * spent wears the same hue burnt down to a dark shade. A bar spends its own colour rather than
     * turning into somebody else's.
     */
    public static List<TimerPreset> starter() {
        List<TimerPreset> out = new ArrayList<TimerPreset>();

        // The pomodoro as everybody states it: twenty-five minutes of work, five of rest, round
        // and round until you stop it. Nothing is spoken — somebody working does not want a voice
        // every half hour, and the beeps already say what happened.
        List<TimerInterval> pomodoro = new ArrayList<TimerInterval>();
        pomodoro.add(new TimerInterval("Work", 25 * 60_000L,
                0xFFE64A19, 0xFF4A1505, "", 60));
        pomodoro.add(new TimerInterval("Break", 5 * 60_000L,
                0xFF66BB6A, 0xFF1B3D1E, "", 30));
        out.add(new TimerPreset("Pomodoro", pomodoro, true));

        // Tabata as Izumi Tabata's protocol is universally given: eight rounds of twenty seconds
        // of work and ten of rest, four minutes in all. Each round says which one it is, because
        // somebody halfway through burpees is not in a position to look at the screen.
        String[] ordinals = {"First", "Second", "Third", "Fourth",
            "Fifth", "Sixth", "Seventh", "Eighth"};
        List<TimerInterval> tabata = new ArrayList<TimerInterval>();
        for (int round = 0; round < ordinals.length; round++) {
            tabata.add(new TimerInterval(ordinals[round] + " set", 20_000L,
                    0xFF1E88E5, 0xFF0B2A47, ordinals[round] + " set", 0));
            tabata.add(new TimerInterval(ordinals[round] + " rest", 10_000L,
                    0xFF66BB6A, 0xFF1B3D1E, ordinals[round] + " rest", 0));
        }
        out.add(new TimerPreset("Tabata", tabata));

        List<TimerInterval> tea = new ArrayList<TimerInterval>();
        // Said once, as the water goes on. Short because a voice that goes on talking is the
        // opposite of what the three minutes are for.
        tea.add(new TimerInterval("Steep", 3 * 60_000L,
                0xFF558B2F, 0xFF1A2A0C, "Deep breath. Soften your shoulders. "
                        + "This time is your own.", 30));
        out.add(new TimerPreset("Tea", tea));

        List<TimerInterval> ramen = new ArrayList<TimerInterval>();
        ramen.add(new TimerInterval("Boil", 4 * 60_000L + 30_000L,
                0xFFFFB300, 0xFF4A3300, "Today's happiness. A meal for myself.", 30));
        out.add(new TimerPreset("Ramen", ramen));

        // Pasta rather than spaghetti, and nine minutes: dried spaghetti is al dente at eight or
        // nine and the packet usually says nine to eleven, but capellini is three and bucatini
        // eleven, so no single number is right for a named shape. A broad name invites the number
        // to be changed, which is the honest thing for a value nobody can pin down.
        List<TimerInterval> pasta = new ArrayList<TimerInterval>();
        pasta.add(new TimerInterval("Boil", 9 * 60_000L,
                0xFFA1887F, 0xFF33251F, "For your health and wallet", 60));
        out.add(new TimerPreset("Pasta", pasta));

        return out;
    }
}
