package com.reteclock.core.layout;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * The app's own arrangements, written as boxes (RFC-0005, D1).
 *
 * Two jobs, and they are the same code. It is what a composed layout **starts from** — the user
 * picks the arrangement they already have and moves it — and it is the **proof** that the engine can
 * say what {@link ClockLayout} says: T074 asks both for the same screen and compares them rectangle
 * by rectangle. Nothing is drawn from this engine until that comparison holds for an arrangement.
 *
 * Only the time-only arrangements are converted so far. The rest answer null, which is the honest
 * reply: an arrangement this cannot express must not be guessed at, because the caller would draw
 * the guess.
 */
public final class Builtin {

    private Builtin() {
    }

    /**
     * The boxes for the arrangement these options ask for on this screen, or null where the engine
     * cannot yet say it.
     */
    /** The grid, as a field the engine places and sizes without looking inside it (D6). */
    public static final String FIELD_CALENDAR = "calendar";

    /** The saying's strip along the foot, as a field placed like any other. */
    public static final String FIELD_QUOTE = ClockLayout.ROLE_QUOTE;

    public static List<LayoutBox> of(int screenW, int screenH, ClockOptions options) {
        // The saying takes a strip off the bottom and the clock lays itself out in what is left —
        // so the arrangement is worked out on a shorter screen, and the strip is added afterwards.
        // Expressing it any other way would mean every arrangement had to know about it.
        float quoteHeight = options.quote && !options.timeOnly
                ? Math.min(screenW, screenH) * 0.13f
                : 0f;
        int usable = Math.max(1, Math.round(screenH - quoteHeight));
        List<LayoutBox> out = onScreen(screenW, usable, options);
        if (out == null || quoteHeight <= 0f) {
            return out;
        }
        float pad = ClockLayout.paddingPx(screenW, screenH);
        // The boxes were placed against the shorter screen; they are stored as fractions of the
        // real one, so each is converted rather than re-derived.
        List<LayoutBox> scaled = new ArrayList<LayoutBox>(out.size() + 1);
        for (LayoutBox box : out) {
            scaled.add(box
                    .at(box.anchor, box.x, box.y * usable / screenH)
                    .sized(box.width, box.naturalHeight()
                            ? LayoutBox.NATURAL : box.height * usable / screenH));
        }
        scaled.add(rect(FIELD_QUOTE, screenW, screenH,
                pad, usable, screenW - 2f * pad, Math.max(1f, quoteHeight - pad)));
        return scaled;
    }

    private static List<LayoutBox> onScreen(int screenW, int screenH, ClockOptions options) {
        float pad = ClockLayout.paddingPx(screenW, screenH);
        boolean wide = screenW > screenH;
        if (options.timeOnly) {
            return wide
                    ? wideTimeOnly(screenW, screenH, options, pad)
                    : tallTimeOnly(screenW, screenH, options, pad);
        }
        if (options.calendar) {
            return wide
                    ? wideCalendar(screenW, screenH, options, pad)
                    : tallCalendar(screenW, screenH, options, pad);
        }
        return wide
                ? wideFull(screenW, screenH, options, pad)
                : tallFull(screenW, screenH, options, pad);
    }

    /**
     * A month beside the clock: the time reads down the left, the grid takes the right.
     *
     * The dial that splits the width is the one place it still does that (issue #42), because this
     * is the one arrangement with two things to divide the screen between.
     */
    private static List<LayoutBox> wideCalendar(int w, int h, ClockOptions options, float pad) {
        float mainWidth = w * options.timeFractionWide;
        float boxWidth = mainWidth - 2f * pad;
        float gap = h * 0.02f;
        float content = h - 2f * pad - 2f * gap;
        float bigSize = content * (options.showSeconds ? 0.40f : 0.50f);
        float secondSize = content * 0.20f;
        boolean marker = options.showsMeridiem();
        float meridiemSize = marker ? bigSize * 0.22f : 0f;
        if (marker) {
            bigSize -= meridiemSize / 2f;
        }

        List<LayoutBox> out = new ArrayList<LayoutBox>(5);
        float cursor = pad;
        out.add(line(ClockLayout.ROLE_HOUR, w, h, pad, cursor, bigSize, boxWidth));
        cursor += bigSize + gap;
        out.add(line(ClockLayout.ROLE_MINUTE, w, h, pad, cursor, bigSize, boxWidth));
        cursor += bigSize + gap;
        if (marker) {
            out.add(line(ClockLayout.ROLE_MERIDIEM, w, h, pad, cursor, meridiemSize, boxWidth));
            cursor += meridiemSize + gap;
        }
        if (options.showSeconds) {
            out.add(line(ClockLayout.ROLE_SECOND, w, h, pad, cursor, secondSize, boxWidth));
        }
        out.add(rect(FIELD_CALENDAR, w, h,
                mainWidth + pad, pad, w - mainWidth - 2f * pad, h - 2f * pad));
        return out;
    }

