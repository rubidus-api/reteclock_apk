package com.reteclock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.layout.Anchor;
import com.reteclock.core.layout.LayoutBook;
import com.reteclock.core.layout.LayoutBox;
import com.reteclock.core.layout.LayoutPreset;

import java.util.ArrayList;
import java.util.List;

/**
 * The fields of one layout, as a list: what is shown, where its writing sits, what is locked
 * (RFC-0005, phase 4).
 *
 * The canvas is for gestures — move a box, resize it, type its numbers. These are choices, not
 * gestures: whether a field is on the layout at all, whether its writing hugs the left of its box or
 * the middle, whether it may be dragged by accident. A list is where choices belong, and it is also
 * the only place a *hidden* box can be found again — an invisible thing cannot be tapped.
 *
 * The font is not repeated here. Fonts and decorations are already a per-field screen of their own
 * and they apply wherever that field is drawn, in any layout; a second place to set them would be
 * two answers to one question, which is how issue #44 happened.
 */
public final class LayoutFieldsActivity extends Activity {

    public static final String EXTRA_INDEX = LayoutEditorActivity.EXTRA_INDEX;
    public static final String EXTRA_LANDSCAPE = LayoutEditorActivity.EXTRA_LANDSCAPE;

    private static final int BACKDROP = 0xFF101010;
    private static final int CARD = 0xFF1C1C1C;
    private static final int ROW = 0xFF242424;
    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF4DB6AC;

