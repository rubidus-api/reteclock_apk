package com.reteclock.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The layouts the user keeps, and which one is in force (RFC-0005, D7 and D8).
 *
 * The first entry is always **Automatic**: the app's own arrangement, steered by the options it has
 * always had. It cannot be renamed, redrawn or removed. It is the way back when a drawn layout goes
 * wrong, and a book that could lose it would be a book that could leave a user with no layout at
 * all.
 *
 * Everything after it is the user's: added, renamed, copied, chosen, deleted. Two presets may not
 * share a name — the list is how a person tells them apart — so a clash is **numbered** rather than
 * refused, which is what a file arriving from another phone needs (D8).
 *
 * A value, like everything else in this engine: every change answers a new book.
 */
public final class LayoutBook {

    /** What the first entry is called. */
    public static final String AUTOMATIC_NAME = "Automatic";

    private final List<LayoutPreset> presets;
    private final int chosen;

    private LayoutBook(List<LayoutPreset> presets, int chosen) {
        List<LayoutPreset> out = new ArrayList<LayoutPreset>();
        out.add(automatic());
        if (presets != null) {
            for (int i = 0; i < presets.size(); i++) {
                LayoutPreset preset = presets.get(i);
                // The automatic entry is this class's own; one arriving in a list is skipped rather
                // than kept, or a file could produce two of them.
                if (preset != null && !(i == 0 && preset.isAutomatic())) {
                    out.add(preset.named(free(out, preset.name)));
                }
            }
        }
        this.presets = Collections.unmodifiableList(out);
        this.chosen = chosen < 0 || chosen >= out.size() ? 0 : chosen;
    }

    private static LayoutPreset automatic() {
        return LayoutPreset.of(AUTOMATIC_NAME, null, null);
    }

    /** A book with nothing in it but Automatic — what a phone that has never been edited has. */
    public static LayoutBook empty() {
        return new LayoutBook(null, 0);
    }

    public int size() {
        return presets.size();
    }

    public LayoutPreset get(int index) {
        return index < 0 || index >= presets.size() ? presets.get(0) : presets.get(index);
    }

    public int chosenIndex() {
        return chosen;
    }

    public LayoutPreset chosen() {
        return presets.get(chosen);
    }

    /** The same book with this preset added at the end, and the same one still in force. */
    public LayoutBook add(LayoutPreset preset) {
        if (preset == null) {
            return this;
        }
        List<LayoutPreset> out = mutable();
        out.add(preset.named(free(presets, preset.name)));
        return new LayoutBook(out.subList(1, out.size()), chosen);
    }

    /** A copy of one of them, added at the end under a free name. */
    public LayoutBook duplicate(int index) {
        if (index <= 0 || index >= presets.size()) {
            return this;                    // Automatic is not copied; there is only one of it
        }
        return add(presets.get(index));
    }

    /** The same book with that one gone. Automatic cannot go. */
    public LayoutBook remove(int index) {
        if (index <= 0 || index >= presets.size()) {
            return this;
        }
        LayoutPreset inForce = chosen();
        List<LayoutPreset> out = mutable();
        out.remove(index);
        List<LayoutPreset> rest = out.subList(1, out.size());
        // The preset in force stays in force, wherever the removal moved it to. Keeping the *number*
        // instead would quietly switch the user to a different layout.
        int nowChosen = 0;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == inForce) {
                nowChosen = i;
                break;
            }
        }
        return new LayoutBook(rest, nowChosen);
    }

    /** The same book with that one under another name. Automatic keeps its own. */
    public LayoutBook rename(int index, String name) {
        if (index <= 0 || index >= presets.size()) {
            return this;
        }
        List<LayoutPreset> out = mutable();
        out.set(index, presets.get(index).named(name));
        return new LayoutBook(out.subList(1, out.size()), chosen);
    }

    /** The same book with that entry redrawn. Automatic cannot be redrawn. */
    public LayoutBook replace(int index, LayoutPreset preset) {
        if (index <= 0 || index >= presets.size() || preset == null) {
            return this;
        }
        List<LayoutPreset> out = mutable();
        out.set(index, preset);
        return new LayoutBook(out.subList(1, out.size()), chosen);
    }

    /** The same book with another one in force. A number naming nothing lands on Automatic. */
    public LayoutBook choose(int index) {
        return new LayoutBook(presets.subList(1, presets.size()), index);
    }

    /** The text the settings file holds: which one is chosen, then the presets, separated. */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("chosen=").append(chosen).append('\n');
        for (int i = 1; i < presets.size(); i++) {
            out.append("--\n").append(presets.get(i).text());
        }
        return out.toString();
    }

    /** A book read back. Anything unreadable still answers a usable book — Automatic at least. */
    public static LayoutBook parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return empty();
        }
        int chosen = 0;
        List<LayoutPreset> presets = new ArrayList<LayoutPreset>();
        String[] chunks = text.split("\n--\n|^--\n");
        for (String chunk : chunks) {
            if (chunk.trim().isEmpty()) {
                continue;
            }
            if (chunk.startsWith("chosen=")) {
                String[] lines = chunk.split("\n", 2);
                chosen = number(lines[0].substring("chosen=".length()).trim());
                if (lines.length > 1 && !lines[1].trim().isEmpty()) {
                    addParsed(presets, lines[1]);
                }
                continue;
            }
            addParsed(presets, chunk);
        }
        return new LayoutBook(presets, chosen);
    }

    private static void addParsed(List<LayoutPreset> out, String chunk) {
        LayoutPreset preset = LayoutPreset.parse(chunk);
        if (preset != null && !preset.isAutomatic()) {
            out.add(preset);
        }
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<LayoutPreset> mutable() {
        return new ArrayList<LayoutPreset>(presets);
    }

    /** {@code name}, or {@code name 2}, {@code name 3}… — whichever is not taken yet. */
    private static String free(List<LayoutPreset> taken, String name) {
        String wanted = name == null || name.trim().isEmpty() ? LayoutPreset.UNNAMED : name.trim();
        if (!isTaken(taken, wanted)) {
            return wanted;
        }
        for (int n = 2; n < 1000; n++) {
            String candidate = wanted + " " + n;
            if (!isTaken(taken, candidate)) {
                return candidate;
            }
        }
        return wanted;
    }

    private static boolean isTaken(List<LayoutPreset> presets, String name) {
        for (LayoutPreset preset : presets) {
            if (preset != null && preset.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
