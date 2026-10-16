package com.reteclock.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Layouts by swiping (issue #70, R144): for each way up, a list of layouts that a sideways swipe on
 * the clock goes through, in the user's order and round again.
 *
 * <p>It is one of three turns a way up can take — none, by swiping, or timed ({@link LayoutSlides})
 * — so a swipe never meets a timed list playing (owner, 2026-10-16). The two lists are kept apart
 * and each keeps its rows whichever is chosen; only the switches say which plays. A file written by
 * hand with both on for one way up plays the swipe.
 *
 * <p>Where a swipe lands is a layout by name; the clock then makes it the layout chosen on the
 * Layout page, so it is kept across a restart (owner). A row whose layout is not on the shelf any
 * more is skipped. Immutable; rows arrive from files other phones wrote.
 */
public final class LayoutSwipe {

    public static final int TURNS_NONE = 0;
    public static final int TURNS_SWIPE = 1;
    public static final int TURNS_TIMED = 2;

    public static final LayoutSwipe NONE = new LayoutSwipe(null, null, false, false);

    private final List<String> portrait;
    private final List<String> landscape;
    private final boolean portraitOn;
    private final boolean landscapeOn;

    private LayoutSwipe(List<String> portrait, List<String> landscape, boolean portraitOn,
            boolean landscapeOn) {
        this.portrait = copy(portrait);
        this.landscape = copy(landscape);
        this.portraitOn = portraitOn;
        this.landscapeOn = landscapeOn;
    }

    private static List<String> copy(List<String> rows) {
        List<String> out = new ArrayList<String>();
        if (rows != null) {
            for (String row : rows) {
                String name = row == null ? "" : row.trim();
                if (!name.isEmpty() && name.indexOf('|') < 0 && !out.contains(name)) {
                    out.add(name);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    public List<String> rows(boolean sideways) {
        return sideways ? landscape : portrait;
    }

    public boolean isOn(boolean sideways) {
        return sideways ? landscapeOn : portraitOn;
    }

    // ---- changing it ----------------------------------------------------------------------------

    private LayoutSwipe with(boolean sideways, List<String> rows) {
        return sideways ? new LayoutSwipe(portrait, rows, portraitOn, landscapeOn)
                : new LayoutSwipe(rows, landscape, portraitOn, landscapeOn);
    }

    public LayoutSwipe withOn(boolean sideways, boolean on) {
        return sideways ? new LayoutSwipe(portrait, landscape, portraitOn, on)
                : new LayoutSwipe(portrait, landscape, on, landscapeOn);
    }

    /** A layout added at the end; one already listed stays where it is. */
    public LayoutSwipe add(boolean sideways, String layout) {
        List<String> rows = new ArrayList<String>(rows(sideways));
        rows.add(layout);
        return with(sideways, rows);
    }

    public LayoutSwipe removed(boolean sideways, int index) {
        List<String> rows = new ArrayList<String>(rows(sideways));
        if (index < 0 || index >= rows.size()) {
            return this;
        }
        rows.remove(index);
        return with(sideways, rows);
    }

    /** A row moved by {@code step} places; one that would leave the list stays where it is. */
    public LayoutSwipe moved(boolean sideways, int index, int step) {
        List<String> rows = new ArrayList<String>(rows(sideways));
        int to = index + step;
        if (index < 0 || index >= rows.size() || to < 0 || to >= rows.size()) {
            return this;
        }
        rows.add(to, rows.remove(index));
        return with(sideways, rows);
    }

    /** A layout renamed on this shelf keeps its place in the list. */
    public LayoutSwipe renamed(boolean sideways, String from, String to) {
        List<String> rows = new ArrayList<String>(rows(sideways));
        int at = rows.indexOf(from);
        if (at < 0) {
            return this;
        }
        rows.set(at, to);
        return with(sideways, rows);
    }

    // ---- where a swipe lands --------------------------------------------------------------------

    /**
     * The layout a swipe goes to from {@code current}: the next one for {@code step} 1, the one
     * before for -1, round at the ends. From a layout not in the list the next is the first and the
     * one before the last. Null when swiping is off for this way up or nothing listed is on the
     * shelf.
     */
    public String step(boolean sideways, String current, int step, Set<String> shelf) {
        if (!isOn(sideways) || step == 0) {
            return null;
        }
        List<String> playable = new ArrayList<String>();
        for (String row : rows(sideways)) {
            if (shelf == null || shelf.contains(row)) {
                playable.add(row);
            }
        }
        int count = playable.size();
        if (count == 0) {
            return null;
        }
        int at = playable.indexOf(current);
        if (at < 0) {
            return playable.get(step > 0 ? 0 : count - 1);
        }
        int to = ((at + (step > 0 ? 1 : -1)) % count + count) % count;
        return playable.get(to);
    }

    // ---- which of the three turns ---------------------------------------------------------------

    /** None, by swiping, or timed, for this way up. */
    public static int turns(LayoutSlides slides, LayoutSwipe swipe, boolean sideways) {
        if (swipe.isOn(sideways)) {
            return TURNS_SWIPE;
        }
        return slides.isOn(sideways) ? TURNS_TIMED : TURNS_NONE;
    }

    /** The timed list as it plays: switched off for a way up that swipes. */
    public static LayoutSlides playing(LayoutSlides slides, LayoutSwipe swipe) {
        LayoutSlides out = slides;
        for (boolean sideways : new boolean[] {false, true}) {
            if (swipe.isOn(sideways)) {
                out = out.withOn(sideways, false);
            }
        }
        return out;
    }

    /** The timed list's switch for this way up when {@code turns} is chosen. */
    public static LayoutSlides slidesFor(LayoutSlides slides, boolean sideways, int turns) {
        return slides.withOn(sideways, turns == TURNS_TIMED);
    }

    /** The swipe's switch for this way up when {@code turns} is chosen. */
    public static LayoutSwipe swipeFor(LayoutSwipe swipe, boolean sideways, int turns) {
        return swipe.withOn(sideways, turns == TURNS_SWIPE);
    }

    // ---- text -----------------------------------------------------------------------------------

    /**
     * <pre>
     *   on=portraitOn,landscapeOn
     *   (P|L) '|' layout      one line per row, in order
     * </pre>
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("on=").append(portraitOn ? '1' : '0').append(',')
                .append(landscapeOn ? '1' : '0').append('\n');
        for (String row : portrait) {
            out.append("P|").append(row).append('\n');
        }
        for (String row : landscape) {
            out.append("L|").append(row).append('\n');
        }
        return out.toString();
    }

    public static LayoutSwipe parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return NONE;
        }
        boolean portraitOn = false;
        boolean landscapeOn = false;
        List<String> portrait = new ArrayList<String>();
        List<String> landscape = new ArrayList<String>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("on=")) {
                String[] parts = line.substring(3).split(",");
                portraitOn = parts.length > 0 && "1".equals(parts[0].trim());
                landscapeOn = parts.length > 1 && "1".equals(parts[1].trim());
                continue;
            }
            String[] fields = line.split("\\|", -1);
            if (fields.length != 2 || !("P".equals(fields[0]) || "L".equals(fields[0]))) {
                continue;
            }
            ("L".equals(fields[0]) ? landscape : portrait).add(fields[1]);
        }
        return new LayoutSwipe(portrait, landscape, portraitOn, landscapeOn);
    }
}
