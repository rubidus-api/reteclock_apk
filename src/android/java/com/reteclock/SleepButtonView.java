package com.reteclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/**
 * The sleep button on its own, for a clock with no timer strip to carry it (issue #54, RFC-0014).
 *
 * <p>It sits where the strip's near end would be, so the moon is in the same place whether the
 * timer is on or off. It takes only its own square: a touch anywhere else is the clock's, and the
 * layout under it is not moved over for it — nothing on the clock may shift for a control laid on
 * top of it (issue #52).
 */
final class SleepButtonView extends View {

    /** How much of the faint colour a control keeps while it is not in force. */
    private static final int DIM_ALPHA = 0x66;

    interface Pressed {
        void pressed();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Pressed pressed;
    private int color;
    private boolean asleep;

    SleepButtonView(Context context, int color, boolean asleep, Pressed pressed) {
        super(context);
        this.color = color;
        this.asleep = asleep;
        this.pressed = pressed;
        setContentDescription(context.getString(R.string.sleep_button));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        paint.setColor(asleep ? color : (color & 0x00FFFFFF) | (DIM_ALPHA << 24));
        SleepGlyph.draw(canvas, paint, path, w / 2f, h / 2f, Math.min(w, h) * 0.29f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && pressed != null) {
            pressed.pressed();
        }
        return true;
    }
}
