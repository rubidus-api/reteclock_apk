package com.reteclock.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The layouts the user keeps: two shelves, one for each way up (RFC-0005, D7 and D8).
 *
 * A layout drawn for an upright phone says nothing about a sideways one, so the two are listed and
 * chosen separately — the owner's call, and the way a person thinks about them.
 *
 * Each shelf begins with **Automatic**: the app's own arrangement, steered by the options it has
 * always had. It is always there, on both shelves, and it cannot be renamed, redrawn, copied or
 * removed. It is the way back when a drawn layout goes wrong, and a book that could lose it could
 * leave somebody with no layout at all.
 *
 * Two presets on one shelf may not share a name — the list is how a person tells them apart — so a
 * clash is **numbered** rather than refused, which is what a preset arriving from another phone
 * needs.
 */
public final class LayoutBook {

    /** What the first entry on each shelf is called. */
    public static final String AUTOMATIC_NAME = "Automatic";

    private final List<LayoutPreset> portrait;
    private final List<LayoutPreset> landscape;
    private final int chosenPortrait;
    private final int chosenLandscape;

    private LayoutBook(List<LayoutPreset> portrait, List<LayoutPreset> landscape,
            int chosenPortrait, int chosenLandscape) {
        this.portrait = shelf(portrait, false);
        this.landscape = shelf(landscape, true);
        this.chosenPortrait = fit(chosenPortrait, this.portrait.size());
        this.chosenLandscape = fit(chosenLandscape, this.landscape.size());
    }

    /** One shelf: Automatic first, then the user's, each under a name nothing else has. */
    private static List<LayoutPreset> shelf(List<LayoutPreset> given, boolean landscape) {
        List<LayoutPreset> out = new ArrayList<LayoutPreset>();
        out.add(LayoutPreset.of(AUTOMATIC_NAME, landscape, null));
        if (given != null) {
            for (LayoutPreset preset : given) {
                if (preset == null || preset.isAutomatic()) {
                    continue;           // there is one Automatic and this class owns it
                }
                out.add(preset.landscape == landscape ? preset : preset.turned());
            }
        }
        // Names are made unique in a second pass, so the first of a clash keeps what it was called.
        List<LayoutPreset> named = new ArrayList<LayoutPreset>(out.size());
        for (LayoutPreset preset : out) {
            named.add(preset.named(free(named, preset.name)));
        }
        return Collections.unmodifiableList(named);
    }

    private static int fit(int index, int size) {
        return index < 0 || index >= size ? 0 : index;
    }

    /** A book with nothing but Automatic on each shelf. */
    public static LayoutBook empty() {
        return new LayoutBook(null, null, 0, 0);
    }

    /** The presets for one way up, Automatic first. */
    public List<LayoutPreset> shelf(boolean landscape) {
        return landscape ? this.landscape : this.portrait;
    }

    public int size(boolean landscape) {
        return shelf(landscape).size();
    }

    public LayoutPreset get(boolean landscape, int index) {
        List<LayoutPreset> shelf = shelf(landscape);
        return index < 0 || index >= shelf.size() ? shelf.get(0) : shelf.get(index);
    }

    public int chosenIndex(boolean landscape) {
        return landscape ? chosenLandscape : chosenPortrait;
    }

    /** The arrangement in force for this way up. */
    public LayoutPreset chosen(boolean landscape) {
        return shelf(landscape).get(chosenIndex(landscape));
    }

    /** The same book with this preset added to the shelf it belongs on. */
    public LayoutBook add(LayoutPreset preset) {
        if (preset == null || preset.isAutomatic()) {
            return this;
        }
        List<LayoutPreset> mine = rest(preset.landscape);
        mine.add(preset);
        return preset.landscape
                ? new LayoutBook(rest(false), mine, chosenPortrait, chosenLandscape)
                : new LayoutBook(mine, rest(true), chosenPortrait, chosenLandscape);
    }

    /** A copy of one of them. Automatic is not copied: there is one of it and the app owns it. */
    public LayoutBook duplicate(boolean landscape, int index) {
        if (index <= 0 || index >= size(landscape)) {
            return this;
        }
        return add(get(landscape, index));
    }

    /** The same preset on the other shelf, for a layout drawn one way up and wanted both. */
    public LayoutBook copyToOtherWay(boolean landscape, int index) {
        if (index <= 0 || index >= size(landscape)) {
            return this;
        }
        return add(get(landscape, index).turned());
    }

