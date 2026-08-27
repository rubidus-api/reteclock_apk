package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
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

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.DateOrder;
import com.reteclock.core.layout.LayoutBook;
import com.reteclock.core.layout.LayoutPreset;

/**
 * Where everything goes: which layout is in force, and the settings of the automatic one.
 *
 * RFC-0005, D7. The app's layout options were spread through the general settings — a switch for
 * the time alone, two sliders that move a boundary, a list that orders a line — and they are a
 * layout editor with a bad interface. Left beside a real one they would be a second place answering
 * the same question, and this app has been bitten by exactly that (issue #44: a switch that governed
 * one screen and not the rest).
 *
 * So there is one screen. At the top, which layout: **Automatic**, or one the user drew. Under it,
 * the settings of whichever is chosen. Nothing about what the automatic layout draws has changed —
 * it has stopped being "the layout" and become "the layout that decides for you".
 */
public final class LayoutSettingsActivity extends Activity {

    private static final int BACKDROP = 0xFF101010;
    private static final int CARD = 0xFF1C1C1C;
    private static final int ROW = 0xFF242424;
    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int DISABLED = 0xFF4A4A4A;

    private LinearLayout presetList;
    private LinearLayout automaticCard;
    private LinearLayout dateOrderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKDROP);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);

        root.addView(heading(getString(R.string.layout_title)));

        LinearLayout which = card();
        which.addView(subheading(getString(R.string.layout_which)));
        presetList = new LinearLayout(this);
        presetList.setOrientation(LinearLayout.VERTICAL);
        which.addView(presetList);
        which.addView(note(getString(R.string.layout_which_note)));
        which.addView(note(getString(R.string.layout_edit_note)));
        root.addView(which);

        automaticCard = card();
        root.addView(automaticCard);

        rebuildPresets();
        rebuildAutomatic();
        setContentView(scroll);
    }

    // ---- which layout ----------------------------------------------------------------------

    /**
     * Two lists, one for each way up, each entry with a picture of what it draws.
     *
     * The owner asked for the split, and it is how a person thinks about layouts: one drawn for an
     * upright phone says nothing about a sideways one. The pictures are there because a name does
     * not describe an arrangement — "Bedside" and "Bedside 2" are the same word twice.
     */
    private void rebuildPresets() {
        presetList.removeAllViews();
        final LayoutBook book = Settings.layouts(this);
        addShelf(false, book);
        addShelf(true, book);
    }

    private void addShelf(final boolean landscape, final LayoutBook book) {
        presetList.addView(subheading(getString(landscape
                ? R.string.layout_sideways_list : R.string.layout_upright_list)));

        for (int i = 0; i < book.size(landscape); i++) {
            final int index = i;
            final LayoutPreset preset = book.get(landscape, i);
            final boolean automatic = i == 0;

            LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable face = new GradientDrawable();
            face.setColor(ROW);
            face.setCornerRadius(dp(6));
            entry.setBackgroundDrawable(face);
            entry.setPadding(dp(8), dp(6), dp(8), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            entry.setLayoutParams(params);

            // The name has the line to itself. Sharing it with four buttons broke "Automatic" across
            // two lines on a 320-wide screen, which is the width this app is built for.
            RadioButton chosen = new RadioButton(this);
            chosen.setText(preset.name);
            chosen.setTextColor(TEXT_WHITE);
            chosen.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            chosen.setChecked(i == book.chosenIndex(landscape));
            chosen.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            chosen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.setLayouts(LayoutSettingsActivity.this,
                            book.choose(landscape, index));
                    refresh();
                }
            });
            entry.addView(chosen);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);

            // Automatic is the way back when a drawn layout goes wrong, so it cannot be edited,
            // renamed, copied or deleted. Its buttons keep their room and lose their use, because a
            // row whose controls come and go is a row that jumps about under the finger.
            top.addView(button(getString(R.string.layout_edit), !automatic, new Runnable() {
                @Override
                public void run() {
                    android.content.Intent intent = new android.content.Intent(
                            LayoutSettingsActivity.this, LayoutEditorActivity.class);
                    intent.putExtra(LayoutEditorActivity.EXTRA_INDEX, index);
                    intent.putExtra(LayoutEditorActivity.EXTRA_LANDSCAPE, landscape);
                    startActivity(intent);
                }
            }));
            top.addView(button(getString(R.string.layout_copy), !automatic, new Runnable() {
                @Override
                public void run() {
                    Settings.setLayouts(LayoutSettingsActivity.this,
                            book.duplicate(landscape, index));
                    refresh();
                }
            }));
            top.addView(button(getString(R.string.layout_delete), !automatic, new Runnable() {
                @Override
                public void run() {
                    confirmDelete(book, landscape, index);
                }
            }));
            entry.addView(top);

            LinearLayout more = new LinearLayout(this);
            more.setOrientation(LinearLayout.HORIZONTAL);
            more.addView(button(getString(R.string.layout_rename), !automatic, new Runnable() {
                @Override
                public void run() {
                    askName(book, landscape, index);
                }
            }));
            more.addView(button(getString(R.string.layout_copy_other_way), !automatic,
                    new Runnable() {
                        @Override
                        public void run() {
                            Settings.setLayouts(LayoutSettingsActivity.this,
                                    book.copyToOtherWay(landscape, index));
                            refresh();
                        }
                    }));
            entry.addView(more);

            LayoutPreview preview = new LayoutPreview(this, landscape,
                    preset.boxes(), Settings.options(this));
            LinearLayout.LayoutParams shot = new LinearLayout.LayoutParams(
                    landscape ? dp(230) : dp(96), LinearLayout.LayoutParams.WRAP_CONTENT);
            shot.topMargin = dp(6);
            preview.setLayoutParams(shot);
            entry.addView(preview);

            presetList.addView(entry);
        }
    }

    private void askName(final LayoutBook book, final boolean landscape, final int index) {
        final EditText field = new EditText(this);
        field.setText(book.get(landscape, index).name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.layout_rename)
                .setView(field)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.setLayouts(LayoutSettingsActivity.this,
                                book.rename(landscape, index, field.getText().toString()));
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Deleting a drawn layout throws away work, so it is asked about rather than done. */
    private void confirmDelete(final LayoutBook book, final boolean landscape, final int index) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.layout_delete_ask, book.get(landscape, index).name))
                .setPositiveButton(R.string.layout_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.setLayouts(LayoutSettingsActivity.this,
                                book.remove(landscape, index));
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refresh() {
        rebuildPresets();
        rebuildAutomatic();
    }

    // ---- the automatic layout's own settings ----------------------------------------------

    /**
     * The settings that steer the automatic arrangement, shown only while it is the one in force.
     *
     * They keep their stored keys and their behaviour exactly; only where they are asked has moved.
     */
    private void rebuildAutomatic() {
        automaticCard.removeAllViews();
        LayoutBook book = Settings.layouts(this);
        if (!book.chosen(false).isAutomatic() && !book.chosen(true).isAutomatic()) {
            automaticCard.addView(subheading(getString(R.string.layout_drawn)));
            automaticCard.addView(note(getString(R.string.layout_drawn_note)));
            return;
        }
        automaticCard.addView(subheading(getString(R.string.layout_automatic)));

        final CheckBox timeOnly = new CheckBox(this);
        timeOnly.setText(R.string.settings_time_only);
        timeOnly.setTextColor(TEXT_WHITE);
        timeOnly.setChecked(Settings.timeOnly(this));
        timeOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setTimeOnly(LayoutSettingsActivity.this, checked);
            }
        });
        automaticCard.addView(timeOnly);
        automaticCard.addView(note(getString(R.string.settings_time_only_note)));

        automaticCard.addView(subheading(getString(R.string.settings_ratio)));
        automaticCard.addView(ratioRow(R.string.settings_ratio_wide,
                Settings.KEY_TIME_PERCENT_WIDE));
        automaticCard.addView(ratioRow(R.string.settings_ratio_tall,
                Settings.KEY_TIME_PERCENT_TALL));
        automaticCard.addView(note(getString(R.string.settings_ratio_note)));

        automaticCard.addView(subheading(getString(R.string.settings_date_order)));
        dateOrderList = new LinearLayout(this);
        dateOrderList.setOrientation(LinearLayout.VERTICAL);
        automaticCard.addView(dateOrderList);
        rebuildDateOrder();
        automaticCard.addView(note(getString(R.string.settings_date_order_note)));
    }

    /** A labelled slider for one orientation's share, 20 to 90 per cent. */
    private View ratioRow(final int label, final String key) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(4), 0, 0);

        final int floor = Math.round(ClockOptions.MIN_TIME_FRACTION * 100f);
        final int ceiling = Math.round(ClockOptions.MAX_TIME_FRACTION * 100f);
        final TextView caption = note(getString(label, Settings.timePercent(this, key)));
        block.addView(caption);

        final android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(ceiling - floor);
        bar.setProgress(Settings.timePercent(this, key) - floor);
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress,
                    boolean fromUser) {
                int percent = floor + progress;
                caption.setText(getString(label, percent));
                if (fromUser) {
                    Settings.setTimePercent(LayoutSettingsActivity.this, key, percent);
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
            }
        });
        block.addView(bar);
        return block;
    }

    /** The order of the landscape date line: one row per item, moved with two arrows (issue #43). */
    private void rebuildDateOrder() {
        dateOrderList.removeAllViews();
        final java.util.List<String> fields = Settings.dateOrder(this).fields();
        for (int i = 0; i < fields.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable face = new GradientDrawable();
            face.setColor(ROW);
            face.setCornerRadius(dp(6));
            row.setBackgroundDrawable(face);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(4);
            row.setLayoutParams(params);

            TextView label = new TextView(this);
            label.setText(dateFieldLabel(fields.get(i)));
            label.setTextColor(TEXT_WHITE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            label.setPadding(dp(12), dp(12), dp(8), dp(12));
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            row.addView(moveButton(R.string.date_order_up, index > 0, index, index - 1));
            row.addView(moveButton(R.string.date_order_down, index + 1 < fields.size(),
                    index, index + 1));
            dateOrderList.addView(row);
        }
    }

    private View moveButton(int label, boolean enabled, final int from, final int to) {
        return button(getString(label), enabled, new Runnable() {
            @Override
            public void run() {
                Settings.setDateOrder(LayoutSettingsActivity.this,
                        Settings.dateOrder(LayoutSettingsActivity.this).move(from, to));
                // Posted, so the rebuild happens after the click has finished being delivered to
                // the very button it takes off the screen — issue #43, and its lesson.
                dateOrderList.post(new Runnable() {
                    @Override
                    public void run() {
                        rebuildDateOrder();
                    }
                });
            }
        });
    }

    private String dateFieldLabel(String role) {
        if (ClockLayout.ROLE_WEEKDAY.equals(role)) {
            return getString(R.string.date_field_weekday);
        }
        if (ClockLayout.ROLE_MONTH_DAY.equals(role)) {
            return getString(R.string.date_field_month_day);
        }
        if (ClockLayout.ROLE_YEAR.equals(role)) {
            return getString(R.string.date_field_year);
        }
        return getString(R.string.date_field_second);
    }

    // ---- the furniture -------------------------------------------------------------------

    /** A small pressable label. Disabled ones stay, dimmed, so no row changes width. */
    private View button(String label, boolean enabled, final Runnable onPress) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(enabled ? ACCENT : DISABLED);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        if (!enabled) {
            return view;
        }
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(Color.TRANSPARENT);
        resting.setCornerRadius(dp(6));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x334DB6AC);
        pressed.setCornerRadius(dp(6));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[] {}, resting);
        view.setBackgroundDrawable(states);
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPress.run();
            }
        });
        return view;
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
