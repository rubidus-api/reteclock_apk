package com.reteclock;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
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

import com.reteclock.core.ClockOptions;

/**
 * The settings screen, reached by long pressing the clock.
 *
 * The views are built in code rather than inflated from XML: the screen is small, and this keeps the
 * app free of layout resources and of any support library, down to API 9.
 */
public class SettingsActivity extends Activity {

    private static final int TEXT_WHITE = Color.WHITE;
    private static final int TEXT_DIM = Color.parseColor("#B0B0B0");

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
                getString(R.string.app_version), Build.VERSION.RELEASE)));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(root);
        setContentView(scroll);
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
