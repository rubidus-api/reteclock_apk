package com.reteclock.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One saved arrangement: a name, the boxes, and which way up it is for (RFC-0005, D8).
 *
 * A preset used to hold both orientations at once. The owner asked for the two to be listed and
 * chosen separately — which is how a person thinks about them, since a layout drawn for an upright
 * phone has nothing to say about a sideways one — so a preset is now one arrangement for one
 * orientation, and the book keeps two shelves of them.
 *
 * It travels two ways: through the settings file, and as a file of its own in the Import/Export
 * package. So it is written and read as text, and it repairs what it is given. A preset with no
 * boxes is **automatic** — the app's own arrangement — rather than an empty screen, which is the one
 * fault the clock face offers no way back from.
 */
public final class LayoutPreset {

    /** What a preset is called when it has not been given a name. */
    public static final String UNNAMED = "Layout";

    /** The user's own words. Never empty. */
    public final String name;
    /** Whether this arrangement is for the phone lying down. */
    public final boolean landscape;

    private final List<LayoutBox> boxes;

    private LayoutPreset(String name, boolean landscape, List<LayoutBox> boxes) {
        String trimmed = name == null ? "" : name.trim();
        this.name = trimmed.isEmpty() ? UNNAMED : trimmed;
        this.landscape = landscape;
        List<LayoutBox> out = new ArrayList<LayoutBox>();
        if (boxes != null) {
            for (LayoutBox box : boxes) {
                if (box != null) {
                    out.add(box);
                }
            }
        }
        this.boxes = Collections.unmodifiableList(out);
    }

    public static LayoutPreset of(String name, boolean landscape, List<LayoutBox> boxes) {
        return new LayoutPreset(name, landscape, boxes);
    }

    /** The boxes this arrangement draws. Empty means {@link #isAutomatic()}. */
    public List<LayoutBox> boxes() {
        return boxes;
    }

    /** Whether this preset draws nothing of its own — the app arranges it instead. */
    public boolean isAutomatic() {
        return boxes.isEmpty();
    }

    public LayoutPreset named(String name) {
        return new LayoutPreset(name, landscape, boxes);
    }

    public LayoutPreset with(List<LayoutBox> boxes) {
        return new LayoutPreset(name, landscape, boxes);
    }

    /** The same arrangement, turned the other way up — what "copy to the other orientation" needs. */
    public LayoutPreset turned() {
        return new LayoutPreset(name, !landscape, boxes);
    }

    /**
     * The text a settings file or a package holds.
     *
     * The name is escaped: it is the user's words, and a name that could hold a newline could add a
     * box.
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("name=").append(escape(name)).append('\n');
        out.append("way=").append(landscape ? "landscape" : "portrait").append('\n');
        for (LayoutBox box : boxes) {
            out.append("box=").append(box.text()).append('\n');
        }
        return out.toString();
    }

    /**
     * A preset read back, or null when the text holds nothing at all.
     *
     * Text written by the first version of this feature held both orientations in one preset, under
     * `portrait=` and `landscape=` keys. That is read as the orientation this preset is for, so an
     * old file loses nothing when it is split: see {@link #parseAll}.
     */
    public static LayoutPreset parse(String text) {
        List<LayoutPreset> all = parseAll(text);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Every preset in this text: one, or two where an old two-orientation preset was found.
     *
     * @return empty when the text holds no preset at all
     */
    public static List<LayoutPreset> parseAll(String text) {
        List<LayoutPreset> out = new ArrayList<LayoutPreset>(2);
        if (text == null || text.trim().isEmpty()) {
            return out;
        }
        String name = null;
        Boolean way = null;
        List<LayoutBox> boxes = new ArrayList<LayoutBox>();
        List<LayoutBox> oldPortrait = new ArrayList<LayoutBox>();
        List<LayoutBox> oldLandscape = new ArrayList<LayoutBox>();

        for (String raw : text.split("\n")) {
            String line = raw.trim();
            int equals = line.indexOf('=');
            if (line.isEmpty() || equals < 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1);
            if ("name".equals(key)) {
                name = unescape(value);
            } else if ("way".equals(key)) {
                way = Boolean.valueOf(value.trim().startsWith("l"));
            } else if ("box".equals(key)) {
                add(boxes, value);
            } else if ("portrait".equals(key)) {
                add(oldPortrait, value);
            } else if ("landscape".equals(key)) {
                add(oldLandscape, value);
            }
            // anything else belongs to a later version and is skipped rather than refused
        }

        if (!oldPortrait.isEmpty() || !oldLandscape.isEmpty()) {
            // The old shape: one name, two arrangements. Each becomes a preset of its own.
            if (!oldPortrait.isEmpty()) {
                out.add(new LayoutPreset(name, false, oldPortrait));
            }
            if (!oldLandscape.isEmpty()) {
                out.add(new LayoutPreset(name, true, oldLandscape));
            }
            return out;
        }
        if (name == null && boxes.isEmpty() && way == null) {
            return out;
        }
        out.add(new LayoutPreset(name, way != null && way.booleanValue(), boxes));
        return out;
    }

    private static void add(List<LayoutBox> out, String value) {
        LayoutBox box = LayoutBox.parse(value.trim());
        if (box != null) {
            out.add(box);
        }
    }

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
                out.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