    /** A month under the clock: the time on one line, the grid beneath it, the seconds under that. */
    private static List<LayoutBox> tallCalendar(int w, int h, ClockOptions options, float pad) {
        float boxWidth = w - 2f * pad;
        float gap = h * 0.020f;
        float content = h - 2f * pad - 3f * gap;
        float together = content * options.timeFractionTall;
        float timeSize = together * 0.32f;
        float gridHeight = together - timeSize;
        float smallSize = content * (1f - options.timeFractionTall) * (1f - 0.6f);
        float timeWidth = options.showsMeridiem() ? boxWidth * (1f - 0.15f) : boxWidth;

        List<LayoutBox> out = new ArrayList<LayoutBox>(3);
        float cursor = pad;
        // The time's box is narrowed to leave the marker somewhere to go, and stays centred on the
        // screen: the marker is drawn beyond the box rather than inside it.
        out.add(rect(ClockLayout.ROLE_HOUR_MINUTE, w, h,
                w / 2f - timeWidth / 2f, cursor, timeWidth, timeSize));
        cursor += timeSize + gap;
        out.add(rect(FIELD_CALENDAR, w, h, pad, cursor, boxWidth, gridHeight));
        cursor += gridHeight + gap;
        if (options.showSeconds) {
            // The old layout calls this the small line even though the seconds are all it holds;
            // the name is kept so the two can be compared field for field.
            out.add(rect(ClockLayout.ROLE_SMALL_LINE, w, h, pad, cursor, boxWidth, smallSize));
        }
        return out;
    }

    /** A box at an exact place and size on this screen, in the fractions a box is stored in. */
    private static LayoutBox rect(String field, int w, int h, float left, float top,
            float width, float height) {
        return LayoutBox.of(field)
                .at(Anchor.TOP_LEFT, left / w, top / h)
                .sized(width / w, height / h)
                .aligned(Anchor.MIDDLE_CENTRE);
    }

    /**
     * The whole clock lying down, since issue #42: the time across the screen, the date under it.
     *
     * The date line's fields are in the order the user set, and the seconds are on it only while
     * they are switched on — so this is one box holding a line whose parts the options decide.
     */
    private static List<LayoutBox> wideFull(int w, int h, ClockOptions options, float pad) {
        float room = h - 2f * pad;
        float dateSize = room * 0.14f;
        float gap = room * 0.04f;
        float timeHeight = room - dateSize - gap;

        float across = w - 2f * pad;
        float reserve = options.showsMeridiem()
                ? Math.min(across * 0.30f, timeHeight * 0.44f)
                : 0f;
        float box = across - reserve;
        float left = w / 2f - reserve / 2f - box / 2f;

        List<LayoutBox> out = new ArrayList<LayoutBox>(2);
        out.add(LayoutBox.of(ClockLayout.ROLE_HOUR_MINUTE)
                .at(Anchor.TOP_LEFT, left / w, pad / h)
                .sized(box / w, timeHeight / h)
                .aligned(Anchor.MIDDLE_CENTRE));
        out.add(line(ClockLayout.ROLE_SMALL_LINE, w, h, pad,
                pad + timeHeight + gap, dateSize, across));
        return out;
    }

