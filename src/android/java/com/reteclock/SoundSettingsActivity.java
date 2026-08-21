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
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.reteclock.core.Bell;
import com.reteclock.core.Bells;
import com.reteclock.core.FontLibrary;
import com.reteclock.core.MediaFormats;
import com.reteclock.core.SoundClip;
import com.reteclock.core.SoundClips;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The sounds this phone holds, how each one plays, and the bells that ring one at a time of day.
 *
 * A page of its own, like the fonts and the pictures, because a sound is the same kind of thing: a
 * file the user brings in, keeps under a name, and points settings at. The pool is a
 * {@link FontLibrary} pointed at a directory of its own, so nothing here is a new way of storing
 * anything.
 *
 * <p>The picker is the one the fonts use — {@code ACTION_OPEN_DOCUMENT} on API 19 and later,
 * {@code ACTION_GET_CONTENT} below it — and the bytes are copied into the app's own storage. That is
 * what keeps R5: no storage permission is asked for on any version of Android, and a file that has
 * been brought in cannot later be taken away by a card being swapped or by scoped storage.
 *
 * <p>Whether a file plays is not decided here from its name. It is opened with the device's own
 * decoder as it arrives, exactly as {@code usableFont} lets the platform decide what a font is, and
 * one this phone refuses is refused at the moment somebody can do something about it.
 */
public class SoundSettingsActivity extends Activity {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int WARNING = 0xFFFFB300;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int DIVIDER = 0xFF272727;
    private static final int PRESSED = 0x334DB6AC;
    private static final int BUTTON_FACE = 0xFF212121;

    private static final int REQUEST_PICK_SOUND = 41;

    /** As much of one sound as is ever read. A song is megabytes; past this it is not a cue. */
    private static final int MAX_SOUND_BYTES = 32 * 1024 * 1024;

