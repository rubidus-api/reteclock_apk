package com.reteclock;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.Bell;
import com.reteclock.core.Bells;
import com.reteclock.core.CivilTime;
import com.reteclock.core.WakeLog;
import com.reteclock.core.WakeSchedule;

import java.text.DateFormatSymbols;
import java.util.List;

/**
 * *Alarms (experimental)* — the page of its own that bells waking the phone live on (RFC-0012).
 *
 * <p>One switch at the top, and under it, before anything else, the warning, set apart in colour
 * and weight: this is an experiment, and what it cannot promise is said where the switch is pressed
 * rather than somewhere a person might never read. Everything else on the page — which bells wake
 * the phone, the next alarm, the phone's battery settings, the record — is shown only while the
 * switch is on.
 *
 * <p>The page adds to *Sounds and bells* and changes nothing there: a bell is made and edited on that
 * page, and only ticked here.
 */
public class AlarmSettingsActivity extends Activity {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    /** A light red: a warning on a black page, readable without relying on the colour alone. */
    private static final int WARNING = 0xFFFF8A80;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int DIVIDER = 0xFF272727;
    private static final int PRESSED = 0x334DB6AC;

    /** How many lines of the record the page shows. */
    private static final int RECORD_SHOWN = 12;

    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        body.setPadding(pad, dp(16), pad, dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(body);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The bells may have been edited on their own page in the meantime.
        rebuild();
    }

