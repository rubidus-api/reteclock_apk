package com.reteclock.core.layout;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of boxes, turned into the layout the drawing code already knows how to paint (RFC-0005).
 *
 * The engine decides where things go. This says it in the shape `ClockView` already consumes, which
 * is what makes moving to a composed layout a substitution rather than a rewrite: the blink, the
 * AM/PM marker, the per-field fonts, the burn-in shift and the calendar grid all keep working,
 * because none of them can tell where the rectangles came from.
 *
 * Two fields are not lines and are handed over as rectangles, which is how the old layout gives
 * them too: the month grid and the saying's strip.
 */
public final class Composed {

    private Composed() {
    }

    /**
     * The layout these boxes come to on a screen of this size, or null when there are no boxes.
     *
     * @param metrics how the caller measures glyphs — the view's own paint, as everywhere else
     */
    public static ClockLayout of(List<LayoutBox> boxes, int screenW, int screenH,
            ClockOptions options, ClockLayout.Metrics metrics) {
        if (boxes == null) {
            return null;
        }
        boolean wide = screenW > screenH;
        BoxPlan plan = BoxPlan.of(boxes, screenW, screenH, options, metrics);

        List<String> roles = new ArrayList<String>();
        List<String[]> fields = new ArrayList<String[]>();
        List<String[]> separators = new ArrayList<String[]>();
        List<float[]> rects = new ArrayList<float[]>();
        float[] calendar = null;
        float[] saying = null;

        for (BoxPlan.Placed placed : plan.placed()) {
            if (Builtin.FIELD_CALENDAR.equals(placed.field)) {
                calendar = placed.rect;
                continue;
            }
            if (Builtin.FIELD_QUOTE.equals(placed.field)) {
                saying = placed.rect;
                continue;
            }
            List<Line.Part> parts = Line.of(placed.field, options, wide);
            String[] partFields = new String[parts.size()];
            String[] partSeparators = new String[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                partFields[i] = parts.get(i).field;
                partSeparators[i] = parts.get(i).separatorBefore;
            }
            roles.add(placed.field);
            fields.add(partFields);
            separators.add(partSeparators);
            rects.add(placed.rect);
        }

        return ClockLayout.composed(wide, options,
                roles.toArray(new String[roles.size()]),
                fields.toArray(new String[fields.size()][]),
                separators.toArray(new String[separators.size()][]),
                rects.toArray(new float[rects.size()][]),
                calendar, saying);
    }
}
