package com.reteclock;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.reteclock.core.ClockOptions;
import com.reteclock.core.FontLibrary;
import com.reteclock.core.ImageFit;
import com.reteclock.core.SlideOrder;

/**
 * The settings screen, reached by long pressing the clock.
 *
 * The views are built in code rather than inflated from XML: the screen is small, and this keeps the
 * app free of layout resources and of any support library, down to API 9.
 */
public class SettingsActivity extends Activity {

    private static final int TEXT_WHITE = Color.WHITE;
    private static final int TEXT_DIM = Color.parseColor("#B0B0B0");

    /** Anything larger than this is a suspicious thing to call a font on an old phone. */
    private static final int MAX_FONT_BYTES = 32 * 1024 * 1024;

    private static final int REQUEST_PICK_FONT = 1;
    private static final int REQUEST_PICK_BACKGROUND = 2;
    private static final int REQUEST_PICK_FOREGROUND = 3;

    /** The orderings the spinner offers, the default first. */
    private static final int[] ORDER_MODES = {
        SlideOrder.NAME_ASC,
        SlideOrder.NAME_DESC,
        SlideOrder.DATE_ASC,
        SlideOrder.DATE_DESC,
        SlideOrder.CUSTOM,
    };
    private static final int[] ORDER_LABELS = {
        R.string.settings_order_name_asc,
        R.string.settings_order_name_desc,
        R.string.settings_order_date_asc,
        R.string.settings_order_date_desc,
        R.string.settings_order_custom,
    };

    /** The images ticked for a group action. Checked names only; pruned as files go. */
    private final java.util.Set<String> selectedBackgrounds = new java.util.HashSet<String>();

    /** How long a still slide can hold, in the order the spinner offers them. */
    private static final int[] SLIDE_SECONDS = {5, 10, 30, 60, 300};
    private static final int[] SLIDE_LABELS = {
        R.string.settings_slide_5s,
        R.string.settings_slide_10s,
        R.string.settings_slide_30s,
        R.string.settings_slide_1m,
        R.string.settings_slide_5m,
    };

    /** The fit modes, in the order the spinner offers them; the sensible default first. */
    private static final int[] FIT_MODES = {
        ImageFit.COVER,
        ImageFit.CONTAIN,
        ImageFit.STRETCH,
        ImageFit.FIT_WIDTH,
        ImageFit.FIT_HEIGHT,
        ImageFit.CENTER,
    };
    private static final int[] FIT_LABELS = {
        R.string.settings_fit_cover,
        R.string.settings_fit_contain,
        R.string.settings_fit_stretch,
        R.string.settings_fit_width,
        R.string.settings_fit_height,
        R.string.settings_fit_center,
    };

    /** In the order Settings.FONT_ROLES lists them. */
    private static final int[] FIELD_LABELS = {
        R.string.settings_field_hour,
        R.string.settings_field_minute,
        R.string.settings_field_second,
        R.string.settings_field_weekday,
        R.string.settings_field_month_day,
        R.string.settings_field_year,
    };

