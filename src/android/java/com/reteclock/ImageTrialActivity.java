package com.reteclock;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import com.reteclock.core.ImageQuality;
import com.reteclock.core.ImageTrial;

/**
 * The test a quality step has to pass before it may be switched on (RFC-0011).
 *
 * The rule this screen exists to keep: **a step is not saved when it is chosen, it is saved when it
 * has been survived.** So the order here is exact and worth reading in order —
 *
 * <ol>
 *   <li>write the mark, and nothing else;</li>
 *   <li>bake the largest animation the user has, at the step being tried, for real;</li>
 *   <li>play it for a few seconds, timing every frame;</li>
 *   <li>if the numbers are good, write the step and clear the mark.</li>
 * </ol>
 *
 * If the app dies or hangs anywhere in that, the only thing on disc is the mark — and the next
 * start reads a mark with no step beside it as "this phone did not survive that", refuses the step
 * and says so. **The worst a failed trial can do is fail:** the clock is never asked to use a step
 * that has not already been played through once, so a step that kills the app cannot leave a clock
 * that will not start.
 *
 * It runs here, in the settings, rather than on the clock, for the same reason: even a trial that
 * hangs leaves the clock alone.
 */
public final class ImageTrialActivity extends Activity {

    /** Which step is being tried. */
    public static final String EXTRA_STEP = "com.reteclock.TRIAL_STEP";

    private static final int BACKDROP = 0xFF101010;
    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int WARNING = 0xFFE57373;

    private final Handler handler = new Handler();
    private final long[] frameTimes = new long[600];

    private int step;
    private TextView saying;
    private TextView numbers;
    private Stage stage;

