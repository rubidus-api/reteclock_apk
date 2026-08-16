package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.widget.EditText;
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

import com.reteclock.core.CustomMarkers;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.FontLibrary;
import com.reteclock.core.ImageFit;
import com.reteclock.core.ImageRoles;
import com.reteclock.core.SlideOrder;

/**
 * The settings screen, reached by long pressing the clock.
 *
 * The views are built in code rather than inflated from XML: the screen is small, and this keeps
 * the app free of layout resources and of any support library, down to API 9. The look is built
 * from the same era's parts — GradientDrawable corners, StateListDrawable presses — arranged as
 * cards on black with one quiet accent, because an API-9 floor limits the toolkit, not the taste.
 */
public class SettingsActivity extends Activity {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    /** One accent, used sparingly: section names, action buttons, the pressed state. */
    private static final int ACCENT = 0xFF4DB6AC;

    /** For the one line that reports something went wrong; nothing else on the page is this colour. */
    private static final int WARNING = 0xFFFFB300;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int DIVIDER = 0xFF272727;
    private static final int PRESSED = 0x334DB6AC;
    private static final int BUTTON_FACE = 0xFF212121;

    /** Anything larger than this is a suspicious thing to call a font on an old phone. */
    private static final int MAX_FONT_BYTES = 32 * 1024 * 1024;

    private static final int REQUEST_PICK_FONT = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_PICK_SETTINGS = 3;
    private static final int REQUEST_SAVE_SETTINGS = 4;

