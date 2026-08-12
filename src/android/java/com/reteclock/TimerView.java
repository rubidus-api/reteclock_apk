package com.reteclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

import com.reteclock.core.ColorRamp;
import com.reteclock.core.FramePacer;
import com.reteclock.core.TimeReadout;
import com.reteclock.core.TimerBar;
import com.reteclock.core.TimerCues;
import com.reteclock.core.TimerInterval;
import com.reteclock.core.TimerPreset;
import com.reteclock.core.TimerRun;
import com.reteclock.core.Tones;

/**
 * The timer's strip: a bar that fills, three readouts, and four controls.
 *
 * A view of its own, beside the clock rather than inside it. That is deliberate. The readouts show
 * hundredths, so this redraws many times a second while the timer runs — and if it were part of the
 * clock, every one of those redraws would re-measure and re-draw the clock's glyphs too. Here, only
 * the strip is invalidated, and when the timer is stopped nothing is invalidated at all.
 *
 * Everything about where things go comes from {@link TimerBar}, which measures along one axis: zero
 * at the end the bar fills from — the bottom of the screen in landscape, the left in portrait.
 */
public class TimerView extends View {

    /** What the empty part of the bar looks like. */
    private static final int TRACK = 0xFF262626;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int CONTROL = 0xFF9E9E9E;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Handler handler = new Handler();
    private final FramePacer pacer = new FramePacer();

    private TimerRun run;
    private TimerPreset preset;
    private TimerBar bar;
    private boolean horizontal;
    private boolean running;
    /** When the cues were last collected, so the next window starts where that one ended. */
    private long lastCueMs = Long.MIN_VALUE / 2L;

    private Listener listener;

    /** What the strip needs the world to do for it. */
    interface Listener {
        /** Write the run down, or forget it, so another screen can pick it up where it stands. */
        void remember(TimerRun run);

        /** Play this pattern, however the settings say it should be heard. */
        void cue(Tones.Note[] pattern);

        /** Say this, if there is anything to say. */
        void speak(String message);

        /** Flash the whole screen: an interval has ended. */
        void flash();

        /** Open the list of presets; the hourglass was pressed. */
        void choosePreset();
    }

