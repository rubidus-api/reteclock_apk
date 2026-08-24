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
    public static List<LayoutBox> of(int screenW, int screenH, ClockOptions options) {
        if (!options.timeOnly) {
            return null;                       // the full clock, the calendar and the saying: not yet
        }
        float pad = ClockLayout.paddingPx(screenW, screenH);
        return screenW > screenH
                ? wideTimeOnly(screenW, screenH, options, pad)
                : tallTimeOnly(screenW, screenH, options, pad);
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
