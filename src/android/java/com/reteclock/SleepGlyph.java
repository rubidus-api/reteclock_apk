package com.reteclock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The sleep button's mark: a crescent moon, in one colour (issue #54, RFC-0014).
 *
 * <p>Drawn rather than shipped, like the timer's controls beside it. The crescent is the part of one
 * circle that a second, offset circle does not cover, built as two arcs meeting at the points where
 * the circles cross. Cutting with a clip would be shorter, but a clip that subtracts is refused on
 * newer Android, and this has to draw the same on 2.3 and on 14.
 */
final class SleepGlyph {

    private SleepGlyph() {
    }

    /** A crescent of radius {@code r} centred on {@code cx, cy}, filled with the paint as it is. */
    static void draw(Canvas canvas, Paint paint, Path path, float cx, float cy, float r) {
        // The covering circle: up and to the right, a little smaller, so the horns point left.
        float dx = r * 0.52f;
        float dy = -r * 0.38f;
        float rho = r * 0.84f;
        double d = Math.sqrt(dx * dx + dy * dy);
        double a = (r * r - rho * rho + d * d) / (2 * d);
        double h = Math.sqrt(Math.max(r * r - a * a, 0));
        double px = a * dx / d;
        double py = a * dy / d;
        double ix1 = px - h * dy / d;
        double iy1 = py + h * dx / d;
        double ix2 = px + h * dy / d;
        double iy2 = py - h * dx / d;

        double towardB = Math.toDegrees(Math.atan2(dy, dx));
        float start = (float) Math.toDegrees(Math.atan2(iy1, ix1));
        float end = (float) Math.toDegrees(Math.atan2(iy2, ix2));
        float sweep = normal(end - start);
        // The outer edge is the arc of the moon that does not face the covering circle.
        if (near(start + sweep / 2f, towardB)) {
            sweep -= 360f;
        }

        float innerStart = (float) Math.toDegrees(Math.atan2(iy2 - dy, ix2 - dx));
        float innerEnd = (float) Math.toDegrees(Math.atan2(iy1 - dy, ix1 - dx));
        float innerSweep = normal(innerEnd - innerStart);
        // The inner edge is the covering circle's arc that lies inside the moon, facing its centre.
        if (!near(innerStart + innerSweep / 2f, towardB + 180.0)) {
            innerSweep -= 360f;
        }

        path.reset();
        path.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, true);
        path.arcTo(new RectF(cx + dx - rho, cy + dy - rho, cx + dx + rho, cy + dy + rho),
                innerStart, innerSweep, false);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    /** An angle folded into (0, 360]. */
    private static float normal(float degrees) {
        float d = degrees % 360f;
        return d <= 0f ? d + 360f : d;
    }

    private static boolean near(double degrees, double target) {
        double diff = Math.abs(((degrees - target) % 360.0 + 540.0) % 360.0 - 180.0);
        return diff < 90.0;
    }
}