    private void rebuild() {
        body.removeAllViews();
        body.addView(title(getString(R.string.alarm_title)));
        boolean on = Settings.wakeOn(this);

        LinearLayout switchCard = card(getString(R.string.alarm_card_switch));
        CheckBox toggle = new CheckBox(this);
        toggle.setText(R.string.alarm_switch);
        toggle.setTextColor(TEXT_WHITE);
        toggle.setChecked(on);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                WakeBells.setSwitch(AlarmSettingsActivity.this, checked);
                rebuild();
            }
        });
        switchCard.addView(toggle);
        TextView warning = new TextView(this);
        warning.setText(R.string.alarm_warning);
        warning.setTextColor(WARNING);
        warning.setTypeface(Typeface.DEFAULT_BOLD);
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        warning.setPadding(0, dp(6), 0, dp(8));
        switchCard.addView(warning);
        switchCard.addView(footer(getString(on ? R.string.alarm_on_note : R.string.alarm_off_note)));
        body.addView(switchCard);

        if (!on) {
            return;
        }

        if (hasMissed()) {
            TextView missed = footer(getString(R.string.alarm_missed_notice));
            missed.setTextColor(WARNING);
            missed.setTypeface(Typeface.DEFAULT_BOLD);
            body.addView(missed, 1);
        }

        body.addView(bellsCard());
        body.addView(phoneCard());
        body.addView(recordCard());
    }

    // ---- the bells ----------------------------------------------------------------------------

    private LinearLayout bellsCard() {
        LinearLayout card = card(getString(R.string.alarm_card_bells));
        WakeSchedule.Next next = WakeBells.next(this);
        TextView nextLine = new TextView(this);
        nextLine.setText(next == null ? getString(R.string.alarm_next_none)
                : getString(R.string.alarm_next, when(next.epochMillis)));
        nextLine.setTextColor(TEXT_WHITE);
        nextLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        nextLine.setPadding(0, 0, 0, dp(6));
        card.addView(nextLine);
        if (!Settings.bellsOn(this)) {
            card.addView(footer(getString(R.string.alarm_bells_off)));
        }
        card.addView(divider());

        final Bells bells = Settings.bells(this);
        if (bells.size() == 0) {
            card.addView(footer(getString(R.string.alarm_no_bells)));
        }
        List<Bell> list = bells.list();
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final Bell bell = list.get(i);
            CheckBox row = new CheckBox(this);
            row.setText(describe(bell));
            row.setTextColor(bell.isLive() ? TEXT_WHITE : TEXT_DIM);
            row.setChecked(bell.wake);
            row.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    Bells now = Settings.bells(AlarmSettingsActivity.this);
                    if (index < now.size()) {
                        Settings.setBells(AlarmSettingsActivity.this,
                                now.replacing(index, now.list().get(index).withWake(checked)));
                    }
                    rebuild();
                }
            });
            card.addView(row);
        }
        card.addView(actionButton(getString(R.string.alarm_open_sounds), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AlarmSettingsActivity.this, SoundSettingsActivity.class));
            }
        }));
        return card;
    }

    private String describe(Bell bell) {
        StringBuilder text = new StringBuilder(String.format("%02d:%02d", bell.hour(), bell.minute()));
        String[] names = new DateFormatSymbols().getShortWeekdays();
        text.append("  ·  ");
        if (bell.days == Bell.EVERY_DAY) {
            text.append("every day");
        } else if (bell.days == Bell.NO_DAY) {
            text.append("no day");
        } else {
            boolean first = true;
            for (int day = 0; day < 7; day++) {
                if (bell.ringsOn(day)) {
                    text.append(first ? "" : " ").append(names[day + 1]);
                    first = false;
                }
            }
        }
        if (!bell.label.isEmpty()) {
            text.append("  ·  ").append(bell.label);
        }
        if (!bell.on) {
            text.append("  ·  off");
        }
        return text.toString();
    }

    // ---- the phone ----------------------------------------------------------------------------

    private LinearLayout phoneCard() {
        LinearLayout card = card(getString(R.string.alarm_card_phone));
        if (Build.VERSION.SDK_INT >= 23) {
            card.addView(actionButton(getString(R.string.alarm_battery), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        // Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, which needs no
                        // permission; the direct request dialog is not used, and needs one.
                        startActivity(new Intent(
                                "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
                    } catch (ActivityNotFoundException e) {
                        // A phone without the list has nothing to exempt from.
                    }
                }
            }));
            card.addView(footer(getString(R.string.alarm_battery_note)));
        }
        if (Build.VERSION.SDK_INT >= 33) {
            card.addView(footer(getString(R.string.alarm_notifications_note)));
        }
        if (Build.VERSION.SDK_INT >= 24) {
            card.addView(footer(getString(R.string.alarm_restart_note)));
        }
        return card;
    }

    // ---- the record ---------------------------------------------------------------------------

    private LinearLayout recordCard() {
        LinearLayout card = card(getString(R.string.alarm_card_record));
        List<WakeLog.Entry> entries = WakeBells.entries(this);
        if (entries.isEmpty()) {
            card.addView(footer(getString(R.string.alarm_record_none)));
            return card;
        }
        for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - RECORD_SHOWN); i--) {
            WakeLog.Entry entry = entries.get(i);
            TextView line = footer(when(entry.dueEpochMillis) + "  —  " + kind(entry));
            if (WakeLog.LATE.equals(entry.kind) || WakeLog.MISSED.equals(entry.kind)) {
                line.setTextColor(WARNING);
                line.setTypeface(Typeface.DEFAULT_BOLD);
            }
            card.addView(line);
        }
        card.addView(actionButton(getString(R.string.alarm_record_clear), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WakeBells.clearRecord(AlarmSettingsActivity.this);
                rebuild();
            }
        }));
        return card;
    }

    private boolean hasMissed() {
        List<WakeLog.Entry> entries = WakeBells.entries(this);
        long week = System.currentTimeMillis() - 7L * 24 * 60 * 60_000L;
        for (WakeLog.Entry entry : entries) {
            if ((WakeLog.LATE.equals(entry.kind) || WakeLog.MISSED.equals(entry.kind))
                    && entry.atEpochMillis > week) {
                return true;
            }
        }
        return false;
    }

    private String kind(WakeLog.Entry entry) {
        String late = lateness(entry.latenessMillis());
        if (WakeLog.RANG.equals(entry.kind)) {
            return getString(R.string.wake_kind_rang) + late;
        }
        if (WakeLog.STOPPED.equals(entry.kind)) {
            return getString(R.string.wake_kind_stopped);
        }
        if (WakeLog.PUT_OFF.equals(entry.kind)) {
            return getString(R.string.wake_kind_put_off);
        }
        if (WakeLog.UNANSWERED.equals(entry.kind)) {
            return getString(R.string.wake_kind_unanswered);
        }
        if (WakeLog.LATE.equals(entry.kind)) {
            return getString(R.string.wake_kind_late) + late;
        }
        if (WakeLog.MISSED.equals(entry.kind)) {
            return getString(R.string.wake_kind_missed);
        }
        if (WakeLog.LOCKED.equals(entry.kind)) {
            return getString(R.string.wake_kind_locked) + late;
        }
        return getString(R.string.wake_kind_dropped);
    }

    private String lateness(long millis) {
        if (millis < 1000L) {
            return "";
        }
        long seconds = millis / 1000L;
        return seconds < 120 ? ", " + getString(R.string.wake_late_seconds, (int) seconds)
                : ", " + getString(R.string.wake_late_minutes, (int) (seconds / 60));
    }

    /** An instant as the clock itself would say it: its own offset, weekday and 24-hour time. */
    private String when(long epochMillis) {
        CivilTime at = CivilTime.of(epochMillis, Settings.offsetMinutes(this, epochMillis));
        String day = new DateFormatSymbols().getShortWeekdays()[CivilTime.weekday(at.jdn) + 1];
        return String.format("%s %02d:%02d", day, at.hour, at.minute);
    }

    // ---- the look, as the other settings pages have it ------------------------------------------

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        view.setPadding(dp(4), 0, 0, dp(12));
        return view;
    }

    private LinearLayout card(String name) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable face = new GradientDrawable();
        face.setColor(CARD);
        face.setCornerRadius(dp(10));
        face.setStroke(1, CARD_STROKE);
        outer.setBackgroundDrawable(face);
        int pad = dp(12);
        outer.setPadding(pad, dp(10), pad, dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        outer.setLayoutParams(params);
        TextView heading = new TextView(this);
        heading.setText(name.toUpperCase());
        heading.setTextColor(ACCENT);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, 0, 0, dp(6));
        outer.addView(heading);
        return outer;
    }

    private TextView footer(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(DIVIDER);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return view;
    }

    private TextView actionButton(String label, View.OnClickListener onClick) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(ACCENT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, dp(10), 0, dp(10));
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(0x00000000);
        resting.setCornerRadius(dp(8));
        resting.setStroke(1, 0x664DB6AC);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(PRESSED);
        pressed.setCornerRadius(dp(8));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[] {}, resting);
        button.setBackgroundDrawable(states);
        Focusable.make(button);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
