package com.reteclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;

import com.reteclock.core.BurnInShift;
import com.reteclock.core.ClockLayout;
import com.reteclock.core.MonthGrid;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.ClockText;
import com.reteclock.core.FramePacer;
import com.reteclock.core.ImageFit;
import com.reteclock.core.SafeStart;
import com.reteclock.core.Slideshow;

/**
 * The clock face. Draws itself with a Canvas, so it needs no layout XML and no support library.
 *
 * Only framework APIs available since API 1 are used here, which keeps the view working from
 * Android 2.3 up to current releases.
 */
public class ClockView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    /** Its own paint, because the fade sets an alpha that must never touch anything else. */
    private final Paint fadePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Handler handler = new Handler();
    // Reused every frame: Dalvik collects garbage on the UI thread, so the draw path allocates nothing.
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    /** How often an animated background redraws. GIFs rarely carry more frames than this. */
    private static final long ANIMATION_FRAME_MS = 40L;

    /** For the few lines this class says out loud; `adb logcat -s reteclock` is the whole story. */
    private static final String LOG = "reteclock";

    private static final Typeface SYSTEM_REGULAR = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface SYSTEM_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    /** How far italic leans. Synthesised, because a user font has one weight and one slant. */
    private static final float ITALIC_SKEW = -0.25f;

    /** One font per field, null where the system face is wanted. Indexed by role. */
    private final java.util.Map<String, Typeface> userFonts = new java.util.HashMap<String, Typeface>();
    /** Decorations per field, alongside the fonts. Empty means the field carries none. */
    private final java.util.Set<String> boldRoles = new java.util.HashSet<String>();
    private final java.util.Set<String> italicRoles = new java.util.HashSet<String>();
    private final java.util.Set<String> underlineRoles = new java.util.HashSet<String>();

    /** The background slideshow's files, in name order; empty for the plain black clock. */
    private final java.util.List<java.io.File> slides = new java.util.ArrayList<java.io.File>();
    /** The timing of the show, or null until the first draw begins it. */
    private Slideshow slideshow;
    /**
     * The slide on screen while it animates; a still is pre-rendered into {@link #slideBitmap}
     * instead and its decode is let go, so at rest only one screen-sized bitmap is held.
     */
    private BackgroundImage slide;
    /** A still slide, already fitted to the screen: drawing it is one unscaled blit. */
    private Bitmap slideBitmap;
    /**
     * How long the slide on screen is up for, kept so a show of one image can start it again
     * without reading and decoding the same file every time round.
     */
    private long slideDurationMs;
    /** Whether the current slide decoded to something drawable. */
    private boolean slideVisible;
    /** How long a still slide holds, from the settings. */
    private long stillMs;
    /** An {@link ImageFit} mode, read with the backgrounds. */
    private int backgroundFit;

    /** How long one background cross-fades into the next. */
    private static final long FADE_MS = 500L;
    /** The outgoing image, captured at the moment of the change, drawn over the new one thinning. */
    private Bitmap snapshot;
    private boolean fadeEnabled;
    /** When the running fade began; any time at least FADE_MS ago means no fade is running. */
    private long fadeStart = Long.MIN_VALUE / 2L;

    /** The text-fill show's files, in pool order; empty for plain white text. */
    private final java.util.List<java.io.File> textSlides = new java.util.ArrayList<java.io.File>();
    /** The timing of the text-fill show, or null until the first draw begins it. */
    private Slideshow textShow;
    /** The image the glyphs are filled with right now, or null for plain white text. */
    private BackgroundImage foreground;
    /** What the text paint actually samples: the still itself, or the animation's current frame. */
    private BitmapShader foregroundShader;
    /** The offscreen frame an animated foreground is rendered into, at the movie's own size. */
    private Bitmap foregroundFrame;
    private Canvas foregroundFrameCanvas;
    private final Matrix foregroundMatrix = new Matrix();
    /** The size the shader's matrix was computed for, so a rotation recomputes it. */
    private int foregroundForW;
    private int foregroundForH;

    /** What the glyphs are painted in when no image fills them; never equal to the background. */
    private int textColor = com.reteclock.core.ClockColors.DEFAULT_TEXT;
    /** What shows where no image does: the empty clock, letterbox bars, the fade's floor. */
    private int backgroundColor = com.reteclock.core.ClockColors.DEFAULT_BACKGROUND;

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

    /**
     * A run that leaves the imported images and fonts alone, because the run before this one never
     * became healthy. Set by whoever creates the view; see {@link SafeStart}.
     */
    private final boolean safeMode;
    /** When this run of the clock began, for the grace period the images wait out. */
    private long startedAtMs = Long.MIN_VALUE / 2L;
    /** What the moving pictures are costing, and therefore how often to ask for the next frame. */
    private final FramePacer pacer = new FramePacer();
    /**
     * Whether the animations have been given up on for this run — only when even the slowest pace
     * cannot be met. The pictures stay, frozen at the frame they reached; what stops is the
     * redrawing. Short of that, a costly picture is slowed down rather than stopped.
     */
    private boolean heavy;
    /** Whether the text fill has a frame yet, and which moment it holds if it was frozen. */
    private boolean foregroundDrawn;
    private long lastForegroundMs;
    /** The bitmap the shader was made over, so a new picture gets a new shader. */
    private Bitmap foregroundShaderSource;
    /**
     * How much of the view the timer's strip covers, on the left in landscape and at the top in
     * portrait.
     *
     * The clock draws its background across the whole view — so a picture runs behind the strip and
     * the screen is one image rather than two — and lays its text out inside what is left. Keeping
     * these apart is the whole trick: the picture does not care about the strip, and the digits
     * must not sit under it.
     */
    private int insetLeft;
    private int insetTop;

    /** The sayings, read once from the app's own resources, and which one is showing. */
    private java.util.List<com.reteclock.core.Quotes.Saying> sayings;
    private int sayingAt = -1;
    private final android.graphics.RectF quoteArea = new android.graphics.RectF();

    /** How many months away from this one the calendar is being looked at. */
    private int monthOffset;
    /** Whether the week is taken to begin on a Monday, and how the month is written. */
    private boolean weekStartsMonday;
    private int headerStyle = MonthGrid.HEADER_NAME;
    /** Where the paging arrows last were, so a touch can be tested against them. */
    private final android.graphics.RectF backArrow = new android.graphics.RectF();
    private final android.graphics.RectF forwardArrow = new android.graphics.RectF();

    /** The frame of the background animation drawn last, so freezing it keeps that frame. */
    private long lastFrameMs;
    /** The screen this device's prepared files were baked for; one pack serves both orientations. */
    private int preparedEdge;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            invalidate();
            // A moving image or a running fade needs frames; a still clock needs one redraw per
            // second. The once-a-second tick also advances the slideshow, to within a second —
            // close enough for slides that hold for many.
            long delay = fastFrames(SystemClock.elapsedRealtime())
                    ? pacer.delayMs()
                    : ClockText.millisToNextSecond(System.currentTimeMillis());
            handler.postDelayed(this, delay);
        }
    };

    public ClockView(Context context) {
        this(context, false);
    }

    /**
     * A view that, in safe mode, draws the clock and nothing else: no background pictures, no image
     * inside the glyphs, no imported font. That is the state the settings can always be reached
     * from, and it is what the next start uses when the last one never became healthy.
     */
    public ClockView(Context context, boolean safeMode) {
        super(context);
        this.safeMode = safeMode;
        options = Settings.options(context);
        loadCalendar(context);
        loadTypeface(context);
        loadImages(context);
        loadColors(context);
        // Parts are positioned by their left edge, since a line can be several of them.
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** How the calendar is to be read: which day leads the week, and how the month is written. */
    private void loadCalendar(Context context) {
        weekStartsMonday = Settings.calendarWeekStartsMonday(context);
        headerStyle = Settings.calendarHeaderStyle(context);
        monthOffset = 0;
    }

    /**
     * Reads the two colours. The settings screen refuses an equal pair; ClockColors is the second
     * line of defence, flipping the text to whatever stays visible.
     */
    private void loadColors(Context context) {
        backgroundColor = com.reteclock.core.ClockColors.opaque(
                Settings.color(context, Settings.KEY_BACKGROUND_COLOR));
        textColor = com.reteclock.core.ClockColors.resolveText(
                Settings.color(context, Settings.KEY_TEXT_COLOR), backgroundColor);
        setBackgroundColor(backgroundColor);
        paint.setColor(textColor);
    }

    /** Re-reads the options, e.g. after the user comes back from the settings screen. */
    public void reloadOptions() {
        options = Settings.options(getContext());
        loadCalendar(getContext());
        loadTypeface(getContext());
        loadImages(getContext());
        loadColors(getContext());
        layout = null;
        plan = null;
        invalidate();
    }

    /**
     * Reads the background slideshow's file list and the text-fill image.
     *
     * Slides are decoded one at a time as the show reaches them, not all up front — ten camera
     * photos would not fit an old device's heap together. The show itself starts on the first
     * draw, which is the first moment there is a frame time to start it at.
     */
    private void loadImages(Context context) {
        preparedEdge = PreparedImages.screenEdge(context);
        releaseSlide();
        releaseForeground();
        slides.clear();
        textSlides.clear();
        if (!safeMode) {
            com.reteclock.core.ImageRoles.Lists roles = Settings.roles(context);
            slides.addAll(Settings.filesFor(context, roles.background));
            textSlides.addAll(Settings.filesFor(context, roles.text));
        }
        stillMs = Settings.backgroundStillSeconds(context) * 1000L;
        backgroundFit = Settings.backgroundFit(context);
        fadeEnabled = Settings.backgroundFade(context);
        slideshow = null;
        slideBitmap = null;
        slideVisible = false;
        snapshot = null;
        fadeStart = Long.MIN_VALUE / 2L;

        textShow = null;
        foreground = null;
        dropForegroundShader();
        updateLayerType();
    }

    /** Lets go of the text-fill image, closing whatever file it had open. */
    private void releaseForeground() {
        if (foreground != null) {
            foreground.release();
            foreground = null;
        }
    }

    /** Forgets everything derived from the current text-fill image. */
    private void dropForegroundShader() {
        foregroundShader = null;
        foregroundShaderSource = null;
        foregroundFrame = null;
        foregroundFrameCanvas = null;
        foregroundDrawn = false;
        foregroundForW = 0;
        foregroundForH = 0;
    }

    /**
     * Whether anything on screen is still being redrawn as an animation, which is what asks for
     * frames in milliseconds. Once the animations have been stood down nothing is: the pictures
     * are there, they simply do not move, and the clock goes back to one redraw a second.
     */
    private boolean animating() {
        return !heavy && ((slide != null && slide.animated())
                || (foreground != null && foreground.animated()));
    }

    /**
     * Whether the drawing still needs a software canvas.
     *
     * Hardly ever, now. Both the background and the text fill render their frames into small
     * offscreen bitmaps that are then blitted or sampled, so the view stays hardware-drawn; only a
     * live Movie that could not get a buffer has to draw itself onto the canvas, which a Movie can
     * only do in software.
     *
     * The text fill used to hold the whole view on a software layer to be sure its shader re-read
     * the bitmap it samples. That cost every pixel of the screen, every frame, on the processor —
     * on a tablet it was the single most expensive thing the app did. The shader now samples a
     * bitmap whose contents change, which Android tracks by generation id and re-uploads; the
     * screenshots on API 19 show the fill moving without the layer.
     */
    private boolean needsSoftwareLayer() {
        // The text fill is never a reason: whatever draws it is handed a canvas over a bitmap of
        // ours, which a Movie is perfectly happy with. Only a background Movie left without a
        // buffer ends up drawing onto the screen's own canvas, and only that needs the layer.
        return slide != null && slide.needsSoftwareCanvas();
    }

    /** Whether a cross-fade is mid-flight. */
    private boolean fading(long nowMs) {
        return nowMs - fadeStart < FADE_MS;
    }

    /** Whether the next frame is wanted in milliseconds rather than at the next second. */
    private boolean fastFrames(long nowMs) {
        return animating() || fading(nowMs);
    }

    /**
     * Movie can only draw on a software canvas, so an animated image puts the view on a software
     * layer; anything else takes the layer away again. The software layer also keeps the animated
     * text shader honest: it samples a bitmap redrawn every frame, which a hardware texture cache
     * would be free to ignore. setLayerType arrived in API 11 — on API 9 and 10 there is no
     * hardware acceleration to switch off. Re-checked at every slide change, since one slide can
     * move and the next not.
     */
    private void updateLayerType() {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            int wanted = needsSoftwareLayer() ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_NONE;
            if (getLayerType() != wanted) {
                setLayerType(wanted, null);
            }
        }
    }

    /** Starts the once-per-second redraw. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        // The grace period runs from here, so coming back from the settings always buys another
        // window in which the clock is certain to answer a long press.
        startedAtMs = SystemClock.elapsedRealtime();
        handler.post(tick);
    }

    /** Stops redrawing so a background clock costs nothing, and closes what it had open. */
    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        // A paused clock has no business holding a file open. Coming back re-reads the settings
        // and opens whatever the show needs again.
        releaseSlide();
        releaseForeground();
        dropForegroundShader();
        slideshow = null;
        textShow = null;
        slideVisible = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        rebuild(w, h);
    }


    /**
     * A month, drawn in the space the layout set aside for it.
     *
     * Eight rows: the year and month with an arrow at each end, the seven weekday headings, then
     * six weeks — always six, so paging through the months does not make the grid breathe. It is
     * drawn with the clock's own paint, which is what makes it obey the text colour and, when one
     * is set, the picture filling the digits: the calendar is the clock's writing, not a widget
     * sitting on top of it.
     */
    private void drawCalendar(Canvas canvas, float[] rect) {
        float left = rect[0];
        float top = rect[1];
        float width = rect[2];
        float height = rect[3];
        if (width <= 0f || height <= 0f) {
            return;
        }

        int rows = MonthGrid.ROWS + 2;
        float cellWidth = width / MonthGrid.COLUMNS;
        float rowHeight = height / rows;
        float size = Math.min(rowHeight * 0.66f, cellWidth * 0.52f);

        MonthGrid grid = monthShown();
        applyStyle(ClockLayout.ROLE_MONTH_DAY, size);
        paint.setTextAlign(Paint.Align.CENTER);

        // The header: the month between two arrows, each given a whole column to be touched in.
        float headerBase = top + rowHeight / 2f - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(grid.header(headerStyle), left + width / 2f, headerBase, paint);
        canvas.drawText("<", left + cellWidth / 2f, headerBase, paint);
        canvas.drawText(">", left + width - cellWidth / 2f, headerBase, paint);
        backArrow.set(left, top, left + cellWidth, top + rowHeight);
        forwardArrow.set(left + width - cellWidth, top, left + width, top + rowHeight);

        String[] names = grid.weekdayNames();
        float namesBase = top + rowHeight * 1.5f - (paint.ascent() + paint.descent()) / 2f;
        for (int column = 0; column < MonthGrid.COLUMNS; column++) {
            canvas.drawText(names[column], left + cellWidth * (column + 0.5f), namesBase, paint);
        }

        // Today's numbers, which ClockText does not carry: it holds the strings the clock draws,
        // and a calendar needs to compare, not to print.
        java.util.Calendar now = java.util.Calendar.getInstance();
        int todayYear = now.get(java.util.Calendar.YEAR);
        int todayMonth = now.get(java.util.Calendar.MONTH) + 1;
        int todayDay = now.get(java.util.Calendar.DAY_OF_MONTH);
        boolean thisMonth = grid.holds(todayYear, todayMonth);
        for (int row = 0; row < MonthGrid.ROWS; row++) {
            float base = top + rowHeight * (row + 2.5f) - (paint.ascent() + paint.descent()) / 2f;
            for (int column = 0; column < MonthGrid.COLUMNS; column++) {
                int day = grid.dayAt(row, column);
                if (day == 0) {
                    continue;
                }
                boolean today = thisMonth && day == todayDay;
                // Today is the one day that has to be findable at a glance from across a room.
                applyStyle(ClockLayout.ROLE_MONTH_DAY, size);
                if (today) {
                    paint.setFakeBoldText(true);
                    paint.setUnderlineText(true);
                }
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(String.valueOf(day),
                        left + cellWidth * (column + 0.5f), base, paint);
                if (today) {
                    paint.setFakeBoldText(false);
                    paint.setUnderlineText(false);
                }
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * The day's saying, in the strip along the bottom.
     *
     * Up to three lines, sized so that they fill the strip; the words and the name are one string,
     * so a short saying by somebody with a long name breaks where it has to rather than by a rule
     * about which half matters. Drawn with the clock's paint in the saying's own font, so it takes
     * the text colour and the picture filling the digits like every other line.
     */
    private void drawSaying(Canvas canvas, float[] rect) {
        if (sayings == null) {
            loadSayings();
        }
        if (sayings.isEmpty()) {
            return;
        }
        if (sayingAt < 0) {
            sayingAt = com.reteclock.core.Quotes.forDay(today(), sayings.size());
        }
        String text = sayings.get(sayingAt).toString();

        float left = rect[0];
        float top = rect[1];
        float width = rect[2];
        float height = rect[3];
        quoteArea.set(left, top, left + width, top + height);

        // The largest lettering that still says the whole thing. One line is the biggest letters
        // the strip can hold but the fewest characters; three lines are a third the height each
        // and three times the room. So the three are tried from biggest to smallest and the first
        // that fits without an ellipsis wins — and a saying that fits nowhere is drawn at the
        // three-line size, which is the most of it anyone will get.
        final com.reteclock.core.QuoteLines.Width measure =
                new com.reteclock.core.QuoteLines.Width() {
                    @Override
                    public float of(String value) {
                        return paint.measureText(value);
                    }
                };
        java.util.List<String> best = null;
        float bestSize = 0f;
        for (int lineCount = 1; lineCount <= 3; lineCount++) {
            float size = height / lineCount * 0.85f;
            applyStyle(ClockLayout.ROLE_QUOTE, size);
            java.util.List<String> lines =
                    com.reteclock.core.QuoteLines.wrap(text, width, lineCount, measure);
            if (lines.isEmpty()) {
                continue;
            }
            best = lines;
            bestSize = size;
            if (!lines.get(lines.size() - 1).endsWith("...")) {
                break;
            }
        }

        if (best == null || best.isEmpty()) {
            return;
        }

        applyStyle(ClockLayout.ROLE_QUOTE, bestSize);
        paint.setTextAlign(Paint.Align.CENTER);
        float lineHeight = height / best.size();
        for (int i = 0; i < best.size(); i++) {
            paint.getFontMetrics(fontMetrics);
            float base = top + lineHeight * (i + 0.5f)
                    - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(best.get(i), left + width / 2f, base, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void loadSayings() {
        String text = "";
        try {
            java.io.InputStream in = getResources().openRawResource(R.raw.quotes);
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                text = new String(out.toByteArray(), "UTF-8");
            } finally {
                in.close();
            }
        } catch (java.io.IOException e) {
            // Nothing to show is better than a clock that will not start.
        }
        sayings = com.reteclock.core.Quotes.parse(text);
    }

    private long today() {
        java.util.TimeZone zone = java.util.TimeZone.getDefault();
        long now = System.currentTimeMillis();
        return com.reteclock.core.Quotes.dayNumber(now, zone.getOffset(now));
    }

    /**
     * Answers a touch on the saying with another saying.
     *
     * Any saying but the one already showing — see {@link com.reteclock.core.Quotes#another} —
     * because a shuffle that can land on what is there reads as a button that did not work.
     */
    boolean nextSaying(float x, float y) {
        if (layout == null || layout.quoteRect() == null || sayings == null || sayings.isEmpty()) {
            return false;
        }
        if (!quoteArea.contains(x - insetLeft, y - insetTop)) {
            return false;
        }
        sayingAt = com.reteclock.core.Quotes.another(sayingAt, sayings.size(), Math.random());
        invalidate();
        return true;
    }

    /** The month being looked at: this one, or however far the arrows have been pressed. */
    private MonthGrid monthShown() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int months = now.get(java.util.Calendar.YEAR) * 12
                + now.get(java.util.Calendar.MONTH) + monthOffset;
        return MonthGrid.of(months / 12, months % 12 + 1, weekStartsMonday);
    }

    /**
     * Answers a touch that landed on one of the paging arrows.
     *
     * The clock's own tap opens the menu, so the arrows have to be asked first — and they are only
     * there at all when the calendar is.
     */
    boolean pageCalendar(float x, float y) {
        if (layout == null || layout.calendarRect() == null) {
            return false;
        }
        float px = x - insetLeft;
        float py = y - insetTop;
        if (backArrow.contains(px, py)) {
            monthOffset--;
        } else if (forwardArrow.contains(px, py)) {
            monthOffset++;
        } else {
            return false;
        }
        invalidate();
        return true;
    }

    /** Back to this month — what the clock should show when nobody is looking at it. */
    void resetCalendarPaging() {
        if (monthOffset != 0) {
            monthOffset = 0;
            invalidate();
        }
    }

    /**
     * Works out the layout and, with it, the sizes and cells every field will use.
     *
     * This is the expensive part — it measures every string each field can ever show, in that
     * field's own font — and it is why the draw path does none of that. Called when the screen size
     * changes and when the settings do, which is the only time any of it can change.
     */
    /** Tells the clock how much of itself the timer's strip is covering. */
    void setContentInset(int left, int top) {
        if (left == insetLeft && top == insetTop) {
            return;
        }
        insetLeft = left;
        insetTop = top;
        layout = null;
        plan = null;
        invalidate();
    }

    private void rebuild(int w, int h) {
        // The background is the size of the view; the text is laid out in what the strip leaves.
        refreshSlideForSize(w, h);
        layout = ClockLayout.of(Math.max(1, w - insetLeft), Math.max(1, h - insetTop), options);
        plan = layout.plan(new ClockLayout.Metrics() {
            @Override
            public float width(String role, String text, float textSize) {
                applyStyle(role, textSize);
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
        // Timed only while something moves: on a still clock this would be one syscall a second
        // spent measuring a frame nobody is worried about.
        boolean timed = animating();
        long began = timed ? elapsed : 0L;

        // Every start draws the bare clock first. Decoding an image is the one thing here that can
        // take longer than the eye allows, so the grace period is a window in which the clock is
        // certain to answer a long press — whatever the pictures turn out to cost afterwards.
        boolean imagesReady = SafeStart.imagesReady(elapsed - startedAtMs);

        // The background sits under everything and does not follow the burn-in shift: a shifted
        // full-screen image would expose a black edge every cycle, and an image is not the kind of
        // fixed bright shape the shift exists to smear.
        if (imagesReady && !slides.isEmpty()) {
            if (slideshow == null) {
                slideshow = new Slideshow(slides.size());
                advanceTo(0, elapsed);
            } else if (slideshow.due(elapsed)) {
                advanceTo(slideshow.next(), elapsed);
            }
            if (slide != null) {
                lastFrameMs = slideshow.frameMs(elapsed);
                drawFitted(canvas, slide, lastFrameMs);
            } else if (slideVisible && slideBitmap != null) {
                canvas.drawBitmap(slideBitmap, 0f, 0f, imagePaint);
            }
            // The outgoing image, thinning over whatever replaced it — the cross-fade.
            if (fading(elapsed) && snapshot != null) {
                fadePaint.setAlpha(Slideshow.fadeOutAlpha(elapsed - fadeStart, FADE_MS));
                canvas.drawBitmap(snapshot, 0f, 0f, fadePaint);
            }
        }

        // The text-fill show runs on the same clockwork as the background's: an animated image
        // plays through once, a still holds for the chosen time, a show of one loops. No fade —
        // a shader has no alpha of its own to thin.
        if (imagesReady && !textSlides.isEmpty()) {
            if (textShow == null) {
                textShow = new Slideshow(textSlides.size());
                advanceText(0, elapsed);
            } else if (textShow.due(elapsed)) {
                advanceText(textShow.next(), elapsed);
            }
        }
        if (foreground != null) {
            updateForegroundShader(Math.max(1, w - insetLeft), Math.max(1, h - insetTop),
                    textShow == null ? 0L : textShow.frameMs(elapsed));
        }
        paint.setShader(foreground != null ? foregroundShader : null);

        canvas.save();
        canvas.translate(insetLeft + BurnInShift.offsetX(elapsed, maxShift),
                insetTop + BurnInShift.offsetY(elapsed, maxShift));

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
                applyStyle(part.role, size);
                float cellStart = plan.cellStart(slot, part);
                float cellWidth = plan.cellWidth(slot, part);
                float textWidth = paint.measureText(pieces[i]);
                paint.getFontMetrics(fontMetrics);
                float baseline = slot.centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
                canvas.drawText(pieces[i], cellStart + (cellWidth - textWidth) / 2f, baseline, paint);
            }
        }
        if (layout.calendarRect() != null) {
            drawCalendar(canvas, layout.calendarRect());
        }
        if (layout.quoteRect() != null) {
            drawSaying(canvas, layout.quoteRect());
        }
        canvas.restore();
        // The shader must not leak into measurement or into a later draw without a foreground.
        paint.setShader(null);

        if (timed) {
            pacer.sample(SystemClock.elapsedRealtime() - began);
            if (pacer.givenUp()) {
                standDownAnimation();
            }
        }
    }

    /**
     * Stops moving the pictures, because even the slowest pace costs more than this device can pay.
     *
     * The background is frozen at the frame it reached — rendered once into the screen-sized
     * bitmap a still slide would have used — and the text fill keeps whatever frame it is showing.
     * Nothing disappears; the clock simply goes back to one redraw a second, which is what it takes
     * for a long press to be delivered and for the settings to be reachable.
     */
    private void standDownAnimation() {
        if (heavy) {
            return;
        }
        heavy = true;
        // Said out loud, because from the outside a stood-down animation and one that never
        // started look identical, and that cost two rounds of guessing to tell apart.
        android.util.Log.i(LOG, "animation stood down: frames cost more than "
                + FramePacer.HOPELESS_MS + "ms each");
        if (slide != null && slide.animated()) {
            freezeSlide();
        }
        // The layer is swapped after this draw finishes rather than inside it: the canvas being
        // drawn on right now belongs to the very layer that is going away.
        handler.post(new Runnable() {
            @Override
            public void run() {
                updateLayerType();
            }
        });
    }

    /**
     * Renders the moving background's current frame into the screen bitmap and lets the animation
     * go. If the bitmap cannot be had, the animation is kept and drawn once a second instead —
     * slow, but still the picture the user chose.
     */
    private void freezeSlide() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (slideBitmap == null || slideBitmap.getWidth() != w || slideBitmap.getHeight() != h) {
            slideBitmap = screenBitmap(w, h);
            if (slideBitmap == null) {
                return;
            }
        }
        slideBitmap.eraseColor(backgroundColor);
        drawFitted(new Canvas(slideBitmap), slide, lastFrameMs);
        slide = null;
    }

    /**
     * Moves the slideshow to this slide, decoding it now — one slide is in memory at a time.
     *
     * A file that will not decode is skipped, trying each in turn so one bad file cannot stop the
     * show. If nothing decodes, the screen stays black for one still-time and the show tries
     * again — the cost of retrying is one decode attempt per slide per interval, and it means a
     * transient failure heals on its own.
     *
     * With the fade on, the outgoing image is captured at screen size first, and thins over the
     * incoming one — or over black, when the incoming one would not decode, which is still a
     * gentler exit than vanishing.
     */
    private void advanceTo(int index, long nowMs) {
        // A show of one image comes back to the image it is already showing. Reading the file and
        // decoding it again for that is pure waste — and on a slow phone with a GIF it was worse
        // than waste: a multi-megabyte decode on the UI thread every play-through, which is what
        // made the clock stop answering. Start the same slide again instead.
        if (slideVisible && index == slideshow.index() && (slide != null || slideBitmap != null)) {
            android.util.Log.d(LOG, "background show restarts slide " + index
                    + " for " + slideDurationMs + "ms, pace " + pacer.delayMs() + "ms");
            slideshow.begin(index, slideDurationMs, nowMs);
            return;
        }
        boolean fade = fadeEnabled && slideVisible && captureSnapshot(nowMs);
        slideVisible = false;
        releaseSlide();
        for (int tried = 0; tried < slides.size(); tried++) {
            int at = (index + tried) % slides.size();
            BackgroundImage decoded = openSlide(slides.get(at));
            if (decoded != null) {
                show(decoded);
                slideDurationMs = Slideshow.slideDurationMs(decoded.durationMs(), stillMs);
                slideshow.begin(at, slideDurationMs, nowMs);
                if (fade) {
                    fadeStart = nowMs;
                }
                updateLayerType();
                return;
            }
        }
        slide = null;
        slideDurationMs = Math.max(stillMs, Slideshow.MIN_SLIDE_MS);
        slideshow.begin(index, stillMs, nowMs);
        if (fade) {
            fadeStart = nowMs;
        }
        updateLayerType();
    }

    /**
     * One slide, opened from what the settings screen prepared for it if that is there.
     *
     * The prepared file holds frames already the right size and already in the screen's pixel
     * format, so playing it needs no decoding at all — which is what makes an animation affordable
     * on an old phone. Anything not yet prepared, or that could not be, falls back to decoding the
     * original here.
     */
    private BackgroundImage openSlide(java.io.File source) {
        java.io.File pack = PreparedImages.packFor(getContext(), source.getName(), preparedEdge);
        return BackgroundImage.open(source, pack);
    }

    /**
     * A turned screen invalidates everything pre-rendered at the old size: the fade is cut short
     * — its snapshot shows the old orientation — and a still slide is decoded again and fitted to
     * the new one. An animation just fits itself at the next draw.
     */
    private void refreshSlideForSize(int w, int h) {
        if (fading(SystemClock.elapsedRealtime())
                && snapshot != null && (snapshot.getWidth() != w || snapshot.getHeight() != h)) {
            fadeStart = Long.MIN_VALUE / 2L;
            snapshot = null;
        }
        if (slideshow != null && slideVisible && slide == null && slideBitmap != null
                && (slideBitmap.getWidth() != w || slideBitmap.getHeight() != h)) {
            BackgroundImage decoded = openSlide(slides.get(slideshow.index()));
            if (decoded == null) {
                slideVisible = false;
            } else {
                show(decoded);
            }
        }
    }

    /**
     * Takes the new slide on screen. An animated one is kept and drawn live; a still is rendered
     * once into a screen-sized bitmap and its decode is dropped, so drawing it costs one blit and
     * holding it costs one screenful — the decode happens again only if the screen turns.
     *
     * If the screen bitmap cannot be had, the decode is simply kept and fitted at draw time, the
     * way an animation always is.
     */
    private void show(BackgroundImage decoded) {
        slideVisible = true;
        // Once the animations have been stood down, a moving slide arrives as its first frame:
        // the still path below renders it once and never asks this device for another frame.
        if (decoded.animated() && !heavy) {
            // Settled here, before anything is drawn, because whether the offscreen buffer could
            // be had is what decides whether the view needs a software layer.
            decoded.prepareFrames(getWidth(), getHeight());
            slide = decoded;
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            slide = decoded;
            return;
        }
        if (slideBitmap == null || slideBitmap.getWidth() != w || slideBitmap.getHeight() != h) {
            slideBitmap = screenBitmap(w, h);
            if (slideBitmap == null) {
                slide = decoded;
                return;
            }
        }
        slideBitmap.eraseColor(backgroundColor);
        drawFitted(new Canvas(slideBitmap), decoded, 0L);
        // Rendered into the screen bitmap, the picture itself is no longer needed: what it holds —
        // a decode, or an open prepared file — goes now rather than at the garbage collector's
        // convenience.
        decoded.release();
        slide = null;
    }

    /** Lets go of the slide on screen, closing whatever file it had open. */
    private void releaseSlide() {
        if (slide != null) {
            slide.release();
            slide = null;
        }
    }

    /**
     * What is on screen right now, at screen size, for fading out. False when there is nothing
     * to capture into.
     */
    private boolean captureSnapshot(long nowMs) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }
        if (snapshot == null || snapshot.getWidth() != w || snapshot.getHeight() != h) {
            snapshot = screenBitmap(w, h);
            if (snapshot == null) {
                return false;
            }
        }
        snapshot.eraseColor(backgroundColor);
        Canvas canvas = new Canvas(snapshot);
        if (slide != null) {
            drawFitted(canvas, slide, slideshow.frameMs(nowMs));
        } else if (slideBitmap != null) {
            canvas.drawBitmap(slideBitmap, 0f, 0f, imagePaint);
        }
        return true;
    }

    /** One screen-sized buffer, or null on a heap that cannot spare one — then there is no fade. */
    private static Bitmap screenBitmap(int w, int h) {
        try {
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Draws this image fitted to the whole view, on whichever canvas — the screen or a buffer. */
    private void drawFitted(Canvas canvas, BackgroundImage image, long frameMs) {
        ImageFit.Placement place = ImageFit.of(getWidth(), getHeight(),
                image.width(), image.height(), backgroundFit);
        if (place == null) {
            return;
        }
        canvas.save();
        canvas.translate(place.dx, place.dy);
        canvas.scale(place.scaleX, place.scaleY);
        image.draw(canvas, frameMs, imagePaint);
        canvas.restore();
    }

    /**
     * Moves the text-fill show to this image, decoding it now, skipping what will not decode —
     * the same walk the background does. Nothing decodes: white text until the next turn.
     */
    private void advanceText(int index, long nowMs) {
        for (int tried = 0; tried < textSlides.size(); tried++) {
            int at = (index + tried) % textSlides.size();
            BackgroundImage decoded = openSlide(textSlides.get(at));
            if (decoded != null) {
                android.util.Log.d(LOG, "text fill takes slide " + at
                        + ", animated=" + decoded.animated()
                        + ", prepared frames=" + decoded.preparedFrames());
                releaseForeground();
                // Its own buffer, settled before anything is drawn — the same offscreen path the
                // background takes, so a live Movie in the glyphs costs no more than in the sky.
                decoded.prepareFrames(getWidth(), getHeight());
                foreground = decoded;
                dropForegroundShader();
                textShow.begin(at,
                        Slideshow.slideDurationMs(decoded.durationMs(), stillMs), nowMs);
                updateLayerType();
                return;
            }
        }
        releaseForeground();
        dropForegroundShader();
        textShow.begin(index, stillMs, nowMs);
        updateLayerType();
    }

    /**
     * Keeps the shader the glyphs are filled with current.
     *
     * A still image needs work only when the screen size changes: one shader, its matrix mapping
     * the image over the whole screen, cover-cropped and centred. An animated one is rendered
     * into an offscreen bitmap every frame — at the show's frame time, so a GIF plays through
     * once per turn and a show of one loops — and the shader samples that bitmap. The shader object is
     * reused: the view is on a software layer whenever the foreground animates, so the paint
     * reads the bitmap's pixels live rather than from a stale texture.
     *
     * The shader is in the canvas's coordinates at draw time, so the burn-in shift moves the
     * image with the glyphs and the picture does not swim inside the text at each step.
     */
    private void updateForegroundShader(int w, int h, long frameMs) {
        boolean resized = w != foregroundForW || h != foregroundForH;
        if (foreground.animated()) {
            // Stood down, the fill keeps the frame it has: one render, then left alone.
            long at = heavy && foregroundDrawn ? lastForegroundMs : frameMs;
            Bitmap source = foreground.frameBitmap(at);
            if (source == null) {
                // No buffer to render into: fall back to a frame of our own, which is also the
                // only case where the view still needs a software layer.
                source = ownForegroundFrame(frameMs);
                if (source == null) {
                    foreground = null;
                    foregroundShader = null;
                    updateLayerType();
                    return;
                }
            }
            lastForegroundMs = at;
            foregroundDrawn = true;
            if (foregroundShader == null || source != foregroundShaderSource) {
                // One shader over the bitmap the picture keeps refilling. Its contents change
                // under the shader, which Android tracks and re-uploads; what must not change is
                // the bitmap object, or the shader would be sampling last slide's picture.
                foregroundShaderSource = source;
                foregroundShader = new BitmapShader(source,
                        Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                resized = true;
            }
        } else if (foregroundShader == null) {
            Bitmap source = foreground.still();
            if (source == null) {
                // A prepared file whose frame could not be read: plain text, not a crash.
                foreground = null;
                updateLayerType();
                return;
            }
            foregroundShaderSource = source;
            foregroundShader = new BitmapShader(source,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        if (resized) {
            ImageFit.Placement place = ImageFit.of(w, h,
                    foreground.width(), foreground.height(), ImageFit.COVER);
            if (place != null) {
                foregroundMatrix.setScale(place.scaleX, place.scaleY);
                foregroundMatrix.postTranslate(place.dx, place.dy);
                foregroundShader.setLocalMatrix(foregroundMatrix);
            }
            foregroundForW = w;
            foregroundForH = h;
        }
    }

    /**
     * The last resort for an animated fill: a bitmap of our own for the picture to be drawn into.
     * Only a live Movie that could not make its own buffer gets here, and that is the one case the
     * software layer still exists for.
     */
    private Bitmap ownForegroundFrame(long frameMs) {
        if (foregroundFrame == null) {
            try {
                foregroundFrame = Bitmap.createBitmap(foreground.width(), foreground.height(),
                        Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                // A frame too big for this device's heap means white text, not a dead clock.
                return null;
            }
            foregroundFrameCanvas = new Canvas(foregroundFrame);
        }
        foregroundFrame.eraseColor(Color.TRANSPARENT);
        foreground.draw(foregroundFrameCanvas, frameMs, imagePaint);
        return foregroundFrame;
    }

    /**
     * Reads the chosen font. A file that is not a usable font leaves the system faces in place
     * rather than a broken clock; the settings screen refuses such files on import, and this is the
     * second line of defence for a file that goes bad afterwards.
     */
    private void loadTypeface(Context context) {
        boldRoles.clear();
        italicRoles.clear();
        underlineRoles.clear();
        for (String role : Settings.FONT_ROLES) {
            if (Settings.bold(context, role)) {
                boldRoles.add(role);
            }
            if (Settings.italic(context, role)) {
                italicRoles.add(role);
            }
            if (Settings.underline(context, role)) {
                underlineRoles.add(role);
            }
        }

        userFonts.clear();
        // A font file can hang or crash the clock as readily as a picture can, so a safe run draws
        // with the system faces and leaves the imported ones untouched until the user says so.
        if (safeMode) {
            return;
        }
        // One Typeface per font file, not per field. Six fields usually name the same font, and
        // createFromFile parses the file every time it is called — six parses of one file, on a
        // phone where that file may be several megabytes, at every start and every return from the
        // settings. The faces are immutable, so sharing one between fields is free.
        java.util.Map<String, Typeface> byFile = new java.util.HashMap<String, Typeface>();
        for (String role : Settings.FONT_ROLES) {
            java.io.File file = Settings.fontFileFor(context, role);
            if (file == null) {
                continue;
            }
            String key = file.getPath();
            Typeface face = byFile.get(key);
            if (face == null && !byFile.containsKey(key)) {
                try {
                    face = Typeface.createFromFile(file);
                } catch (RuntimeException e) {
                    // A file that stops loading leaves that field on the system face rather than
                    // taking the whole clock down.
                    face = null;
                }
                byFile.put(key, face);
            }
            if (face != null) {
                userFonts.put(role, face);
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
            // Punctuation goes on the end of the piece before it, drawn in that field's font,
            // because a comma or a colon belongs to what it follows. The whitespace does not: it is
            // reserved as a gap by the plan and nothing is painted there, so underlining a field
            // cannot underline the space beside it.
            if (i > 0) {
                String punctuation = ClockLayout.visibleOf(part.separatorBefore);
                if (!punctuation.isEmpty()) {
                    pieces[i - 1] = pieces[i - 1] + punctuation;
                }
            }
            pieces[i] = text;
            anything = anything || !text.isEmpty();
        }
        return anything ? pieces : null;
    }

    /** Sets the paint up for one field: its font and its decorations. */
    private void applyStyle(String role, float textSize) {
        Typeface font = userFonts.get(role);
        // Weight comes from the field's own bold toggle and from nothing else. The layout used to
        // make the hour and minute bold whatever the user wanted, which meant bold could be turned
        // on but never off for the two fields most likely to want a light face.
        boolean wantBold = boldRoles.contains(role);
        if (font != null) {
            // A user font has one weight, so bold over it is synthesised.
            paint.setTypeface(font);
            paint.setFakeBoldText(wantBold);
        } else {
            paint.setTypeface(wantBold ? SYSTEM_BOLD : SYSTEM_REGULAR);
            paint.setFakeBoldText(false);
        }
        paint.setTextSkewX(italicRoles.contains(role) ? ITALIC_SKEW : 0f);
        paint.setUnderlineText(underlineRoles.contains(role));
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
