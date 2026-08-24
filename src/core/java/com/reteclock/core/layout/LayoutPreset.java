package com.reteclock.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One saved layout: a name and both orientations (RFC-0005, D2 and D8).
 *
 * This is what the user keeps, switches to, copies and sends to somebody else. It travels two ways —
 * through the settings file, and as a file of its own in the Import/Export package — so it is
 * written and read as text, and it repairs what it is given.
 *
 * The important repair: an orientation with no boxes means **automatic**, the app's own arrangement,
 * not an empty screen. A preset that draws nothing is the one fault the clock face offers no way
 * back from, and a half-written file must not be able to cause it.
 *
 * A value. Renaming or redrawing answers another preset, which is what lets the editor keep an undo
 * stack and the settings screen show a list that never changes under it.
 */
public final class LayoutPreset {

    /** What a preset is called when it has not been given a name. */
    public static final String UNNAMED = "Layout";

    /** The user's own words. Never empty. */
    public final String name;

    private final List<LayoutBox> portrait;
    private final List<LayoutBox> landscape;

    private LayoutPreset(String name, List<LayoutBox> portrait, List<LayoutBox> landscape) {
        String trimmed = name == null ? "" : name.trim();
        this.name = trimmed.isEmpty() ? UNNAMED : trimmed;
        this.portrait = lock(portrait);
        this.landscape = lock(landscape);
    }

    private static List<LayoutBox> lock(List<LayoutBox> boxes) {
        List<LayoutBox> out = new ArrayList<LayoutBox>();
        if (boxes != null) {
            for (LayoutBox box : boxes) {
                if (box != null) {
                    out.add(box);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static LayoutPreset of(String name, List<LayoutBox> portrait,
            List<LayoutBox> landscape) {
        return new LayoutPreset(name, portrait, landscape);
    }

    /** The boxes for the phone standing up. Empty means {@link #automaticPortrait()}. */
    public List<LayoutBox> portrait() {
        return portrait;
    }

    /** And lying down. */
    public List<LayoutBox> landscape() {
        return landscape;
    }

    /** Whether this orientation is left to the app's own arrangement. */
    public boolean automaticPortrait() {
        return portrait.isEmpty();
    }

    public boolean automaticLandscape() {
        return landscape.isEmpty();
    }

    /** Whether this preset draws nothing of its own at all, either way up. */
    public boolean isAutomatic() {
        return automaticPortrait() && automaticLandscape();
    }

    public LayoutPreset named(String name) {
        return new LayoutPreset(name, portrait, landscape);
    }

    public LayoutPreset withPortrait(List<LayoutBox> boxes) {
        return new LayoutPreset(name, boxes, landscape);
    }

    public LayoutPreset withLandscape(List<LayoutBox> boxes) {
        return new LayoutPreset(name, portrait, boxes);
    }

    /**
     * The text a settings file or a package holds.
     *
     * One key per line: the name, then a line per box. The name is escaped, because it is the user's
     * words and their words may contain a newline or the character a box line is split on — a name
     * must never be able to add a box.
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("name=").append(escape(name)).append('\n');
        for (LayoutBox box : portrait) {
            out.append("portrait=").append(box.text()).append('\n');
        }
        for (LayoutBox box : landscape) {
            out.append("landscape=").append(box.text()).append('\n');
        }
        return out.toString();
    }

    /**
     * A preset read back from {@link #text()}.
     *
     * @return the preset, or null when the text holds nothing at all — which is not a damaged preset
     *         but no preset
     */
    public static LayoutPreset parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String name = null;
        List<LayoutBox> portrait = new ArrayList<LayoutBox>();
        List<LayoutBox> landscape = new ArrayList<LayoutBox>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;                       // a line that says nothing; the future may add some
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1);
            if ("name".equals(key)) {
                name = unescape(value);
            } else if ("portrait".equals(key)) {
                add(portrait, value);
            } else if ("landscape".equals(key)) {
                add(landscape, value);
            }
            // anything else is a key from a later version, and is skipped rather than refused
        }
        return new LayoutPreset(name, portrait, landscape);
    }

    private static void add(List<LayoutBox> out, String value) {
        LayoutBox box = LayoutBox.parse(value.trim());
        if (box != null) {
            out.add(box);
        }
    }

    /** A name goes on one line and cannot be allowed to become two, or to look like a box. */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') {
                    out.append('\n');
                } else if (next == 'r') {
                    out.append('\r');
                } else {
                    out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
