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

    /** One font per field, null where the system face is wanted. Indexed by role. */
    private final java.util.Map<String, Typeface> userFonts = new java.util.HashMap<String, Typeface>();
    private boolean decorBold;
    private boolean decorItalic;
    private boolean decorUnderline;

    private ClockOptions options;
    private ClockLayout layout;
    /**
     * Sizes and cell positions, worked out when the fonts, options or screen change.
     *
     * Never rebuilt while drawing: the whole point is that no measurement depends on the time, so
     * the clock neither resizes nor slides as the digits change.
     */
    private ClockLayout.Plan plan;
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
        // Parts are positioned by their left edge, since a line can be several of them.
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Re-reads the options, e.g. after the user comes back from the settings screen. */
    public void reloadOptions() {
        options = Settings.options(getContext());
        loadTypeface(getContext());
        layout = null;
        plan = null;
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
        rebuild(w, h);
    }


    /**
     * Works out the layout and, with it, the sizes and cells every field will use.
     *
     * This is the expensive part — it measures every string each field can ever show, in that
     * field's own font — and it is why the draw path does none of that. Called when the screen size
     * changes and when the settings do, which is the only time any of it can change.
     */
    private void rebuild(int w, int h) {
        layout = ClockLayout.of(w, h, options);
        plan = layout.plan(new ClockLayout.Metrics() {
            @Override
            public float width(String role, boolean bold, String text, float textSize) {
                applyStyle(role, bold, textSize);
                return paint.measureText(text);
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (layout == null || plan == null) {
            rebuild(w, h);
        }

        ClockText time = ClockText.of(System.currentTimeMillis(), options);

        int maxShift = BurnInShift.maxShiftPx(w, h);
        long elapsed = SystemClock.elapsedRealtime();
        canvas.save();
        canvas.translate(BurnInShift.offsetX(elapsed, maxShift), BurnInShift.offsetY(elapsed, maxShift));

        for (ClockLayout.Slot slot : layout.slots()) {
            String[] pieces = piecesFor(slot, time);
            if (pieces == null) {
                continue;
            }
            // Everything about size and place was settled when the fonts were. All that is left is
            // to centre the text of the moment inside the cell reserved for its widest case, so a
            // narrow reading sits where a wide one would and nothing moves as the clock ticks.
            float size = plan.textSize(slot);
            for (int i = 0; i < slot.parts.size(); i++) {
                ClockLayout.Part part = slot.parts.get(i);
                applyStyle(part.role, slot.bold, size);
                float cellStart = plan.cellStart(slot, part);
                float cellWidth = plan.cellWidth(slot, part);
                float textWidth = paint.measureText(pieces[i]);
                paint.getFontMetrics(fontMetrics);
                float baseline = slot.centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
                canvas.drawText(pieces[i], cellStart + (cellWidth - textWidth) / 2f, baseline, paint);
            }
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

        userFonts.clear();
        for (String role : Settings.FONT_ROLES) {
            java.io.File file = Settings.fontFileFor(context, role);
            if (file == null) {
                continue;
            }
            try {
                userFonts.put(role, Typeface.createFromFile(file));
            } catch (RuntimeException e) {
                // A file that stops loading leaves that field on the system face rather than
                // taking the whole clock down.
            }
        }
    }


    /**
     * The strings for one line, separators included, or null when the line has nothing to show.
     *
     * The separator belongs to the part it precedes, so it is drawn in the font of the part before
     * it — the colon of the time reads as part of the time, not as a piece of the minute.
     */
    private static String[] piecesFor(ClockLayout.Slot slot, ClockText time) {
        int count = slot.parts.size();
        String[] pieces = new String[count];
        boolean anything = false;
        for (int i = 0; i < count; i++) {
            ClockLayout.Part part = slot.parts.get(i);
            String text = textFor(part.role, time);
            if (text == null) {
                return null;
            }
            // The separator is measured and drawn with the part before it, so it goes on the end of
            // that piece rather than the start of this one.
            if (i > 0 && !part.separatorBefore.isEmpty()) {
                pieces[i - 1] = pieces[i - 1] + part.separatorBefore;
            }
            pieces[i] = text;
            anything = anything || !text.isEmpty();
        }
        return anything ? pieces : null;
    }

    /** Sets the paint up for one field: its font, its weight, and the decorations. */
    private void applyStyle(String role, boolean slotBold, float textSize) {
        Typeface font = userFonts.get(role);
        boolean wantBold = slotBold || decorBold;
        // With no font of its own this is exactly what the app has always drawn: two system faces,
        // hour and minute bold. A user font has one weight, so bold there is synthesised.
        if (font != null) {
            paint.setTypeface(font);
            paint.setFakeBoldText(wantBold);
        } else {
            paint.setTypeface(wantBold ? SYSTEM_BOLD : SYSTEM_REGULAR);
            paint.setFakeBoldText(false);
        }
        paint.setTextSkewX(decorItalic ? ITALIC_SKEW : 0f);
        paint.setUnderlineText(decorUnderline);
        paint.setTextSize(textSize);
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
