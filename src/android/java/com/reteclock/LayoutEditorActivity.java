package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.layout.Anchor;
import com.reteclock.core.layout.BoxPlan;
import com.reteclock.core.layout.Grab;
import com.reteclock.core.layout.LayoutBook;
import com.reteclock.core.layout.LayoutBox;
import com.reteclock.core.layout.LayoutPreset;

import java.util.ArrayList;
import java.util.List;

/**
 * The canvas: the phone's own screen, to scale, with the layout's boxes on it (RFC-0005, phase 4).
 *
 * Three gestures, as the owner asked for them: **a corner resizes**, **the inside moves**, and **a
 * long press opens the numbers** — a box's place and size typed in, for when a fingertip is not
 * precise enough. Everything else about a box — its font, where its writing sits inside it, whether
 * it is shown at all — is a list on another screen, because those are choices rather than gestures
 * and a list is where choices belong.
 *
 * The arithmetic is all in {@link Grab}; what is here is drawing, which is the part no test on this
 * machine can reach. That division is deliberate.
 */
public final class LayoutEditorActivity extends Activity {

    /** Which preset, and which way up, this is editing. */
    public static final String EXTRA_INDEX = "com.reteclock.LAYOUT_INDEX";
    public static final String EXTRA_LANDSCAPE = "com.reteclock.LAYOUT_LANDSCAPE";

    private static final int BACKDROP = 0xFF101010;
    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF4DB6AC;

    private int index;
    private boolean landscape;
    private List<LayoutBox> boxes = new ArrayList<LayoutBox>();
    private final List<List<LayoutBox>> undo = new ArrayList<List<LayoutBox>>();
    private int selected = -1;

