package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.reteclock.core.CalendarDate;
import com.reteclock.core.Calendars;
import com.reteclock.core.CivilTime;
import com.reteclock.core.CustomNames;
import com.reteclock.core.SpokenTemplate;

/**
 * *Speech settings* — everything the app says aloud, in one place.
 *
 * <p>What a tap on the clock says (issue #57), how it reads the time (issue #62), the user's own
 * sentence with fields in braces, the names months and weekdays are said by, and the voice — one
 * engine and one language for the time and for the timer's spoken messages alike (T111). Until
 * 0.50.0 the tap and the voice were a card on *General settings*; they moved here when the sentence
 * and the names gave them more than a card's worth to say.
 */
public class SpeechSettingsActivity extends Activity {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int WARNING = 0xFFFFB300;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int PRESSED = 0x334DB6AC;

    /** The reading styles as the page offers them: the two fixed ones, then the user's sentence. */
    private static final int CHOICE_OWN = 2;

    private LinearLayout body;
    private LinearLayout sentence;
    private LinearLayout names;
    private TextView preview;
    private TextView voiceButton;
    private TimerVoice trialVoice;

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

    @Override
    protected void onPause() {
        if (trialVoice != null) {
            trialVoice.release();
            trialVoice = null;
        }
        super.onPause();
    }

    private void rebuild() {
        body.removeAllViews();
        body.addView(title(getString(R.string.speech_title)));
        body.addView(tapCard());
        names = card(getString(R.string.speak_names_card));
        body.addView(names);
        rebuildNames();
        body.addView(voiceCard());
    }

    // ---- what a tap says --------------------------------------------------------------------

