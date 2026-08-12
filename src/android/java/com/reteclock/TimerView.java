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
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
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

    /**
     * Stops for good: no more ticks, no more sounds, and nothing said to the listener.
     *
     * The screen builds a fresh strip whenever it is turned or returned to, and the one it drops
     * used to carry on ticking in the background — still holding its own run, still sounding it
     * through the listener it shares with the live one. That is why a stopped timer went on
     * beeping at times that made no sense. A view that leaves the window now falls silent.
     */
    void retire() {
        running = false;
        handler.removeCallbacks(tick);
        listener = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        retire();
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
        if (horizontal) {
            canvas.drawRect(fromAlong, bar.barNear(), toAlong, bar.barFar(), paint);
        } else {
            // Landscape counts upwards from the bottom of the screen.
            canvas.drawRect(bar.barNear(), getHeight() - toAlong, bar.barFar(),
                    getHeight() - fromAlong, paint);
        }
    }

    /**
     * The three times, written inside the bar: the whole length at the left, how much has gone in
     * the middle, and what is left at the right — left, middle and right along the bar, whichever
     * way up the strip is.
     *
     * They sit in the bar rather than beside it because beside it they were forever either touching
     * the bar or being clipped by the end of the strip. Inside, each is outlined in the bar's own
     * dark so it stays readable over the fill, whatever colour the interval has reached.
     */
    private void drawReadouts(Canvas canvas, long now, float progress) {
        long total = run == null
                ? (preset == null ? 0L : preset.totalMs())
                : run.totalMs();
        long gone = run == null ? 0L : run.elapsedAt(now);
        long left = total - gone;

        String whole = TimeReadout.trimmed(total);
        String elapsed = TimeReadout.trimmed(gone);
        String remaining = TimeReadout.trimmed(left);

        // The geometry sizes them by counting characters, which is close enough to start from but
        // not close enough to trust: a colon is not a digit's width, and the middle readout grows a
        // hundredths place and loses it again while it runs. So the three are measured in the font
        // actually being drawn, and shrunk until they and the air between them fit the bar.
        float size = bar.textSize(whole.length() + elapsed.length() + remaining.length());
        float room = bar.barEnd() - bar.barStart() - size * 0.5f;
        paint.setTextSize(size);
        float wide = paint.measureText(whole) + paint.measureText(elapsed)
                + paint.measureText(remaining) + size * 0.8f;
        if (wide > room && wide > 0f) {
            size *= room / wide;
            paint.setTextSize(size);
        }

        text(canvas, whole, bar.totalAt(), bar.barMiddle(), Paint.Align.LEFT);
        text(canvas, elapsed, middleOf(whole, elapsed, remaining), bar.barMiddle(),
                Paint.Align.CENTER);
        text(canvas, remaining, bar.remainingAt(), bar.barMiddle(), Paint.Align.RIGHT);
    }

    /**
     * Where the middle readout sits: the middle of the bar, unless that would put it against one of
     * its neighbours, in which case as near the middle as it can get without touching them.
     */
    private float middleOf(String whole, String elapsed, String remaining) {
        float inset = paint.getTextSize() * 0.25f;
        float gap = paint.getTextSize() * 0.4f;
        float half = paint.measureText(elapsed) / 2f;
        float low = bar.barStart() + inset + paint.measureText(whole) + gap + half;
        float high = bar.barEnd() - inset - paint.measureText(remaining) - gap - half;
        if (low > high) {
            return bar.midAt();
        }
        float at = bar.midAt();
        return at < low ? low : at > high ? high : at;
    }

    /**
     * One readout, at {@code along} the strip and centred on the lane {@code across} it.
     *
     * The baseline comes from the font's own metrics rather than from a guessed fraction of the
     * text size, so the line really is centred in its lane and cannot creep over the bar or off the
     * edge. In landscape the whole thing is turned a quarter turn, which is one rotate rather than
     * a second code path.
     */
    private void text(Canvas canvas, String value, float along, float across, Paint.Align align) {
        paint.setTextAlign(align);
        paint.getFontMetrics(metrics);
        float baseline = -(metrics.ascent + metrics.descent) / 2f;
        // A little air at the ends so a left- or right-aligned readout does not touch the edge.
        float inset = bar.textSize() * 0.25f;
        float at = align == Paint.Align.LEFT ? along + inset
                : align == Paint.Align.RIGHT ? along - inset
                : along;

        canvas.save();
        if (!horizontal) {
            // Landscape: along counts up from the bottom, across from the left.
            canvas.rotate(-90f, across, getHeight() - at);
        }
        float x = horizontal ? at : across;
        float y = (horizontal ? across : getHeight() - at) + baseline;
        // Outlined, so it reads over the track and over the fill alike.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, bar.textSize() * 0.12f));
        paint.setColor(TRACK);
        canvas.drawText(value, x, y, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TEXT);
        canvas.drawText(value, x, y, paint);
        canvas.restore();
        paint.setTextAlign(Paint.Align.CENTER);
    }

    /** Hourglass, stop, pause, play — drawn rather than shipped, like everything else here. */
    private void drawControls(Canvas canvas) {
        float size = Math.min(bar.thickness() * 1.1f, shortEdge() * 0.7f);
        for (int i = 0; i < bar.controlCount(); i++) {
            float along = bar.controlCenter(i);
            float middle = (bar.barNear() + bar.barFar()) / 2f;
            float cx = horizontal ? along : middle;
            float cy = horizontal ? middle : getHeight() - along;
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
