package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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

import com.reteclock.core.layout.LayoutBook;
import com.reteclock.core.layout.LayoutSlides;
import com.reteclock.core.layout.LayoutSwipe;

import java.util.List;
import java.util.Set;

/**
 * *Layout slides* — layouts played in turn, each for its own time (issue #53, RFC-0013).
 *
 * <p>A page of its own, as the owner asked. Two cards, one for each way up, because a layout
 * belongs to one of the two shelves: a switch, the rows in the order they play, and a way to add
 * one. Each row names a layout, how long it stays, and whether its slide shows a background — the
 * reporter's case is a calendar that cannot be read over a photograph.
 *
 * <p>Every change is saved at once and starts the show from its first row.
 *
 * <p>Each way up takes one of three turns: none, by swiping, or timed (issue #70, R144; owner,
 * 2026-10-16). Swiping has its list of its own — layouts only, in the order a swipe goes through
 * them — and each list is kept while the other turns are chosen.
 */
public class LayoutSlidesActivity extends WebActivity {

    private static final int BACKDROP = 0xFF101010;
    private static final int CARD = 0xFF1C1C1C;
    private static final int ROW = 0xFF242424;
    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int DISABLED = 0xFF4A4A4A;
    private static final int PRESSED = 0x334DB6AC;

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKDROP);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Layouts may have been added, renamed or deleted on the Layout page meanwhile.
        rebuild();
    }

    private void rebuild() {
        root.removeAllViews();
        root.addView(heading(getString(R.string.slides_title)));
        root.addView(note(getString(R.string.slides_intro)));
        LayoutBook book = Settings.layouts(this);
        LayoutSlides slides = Settings.slides(this);
        root.addView(shelfCard(false, book, slides));
        root.addView(shelfCard(true, book, slides));
        root.addView(button(getString(R.string.slides_open_layouts), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LayoutSlidesActivity.this, LayoutSettingsActivity.class));
            }
        }));
    }

    private LinearLayout shelfCard(final boolean sideways, final LayoutBook book,
            final LayoutSlides slides) {
        LinearLayout card = card();
        card.addView(subheading(getString(sideways
                ? R.string.layout_sideways_list : R.string.layout_upright_list)));

        final LayoutSwipe swipe = Settings.swipe(this);
        final int turns = LayoutSwipe.turns(slides, swipe, sideways);
        RadioGroup choice = new RadioGroup(this);
        choice.setOrientation(RadioGroup.VERTICAL);
        final int[] labels = {R.string.turns_none, R.string.turns_swipe, R.string.turns_timed};
        final int[] values = {LayoutSwipe.TURNS_NONE, LayoutSwipe.TURNS_SWIPE, LayoutSwipe.TURNS_TIMED};
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(0x7101 + i);
            option.setText(labels[i]);
            option.setTextColor(TEXT_WHITE);
            choice.addView(option);
            if (values[i] == turns) {
                option.setChecked(true);
            }
        }
        choice.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int id) {
                int chosen = values[id - 0x7101];
                Settings.setSwipe(LayoutSlidesActivity.this, LayoutSwipe.swipeFor(
                        Settings.swipe(LayoutSlidesActivity.this), sideways, chosen));
                save(LayoutSwipe.slidesFor(Settings.slides(LayoutSlidesActivity.this), sideways,
                        chosen));
            }
        });
        card.addView(choice);

        if (turns == LayoutSwipe.TURNS_NONE) {
            card.addView(note(getString(R.string.turns_none_note)));
            return card;
        }
        if (turns == LayoutSwipe.TURNS_SWIPE) {
            swipeRows(card, sideways, book, swipe);
            return card;
        }

        final Set<String> shelf = Settings.shelfNames(book, sideways);
        List<LayoutSlides.Slide> rows = slides.rows(sideways);
        if (rows.isEmpty()) {
            card.addView(note(getString(R.string.slides_empty)));
        }
        int inForce = slides.currentRow(sideways, System.currentTimeMillis(), shelf);
        for (int i = 0; i < rows.size(); i++) {
            card.addView(row(sideways, i, rows.size(), rows.get(i), shelf.contains(rows.get(i).layout),
                    i == inForce));
        }
        if (slides.isOn(sideways) && inForce < 0) {
            card.addView(note(getString(R.string.slides_nothing_plays)));
        }
        card.addView(button(getString(R.string.slides_add), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickLayout(sideways, book);
            }
        }));
        return card;
    }

    /** The swipe list: layouts in the order a swipe goes through them, and a way to add one. */
    private void swipeRows(LinearLayout card, final boolean sideways, final LayoutBook book,
            LayoutSwipe swipe) {
        card.addView(note(getString(R.string.turns_swipe_note)));
        Set<String> shelf = Settings.shelfNames(book, sideways);
        final List<String> rows = swipe.rows(sideways);
        if (rows.isEmpty()) {
            card.addView(note(getString(R.string.slides_empty)));
        }
        String chosen = book.chosen(sideways).name;
        for (int i = 0; i < rows.size(); i++) {
            final int index = i;
            boolean exists = shelf.contains(rows.get(i));
            boolean inForce = rows.get(i).equals(chosen);
            LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable face = new GradientDrawable();
            face.setColor(ROW);
            face.setCornerRadius(dp(6));
            if (inForce) {
                face.setStroke(dp(1), ACCENT);
            }
            entry.setBackgroundDrawable(face);
            entry.setPadding(dp(8), dp(6), dp(8), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(6);
            entry.setLayoutParams(params);
            TextView name = new TextView(this);
            name.setText((i + 1) + ".  " + rows.get(i)
                    + (exists ? "" : "  —  " + getString(R.string.slides_missing))
                    + (inForce ? "  ·  " + getString(R.string.slides_now) : ""));
            name.setTextColor(exists ? (inForce ? ACCENT : TEXT_WHITE) : TEXT_DIM);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            entry.addView(name);
            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.addView(small("▲", i > 0, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveSwipe(Settings.swipe(LayoutSlidesActivity.this).moved(sideways, index, -1));
                }
            }));
            buttons.addView(small("▼", i < rows.size() - 1, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveSwipe(Settings.swipe(LayoutSlidesActivity.this).moved(sideways, index, 1));
                }
            }));
            buttons.addView(small(getString(R.string.slides_remove), true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveSwipe(Settings.swipe(LayoutSlidesActivity.this).removed(sideways, index));
                }
            }));
            entry.addView(buttons);
            card.addView(entry);
        }
        if (rows.size() == 1) {
            card.addView(note(getString(R.string.turns_swipe_one)));
        }
        final java.util.ArrayList<String> left = new java.util.ArrayList<String>();
        for (int i = 0; i < book.size(sideways); i++) {
            if (!rows.contains(book.get(sideways, i).name)) {
                left.add(book.get(sideways, i).name);
            }
        }
        card.addView(button(getString(R.string.slides_add), !left.isEmpty(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String[] names = left.toArray(new String[0]);
                new AlertDialog.Builder(LayoutSlidesActivity.this)
                        .setTitle(R.string.slides_add)
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                saveSwipe(Settings.swipe(LayoutSlidesActivity.this).add(sideways,
                                        names[which]));
                            }
                        })
                        .show();
            }
        }));
    }

    private void saveSwipe(LayoutSwipe swipe) {
        Settings.setSwipe(this, swipe);
        rebuild();
    }

    private View row(final boolean sideways, final int index, int count,
            final LayoutSlides.Slide slide, boolean exists, boolean inForce) {
        LinearLayout entry = new LinearLayout(this);
        entry.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable face = new GradientDrawable();
        face.setColor(ROW);
        face.setCornerRadius(dp(6));
        if (inForce) {
            face.setStroke(dp(1), ACCENT);
        }
        entry.setBackgroundDrawable(face);
        entry.setPadding(dp(8), dp(6), dp(8), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        entry.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setText((index + 1) + ".  " + slide.layout
                + (exists ? "" : "  —  " + getString(R.string.slides_missing)));
        name.setTextColor(exists ? TEXT_WHITE : TEXT_DIM);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        entry.addView(name);

        TextView length = new TextView(this);
        length.setText(duration(slide.seconds) + (inForce ? "  ·  " + getString(R.string.slides_now) : ""));
        length.setTextColor(inForce ? ACCENT : TEXT_DIM);
        length.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        entry.addView(length);

        CheckBox background = new CheckBox(this);
        background.setText(R.string.slides_background);
        background.setTextColor(TEXT_WHITE);
        background.setChecked(slide.background);
        background.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                LayoutSlides now = Settings.slides(LayoutSlidesActivity.this);
                save(now.replaced(sideways, index, slide.withBackground(checked)));
            }
        });
        entry.addView(background);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(small(getString(R.string.slides_time), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askTime(sideways, index, slide);
            }
        }));
        buttons.addView(small("▲", index > 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save(Settings.slides(LayoutSlidesActivity.this).moved(sideways, index, -1));
            }
        }));
        buttons.addView(small("▼", index < count - 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save(Settings.slides(LayoutSlidesActivity.this).moved(sideways, index, 1));
            }
        }));
        buttons.addView(small(getString(R.string.slides_remove), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save(Settings.slides(LayoutSlidesActivity.this).removed(sideways, index));
            }
        }));
        entry.addView(buttons);
        return entry;
    }

    private void pickLayout(final boolean sideways, final LayoutBook book) {
        final String[] names = new String[book.size(sideways)];
        for (int i = 0; i < names.length; i++) {
            names[i] = book.get(sideways, i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.slides_add)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        save(Settings.slides(LayoutSlidesActivity.this).add(sideways,
                                LayoutSlides.Slide.of(names[which], LayoutSlides.DEFAULT_SECONDS,
                                        true)));
                    }
                })
                .show();
    }

    /** Minutes and seconds, two fields, because "300" is not how anybody thinks of five minutes. */
    private void askTime(final boolean sideways, final int index, final LayoutSlides.Slide slide) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setPadding(dp(16), dp(8), dp(16), 0);
        final EditText minutes = number(String.valueOf(slide.seconds / 60));
        final EditText seconds = number(String.valueOf(slide.seconds % 60));
        fields.addView(minutes);
        fields.addView(label(getString(R.string.slides_minutes)));
        fields.addView(seconds);
        fields.addView(label(getString(R.string.slides_seconds)));
        new AlertDialog.Builder(this)
                .setTitle(slide.layout)
                .setMessage(R.string.slides_time_note)
                .setView(fields)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int total = parse(minutes) * 60 + parse(seconds);
                        save(Settings.slides(LayoutSlidesActivity.this).replaced(sideways, index,
                                slide.withSeconds(total)));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void save(LayoutSlides slides) {
        Settings.setSlides(this, slides);
        rebuild();
    }

    static String duration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        StringBuilder out = new StringBuilder();
        if (hours > 0) {
            out.append(hours).append(" h ");
        }
        if (minutes > 0) {
            out.append(minutes).append(" min ");
        }
        if (seconds > 0 || out.length() == 0) {
            out.append(seconds).append(" s");
        }
        return out.toString().trim();
    }

    private static int parse(EditText field) {
        try {
            return Math.max(0, Integer.parseInt(field.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- the look, as the Layout page has it ---------------------------------------------------

    private EditText number(String value) {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(value);
        field.setSelectAllOnFocus(true);
        field.setMinEms(3);
        return field;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(dp(4), 0, dp(12), 0);
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
        view.setPadding(0, 0, 0, dp(6));
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
        view.setPadding(0, dp(4), 0, dp(6));
        return view;
    }

    private TextView small(String text, boolean enabled, View.OnClickListener onClick) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(enabled ? ACCENT : DISABLED);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(PRESSED);
        pressed.setCornerRadius(dp(6));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[] {}, new GradientDrawable());
        button.setBackgroundDrawable(states);
        if (enabled) {
            Focusable.make(button);
            button.setOnClickListener(onClick);
        }
        return button;
    }

    private TextView button(String text, boolean enabled, View.OnClickListener onClick) {
        TextView button = small(text, enabled, onClick);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
