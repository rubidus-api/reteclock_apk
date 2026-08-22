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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.Calendars;
import com.reteclock.core.CalendarDate;
import com.reteclock.core.CivilTime;
import com.reteclock.core.CustomNames;
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
    private LinearLayout customSummer;
    private LinearLayout ownNames;

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
        // Whether the month grid is drawn at all comes first: it is the switch that decides
        // whether half of this screen matters, and it was sitting below the calendar system it
        // governs, which reads as a detail of that choice rather than as the question above it.
        addCalendarSwitch(root);
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

    /** The one switch this screen is about: whether the month grid is under the time at all. */
    private void addCalendarSwitch(LinearLayout root) {
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
    }

    private void addCalendarGrid(LinearLayout root) {
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

        ownNames = card();
        root.addView(ownNames);
        rebuildOwnNames();
    }

    // ---- the user's own names ------------------------------------------------------------

    /**
     * The months and the weekdays, renameable, for the calendar in force.
     *
     * The names that ship are three or four Latin characters because that is what fits every grid
     * on every screen this app runs on. That is a default, not a rule somebody else's language has
     * to live under, so nothing here is checked: any length, any script. The note says what it
     * costs, and the clock draws whatever it is given.
     */
    private void rebuildOwnNames() {
        if (ownNames == null) {
            return;
        }
        ownNames.removeAllViews();
        final int system = Settings.calendarSystem(this);
        ownNames.addView(subheading(getString(R.string.names_own,
                Calendars.name(system))));

        // Side by side, each button only as wide as the name on it. Thirteen months and seven
        // weekdays as full-width rows is twenty rows of screen spent on twenty short words, and the
        // set is far easier to read as a set: this is a calendar's names, and they belong together.
        CustomNames names = Settings.customNames(this, system);
        String[] months = Calendars.monthNames(system, Settings.calendarNameStyle(this, system));
        final int howMany = mostMonths(system);
        ownNames.addView(subheading(getString(R.string.names_months)));
        FlowLayout monthRow = new FlowLayout(this, dp(4), dp(2));
        for (int month = 1; month <= howMany; month++) {
            String built = month <= months.length ? months[month - 1] : Integer.toString(month);
            monthRow.addView(nameRow(built, names.monthEntry(month), true, month, howMany));
        }
        ownNames.addView(monthRow);

        String[] weekdays = Calendars.weekdayNames(system,
                Settings.calendarWeekdayStyle(this, system));
        ownNames.addView(subheading(getString(R.string.names_weekdays)));
        FlowLayout weekdayRow = new FlowLayout(this, dp(4), dp(2));
        for (int day = 0; day < weekdays.length; day++) {
            weekdayRow.addView(nameRow(weekdays[day], names.weekdayEntry(day), false, day,
                    howMany));
        }
        ownNames.addView(weekdayRow);

        Button clear = new Button(this);
        clear.setText(R.string.names_clear);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Settings.setCustomNames(TimeDateSettingsActivity.this, system, CustomNames.NONE);
                rebuildOwnNames();
            }
        });
        ownNames.addView(clear);
        ownNames.addView(note(getString(R.string.names_own_note)));
    }

    /**
     * How many months to offer.
     *
     * A Hebrew or lunisolar year has twelve months or thirteen depending on the year, so asking
     * about this year alone would hide the leap month for most of a decade. Nineteen years is a
     * whole Metonic cycle: whatever the calendar can do, it does inside one.
     */
    private static int mostMonths(int system) {
        int[] today = com.reteclock.core.Gregorian.parts(
                CivilTime.jdnOf(System.currentTimeMillis(), 0));
        CalendarDate here = Calendars.dateOf(system,
                com.reteclock.core.Gregorian.toJdn(today[0], today[1], today[2]));
        int most = 12;
        for (int year = here.year; year < here.year + 19; year++) {
            int count = Calendars.monthsInYear(system, year);
            if (count > most) {
                most = count;
            }
        }
        return most;
    }

    /**
     * One renameable name, as wide as the word on it.
     *
     * The button shows the name in force — the user's own where there is one, the built-in one
     * otherwise — rather than both with an arrow between them. In a row of thirteen, position says
     * which month this is, and the pair "Jan → 1월" is twice as wide as either half of it.
     */
    private View nameRow(final String built, String typed, final boolean isMonth, final int index,
            final int howMany) {
        Button row = new Button(this);
        row.setText(typed.length() == 0 ? built : typed);
        row.setMinWidth(0);
        row.setMinimumWidth(0);
        row.setPadding(dp(10), dp(4), dp(10), dp(4));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askForName(built, isMonth, index, howMany);
            }
        });
        return row;
    }

    private void askForName(final String built, final boolean isMonth, final int index,
            final int howMany) {
        final int system = Settings.calendarSystem(this);
        CustomNames names = Settings.customNames(this, system);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(isMonth ? names.monthEntry(index) : names.weekdayEntry(index));
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(built)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CustomNames now = Settings.customNames(
                                TimeDateSettingsActivity.this, system);
                        String typed = input.getText().toString().trim();
                        Settings.setCustomNames(TimeDateSettingsActivity.this, system,
                                isMonth ? now.withMonth(index, typed, howMany)
                                        : now.withWeekday(index, typed));
                        rebuildOwnNames();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                    Settings.setCustomSummerNumbers(TimeDateSettingsActivity.this,
                            Settings.customSummerNumbers(TimeDateSettingsActivity.this));
                }
                rebuildCustomSummer();
                refresh();
            }
        });
        summer.addView(rules);
        customSummer = new LinearLayout(this);
        customSummer.setOrientation(LinearLayout.VERTICAL);
        summer.addView(customSummer);
        rebuildCustomSummer();
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


    // ---- the user's own summer time ------------------------------------------------------

    /** 1..4 for the n-th such weekday, or {@link SummerTime#LAST}; the value is the index. */
    private static final int[] ORDINALS = {SummerTime.LAST, 1, 2, 3, 4};
    private static final int[] AMOUNTS = {30, 60, 120};

    /**
     * The dates behind *My own dates*, shown only when that is the rule chosen.
     *
     * Rebuilt rather than patched, for the reason the timer presets are: eight buttons whose labels
     * must agree with what is stored, and rebuilding cannot leave one of them lying.
     */
    private void rebuildCustomSummer() {
        if (customSummer == null) {
            return;
        }
        customSummer.removeAllViews();
        if (Settings.summerTimePreset(this) != SummerTime.PRESET_CUSTOM) {
            customSummer.setVisibility(View.GONE);
            return;
        }
        customSummer.setVisibility(View.VISIBLE);
        int[] n = Settings.customSummerNumbers(this);
        customSummer.addView(subheading(getString(R.string.timedate_summer_starts)));
        customSummer.addView(transitionRow(n, 0));
        customSummer.addView(subheading(getString(R.string.timedate_summer_ends)));
        customSummer.addView(transitionRow(n, 4));

        customSummer.addView(subheading(getString(R.string.timedate_summer_shift)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] amounts = new String[AMOUNTS.length];
        int chosen = 1;
        for (int i = 0; i < AMOUNTS.length; i++) {
            amounts[i] = amountLabel(AMOUNTS[i]);
            if (AMOUNTS[i] == n[8]) {
                chosen = i;
            }
        }
        row.addView(picker(amounts[chosen], getString(R.string.timedate_summer_shift), amounts,
                n, 8, AMOUNTS));
        customSummer.addView(row);
        customSummer.addView(note(getString(R.string.timedate_summer_custom_note)));
    }

    /** Month, which one, weekday and time — the four numbers that place one transition. */
    private LinearLayout transitionRow(int[] n, int base) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        String[] months = Calendars.monthNames(Calendars.GREGORIAN, 0);
        int[] monthValues = new int[12];
        for (int i = 0; i < 12; i++) {
            monthValues[i] = i + 1;
        }
        row.addView(picker(months[n[base] - 1], getString(R.string.timedate_summer_month),
                months, n, base, monthValues));

        String[] ordinals = {
            getString(R.string.timedate_summer_last), getString(R.string.timedate_summer_first),
            getString(R.string.timedate_summer_second), getString(R.string.timedate_summer_third),
            getString(R.string.timedate_summer_fourth)};
        int ordinalIndex = 0;
        for (int i = 0; i < ORDINALS.length; i++) {
            if (ORDINALS[i] == n[base + 2]) {
                ordinalIndex = i;
            }
        }
        row.addView(picker(ordinals[ordinalIndex], getString(R.string.timedate_summer_which),
                ordinals, n, base + 2, ORDINALS));

        String[] weekdays = Calendars.weekdayNames(Calendars.GREGORIAN, 0);
        int[] weekdayValues = new int[weekdays.length];
        for (int i = 0; i < weekdays.length; i++) {
            weekdayValues[i] = i;
        }
        row.addView(picker(weekdays[n[base + 1]], getString(R.string.timedate_summer_weekday),
                weekdays, n, base + 1, weekdayValues));

        String[] hours = new String[24];
        int[] hourValues = new int[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = two(i) + ":00";
            hourValues[i] = i * 60;
        }
        row.addView(picker(two(n[base + 3] / 60) + ":" + two(n[base + 3] % 60),
                getString(R.string.timedate_summer_at), hours, n, base + 3, hourValues));
        return row;
    }

    /** A button showing one of the nine numbers, which opens the list of what it may be. */
    private Button picker(String label, final String title, final String[] items, final int[] n,
            final int slot, final int[] values) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(TimeDateSettingsActivity.this)
                        .setTitle(title)
                        .setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                n[slot] = values[which];
                                Settings.setCustomSummerNumbers(
                                        TimeDateSettingsActivity.this, n);
                                rebuildCustomSummer();
                                refresh();
                            }
                        })
                        .show();
            }
        });
        return button;
    }

    private String amountLabel(int minutes) {
        if (minutes % 60 == 0) {
            return getString(minutes == 60 ? R.string.timedate_summer_hour
                    : R.string.timedate_summer_hours, minutes / 60);
        }
        return getString(R.string.timedate_summer_minutes, minutes);
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
