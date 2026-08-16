package com.reteclock;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Children laid side by side, wrapping to the next line when they run out of room.
 *
 * A column of radio buttons reading `12:00 PM` / `12:00 AM` / `12:00 NN` / `0:00 PM` is four lines
 * of screen spent on sixteen characters, and the settings are long enough already. Side by side
 * they read as what they are — a short list of alternatives — and the ones that do not fit drop to
 * the next line rather than being clipped or squeezed.
 *
 * Written here rather than taken from a support library: this app has no dependencies, and what it
 * needs from a flow layout is thirty lines of measure and lay out.
 */
final class FlowLayout extends ViewGroup {

    private final int gapX;
    private final int gapY;

    FlowLayout(Context context, int gapX, int gapY) {
        super(context);
        this.gapX = gapX;
        this.gapY = gapY;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int limit = width - getPaddingLeft() - getPaddingRight();
        int childSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST);

        int x = 0;
        int y = 0;
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(childSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = child.getMeasuredWidth();
            if (x > 0 && x + w > limit) {
                x = 0;
                y += lineHeight + gapY;
                lineHeight = 0;
            }
            x += w + gapX;
            lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
        }
        int height = y + lineHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int limit = right - left - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + w > limit + getPaddingLeft()) {
                x = getPaddingLeft();
                y += lineHeight + gapY;
                lineHeight = 0;
            }
            child.layout(x, y, x + w, y + h);
            x += w + gapX;
            lineHeight = Math.max(lineHeight, h);
        }
    }
}
