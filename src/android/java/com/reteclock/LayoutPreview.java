package com.reteclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.layout.BoxPlan;
import com.reteclock.core.layout.Builtin;
import com.reteclock.core.layout.LayoutBox;

import java.util.List;

/**
 * A small picture of an arrangement, for the list of layouts (RFC-0005).
 *
 * A layout's name says nothing about what it looks like — "Bedside" and "Bedside 2" are the same
 * word twice — so each entry carries a thumbnail of the screen it would draw. Boxes are drawn as
 * outlines with their field's name where there is room, which is enough to tell two arrangements
 * apart at a glance and cheap enough to put a dozen of them in a scrolling list.
 *
 * An **automatic** entry has no boxes of its own, so it shows what the app would arrange for this
 * phone: that is what choosing it produces, and an empty rectangle would say the opposite.
 */
final class LayoutPreview extends View {

    private static final int FRAME = 0xFF3A3A3A;
    private static final int SCREEN = 0xFF000000;
    private static final int BOX = 0xFF6FBFB6;
    private static final int LABEL = 0xFFBFBFBF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean landscape;
    private final List<LayoutBox> boxes;
    private final ClockOptions options;

    /**
     * @param boxes the arrangement, or null for an automatic one, which is drawn as the app would
     *              arrange this phone
     */
    LayoutPreview(Context context, boolean landscape, List<LayoutBox> boxes, ClockOptions options) {
        super(context);
        this.landscape = landscape;
        this.options = options;
        this.boxes = boxes == null || boxes.isEmpty()
                ? Builtin.of(screenW(), screenH(), options)
                : boxes;
    }

    private int shortEdge() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.min(metrics.widthPixels, metrics.heightPixels);
    }

    private int longEdge() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(metrics.widthPixels, metrics.heightPixels);
    }

    private int screenW() {
        return landscape ? longEdge() : shortEdge();
    }

    private int screenH() {
        return landscape ? shortEdge() : longEdge();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // As tall as the phone's own proportions make it at the width it is given, so the picture is
        // the shape of the screen it describes rather than a box the layout has been squeezed into.
        int width = MeasureSpec.getSize(widthSpec);
        int height = Math.round(width * screenH() / (float) screenW());
        setMeasuredDimension(width, Math.max(1, height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SCREEN);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(FRAME);
        canvas.drawRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, paint);

        if (boxes == null || boxes.isEmpty()) {
            return;
        }
        BoxPlan plan = BoxPlan.of(boxes, screenW(), screenH(), options,
                new ClockLayout.Metrics() {
                    @Override
                    public float width(String role, String text, float textSize) {
                        paint.setTextSize(textSize);
                        return paint.measureText(text);
                    }
                });
        float scale = w / (float) screenW();
        for (BoxPlan.Placed placed : plan.placed()) {
            float left = placed.rect[0] * scale;
            float top = placed.rect[1] * scale;
            float right = left + placed.rect[2] * scale;
            float bottom = top + placed.rect[3] * scale;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(BOX);
            canvas.drawRect(left, top, right, bottom, paint);

            String label = LayoutFieldsActivity.fieldLabel(this, placed.field);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(LABEL);
            float size = Math.min((bottom - top) * 0.6f, (right - left) / 6f);
            if (size >= 6f) {
                paint.setTextSize(size);
                canvas.drawText(label, left + 2f, bottom - 2f, paint);
            }
        }
    }
}
