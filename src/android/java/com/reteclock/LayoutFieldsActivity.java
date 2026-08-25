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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        index = getIntent() == null ? 1 : getIntent().getIntExtra(EXTRA_INDEX, 1);
        landscape = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LANDSCAPE, false);

        ScrollView scroll = new ScrollView(this);
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
    }

    private List<LayoutBox> boxes() {
        LayoutPreset preset = Settings.layouts(this).get(index);
        return landscape ? preset.landscape() : preset.portrait();
    }

    private void write(List<LayoutBox> boxes) {
        LayoutBook book = Settings.layouts(this);
        LayoutPreset preset = book.get(index);
        Settings.setLayouts(this, book.replace(index,
                landscape ? preset.withLandscape(boxes) : preset.withPortrait(boxes)));
    }

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
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            card.setLayoutParams(params);

            TextView name = new TextView(this);
            name.setText(fieldLabel(this, box.field));
            name.setTextColor(TEXT_WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            card.addView(name);

            final CheckBox shown = new CheckBox(this);
            shown.setText(R.string.layout_field_shown);
            shown.setTextColor(TEXT_WHITE);
            shown.setChecked(box.shown);
            shown.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                    out.set(which, out.get(which).shown(checked));
                    write(out);
                }
            });
            card.addView(shown);

            final CheckBox locked = new CheckBox(this);
            locked.setText(R.string.layout_field_locked);
            locked.setTextColor(TEXT_WHITE);
            locked.setChecked(box.locked);
            locked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    List<LayoutBox> out = new ArrayList<LayoutBox>(boxes());
                    out.set(which, out.get(which).locked(checked));
                    write(out);
                }
            });
            card.addView(locked);

            card.addView(subheading(getString(R.string.layout_field_align_h)));
            card.addView(alignRow(which, true));
            card.addView(subheading(getString(R.string.layout_field_align_v)));
            card.addView(alignRow(which, false));

            list.addView(card);
        }
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

    /** What a field is called on screen. Shared with the editor, so both say the same words. */
    static String fieldLabel(Activity activity, String field) {
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
