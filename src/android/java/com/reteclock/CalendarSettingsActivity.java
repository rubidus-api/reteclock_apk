package com.reteclock;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.MonthGrid;

/**
 * The calendar's own settings: whether it is shown, which day the week starts on, and how the
 * month at its head is written.
 *
 * A screen of its own rather than three more rows on the main settings, because the calendar is a
 * thing you either use or do not, and somebody who does not should not have to scroll past it.
 */
public final class CalendarSettingsActivity extends Activity {

    private static final int BACKDROP = 0xFF101010;
    private static final int CARD = 0xFF1C1C1C;
    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF4DB6AC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKDROP);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);

        root.addView(heading(getString(R.string.calendar_title)));

        LinearLayout card = card();
        final CheckBox on = new CheckBox(this);
        on.setText(R.string.calendar_show);
        on.setTextColor(TEXT_WHITE);
        on.setChecked(Settings.calendarOn(this));
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setCalendarOn(CalendarSettingsActivity.this, checked);
            }
        });
        card.addView(on);
        card.addView(note(getString(R.string.calendar_show_note)));
        root.addView(card);

        LinearLayout week = card();
        week.addView(subheading(getString(R.string.calendar_week_start)));
        final RadioGroup weekChoice = new RadioGroup(this);
        weekChoice.addView(radio(R.string.calendar_week_sunday, 0));
        weekChoice.addView(radio(R.string.calendar_week_monday, 1));
        weekChoice.check(Settings.calendarWeekStartsMonday(this) ? 1 : 0);
        weekChoice.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Settings.setCalendarWeekStartsMonday(CalendarSettingsActivity.this, checkedId == 1);
            }
        });
        week.addView(weekChoice);
        root.addView(week);

        LinearLayout header = card();
        header.addView(subheading(getString(R.string.calendar_header)));
        final RadioGroup headerChoice = new RadioGroup(this);
        headerChoice.addView(radio(R.string.calendar_header_name, MonthGrid.HEADER_NAME + 10));
        headerChoice.addView(radio(R.string.calendar_header_numbers, MonthGrid.HEADER_NUMBERS + 10));
        headerChoice.check(Settings.calendarHeaderStyle(this) + 10);
        headerChoice.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Settings.setCalendarHeaderStyle(CalendarSettingsActivity.this, checkedId - 10);
            }
        });
        header.addView(headerChoice);
        root.addView(header);

        root.addView(note(getString(R.string.calendar_paging_note)));

        setContentView(scroll);
    }

    private RadioButton radio(int label, int id) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        button.setTextColor(TEXT_WHITE);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(CARD);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ACCENT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private TextView subheading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setPadding(0, 0, 0, dp(2));
        return view;
    }

    private TextView note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setGravity(Gravity.LEFT);
        view.setPadding(0, dp(4), 0, 0);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
