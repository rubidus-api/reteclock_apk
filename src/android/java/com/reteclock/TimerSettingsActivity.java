package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.reteclock.core.TimerInterval;
import com.reteclock.core.TimerPreset;
import com.reteclock.core.TimeReadout;

/**
 * The timer's settings: whether it shows at all, how it makes itself heard, and the presets.
 *
 * A preset is a named list of intervals, so this screen is a tree — and this app has one flat
 * settings screen and no navigation to speak of. Rather than push a second screen for the
 * intervals, the chosen preset opens in place and its intervals appear beneath it. That keeps the
 * whole tree on one page, which is what the rest of the app does.
 *
 * Built in code, in the same idiom as the general settings (R27): rounded cards on black, one
 * accent, hairlines, compact controls that fit a 320 dp row.
 */
public class TimerSettingsActivity extends Activity {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int DIVIDER = 0xFF272727;
    private static final int PRESSED = 0x334DB6AC;
    private static final int BUTTON_FACE = 0xFF212121;

    /** The colours an interval can wear, the same ring the clock's palette offers. */
    private static final int[] PALETTE = {
        0xFFEF5350, 0xFFFF8A65, 0xFFFFB300, 0xFFFFF176, 0xFFAED581,
        0xFF66BB6A, 0xFF4DB6AC, 0xFF4DD0E1, 0xFF64B5F6, 0xFF7986CB,
        0xFF9575CD, 0xFFF06292, 0xFF8D6E63, 0xFFE0E0E0, 0xFF616161,
    };

