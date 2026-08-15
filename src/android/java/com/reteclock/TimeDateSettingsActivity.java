package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.Calendars;
import com.reteclock.core.CivilTime;
import com.reteclock.core.ClockText;
import com.reteclock.core.MonthGrid;
import com.reteclock.core.Places;
import com.reteclock.core.SummerTime;

/**
 * Time and date: which calendar the clock counts in, and where its local time comes from.
 *
 * One screen for both, because they are one question asked twice — RFC-0003 and RFC-0004 ship
 * together and between them add a calendar system, a week start, a header style, a badge, a Hijri
 * offset, a UTC offset and a summer-time rule. The general settings screen was already long.
 *
 * The part that earns its place is at the foot: what the phone says, and what this clock will show.
 * The way to get this feature wrong is to set an offset *and* leave the phone's zone wrong, and no
 * amount of explaining prevents that. Showing both numbers does.
 */
public final class TimeDateSettingsActivity extends Activity {

    private static final int BACKDROP = 0xFF101010;
    private static final int CARD = 0xFF1C1C1C;
    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF4DB6AC;

    private TextView offsetLine;
    private TextView reconciliation;
    private LinearLayout nameStyles;
    private LinearLayout weekdayStyles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKDROP);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);

        root.addView(heading(getString(R.string.timedate_title)));
        addCalendarSystem(root);
        addCalendarGrid(root);
        addTimeSource(root);

        setContentView(scroll);
    }

    // ---- the calendar --------------------------------------------------------------------

    private void addCalendarSystem(LinearLayout root) {
        LinearLayout card = card();
        card.addView(subheading(getString(R.string.timedate_count_in)));
        final RadioGroup group = new RadioGroup(this);
        // Alphabetically. The stored numbers cannot be rearranged, so the order is asked for.
        int[] order = Calendars.byName();
        for (int i = 0; i < order.length; i++) {
            group.addView(radio(Calendars.name(order[i]), 100 + order[i]));
        }
        group.check(100 + Settings.calendarSystem(this));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                Settings.setCalendarSystem(TimeDateSettingsActivity.this, checkedId - 100);
                refreshNameStyles();
            }
        });
        card.addView(group);
        card.addView(note(getString(R.string.timedate_count_in_note)));
        root.addView(card);

        nameStyles = card();
        root.addView(nameStyles);
        weekdayStyles = card();
        root.addView(weekdayStyles);
        refreshNameStyles();

        LinearLayout badge = card();
        final CheckBox show = new CheckBox(this);
        show.setText(R.string.timedate_badge);
        show.setTextColor(TEXT_WHITE);
        show.setChecked(Settings.gregorianBadge(this));
        show.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setGregorianBadge(TimeDateSettingsActivity.this, checked);
            }
        });
        badge.addView(show);
        badge.addView(note(getString(R.string.timedate_badge_note)));
        root.addView(badge);

        LinearLayout hijri = card();
        hijri.addView(subheading(getString(R.string.timedate_hijri_offset)));
        final RadioGroup offsets = new RadioGroup(this);
        offsets.setOrientation(RadioGroup.HORIZONTAL);
        for (int days = -2; days <= 2; days++) {
            offsets.addView(radio(days > 0 ? "+" + days : Integer.toString(days), 200 + days));
        }
        offsets.check(200 + Settings.hijriOffset(this));
        offsets.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                Settings.setHijriOffset(TimeDateSettingsActivity.this, checkedId - 200);
            }
        });
        hijri.addView(offsets);
        hijri.addView(note(getString(R.string.timedate_hijri_note)));
        root.addView(hijri);
    }

    /**
     * How the months are spelled, where a calendar is read by people who spell them differently.
     *
     * The choices are labelled with the abbreviations themselves rather than with the name of a
     * tradition — `Rab1 · Jum1 · Shaw · DhuQ` beside `RbAw · JmAw · Syaw · Zulk`. A label naming a
     * country or a rite would be the app taking a side in something that is not its business; the
     * strings are what the user is actually choosing between, and they say it better anyway.
     */
    private void refreshNameStyles() {
        if (nameStyles == null) {
            return;
        }
        nameStyles.removeAllViews();
        final int system = Settings.calendarSystem(this);
        int count = Calendars.styleCount(system);
        if (count < 2) {
            nameStyles.setVisibility(View.GONE);
            refreshWeekdayStyles();
            return;
        }
        nameStyles.setVisibility(View.VISIBLE);
        nameStyles.addView(subheading(getString(R.string.timedate_month_names)));
        RadioGroup group = new RadioGroup(this);
        for (int style = 0; style < count; style++) {
            group.addView(radio(sampleOf(system, style), 700 + style));
        }
        group.check(700 + Settings.calendarNameStyle(this, system));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                Settings.setCalendarNameStyle(TimeDateSettingsActivity.this, system,
                        checkedId - 700);
            }
        });
        nameStyles.addView(group);
        nameStyles.addView(note(getString(R.string.timedate_month_names_note)));
        refreshWeekdayStyles();
    }

    /**
     * And the weekdays, English or the calendar's own.
     *
     * Same idea as the months, and the same labelling: the choice is shown as the days themselves.
     * Every calendar here rides the same seven-day week, so this is not a different week — it is
     * the same week said in another language, and it fits three characters in every one of them.
     */
    private void refreshWeekdayStyles() {
        if (weekdayStyles == null) {
            return;
        }
        weekdayStyles.removeAllViews();
        final int system = Settings.calendarSystem(this);
        if (Calendars.weekdayStyleCount(system) < 2) {
            weekdayStyles.setVisibility(View.GONE);
            return;
        }
        weekdayStyles.setVisibility(View.VISIBLE);
        weekdayStyles.addView(subheading(getString(R.string.timedate_weekday_names)));
        RadioGroup group = new RadioGroup(this);
        for (int style = 0; style < 2; style++) {
            group.addView(radio(join(Calendars.weekdayNames(system, style)), 800 + style));
        }
        group.check(800 + Settings.calendarWeekdayStyle(this, system));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                Settings.setCalendarWeekdayStyle(TimeDateSettingsActivity.this, system,
                        checkedId - 800);
            }
        });
        weekdayStyles.addView(group);
    }

    private static String join(String[] names) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(names[i]);
        }
        return out.toString();
    }

    /** A few of the months, as that style writes them — which is the whole of the choice. */
    private static String sampleOf(int system, int style) {
        String[] names = Calendars.monthNames(system, style);
        StringBuilder out = new StringBuilder();
        int[] pick = {0, 2, 6, names.length - 1};
        for (int i = 0; i < pick.length; i++) {
            if (i > 0) {
                out.append("  ");
            }
            out.append(names[pick[i]]);
        }
        return out.toString();
    }

    private void addCalendarGrid(LinearLayout root) {
        LinearLayout card = card();
        final CheckBox on = new CheckBox(this);
        on.setText(R.string.calendar_show);
        on.setTextColor(TEXT_WHITE);
        on.setChecked(Settings.calendarOn(this));
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setCalendarOn(TimeDateSettingsActivity.this, checked);
            }
        });
        card.addView(on);
        card.addView(note(getString(R.string.calendar_show_note)));
        root.addView(card);

        LinearLayout week = card();
        week.addView(subheading(getString(R.string.calendar_week_start)));
        final RadioGroup weekChoice = new RadioGroup(this);
        weekChoice.addView(radio(getString(R.string.calendar_week_sunday), 300));
        weekChoice.addView(radio(getString(R.string.calendar_week_monday), 301));
        weekChoice.addView(radio(getString(R.string.calendar_week_saturday), 306));
        weekChoice.check(300 + Settings.calendarWeekStart(this));
        weekChoice.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                Settings.setCalendarWeekStart(TimeDateSettingsActivity.this, checkedId - 300);
            }
        });
        week.addView(weekChoice);
        week.addView(note(getString(R.string.calendar_week_note)));
        root.addView(week);

        LinearLayout header = card();
        header.addView(subheading(getString(R.string.calendar_header)));
        final RadioGroup headerChoice = new RadioGroup(this);
        headerChoice.addView(radio(getString(R.string.calendar_header_name), 400));
        headerChoice.addView(radio(getString(R.string.calendar_header_numbers), 401));
        headerChoice.check(400 + Settings.calendarHeaderStyle(this));
        headerChoice.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                Settings.setCalendarHeaderStyle(TimeDateSettingsActivity.this, checkedId - 400);
            }
        });
        header.addView(headerChoice);
        header.addView(note(getString(R.string.calendar_paging_note)));
        root.addView(header);
    }

    // ---- the time base -------------------------------------------------------------------

    private void addTimeSource(LinearLayout root) {
        LinearLayout card = card();
        card.addView(subheading(getString(R.string.timedate_source)));
        final RadioGroup group = new RadioGroup(this);
        group.addView(radio(getString(R.string.timedate_source_phone),
                500 + Settings.TIME_SOURCE_PHONE));
        group.addView(radio(getString(R.string.timedate_source_manual),
                500 + Settings.TIME_SOURCE_MANUAL));
        group.check(500 + Settings.timeSource(this));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                Settings.setTimeSource(TimeDateSettingsActivity.this, checkedId - 500);
                refresh();
            }
        });
        card.addView(group);
        card.addView(note(getString(R.string.timedate_source_note)));
        root.addView(card);

        LinearLayout offset = card();
        offset.addView(subheading(getString(R.string.timedate_offset)));
        offsetLine = new TextView(this);
        offsetLine.setTextColor(TEXT_WHITE);
        offsetLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        offset.addView(offsetLine);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(stepButton("-15", -15));
        row.addView(stepButton("+15", 15));
        Button pick = new Button(this);
        pick.setText(R.string.timedate_pick_place);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPlaces();
            }
        });
        row.addView(pick);
        offset.addView(row);
        offset.addView(note(getString(R.string.timedate_offset_note)));
        root.addView(offset);

        LinearLayout summer = card();
        summer.addView(subheading(getString(R.string.timedate_summer)));
        final RadioGroup rules = new RadioGroup(this);
        rules.addView(radio(getString(R.string.timedate_summer_none),
                600 + SummerTime.PRESET_NONE));
        rules.addView(radio(getString(R.string.timedate_summer_europe),
                600 + SummerTime.PRESET_EUROPE));
        rules.addView(radio(getString(R.string.timedate_summer_north_america),
                600 + SummerTime.PRESET_NORTH_AMERICA));
        rules.addView(radio(getString(R.string.timedate_summer_southern),
                600 + SummerTime.PRESET_SOUTHERN));
        rules.addView(radio(getString(R.string.timedate_summer_custom),
                600 + SummerTime.PRESET_CUSTOM));
        rules.check(600 + Settings.summerTimePreset(this));
        rules.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                int preset = checkedId - 600;
                Settings.setSummerTimePreset(TimeDateSettingsActivity.this, preset);
                if (preset == SummerTime.PRESET_CUSTOM
                        && Settings.customSummerTime(TimeDateSettingsActivity.this) == null) {
                    // Somewhere to start from: the European dates, at an hour.
                    Settings.setCustomSummerTime(TimeDateSettingsActivity.this,
                            3, 0, SummerTime.LAST, 60, 10, 0, SummerTime.LAST, 60, 60);
                }
                refresh();
            }
        });
        summer.addView(rules);
        summer.addView(note(getString(R.string.timedate_summer_note)));
        root.addView(summer);

        LinearLayout check = card();
        check.addView(subheading(getString(R.string.timedate_check)));
        reconciliation = new TextView(this);
        reconciliation.setTextColor(TEXT_WHITE);
        reconciliation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        check.addView(reconciliation);
        check.addView(note(getString(R.string.timedate_check_note)));
        root.addView(check);

        refresh();
    }

    private Button stepButton(String label, final int minutes) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int now = Settings.utcOffsetMinutes(TimeDateSettingsActivity.this) + minutes;
                if (now < -720) {
                    now = -720;
                }
                if (now > 840) {
                    now = 840;
                }
                Settings.setUtcOffsetMinutes(TimeDateSettingsActivity.this, now);
                refresh();
            }
        });
        return button;
    }

    private void showPlaces() {
        final String[] labels = new String[Places.count()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Places.label(i);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.timedate_pick_place)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.setUtcOffsetMinutes(TimeDateSettingsActivity.this,
                                Places.OFFSETS[which]);
                        Settings.setSummerTimePreset(TimeDateSettingsActivity.this,
                                Places.SUGGESTED_RULES[which]);
                        refresh();
                    }
                })
                .show();
    }

    /** Both numbers, live: what the phone says, and what this clock will show. */
    private void refresh() {
        long now = System.currentTimeMillis();
        int stored = Settings.utcOffsetMinutes(this);
        if (offsetLine != null) {
            offsetLine.setText(Places.offsetText(stored));
        }
        if (reconciliation == null) {
            return;
        }
        int phone = Settings.phoneOffsetMinutes();
        int mine = Settings.offsetMinutes(this, now);
        CivilTime phoneTime = CivilTime.of(now, phone);
        CivilTime myTime = CivilTime.of(now, mine);
        reconciliation.setText(
                getString(R.string.timedate_check_phone) + "  " + clock(phoneTime)
                + "   (" + Places.offsetText(phone) + ")\n"
                + getString(R.string.timedate_check_clock) + "  " + clock(myTime)
                + "   (" + Places.offsetText(mine) + ")");
    }

    private static String clock(CivilTime time) {
        return two(time.hour) + ":" + two(time.minute);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    // ---- the furniture -------------------------------------------------------------------

    private RadioButton radio(String label, int id) {
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