    private File pack;
    private long bakeMs;
    private int framesDrawn;
    private long startedAt;
    private long lastFrameAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        step = ImageQuality.of(getIntent() == null ? ImageQuality.FLOOR
                : getIntent().getIntExtra(EXTRA_STEP, ImageQuality.FLOOR));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKDROP);
        root.setPadding(dp(14), dp(16), dp(14), dp(16));

        TextView title = new TextView(this);
        title.setText(R.string.quality_trial_title);
        title.setTextColor(ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        root.addView(title);

        saying = new TextView(this);
        saying.setText(R.string.quality_trial_baking);
        saying.setTextColor(TEXT_WHITE);
        saying.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        saying.setPadding(0, dp(8), 0, dp(8));
        root.addView(saying);

        stage = new Stage(this);
        stage.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(stage);

        numbers = new TextView(this);
        numbers.setTextColor(TEXT_DIM);
        numbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        numbers.setPadding(0, dp(8), 0, 0);
        root.addView(numbers);

        setContentView(root);

        // The mark first, and nothing else. Everything after this line may fail to return.
        Settings.beginImageTrial(this, step);
        handler.post(new Runnable() {
            @Override
            public void run() {
                bake();
            }
        });
    }

    /**
     * Bakes the hardest picture the user has, at the step being tried.
     *
     * The hardest rather than a sample of our own: the question is whether *this* phone can play
     * *these* pictures, and a stock animation would answer a different one.
     */
    private void bake() {
        File source = hardestPicture();
        if (source == null) {
            finishWith(false, getString(R.string.quality_trial_no_pictures), null);
            return;
        }
        if (ImageQuality.playsOriginal(step, android.os.Build.VERSION.SDK_INT)) {
            // Nothing is baked at this step, so what is timed instead is the platform opening the
            // file — which is the wait a person actually has at this step, and the same question
            // the bake's seconds answer at the others. The pack stays null, so the disc budget is
            // measured as nothing, which is the truth: there is nothing on disc.
            long opened = SystemClock.elapsedRealtime();
            stage.openLive(source);
            bakeMs = SystemClock.elapsedRealtime() - opened;
            if (!stage.ready()) {
                finishWith(false, getString(R.string.quality_trial_cannot_bake), null);
                return;
            }
            saying.setText(R.string.quality_trial_playing);
            startedAt = SystemClock.elapsedRealtime();
            lastFrameAt = startedAt;
            handler.post(tick);
            return;
        }
        pack = new File(getCacheDir(), "trial." + ImageQuality.tag(step) + ".pack");
        pack.delete();
        long began = SystemClock.elapsedRealtime();
        boolean made = PreparedImages.prepare(source, pack, PreparedImages.screenEdge(this), step);
        bakeMs = SystemClock.elapsedRealtime() - began;
        if (!made || !pack.isFile()) {
            finishWith(false, getString(R.string.quality_trial_cannot_bake), null);
            return;
        }
        stage.open(pack);
        if (!stage.ready()) {
            finishWith(false, getString(R.string.quality_trial_cannot_bake), null);
            return;
        }
        saying.setText(R.string.quality_trial_playing);
        startedAt = SystemClock.elapsedRealtime();
        lastFrameAt = startedAt;
        handler.post(tick);
    }

    /** The largest picture in use, which is the one most likely to be too much for the phone. */
    private File hardestPicture() {
        com.reteclock.core.FontLibrary pool = Settings.images(this);
        com.reteclock.core.ImageRoles.Lists roles = Settings.roles(this);
        File biggest = null;
        long most = 0;
        for (com.reteclock.core.FontLibrary.Entry entry : pool.list()) {
            if (com.reteclock.core.ImageRoles.roleOf(roles, entry.name)
                    == com.reteclock.core.ImageRoles.NONE) {
                continue;
            }
            File file = pool.file(entry.name);
            if (file != null && file.length() > most) {
                most = file.length();
                biggest = file;
            }
        }
        return biggest;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            if (framesDrawn < frameTimes.length) {
                frameTimes[framesDrawn] = now - lastFrameAt;
            }
            lastFrameAt = now;
            framesDrawn++;
            stage.showAt(now - startedAt);

            if (now - startedAt >= ImageTrial.PLAY_MS) {
                judge();
                return;
            }
            handler.postDelayed(this, 16L);
        }
    };

    /** The measurements, the verdict, and — only if it passed — the setting. */
    private void judge() {
        // The first frames include the decode of the first picture and the window settling; they
        // are the truth about starting, not about playing, and they are dropped for that reason.
        int skip = Math.min(5, framesDrawn);
        int counted = Math.max(0, Math.min(framesDrawn, frameTimes.length) - skip);
        long[] kept = new long[counted];
        System.arraycopy(frameTimes, skip, kept, 0, counted);

        ImageTrial.Measured measured = new ImageTrial.Measured(bakeMs,
                ImageTrial.median(kept, counted), ImageTrial.worst(kept, counted),
                framesDrawn, pack == null ? 0L : pack.length());
        boolean passed = ImageTrial.passed(measured, step, ImageQuality.budgetBytes(step));
        finishWith(passed, ImageTrial.complaint(measured, step, ImageQuality.budgetBytes(step)),
                measured);
    }

    /**
     * How every road out of here ends: the mark is cleared, and the step is written only on a pass.
     *
     * A failure is remembered as well — not to punish the phone, but so the step is not offered
     * again as though it were a fresh question.
     */
    private void finishWith(boolean passed, String complaint, ImageTrial.Measured measured) {
        stage.close();
        if (pack != null) {
            pack.delete();
        }
        if (passed) {
            Settings.setImageQuality(this, step);
        } else {
            Settings.refuseImageStep(this, step);
        }
        Settings.endImageTrial(this);

        saying.setText(passed ? getString(R.string.quality_trial_passed)
                : getString(R.string.quality_trial_failed, complaint));
        saying.setTextColor(passed ? ACCENT : WARNING);
        numbers.setText(measured == null ? "" : getString(R.string.quality_trial_numbers,
                measured.bakeMs / 1000f, measured.medianFrameMs, measured.worstFrameMs,
                measured.framesDrawn, FontLibraryBytes(measured.packBytes)));
    }

    private static String FontLibraryBytes(long bytes) {
        return com.reteclock.core.FontLibrary.humanBytes(bytes);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        // Leaving in the middle is not a pass and is not a failure of the phone: the mark goes, so
        // the next start does not read a walk-away as a crash.
        if (Settings.unfinishedImageTrial(this) >= 0) {
            Settings.endImageTrial(this);
        }
        stage.close();
        if (pack != null) {
            pack.delete();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Where the picture is actually played, so the numbers are about drawing and not about maths. */
    private final class Stage extends View {

        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect source = new Rect();
        private final RectF target = new RectF();
        private PreparedImage prepared;
        /** The platform's own drawable, at the top step; then {@link #prepared} is not used. */
        private android.graphics.drawable.Drawable live;
        private Bitmap showing;

        Stage(Activity activity) {
            super(activity);
            setBackgroundColor(Color.BLACK);
        }

        void open(File path) {
            prepared = PreparedImage.open(path);
        }

        /**
         * Opens the picture the way the top step plays it: handed to the platform, nothing baked.
         *
         * Guarded by the caller's SDK test, like every other road to {@link NativeAnimation}.
         */
        void openLive(File source) {
            live = NativeAnimation.open(source);
            if (live != null) {
                // The same rule as on the clock: it advances because it asks to be redrawn, and it
                // asks through the view. Without this the trial would time a picture standing still
                // and call the phone quick.
                live.setCallback(this);
            }
        }

        boolean ready() {
            return prepared != null || live != null;
        }

        void showAt(long elapsedMs) {
            if (live != null) {
                // The platform advances this one by how long it has been since it was last drawn,
                // so there is no frame to fetch — only a redraw to ask for, which is what is being
                // timed.
                invalidate();
                return;
            }
            if (prepared == null) {
                return;
            }
            int duration = prepared.durationMs();
            showing = prepared.frame(duration > 0 ? elapsedMs % duration : 0L);
            invalidate();
        }

        void close() {
            if (prepared != null) {
                prepared.release();
                prepared = null;
            }
            if (live != null) {
                NativeAnimation.stop(live);
                live.setCallback(null);
                live = null;
            }
            showing = null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (live != null) {
                float scale = Math.max(getWidth() / (float) live.getIntrinsicWidth(),
                        getHeight() / (float) live.getIntrinsicHeight());
                float w = live.getIntrinsicWidth() * scale;
                float h = live.getIntrinsicHeight() * scale;
                live.setBounds((int) ((getWidth() - w) / 2f), (int) ((getHeight() - h) / 2f),
                        (int) ((getWidth() + w) / 2f), (int) ((getHeight() + h) / 2f));
                live.draw(canvas);
                return;
            }
            if (showing == null || showing.isRecycled()) {
                return;
            }
            source.set(0, 0, showing.getWidth(), showing.getHeight());
            float scale = Math.max(getWidth() / (float) showing.getWidth(),
                    getHeight() / (float) showing.getHeight());
            float w = showing.getWidth() * scale;
            float h = showing.getHeight() * scale;
            target.set((getWidth() - w) / 2f, (getHeight() - h) / 2f,
                    (getWidth() + w) / 2f, (getHeight() + h) / 2f);
            canvas.drawBitmap(showing, source, target, paint);
        }
    }
}
