package com.reteclock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.SunTimes;

/**
 * *Religious settings* — the conventions a religious timetable is read by, gathered in one place
 * (issue #56, the owner's suggestion, renamed at his second one).
 *
 * <p>Not one faith's page. The three decisions here are the ones an Islamic timetable needs, and
 * they are the shape of several others too: Jewish zmanim divide the day the same way and split on
 * which school is followed, the canonical hours were seasonal hours of a divided day, Ethiopia
 * counts its hours from sunrise, and a Hindu brahma muhurta is a fixed while before it. What this
 * page holds is arithmetic anybody can point at; which of it to follow is nobody's business here.
 *
 * <p>The app computes the sun and nothing else, and calls each moment what it is: dawn, sunrise,
 * solar noon, the afternoon shadow, sunset, dusk, the night's middle. Reading those moments as a
 * timetable takes three decisions that recognised authorities answer differently — how far below
 * the horizon dawn and dusk are taken to be, whether the afternoon shadow is once or twice a
 * thing's height, and what to do in the far north where the sun never sinks that far. Those are the
 * user's to make, and this is where they are made. Nothing here names a prayer, and the app
 * publishes no timetable: a quietly wrong answer would cost the person praying, not the app.
 */
public class ReligiousSettingsActivity extends Activity {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int WARNING = 0xFFFFB300;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int DIVIDER = 0xFF272727;
    private static final int PRESSED = 0x334DB6AC;

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
        rebuild();
    }

    private void rebuild() {
        body.removeAllViews();
        body.addView(title(getString(R.string.religious_title)));
        body.addView(footer(getString(R.string.religious_intro)));
        body.addView(methodCard());
        body.addView(reckoningCard());
        body.addView(elsewhereCard());
    }

    /**
     * The published sets of numbers, and the numbers the chosen one fills in (issue #56, the
     * owner's request for presets).
     *
     * Choosing a set is a shortcut, not a ruling: the numbers it sets are written underneath in
     * plain sight, and changing any of them below makes the choice Custom again.
     */
    private LinearLayout methodCard() {
        LinearLayout card = card(getString(R.string.religious_card_method));
        final com.reteclock.core.SunRules rules = Settings.sunRules(this);
        // What the user chose, not what the numbers happen to match: the defaults are Karachi's
        // numbers, and a clock nobody has touched must not claim to follow anybody.
        int chosen = Settings.sunMethod(this);
        FlowLayout sets = new FlowLayout(this, dp(4), dp(2));
        for (com.reteclock.core.SunMethods.Method method
                : com.reteclock.core.SunMethods.all()) {
            final com.reteclock.core.SunMethods.Method chosenOne = method;
            String label = method.id == com.reteclock.core.SunMethods.CUSTOM
                    ? getString(R.string.religious_method_custom) : method.name;
            sets.addView(chip(label, method.id == chosen, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (chosenOne.id == com.reteclock.core.SunMethods.CUSTOM) {
                        // Custom is what the numbers already are: nothing to set, nothing to undo.
                        return;
                    }
                    // The shadow and the far-north answer are the user's own and are carried over:
                    // a published set says nothing about either.
                    Settings.setSunRules(ReligiousSettingsActivity.this,
                            chosenOne.rules.withShadow(rules.shadowMultiple)
                                    .withHighRule(rules.highRule), chosenOne.id);
                    rebuild();
                }
            }));
        }
        card.addView(sets);
        card.addView(numbersLine(rules));
        card.addView(footer(getString(R.string.religious_method_note)));

        final CheckBox names = new CheckBox(this);
        names.setText(R.string.religious_show_names);
        names.setTextColor(TEXT_WHITE);
        names.setChecked(Settings.sunShowNames(this));
        names.setOnCheckedChangeListener(new android.widget.CompoundButton
                .OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                Settings.setSunShowNames(ReligiousSettingsActivity.this, checked);
                rebuild();
            }
        });
        card.addView(names);
        card.addView(footer(getString(R.string.religious_show_names_note)));
        return card;
    }

    /** The numbers in force, written out, whichever set they came from. */
    private TextView numbersLine(com.reteclock.core.SunRules rules) {
        String dusk = rules.duskByInterval()
                ? getString(R.string.religious_dusk_interval, rules.duskMinutesAfterEvening)
                : getString(R.string.religious_dusk_angle,
                        com.reteclock.core.SunRules.degrees(rules.duskTenths));
        String shadow = getString(rules.shadowMultiple == 2
                ? R.string.sun_shadow_two : R.string.sun_shadow_one);
        String nightEnd = getString(rules.nightEndsAtDawn
                ? R.string.religious_night_dawn : R.string.religious_night_sunrise);
        TextView line = new TextView(this);
        line.setText(getString(R.string.religious_numbers,
                com.reteclock.core.SunRules.degrees(rules.dawnTenths), dusk, shadow, nightEnd));
        line.setTextColor(TEXT_WHITE);
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        line.setPadding(0, dp(6), 0, dp(2));
        return line;
    }

    private LinearLayout reckoningCard() {
        LinearLayout card = card(getString(R.string.religious_card_reckoning));

        card.addView(subheading(getString(R.string.sun_twilight_heading)));
        FlowLayout angles = new FlowLayout(this, dp(4), dp(2));
        // A set's angle may be half a degree, which no quick chip can show: then none is lit and
        // the numbers line above is what says where dawn is.
        int dawnTenths = Settings.sunRules(this).dawnTenths;
        int chosenAngle = dawnTenths % 10 == 0 ? dawnTenths / 10 : -1;
        for (int degrees : SunTimes.TWILIGHT_CHOICES) {
            final int value = degrees;
            angles.addView(chip(degrees + "\u00B0", degrees == chosenAngle,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Changing an angle by hand is what makes the set Custom.
                            com.reteclock.core.SunRules now = Settings.sunRules(
                                    ReligiousSettingsActivity.this);
                            Settings.setSunRules(ReligiousSettingsActivity.this,
                                    new com.reteclock.core.SunRules(value * 10,
                                            now.duskByInterval() ? 0 : value * 10,
                                            now.duskMinutesAfterEvening, now.eveningTenths,
                                            now.shadowMultiple, now.nightEndsAtDawn,
                                            now.highRule));
                            rebuild();
                        }
                    }));
        }
        if (chosenAngle < 0) {
            // A set's angle can be half a degree, which no quick chip carries — 19.5° for Egypt,
            // 17.7° for Tehran. Rather than leave the row with nothing lit, as if no angle were
            // set at all, the angle in force gets a chip of its own at the end. Pressing it changes
            // nothing: it is already what is in force.
            angles.addView(chip(getString(R.string.religious_dusk_angle,
                    com.reteclock.core.SunRules.degrees(dawnTenths)), true,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                        }
                    }));
        }
        card.addView(angles);
        card.addView(footer(getString(R.string.sun_twilight_note)));
        card.addView(divider());

        // Where the evening is taken, which two of the published sets put below the horizon.
        card.addView(subheading(getString(R.string.religious_evening_heading)));
        com.reteclock.core.SunRules rules = Settings.sunRules(this);
        TextView evening = footer(rules.eveningIsSunset()
                ? getString(R.string.religious_evening_sunset)
                : getString(R.string.religious_evening_angle,
                        com.reteclock.core.SunRules.degrees(rules.eveningTenths)));
        evening.setTextColor(TEXT_WHITE);
        card.addView(evening);
        card.addView(divider());

        card.addView(subheading(getString(R.string.sun_shadow_heading)));
        FlowLayout shadows = new FlowLayout(this, dp(4), dp(2));
        int shadow = Settings.sunShadow(this);
        int[] labels = {R.string.sun_shadow_one, R.string.sun_shadow_two};
        for (int i = 0; i < SunTimes.SHADOW_CHOICES.length; i++) {
            final int multiple = SunTimes.SHADOW_CHOICES[i];
            shadows.addView(chip(getString(labels[i]), multiple == shadow,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Settings.setSunShadow(ReligiousSettingsActivity.this, multiple);
                            rebuild();
                        }
                    }));
        }
        card.addView(shadows);
        card.addView(footer(getString(R.string.sun_shadow_note)));
        card.addView(divider());

        card.addView(subheading(getString(R.string.sun_high_heading)));
        FlowLayout ruleChips = new FlowLayout(this, dp(4), dp(2));
        int rule = Settings.sunHighRule(this);
        int[] ruleLabels = {R.string.sun_high_nothing, R.string.sun_high_middle,
            R.string.sun_high_seventh, R.string.sun_high_share, R.string.sun_high_nearest};
        for (int i = 0; i < SunTimes.HIGH_CHOICES.length; i++) {
            final int value = SunTimes.HIGH_CHOICES[i];
            ruleChips.addView(chip(getString(ruleLabels[i]), value == rule, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.setSunHighRule(ReligiousSettingsActivity.this, value);
                    rebuild();
                }
            }));
        }
        card.addView(ruleChips);
        TextView note = footer(getString(R.string.sun_high_note));
        note.setTextColor(WARNING);
        card.addView(note);
        return card;
    }

    private LinearLayout elsewhereCard() {
        LinearLayout card = card(getString(R.string.religious_card_elsewhere));
        card.addView(footer(getString(R.string.religious_elsewhere_note)));
        card.addView(actionButton(getString(R.string.religious_open_sun), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ReligiousSettingsActivity.this, SunSettingsActivity.class));
            }
        }));
        card.addView(actionButton(getString(R.string.religious_open_timedate),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(ReligiousSettingsActivity.this,
                                TimeDateSettingsActivity.class));
                    }
                }));
        return card;
    }

    /** One choice among several, lit when it is the one in force. */
    private TextView chip(String label, boolean chosen, View.OnClickListener onClick) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextColor(chosen ? Color.BLACK : TEXT_DIM);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable face = new GradientDrawable();
        face.setColor(chosen ? ACCENT : 0xFF212121);
        face.setCornerRadius(dp(6));
        chip.setBackgroundDrawable(face);
        Focusable.make(chip);
        chip.setOnClickListener(onClick);
        return chip;
    }

    private TextView subheading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setPadding(0, dp(8), 0, dp(4));
        return view;
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