    public TimerView(Context context) {
        super(context);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Which preset the controls will start, and what the bar shows before anything runs. */
    void setPreset(TimerPreset preset) {
        this.preset = preset;
        if (run != null) {
            stop();
        }
        invalidate();
    }

    TimerPreset preset() {
        return preset;
    }

    boolean isRunning() {
        return run != null && !run.isPaused();
    }

    void start() {
        if (preset == null || preset.intervals.isEmpty()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        run = TimerRun.start(preset, now);
        // A window that opens a moment before the start, so the preset's own opening cue is caught.
        lastCueMs = now - 1L;
        remember();
        begin();
    }

    /**
     * Takes on a run that another screen — or this one before it was left — had going.
     *
     * The cue window opens at now, so whatever sounded while nobody was looking is not replayed.
     */
    void adopt(TimerRun existing) {
        run = existing;
        lastCueMs = SystemClock.elapsedRealtime();
        if (run != null && !run.isPaused() && !run.finishedAt(lastCueMs)) {
            begin();
        } else {
            invalidate();
        }
    }

    private void remember() {
        if (listener != null) {
            listener.remember(run);
        }
    }

    void pause() {
        if (run == null || run.isPaused()) {
            return;
        }
        run = run.pausedAt(SystemClock.elapsedRealtime());
        remember();
        invalidate();
    }

    void resume() {
        if (run == null || !run.isPaused()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        run = run.resumedAt(now);
        lastCueMs = now;
        remember();
        begin();
    }

    void stop() {
        run = null;
        remember();
        running = false;
        handler.removeCallbacks(tick);
        invalidate();
    }

    /** Starts the redraws, if they are not already going. */
    private void begin() {
        if (!running) {
            running = true;
            handler.post(tick);
        }
        invalidate();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || run == null || run.isPaused()) {
                running = false;
                return;
            }
            long now = SystemClock.elapsedRealtime();
            playCues(now);
            invalidate();
            if (run.finishedAt(now)) {
                // The last cue has sounded; the bar holds full until somebody stops it.
                running = false;
                return;
            }
            handler.postDelayed(this, pacer.delayMs());
        }
    };

    /** Whatever fell between the last look and now, in order — see {@link TimerCues}. */
    private void playCues(long now) {
        if (listener == null || run == null) {
            lastCueMs = now;
            return;
        }
        List<TimerCues.Cue> cues = TimerCues.between(run, lastCueMs, now);
        lastCueMs = now;
        for (TimerCues.Cue cue : cues) {
            switch (cue.kind) {
                case TimerCues.PRE_ALARM:
                    listener.cue(Tones.PRE_ALARM);
                    break;
                case TimerCues.START:
                case TimerCues.TICK:
                    listener.cue(Tones.TICK);
                    break;
                case TimerCues.END:
                    listener.cue(Tones.END);
                    listener.flash();
                    break;
                case TimerCues.FINISH:
                    listener.cue(Tones.FINISH);
                    break;
                case TimerCues.SPEAK:
                    if (cue.interval < preset.intervals.size()) {
                        listener.speak(preset.intervals.get(cue.interval).message);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        horizontal = w >= h;
        bar = TimerBar.of(w, h, horizontal);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bar == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long began = isRunning() ? now : 0L;

        float progress = run == null ? 0f : run.progressAt(now);
        TimerInterval interval = run == null
                ? (preset == null || preset.intervals.isEmpty() ? null : preset.intervals.get(0))
                : run.intervalObjectAt(now);
        float inInterval = run == null ? 0f : run.progressInIntervalAt(now);
        int filled = interval == null
                ? TimerInterval.DEFAULT_COLOR
                : ColorRamp.blend(interval.color, interval.endColor, inInterval);

        drawTrack(canvas);
        drawFill(canvas, progress, filled);
        drawReadouts(canvas, now, progress);
        drawControls(canvas);

        if (began != 0L) {
            pacer.sample(SystemClock.elapsedRealtime() - began);
        }
    }

    /** The bar's empty length. */
    private void drawTrack(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TRACK);
        rect(canvas, bar.barStart(), bar.barEnd());
    }

    private void drawFill(Canvas canvas, float progress, int color) {
        paint.setColor(color);
        rect(canvas, bar.barStart(), bar.fillAt(progress));
    }

    /** One rectangle of the bar, from one point along the strip to another. */
    private void rect(Canvas canvas, float fromAlong, float toAlong) {
        float thickness = bar.thickness();
        if (horizontal) {
            float top = (getHeight() - thickness) / 2f;
            canvas.drawRect(fromAlong, top, toAlong, top + thickness, paint);
        } else {
            // Landscape counts upwards from the bottom of the screen.
            float left = (getWidth() - thickness) / 2f;
            canvas.drawRect(left, getHeight() - toAlong, left + thickness,
                    getHeight() - fromAlong, paint);
        }
    }

    /**
     * The three times: the whole preset at the empty end, what is left at the full end, and how far
     * into this interval we are, riding on the edge between them.
     *
     * In landscape they are turned ninety degrees and read up the bar; in portrait they are
     * upright. Nothing else about them differs, which is why the positions come from one place.
     */
    private void drawReadouts(Canvas canvas, long now, float progress) {
        float size = Math.min(bar.thickness() * 0.62f, textCeiling());
        paint.setTextSize(size);
        paint.setColor(TEXT);

        long total = run == null
                ? (preset == null ? 0L : preset.totalMs())
                : run.totalMs();
        long into = run == null ? 0L : run.elapsedInIntervalAt(now);
        long left = run == null
                ? (preset == null || preset.intervals.isEmpty()
                        ? 0L : preset.intervals.get(0).lengthMs)
                : run.remainingInIntervalAt(now);

        // The total sits on the far side of the bar, the remaining on the far side too but at the
        // other end, and the elapsed on the near side where the fill edge is.
        text(canvas, TimeReadout.of(total), bar.totalAt(), true, Paint.Align.LEFT);
        text(canvas, TimeReadout.of(left), bar.remainingAt(), true, Paint.Align.RIGHT);

        // The riding readout is centred on the fill's edge, so half of it can hang off the end of
        // the strip when the edge is near one. The geometry cannot know how wide the text is; this
        // does, so the clamping belongs here.
        String elapsed = TimeReadout.of(into);
        float half = paint.measureText(elapsed) / 2f;
        float ride = bar.elapsedAt(progress);
        float low = bar.barStart() + half;
        float high = bar.barEnd() - half;
        if (low <= high) {
            ride = ride < low ? low : ride > high ? high : ride;
        }
        text(canvas, elapsed, ride, false, Paint.Align.CENTER);
    }

    /** How large the readouts may be before they would not fit across the strip. */
    private float textCeiling() {
        float breadth = horizontal ? getHeight() : getWidth();
        return breadth * 0.30f;
    }

    /**
     * One readout beside the bar. {@code farSide} puts it on the side away from the bar's centre;
     * in landscape everything is drawn turned, which is one save-rotate-restore rather than a
     * different code path.
     */
    private void text(Canvas canvas, String value, float along, boolean farSide,
            Paint.Align align) {
        paint.setTextAlign(align);
        float offset = bar.thickness() * 0.5f + paint.getTextSize() * 0.85f;
        canvas.save();
        if (horizontal) {
            float y = (getHeight() + bar.thickness()) / 2f
                    + (farSide ? offset : -offset + paint.getTextSize() * 0.2f);
            float x = along;
            if (align == Paint.Align.LEFT) {
                x += paint.getTextSize() * 0.2f;
            } else if (align == Paint.Align.RIGHT) {
                x -= paint.getTextSize() * 0.2f;
            }
            canvas.drawText(value, x, y, paint);
        } else {
            // Turned a quarter turn, reading up the bar, on whichever side was asked for.
            float x = (getWidth() + bar.thickness()) / 2f
                    + (farSide ? offset : -offset + paint.getTextSize() * 0.2f);
            float y = getHeight() - along;
            canvas.rotate(-90f, x, y);
            canvas.drawText(value, x, y, paint);
        }
        canvas.restore();
        paint.setTextAlign(Paint.Align.CENTER);
    }

    /** Hourglass, stop, pause, play — drawn rather than shipped, like everything else here. */
    private void drawControls(Canvas canvas) {
        float size = Math.min(bar.thickness() * 1.1f, shortEdge() * 0.7f);
        for (int i = 0; i < bar.controlCount(); i++) {
            float along = bar.controlCenter(i);
            float cx = horizontal ? along : getWidth() / 2f;
            float cy = horizontal ? getHeight() / 2f : getHeight() - along;
            boolean lit = i == TimerBar.CONTROL_PLAY ? !isRunning()
                    : i == TimerBar.CONTROL_PAUSE ? isRunning()
                    : true;
            paint.setColor(lit ? CONTROL : 0xFF4A4A4A);
            // In landscape the strip is turned, so the glyphs are turned with it: play points the
            // way the bar fills — upwards — rather than off to the side.
            canvas.save();
            if (!horizontal) {
                canvas.rotate(-90f, cx, cy);
            }
            drawControl(canvas, i, cx, cy, size * 0.42f);
            canvas.restore();
        }
    }

    private void drawControl(Canvas canvas, int which, float cx, float cy, float r) {
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        switch (which) {
            case TimerBar.CONTROL_PLAY:
                path.moveTo(cx - r * 0.6f, cy - r);
                path.lineTo(cx + r * 0.8f, cy);
                path.lineTo(cx - r * 0.6f, cy + r);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case TimerBar.CONTROL_PAUSE:
                canvas.drawRect(cx - r * 0.7f, cy - r, cx - r * 0.15f, cy + r, paint);
                canvas.drawRect(cx + r * 0.15f, cy - r, cx + r * 0.7f, cy + r, paint);
                break;
            case TimerBar.CONTROL_STOP:
                canvas.drawRect(cx - r * 0.75f, cy - r * 0.75f, cx + r * 0.75f, cy + r * 0.75f,
                        paint);
                break;
            default:
                // An hourglass: two triangles meeting at their points, with a lid and a foot.
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(cx - r * 0.75f, cy - r);
                path.lineTo(cx + r * 0.75f, cy - r);
                path.lineTo(cx, cy);
                path.close();
                path.moveTo(cx - r * 0.75f, cy + r);
                path.lineTo(cx + r * 0.75f, cy + r);
                path.lineTo(cx, cy);
                path.close();
                canvas.drawPath(path, paint);
                break;
        }
    }

    private float shortEdge() {
        return Math.min(getWidth(), getHeight());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bar == null || event.getAction() != MotionEvent.ACTION_DOWN) {
            // The strip swallows the whole gesture so a press on it never opens the clock's menu.
            return event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_MOVE;
        }
        float along = horizontal ? event.getX() : getHeight() - event.getY();
        int control = bar.controlAt(along);
        switch (control) {
            case TimerBar.CONTROL_PLAY:
                if (run == null || run.finishedAt(SystemClock.elapsedRealtime())) {
                    start();
                } else {
                    resume();
                }
                break;
            case TimerBar.CONTROL_PAUSE:
                pause();
                break;
            case TimerBar.CONTROL_STOP:
                stop();
                break;
            case TimerBar.CONTROL_HOURGLASS:
                if (listener != null) {
                    listener.choosePreset();
                }
                break;
            default:
                break;
        }
        return true;
    }

    /** Stops the redraws; the run itself is kept, so coming back shows where it got to. */
    void pauseDrawing() {
        running = false;
        handler.removeCallbacks(tick);
    }

    /** Starts them again if something is still going. */
    void resumeDrawing() {
        if (run != null && !run.isPaused() && !run.finishedAt(SystemClock.elapsedRealtime())) {
            // Whatever sounded while the clock was away has been and gone; do not play it now.
            lastCueMs = SystemClock.elapsedRealtime();
            begin();
        } else {
            invalidate();
        }
    }
}
