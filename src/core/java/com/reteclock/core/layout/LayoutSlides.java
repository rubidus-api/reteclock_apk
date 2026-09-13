package com.reteclock.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Layouts in turn (issue #53, RFC-0013): for each way up, a list of layouts played top to bottom and
 * round again, each for its own time, and whether each shows a background.
 *
 * <p>Arithmetic and text only. Which slide is in force is a function of the instant, counted on the
 * wall clock from {@link #anchorMillis} — the moment the list was last changed — so a clock that is
 * opened and left many times a day carries on where the show is rather than starting it again
 * (RFC-0013 D2). A row whose layout is not on the shelf any more is skipped; when nothing playable
 * is left there is no slide in force and the layout chosen on the *Layout* page shows.
 *
 * <p>Immutable. Rows arrive from files other phones wrote, so everything is made safe here.
 */
public final class LayoutSlides {

    public static final int MIN_SECONDS = 10;
    public static final int MAX_SECONDS = 24 * 60 * 60;
    public static final int DEFAULT_SECONDS = 5 * 60;

    public static final LayoutSlides NONE = new LayoutSlides(null, null, false, false, 0L);

    /** One row: a layout by name, how long it stays, and whether it shows a background. */
    public static final class Slide {
        public final String layout;
        public final int seconds;
        public final boolean background;

        private Slide(String layout, int seconds, boolean background) {
            this.layout = layout == null ? "" : layout.trim();
            this.seconds = seconds < MIN_SECONDS ? MIN_SECONDS
                    : seconds > MAX_SECONDS ? MAX_SECONDS : seconds;
            this.background = background;
        }

        public static Slide of(String layout, int seconds, boolean background) {
            return new Slide(layout, seconds, background);
        }

        public Slide withLayout(String name) {
            return new Slide(name, seconds, background);
        }

        public Slide withSeconds(int value) {
            return new Slide(layout, value, background);
        }

        public Slide withBackground(boolean shows) {
            return new Slide(layout, seconds, shows);
        }
    }

    private final List<Slide> portrait;
    private final List<Slide> landscape;
    private final boolean portraitOn;
    private final boolean landscapeOn;
    private final long anchor;

    private LayoutSlides(List<Slide> portrait, List<Slide> landscape, boolean portraitOn,
            boolean landscapeOn, long anchor) {
        this.portrait = copy(portrait);
        this.landscape = copy(landscape);
        this.portraitOn = portraitOn;
        this.landscapeOn = landscapeOn;
        this.anchor = anchor;
    }

    private static List<Slide> copy(List<Slide> rows) {
        List<Slide> out = new ArrayList<Slide>();
        if (rows != null) {
            for (Slide row : rows) {
                if (row != null && !row.layout.isEmpty()) {
                    out.add(row);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    public List<Slide> rows(boolean sideways) {
        return sideways ? landscape : portrait;
    }

    public boolean isOn(boolean sideways) {
        return sideways ? landscapeOn : portraitOn;
    }

    /** The instant the show is counted from. */
    public long anchorMillis() {
        return anchor;
    }

    // ---- changing it ----------------------------------------------------------------------------

    private LayoutSlides with(boolean sideways, List<Slide> rows) {
        return sideways ? new LayoutSlides(portrait, rows, portraitOn, landscapeOn, anchor)
                : new LayoutSlides(rows, landscape, portraitOn, landscapeOn, anchor);
    }

    public LayoutSlides withOn(boolean sideways, boolean on) {
        return sideways ? new LayoutSlides(portrait, landscape, portraitOn, on, anchor)
                : new LayoutSlides(portrait, landscape, on, landscapeOn, anchor);
    }

    /** The same list counted from another instant — what saving a change does (D2). */
    public LayoutSlides anchoredAt(long epochMillis) {
        return new LayoutSlides(portrait, landscape, portraitOn, landscapeOn, epochMillis);
    }

    public LayoutSlides add(boolean sideways, Slide slide) {
        List<Slide> rows = new ArrayList<Slide>(rows(sideways));
        rows.add(slide);
        return with(sideways, rows);
    }

    public LayoutSlides replaced(boolean sideways, int index, Slide slide) {
        List<Slide> rows = new ArrayList<Slide>(rows(sideways));
        if (index < 0 || index >= rows.size() || slide == null) {
            return this;
        }
        rows.set(index, slide);
        return with(sideways, rows);
    }

    public LayoutSlides removed(boolean sideways, int index) {
        List<Slide> rows = new ArrayList<Slide>(rows(sideways));
        if (index < 0 || index >= rows.size()) {
            return this;
        }
        rows.remove(index);
        return with(sideways, rows);
    }

    /** A row moved by {@code step} places; one that would leave the list stays where it is. */
    public LayoutSlides moved(boolean sideways, int index, int step) {
        List<Slide> rows = new ArrayList<Slide>(rows(sideways));
        int to = index + step;
        if (index < 0 || index >= rows.size() || to < 0 || to >= rows.size()) {
            return this;
        }
        rows.add(to, rows.remove(index));
        return with(sideways, rows);
    }

    /** Every row naming {@code from} on this shelf now names {@code to} (D5). */
    public LayoutSlides renamed(boolean sideways, String from, String to) {
        List<Slide> rows = new ArrayList<Slide>();
        boolean changed = false;
        for (Slide row : rows(sideways)) {
            if (row.layout.equals(from)) {
                rows.add(row.withLayout(to));
                changed = true;
            } else {
                rows.add(row);
            }
        }
        return changed ? with(sideways, rows) : this;
    }

    // ---- what is in force -----------------------------------------------------------------------

    /** The slide in force at this instant, or null when slides are off or nothing is playable. */
    public Slide current(boolean sideways, long nowMillis, Set<String> shelf) {
        int row = currentRow(sideways, nowMillis, shelf);
        return row < 0 ? null : rows(sideways).get(row);
    }

    /**
     * Which row is in force, by its place in {@link #rows}, or -1.
     *
     * The row rather than the layout, because the same layout may be listed twice with a different
     * background, and a change from one to the other is a change the clock must redraw for.
     */
    public int currentRow(boolean sideways, long nowMillis, Set<String> shelf) {
        long[] at = position(sideways, nowMillis, shelf);
        return at == null ? -1 : (int) at[0];
    }

    /** When the row in force ends, or {@link Long#MAX_VALUE} when there is none. */
    public long nextChange(boolean sideways, long nowMillis, Set<String> shelf) {
        long[] at = position(sideways, nowMillis, shelf);
        return at == null ? Long.MAX_VALUE : at[1];
    }

    /** {row, the instant it ends}, or null. */
    private long[] position(boolean sideways, long nowMillis, Set<String> shelf) {
        if (!isOn(sideways)) {
            return null;
        }
        List<Slide> rows = rows(sideways);
        long total = 0;
        for (Slide row : rows) {
            if (playable(row, shelf)) {
                total += row.seconds * 1000L;
            }
        }
        if (total == 0) {
            return null;
        }
        long offset = (nowMillis - anchor) % total;
        if (offset < 0) {
            offset += total;
        }
        long start = nowMillis - offset;
        long reached = 0;
        for (int i = 0; i < rows.size(); i++) {
            Slide row = rows.get(i);
            if (!playable(row, shelf)) {
                continue;
            }
            long end = reached + row.seconds * 1000L;
            if (offset < end) {
                return new long[] {i, start + end};
            }
            reached = end;
        }
        return null;
    }

    private static boolean playable(Slide row, Set<String> shelf) {
        return shelf == null || shelf.contains(row.layout);
    }

    // ---- text -----------------------------------------------------------------------------------

    /**
     * <pre>
     *   on=portraitOn,landscapeOn
     *   anchor=epochMillis
     *   (P|L) '|' seconds '|' background '|' layout      one line per row, in order
     * </pre>
     * Layout names are C identifiers (LayoutName), so a bar cannot be part of one.
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("on=").append(portraitOn ? '1' : '0').append(',')
                .append(landscapeOn ? '1' : '0').append('\n');
        out.append("anchor=").append(anchor).append('\n');
        line(out, 'P', portrait);
        line(out, 'L', landscape);
        return out.toString();
    }

    private static void line(StringBuilder out, char way, List<Slide> rows) {
        for (Slide row : rows) {
            out.append(way).append('|').append(row.seconds).append('|')
                    .append(row.background ? '1' : '0').append('|').append(row.layout).append('\n');
        }
    }

    public static LayoutSlides parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return NONE;
        }
        boolean portraitOn = false;
        boolean landscapeOn = false;
        long anchor = 0L;
        List<Slide> portrait = new ArrayList<Slide>();
        List<Slide> landscape = new ArrayList<Slide>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("on=")) {
                String[] parts = line.substring(3).split(",");
                portraitOn = parts.length > 0 && "1".equals(parts[0].trim());
                landscapeOn = parts.length > 1 && "1".equals(parts[1].trim());
                continue;
            }
            if (line.startsWith("anchor=")) {
                try {
                    anchor = Long.parseLong(line.substring(7).trim());
                } catch (NumberFormatException ignored) {
                    anchor = 0L;
                }
                continue;
            }
            String[] fields = line.split("\\|");
            if (fields.length < 4 || !("P".equals(fields[0]) || "L".equals(fields[0]))) {
                continue;
            }
            int seconds;
            try {
                seconds = Integer.parseInt(fields[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            Slide row = Slide.of(fields[3], seconds, "1".equals(fields[2].trim()));
            ("L".equals(fields[0]) ? landscape : portrait).add(row);
        }
        return new LayoutSlides(portrait, landscape, portraitOn, landscapeOn, anchor);
    }
}