    private LinearLayout tapCard() {
        LinearLayout speak = card(getString(R.string.speak_card));
        speak.addView(footer(getString(R.string.speak_intro)));

        CheckBox say = new CheckBox(this);
        say.setText(R.string.speak_time);
        say.setTextColor(TEXT_WHITE);
        say.setChecked(Settings.speakTime(this));
        say.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setSpeakTime(SpeechSettingsActivity.this, checked);
            }
        });
        speak.addView(say);
        speak.addView(footer(getString(R.string.speak_time_note)));

        speak.addView(subheading(getString(R.string.speak_style)));
        int chosen = Settings.spokenTemplateOn(this) ? CHOICE_OWN : Settings.spokenTimeStyle(this);
        speak.addView(inlineChoice(
                new String[] {getString(R.string.speak_style_reading),
                    getString(R.string.speak_style_plain),
                    getString(R.string.speak_style_own)},
                chosen,
                new OnChoice() {
                    @Override
                    public void chose(int which) {
                        // The fixed style is kept underneath the sentence, so switching the
                        // sentence off again brings back exactly what was chosen before.
                        if (which == CHOICE_OWN) {
                            Settings.setSpokenTemplateOn(SpeechSettingsActivity.this, true);
                        } else {
                            Settings.setSpokenTemplateOn(SpeechSettingsActivity.this, false);
                            Settings.setSpokenTimeStyle(SpeechSettingsActivity.this, which);
                        }
                        sentence.setVisibility(which == CHOICE_OWN ? View.VISIBLE : View.GONE);
                        updatePreview();
                    }
                }));
        speak.addView(footer(getString(R.string.speak_style_note)));

        sentence = new LinearLayout(this);
        sentence.setOrientation(LinearLayout.VERTICAL);
        sentence.setVisibility(chosen == CHOICE_OWN ? View.VISIBLE : View.GONE);
        speak.addView(sentence);

        sentence.addView(subheading(getString(R.string.speak_own_heading)));
        final EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextColor(TEXT_WHITE);
        field.setHintTextColor(TEXT_DIM);
        field.setHint(SpokenTemplate.DEFAULT);
        field.setText(Settings.spokenTemplate(this));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Settings.setSpokenTemplate(SpeechSettingsActivity.this, s.toString());
                updatePreview();
            }
        });
        sentence.addView(field);
        preview = new TextView(this);
        preview.setTextColor(ACCENT);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        preview.setPadding(0, dp(4), 0, dp(4));
        sentence.addView(preview);
        sentence.addView(footer(getString(R.string.speak_own_fields)));
        sentence.addView(actionButton(getString(R.string.speak_own_default),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        field.setText("");
                    }
                }));
        sentence.addView(footer(getString(R.string.speak_own_note)));
        updatePreview();

        speak.addView(actionButton(getString(R.string.speak_try), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryVoice();
            }
        }));
        return speak;
    }

    /** What the sentence says at this moment, so a field is heard in the head before the ear. */
    private void updatePreview() {
        if (preview == null) {
            return;
        }
        preview.setText(getString(R.string.speak_own_preview,
                Settings.spokenSentence(this, Settings.spokenTemplate(this),
                        System.currentTimeMillis())));
    }

    private void tryVoice() {
        // A voice of its own, made afresh, so what is heard is the choice just made.
        if (trialVoice != null) {
            trialVoice.release();
        }
        trialVoice = new TimerVoice(this);
        trialVoice.say(Settings.spokenTimeNow(this), android.os.SystemClock.elapsedRealtime());
    }

    // ---- the names a month and a weekday are said by ----------------------------------------

    private void rebuildNames() {
        if (names == null) {
            return;
        }
        while (names.getChildCount() > 1) {
            names.removeViewAt(1);
        }
        final int system = Settings.calendarSystem(this);

        CheckBox own = new CheckBox(this);
        own.setText(R.string.speak_names_on);
        own.setTextColor(TEXT_WHITE);
        own.setChecked(Settings.spokenNamesOn(this));
        own.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                // Off keeps the names; it only stops them being said.
                Settings.setSpokenNamesOn(SpeechSettingsActivity.this, checked);
                updatePreview();
            }
        });
        names.addView(own);
        names.addView(footer(getString(R.string.speak_names_note, Calendars.name(system))));

        SpokenTemplate.Words words = Settings.spokenWords(this);
        CustomNames typed = Settings.spokenNames(this, system);
        CalendarDate today = Settings.spokenMoment(this, System.currentTimeMillis()).date;
        final int howMany = mostMonths(system, today.year);

        names.addView(subheading(getString(R.string.names_months)));
        FlowLayout monthRow = new FlowLayout(this, dp(4), dp(2));
        for (int month = 1; month <= howMany; month++) {
            String built = builtMonth(system, today.year, month, words);
            monthRow.addView(nameButton(built, typed.monthEntry(month), true, month, howMany));
        }
        names.addView(monthRow);

        names.addView(subheading(getString(R.string.names_weekdays)));
        FlowLayout weekdayRow = new FlowLayout(this, dp(4), dp(2));
        for (int day = 0; day < 7; day++) {
            weekdayRow.addView(nameButton(SpokenTemplate.builtWeekdayName(day, words),
                    typed.weekdayEntry(day), false, day, howMany));
        }
        names.addView(weekdayRow);

        names.addView(actionButton(getString(R.string.speak_names_clear),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmClearNames(system);
                    }
                }));
    }

    /** The built-in spoken name of a month in this calendar, or its number past the year's end. */
    private static String builtMonth(int system, int year, int month,
            SpokenTemplate.Words words) {
        int inYear = Calendars.monthsInYear(system, year);
        int shownYear = year;
        if (month > inYear) {
            // A leap month this year does not have: name it from the next year that does.
            for (int y = year + 1; y < year + 19; y++) {
                if (Calendars.monthsInYear(system, y) >= month) {
                    shownYear = y;
                    break;
                }
            }
        }
        if (Calendars.monthsInYear(system, shownYear) < month) {
            return Integer.toString(month);
        }
        CalendarDate date = new CalendarDate(system, shownYear, month, 1,
                Calendars.monthsInYear(system, shownYear), false);
        return SpokenTemplate.builtMonthName(date, words);
    }

    /** As many months as the calendar ever has: a Hebrew or lunisolar year may have thirteen. */
    private static int mostMonths(int system, int year) {
        int most = 12;
        for (int y = year; y < year + 19; y++) {
            int count = Calendars.monthsInYear(system, y);
            if (count > most) {
                most = count;
            }
        }
        return most;
    }

    private View nameButton(final String built, String typed, final boolean isMonth,
            final int index, final int howMany) {
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
        CustomNames typed = Settings.spokenNames(this, system);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(built);
        input.setText(isMonth ? typed.monthEntry(index) : typed.weekdayEntry(index));
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(built)
                .setMessage(R.string.speak_names_blank)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CustomNames now = Settings.spokenNames(SpeechSettingsActivity.this,
                                system);
                        String name = input.getText().toString().trim();
                        Settings.setSpokenNames(SpeechSettingsActivity.this, system,
                                isMonth ? now.withMonth(index, name, howMany)
                                        : now.withWeekday(index, name));
                        rebuildNames();
                        updatePreview();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClearNames(final int system) {
        if (Settings.spokenNames(this, system).isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.speak_names_clear_ask, Calendars.name(system)))
                .setPositiveButton(R.string.speak_names_clear_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Settings.setSpokenNames(SpeechSettingsActivity.this, system,
                                        CustomNames.NONE);
                                rebuildNames();
                                updatePreview();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- the voice --------------------------------------------------------------------------

    private LinearLayout voiceCard() {
        LinearLayout card = card(getString(R.string.speak_voice_card));
        card.addView(footer(getString(R.string.speak_voice_note)));
        int state = Settings.voiceSummary(this);
        if (state == com.reteclock.core.VoiceState.ABSENT
                || state == com.reteclock.core.VoiceState.NO_VOICE) {
            TextView warning = footer(getString(state == com.reteclock.core.VoiceState.ABSENT
                    ? R.string.timer_no_speech : R.string.timer_no_voice));
            warning.setTextColor(WARNING);
            card.addView(warning);
        }
        voiceButton = actionButton(voiceText(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseVoice();
            }
        });
        card.addView(voiceButton);
        card.addView(actionButton(getString(R.string.speak_try), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryVoice();
            }
        }));
        return card;
    }

    /** "Voice · German (Germany) · de-DE · Pico TTS", or the phone's default voice. */
    private String voiceText() {
        String pkg = Settings.ttsEngine(this);
        String tag = Settings.ttsLanguage(this);
        if (pkg.isEmpty() && tag.isEmpty()) {
            return getString(R.string.speak_voice_button, getString(R.string.speak_voice_default));
        }
        String language = tag.isEmpty() ? getString(R.string.speak_language_default)
                : VoiceChoices.languageLabel(tag);
        String engine = pkg.isEmpty() ? getString(R.string.speak_engine_default)
                : VoiceChoices.engineLabel(this, pkg);
        return getString(R.string.speak_voice_button, language + "  ·  " + engine);
    }

    /** Every installed voice in one list, the phone's default first. */
    private void chooseVoice() {
        voiceButton.setText(R.string.speak_voice_asking);
        voiceButton.setEnabled(false);
        VoiceChoices.catalogue(this, new VoiceChoices.Catalogue() {
            @Override
            public void found(final java.util.List<com.reteclock.core.VoiceOptions.Option> rows,
                    String defaultEngine) {
                voiceButton.setEnabled(true);
                voiceButton.setText(voiceText());
                if (isFinishing()) {
                    return;
                }
                String[] labels = new String[rows.size() + 1];
                labels[0] = getString(R.string.speak_voice_default);
                for (int i = 0; i < rows.size(); i++) {
                    com.reteclock.core.VoiceOptions.Option o = rows.get(i);
                    labels[i + 1] = o.engineLabel.isEmpty()
                            ? VoiceChoices.languageLabel(o.tag)
                            : VoiceChoices.languageLabel(o.tag) + "  ·  " + o.engineLabel;
                }
                int at = com.reteclock.core.VoiceOptions.chosen(rows,
                        Settings.ttsEngine(SpeechSettingsActivity.this),
                        Settings.ttsLanguage(SpeechSettingsActivity.this), defaultEngine);
                new AlertDialog.Builder(SpeechSettingsActivity.this)
                        .setTitle(rows.isEmpty() ? getString(R.string.speak_voice_none)
                                : getString(R.string.speak_voice_title))
                        .setSingleChoiceItems(labels, at < 0 ? -1 : at + 1,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (which == 0) {
                                            Settings.setTtsEngine(SpeechSettingsActivity.this, "");
                                            Settings.setTtsLanguage(SpeechSettingsActivity.this,
                                                    "");
                                        } else {
                                            com.reteclock.core.VoiceOptions.Option o =
                                                    rows.get(which - 1);
                                            Settings.setTtsEngine(SpeechSettingsActivity.this,
                                                    o.engine);
                                            Settings.setTtsLanguage(SpeechSettingsActivity.this,
                                                    o.tag);
                                        }
                                        dialog.dismiss();
                                        // The built-in names are the voice's language: redraw.
                                        rebuild();
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
    }

    // ---- the look, as the other settings pages have it --------------------------------------

    /** Told which of a short list of alternatives was picked. */
    private interface OnChoice {
        void chose(int which);
    }

    /** A short list of alternatives, side by side, wrapping when they run out of room. */
    private View inlineChoice(final String[] labels, int chosen, final OnChoice listener) {
        FlowLayout row = new FlowLayout(this, dp(4), dp(2));
        final RadioButton[] buttons = new RadioButton[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            RadioButton option = new RadioButton(this);
            option.setText(labels[i]);
            option.setTextColor(TEXT_WHITE);
            option.setChecked(i == chosen);
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int j = 0; j < buttons.length; j++) {
                        buttons[j].setChecked(j == which);
                    }
                    listener.chose(which);
                }
            });
            buttons[i] = option;
            row.addView(option);
        }
        return row;
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
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setPadding(0, dp(12), 0, dp(2));
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
