package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.reteclock.core.Bell;
import com.reteclock.core.Bells;
import com.reteclock.core.CivilTime;
import com.reteclock.core.SunClock;
import com.reteclock.core.SunPlaces;
import com.reteclock.core.SunTimes;

import java.text.DateFormatSymbols;
import java.util.List;

/**
 * *Sunset/sunrise settings* — where the sun is reckoned from, and the bells that follow it (issue
 * #55, RFC-0015).
 *
 * <p>The place is picked from a list, region then country then city, or typed as a latitude and a
 * longitude. Nothing is looked up: no network, no location permission. The next week's sunrise and
 * sunset are shown for the place, in the clock's own time, so a wrong place is visible before a bell
 * rings at the wrong moment.
 *
 * <p>A bell that follows the sun is still a bell: it is made here or on *Sounds and bells*, edited on
 * *Sounds and bells*, and ticked on *Alarms (experimental)* to wake the phone like any other.
 */
public class SunSettingsActivity extends Activity {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int WARNING = 0xFFFFB300;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int DIVIDER = 0xFF272727;
    private static final int PRESSED = 0x334DB6AC;

    /** How many days of sunrise and sunset the page lists. */
    private static final int DAYS_SHOWN = 7;

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
        body.addView(title(getString(R.string.sun_title)));
        body.addView(placeCard());
        body.addView(timesCard());
        body.addView(bellsCard());
    }

    // ---- the place ----------------------------------------------------------------------------

    private LinearLayout placeCard() {
        LinearLayout card = card(getString(R.string.sun_card_place));
        SunClock sun = Settings.sunClock(this);
        TextView where = new TextView(this);
        where.setTextColor(sun.isSet() ? TEXT_WHITE : WARNING);
        where.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        if (sun.isSet()) {
            String name = Settings.sunPlace(this);
            String coordinates = coordinates(sun.latitude, sun.longitude);
            where.setText(name.isEmpty() ? coordinates : name + "\n" + coordinates);
        } else {
            where.setText(R.string.sun_no_place);
        }
        card.addView(where);
        card.addView(actionButton(getString(R.string.sun_pick_city), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickRegion();
            }
        }));
        card.addView(actionButton(getString(R.string.sun_type_coordinates),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        typeCoordinates();
                    }
                }));
        card.addView(footer(getString(R.string.sun_place_note)));
        return card;
    }

    private static String coordinates(double lat, double lon) {
        return String.format(java.util.Locale.US, "%.2f°%s  %.2f°%s", Math.abs(lat),
                lat >= 0 ? "N" : "S", Math.abs(lon), lon >= 0 ? "E" : "W");
    }

    private void pickRegion() {
        final List<String> regions = SunPlaces.regions();
        new AlertDialog.Builder(this)
                .setTitle(R.string.sun_pick_region)
                .setItems(regions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pickCountry(regions.get(which));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickCountry(final String region) {
        final List<String> countries = SunPlaces.countries(region);
        new AlertDialog.Builder(this)
                .setTitle(region)
                .setItems(countries.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pickCity(region, countries.get(which));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickCity(String region, String country) {
        final List<SunPlaces.Place> cities = SunPlaces.cities(region, country);
        String[] names = new String[cities.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = cities.get(i).city;
        }
        new AlertDialog.Builder(this)
                .setTitle(country)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SunPlaces.Place place = cities.get(which);
                        Settings.setSunPlace(SunSettingsActivity.this, place.latitude,
                                place.longitude, place.name());
                        rebuild();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void typeCoordinates() {
        SunClock sun = Settings.sunClock(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final EditText lat = coordinateField(sun.isSet() ? Double.toString(sun.latitude) : "");
        final EditText lon = coordinateField(sun.isSet() ? Double.toString(sun.longitude) : "");
        box.addView(footer(getString(R.string.sun_latitude)));
        box.addView(lat);
        box.addView(footer(getString(R.string.sun_longitude)));
        box.addView(lon);
        box.addView(footer(getString(R.string.sun_coordinates_note)));
        new AlertDialog.Builder(this)
                .setTitle(R.string.sun_type_coordinates)
                .setView(box)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        double a = number(lat.getText().toString());
                        double o = number(lon.getText().toString());
                        if (!SunClock.validLatitude(a) || !SunClock.validLongitude(o)) {
                            Toast.makeText(SunSettingsActivity.this, R.string.sun_coordinates_bad,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        Settings.setSunPlace(SunSettingsActivity.this, a, o, "");
                        rebuild();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private EditText coordinateField(String value) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        field.setText(value);
        return field;
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ---- the week's times -----------------------------------------------------------------------

    private LinearLayout timesCard() {
        LinearLayout card = card(getString(R.string.sun_card_times));
        SunClock sun = Settings.sunClock(this);
        if (!sun.isSet()) {
            card.addView(footer(getString(R.string.sun_times_need_place)));
            return card;
        }
        long now = System.currentTimeMillis();
        int today = CivilTime.jdnOf(now, Settings.offsetMinutes(this, now));
        String[] weekdays = new DateFormatSymbols().getShortWeekdays();
        for (int d = today; d < today + DAYS_SHOWN; d++) {
            int rise = sun.localMinute(d, SunTimes.SUNRISE);
            int set = sun.localMinute(d, SunTimes.SUNSET);
            TextView row = new TextView(this);
            row.setTextColor(d == today ? TEXT_WHITE : TEXT_DIM);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            row.setText(weekdays[CivilTime.weekday(d) + 1] + " "
                    + com.reteclock.core.Gregorian.day(d) + "    "
                    + getString(R.string.sun_bell_sunrise) + " " + time(rise) + "    "
                    + getString(R.string.sun_bell_sunset) + " " + time(set));
            row.setPadding(0, dp(3), 0, dp(3));
            card.addView(row);
        }
        card.addView(footer(getString(R.string.sun_times_note)));
        return card;
    }

    private String time(int minute) {
        return minute == SunTimes.NONE ? "—" : BellTime.clock(minute);
    }

    // ---- the bells ----------------------------------------------------------------------------

    private LinearLayout bellsCard() {
        LinearLayout card = card(getString(R.string.sun_card_bells));
        Bells bells = Settings.bells(this);
        boolean any = false;
        for (Bell bell : bells.list()) {
            if (!bell.followsSun()) {
                continue;
            }
            any = true;
            TextView row = new TextView(this);
            row.setTextColor(bell.isLive() ? TEXT_WHITE : TEXT_DIM);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            row.setText(BellTime.today(this, bell)
                    + (bell.label.isEmpty() ? "" : "  ·  " + bell.label));
            row.setPadding(0, dp(4), 0, dp(4));
            card.addView(row);
            card.addView(divider());
        }
        if (!any) {
            card.addView(footer(getString(R.string.sun_bells_none)));
        }
        card.addView(actionButton(getString(R.string.sun_add_sunrise), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add(Bell.AT_SUNRISE);
            }
        }));
        card.addView(actionButton(getString(R.string.sun_add_sunset), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add(Bell.AT_SUNSET);
            }
        }));
        card.addView(actionButton(getString(R.string.alarm_open_sounds), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SunSettingsActivity.this, SoundSettingsActivity.class));
            }
        }));
        card.addView(footer(getString(R.string.sun_bells_note)));
        return card;
    }

    /** A new bell at sunrise or sunset, every day, on the chime: edited on *Sounds and bells*. */
    private void add(int event) {
        Bell bell = Bell.atHour(7).withSun(event, 0).withLabel(getString(
                event == Bell.AT_SUNRISE ? R.string.sun_bell_sunrise : R.string.sun_bell_sunset));
        Settings.setBells(this, Settings.bells(this).with(bell));
        Toast.makeText(this, R.string.sun_bell_added, Toast.LENGTH_SHORT).show();
        rebuild();
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