    /** A settings file is a few dozen short lines; anything far larger is not one of ours. */
    private static final int MAX_SETTINGS_BYTES = 256 * 1024;

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
    private final java.util.Set<String> selectedImages = new java.util.HashSet<String>();

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
        R.string.settings_field_meridiem,
        R.string.settings_field_second,
        R.string.settings_field_weekday,
        R.string.settings_field_month_day,
        R.string.settings_field_year,
        R.string.settings_field_quote,
        R.string.settings_field_calendar_title,
        R.string.settings_field_calendar_weekday,
        R.string.settings_field_calendar_day,
    };

    /** Rebuilt in place whenever a font is added or deleted. */
    private LinearLayout fontSection;
    /** The per-field font choices, rebuilt with it because the choices are the stored fonts. */
    private LinearLayout fieldSection;
    /** Rebuilt in place whenever the image pool or the roles change. */
    private LinearLayout imageSection;
    /** The arrangement waiting for the system to say where to put it. */
    private String pendingExport;
    /** The worker that bakes the images, and whether another pass was asked for while it ran. */
    private final Object bakeLock = new Object();
    private Thread baker;
    private boolean bakeAgain;
    /** The two colour rows, rebuilt whenever a colour is picked so the swatches follow. */
    private LinearLayout colorSection;

    /**
     * The colours on offer: greys and blacks first, then one ring of era-friendly hues. A grid
     * of swatches is the whole picker — an RGB dial would out-grow both the screen and the need.
     */
    private static final int[] PALETTE = {
        0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF616161, 0xFF000000,
        0xFFEF5350, 0xFFFF8A65, 0xFFFFB300, 0xFFFFF176, 0xFFAED581,
        0xFF66BB6A, 0xFF4DB6AC, 0xFF4DD0E1, 0xFF64B5F6, 0xFF7986CB,
        0xFF9575CD, 0xFFF06292, 0xFF8D6E63, 0xFF37474F, 0xFF263238,
    };

    /**
     * Which page of the settings this is.
     *
     * The fonts and the pictures are long enough to be screens of their own — scrolling past forty
     * rows of somebody else's photographs to reach the dock is not a settings screen, it is a
     * corridor. They are pages of one activity rather than three activities because every row of
     * all three is built by the same furniture, and because the way back is the same Back button
     * either way.
     */
    public static final String EXTRA_PAGE = "page";
    public static final int PAGE_MAIN = 0;
    public static final int PAGE_FONTS = 1;
    public static final int PAGE_PICTURES = 2;

    private int page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The page carries its own title; the window frame repeating it is clutter.
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        page = getIntent() == null ? PAGE_MAIN : getIntent().getIntExtra(EXTRA_PAGE, PAGE_MAIN);

        if (page == PAGE_MAIN && bounceToClock()) {
            return;
        }

        // Getting here is proof the long-press hint has done its job.
        Settings.setHintSeen(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, dp(16), pad, dp(16));

        if (page == PAGE_FONTS) {
            buildFontPage(root);
            finishPage(root);
            return;
        }
        if (page == PAGE_PICTURES) {
            buildPicturePage(root);
            finishPage(root);
            return;
        }

        root.addView(title(getString(R.string.settings_title)));

        // ---- Start ----
        // First card on the page, because this is where the home-screen button now lands: the way
        // to the clock, and the way back out of a picture that made the clock unusable.
        LinearLayout start = card(getString(R.string.settings_card_start));
        if (Settings.safeNotice(this) || Settings.runUnfinished(this)) {
            TextView warning = footer(getString(R.string.settings_safe_notice));
            warning.setTextColor(WARNING);
            start.addView(warning);
            // Said once. The mark itself is cleared by the next healthy run of the clock.
            Settings.setSafeNotice(this, false);
        }
        start.addView(actionButton(getString(R.string.settings_start_clock),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this, ClockActivity.class));
                    }
                }));

        // Directly under the button that starts the clock, because it decides whether the clock
        // you are about to look at can be touched at all: Android hands no touches to a
        // screensaver or to anything over the lock screen. It used to sit at the foot of the
        // screen with the dock settings, where nobody found it until they wondered why the
        // timer's buttons did nothing.
        final CheckBox stay = new CheckBox(this);
        stay.setText(R.string.settings_stay_unlocked);
        stay.setTextColor(TEXT_WHITE);
        stay.setChecked(Settings.stayUnlocked(this));
        stay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setStayUnlocked(SettingsActivity.this, checked);
            }
        });
        start.addView(stay);
        start.addView(footer(getString(R.string.settings_stay_unlocked_note)));
        final CheckBox direct = new CheckBox(this);
        direct.setText(R.string.settings_direct_start);
        direct.setTextColor(TEXT_WHITE);
        direct.setChecked(Settings.directStart(this));
        direct.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setDirectStart(SettingsActivity.this, checked);
            }
        });
        start.addView(direct);
        start.addView(footer(getString(R.string.settings_direct_start_note)));
        root.addView(start);

        // ---- Clock ----
        LinearLayout clock = card(getString(R.string.settings_card_clock));
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
        clock.addView(seconds);

        // Everything the twelve-hour clock brings with it — the warning, what other countries do,
        // and the two questions about noon and midnight — lives in one box that is only there when
        // the twelve-hour clock is. On a 24-hour clock none of it applies: 00:00 and 12:00 say what
        // they are, and a screen full of warning about an ambiguity you do not have is just noise.
        final LinearLayout twelveHourExtras = new LinearLayout(this);
        twelveHourExtras.setOrientation(LinearLayout.VERTICAL);

        final CheckBox twelveHour = new CheckBox(this);
        twelveHour.setText(R.string.settings_hour12);
        twelveHour.setTextColor(TEXT_WHITE);
        twelveHour.setChecked(Settings.hour12(this));
        twelveHour.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setHour12(SettingsActivity.this, checked);
                twelveHourExtras.setVisibility(checked ? View.VISIBLE : View.GONE);
            }
        });
        clock.addView(twelveHour);
        clock.addView(twelveHourExtras);

        twelveHourExtras.addView(warning(getString(R.string.settings_hour12_warning)));
        twelveHourExtras.addView(footer(getString(R.string.settings_hour12_countries)));
        twelveHourExtras.addView(footer(getString(R.string.settings_hour12_note)));

        // Before the conventions: what the marker actually says. AM and PM are Latin abbreviations
        // that much of the world does not write in Latin, and the labels below are built from
        // whatever is typed here, so the options keep showing the reading they produce.
        final CustomMarkers marks = Settings.markers(this);
        twelveHourExtras.addView(subheading(getString(R.string.settings_markers)));
        twelveHourExtras.addView(markerRow(R.string.settings_marker_am, "AM", marks.amEntry(), 0));
        twelveHourExtras.addView(markerRow(R.string.settings_marker_pm, "PM", marks.pmEntry(), 1));
        twelveHourExtras.addView(markerRow(R.string.settings_marker_noon, "PM", marks.noonEntry(),
                2));
        twelveHourExtras.addView(markerRow(R.string.settings_marker_midnight, "AM",
                marks.midnightEntry(), 3));
        twelveHourExtras.addView(footer(getString(R.string.settings_markers_note)));

        // Noon and midnight are the one thing a twelve-hour clock cannot say plainly, and they are
        // two separate questions rather than one. Each option is labelled with the reading itself,
        // so nobody has to know which country's convention they are picking.
        twelveHourExtras.addView(subheading(getString(R.string.settings_noon)));
        RadioGroup noonStyle = new RadioGroup(this);
        noonStyle.setOrientation(RadioGroup.VERTICAL);
        // The examples are the instant the setting is about — noon itself, not some minute past
        // it. "12:43 PM" made the reader work out which of the five numbers was the one in question.
        String[] noons = {"12:00 " + marks.atNoon("PM"), "12:00 " + marks.atNoon("AM"),
            "12:00 " + marks.atNoon("NN"), "0:00 " + marks.atNoon("PM")};
        for (int style = 0; style < noons.length; style++) {
            RadioButton option = new RadioButton(this);
            option.setId(900 + style);
            option.setText(noons[style]);
            option.setTextColor(TEXT_WHITE);
            noonStyle.addView(option);
        }
        noonStyle.check(900 + Settings.noonStyle(this));
        noonStyle.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Settings.setNoonStyle(SettingsActivity.this, checkedId - 900);
            }
        });
        twelveHourExtras.addView(noonStyle);

        twelveHourExtras.addView(subheading(getString(R.string.settings_midnight)));
        RadioGroup midnightStyle = new RadioGroup(this);
        midnightStyle.setOrientation(RadioGroup.VERTICAL);
        String[] midnights = {"12:00 " + marks.atMidnight("AM"), "12:00 " + marks.atMidnight("PM"),
            "00:00", "12:00 " + marks.atMidnight("MN"), "0:00 " + marks.atMidnight("AM")};
        for (int style = 0; style < midnights.length; style++) {
            RadioButton option = new RadioButton(this);
            option.setId(950 + style);
            option.setText(midnights[style]);
            option.setTextColor(TEXT_WHITE);
            midnightStyle.addView(option);
        }
        midnightStyle.check(950 + Settings.midnightStyle(this));
        midnightStyle.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Settings.setMidnightStyle(SettingsActivity.this, checkedId - 950);
            }
        });
        twelveHourExtras.addView(midnightStyle);

        twelveHourExtras.setVisibility(Settings.hour12(this) ? View.VISIBLE : View.GONE);


        clock.addView(subheading(getString(R.string.settings_date_format)));
        RadioGroup dateStyle = new RadioGroup(this);
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
        clock.addView(dateStyle);

        final CheckBox wander = new CheckBox(this);
        wander.setText(R.string.settings_burn_in);
        wander.setTextColor(TEXT_WHITE);
        wander.setChecked(Settings.burnInShift(this));
        wander.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setBurnInShift(SettingsActivity.this, checked);
            }
        });
        clock.addView(wander);
        clock.addView(footer(getString(R.string.settings_burn_in_note)));

        final CheckBox saying = new CheckBox(this);
        saying.setText(R.string.settings_quote);
        saying.setTextColor(TEXT_WHITE);
        saying.setChecked(Settings.quoteOn(this));
        saying.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setQuoteOn(SettingsActivity.this, checked);
            }
        });
        clock.addView(saying);
        clock.addView(footer(getString(R.string.settings_quote_note)));
        // The sayings are somebody else's work, even if nobody's copyright. Cited in full, where
        // the person switching them on can actually see it.
        clock.addView(subheading(getString(R.string.settings_quote_sources)));
        clock.addView(footer(getString(R.string.settings_quote_source1)));
        clock.addView(footer(getString(R.string.settings_quote_source2)));
        clock.addView(footer(getString(R.string.settings_quote_source3)));
        clock.addView(footer(getString(R.string.settings_quote_rights)));

        clock.addView(subheading(getString(R.string.settings_colors)));
        colorSection = new LinearLayout(this);
        colorSection.setOrientation(LinearLayout.VERTICAL);
        clock.addView(colorSection);
        clock.addView(footer(getString(R.string.settings_color_note)));

        clock.addView(subheading(getString(R.string.settings_ratio)));
        clock.addView(ratioRow(R.string.settings_ratio_wide, Settings.KEY_TIME_PERCENT_WIDE));
        clock.addView(ratioRow(R.string.settings_ratio_tall, Settings.KEY_TIME_PERCENT_TALL));
        clock.addView(footer(getString(R.string.settings_ratio_note)));
        root.addView(clock);

        // ---- Fonts and pictures, each on a page of its own ----
        LinearLayout elsewhere = card(getString(R.string.settings_card_look));
        elsewhere.addView(actionButton(getString(R.string.settings_open_fonts),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openPage(PAGE_FONTS);
                    }
                }));
        elsewhere.addView(actionButton(getString(R.string.settings_open_pictures),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openPage(PAGE_PICTURES);
                    }
                }));
        elsewhere.addView(footer(getString(R.string.settings_card_look_note)));
        root.addView(elsewhere);

        // ---- Carrying the settings ----
        LinearLayout carry = card(getString(R.string.settings_card_carry));
        carry.addView(footer(getString(R.string.settings_carry_note)));
        carry.addView(actionButton(getString(R.string.settings_export),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        exportSettings();
                    }
                }));
        carry.addView(actionButton(getString(R.string.settings_import),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickSettings();
                    }
                }));
        root.addView(carry);

        // ---- Dock ----
        LinearLayout dock = card(getString(R.string.settings_dock));
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
        dock.addView(charging);

        addScreensaverRow(dock);
        root.addView(dock);

        TextView about = footer(getString(R.string.settings_about,
                versionName(), Build.VERSION.RELEASE));
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        about.setPadding(0, dp(8), 0, dp(4));
        root.addView(about);

        rebuildColorSection();

        finishPage(root);
    }

    /** Every page ends the same way: black behind it, and the whole of it scrollable. */
    private void finishPage(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void openPage(int which) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(EXTRA_PAGE, which);
        startActivity(intent);
    }

    /** The font library and the per-field choices: everything about what the clock is written in. */
    private void buildFontPage(LinearLayout root) {
        root.addView(title(getString(R.string.settings_open_fonts)));
        LinearLayout fonts = card(getString(R.string.settings_font));
        fontSection = new LinearLayout(this);
        fontSection.setOrientation(LinearLayout.VERTICAL);
        fonts.addView(fontSection);
        fonts.addView(actionButton(getString(R.string.settings_font_add),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickFont();
                    }
                }));
        fonts.addView(footer(getString(R.string.settings_font_kinds)));
        fonts.addView(divider());
        fonts.addView(subheading(getString(R.string.settings_font_per_field)));
        fieldSection = new LinearLayout(this);
        fieldSection.setOrientation(LinearLayout.VERTICAL);
        fonts.addView(fieldSection);
        root.addView(fonts);
        rebuildFontSection();
        rebuildFieldSection();
    }

    /** The picture pool and what each picture is for. */
    private void buildPicturePage(LinearLayout root) {
        root.addView(title(getString(R.string.settings_open_pictures)));
        LinearLayout images = card(getString(R.string.settings_images));
        imageSection = new LinearLayout(this);
        imageSection.setOrientation(LinearLayout.VERTICAL);
        images.addView(imageSection);
        root.addView(images);
        rebuildImageSection();
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
        for (int i = 0; i < entries.size(); i++) {
            FontLibrary.Entry entry = entries.get(i);
            String label = entry.name + "  ·  " + FontLibrary.humanBytes(entry.bytes);
            if (i > 0) {
                fontSection.addView(divider());
            }
            fontSection.addView(fontRow(entry.name, label));
        }

        TextView total = footer(entries.isEmpty()
                ? getString(R.string.settings_font_none)
                : getString(R.string.settings_font_total, entries.size(),
                        FontLibrary.humanBytes(library.totalBytes())));
        total.setPadding(0, dp(6), 0, dp(6));
        fontSection.addView(total);
    }

    /** One row: the font's name and size, and a compact button that deletes it. */
    private View fontRow(final String name, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(TEXT_WHITE);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text);

        row.addView(iconButton("✕", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.fonts(SettingsActivity.this).delete(name);
                // A field still naming this font falls back to the system face on its own, because
                // the lookup checks the file is there. Nothing else to clean up.
                rebuildFontSection();
                rebuildFieldSection();
            }
        }));
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
        header.addView(toggle(role, Settings.KEY_OUTLINE, R.string.settings_outline));
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

    /** The pixel width of one checkbox column, so the header and every row line up. */
    private int columnWidth() {
        return dp(38);
    }

    /**
     * The image pool: one list, three checkbox columns.
     *
     * The first column chooses images for group actions (delete). The other two say where an
     * image serves — behind the clock or inside the digits — and are exclusive: ticking one
     * clears the other, and neither ticked means the image is held, kept but unused. The header
     * carries a select-all for each column. Both shows and this screen read the same ordered
     * pool, so what is listed is what plays, in this order.
     */
    private void rebuildImageSection() {
        imageSection.removeAllViews();
        // Whatever just changed — an import, a deletion, a rename — the baked files follow it.
        prepareImages();

        final FontLibrary store = Settings.images(this);
        final List<FontLibrary.Entry> entries = Settings.orderedImages(this);
        final ImageRoles.Lists roles = Settings.roles(this);
        final List<String> names = new ArrayList<String>();
        for (FontLibrary.Entry entry : entries) {
            names.add(entry.name);
        }
        selectedImages.retainAll(names);

        if (!entries.isEmpty()) {
            imageSection.addView(spinner(ORDER_LABELS,
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
                            rebuildImageSection();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    }));

            imageSection.addView(columnLabels());
            imageSection.addView(selectAllRow(names, roles));
            imageSection.addView(divider());
            for (int i = 0; i < entries.size(); i++) {
                imageSection.addView(imageRow(entries.get(i), roles, i, entries.size()));
            }
            imageSection.addView(divider());
        }

        TextView total = footer(entries.isEmpty()
                ? getString(R.string.settings_background_none)
                : getString(R.string.settings_background_total, entries.size(),
                        FontLibrary.humanBytes(store.totalBytes())));
        total.setPadding(0, dp(6), 0, dp(4));
        imageSection.addView(total);

        long prepared = PreparedImages.preparedBytes(this);
        if (prepared > 0) {
            TextView preparedTotal = footer(getString(R.string.settings_prepared_total,
                    FontLibrary.humanBytes(prepared)));
            preparedTotal.setPadding(0, 0, 0, dp(4));
            imageSection.addView(preparedTotal);
        }

        if (!entries.isEmpty()) {
            TextView slideLabel = footer(getString(R.string.settings_slide_time));
            imageSection.addView(slideLabel);
            imageSection.addView(spinner(SLIDE_LABELS,
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
            imageSection.addView(spinner(FIT_LABELS,
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
            imageSection.addView(fade);
        }

        imageSection.addView(actionButton(getString(R.string.settings_background_add),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickImage();
                    }
                }));
        imageSection.addView(footer(getString(R.string.settings_images_hint)));
        imageSection.addView(footer(getString(R.string.settings_background_kinds)));
    }

    /** The three column captions, sitting exactly over their checkboxes. */
    private View columnLabels() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        row.addView(columnLabel(R.string.settings_col_sel));
        row.addView(columnLabel(R.string.settings_col_bg));
        row.addView(columnLabel(R.string.settings_col_text));
        TextView image = new TextView(this);
        image.setText(R.string.settings_col_image);
        image.setTextColor(TEXT_DIM);
        image.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        image.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(image);
        return row;
    }

    private TextView columnLabel(int label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                columnWidth(), LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** One select-all per column, and the delete that acts on the first of them. */
    private View selectAllRow(final List<String> names, final ImageRoles.Lists roles) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        boolean allSelected = !names.isEmpty() && selectedImages.containsAll(names);
        row.addView(columnCheckBox(allSelected, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (checked) {
                    selectedImages.addAll(names);
                } else {
                    selectedImages.clear();
                }
                rebuildImageSection();
            }
        }));

        boolean allBackground = allIn(names, roles.background);
        row.addView(columnCheckBox(allBackground, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                assignAll(names, checked ? ImageRoles.BACKGROUND : ImageRoles.NONE,
                        ImageRoles.BACKGROUND);
            }
        }));

        boolean allText = allIn(names, roles.text);
        row.addView(columnCheckBox(allText, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                assignAll(names, checked ? ImageRoles.TEXT : ImageRoles.NONE, ImageRoles.TEXT);
            }
        }));

        TextView label = new TextView(this);
        label.setText(R.string.settings_select_all);
        label.setTextColor(TEXT_DIM);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        TextView delete = iconButton(getString(R.string.settings_font_delete),
                !selectedImages.isEmpty(), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        deleteSelected();
                    }
                });
        row.addView(delete);
        return row;
    }

    private static boolean allIn(List<String> names, List<String> role) {
        return !names.isEmpty() && role.containsAll(names);
    }

    /**
     * Points every image at {@code role} — or, when un-ticking, releases only the images that
     * held {@code released}, so clearing the background column cannot silently strip the text one.
     */
    private void assignAll(List<String> names, int role, int released) {
        ImageRoles.Lists lists = Settings.roles(this);
        for (String name : names) {
            if (role != ImageRoles.NONE || ImageRoles.roleOf(lists, name) == released) {
                lists = ImageRoles.assign(lists, name, role);
            }
        }
        Settings.saveRoles(this, lists);
        rebuildImageSection();
    }

    private void deleteSelected() {
        FontLibrary store = Settings.images(this);
        ImageRoles.Lists lists = Settings.roles(this);
        List<String> arrangement = Settings.backgroundCustomOrder(this);
        for (String name : new ArrayList<String>(selectedImages)) {
            store.delete(name);
            lists = ImageRoles.removed(lists, name);
            arrangement.remove(name);
        }
        Settings.saveRoles(this, lists);
        Settings.setBackgroundCustomOrder(this, arrangement);
        selectedImages.clear();
        rebuildImageSection();
    }

    /**
     * One image: selection, its two role boxes, its name (tap it to rename), and its arrows.
     */
    private View imageRow(final FontLibrary.Entry entry, ImageRoles.Lists roles,
            final int position, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        row.addView(columnCheckBox(selectedImages.contains(entry.name),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        if (checked) {
                            selectedImages.add(entry.name);
                        } else {
                            selectedImages.remove(entry.name);
                        }
                        rebuildImageSection();
                    }
                }));

        int role = ImageRoles.roleOf(roles, entry.name);
        row.addView(columnCheckBox(role == ImageRoles.BACKGROUND,
                roleListener(entry.name, ImageRoles.BACKGROUND)));
        row.addView(columnCheckBox(role == ImageRoles.TEXT,
                roleListener(entry.name, ImageRoles.TEXT)));

        // Two lines in one column: the name with the file's own size, and under it what its
        // prepared file costs. On one line the second half was simply ellipsized away.
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(entry.name + "  ·  " + FontLibrary.humanBytes(entry.bytes));
        name.setTextColor(TEXT_WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        column.addView(name);

        String note = preparedNote(entry.name, role);
        if (!note.isEmpty()) {
            TextView prepared = new TextView(this);
            prepared.setText(note);
            prepared.setTextColor(TEXT_DIM);
            prepared.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            prepared.setSingleLine(true);
            column.addView(prepared);
        }

        column.setClickable(true);
        column.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askForNewName(entry.name);
            }
        });
        row.addView(column);

        row.addView(iconButton("▲", position > 0, moveListener(position, -1)));
        row.addView(iconButton("▼", position < count - 1, moveListener(position, +1)));
        return row;
    }

    /**
     * What one row says about its prepared file: how much disk it takes, or that there is none.
     *
     * Only for the images a show actually uses — a held image is not prepared, and saying so on
     * every row would be noise. "Played live" is the honest word for the rest: a picture with
     * transparency is never baked, and one imported a moment ago has not been yet.
     */
    private String preparedNote(String imageName, int role) {
        if (role == ImageRoles.NONE) {
            return "";
        }
        int edge = PreparedImages.screenEdge(this);
        long bytes = PreparedImages.preparedBytes(this, imageName, edge);
        if (bytes <= 0) {
            return getString(R.string.settings_image_live);
        }
        String inside = PreparedImages.describe(PreparedImages.packFor(this, imageName, edge));
        String size = FontLibrary.humanBytes(bytes);
        return inside == null
                ? getString(R.string.settings_image_ready, size)
                : getString(R.string.settings_image_ready_detail, size, inside);
    }

    /** Ticking claims the role; un-ticking releases the image to held. Exclusivity is the core's. */
    private CompoundButton.OnCheckedChangeListener roleListener(final String name,
            final int role) {
        return new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                ImageRoles.Lists lists = Settings.roles(SettingsActivity.this);
                if (checked) {
                    lists = ImageRoles.assign(lists, name, role);
                } else if (ImageRoles.roleOf(lists, name) == role) {
                    lists = ImageRoles.assign(lists, name, ImageRoles.NONE);
                }
                Settings.saveRoles(SettingsActivity.this, lists);
                rebuildImageSection();
            }
        };
    }

    private View.OnClickListener moveListener(final int position, final int direction) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.setBackgroundCustomOrder(SettingsActivity.this,
                        SlideOrder.moved(orderedNames(), position, direction));
                Settings.setBackgroundOrderMode(SettingsActivity.this, SlideOrder.CUSTOM);
                rebuildImageSection();
            }
        };
    }

    /** A bare checkbox sitting in one of the three fixed columns. */
    private CheckBox columnCheckBox(boolean checked,
            CompoundButton.OnCheckedChangeListener listener) {
        CheckBox box = new CheckBox(this);
        box.setChecked(checked);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                columnWidth(), LinearLayout.LayoutParams.WRAP_CONTENT));
        // The listener goes on after the state, so a rebuild cannot fire it.
        box.setOnCheckedChangeListener(listener);
        return box;
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
                        renameImage(name, input.getText().toString());
                    }
                })
                .show();
    }

    /** Applies one rename; the role, the arrangement, the ticks and the screen follow the file. */
    private void renameImage(String name, String wanted) {
        String stored = Settings.images(this).rename(name, wanted);
        if (stored == null) {
            toast(getString(R.string.settings_rename_taken));
            return;
        }
        if (!stored.equals(name)) {
            Settings.saveRoles(this, ImageRoles.renamed(Settings.roles(this), name, stored));
            List<String> arrangement = Settings.backgroundCustomOrder(this);
            int at = arrangement.indexOf(name);
            if (at >= 0) {
                arrangement.set(at, stored);
                Settings.setBackgroundCustomOrder(this, arrangement);
            }
            if (selectedImages.remove(name)) {
                selectedImages.add(stored);
            }
        }
        rebuildImageSection();
    }

    /** The names in the order the screen and the shows all use right now. */
    private List<String> orderedNames() {
        List<String> names = new ArrayList<String>();
        for (FontLibrary.Entry entry : Settings.orderedImages(this)) {
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

    /** The two colour rows: a name, and a swatch that opens the palette. */
    private void rebuildColorSection() {
        colorSection.removeAllViews();
        colorSection.addView(colorRow(R.string.settings_text_color, Settings.KEY_TEXT_COLOR,
                Settings.KEY_BACKGROUND_COLOR));
        colorSection.addView(colorRow(R.string.settings_background_color,
                Settings.KEY_BACKGROUND_COLOR, Settings.KEY_TEXT_COLOR));
    }

    private View colorRow(int label, final String key, final String otherKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(TEXT_WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        View swatch = swatch(Settings.color(this, key), dp(30));
        swatch.setClickable(true);
        swatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickColor(key, otherKey);
            }
        });
        row.addView(swatch);
        return row;
    }

    /** One rounded square of colour, with a hairline so white reads on the card too. */
    private View swatch(int color, int size) {
        View view = new View(this);
        GradientDrawable face = new GradientDrawable();
        face.setColor(color | 0xFF000000);
        face.setCornerRadius(dp(6));
        face.setStroke(1, 0xFF555555);
        view.setBackgroundDrawable(face);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }

    /**
     * The palette, four rows of five. Picking the colour the other setting already wears is
     * refused with a word — a clock in its own background colour is no clock — and the dialog
     * stays open for a second thought.
     */
    private void pickColor(final String key, final String otherKey) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        grid.setPadding(pad, pad, pad, pad);

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(key.equals(Settings.KEY_TEXT_COLOR)
                        ? R.string.settings_text_color : R.string.settings_background_color)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        LinearLayout line = null;
        for (int i = 0; i < PALETTE.length; i++) {
            if (i % 5 == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(line);
            }
            final int color = PALETTE[i];
            View swatch = swatch(color, dp(44));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatch.setLayoutParams(params);
            swatch.setClickable(true);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (com.reteclock.core.ClockColors.same(color,
                            Settings.color(SettingsActivity.this, otherKey))) {
                        toast(getString(R.string.settings_color_same));
                        return;
                    }
                    Settings.setColor(SettingsActivity.this, key, color);
                    dialog.dismiss();
                    rebuildColorSection();
                }
            });
            line.addView(swatch);
        }
        dialog.show();
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

        final int floor = Math.round(ClockOptions.MIN_TIME_FRACTION * 100f);
        final int ceiling = Math.round(ClockOptions.MAX_TIME_FRACTION * 100f);

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

    private void pickImage() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.settings_font_no_picker));
        }
    }

    /**
     * Adds one image to the pool. A new arrival starts as a background — the common wish, and one
     * tick away from anything else; a re-import of a file already in the pool keeps whatever role
     * it has.
     */
    private void importImage(Uri uri) {
        FontLibrary store = Settings.images(this);
        byte[] content;
        try {
            content = readAll(uri, BackgroundImage.MAX_IMAGE_BYTES);
        } catch (IOException e) {
            toast(getString(R.string.settings_font_failed));
            return;
        }
        String stored;
        try {
            stored = store.add(displayName(uri), content);
        } catch (IOException e) {
            toast(getString(R.string.settings_font_failed));
            return;
        }
        // Whether the file is an image this device can draw is something only decoding can say,
        // so it is asked now, while the file can still be thrown away.
        if (BackgroundImage.load(store.file(stored)) == null) {
            store.delete(stored);
            toast(getString(R.string.settings_background_rejected));
            return;
        }
        ImageRoles.Lists lists = Settings.roles(this);
        if (ImageRoles.roleOf(lists, stored) == ImageRoles.NONE) {
            Settings.saveRoles(this, ImageRoles.assign(lists, stored, ImageRoles.BACKGROUND));
        }
        rebuildImageSection();
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
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
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
        } else if (requestCode == REQUEST_PICK_IMAGE) {
            importImage(data.getData());
        } else if (requestCode == REQUEST_PICK_SETTINGS) {
            importSettings(data.getData());
        } else if (requestCode == REQUEST_SAVE_SETTINGS) {
            writeSettings(data.getData());
        }
    }

    /**
     * Writes the arrangement out.
     *
     * On KitKat and later the system asks where to put it, which needs no storage permission. On
     * anything older there is no such picker, so the text is handed to whatever the user shares
     * with — mail, notes, a file manager — which also needs no permission and is the only way to
     * get a file off a 2011 phone without asking for the whole of external storage.
     */
    private void exportSettings() {
        String text = SettingsPortability.export(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, exportName());
            try {
                pendingExport = text;
                startActivityForResult(intent, REQUEST_SAVE_SETTINGS);
                return;
            } catch (ActivityNotFoundException e) {
                pendingExport = null;
            }
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, exportName());
        share.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(share, getString(R.string.settings_export)));
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.settings_carry_no_picker));
        }
    }

    /** reteclock-settings-20260812-2130.txt — the date is what tells two backups apart. */
    private String exportName() {
        return "reteclock-settings-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                        .format(new java.util.Date())
                + ".txt";
    }

    private void writeSettings(Uri uri) {
        String text = pendingExport;
        pendingExport = null;
        if (text == null) {
            return;
        }
        java.io.OutputStream out = null;
        try {
            out = getContentResolver().openOutputStream(uri);
            if (out == null) {
                throw new IOException("cannot write " + uri);
            }
            out.write(text.getBytes("UTF-8"));
            toast(getString(R.string.settings_export_done));
        } catch (IOException e) {
            toast(getString(R.string.settings_export_failed));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Nothing useful to do; the toast above already said how it went.
                }
            }
        }
    }

    private void pickSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_SETTINGS);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.settings_carry_no_picker));
        }
    }

    private void importSettings(Uri uri) {
        String text;
        try {
            text = new String(readAll(uri, MAX_SETTINGS_BYTES), "UTF-8");
        } catch (IOException e) {
            toast(getString(R.string.settings_import_failed));
            return;
        }
        SettingsPortability.Result result = SettingsPortability.importFrom(this, text);
        if (!result.readable) {
            toast(getString(R.string.settings_import_not_ours));
            return;
        }
        toast(result.droppedNames > 0
                ? getString(R.string.settings_import_done_partial, result.applied,
                        result.droppedNames)
                : getString(R.string.settings_import_done, result.applied));
        // Everything on screen was built from the old values; the sections that can be rebuilt
        // are, and a restart of the screen catches the rest.
        rebuildFontSection();
        rebuildFieldSection();
        rebuildImageSection();
        rebuildColorSection();
        finish();
        startActivity(new Intent(this, SettingsActivity.class));
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

    // ---- The era-appropriate design system: cards, one accent, compact controls. ----

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        view.setPadding(dp(4), 0, 0, dp(12));
        return view;
    }

    /**
     * One card: a rounded panel on the black, its section name in the accent at the top.
     *
     * GradientDrawable has drawn rounded corners since API 1, which is all a card is.
     */
    private LinearLayout card(String name) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable face = new GradientDrawable();
        face.setColor(CARD);
        face.setCornerRadius(dp(10));
        face.setStroke(1, CARD_STROKE);
        card.setBackgroundDrawable(face);
        int pad = dp(14);
        card.setPadding(pad, dp(12), pad, dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        TextView heading = new TextView(this);
        heading.setText(name.toUpperCase(java.util.Locale.US));
        heading.setTextColor(ACCENT);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, 0, 0, dp(6));
        card.addView(heading);
        return card;
    }

    /** A quieter heading inside a card, for its second thought. */
    /**
     * One of the four markers, shown as "AM  →  오전" once it has been given a word of its own.
     *
     * Editing one rebuilds the screen rather than the row, because the noon and midnight options
     * below are labelled with the reading they produce and that reading has just changed.
     */
    private Button markerRow(final int label, final String built, String typed, final int which) {
        Button row = new Button(this);
        row.setText(typed.length() == 0
                ? getString(label) : getString(label) + "  \u2192  " + typed);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askForMarker(label, which);
            }
        });
        return row;
    }

    private void askForMarker(int label, final int which) {
        CustomMarkers now = Settings.markers(this);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(which == 0 ? now.amEntry() : which == 1 ? now.pmEntry()
                : which == 2 ? now.noonEntry() : now.midnightEntry());
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(getString(label))
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int ignored) {
                        CustomMarkers marks = Settings.markers(SettingsActivity.this);
                        String typed = input.getText().toString();
                        Settings.setMarkers(SettingsActivity.this,
                                which == 0 ? marks.withAm(typed)
                                        : which == 1 ? marks.withPm(typed)
                                        : which == 2 ? marks.withNoon(typed)
                                        : marks.withMidnight(typed));
                        recreate();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private TextView subheading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setPadding(0, dp(12), 0, dp(2));
        return view;
    }

    /**
     * A note that is a warning rather than an explanation.
     *
     * Amber, and above the choice it is about. The screen has one of these and it is the twelve-hour
     * clock, because that is the only setting here that can make the app state a time somebody
     * reads as a different time.
     */
    private TextView warning(String text) {
        TextView view = footer(text);
        view.setTextColor(WARNING);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private TextView footer(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setGravity(Gravity.LEFT);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    /** A hairline between rows. */
    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        line.setLayoutParams(params);
        return line;
    }

    /** The rounded face a pressable control wears, with the accent showing through a press. */
    private StateListDrawable pressable(int restingColor) {
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(PRESSED);
        pressed.setCornerRadius(dp(8));
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(restingColor);
        resting.setCornerRadius(dp(8));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[] {}, resting);
        return states;
    }

    /**
     * A compact square-ish button for one glyph or one word — a stock Button insists on being
     * wide enough to crowd a 320dp row off the screen.
     */
    private TextView iconButton(String glyph, boolean enabled, View.OnClickListener onClick) {
        TextView button = new TextView(this);
        button.setText(glyph);
        button.setTextColor(enabled ? TEXT_WHITE : 0xFF555555);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(6), dp(8), dp(6));
        button.setBackgroundDrawable(pressable(BUTTON_FACE));
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

    /**
     * Bakes every image into the form the clock plays, on a worker thread.
     *
     * The settings screen is where the waiting belongs: it can afford a second of work, and the
     * clock cannot. One worker at a time; a request that arrives while one is running is remembered
     * and served when it finishes, so a run of quick changes ends in one final pass rather than a
     * queue of them.
     */
    private void prepareImages() {
        synchronized (bakeLock) {
            if (baker != null) {
                bakeAgain = true;
                return;
            }
            baker = new Thread(new Runnable() {
                @Override
                public void run() {
                    int baked = 0;
                    while (true) {
                        baked += PreparedImages.prepareAll(SettingsActivity.this);
                        synchronized (bakeLock) {
                            if (!bakeAgain) {
                                baker = null;
                                break;
                            }
                            bakeAgain = false;
                        }
                    }
                    announcePrepared(baked);
                }
            }, "reteclock-prepare");
            baker.setPriority(Thread.MIN_PRIORITY);
            baker.start();
        }
    }

    /** Says what was prepared, and only when there was something — silence is the usual case. */
    private void announcePrepared(final int baked) {
        if (baked <= 0 || isFinishing()) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                Toast.makeText(SettingsActivity.this,
                        getString(R.string.settings_images_prepared, baked),
                        Toast.LENGTH_SHORT).show();
                // The rows were drawn before those files existed; now they can say what they cost.
                // Rebuilding asks for another bake, which finds nothing to do and stops there.
                rebuildImageSection();
            }
        });
    }

    /**
     * The screensaver row: whether this app is the chosen one, and a button that opens the system
     * screen where it is chosen.
     *
     * Finding that screen by hand is the awkward part — it has lived under Display, under Security,
     * and under its own name, depending on the version and the manufacturer — so the button goes
     * straight there. Android 4.2 is where screensavers begin; below that the row is not shown at
     * all rather than shown as something that cannot work.
     */
    private void addScreensaverRow(LinearLayout dock) {
        if (Build.VERSION.SDK_INT < 17) {
            return;
        }
        dock.addView(divider());
        dock.addView(subheading(getString(R.string.settings_screensaver)));
        TextView state = footer(getString(isScreensaver()
                ? R.string.settings_screensaver_on
                : R.string.settings_screensaver_off));
        if (isScreensaver()) {
            state.setTextColor(ACCENT);
        }
        dock.addView(state);
        dock.addView(actionButton(getString(R.string.settings_screensaver_open),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openScreensaverSettings();
                    }
                }));
        dock.addView(footer(getString(R.string.settings_screensaver_note)));
    }

    /** Whether the system's screensaver setting names this app. */
    private boolean isScreensaver() {
        try {
            String stored = android.provider.Settings.Secure.getString(
                    getContentResolver(), "screensaver_components");
            return com.reteclock.core.ScreensaverState.isChosen(stored,
                    new android.content.ComponentName(this, ClockDreamService.class)
                            .flattenToString());
        } catch (RuntimeException e) {
            // The key is not part of the public API; a platform that does not keep it there simply
            // leaves the row saying nothing is known, which is better than a crash.
            return false;
        }
    }

    /**
     * Opens the system's screensaver settings, or the nearest screen that leads there.
     *
     * The dedicated screen is not present everywhere — some builds only have Display — so each
     * candidate is offered to the package manager and the first that resolves is used.
     */
    private void openScreensaverSettings() {
        String[] candidates = {
            "android.settings.DREAM_SETTINGS",
            "android.settings.DISPLAY_SETTINGS",
            "android.settings.SETTINGS",
        };
        for (String action : candidates) {
            Intent intent = new Intent(action);
            if (intent.resolveActivity(getPackageManager()) == null) {
                continue;
            }
            try {
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                // Resolved a moment ago and gone now: try the next one.
            }
        }
        Toast.makeText(this, R.string.settings_screensaver_no_screen, Toast.LENGTH_LONG).show();
    }

    /**
     * Sends a home-screen launch straight on to the clock, when that is what the user asked for.
     *
     * The home-screen button opens this screen by default. It is the only door back into the app
     * when a picture or a font has made the full-screen clock unusable — there are no buttons on a
     * clock face, and a clock that has stopped answering will not take a long press. Whoever wants
     * the old habit back turns the switch on; a run that never came back overrules it, because that
     * is precisely the case the door exists for.
     */
    private boolean bounceToClock() {
        Intent intent = getIntent();
        boolean fromHomeScreen = intent != null
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
        if (!fromHomeScreen || !Settings.directStart(this) || Settings.runUnfinished(this)) {
            return false;
        }
        startActivity(new Intent(this, ClockActivity.class));
        finish();
        return true;
    }

    /** A full-width action in the accent, for the one thing a card invites you to do. */
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