    private Canvas2D canvas;
    private TextView complaint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        index = getIntent() == null ? 1 : getIntent().getIntExtra(EXTRA_INDEX, 1);
        landscape = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LANDSCAPE, false);

        LayoutPreset preset = Settings.layouts(this).get(index);
        List<LayoutBox> drawn = landscape ? preset.landscape() : preset.portrait();
        if (drawn.isEmpty()) {
            // Nothing drawn for this way up yet: start from what the app would draw, which is what
            // "edit this layout" means when the layout has not been touched.
            List<LayoutBox> automatic = com.reteclock.core.layout.Builtin.of(
                    landscape ? longEdge() : shortEdge(), landscape ? shortEdge() : longEdge(),
                    Settings.options(this));
            drawn = automatic == null ? new ArrayList<LayoutBox>() : automatic;
        }
        boxes = new ArrayList<LayoutBox>(drawn);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKDROP);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText(getString(landscape ? R.string.layout_edit_landscape
                : R.string.layout_edit_portrait, preset.name));
        title.setTextColor(ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        root.addView(title);

        canvas = new Canvas2D(this);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        canvasParams.topMargin = dp(8);
        canvasParams.bottomMargin = dp(8);
        canvas.setLayoutParams(canvasParams);
        root.addView(canvas);

        complaint = new TextView(this);
        complaint.setTextColor(TEXT_DIM);
        complaint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        root.addView(complaint);

        root.addView(buttons());
        setContentView(root);
        refreshComplaints();
    }

    private LinearLayout buttons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button(getString(R.string.layout_edit_fields), new Runnable() {
            @Override
            public void run() {
                save();
                Intent intent = new Intent(LayoutEditorActivity.this,
                        LayoutFieldsActivity.class);
                intent.putExtra(LayoutFieldsActivity.EXTRA_INDEX, index);
                intent.putExtra(LayoutFieldsActivity.EXTRA_LANDSCAPE, landscape);
                startActivity(intent);
            }
        }));
        row.addView(button(getString(R.string.layout_edit_undo), new Runnable() {
            @Override
            public void run() {
                if (!undo.isEmpty()) {
                    boxes = undo.remove(undo.size() - 1);
                    save();
                    canvas.invalidate();
                    refreshComplaints();
                }
            }
        }));
        row.addView(button(getString(R.string.layout_edit_save), new Runnable() {
            @Override
            public void run() {
                save();
                finish();
            }
        }));
        return row;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Leaving by any road keeps the work: a layout is minutes of fiddling and losing it to the
        // back button would be the cruellest possible way to lose it.
        save();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The fields screen may have hidden a box or changed its alignment while we were away.
        LayoutPreset preset = Settings.layouts(this).get(index);
        List<LayoutBox> drawn = landscape ? preset.landscape() : preset.portrait();
        if (!drawn.isEmpty()) {
            boxes = new ArrayList<LayoutBox>(drawn);
            canvas.invalidate();
            refreshComplaints();
        }
    }

    /**
     * Writes the layout down.
     *
     * After every gesture rather than only on the way out. Leaving by the back button does call
     * onPause, but a process killed from outside does not — and the first emulator pass lost a
     * move exactly that way. A preferences write is cheap; minutes of fiddling are not.
     */
    private void save() {
        LayoutBook book = Settings.layouts(this);
        LayoutPreset preset = book.get(index);
        Settings.setLayouts(this, book.replace(index,
                landscape ? preset.withLandscape(boxes) : preset.withPortrait(boxes)));
    }

    /** What the engine thinks of the arrangement, in the user's words, while they are still here. */
    private void refreshComplaints() {
        BoxPlan plan = BoxPlan.of(boxes, screenW(), screenH(), Settings.options(this),
                new SimpleMetrics());
        StringBuilder out = new StringBuilder();
        for (BoxPlan.Complaint said : plan.complaints()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(describe(said));
        }
        complaint.setText(out.length() == 0
                ? getString(R.string.layout_edit_no_complaints) : out.toString());
    }

    private String describe(BoxPlan.Complaint said) {
        String field = said.field == null ? "" : label(said.field);
        if (said.kind == BoxPlan.NOTHING_DRAWN) {
            return getString(R.string.layout_complaint_empty);
        }
        if (said.kind == BoxPlan.OFF_SCREEN) {
            return getString(R.string.layout_complaint_off_screen, field);
        }
        if (said.kind == BoxPlan.OVERLAP) {
            return getString(R.string.layout_complaint_overlap, field, label(said.other));
        }
        return getString(R.string.layout_complaint_shrunk, field,
                Math.round(said.ratio * 100f));
    }

    private String label(String field) {
        return LayoutFieldsActivity.fieldLabel(this, field);
    }

    // ---- the canvas ----------------------------------------------------------------------

    /** The phone's screen at whatever scale fits, with the boxes drawn as outlines on it. */
    private final class Canvas2D extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float scale = 1f;
        private float offsetX;
        private float offsetY;
        private int grabbed = Grab.NONE;
        private float lastX;
        private float lastY;
        private boolean moved;
        private long downAt;

        Canvas2D(Activity activity) {
            super(activity);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            scale = Math.min(w / (float) screenW(), h / (float) screenH());
            offsetX = (w - screenW() * scale) / 2f;
            offsetY = (h - screenH() * scale) / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF000000);
            c.drawRect(offsetX, offsetY, offsetX + screenW() * scale,
                    offsetY + screenH() * scale, paint);

            for (int i = 0; i < boxes.size(); i++) {
                LayoutBox box = boxes.get(i);
                float[] rect = rectOf(box);
                float left = offsetX + rect[0] * scale;
                float top = offsetY + rect[1] * scale;
                float right = left + rect[2] * scale;
                float bottom = top + rect[3] * scale;

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(i == selected ? dp(2) : dp(1));
                paint.setColor(!box.shown ? 0xFF555555 : i == selected ? ACCENT : 0xFF8A8A8A);
                c.drawRect(left, top, right, bottom, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(Math.max(dp(9), Math.min(dp(13), (bottom - top) * 0.5f)));
                paint.setColor(box.shown ? TEXT_WHITE : 0xFF666666);
                c.drawText(label(box.field), left + dp(3), bottom - dp(3), paint);

                if (i == selected && box.shown && !box.locked) {
                    paint.setColor(ACCENT);
                    float handle = dp(5);
                    c.drawRect(left - handle, top - handle, left + handle, top + handle, paint);
                    c.drawRect(right - handle, top - handle, right + handle, top + handle, paint);
                    c.drawRect(left - handle, bottom - handle, left + handle, bottom + handle,
                            paint);
                    c.drawRect(right - handle, bottom - handle, right + handle, bottom + handle,
                            paint);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = (event.getX() - offsetX) / scale;
            float y = (event.getY() - offsetY) / scale;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    take(x, y);
                    lastX = x;
                    lastY = y;
                    moved = false;
                    downAt = System.currentTimeMillis();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (selected < 0 || grabbed == Grab.NONE) {
                        return true;
                    }
                    float dx = x - lastX;
                    float dy = y - lastY;
                    if (Math.abs(dx) * scale > dp(2) || Math.abs(dy) * scale > dp(2)) {
                        moved = true;
                    }
                    drag(dx, dy);
                    lastX = x;
                    lastY = y;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // A press that stayed still and lasted is the way to the numbers. Held here
                    // rather than with setOnLongClickListener because the same touch may also be
                    // the start of a drag, and the long press must lose to the drag, not race it.
                    if (!moved && selected >= 0
                            && System.currentTimeMillis() - downAt >= 500L) {
                        askNumbers(selected);
                    } else if (moved) {
                        refreshComplaints();
                    }
                    grabbed = Grab.NONE;
                    invalidate();
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        /** What is under the finger: the box that is on top wins, as in every drawing program. */
        private void take(float x, float y) {
            float reach = dp(14) / scale;
            for (int i = boxes.size() - 1; i >= 0; i--) {
                int what = Grab.at(rectOf(boxes.get(i)), x, y, reach);
                if (what != Grab.NONE) {
                    selected = i;
                    grabbed = boxes.get(i).locked ? Grab.NONE : what;
                    return;
                }
            }
            selected = -1;
            grabbed = Grab.NONE;
        }

        private void drag(float dx, float dy) {
            LayoutBox box = boxes.get(selected);
            float[] was = rectOf(box);
            float[] now = Grab.apply(was, grabbed, dx, dy, screenW(), screenH(), dp(8));
            remember();
            boxes.set(selected, write(box, now));
            save();
        }
    }

    // ---- boxes and rectangles --------------------------------------------------------------

    /** Where a box is on the phone's screen, at the size the editor treats as its own. */
    private float[] rectOf(LayoutBox box) {
        float width = box.widthOn(screenW(), screenW() * 0.4f);
        float height = box.heightOn(screenH(), screenH() * BoxPlan.DEFAULT_HEIGHT_SHARE);
        return box.rectOn(screenW(), screenH(), width, height);
    }

    /**
     * The box that sits at this rectangle, keeping the anchor it already had.
     *
     * The anchor is the user's statement about what the box is measured from — the middle, a corner,
     * an edge — and a drag is not a change of mind about that. So the offset is worked out for the
     * anchor the box has rather than the box being re-anchored to wherever it landed.
     */
    private LayoutBox write(LayoutBox box, float[] rect) {
        float x = Anchor.offsetX(box.anchor, rect[0], rect[2], screenW()) / screenW();
        float y = Anchor.offsetY(box.anchor, rect[1], rect[3], screenH()) / screenH();
        return box.at(box.anchor, x, y).sized(rect[2] / screenW(), rect[3] / screenH());
    }

    private void remember() {
        // One step is one gesture, not one pixel: the list is only added to when a drag begins.
        if (undo.size() > 20) {
            undo.remove(0);
        }
        if (undo.isEmpty() || undo.get(undo.size() - 1) != boxes) {
            undo.add(new ArrayList<LayoutBox>(boxes));
        }
    }

    /** The numbers, for a fingertip that is not precise enough — place and size, in per cent. */
    private void askNumbers(final int which) {
        final LayoutBox box = boxes.get(which);
        final float[] rect = rectOf(box);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        final EditText left = number(form, R.string.layout_number_left, rect[0] / screenW());
        final EditText top = number(form, R.string.layout_number_top, rect[1] / screenH());
        final EditText width = number(form, R.string.layout_number_width, rect[2] / screenW());
        final EditText height = number(form, R.string.layout_number_height, rect[3] / screenH());

        new AlertDialog.Builder(this)
                .setTitle(label(box.field))
                .setView(form)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int ignored) {
                        float[] wanted = {
                            read(left, rect[0] / screenW()) * screenW(),
                            read(top, rect[1] / screenH()) * screenH(),
                            read(width, rect[2] / screenW()) * screenW(),
                            read(height, rect[3] / screenH()) * screenH(),
                        };
                        remember();
                        // Through the same door a drag goes through, so a typed number cannot
                        // reach a place a finger could not: nothing off the screen, nothing
                        // inside out.
                        float[] safe = Grab.apply(
                                new float[] {wanted[0], wanted[1],
                                    Math.max(dp(8), wanted[2]), Math.max(dp(8), wanted[3])},
                                Grab.INSIDE, 0f, 0f, screenW(), screenH(), dp(8));
                        boxes.set(which, write(box, safe));
                        save();
                        canvas.invalidate();
                        refreshComplaints();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private EditText number(LinearLayout form, int label, float fraction) {
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(TEXT_DIM);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        form.addView(caption);

        EditText field = new EditText(this);
        field.setText(Integer.toString(Math.round(fraction * 100f)));
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        // No colour is set on purpose. A dialog is the platform's window, not this app's dark one,
        // and its edit boxes are light: the app's white would be white on white — which is exactly
        // what the first emulator pass showed, four fields that looked empty and were not.
        form.addView(field);
        return field;
    }

    /** Per cent, because a phone is not a drawing board and nobody knows their screen in pixels. */
    private float read(EditText field, float fallback) {
        try {
            return Integer.parseInt(field.getText().toString().trim()) / 100f;
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    // ---- the furniture -------------------------------------------------------------------

    /** A measurer for the complaint line. The editor draws outlines, not the clock's own glyphs. */
    private final class SimpleMetrics implements ClockLayout.Metrics {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public float width(String role, String text, float textSize) {
            paint.setTextSize(textSize);
            return paint.measureText(text);
        }
    }

    private int screenW() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return landscape ? longEdge() : shortEdge();
    }

    private int screenH() {
        return landscape ? shortEdge() : longEdge();
    }

    private int shortEdge() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.min(metrics.widthPixels, metrics.heightPixels);
    }

    private int longEdge() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(metrics.widthPixels, metrics.heightPixels);
    }

    private View button(String label, final Runnable onPress) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(ACCENT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(12), dp(10), dp(12));
        view.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPress.run();
            }
        });
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
