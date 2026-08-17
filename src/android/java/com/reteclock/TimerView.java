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

import com.reteclock.core.ColorText;
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
    /** How much of its colour a control keeps when it has nothing to do. */
    private static final int DIM_ALPHA = 0x66;

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
    /** Showing the hourglass alone, with the clock having the rest of the screen back. */
    private boolean hidden;
    /** The blink of a running hourglass: a second long, lit for most of it. */
    private static final long BLINK_MS = 1000L;
    private static final long BLINK_LIT_MS = 600L;
    /** The clock's text colour, which the controls are drawn in. */
    private int chromeControl = CONTROL;
    /** Settled by {@link #settleReadouts()}; never recomputed while drawing. */
    private float readoutSize;
    private float readoutMiddle;
    /** When the cues were last collected, so the next window starts where that one ended. */
    private long lastCueMs = Long.MIN_VALUE / 2L;

    private Listener listener;

    /** What the strip needs the world to do for it. */
    interface Listener {
        /** Write the run down, or forget it, so another screen can pick it up where it stands. */
        void remember(TimerRun run);

        /** Play this pattern, however the settings say it should be heard. */
        void cue(Tones.Note[] pattern);

        /**
         * Play the sound this cue was given, falling back to the pattern when there is none.
         *
         * The strip knows which sound a slot names; what a sound *is* — a file, a clip, a decoder —
         * belongs to the screen, which is where the player and the settings live.
         */
        void sound(String name, Tones.Note[] fallback);

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

    /**
     * Puts the strip away, leaving only the hourglass.
     *
     * A timer that is not being watched still runs: the beeps and the speech carry on, and the
     * hourglass is where they are called back from. Hiding is about the screen, not the clock.
     */
    /**
     * The colour the clock draws its text in, which the strip's controls take too.
     *
     * The strip paints no background of its own: the clock is laid out behind it at full size, so
     * whatever is back there — a colour or a picture — runs under the strip and the screen stays
     * one image instead of two.
     */
    void setChrome(int control) {
        chromeControl = control;
        invalidate();
    }

    void setHidden(boolean hide) {
        hidden = hide;
        invalidate();
    }

    /** Which preset the controls will start, and what the bar shows before anything runs. */
    void setPreset(TimerPreset preset) {
        this.preset = preset;
        settleReadouts();
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
        // The preset begins three seconds from now, not this instant: pressing play and finding the
        // clock already running is no use to somebody timing something they do with their hands.
        // Those three seconds are counted out loud — see TimerCues.LEAD_IN_SECONDS.
        run = TimerRun.start(preset, now + TimerCues.LEAD_IN_MS);
        // A window that opens a moment before the count, so its first beep is caught.
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
                    listener.sound(cue.interval < preset.intervals.size()
                            ? preset.intervals.get(cue.interval).preAlarmSound : "",
                            Tones.PRE_ALARM);
                    break;
                case TimerCues.TICK:
                    listener.cue(Tones.TICK);
                    break;
                case TimerCues.START:
                    // The high one, landing on the moment the preset really begins — the same
                    // sound as an ending, because it marks an instant just as exactly. A preset
                    // with a sound of its own plays that instead.
                    listener.sound(preset.startSound, Tones.END);
                    break;
                case TimerCues.END:
                    listener.cue(Tones.END);
                    listener.flash();
                    break;
                case TimerCues.FINISH:
                    listener.sound(preset.finishSound, Tones.FINISH);
                    break;
                case TimerCues.SPEAK:
                    if (cue.interval < preset.intervals.size()) {
                        // The message and the sound share this moment rather than replacing each
                        // other: one says what is beginning, the other marks that it has.
                        String named = preset.intervals.get(cue.interval).startSound;
                        if (!named.isEmpty()) {
                            listener.sound(named, null);
                        }
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
        settleReadouts();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bar == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long began = isRunning() ? now : 0L;

        if (hidden) {
            drawHourglassAlone(canvas);
            if (began != 0L) {
                pacer.sample(SystemClock.elapsedRealtime() - began);
            }
            return;
        }

        float progress = run == null ? 0f : run.progressAt(now);
        TimerInterval interval = run == null
                ? (preset == null || preset.intervals.isEmpty() ? null : preset.intervals.get(0))
                : run.intervalObjectAt(now);
        drawIntervals(canvas, progress);
        drawReadouts(canvas, now, progress);
        drawControls(canvas);

        if (began != 0L) {
            pacer.sample(SystemClock.elapsedRealtime() - began);
        }
    }

    /**
     * The whole preset along the bar: every interval in its own place, in its own two colours.
     *
     * Each interval owns the share of the length its duration is worth. Within that share, what the
     * fill has already passed is drawn in the interval's second colour and what is still to come in
     * its first — so the bar reads as the shape of the whole session, and you can see at a glance
     * that the long green stretch is followed by a short orange one. Either colour may be absent,
     * and then the clock shows through that piece.
     */
    private void drawIntervals(Canvas canvas, float progress) {
        paint.setStyle(Paint.Style.FILL);
        float edge = bar.fillAt(progress);

        if (preset == null || preset.intervals.isEmpty()) {
            piece(canvas, bar.barStart(), bar.barEnd(), TimerInterval.DEFAULT_COLOR);
            return;
        }
        for (int i = 0; i < preset.intervals.size(); i++) {
            TimerInterval part = preset.intervals.get(i);
            float from = bar.fillAt(preset.startFraction(i));
            float to = bar.fillAt(preset.endFraction(i));
            // Gone, then still to come. Either can be empty, which draws nothing.
            piece(canvas, from, Math.min(edge, to), part.endColor);
            piece(canvas, Math.max(edge, from), to, part.color);
        }
    }

    /** One stretch of the bar in one colour; nothing at all if the colour is none. */
    private void piece(Canvas canvas, float fromAlong, float toAlong, int color) {
        if (toAlong <= fromAlong || ColorText.isNone(color)) {
            return;
        }
        paint.setColor(color);
        rect(canvas, fromAlong, toAlong);
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
        if (readoutSize <= 0f) {
            return;
        }
        // The bar shows the whole preset; the numbers show the interval you are actually in. Its
        // own length, how far into it you are, and what is left of it — which is what somebody
        // timing a rest between sets wants, rather than the total of a session they can see.
        TimerInterval part = run == null
                ? (preset == null || preset.intervals.isEmpty() ? null : preset.intervals.get(0))
                : run.intervalObjectAt(now);
        long total = part == null ? 0L : part.lengthMs;
        // Both readouts are cut down to whole seconds, so the time left is taken from the elapsed
        // time *as shown* rather than from the exact one. Otherwise a twenty-second interval reads
        // "20, 13, 6" halfway through — both truncated, and a second lost between them.
        long gone = run == null ? 0L : run.elapsedInIntervalAt(now) / 1000L * 1000L;
        long left = Math.max(total - gone, 0L);

        // While it is counting in, the middle readout counts down with the beeps rather than
        // sitting at zero, so the three seconds read as "starting" and not as "did I press it?".
        long before = run == null ? 0L : -run.rawElapsedAt(now);
        String middle = before > 0L
                ? "-" + ((before + 999L) / 1000L)
                : TimeReadout.trimmed(gone);

        paint.setTextSize(readoutSize);
        text(canvas, TimeReadout.trimmed(total), bar.totalAt(), bar.barMiddle(), Paint.Align.LEFT);
        text(canvas, middle, readoutMiddle, bar.barMiddle(), Paint.Align.CENTER);
        text(canvas, TimeReadout.trimmed(left), bar.remainingAt(), bar.barMiddle(),
                Paint.Align.RIGHT);
    }

    /**
     * Decides, once, how the three readouts will be lettered — before anything is drawn with them.
     *
     * They used to be sized frame by frame from the strings of that instant, which meant the
     * lettering changed size whenever a readout gained or lost a digit, and could be shrunk to
     * nothing when the numbers were briefly long. Nothing about the size actually depends on the
     * moment: the longest any of the three can ever be is the whole preset's length, written out.
     * So that is measured once, here, and kept until the strip or the preset changes.
     */
    private void settleReadouts() {
        readoutSize = 0f;
        if (bar == null) {
            return;
        }
        // The readouts speak about one interval at a time, so the longest of them is the widest
        // the numbers can ever be — not the preset's total.
        long longest = 0L;
        if (preset != null) {
            for (TimerInterval part : preset.intervals) {
                longest = Math.max(longest, part.lengthMs);
            }
        }
        String widest = TimeReadout.trimmed(longest);
        float size = bar.textSize(widest.length() * 3);
        float room = bar.barEnd() - bar.barStart();
        if (size <= 0f || room <= 0f) {
            return;
        }
        // Three of the widest, the air between them and the air at the ends, measured in the font
        // that will draw them rather than guessed from a character count.
        paint.setTextSize(size);
        float needed = paint.measureText(widest) * 3f + size * 1.3f;
        if (needed > room) {
            size *= room / needed;
            paint.setTextSize(size);
        }
        readoutSize = size;

        // The middle one sits in the middle of the bar unless the widest case would put it against
        // a neighbour, in which case it sits as near the middle as it can. Fixed here too: the
        // moving readout must not shuffle sideways as its own digits change.
        float widestWidth = paint.measureText(widest);
        float low = bar.barStart() + size * 0.25f + widestWidth + size * 0.4f + widestWidth / 2f;
        float high = bar.barEnd() - size * 0.25f - widestWidth - size * 0.4f - widestWidth / 2f;
        float middle = bar.midAt();
        readoutMiddle = low > high ? middle : middle < low ? low : middle > high ? high : middle;
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
    /**
     * The one control that is left when the strip is emptied — on the pixel it was already on.
     *
     * The strip keeps its place in the layout when it is hidden, so the hourglass has no reason to
     * move: hiding should look like everything else fading out around it, not like a button
     * jumping to a corner.
     */
    private void drawHourglassAlone(Canvas canvas) {
        if (bar == null) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(hourglassColor());
        float size = Math.min(bar.thickness() * 1.1f, shortEdge() * 0.7f);
        float along = bar.controlCenter(TimerBar.CONTROL_HOURGLASS);
        float middle = (bar.barNear() + bar.barFar()) / 2f;
        float cx = horizontal ? along : middle;
        float cy = horizontal ? middle : getHeight() - along;
        drawControl(canvas, TimerBar.CONTROL_HOURGLASS, cx, cy, size * 0.42f);
    }

    /**
     * The hourglass while a run is going: on, then faint, once a second.
     *
     * A timer somebody else set going is otherwise invisible on a clock left on a shelf — the
     * strip can be hidden, and the hourglass alone looks exactly like an hourglass at rest. A
     * blink says "this is counting" from across the room, and says it whether the strip is showing
     * or put away.
     */
    private int hourglassColor() {
        if (!isRunning()) {
            return chromeControl;
        }
        return SystemClock.elapsedRealtime() % BLINK_MS < BLINK_LIT_MS
                ? chromeControl : dimmed(chromeControl);
    }

    /** The same colour, faded, for a control there is nothing to do with just now. */
    private static int dimmed(int color) {
        return (color & 0x00FFFFFF) | (DIM_ALPHA << 24);
    }

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
            paint.setColor(i == TimerBar.CONTROL_HOURGLASS ? hourglassColor()
                    : lit ? chromeControl : dimmed(chromeControl));
            // Upright in both orientations. The readouts are turned with the strip because they
            // are words and must be read along it, but a symbol is not read that way: an hourglass
            // laid on its side stops being an hourglass and becomes a mourning ribbon, and play
            // pointing up reads as "eject". Sand falls downwards whichever way the phone is held.
            drawControl(canvas, i, cx, cy, size * 0.42f);
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
        if (hidden) {
            // The whole of what is left is the hourglass, so anywhere on it opens the list.
            if (event.getAction() == MotionEvent.ACTION_DOWN && listener != null) {
                listener.choosePreset();
            }
            return true;
        }
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