    /** The same book with that one gone. Automatic cannot go. */
    public LayoutBook remove(boolean landscape, int index) {
        if (index <= 0 || index >= size(landscape)) {
            return this;
        }
        LayoutPreset inForce = chosen(landscape);
        List<LayoutPreset> mine = rest(landscape);
        mine.remove(index - 1);
        // The preset in force stays in force wherever the removal moved it to; keeping the *number*
        // would quietly switch the user's clock to another layout.
        int nowChosen = 0;
        for (int i = 0; i < mine.size(); i++) {
            if (mine.get(i) == inForce) {
                nowChosen = i + 1;
                break;
            }
        }
        return landscape
                ? new LayoutBook(rest(false), mine, chosenPortrait, nowChosen)
                : new LayoutBook(mine, rest(true), nowChosen, chosenLandscape);
    }

    public LayoutBook rename(boolean landscape, int index, String name) {
        return replace(landscape, index, get(landscape, index).named(name));
    }

    /** The same book with that entry redrawn. Automatic cannot be redrawn. */
    public LayoutBook replace(boolean landscape, int index, LayoutPreset preset) {
        if (index <= 0 || index >= size(landscape) || preset == null) {
            return this;
        }
        List<LayoutPreset> mine = rest(landscape);
        mine.set(index - 1, preset.landscape == landscape ? preset : preset.turned());
        return landscape
                ? new LayoutBook(rest(false), mine, chosenPortrait, chosenLandscape)
                : new LayoutBook(mine, rest(true), chosenPortrait, chosenLandscape);
    }

    /** The same book with another arrangement in force for this way up. */
    public LayoutBook choose(boolean landscape, int index) {
        return landscape
                ? new LayoutBook(rest(false), rest(true), chosenPortrait, index)
                : new LayoutBook(rest(false), rest(true), index, chosenLandscape);
    }

    /** The text the settings file holds. */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("chosen=").append(chosenPortrait).append(',').append(chosenLandscape)
                .append('\n');
        for (int i = 1; i < portrait.size(); i++) {
            out.append("--\n").append(portrait.get(i).text());
        }
        for (int i = 1; i < landscape.size(); i++) {
            out.append("--\n").append(landscape.get(i).text());
        }
        return out.toString();
    }

    /**
     * A book read back. Anything unreadable still answers a usable book.
     *
     * Text written by the first version held one shelf and one chosen number, with each preset
     * carrying both orientations. Such a preset is split in two — one for each shelf — so nothing
     * anybody drew is lost when the shape changes under them.
     */
    public static LayoutBook parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return empty();
        }
        int chosenPortrait = 0;
        int chosenLandscape = 0;
        List<LayoutPreset> portrait = new ArrayList<LayoutPreset>();
        List<LayoutPreset> landscape = new ArrayList<LayoutPreset>();

        for (String chunk : text.split("\n--\n|^--\n")) {
            if (chunk.trim().isEmpty()) {
                continue;
            }
            if (chunk.startsWith("chosen=")) {
                String[] lines = chunk.split("\n", 2);
                String[] numbers = lines[0].substring("chosen=".length()).trim().split(",");
                chosenPortrait = number(numbers.length > 0 ? numbers[0] : "0");
                chosenLandscape = number(numbers.length > 1 ? numbers[1] : numbers[0]);
                if (lines.length > 1 && !lines[1].trim().isEmpty()) {
                    sort(LayoutPreset.parseAll(lines[1]), portrait, landscape);
                }
                continue;
            }
            sort(LayoutPreset.parseAll(chunk), portrait, landscape);
        }
        return new LayoutBook(portrait, landscape, chosenPortrait, chosenLandscape);
    }

    private static void sort(List<LayoutPreset> parsed, List<LayoutPreset> portrait,
            List<LayoutPreset> landscape) {
        for (LayoutPreset preset : parsed) {
            if (preset.isAutomatic()) {
                continue;
            }
            (preset.landscape ? landscape : portrait).add(preset);
        }
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** One shelf without its Automatic, which every constructor puts back. */
    private List<LayoutPreset> rest(boolean landscape) {
        List<LayoutPreset> shelf = shelf(landscape);
        return new ArrayList<LayoutPreset>(shelf.subList(1, shelf.size()));
    }

    /** {@code name}, or {@code name 2}, {@code name 3}… — whichever is not taken on this shelf. */
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
