package com.reteclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;

import com.reteclock.core.BurnInShift;
import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.ClockText;

/**
 * The clock face. Draws itself with a Canvas, so it needs no layout XML and no support library.
 *
 * Only framework APIs available since API 1 are used here, which keeps the view working from
 * Android 2.3 up to current releases.
 */
public class ClockView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    // Reused every frame: Dalvik collects garbage on the UI thread, so the draw path allocates nothing.
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    private static final Typeface SYSTEM_REGULAR = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface SYSTEM_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    /** How far italic leans. Synthesised, because a user font has one weight and one slant. */
    private static final float ITALIC_SKEW = -0.25f;

    /** The user's font, or null to draw with the system faces exactly as the app always has. */
    private Typeface userFont;
    private boolean decorBold;
    private boolean decorItalic;
    private boolean decorUnderline;

    private ClockOptions options;
    private ClockLayout layout;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            invalidate();
            handler.postDelayed(this, ClockText.millisToNextSecond(System.currentTimeMillis()));
        }
    };

    public ClockView(Context context) {
        super(context);
        options = Settings.options(context);
        loadTypeface(context);
        setBackgroundColor(Color.BLACK);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    /** Re-reads the options, e.g. after the user comes back from the settings screen. */
    public void reloadOptions() {
        options = Settings.options(getContext());
        loadTypeface(getContext());
        layout = null;
        invalidate();
    }

    /** Starts the once-per-second redraw. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        handler.post(tick);
    }

    /** Stops redrawing so a background clock costs nothing. */
    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        layout = ClockLayout.of(w, h, options);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (layout == null) {
            layout = ClockLayout.of(w, h, options);
        }

        ClockText time = ClockText.of(System.currentTimeMillis(), options);

        int maxShift = BurnInShift.maxShiftPx(w, h);
        long elapsed = SystemClock.elapsedRealtime();
        canvas.save();
        canvas.translate(BurnInShift.offsetX(elapsed, maxShift), BurnInShift.offsetY(elapsed, maxShift));

        for (ClockLayout.Slot slot : layout.slots()) {
            String text = textFor(slot.role, time);
            if (text == null) {
                continue;
            }
            // With no user font this is exactly what the app has always drawn: two system faces,
            // hour and minute bold. A user font has one weight, so bold there is synthesised.
            boolean wantBold = slot.bold || decorBold;
            if (userFont != null) {
                paint.setTypeface(userFont);
                paint.setFakeBoldText(wantBold);
            } else {
                paint.setTypeface(wantBold ? SYSTEM_BOLD : SYSTEM_REGULAR);
                paint.setFakeBoldText(false);
            }
            paint.setTextSkewX(decorItalic ? ITALIC_SKEW : 0f);
            paint.setUnderlineText(decorUnderline);
            paint.setTextSize(slot.textSize);
            float measured = paint.measureText(text);
            float fitted = ClockLayout.shrinkToFit(slot.textSize, measured, slot.maxWidth);
            if (fitted != slot.textSize) {
                paint.setTextSize(fitted);
            }

            // Center the glyphs vertically on slot.centerY.
            paint.getFontMetrics(fontMetrics);
            float baseline = slot.centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(text, slot.centerX, baseline, paint);
        }
        canvas.restore();
    }


    /**
     * Reads the chosen font. A file that is not a usable font leaves the system faces in place
     * rather than a broken clock; the settings screen refuses such files on import, and this is the
     * second line of defence for a file that goes bad afterwards.
     */
    private void loadTypeface(Context context) {
        decorBold = Settings.bold(context);
        decorItalic = Settings.italic(context);
        decorUnderline = Settings.underline(context);

        java.io.File file = Settings.fontFile(context);
        if (file == null) {
            userFont = null;
            return;
        }
        try {
            userFont = Typeface.createFromFile(file);
        } catch (RuntimeException e) {
            userFont = null;
        }
    }

    private static String textFor(String role, ClockText time) {
        if (ClockLayout.ROLE_HOUR_MINUTE.equals(role)) {
            return time.hourMinute;
        }
        if (ClockLayout.ROLE_HOUR.equals(role)) {
            return time.hour;
        }
        if (ClockLayout.ROLE_MINUTE.equals(role)) {
            return time.minute;
        }
        if (ClockLayout.ROLE_SECOND.equals(role)) {
            return time.secondLabel;
        }
        if (ClockLayout.ROLE_WEEKDAY.equals(role)) {
            return time.weekday;
        }
        if (ClockLayout.ROLE_MONTH_DAY.equals(role)) {
            return time.monthDay;
        }
        if (ClockLayout.ROLE_YEAR.equals(role)) {
            return time.year;
        }
        if (ClockLayout.ROLE_WEEKDAY_DATE.equals(role)) {
            return time.weekdayDate;
        }
        if (ClockLayout.ROLE_SMALL_LINE.equals(role)) {
            return time.smallLine;
        }
        return null;
    }
}