    /** The preset whose intervals are open, or -1 when the list is closed. */
    private int openPreset = -1;
    private LinearLayout presetSection;
    private List<TimerPreset> presets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        presets = new ArrayList<TimerPreset>(Settings.timerPresets(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, dp(16), pad, dp(16));
        root.addView(title(getString(R.string.timer_title)));

        // ---- Whether, and how loudly ----
        LinearLayout using = card(getString(R.string.timer_card_using));
        final CheckBox on = new CheckBox(this);
        on.setText(R.string.timer_show);
        on.setTextColor(TEXT_WHITE);
        on.setChecked(Settings.timerOn(this));
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setTimerOn(TimerSettingsActivity.this, checked);
            }
        });
        using.addView(on);
        using.addView(footer(getString(R.string.timer_show_note)));

        using.addView(subheading(getString(R.string.timer_alert)));
        RadioGroup alert = new RadioGroup(this);
        alert.setOrientation(RadioGroup.VERTICAL);
        alert.addView(radio(1, R.string.timer_alert_sound));
        alert.addView(radio(2, R.string.timer_alert_vibrate));
        alert.addView(radio(3, R.string.timer_alert_silent));
        alert.check(Settings.timerAlert(this) + 1);
        alert.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Settings.setTimerAlert(TimerSettingsActivity.this, checkedId - 1);
            }
        });
        using.addView(alert);
        using.addView(footer(getString(R.string.timer_alert_note)));
        root.addView(using);

        // ---- The presets ----
        LinearLayout sets = card(getString(R.string.timer_card_presets));
        presetSection = new LinearLayout(this);
        presetSection.setOrientation(LinearLayout.VERTICAL);
        sets.addView(presetSection);
        sets.addView(actionButton(getString(R.string.timer_preset_add),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        List<TimerInterval> parts = new ArrayList<TimerInterval>();
                        parts.add(new TimerInterval(getString(R.string.timer_interval_new),
                                5 * 60_000L, TimerInterval.DEFAULT_COLOR,
                                TimerInterval.DEFAULT_END_COLOR, "", 0));
                        presets.add(new TimerPreset(getString(R.string.timer_preset_new), parts));
                        openPreset = presets.size() - 1;
                        save();
                    }
                }));
        sets.addView(footer(getString(R.string.timer_preset_note)));
        root.addView(sets);

        rebuildPresets();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(root);
        setContentView(scroll);
    }

    private RadioButton radio(int id, int label) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        button.setTextColor(TEXT_WHITE);
        return button;
    }

    /** Writes the presets back and redraws the list; every edit goes through here. */
    private void save() {
        Settings.setTimerPresets(this, presets);
        rebuildPresets();
    }

    /**
     * The whole list, rebuilt rather than patched: it is a handful of rows, and rebuilding cannot
     * leave a row disagreeing with what is stored.
     */
    private void rebuildPresets() {
        presetSection.removeAllViews();
        if (presets.isEmpty()) {
            presetSection.addView(footer(getString(R.string.timer_preset_none)));
            return;
        }
        for (int i = 0; i < presets.size(); i++) {
            if (i > 0) {
                presetSection.addView(divider());
            }
            presetSection.addView(presetRow(i));
            if (i == openPreset) {
                presetSection.addView(intervalList(i));
            }
        }
    }

    /** One preset: open it, rename it, move it, delete it. */
    private View presetRow(final int index) {
        final TimerPreset preset = presets.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText((index == openPreset ? "▾  " : "▸  ") + preset.name);
        name.setTextColor(index == openPreset ? ACCENT : TEXT_WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        name.setSingleLine(true);
        column.addView(name);

        TextView summary = new TextView(this);
        summary.setText(getString(R.string.timer_preset_summary,
                preset.intervals.size(), TimeReadout.of(preset.totalMs())));
        summary.setTextColor(TEXT_DIM);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        column.addView(summary);

        column.setClickable(true);
        column.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPreset = openPreset == index ? -1 : index;
                rebuildPresets();
            }
        });
        row.addView(column);

        row.addView(iconButton("✎", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askForText(getString(R.string.timer_preset_rename), preset.name, false,
                        new OnText() {
                            @Override
                            public void got(String text) {
                                presets.set(index, preset.withName(text));
                                save();
                            }
                        });
            }
        }));
        row.addView(iconButton("▲", index > 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                move(presets, index, -1);
                openPreset = index - 1;
                save();
            }
        }));
        row.addView(iconButton("▼", index < presets.size() - 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                move(presets, index, +1);
                openPreset = index + 1;
                save();
            }
        }));
        row.addView(iconButton("✕", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presets.remove(index);
                openPreset = -1;
                save();
            }
        }));
        return row;
    }

    /** The open preset's intervals, indented beneath it. */
    private View intervalList(final int presetIndex) {
        final TimerPreset preset = presets.get(presetIndex);
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(14), 0, 0, dp(6));

        for (int i = 0; i < preset.intervals.size(); i++) {
            block.addView(intervalRow(presetIndex, i));
        }
        block.addView(actionButton(getString(R.string.timer_interval_add),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        List<TimerInterval> parts =
                                new ArrayList<TimerInterval>(preset.intervals);
                        parts.add(new TimerInterval(getString(R.string.timer_interval_new),
                                5 * 60_000L, TimerInterval.DEFAULT_COLOR,
                                TimerInterval.DEFAULT_END_COLOR, "", 0));
                        presets.set(presetIndex, preset.withIntervals(parts));
                        save();
                    }
                }));
        return block;
    }

    /** One interval: everything about it is edited from this row. */
    private View intervalRow(final int presetIndex, final int index) {
        final TimerPreset preset = presets.get(presetIndex);
        final TimerInterval interval = preset.intervals.get(index);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(4), 0, dp(4));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(interval.name + "   " + TimeReadout.of(interval.lengthMs));
        name.setTextColor(TEXT_WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        name.setClickable(true);
        name.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askForText(getString(R.string.timer_interval_rename), interval.name, false,
                        new OnText() {
                            @Override
                            public void got(String text) {
                                replace(presetIndex, index, interval.withName(text));
                            }
                        });
            }
        });
        top.addView(name);

        top.addView(swatch(interval.color, new OnColor() {
            @Override
            public void got(int color) {
                replace(presetIndex, index, interval.withColors(color, interval.endColor));
            }
        }));
        top.addView(swatch(interval.endColor, new OnColor() {
            @Override
            public void got(int color) {
                replace(presetIndex, index, interval.withColors(interval.color, color));
            }
        }));
        top.addView(iconButton("▲", index > 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<TimerInterval> parts = new ArrayList<TimerInterval>(preset.intervals);
                move(parts, index, -1);
                presets.set(presetIndex, preset.withIntervals(parts));
                save();
            }
        }));
        top.addView(iconButton("▼", index < preset.intervals.size() - 1,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        List<TimerInterval> parts =
                                new ArrayList<TimerInterval>(preset.intervals);
                        move(parts, index, +1);
                        presets.set(presetIndex, preset.withIntervals(parts));
                        save();
                    }
                }));
        top.addView(iconButton("✕", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<TimerInterval> parts = new ArrayList<TimerInterval>(preset.intervals);
                parts.remove(index);
                presets.set(presetIndex, preset.withIntervals(parts));
                save();
            }
        }));
        block.addView(top);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(smallButton(getString(R.string.timer_interval_length),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        askForText(getString(R.string.timer_interval_length_ask),
                                String.valueOf(interval.lengthMs / 1000L), true, new OnText() {
                                    @Override
                                    public void got(String text) {
                                        replace(presetIndex, index,
                                                interval.withLength(seconds(text) * 1000L));
                                    }
                                });
                    }
                }));
        bottom.addView(smallButton(getString(R.string.timer_interval_message),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        askForText(getString(R.string.timer_interval_message_ask),
                                interval.message, false, new OnText() {
                                    @Override
                                    public void got(String text) {
                                        replace(presetIndex, index, interval.withMessage(text));
                                    }
                                });
                    }
                }));
        bottom.addView(smallButton(getString(R.string.timer_interval_prealarm,
                interval.preAlarmSeconds), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        askForText(getString(R.string.timer_interval_prealarm_ask),
                                String.valueOf(interval.preAlarmSeconds), true, new OnText() {
                                    @Override
                                    public void got(String text) {
                                        replace(presetIndex, index,
                                                interval.withPreAlarm((int) seconds(text)));
                                    }
                                });
                    }
                }));
        block.addView(bottom);

        if (!interval.message.isEmpty()) {
            TextView says = footer("“" + interval.message + "”");
            says.setPadding(0, 0, 0, dp(2));
            block.addView(says);
        }
        return block;
    }

    private void replace(int presetIndex, int index, TimerInterval interval) {
        TimerPreset preset = presets.get(presetIndex);
        List<TimerInterval> parts = new ArrayList<TimerInterval>(preset.intervals);
        parts.set(index, interval);
        presets.set(presetIndex, preset.withIntervals(parts));
        save();
    }

    private static long seconds(String text) {
        try {
            return Math.max(0L, Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static <T> void move(List<T> list, int from, int by) {
        int to = from + by;
        if (to < 0 || to >= list.size()) {
            return;
        }
        T moved = list.remove(from);
        list.add(to, moved);
    }

    // ---- the small pieces the rows are built from ----

    private interface OnText {
        void got(String text);
    }

    private interface OnColor {
        void got(int color);
    }

    private void askForText(String title, String current, boolean numeric, final OnText then) {
        final EditText input = new EditText(this);
        input.setText(current);
        input.setSingleLine(true);
        if (numeric) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        then.got(input.getText().toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** A colour square that opens the palette. */
    private View swatch(int color, final OnColor then) {
        final TextView view = new TextView(this);
        GradientDrawable face = new GradientDrawable();
        face.setColor(color);
        face.setCornerRadius(dp(4));
        face.setStroke(1, CARD_STROKE);
        view.setBackgroundDrawable(face);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(26), dp(26));
        params.leftMargin = dp(4);
        view.setLayoutParams(params);
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askForColor(then);
            }
        });
        return view;
    }

    private void askForColor(final OnColor then) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10);
        grid.setPadding(pad, pad, pad, pad);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.timer_interval_colour)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        LinearLayout row = null;
        for (int i = 0; i < PALETTE.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            final int color = PALETTE[i];
            TextView cell = new TextView(this);
            GradientDrawable face = new GradientDrawable();
            face.setColor(color);
            face.setCornerRadius(dp(4));
            cell.setBackgroundDrawable(face);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
            params.rightMargin = dp(6);
            params.bottomMargin = dp(6);
            cell.setLayoutParams(params);
            cell.setClickable(true);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    then.got(color);
                }
            });
            row.addView(cell);
        }
        dialog.show();
    }

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

    private TextView subheading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setPadding(0, dp(8), 0, dp(2));
        return view;
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

    private TextView iconButton(String glyph, boolean enabled, View.OnClickListener onClick) {
        TextView button = new TextView(this);
        button.setText(glyph);
        button.setTextColor(enabled ? TEXT_WHITE : 0xFF4A4A4A);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(BUTTON_FACE);
        resting.setCornerRadius(dp(6));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(PRESSED);
        pressed.setCornerRadius(dp(6));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[] {}, resting);
        button.setBackgroundDrawable(states);
        button.setClickable(enabled);
        button.setEnabled(enabled);
        if (enabled) {
            button.setOnClickListener(onClick);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    private TextView smallButton(String label, View.OnClickListener onClick) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(ACCENT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(0x00000000);
        resting.setCornerRadius(dp(6));
        resting.setStroke(1, 0x664DB6AC);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(PRESSED);
        pressed.setCornerRadius(dp(6));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[] {}, resting);
        button.setBackgroundDrawable(states);
        button.setClickable(true);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        params.topMargin = dp(2);
        button.setLayoutParams(params);
        return button;
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
        button.setClickable(true);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        params.bottomMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