    /**
     * The whole clock standing up: the time, the marker where there is one, the date, and the small
     * line under it.
     *
     * The dial the user sets splits the height between the time and the rest; the marker comes out
     * of the time's own share, so turning it on does not move the date.
     */
    private static List<LayoutBox> tallFull(int w, int h, ClockOptions options, float pad) {
        float boxWidth = w - 2f * pad;
        float gap = h * 0.020f;
        float content = h - 2f * pad - 3f * gap;
        boolean marker = options.showsMeridiem();

        float timeShare = content * options.timeFractionTall;
        float mainSize = marker ? (timeShare - gap) / (2f + 0.22f) : timeShare / 2f;
        float meridiemSize = marker ? mainSize * 0.22f : 0f;
        float rest = content * (1f - options.timeFractionTall);
        float dateSize = rest * 0.6f;
        float smallSize = rest * (1f - 0.6f);

        List<LayoutBox> out = new ArrayList<LayoutBox>(5);
        float cursor = pad;
        out.add(line(ClockLayout.ROLE_HOUR, w, h, pad, cursor, mainSize, boxWidth));
        cursor += mainSize + gap;
        out.add(line(ClockLayout.ROLE_MINUTE, w, h, pad, cursor, mainSize, boxWidth));
        cursor += mainSize + gap;
        if (marker) {
            out.add(line(ClockLayout.ROLE_MERIDIEM, w, h, pad, cursor, meridiemSize, boxWidth));
            cursor += meridiemSize + gap;
        }
        out.add(line(ClockLayout.ROLE_WEEKDAY_DATE, w, h, pad, cursor, dateSize, boxWidth));
        cursor += dateSize + gap;
        out.add(line(ClockLayout.ROLE_SMALL_LINE, w, h, pad, cursor, smallSize, boxWidth));
        return out;
    }

    /**
     * The time on one line, across the screen.
     *
     * The marker, where there is one, is drawn beside the line out of a slice of the width the
     * layout keeps back — so the *line's* box is the room that is left, and it sits centred in it
     * rather than on the screen. That is the same arithmetic {@code ClockLayout.wideTimeOnly} does;
     * it is repeated here rather than shared because the two must be able to disagree, or the
     * comparison in T074 would be comparing a thing with itself.
     */
    private static List<LayoutBox> wideTimeOnly(int w, int h, ClockOptions options, float pad) {
        float room = w - 2f * pad;
        float reserve = options.showsMeridiem()
                ? Math.min(room * 0.30f, (h - 2f * pad) * 0.44f)
                : 0f;
        float box = room - reserve;
        float centreX = w / 2f - reserve / 2f;
        float left = centreX - box / 2f;

        List<LayoutBox> out = new ArrayList<LayoutBox>(1);
        out.add(LayoutBox.of(ClockLayout.ROLE_HOUR_MINUTE)
                .at(Anchor.TOP_LEFT, left / w, pad / h)
                .sized(box / w, (h - 2f * pad) / h)
                .aligned(Anchor.MIDDLE_CENTRE));
        return out;
    }

    /** The hour over the minute, with the marker on a line of its own beneath them. */
    private static List<LayoutBox> tallTimeOnly(int w, int h, ClockOptions options, float pad) {
        float boxWidth = w - 2f * pad;
        float gap = h * 0.020f;
        boolean marker = options.showsMeridiem();
        float markerGap = gap * 0.35f;
        float content = h - 2f * pad - gap - (marker ? markerGap : 0f);
        float mainSize = marker ? content / (2f + 0.22f) : content / 2f;
        float meridiemSize = marker ? mainSize * 0.22f : 0f;

        List<LayoutBox> out = new ArrayList<LayoutBox>(3);
        float cursor = pad;
        out.add(line(ClockLayout.ROLE_HOUR, w, h, pad, cursor, mainSize, boxWidth));
        cursor += mainSize + gap;
        out.add(line(ClockLayout.ROLE_MINUTE, w, h, pad, cursor, mainSize, boxWidth));
        if (marker) {
            cursor += mainSize + markerGap;
            out.add(line(ClockLayout.ROLE_MERIDIEM, w, h, pad, cursor, meridiemSize, boxWidth));
        }
        return out;
    }

    /** One full-width line at a given top and height, in the fractions a box is stored in. */
    private static LayoutBox line(String field, int w, int h, float pad, float top, float size,
            float boxWidth) {
        return LayoutBox.of(field)
                .at(Anchor.TOP_LEFT, pad / w, top / h)
                .sized(boxWidth / w, size / h)
                .aligned(Anchor.MIDDLE_CENTRE);
    }
}
