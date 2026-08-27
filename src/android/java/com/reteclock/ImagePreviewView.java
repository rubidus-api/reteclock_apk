package com.reteclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import com.reteclock.core.ImageFit;
import com.reteclock.core.PreviewShape;

/**
 * What a picture will look like as a background: on the phone upright, and on the phone turned.
 *
 * The two are always shown together, because the second is the one that surprises people. A tall
 * photograph on a wide screen is either pulled out of shape or shown from the middle outwards,
 * depending on the fit — and which of those is happening is obvious in a picture and very hard to
 * say in a sentence, which is what issue #48 was really about.
 *
 * The placement comes from {@link ImageFit}, the same arithmetic the clock draws with, so this is a
 * preview rather than an impression of one.
 */
final class ImagePreviewView extends View {

    /** The bare screen behind the picture, and the line around each little screen. */
    private static final int EMPTY = 0xFF101010;
    private static final int EDGE = 0x66FFFFFF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect source = new Rect();
    private final RectF target = new RectF();

    private Bitmap picture;
    private int mode = ImageFit.COVER;
    private int screenWidth = 1080;
    private int screenHeight = 1920;
    private int height;

    ImagePreviewView(Context context, int heightPx) {
        super(context);
        height = Math.max(heightPx, 1);
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        screenWidth = Math.max(1, metrics.widthPixels);
        screenHeight = Math.max(1, metrics.heightPixels);
    }

    /** The picture it is showing, so a change of fit can be applied without finding it again. */
    Bitmap picture() {
        return picture;
    }

    void show(Bitmap picture, int fitMode) {
        this.picture = picture;
        this.mode = fitMode;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        PreviewShape shape = PreviewShape.of(height, screenWidth, screenHeight);
        setMeasuredDimension(resolveSize(Math.round(shape.width), widthSpec),
                resolveSize(Math.round(shape.height), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        PreviewShape shape = PreviewShape.inside(getWidth(), height, screenWidth, screenHeight);
        // Centred in whatever room the row gave it, so a preview squeezed by a long name still
        // sits under the middle of its column rather than hanging off one side.
        float left = (getWidth() - shape.width) / 2f;
        float top = (getHeight() - shape.height) / 2f;
        drawScreen(canvas, shape.portrait, left, top);
        drawScreen(canvas, shape.landscape, left, top);
    }

    /** One little screen: the bare ground, the picture placed as the clock would place it, a line. */
    private void drawScreen(Canvas canvas, float[] rect, float left, float top) {
        float x = left + rect[0];
        float y = top + rect[1];
        float w = rect[2];
        float h = rect[3];

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(EMPTY);
        canvas.drawRect(x, y, x + w, y + h, paint);

        if (picture != null && !picture.isRecycled()) {
            ImageFit.Placement placement = ImageFit.of(Math.round(w), Math.round(h),
                    picture.getWidth(), picture.getHeight(), mode);
            if (placement != null) {
                int saved = canvas.save();
                // The little screen is the whole of what is seen, exactly as the real one is: what
                // falls outside it is cropped here for the same reason it is cropped there.
                canvas.clipRect(x, y, x + w, y + h);
                source.set(0, 0, picture.getWidth(), picture.getHeight());
                target.set(0f, 0f,
                        picture.getWidth() * placement.scaleX,
                        picture.getHeight() * placement.scaleY);
                target.offset(x + placement.dx, y + placement.dy);
                paint.setFilterBitmap(true);
                canvas.drawBitmap(picture, source, target, paint);
                canvas.restoreToCount(saved);
            }
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(EDGE);
        canvas.drawRect(x, y, x + w, y + h, paint);
    }
}
