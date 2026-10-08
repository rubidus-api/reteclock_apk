package com.reteclock.core;

import com.reteclock.core.layout.BoxPlan;
import com.reteclock.core.layout.LayoutBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the clock thinks of a layout drawn in the browser, before it is saved (R143).
 *
 * <p>The editor on the device says, under its canvas, what {@link BoxPlan} has to complain of: a
 * box whose writing has to shrink, boxes on the same ground, nothing drawn at all. Whether writing
 * fits is a matter of the clock's own fonts and what its settings make the fields say, which the
 * browser does not have — so the browser sends the box lines and is told.
 *
 * <p>Nothing is stored. The lines are held to what a box line is: a known field, and no more of
 * them than a layout could want.
 */
public final class WebLayoutCheck {

    /** More boxes than this is not a layout somebody drew. */
    public static final int MAX_BOXES = 200;

    private WebLayoutCheck() {
    }

    /**
     * One line to a complaint: kind, field, the other field, and the per cent the writing is drawn
     * at — {@code kind|field|other|percent}, the kinds being {@link BoxPlan}'s. Empty when there is
     * nothing in the way.
     *
     * @param boxes box lines as {@link LayoutBox#text()} writes them, one to a line
     * @throws IllegalArgumentException for a line that is no box, an unknown field, or too many
     */
    public static String complaints(String boxes, int screenW, int screenH, ClockOptions options,
            ClockLayout.Metrics metrics) {
        List<LayoutBox> drawn = new ArrayList<LayoutBox>();
        List<String> fields = Arrays.asList(WebSettings.BOX_FIELDS);
        for (String line : (boxes == null ? "" : boxes).split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            LayoutBox box = LayoutBox.parse(line);
            if (box == null || !fields.contains(box.field)) {
                throw new IllegalArgumentException("Not a box of a layout");
            }
            if (drawn.size() >= MAX_BOXES) {
                throw new IllegalArgumentException("Too many boxes");
            }
            drawn.add(box);
        }
        StringBuilder out = new StringBuilder();
        for (BoxPlan.Complaint said : BoxPlan.of(drawn, screenW, screenH, options, metrics)
                .complaints()) {
            out.append(said.kind).append('|')
                    .append(said.field == null ? "" : said.field).append('|')
                    .append(said.other == null ? "" : said.other).append('|')
                    .append(Math.round(said.ratio * 100f)).append('\n');
        }
        return out.toString();
    }
}