    /** The weekday letters, Sunday first, in the order {@link Bell} numbers them. */
    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private LinearLayout soundSection;
    private LinearLayout bellSection;
    private final SoundPlayer player = new SoundPlayer();
    /** Which sound the preview is playing, so its row can offer to stop it. */
    private String previewing = "";
    /**
     * Told by the player when it has fallen quiet, so a row stops offering *Stop*.
     *
     * A sound ends by itself and the screen had no way of knowing — the sound half of issue #29.
     */
    private final SoundPlayer.OnIdle idleListener = new SoundPlayer.OnIdle() {
        @Override
        public void idle() {
            previewing = "";
            rebuildSounds();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, dp(16), pad, dp(16));
        root.addView(title(getString(R.string.sound_title)));

        LinearLayout sounds = card(getString(R.string.sound_card_files));
        soundSection = new LinearLayout(this);
        soundSection.setOrientation(LinearLayout.VERTICAL);
        sounds.addView(soundSection);
        sounds.addView(actionButton(getString(R.string.sound_add), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSound();
            }
        }));
        sounds.addView(footer(getString(R.string.sound_kinds,
                MediaFormats.labelsAt(Build.VERSION.SDK_INT))));
        // One at a time is the picker above; a folder at a time is the zip the fonts and the
        // pictures already travel in, and it is the same page for all three.
        sounds.addView(actionButton(getString(R.string.sound_carry), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SoundSettingsActivity.this, SettingsActivity.class);
                intent.putExtra(SettingsActivity.EXTRA_PAGE, SettingsActivity.PAGE_CARRY);
                startActivity(intent);
            }
        }));
        sounds.addView(footer(getString(R.string.sound_carry_note)));
        root.addView(sounds);

        LinearLayout bells = card(getString(R.string.sound_card_bells));
        CheckBox on = new CheckBox(this);
        on.setText(R.string.sound_bells_on);
        on.setTextColor(TEXT_WHITE);
        on.setChecked(Settings.bellsOn(this));
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setBellsOn(SoundSettingsActivity.this, checked);
                rebuildBells();
            }
        });
        bells.addView(on);
        bells.addView(footer(getString(R.string.sound_bells_note)));
        bells.addView(divider());
        bellSection = new LinearLayout(this);
        bellSection.setOrientation(LinearLayout.VERTICAL);
        bells.addView(bellSection);
        bells.addView(actionButton(getString(R.string.sound_bell_add), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addBell();
            }
        }));
        root.addView(bells);

        // A sound that ends by itself leaves the row still offering *Stop*, which is the sound
        // half of issue #29. The player says when it has fallen quiet and the rows are drawn again.
        rebuildSounds();
        rebuildBells();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Armed again on the way back in: onPause takes the listener off, and without this the
        // rows would go back to sticking on *Stop* the second time the screen is opened.
        player.setOnIdle(idleListener);
        previewing = "";
        rebuildSounds();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // A preview belongs to the screen that started it. Leaving the screen stops it, or the phone
        // plays a song at somebody from a window they have already left. The listener goes first:
        // there is no point rebuilding rows for a screen on its way out.
        player.setOnIdle(null);
        player.stopNow();
        previewing = "";
    }

    // ---- the pool -------------------------------------------------------------------------

    private void rebuildSounds() {
        if (soundSection == null) {
            return;
        }
        soundSection.removeAllViews();
        FontLibrary library = Settings.sounds(this);
        List<FontLibrary.Entry> stored = library.list();
        if (stored.isEmpty()) {
            soundSection.addView(footer(getString(R.string.sound_none)));
            return;
        }
        SoundClips clips = Settings.soundClips(this);
        for (int i = 0; i < stored.size(); i++) {
            soundSection.addView(soundRow(stored.get(i), clips.of(stored.get(i).name)));
        }
        soundSection.addView(footer(getResources().getQuantityString(R.plurals.sound_total,
                stored.size(), stored.size(), FontLibrary.humanBytes(library.totalBytes()))));
    }

    /**
     * One sound: what it is called and how it plays, then the four things that can be done to it.
     *
     * Two lines rather than one. A row that is one line long is ellipsised by the platform the
     * moment the name is ordinary, and what gets cut is the part that says how the sound plays.
     */
    private View soundRow(final FontLibrary.Entry entry, final SoundClip clip) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(TEXT_WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        row.addView(name);

        TextView detail = new TextView(this);
        detail.setText(FontLibrary.humanBytes(entry.bytes) + "  ·  " + clipSummary(clip));
        detail.setTextColor(TEXT_DIM);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        row.addView(detail);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final boolean sounding = player.isPlaying() && entry.name.equals(previewing);
        buttons.addView(smallButton(getString(sounding
                ? R.string.sound_stop : R.string.sound_play), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sounding) {
                    player.fadeOutAndStop();
                    previewing = "";
                } else {
                    preview(entry.name);
                }
                rebuildSounds();
            }
        }));
        buttons.addView(smallButton(getString(R.string.sound_clip), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editClip(entry.name);
            }
        }));
        buttons.addView(smallButton(getString(R.string.sound_rename), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renameSound(entry.name);
            }
        }));
        buttons.addView(smallButton(getString(R.string.sound_delete), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSound(entry.name);
            }
        }));
        row.addView(buttons);
        return row;
    }

    private String clipSummary(SoundClip clip) {
        if (clip.isWhole()) {
            return getString(R.string.sound_row_plain);
        }
        String end = clip.endTenths == SoundClip.TO_THE_END
                ? getString(R.string.sound_to_the_end)
                : SoundClip.tenthsText(clip.endTenths);
        return getString(R.string.sound_row_clipped,
                SoundClip.tenthsText(clip.startTenths), end,
                clip.loops ? getString(R.string.sound_row_repeats) : "");
    }

    /**
     * Plays one sound as it will actually be played — clip and all — or the built-in chime when the
     * name is empty, since that is what a bell with no sound rings.
     */
    private void preview(String name) {
        // The phone's own switch is obeyed here too — but silently failing to play would read as a
        // broken button, so this one says what happened.
        if (!PhoneQuiet.soundAllowed(this)) {
            toast(getString(R.string.sound_phone_silent));
            return;
        }
        if (name == null || name.isEmpty()) {
            player.stopNow();
            previewing = "";
            new TimerSounds(this).play(com.reteclock.core.Tones.CHIME, Settings.ALERT_SOUND);
            return;
        }
        File file = Settings.sounds(this).file(name);
        player.play(file, Settings.soundClips(this).of(name));
        previewing = name;
    }

    // ---- bringing one in ------------------------------------------------------------------

    /**
     * Asks the system for a file.
     *
     * The same two intents the fonts use, for the same reason: the system opens the stream, so no
     * storage permission is involved on any version. {@code audio/*} is a hint to the picker, not a
     * rule — what is actually kept is decided by the decoder below.
     */
    private void pickSound() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_SOUND);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.sound_no_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_SOUND || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        importSound(data.getData());
    }

    private void importSound(Uri uri) {
        byte[] content;
        try {
            content = readAll(uri, MAX_SOUND_BYTES);
        } catch (IOException e) {
            toast(getString(R.string.sound_failed));
            return;
        }
        FontLibrary library = Settings.sounds(this);
        String stored;
        try {
            stored = library.add(displayName(uri), content);
        } catch (IOException e) {
            toast(getString(R.string.sound_failed));
            return;
        }
        // The decoder has the last word. A picture somebody renamed, a format this phone was never
        // given, a file that arrived truncated: all of them look like a sound until one is opened.
        File file = library.file(stored);
        if (!SoundPlayer.playable(file)) {
            library.delete(stored);
            toast(getString(R.string.sound_unplayable, stored));
            return;
        }
        toast(getString(R.string.sound_added, stored));
        rebuildSounds();
    }

    private void renameSound(final String name) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(name);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.sound_rename)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String landed = Settings.sounds(SoundSettingsActivity.this)
                                .rename(name, input.getText().toString());
                        if (landed == null) {
                            toast(getString(R.string.sound_rename_taken));
                            return;
                        }
                        followRename(name, landed);
                        rebuildSounds();
                        rebuildBells();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Everything that named the old name now names the new one.
     *
     * A rename that left the bells ringing a beep would be a rename that quietly broke the thing it
     * was done to. The presets are a tree written as one string, so they are parsed and written
     * again; the bells and the clips carry their own remapping.
     */
    private void followRename(String from, String to) {
        java.util.Map<String, String> renames = new java.util.HashMap<String, String>();
        renames.put(from, to);
        Settings.setSoundClips(this, Settings.soundClips(this).renamed(renames));
        Settings.setBells(this, Settings.bells(this).renamed(renames));
        List<com.reteclock.core.TimerPreset> presets = Settings.timerPresets(this);
        for (int i = 0; i < presets.size(); i++) {
            presets.set(i, presets.get(i).soundsRenamed(renames));
        }
        Settings.setTimerPresets(this, presets);
    }

    private void deleteSound(final String name) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.sound_delete_ask, name))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (name.equals(previewing)) {
                            player.stopNow();
                            previewing = "";
                        }
                        Settings.sounds(SoundSettingsActivity.this).delete(name);
                        // The clip described a file that is gone; the bells keep their times and
                        // fall back to the chime.
                        Settings.setSoundClips(SoundSettingsActivity.this,
                                Settings.soundClips(SoundSettingsActivity.this).without(name));
                        Settings.setBells(SoundSettingsActivity.this,
                                Settings.bells(SoundSettingsActivity.this)
                                        .soundsKeptTo(storedNames()));
                        rebuildSounds();
                        rebuildBells();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private java.util.Set<String> storedNames() {
        java.util.Set<String> out = new java.util.HashSet<String>();
        List<FontLibrary.Entry> stored = Settings.sounds(this).list();
        for (int i = 0; i < stored.size(); i++) {
            out.add(stored.get(i).name);
        }
        return out;
    }

    // ---- how one plays --------------------------------------------------------------------

    /** The stretch of a file that plays, and whether it repeats. */
    private void editClip(final String name) {
        final SoundClip clip = Settings.soundClips(this).of(name);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, dp(8), pad, 0);

        // The length as the timer writes a length — "3:41", "1:02:00" — rather than as a count of
        // tenths, which past a minute nobody can read.
        long length = SoundPlayer.durationMs(Settings.sounds(this).file(name));
        box.addView(footer(length > 0
                ? getString(R.string.sound_clip_length,
                        com.reteclock.core.TimeReadout.trimmed(length))
                : getString(R.string.sound_clip_length_unknown)));

        box.addView(subheading(getString(R.string.sound_clip_start)));
        final EditText start = decimalField(SoundClip.tenthsText(clip.startTenths));
        box.addView(start);

        box.addView(subheading(getString(R.string.sound_clip_end)));
        final EditText end = decimalField(clip.endTenths == SoundClip.TO_THE_END
                ? "" : SoundClip.tenthsText(clip.endTenths));
        box.addView(end);

        final CheckBox loop = new CheckBox(this);
        loop.setText(R.string.sound_clip_loop);
        loop.setTextColor(TEXT_WHITE);
        loop.setChecked(clip.loops);
        box.addView(loop);
        box.addView(footer(getString(R.string.sound_clip_note)));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sound_clip_title, name))
                .setView(box)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String typedEnd = end.getText().toString().trim();
                        SoundClip made = new SoundClip(name,
                                SoundClip.parseTenths(start.getText().toString()),
                                typedEnd.isEmpty()
                                        ? SoundClip.TO_THE_END : SoundClip.parseTenths(typedEnd),
                                loop.isChecked());
                        Settings.setSoundClips(SoundSettingsActivity.this,
                                Settings.soundClips(SoundSettingsActivity.this).with(made));
                        preview(name);
                        rebuildSounds();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private EditText decimalField(String value) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(value);
        field.setSelection(field.getText().length());
        return field;
    }

    // ---- the bells ------------------------------------------------------------------------

    private void rebuildBells() {
        if (bellSection == null) {
            return;
        }
        bellSection.removeAllViews();
        Bells bells = Settings.bells(this);
        if (bells.size() == 0) {
            bellSection.addView(footer(getString(R.string.sound_bell_none)));
            return;
        }
        for (int i = 0; i < bells.size(); i++) {
            bellSection.addView(bellRow(i, bells.list().get(i)));
        }
        int minutes = Settings.bellsOn(this)
                ? bells.minutesUntilNext(Bells.stampOf(System.currentTimeMillis(),
                        Settings.offsetMinutes(this, System.currentTimeMillis())))
                : -1;
        bellSection.addView(footer(minutes < 0
                ? getString(R.string.sound_bell_next_none)
                : getString(R.string.sound_bell_next, untilText(minutes))));
    }

    private String untilText(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours <= 0) {
            return rest + " min";
        }
        return hours + " h " + rest + " min";
    }

    private View bellRow(final int index, final Bell bell) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        CheckBox on = new CheckBox(this);
        on.setText(clockText(bell) + (bell.label.isEmpty() ? "" : "  ·  " + bell.label));
        on.setTextColor(TEXT_WHITE);
        on.setChecked(bell.on);
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Settings.setBells(SoundSettingsActivity.this,
                        Settings.bells(SoundSettingsActivity.this)
                                .replacing(index, bell.withOn(checked)));
                rebuildBells();
            }
        });
        row.addView(on);

        TextView detail = new TextView(this);
        detail.setText(daysText(bell) + "  ·  "
                + (bell.sound.isEmpty() ? getString(R.string.sound_bell_chime) : bell.sound)
                + (bell.repeats > 1 ? "  ·  " + getString(R.string.sound_bell_times,
                        bell.repeats) : ""));
        detail.setTextColor(bell.isLive() ? TEXT_DIM : WARNING);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        row.addView(detail);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(smallButton(getString(R.string.sound_bell_edit),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        editBell(index, bell);
                    }
                }));
        buttons.addView(smallButton(getString(R.string.sound_play), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preview(bell.sound);
            }
        }));
        buttons.addView(smallButton(getString(R.string.sound_delete), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteBell(index, bell);
            }
        }));
        row.addView(buttons);
        return row;
    }

    /** "07:00", in the clock's own reading of the hour. */
    private String clockText(Bell bell) {
        return (bell.hour() < 10 ? "0" : "") + bell.hour()
                + ":" + (bell.minute() < 10 ? "0" : "") + bell.minute();
    }

    private String daysText(Bell bell) {
        if (bell.days == Bell.EVERY_DAY) {
            return getString(R.string.sound_bell_every_day);
        }
        if (bell.days == 0) {
            return getString(R.string.sound_bell_days_none);
        }
        StringBuilder out = new StringBuilder();
        for (int day = 0; day < 7; day++) {
            if (bell.ringsOn(day)) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(DAY_LETTERS[day]);
            }
        }
        return out.toString();
    }

    private void deleteBell(final int index, final Bell bell) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.sound_bell_delete_ask, clockText(bell)))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.setBells(SoundSettingsActivity.this,
                                Settings.bells(SoundSettingsActivity.this).removing(index));
                        rebuildBells();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addBell() {
        Bells bells = Settings.bells(this);
        Settings.setBells(this, bells.with(Bell.atHour(7)));
        rebuildBells();
        Bells now = Settings.bells(this);
        editBell(now.size() - 1, now.list().get(now.size() - 1));
    }

    /**
     * One bell, edited whole: the time, the days, the sound, the name.
     *
     * Everything in one dialog rather than a row of pop-ups, because the four answers only make
     * sense together — a bell with a sound and no day is a decision half made.
     */
    private void editBell(final int index, final Bell bell) {
        final Bell[] edited = {bell};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, dp(8), pad, 0);

        box.addView(subheading(getString(R.string.sound_bell_time)));
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText hour = numberField(Integer.toString(bell.hour()));
        final EditText minute = numberField(Integer.toString(bell.minute()));
        timeRow.addView(hour, new LinearLayout.LayoutParams(dp(64),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextColor(TEXT_WHITE);
        colon.setPadding(dp(6), dp(8), dp(6), 0);
        timeRow.addView(colon);
        timeRow.addView(minute, new LinearLayout.LayoutParams(dp(64),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(timeRow);

        box.addView(subheading(getString(R.string.sound_bell_days)));
        final FlowLayout days = new FlowLayout(this, dp(4), dp(2));
        box.addView(days);
        rebuildDayChips(days, edited);

        box.addView(subheading(getString(R.string.sound_bell_sound)));
        final TextView soundButton = smallButton(bell.sound.isEmpty()
                ? getString(R.string.sound_bell_chime) : bell.sound, null);
        soundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SoundChoice.ask(SoundSettingsActivity.this, edited[0].sound,
                        new SoundChoice.Chosen() {
                            @Override
                            public void chose(String name) {
                                edited[0] = edited[0].withSound(name);
                                soundButton.setText(name.isEmpty()
                                        ? getString(R.string.sound_bell_chime) : name);
                                preview(name);
                            }
                        });
            }
        });
        box.addView(soundButton);

        box.addView(subheading(getString(R.string.sound_bell_repeats)));
        final EditText repeats = numberField(Integer.toString(bell.repeats));
        box.addView(repeats, new LinearLayout.LayoutParams(dp(64),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(footer(getString(R.string.sound_bell_repeats_note)));

        box.addView(subheading(getString(R.string.sound_bell_label)));
        final EditText label = new EditText(this);
        label.setSingleLine(true);
        label.setText(bell.label);
        box.addView(label);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        new AlertDialog.Builder(this)
                .setTitle(R.string.sound_bell_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Bell made = edited[0]
                                .withTime(number(hour.getText().toString(), 23),
                                        number(minute.getText().toString(), 59))
                                .withRepeats(number(repeats.getText().toString(),
                                        Bell.MAX_REPEATS))
                                .withLabel(label.getText().toString().trim());
                        Settings.setBells(SoundSettingsActivity.this,
                                Settings.bells(SoundSettingsActivity.this)
                                        .replacing(index, made));
                        player.stopNow();
                        previewing = "";
                        rebuildBells();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The seven days as chips that toggle, rebuilt in place so the state is visible. */
    private void rebuildDayChips(final FlowLayout into, final Bell[] edited) {
        into.removeAllViews();
        for (int day = 0; day < 7; day++) {
            final int which = day;
            final boolean rings = edited[0].ringsOn(day);
            TextView chip = new TextView(this);
            chip.setText(DAY_LETTERS[day]);
            chip.setTextColor(rings ? Color.BLACK : TEXT_DIM);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            GradientDrawable face = new GradientDrawable();
            face.setColor(rings ? ACCENT : BUTTON_FACE);
            face.setCornerRadius(dp(6));
            chip.setBackgroundDrawable(face);
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    edited[0] = edited[0].withDay(which, !rings);
                    rebuildDayChips(into, edited);
                }
            });
            into.addView(chip);
        }
    }

    private EditText numberField(String value) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(value);
        field.setSelection(field.getText().length());
        return field;
    }

    private static int number(String text, int most) {
        try {
            int value = Integer.parseInt(text.trim());
            return value < 0 ? 0 : Math.min(value, most);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- reading a picked file ------------------------------------------------------------

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
                int column = cursor.getColumnIndex("_display_name");
                if (column >= 0 && !cursor.isNull(column)) {
                    return cursor.getString(column);
                }
            }
        } catch (RuntimeException e) {
            // A provider that will not be queried still gave us bytes; the URI's tail will do.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String path = uri.getLastPathSegment();
        return path == null ? "sound" : path;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---- the same small pieces the other settings screens are built from -------------------

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
        if (onClick != null) {
            button.setOnClickListener(onClick);
        }
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
