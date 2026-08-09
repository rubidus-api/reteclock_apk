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
import com.reteclock.core.ClockOptions;
import com.reteclock.core.ClockText;
import com.reteclock.core.FrameBudget;
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
    /** What the moving pictures are costing, and whether this device can afford them. */
    private final FrameBudget frameBudget = new FrameBudget();
    /**
     * Whether the animations have been stood down for this run. The pictures stay, frozen at the
     * frame they reached; what stops is the redrawing that was eating the whole UI thread.
     */
    private boolean heavy;
    /** Whether the frozen text-fill frame has been rendered, so it is not rendered again. */
    private boolean foregroundDrawn;
    /** The frame of the background animation drawn last, so freezing it keeps that frame. */
    private long lastFrameMs;

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
                    ? ANIMATION_FRAME_MS
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
        loadTypeface(context);
        loadImages(context);
        loadColors(context);
        // Parts are positioned by their left edge, since a line can be several of them.
        paint.setTextAlign(Paint.Align.LEFT);
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
        slide = null;
        slideBitmap = null;
        slideVisible = false;
        snapshot = null;
        fadeStart = Long.MIN_VALUE / 2L;

        textShow = null;
        foreground = null;
        dropForegroundShader();
        updateLayerType();
    }

    /** Forgets everything derived from the current text-fill image. */
    private void dropForegroundShader() {
        foregroundShader = null;
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
     * Whether the drawing still needs a software canvas: a {@link android.graphics.Movie} drawn
     * straight onto the screen, or a shader bitmap being rewritten under a live paint.
     */
    private boolean needsSoftwareLayer() {
        return (slide != null && slide.animated())
                || (foreground != null && foreground.animated() && !heavy);
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
        refreshSlideForSize(w, h);
        layout = ClockLayout.of(w, h, options);
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
            updateForegroundShader(w, h, textShow == null ? 0L : textShow.frameMs(elapsed));
        }
        paint.setShader(foreground != null ? foregroundShader : null);

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
                applyStyle(part.role, size);
                float cellStart = plan.cellStart(slot, part);
                float cellWidth = plan.cellWidth(slot, part);
                float textWidth = paint.measureText(pieces[i]);
                paint.getFontMetrics(fontMetrics);
                float baseline = slot.centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
                canvas.drawText(pieces[i], cellStart + (cellWidth - textWidth) / 2f, baseline, paint);
            }
        }
        canvas.restore();
        // The shader must not leak into measurement or into a later draw without a foreground.
        paint.setShader(null);

        if (timed) {
            frameBudget.sample(SystemClock.elapsedRealtime() - began);
            if (frameBudget.overloaded()) {
                standDownAnimation();
            }
        }
    }

    /**
     * Stops moving the pictures, because moving them costs more than this device can pay.
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
            slideshow.begin(index, slideDurationMs, nowMs);
            return;
        }
        boolean fade = fadeEnabled && slideVisible && captureSnapshot(nowMs);
        slideVisible = false;
        for (int tried = 0; tried < slides.size(); tried++) {
            int at = (index + tried) % slides.size();
            BackgroundImage decoded = BackgroundImage.load(slides.get(at));
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
            BackgroundImage decoded = BackgroundImage.load(slides.get(slideshow.index()));
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
        slide = null;
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
            BackgroundImage decoded = BackgroundImage.load(textSlides.get(at));
            if (decoded != null) {
                foreground = decoded;
                dropForegroundShader();
                textShow.begin(at,
                        Slideshow.slideDurationMs(decoded.durationMs(), stillMs), nowMs);
                updateLayerType();
                return;
            }
        }
        foreground = null;
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
            if (foregroundFrame == null) {
                try {
                    foregroundFrame = Bitmap.createBitmap(foreground.width(), foreground.height(),
                            Bitmap.Config.ARGB_8888);
                } catch (OutOfMemoryError e) {
                    // A frame too big for this device's heap means white text, not a dead clock.
                    foreground = null;
                    foregroundShader = null;
                    updateLayerType();
                    return;
                }
                foregroundFrameCanvas = new Canvas(foregroundFrame);
                foregroundShader = new BitmapShader(foregroundFrame,
                        Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                foregroundDrawn = false;
            }
            // Stood down, the fill keeps the frame it has: one render, then left alone.
            if (!heavy || !foregroundDrawn) {
                foregroundFrame.eraseColor(Color.TRANSPARENT);
                foreground.draw(foregroundFrameCanvas, frameMs, imagePaint);
                foregroundDrawn = true;
            }
        } else if (foregroundShader == null) {
            foregroundShader = new BitmapShader(foreground.still(),
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
