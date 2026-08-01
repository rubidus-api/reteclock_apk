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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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

    /** Rebuilt in place whenever a font is added, chosen or deleted. */
    private LinearLayout fontSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        rebuildFontSection();

        Button add = new Button(this);
        add.setText(R.string.settings_font_add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFont();
            }
        });
        root.addView(add);

        root.addView(heading(getString(R.string.settings_decoration)));
        root.addView(decoration(R.string.settings_bold, Settings.bold(this), new Setter() {
            @Override
            public void set(boolean checked) {
                Settings.setBold(SettingsActivity.this, checked);
            }
        }));
        root.addView(decoration(R.string.settings_italic, Settings.italic(this), new Setter() {
            @Override
            public void set(boolean checked) {
                Settings.setItalic(SettingsActivity.this, checked);
            }
        }));
        root.addView(decoration(R.string.settings_underline, Settings.underline(this), new Setter() {
            @Override
            public void set(boolean checked) {
                Settings.setUnderline(SettingsActivity.this, checked);
            }
        }));

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

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(root);
        setContentView(scroll);
    }


    /** A decoration toggle. They are independent, so a checkbox each rather than a group. */
    private interface Setter {
        void set(boolean checked);
    }

    private CheckBox decoration(int label, boolean checked, final Setter setter) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(TEXT_WHITE);
        box.setChecked(checked);
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setter.set(isChecked);
            }
        });
        return box;
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
        String chosen = Settings.fontName(this);
        final List<RadioButton> buttons = new ArrayList<RadioButton>();

        fontSection.addView(fontRow(buttons, null, getString(R.string.settings_font_system),
                chosen.isEmpty()));
        for (FontLibrary.Entry entry : entries) {
            String label = entry.name + "  \u00b7  " + FontLibrary.humanBytes(entry.bytes);
            fontSection.addView(fontRow(buttons, entry.name, label, entry.name.equals(chosen)));
        }

        TextView total = footer(entries.isEmpty()
                ? getString(R.string.settings_font_none)
                : getString(R.string.settings_font_total, entries.size(),
                        FontLibrary.humanBytes(library.totalBytes())));
        total.setPadding(0, dp(6), 0, 0);
        fontSection.addView(total);
    }

    /** One row: a radio that selects the font, and for an imported one, a button that deletes it. */
    private View fontRow(final List<RadioButton> buttons, final String name, String label,
            boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        final RadioButton radio = new RadioButton(this);
        radio.setText(label);
        radio.setTextColor(TEXT_WHITE);
        radio.setChecked(selected);
        radio.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        radio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (RadioButton other : buttons) {
                    other.setChecked(other == radio);
                }
                Settings.setFontName(SettingsActivity.this, name == null ? "" : name);
            }
        });
        buttons.add(radio);
        row.addView(radio);

        if (name != null) {
            Button delete = new Button(this);
            delete.setText(R.string.settings_font_delete);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.fonts(SettingsActivity.this).delete(name);
                    // Deleting the font in use falls back to the system font rather than leaving
                    // the clock with nothing to draw with.
                    if (name.equals(Settings.fontName(SettingsActivity.this))) {
                        Settings.setFontName(SettingsActivity.this, "");
                    }
                    rebuildFontSection();
                }
            });
            row.addView(delete);
        }
        return row;
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
        if (requestCode != REQUEST_PICK_FONT || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        importFont(data.getData());
    }

    private void importFont(Uri uri) {
        byte[] content;
        try {
            content = readAll(uri);
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

        Settings.setFontName(this, stored);
        rebuildFontSection();
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

    private byte[] readAll(Uri uri) throws IOException {
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
                if (total > MAX_FONT_BYTES) {
                    throw new IOException("font larger than " + MAX_FONT_BYTES + " bytes");
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