    /** Rebuilt in place whenever a font is added or deleted. */
    private LinearLayout fontSection;
    /** The per-field font choices, rebuilt with it because the choices are the stored fonts. */
    private LinearLayout fieldSection;
    /** Rebuilt in place whenever the background images change. */
    private LinearLayout backgroundSection;
    /** Rebuilt in place whenever the text-fill image changes. */
    private LinearLayout foregroundSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Getting here is proof the long-press hint has done its job.
        Settings.setHintSeen(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        root.addView(title(getString(R.string.settings_title)));

        final CheckBox seconds = new CheckBox(this);
        seconds.setText(R.string.settings_show_seconds);
        seconds.setTextColor(TEXT_WHITE);
        seconds.setChecked(Settings.showSeconds(this));
        seconds.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setShowSeconds(SettingsActivity.this, checked);
            }
        });
        root.addView(seconds);

        root.addView(heading(getString(R.string.settings_date_format)));

        final RadioGroup dateStyle = new RadioGroup(this);
        dateStyle.setOrientation(RadioGroup.VERTICAL);

        RadioButton byName = new RadioButton(this);
        byName.setId(1);
        byName.setText(R.string.settings_date_name);
        byName.setTextColor(TEXT_WHITE);

        RadioButton byNumber = new RadioButton(this);
        byNumber.setId(2);
        byNumber.setText(R.string.settings_date_numeric);
        byNumber.setTextColor(TEXT_WHITE);

        dateStyle.addView(byName);
        dateStyle.addView(byNumber);
        dateStyle.check(Settings.dateStyle(this) == ClockOptions.DATE_STYLE_NUMERIC ? 2 : 1);
        dateStyle.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Settings.setDateStyle(SettingsActivity.this, checkedId == 2
                        ? ClockOptions.DATE_STYLE_NUMERIC
                        : ClockOptions.DATE_STYLE_NAME);
            }
        });
        root.addView(dateStyle);

        root.addView(heading(getString(R.string.settings_font)));

        fontSection = new LinearLayout(this);
        fontSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(fontSection);

        root.addView(heading(getString(R.string.settings_font_per_field)));
        fieldSection = new LinearLayout(this);
        fieldSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(fieldSection);

        Button add = new Button(this);
        add.setText(R.string.settings_font_add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFont();
            }
        });
        root.addView(add);
        root.addView(footer(getString(R.string.settings_font_kinds)));

        root.addView(heading(getString(R.string.settings_ratio)));
        root.addView(ratioRow(R.string.settings_ratio_wide, Settings.KEY_TIME_PERCENT_WIDE));
        root.addView(ratioRow(R.string.settings_ratio_tall, Settings.KEY_TIME_PERCENT_TALL));
        root.addView(footer(getString(R.string.settings_ratio_note)));

        root.addView(heading(getString(R.string.settings_background)));
        backgroundSection = new LinearLayout(this);
        backgroundSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(backgroundSection);

        root.addView(heading(getString(R.string.settings_foreground)));
        foregroundSection = new LinearLayout(this);
        foregroundSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(foregroundSection);

        root.addView(heading(getString(R.string.settings_dock)));

        final CheckBox charging = new CheckBox(this);
        charging.setText(R.string.settings_start_when_charging);
        charging.setTextColor(TEXT_WHITE);
        charging.setChecked(Settings.startWhenCharging(this));
        charging.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setStartWhenCharging(SettingsActivity.this, checked);
            }
        });
        root.addView(charging);

        root.addView(spacer());
        root.addView(footer(getString(R.string.settings_about,
                versionName(), Build.VERSION.RELEASE)));

        rebuildFontSection();
        rebuildFieldSection();
        rebuildBackgroundSection();
        rebuildForegroundSection();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(root);
        setContentView(scroll);
    }


    /**
     * The system font, then one row per imported font, then what they occupy together.
     *
     * Rebuilt wholesale rather than patched: the list is a handful of rows, and rebuilding cannot
     * leave the radio buttons disagreeing with the setting.
     */
    private void rebuildFontSection() {
        fontSection.removeAllViews();

        FontLibrary library = Settings.fonts(this);
        List<FontLibrary.Entry> entries = library.list();
        for (FontLibrary.Entry entry : entries) {
            String label = entry.name + "  \u00b7  " + FontLibrary.humanBytes(entry.bytes);
            fontSection.addView(fontRow(entry.name, label));
        }

        TextView total = footer(entries.isEmpty()
                ? getString(R.string.settings_font_none)
                : getString(R.string.settings_font_total, entries.size(),
                        FontLibrary.humanBytes(library.totalBytes())));
        total.setPadding(0, dp(6), 0, 0);
        fontSection.addView(total);
    }

    /** One row: the font's name and size, and a button that deletes it. */
    private View fontRow(final String name, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(TEXT_WHITE);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text);

        Button delete = new Button(this);
        delete.setText(R.string.settings_font_delete);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.fonts(SettingsActivity.this).delete(name);
                // A field still naming this font falls back to the system face on its own, because
                // the lookup checks the file is there. Nothing else to clean up.
                rebuildFontSection();
                rebuildFieldSection();
            }
        });
        row.addView(delete);
        return row;
    }

    /** One spinner per field, each offering the system font and every stored font. */
    private void rebuildFieldSection() {
        fieldSection.removeAllViews();

        List<String> names = new ArrayList<String>();
        List<String> labels = new ArrayList<String>();
        names.add("");
        labels.add(getString(R.string.settings_font_system));
        for (FontLibrary.Entry entry : Settings.fonts(this).list()) {
            names.add(entry.name);
            labels.add(entry.name);
        }

        for (int i = 0; i < Settings.FONT_ROLES.length; i++) {
            fieldSection.addView(fieldRow(Settings.FONT_ROLES[i], FIELD_LABELS[i], names, labels));
        }
    }

    /**
     * One field: its name and its three decorations on one line, its font on the next.
     *
     * The name is short and the toggles are narrow, so they share a line comfortably. The spinner
     * then gets the full width, which it needs — sharing a line with three checkboxes truncated it
     * to "System for" on a 320dp screen.
     */
    private View fieldRow(final String role, int label, final List<String> names,
            List<String> labels) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(8), 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(TEXT_DIM);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(name);

        header.addView(toggle(role, Settings.KEY_BOLD, R.string.settings_bold));
        header.addView(toggle(role, Settings.KEY_ITALIC, R.string.settings_italic));
        header.addView(toggle(role, Settings.KEY_UNDERLINE, R.string.settings_underline));
        block.addView(header);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        final Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        int selected = names.indexOf(Settings.fontNameFor(this, role));
        spinner.setSelection(selected < 0 ? 0 : selected);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Settings.setFontNameFor(SettingsActivity.this, role, names.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        block.addView(spinner);
        return block;
    }

    /**
     * One decoration toggle for one field.
     *
     * A decoration changes how wide the text is — bold is wider, italic leans — so switching one
     * has to make the clock work its sizes out again. It does: the view drops its plan whenever the
     * settings change, and rebuilds it by measuring with the decorations applied.
     */
    private CheckBox toggle(final String role, final String key, int label) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(TEXT_WHITE);
        box.setChecked(Settings.decoration(this, key, role));
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setDecoration(SettingsActivity.this, key, role, checked);
            }
        });
        return box;
    }

    /**
     * The background images: the slideshow's list, its order, how long a still holds, how they
     * are fitted, and the way to add more.
     *
     * The list *is* the slideshow — everything stored shows, in the order this screen shows it,
     * because both read {@link Settings#orderedBackgrounds}. Each row is a checkbox for choosing
     * and two arrows for the user's own arrangement; deleting acts on what is chosen.
     */
    private void rebuildBackgroundSection() {
        backgroundSection.removeAllViews();

        final FontLibrary store = Settings.backgrounds(this);
        final List<FontLibrary.Entry> entries = Settings.orderedBackgrounds(this);
        final List<String> names = new ArrayList<String>();
        for (FontLibrary.Entry entry : entries) {
            names.add(entry.name);
        }
        selectedBackgrounds.retainAll(names);

        if (!entries.isEmpty()) {
            backgroundSection.addView(spinner(ORDER_LABELS,
                    orderIndex(Settings.backgroundOrderMode(this)),
                    new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position,
                                long id) {
                            int mode = ORDER_MODES[position];
                            if (mode == Settings.backgroundOrderMode(SettingsActivity.this)) {
                                return;
                            }
                            // Choosing "my own order" freezes what is on screen right now; the
                            // arrows then rearrange it.
                            if (mode == SlideOrder.CUSTOM) {
                                Settings.setBackgroundCustomOrder(SettingsActivity.this,
                                        orderedNames());
                            }
                            Settings.setBackgroundOrderMode(SettingsActivity.this, mode);
                            rebuildBackgroundSection();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    }));
        }

        for (int i = 0; i < entries.size(); i++) {
            backgroundSection.addView(backgroundRow(entries.get(i), i, entries.size()));
        }

        if (!entries.isEmpty()) {
            LinearLayout chosen = new LinearLayout(this);
            chosen.setOrientation(LinearLayout.HORIZONTAL);
            chosen.setGravity(Gravity.CENTER_VERTICAL);

            final CheckBox all = new CheckBox(this);
            all.setText(R.string.settings_select_all);
            all.setTextColor(TEXT_WHITE);
            all.setChecked(selectedBackgrounds.containsAll(names));
            all.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            all.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    if (checked) {
                        selectedBackgrounds.addAll(names);
                    } else {
                        selectedBackgrounds.clear();
                    }
                    rebuildBackgroundSection();
                }
            });
            chosen.addView(all);

            Button deleteSelected = new Button(this);
            deleteSelected.setText(R.string.settings_delete_selected);
            deleteSelected.setEnabled(!selectedBackgrounds.isEmpty());
            deleteSelected.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<String> arrangement = Settings.backgroundCustomOrder(SettingsActivity.this);
                    for (String name : new ArrayList<String>(selectedBackgrounds)) {
                        store.delete(name);
                        arrangement.remove(name);
                    }
                    Settings.setBackgroundCustomOrder(SettingsActivity.this, arrangement);
                    selectedBackgrounds.clear();
                    rebuildBackgroundSection();
                }
            });
            chosen.addView(deleteSelected);
            backgroundSection.addView(chosen);
        }

        TextView total = footer(entries.isEmpty()
                ? getString(R.string.settings_background_none)
                : getString(R.string.settings_background_total, entries.size(),
                        FontLibrary.humanBytes(store.totalBytes())));
        total.setPadding(0, dp(6), 0, dp(4));
        backgroundSection.addView(total);

        if (!entries.isEmpty()) {
            TextView slideLabel = footer(getString(R.string.settings_slide_time));
            backgroundSection.addView(slideLabel);
            backgroundSection.addView(spinner(SLIDE_LABELS,
                    slideIndex(Settings.backgroundStillSeconds(this)),
                    new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position,
                                long id) {
                            Settings.setBackgroundStillSeconds(SettingsActivity.this,
                                    SLIDE_SECONDS[position]);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    }));
            backgroundSection.addView(spinner(FIT_LABELS,
                    fitIndex(Settings.backgroundFit(this)),
                    new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position,
                                long id) {
                            Settings.setBackgroundFit(SettingsActivity.this, FIT_MODES[position]);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    }));

            CheckBox fade = new CheckBox(this);
            fade.setText(R.string.settings_background_fade);
            fade.setTextColor(TEXT_WHITE);
            fade.setChecked(Settings.backgroundFade(this));
            fade.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    Settings.setBackgroundFade(SettingsActivity.this, checked);
                }
            });
            backgroundSection.addView(fade);
        }

        Button add = new Button(this);
        add.setText(R.string.settings_background_add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage(REQUEST_PICK_BACKGROUND);
            }
        });
        backgroundSection.addView(add);
        backgroundSection.addView(footer(getString(R.string.settings_background_kinds)));
    }

    /** One image of the slideshow: chosen with its checkbox, moved with its arrows. */
    private View backgroundRow(final FontLibrary.Entry entry, final int position, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        final CheckBox choose = new CheckBox(this);
        choose.setText(entry.name + "  ·  " + FontLibrary.humanBytes(entry.bytes));
        choose.setTextColor(TEXT_WHITE);
        choose.setChecked(selectedBackgrounds.contains(entry.name));
        choose.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        choose.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (checked) {
                    selectedBackgrounds.add(entry.name);
                } else {
                    selectedBackgrounds.remove(entry.name);
                }
                rebuildBackgroundSection();
            }
        });
        row.addView(choose);

        Button rename = new Button(this);
        rename.setText(R.string.settings_rename);
        rename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askForNewName(entry.name);
            }
        });
        row.addView(rename);

        row.addView(moveButton(R.string.settings_move_up, position, -1, position > 0));
        row.addView(moveButton(R.string.settings_move_down, position, +1, position < count - 1));
        return row;
    }

    /**
     * A dialog asking what to call this image now.
     *
     * Renaming is how the name sorts become an ordering tool: put "001", "002" in front and the
     * A-to-Z sort is the show's order. The hint says so.
     */
    private void askForNewName(final String name) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(name);
        input.setSelection(0);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_rename_title)
                .setMessage(R.string.settings_rename_hint)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        renameBackground(name, input.getText().toString());
                    }
                })
                .show();
    }

    /** Applies one rename and carries the arrangement, the ticks and the screen along with it. */
    private void renameBackground(String name, String wanted) {
        String stored = Settings.backgrounds(this).rename(name, wanted);
        if (stored == null) {
            toast(getString(R.string.settings_rename_taken));
            return;
        }
        if (!stored.equals(name)) {
            // The user's own arrangement follows the file: same position, new name.
            List<String> arrangement = Settings.backgroundCustomOrder(this);
            int at = arrangement.indexOf(name);
            if (at >= 0) {
                arrangement.set(at, stored);
                Settings.setBackgroundCustomOrder(this, arrangement);
            }
            if (selectedBackgrounds.remove(name)) {
                selectedBackgrounds.add(stored);
            }
        }
        rebuildBackgroundSection();
    }

    /**
     * An arrow that steps one image through the list. The first touch of an arrow adopts whatever
     * order is on screen as the user's own — from then on the spinner reads "my own order".
     */
    private Button moveButton(int label, final int position, final int direction, boolean enabled) {
        Button button = new Button(this);
        button.setText(label);
        button.setEnabled(enabled);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.setBackgroundCustomOrder(SettingsActivity.this,
                        SlideOrder.moved(orderedNames(), position, direction));
                Settings.setBackgroundOrderMode(SettingsActivity.this, SlideOrder.CUSTOM);
                rebuildBackgroundSection();
            }
        });
        return button;
    }

    /** The names in the order the screen and the show both use right now. */
    private List<String> orderedNames() {
        List<String> names = new ArrayList<String>();
        for (FontLibrary.Entry entry : Settings.orderedBackgrounds(this)) {
            names.add(entry.name);
        }
        return names;
    }

    /** The spinner row for this order mode; an unknown stored mode lands on the default. */
    private static int orderIndex(int mode) {
        for (int i = 0; i < ORDER_MODES.length; i++) {
            if (ORDER_MODES[i] == mode) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The image inside the text: what is set and the ways to change it. One image; the glyphs are
     * a window onto it, so a second would have nowhere to show.
     */
    private void rebuildForegroundSection() {
        foregroundSection.removeAllViews();

        String name = Settings.foregroundName(this);
        File file = Settings.foregrounds(this).file(name);

        TextView current = footer(file == null
                ? getString(R.string.settings_foreground_none)
                : name + "  ·  " + FontLibrary.humanBytes(file.length()));
        current.setPadding(0, 0, 0, dp(4));
        foregroundSection.addView(current);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button choose = new Button(this);
        choose.setText(R.string.settings_background_choose);
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage(REQUEST_PICK_FOREGROUND);
            }
        });
        buttons.addView(choose);

        if (file != null) {
            Button remove = new Button(this);
            remove.setText(R.string.settings_background_remove);
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.foregrounds(SettingsActivity.this)
                            .delete(Settings.foregroundName(SettingsActivity.this));
                    Settings.setForegroundName(SettingsActivity.this, "");
                    rebuildForegroundSection();
                }
            });
            buttons.addView(remove);
        }
        foregroundSection.addView(buttons);
        foregroundSection.addView(footer(getString(R.string.settings_foreground_kinds)));
    }

    /**
     * A labelled slider for one orientation's time share, 20 to 90 percent.
     *
     * SeekBar has counted from zero since API 1 and only grew a minimum in API 26, so the
     * progress is kept zero-based and the floor added on. The label repeats the current number,
     * because a bare slider answers "more or less?" but never "how much?".
     */
    private View ratioRow(final int label, final String key) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(4), 0, 0);

        final int floor = Math.round(com.reteclock.core.ClockOptions.MIN_TIME_FRACTION * 100f);
        final int ceiling = Math.round(com.reteclock.core.ClockOptions.MAX_TIME_FRACTION * 100f);

        final TextView caption = footer(getString(label, Settings.timePercent(this, key)));
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
                    Settings.setTimePercent(SettingsActivity.this, key, percent);
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

    /** A full-width spinner over these string resources, selecting {@code selected}. */
    private Spinner spinner(int[] labels, int selected,
            AdapterView.OnItemSelectedListener listener) {
        String[] texts = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            texts[i] = getString(labels[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, texts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        spinner.setOnItemSelectedListener(listener);
        return spinner;
    }

    /** The spinner row for this stored mode; a mode from the future lands on the default. */
    private static int fitIndex(int mode) {
        for (int i = 0; i < FIT_MODES.length; i++) {
            if (FIT_MODES[i] == mode) {
                return i;
            }
        }
        return 0;
    }

    /** The spinner row for this still time; an unknown stored value lands on the default. */
    private static int slideIndex(int seconds) {
        for (int i = 0; i < SLIDE_SECONDS.length; i++) {
            if (SLIDE_SECONDS[i] == seconds) {
                return i;
            }
        }
        return 1;
    }

    private void pickImage(int requestCode) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.settings_font_no_picker));
        }
    }

    /** Adds one image to the slideshow. Nothing is replaced; the show just gains a slide. */
    private void importBackground(Uri uri) {
        String stored = importImage(uri, Settings.backgrounds(this));
        if (stored != null) {
            rebuildBackgroundSection();
        }
    }

    /** Sets the text-fill image; the old one goes only once the new one has decoded. */
    private void importForeground(Uri uri) {
        FontLibrary store = Settings.foregrounds(this);
        String stored = importImage(uri, store);
        if (stored == null) {
            return;
        }
        String old = Settings.foregroundName(this);
        if (!old.isEmpty() && !old.equals(stored)) {
            store.delete(old);
        }
        Settings.setForegroundName(this, stored);
        rebuildForegroundSection();
    }

    /**
     * Reads, stores and decode-checks one picked image; returns its stored name, or null after a
     * toast. Whether the file is an image this device can draw is something only decoding can
     * say, so it is asked now, while the file can still be thrown away.
     */
    private String importImage(Uri uri, FontLibrary store) {
        byte[] content;
        try {
            content = readAll(uri, BackgroundImage.MAX_IMAGE_BYTES);
        } catch (IOException e) {
            toast(getString(R.string.settings_font_failed));
            return null;
        }
        String stored;
        try {
            stored = store.add(displayName(uri), content);
        } catch (IOException e) {
            toast(getString(R.string.settings_font_failed));
            return null;
        }
        if (BackgroundImage.load(store.file(stored)) == null) {
            store.delete(stored);
            toast(getString(R.string.settings_background_rejected));
            return null;
        }
        return stored;
    }

    /**
     * Asks the system for a file. ACTION_OPEN_DOCUMENT is the modern picker and arrived in API 19;
     * older devices get ACTION_GET_CONTENT, which has been there since API 1. Either way the file
     * comes back as a stream the system opens for us, so the app needs no storage permission.
     */
    private void pickFont() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_FONT);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.settings_font_no_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_FONT) {
            importFont(data.getData());
        } else if (requestCode == REQUEST_PICK_BACKGROUND) {
            importBackground(data.getData());
        } else if (requestCode == REQUEST_PICK_FOREGROUND) {
            importForeground(data.getData());
        }
    }

    private void importFont(Uri uri) {
        byte[] content;
        try {
            content = readAll(uri, MAX_FONT_BYTES);
        } catch (IOException e) {
            toast(getString(R.string.settings_font_failed));
            return;
        }

        FontLibrary library = Settings.fonts(this);
        String stored;
        try {
            stored = library.add(displayName(uri), content);
        } catch (IOException e) {
            toast(getString(R.string.settings_font_failed));
            return;
        }

        // Whether the file is really a font is something only the platform can say. Ask it now,
        // while the file can still be thrown away, rather than leaving a dud in the library.
        if (!usableFont(library.file(stored))) {
            library.delete(stored);
            toast(getString(R.string.settings_font_rejected));
            return;
        }

        rebuildFontSection();
        rebuildFieldSection();
        toast(getString(R.string.settings_font_added, stored));
    }

    /**
     * Whether the platform can make a typeface out of this file.
     *
     * createFromFile does not throw for a file that is not a font: it hands back the default
     * typeface. Comparing against the default is therefore the check, and a real font that somehow
     * equals the default would only mean the clock draws in the default face anyway.
     */
    private static boolean usableFont(File file) {
        if (file == null) {
            return false;
        }
        try {
            Typeface loaded = Typeface.createFromFile(file);
            return loaded != null && !loaded.equals(Typeface.DEFAULT);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] readAll(Uri uri, int maxBytes) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("cannot open " + uri);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("file larger than " + maxBytes + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The name the picker shows for this document, falling back to the tail of the URI. */
    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String name = cursor.getString(column);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (RuntimeException e) {
            // Some providers refuse to be queried; the URI still has a tail we can use.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "font.ttf" : last;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        view.setPadding(0, 0, 0, dp(16));
        return view;
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setPadding(0, dp(20), 0, dp(4));
        return view;
    }

    private TextView footer(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setGravity(Gravity.LEFT);
        return view;
    }

    private View spacer() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        return view;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
