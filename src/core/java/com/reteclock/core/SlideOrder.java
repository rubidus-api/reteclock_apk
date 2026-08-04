package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The order the background images show and list in.
 *
 * Pure Java, so the rules are unit tested on a JVM. Four sorts — by name or by the date the file
 * was stored, each way — and the user's own arrangement, kept as a list of names. The settings
 * screen and the slideshow both go through here, so they can never disagree about the order.
 */
public final class SlideOrder {

    /** By file name. The default: it is what the show has always done. */
    public static final int NAME_ASC = 0;
    public static final int NAME_DESC = 1;
    /** By when the file was stored — import time, since nothing edits a stored file. */
    public static final int DATE_ASC = 2;
    public static final int DATE_DESC = 3;
    /** The user's own arrangement, made by moving entries in the settings screen. */
    public static final int CUSTOM = 4;

    private SlideOrder() {
    }

    /**
     * These entries in this mode's order. An unknown mode reads as {@link #NAME_ASC}, so a stored
     * mode from some future version still produces a working show.
     *
     * In {@link #CUSTOM} the stored names come first, in their stored order; a name whose file is
     * gone is skipped; files the arrangement does not know join at the end in name order — a new
     * import appends, it does not shuffle what the user arranged.
     */
    public static List<FontLibrary.Entry> apply(List<FontLibrary.Entry> entries, int mode,
            List<String> custom) {
        List<FontLibrary.Entry> out = new ArrayList<FontLibrary.Entry>(entries);
        Collections.sort(out, byName(false));
        switch (mode) {
            case NAME_DESC:
                Collections.reverse(out);
                return out;
            case DATE_ASC:
                Collections.sort(out, byDate(false));
                return out;
            case DATE_DESC:
                Collections.sort(out, byDate(true));
                return out;
            case CUSTOM:
                return customOrder(out, custom);
            case NAME_ASC:
            default:
                return out;
        }
    }

    private static List<FontLibrary.Entry> customOrder(List<FontLibrary.Entry> nameSorted,
            List<String> custom) {
        List<FontLibrary.Entry> out = new ArrayList<FontLibrary.Entry>(nameSorted.size());
        Set<String> placed = new HashSet<String>();
        for (String name : custom) {
            for (FontLibrary.Entry entry : nameSorted) {
                if (entry.name.equals(name) && placed.add(name)) {
                    out.add(entry);
                }
            }
        }
        for (FontLibrary.Entry entry : nameSorted) {
            if (!placed.contains(entry.name)) {
                out.add(entry);
            }
        }
        return out;
    }

    /** {@code names} with the entry at {@code index} stepped by {@code direction} (-1 up, +1 down). */
    public static List<String> moved(List<String> names, int index, int direction) {
        int to = index + direction;
        if (index < 0 || index >= names.size() || to < 0 || to >= names.size()) {
            return new ArrayList<String>(names);
        }
        List<String> out = new ArrayList<String>(names);
        Collections.swap(out, index, to);
        return out;
    }

    private static Comparator<FontLibrary.Entry> byName(final boolean reverse) {
        return new Comparator<FontLibrary.Entry>() {
            @Override
            public int compare(FontLibrary.Entry a, FontLibrary.Entry b) {
                int order = a.name.compareTo(b.name);
                return reverse ? -order : order;
            }
        };
    }

    /** Date, then name — sorting must never be arbitrary, or the show reshuffles between reads. */
    private static Comparator<FontLibrary.Entry> byDate(final boolean reverse) {
        return new Comparator<FontLibrary.Entry>() {
            @Override
            public int compare(FontLibrary.Entry a, FontLibrary.Entry b) {
                if (a.modifiedMs != b.modifiedMs) {
                    int order = a.modifiedMs < b.modifiedMs ? -1 : 1;
                    return reverse ? -order : order;
                }
                return a.name.compareTo(b.name);
            }
        };
    }
}