    private int index;
    private boolean landscape;
    private LinearLayout list;
    /**
     * The page, kept so a rebuild can put the scroll back where it was.
     *
     * Every change here rebuilds the list — it is the only way to be sure the screen says what is
     * stored — and a rebuild that jumps to the top loses the reader's place. Pressing *Centre* on
     * the fourth field and being thrown to the first is the sort of thing that makes a screen feel
     * unreliable even when it is doing exactly what it was told.
     */
    private ScrollView page;
    /**
     * Which fields the align buttons act on.
     *
     * Kept for as long as the screen is open and no longer: a selection is a sentence being spoken,
     * not a setting. Saving it would mean a user who came back tomorrow found three boxes already
     * ticked and no memory of why.
     */
    private final java.util.Set<Integer> selected = new java.util.HashSet<Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        index = getIntent() == null ? 1 : getIntent().getIntExtra(EXTRA_INDEX, 1);
        landscape = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LANDSCAPE, false);

        // A list is not a form: the first number box on it must not summon the keyboard and hide
        // half the screen on the way in. The window opens with the keyboard down, and the page
        // itself takes the focus so nothing else claims it.
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        ScrollView scroll = new ScrollView(this);
        page = scroll;
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setBackgroundColor(BACKDROP);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.layout_fields_title);
        title.setTextColor(ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        root.addView(addBar());
        root.addView(alignBar());

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        TextView note = new TextView(this);
        note.setText(R.string.layout_fields_note);
        note.setTextColor(TEXT_DIM);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        note.setPadding(0, dp(10), 0, 0);
        root.addView(note);

        rebuild();
        setContentView(scroll);
        scroll.requestFocus();
    }

    private List<LayoutBox> boxes() {
        return Settings.layouts(this).get(landscape, index).boxes();
    }

    private void write(List<LayoutBox> boxes) {
        LayoutBook book = Settings.layouts(this);
        Settings.setLayouts(this, book.replace(landscape, index,
                book.get(landscape, index).with(boxes)));
    }

    /** Rebuilds the list, and puts the scroll back where the reader had it. */
    private void rebuildKeepingPlace() {
        final int wasAt = page == null ? 0 : page.getScrollY();
        rebuild();
        if (page != null) {
            page.requestFocus();
            page.post(new Runnable() {
                @Override
                public void run() {
                    page.scrollTo(0, wasAt);
                }
            });
        }
    }

    /**
     * One card per field: its name, then the ticks, then the numbers, then where its writing sits.
     *
     * Dense on purpose — a layout has a dozen fields and a phone screen holds six lines — but the
     * order never changes and neither does the height of a card, so the list does not shuffle under
     * the finger as things are switched.
     */
    private void rebuild() {
        list.removeAllViews();
        final List<LayoutBox> current = new ArrayList<LayoutBox>(boxes());
        for (int i = 0; i < current.size(); i++) {
            final int which = i;
            final LayoutBox box = current.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable face = new GradientDrawable();
            face.setColor(CARD);
            face.setCornerRadius(dp(8));
            card.setBackgroundDrawable(face);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            card.setLayoutParams(params);

            // The name, and the one destructive thing, at opposite ends of the same line: a title
            // has a line to itself but does not need the whole of it.
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setText(fieldLabel(this, box.field));
            name.setTextColor(ACCENT);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(name);

            TextView remove = new TextView(this);
            remove.setText(R.string.layout_field_remove);
            remove.setTextColor(0xFFE57373);
            remove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            remove.setPadding(dp(10), dp(6), dp(2), dp(6));
            remove.setClickable(true);
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    remove(which);
                }
            });
            header.addView(remove);
            card.addView(header);

            View rule = new View(this);
            rule.setBackgroundColor(0xFF3A3A3A);
            LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            ruleParams.bottomMargin = dp(4);
            rule.setLayoutParams(ruleParams);
            card.addView(rule);

            // The three ticks on one line: chosen for the align buttons, drawn at all, and pinned.
            LinearLayout ticks = new LinearLayout(this);
            ticks.setOrientation(LinearLayout.HORIZONTAL);
            ticks.addView(tick(R.string.layout_field_pick,
                    selected.contains(Integer.valueOf(which)),
                    new Switch() {
                        @Override
                        public void set(boolean on) {
                            if (on) {
                                selected.add(Integer.valueOf(which));
                            } else {
                                selected.remove(Integer.valueOf(which));
                            }
                            refreshAlignBar();
                        }
                    }));
            ticks.addView(tick(R.string.layout_field_shown, box.shown, new Switch() {
                @Override
                public void set(boolean on) {
                    List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                    out.set(which, out.get(which).shown(on));
                    write(out);
                }
            }));
            ticks.addView(tick(R.string.layout_field_locked, box.locked, new Switch() {
                @Override
                public void set(boolean on) {
                    List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                    out.set(which, out.get(which).locked(on));
                    write(out);
                }
            }));
            card.addView(ticks);

            if (isStrip(box.field)) {
                // A strip's question is which edge, not where its writing sits: alignment inside a
                // band the width of the screen says nothing, and its place is the edge itself.
                card.addView(subheading(getString(R.string.layout_field_edge)));
                card.addView(edgeRow(which));
                list.addView(card);
                continue;
            }

            card.addView(numbers(which));
            card.addView(alignRows(which));
            list.addView(card);
        }
    }

    /** What a tick does when it is pressed. */
    private interface Switch {
        void set(boolean on);
    }

    /** One checkbox of the three on a card's second line. */
    private View tick(int label, boolean on, final Switch what) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(TEXT_WHITE);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        box.setPadding(dp(2), dp(2), 0, dp(2));
        box.setChecked(on);
        box.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                what.set(checked);
            }
        });
        return box;
    }

    /** Where the writing sits inside the box: across on one line, down on the next. */
    private View alignRows(int which) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(subheading(getString(R.string.layout_field_align_h)));
        block.addView(alignRow(which, true));
        block.addView(alignRow(which, false));
        return block;
    }

    // ---- putting a field back ---------------------------------------------------------------

    /**
     * Every field this app can draw, in the order they are offered.
     *
     * A layout that never had a box for the seconds has no way to gain one from the canvas — there
     * is nothing there to drag. So the list, which is where a layout's contents are decided, is
     * where a field is put back.
     */
    private static final String[] EVERY_FIELD = {
        ClockLayout.ROLE_HOUR_MINUTE,
        ClockLayout.ROLE_HOUR,
        ClockLayout.ROLE_MINUTE,
        ClockLayout.ROLE_MERIDIEM,
        ClockLayout.ROLE_SECOND,
        ClockLayout.ROLE_WEEKDAY,
        ClockLayout.ROLE_MONTH_DAY,
        ClockLayout.ROLE_YEAR,
        ClockLayout.ROLE_WEEKDAY_DATE,
        ClockLayout.ROLE_SMALL_LINE,
        com.reteclock.core.layout.Builtin.FIELD_CALENDAR,
        ClockLayout.ROLE_QUOTE,
        com.reteclock.core.layout.BoxPlan.FIELD_TIMER,
    };

    private View addBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(8));
        TextView add = new TextView(this);
        add.setText(R.string.layout_field_add);
        add.setTextColor(ACCENT);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(10), dp(12), dp(10), dp(12));
        GradientDrawable face = new GradientDrawable();
        face.setColor(ROW);
        face.setCornerRadius(dp(6));
        add.setBackgroundDrawable(face);
        add.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        add.setClickable(true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askWhichField();
            }
        });
        row.addView(add);
        return row;
    }

    /** The fields this layout has no box for, offered by name. */
    private void askWhichField() {
        final List<String> missing = new ArrayList<String>();
        List<LayoutBox> current = boxes();
        for (int i = 0; i < EVERY_FIELD.length; i++) {
            boolean already = false;
            for (int j = 0; j < current.size(); j++) {
                if (current.get(j).field.equals(EVERY_FIELD[i])) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                missing.add(EVERY_FIELD[i]);
            }
        }
        if (missing.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.layout_field_all_there,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] names = new CharSequence[missing.size()];
        for (int i = 0; i < missing.size(); i++) {
            names[i] = fieldLabel(this, missing.get(i));
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.layout_field_add)
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                        // In the middle, at the size a new box starts at. It is somewhere the user
                        // can certainly see it and drag it from, which is the whole requirement:
                        // a box added off in a corner would look like nothing having happened.
                        String field = missing.get(which);
                        LayoutBox added = LayoutBox.of(field)
                                .at(Anchor.MIDDLE_CENTRE, 0f, 0f)
                                .sized(0.6f,
                                        com.reteclock.core.layout.BoxPlan.DEFAULT_HEIGHT_SHARE);
                        if (isStrip(field)) {
                            // A strip arrives as one, along the foot: added as an ordinary box in
                            // the middle it would sit on top of the clock and look like a mistake.
                            added = added.at(Anchor.BOTTOM_CENTRE, 0f, 0f)
                                    .sized(1f, 0.12f)
                                    .onEdge(com.reteclock.core.layout.Strips.BOTTOM);
                        }
                        out.add(added);
                        write(out);
                        rebuild();
                    }
                })
                .show();
    }

    /** Taking a field off this layout. The box goes; the field itself is always addable again. */
    private void remove(final int which) {
        final LayoutBox box = boxes().get(which);
        new android.app.AlertDialog.Builder(this)
                .setMessage(getString(R.string.layout_field_remove_ask,
                        fieldLabel(this, box.field)))
                .setPositiveButton(R.string.layout_field_remove,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int i) {
                                List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                                out.remove(which);
                                write(out);
                                selected.clear();
                                rebuild();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- aligning a selection --------------------------------------------------------------

    private LinearLayout alignCard;
    private TextView alignNote;

    /**
     * The align and distribute buttons, acting on whatever is ticked (T072).
     *
     * On this screen rather than on the canvas because aligning is a statement about *several*
     * boxes, and choosing several things on a canvas means a rubber band, a modifier key, or a
     * long press that already means something else. A list has checkboxes.
     */
    private View alignCard() {
        alignCard = new LinearLayout(this);
        alignCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable face = new GradientDrawable();
        face.setColor(CARD);
        face.setCornerRadius(dp(8));
        alignCard.setBackgroundDrawable(face);
        alignCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        alignCard.setLayoutParams(params);
        return alignCard;
    }

    private View alignBar() {
        View card = alignCard();
        alignCard.addView(subheading(getString(R.string.layout_align_title)));

        LinearLayout edges = new LinearLayout(this);
        edges.setOrientation(LinearLayout.HORIZONTAL);
        edges.addView(action(R.string.layout_align_left, Op.LEFT));
        edges.addView(action(R.string.layout_align_centre, Op.CENTRE_X));
        edges.addView(action(R.string.layout_align_right, Op.RIGHT));
        alignCard.addView(edges);

        LinearLayout sides = new LinearLayout(this);
        sides.setOrientation(LinearLayout.HORIZONTAL);
        sides.addView(action(R.string.layout_align_top, Op.TOP));
        sides.addView(action(R.string.layout_align_middle, Op.MIDDLE_Y));
        sides.addView(action(R.string.layout_align_bottom, Op.BOTTOM));
        alignCard.addView(sides);

        LinearLayout more = new LinearLayout(this);
        more.setOrientation(LinearLayout.HORIZONTAL);
        more.addView(action(R.string.layout_spread_x, Op.SPREAD_X));
        more.addView(action(R.string.layout_spread_y, Op.SPREAD_Y));
        more.addView(action(R.string.layout_same_size, Op.SAME_SIZE));
        alignCard.addView(more);

        alignNote = new TextView(this);
        alignNote.setTextColor(TEXT_DIM);
        alignNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        alignNote.setPadding(0, dp(6), 0, 0);
        alignCard.addView(alignNote);
        refreshAlignBar();
        return card;
    }

    /** Which operation a button asks for. */
    private static final class Op {
        static final int LEFT = 0;
        static final int CENTRE_X = 1;
        static final int RIGHT = 2;
        static final int TOP = 3;
        static final int MIDDLE_Y = 4;
        static final int BOTTOM = 5;
        static final int SPREAD_X = 6;
        static final int SPREAD_Y = 7;
        static final int SAME_SIZE = 8;
    }

    private View action(int label, final int op) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(ACCENT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), dp(10), dp(6), dp(10));
        GradientDrawable face = new GradientDrawable();
        face.setColor(ROW);
        face.setCornerRadius(dp(6));
        button.setBackgroundDrawable(face);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = dp(4);
        params.bottomMargin = dp(4);
        button.setLayoutParams(params);
        button.setClickable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply(op);
            }
        });
        return button;
    }

    /** How many are ticked, and what that lets the buttons do. */
    private void refreshAlignBar() {
        if (alignNote == null) {
            return;
        }
        int count = selected.size();
        alignNote.setText(count < 2
                ? getString(R.string.layout_align_pick)
                : getString(R.string.layout_align_count, count));
    }

    /**
     * Runs one operation over the ticked boxes.
     *
     * The rectangles are worked out on the phone's own screen, moved by the pure functions in
     * {@link com.reteclock.core.layout.Align}, and written back with each box keeping the anchor it
     * had. Fewer than two ticked does nothing, which is what a drawing program does too.
     */
    private void apply(int op) {
        if (selected.size() < 2) {
            return;
        }
        List<LayoutBox> all = new ArrayList<LayoutBox>(boxes());
        List<Integer> which = new ArrayList<Integer>(selected);
        java.util.Collections.sort(which);

        float[][] rects = new float[which.size()][];
        for (int i = 0; i < which.size(); i++) {
            rects[i] = rectOf(all.get(which.get(i).intValue()));
        }

        float[][] moved;
        if (op == Op.LEFT) {
            moved = com.reteclock.core.layout.Align.left(rects);
        } else if (op == Op.CENTRE_X) {
            moved = com.reteclock.core.layout.Align.centreX(rects);
        } else if (op == Op.RIGHT) {
            moved = com.reteclock.core.layout.Align.right(rects);
        } else if (op == Op.TOP) {
            moved = com.reteclock.core.layout.Align.top(rects);
        } else if (op == Op.MIDDLE_Y) {
            moved = com.reteclock.core.layout.Align.middleY(rects);
        } else if (op == Op.BOTTOM) {
            moved = com.reteclock.core.layout.Align.bottom(rects);
        } else if (op == Op.SPREAD_X) {
            moved = com.reteclock.core.layout.Align.distributeX(rects);
        } else if (op == Op.SPREAD_Y) {
            moved = com.reteclock.core.layout.Align.distributeY(rects);
        } else {
            moved = com.reteclock.core.layout.Align.sameSize(rects);
        }

        for (int i = 0; i < which.size(); i++) {
            int at = which.get(i).intValue();
            all.set(at, all.get(at).placedAt(moved[i], screenW(), screenH()));
        }
        write(all);
        list.post(new Runnable() {
            @Override
            public void run() {
                rebuildKeepingPlace();
            }
        });
    }

    /**
     * Where a box actually ends up on the phone's screen — the engine's answer, not a guess.
     *
     * A box may leave its width to the field ("as wide as the widest thing it can ever show"), and
     * a strip is placed by the edge it takes rather than by its own numbers. Working the rectangle
     * out here from the box alone would print a number that is not what is drawn, which is worse
     * than printing nothing. So the plan is asked, exactly as the clock asks it.
     */
    private float[] rectOf(LayoutBox box) {
        com.reteclock.core.layout.BoxPlan plan = com.reteclock.core.layout.BoxPlan.of(
                boxes(), screenW(), screenH(), Settings.options(this), measurer());
        for (com.reteclock.core.layout.BoxPlan.Placed placed : plan.placed()) {
            if (placed.field.equals(box.field)) {
                return placed.rect;
            }
        }
        // Hidden boxes are not placed at all, and still have to show their numbers: they are what
        // the box will be when it comes back.
        float width = box.widthOn(screenW(), screenW() * 0.4f);
        float height = box.heightOn(screenH(),
                screenH() * com.reteclock.core.layout.BoxPlan.DEFAULT_HEIGHT_SHARE);
        return box.rectOn(screenW(), screenH(), width, height);
    }

    /** How glyphs are measured for the plan: this screen draws no clock, so a bare paint will do. */
    private ClockLayout.Metrics measurer() {
        final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        return new ClockLayout.Metrics() {
            @Override
            public float width(String role, String text, float textSize) {
                paint.setTextSize(textSize);
                return paint.measureText(text);
            }
        };
    }

    private int screenW() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return landscape ? Math.max(metrics.widthPixels, metrics.heightPixels)
                : Math.min(metrics.widthPixels, metrics.heightPixels);
    }

    private int screenH() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return landscape ? Math.min(metrics.widthPixels, metrics.heightPixels)
                : Math.max(metrics.widthPixels, metrics.heightPixels);
    }

    /**
     * Three buttons: left, centre, right — or top, middle, bottom.
     *
     * Where the writing sits *inside* its own box, which is a different question from where the box
     * sits on the screen. The two are kept apart in the engine for the same reason they are kept
     * apart here: conflating them is the usual way this feature goes wrong.
     */
    private View alignRow(final int which, final boolean horizontal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LayoutBox box = boxes().get(which);
        int now = horizontal ? Anchor.horizontal(box.align) : Anchor.vertical(box.align);
        int[] labels = horizontal
                ? new int[] {R.string.layout_align_left, R.string.layout_align_centre,
                    R.string.layout_align_right}
                : new int[] {R.string.layout_align_top, R.string.layout_align_middle,
                    R.string.layout_align_bottom};
        for (int i = 0; i < 3; i++) {
            final int wanted = i;
            TextView button = new TextView(this);
            button.setText(labels[i]);
            button.setTextColor(i == now ? ACCENT : TEXT_DIM);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(8), dp(10), dp(8), dp(10));
            GradientDrawable face = new GradientDrawable();
            face.setColor(i == now ? 0x334DB6AC : ROW);
            face.setCornerRadius(dp(6));
            button.setBackgroundDrawable(face);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.rightMargin = dp(4);
            button.setLayoutParams(params);
            button.setClickable(true);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                    LayoutBox box = out.get(which);
                    int align = horizontal
                            ? Anchor.of(wanted, Anchor.vertical(box.align))
                            : Anchor.of(Anchor.horizontal(box.align), wanted);
                    out.set(which, box.aligned(align));
                    write(out);
                    // Posted for the reason issue #43 taught: the rebuild takes this very button
                    // off the screen while the click that caused it is still being delivered.
                    list.post(new Runnable() {
                        @Override
                        public void run() {
                            rebuild();
                        }
                    });
                }
            });
            row.addView(button);
        }
        return row;
    }

    /**
     * Four small boxes — left, top, width, height — in per cent of the screen.
     *
     * Per cent because nobody knows their screen in pixels, and because a layout is stored that way:
     * what is typed here is what is kept. Everything typed goes through the same door a drag goes
     * through, so a number cannot put a box somewhere a finger could not.
     */
    private View numbers(final int which) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        float[] rect = rectOf(boxes().get(which));
        final EditText left = number(row, R.string.layout_number_left_short, rect[0] / screenW());
        final EditText top = number(row, R.string.layout_number_top_short, rect[1] / screenH());
        final EditText width = number(row, R.string.layout_number_width_short, rect[2] / screenW());
        final EditText height = number(row, R.string.layout_number_height_short,
                rect[3] / screenH());

        row.addView(button(getString(R.string.layout_number_set), new Runnable() {
            @Override
            public void run() {
                float[] was = rectOf(boxes().get(which));
                float[] wanted = {
                    read(left, was[0] / screenW()) * screenW(),
                    read(top, was[1] / screenH()) * screenH(),
                    Math.max(dp(8), read(width, was[2] / screenW()) * screenW()),
                    Math.max(dp(8), read(height, was[3] / screenH()) * screenH()),
                };
                float[] safe = com.reteclock.core.layout.Grab.apply(wanted,
                        com.reteclock.core.layout.Grab.INSIDE, 0f, 0f,
                        screenW(), screenH(), dp(8));
                List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                out.set(which, out.get(which).placedAt(safe, screenW(), screenH()));
                write(out);
                list.post(new Runnable() {
                    @Override
                    public void run() {
                        rebuildKeepingPlace();
                    }
                });
            }
        }));
        return row;
    }

    /** One labelled number box, narrow enough that four and a button share a line. */
    private EditText number(LinearLayout row, int label, float fraction) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(TEXT_DIM);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        cell.addView(caption);

        EditText field = new EditText(this);
        field.setText(LayoutBox.spell(fraction));
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        // No colour. An EditText draws itself on the platform's own light box, and this app's white
        // on that is white on white — the numbers were there all along and could not be read. The
        // same mistake as the editor's dialog, made twice in two days.
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        field.setPadding(dp(4), dp(4), dp(4), dp(4));
        cell.addView(field);
        row.addView(cell);
        return field;
    }

    private float read(EditText field, float fallback) {
        return LayoutBox.readPerCent(field.getText().toString(), fallback);
    }

    /** A small pressable label. */
    private View button(String label, final Runnable onPress) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(ACCENT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(14), dp(8), dp(8));
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPress.run();
            }
        });
        return view;
    }

    /** Whether this field is one of the two that take a whole edge (D9). */
    private static boolean isStrip(String field) {
        return com.reteclock.core.layout.BoxPlan.FIELD_TIMER.equals(field)
                || ClockLayout.ROLE_QUOTE.equals(field);
    }

    /**
     * Four buttons and an off: which edge this strip takes.
     *
     * "Not a strip" is offered because the saying can also be an ordinary box — that is what the
     * app's own arrangement makes it, a line low on the screen with padding round it — and somebody
     * who wants it that way should not have to delete it and start again.
     */
    private View edgeRow(final int which) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LayoutBox box = boxes().get(which);
        int now = box.edge;
        // The saying is offered the top and the bottom and nothing else: a sentence down the side
        // of a screen is a column two words wide. The timer may go anywhere — it is a bar and a few
        // numbers, and it reads perfectly well turned on its side.
        boolean sentence = ClockLayout.ROLE_QUOTE.equals(box.field);
        int[] labels = sentence
                ? new int[] {R.string.layout_edge_none, R.string.layout_align_top,
                    R.string.layout_align_bottom}
                : new int[] {R.string.layout_edge_none, R.string.layout_align_top,
                    R.string.layout_align_bottom, R.string.layout_align_left,
                    R.string.layout_align_right};
        int[] edges = sentence
                ? new int[] {com.reteclock.core.layout.Strips.NONE,
                    com.reteclock.core.layout.Strips.TOP,
                    com.reteclock.core.layout.Strips.BOTTOM}
                : new int[] {com.reteclock.core.layout.Strips.NONE,
                    com.reteclock.core.layout.Strips.TOP,
                    com.reteclock.core.layout.Strips.BOTTOM,
                    com.reteclock.core.layout.Strips.LEFT,
                    com.reteclock.core.layout.Strips.RIGHT};
        for (int i = 0; i < edges.length; i++) {
            final int wanted = edges[i];
            TextView button = new TextView(this);
            button.setText(labels[i]);
            button.setTextColor(edges[i] == now ? ACCENT : TEXT_DIM);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(4), dp(10), dp(4), dp(10));
            GradientDrawable face = new GradientDrawable();
            face.setColor(edges[i] == now ? 0x334DB6AC : ROW);
            face.setCornerRadius(dp(6));
            button.setBackgroundDrawable(face);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.rightMargin = dp(3);
            button.setLayoutParams(params);
            button.setClickable(true);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                    out.set(which, out.get(which).onEdge(wanted));
                    write(out);
                    list.post(new Runnable() {
                        @Override
                        public void run() {
                            rebuild();
                        }
                    });
                }
            });
            row.addView(button);
        }
        return row;
    }

    /** What a field is called on screen. Shared with the editor, so both say the same words. */
    static String fieldLabel(View view, String field) {
        return fieldLabel(view.getContext(), field);
    }

    static String fieldLabel(Activity activity, String field) {
        return fieldLabel((android.content.Context) activity, field);
    }

    static String fieldLabel(android.content.Context activity, String field) {
        if (field == null) {
            return "";
        }
        if (ClockLayout.ROLE_HOUR.equals(field)) {
            return activity.getString(R.string.layout_field_hour);
        }
        if (ClockLayout.ROLE_MINUTE.equals(field)) {
            return activity.getString(R.string.layout_field_minute);
        }
        if (ClockLayout.ROLE_HOUR_MINUTE.equals(field)) {
            return activity.getString(R.string.layout_field_time);
        }
        if (ClockLayout.ROLE_SECOND.equals(field)) {
            return activity.getString(R.string.date_field_second);
        }
        if (ClockLayout.ROLE_MERIDIEM.equals(field)) {
            return activity.getString(R.string.layout_field_marker);
        }
        if (ClockLayout.ROLE_WEEKDAY.equals(field)) {
            return activity.getString(R.string.date_field_weekday);
        }
        if (ClockLayout.ROLE_MONTH_DAY.equals(field)) {
            return activity.getString(R.string.date_field_month_day);
        }
        if (ClockLayout.ROLE_YEAR.equals(field)) {
            return activity.getString(R.string.date_field_year);
        }
        if (ClockLayout.ROLE_WEEKDAY_DATE.equals(field)) {
            return activity.getString(R.string.layout_field_date);
        }
        if (ClockLayout.ROLE_SMALL_LINE.equals(field)) {
            return activity.getString(R.string.layout_field_small_line);
        }
        if (com.reteclock.core.layout.Builtin.FIELD_CALENDAR.equals(field)) {
            return activity.getString(R.string.layout_field_calendar);
        }
        if (ClockLayout.ROLE_QUOTE.equals(field)) {
            return activity.getString(R.string.layout_field_saying);
        }
        if (com.reteclock.core.layout.BoxPlan.FIELD_TIMER.equals(field)) {
            return activity.getString(R.string.layout_field_timer);
        }
        return field;
    }

    private TextView subheading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setPadding(0, dp(8), 0, dp(2));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
