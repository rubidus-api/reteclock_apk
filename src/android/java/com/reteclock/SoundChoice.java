package com.reteclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.reteclock.core.FontLibrary;

import java.util.List;

/**
 * "Which sound?", asked the same way wherever it is asked.
 *
 * The timer's four slots and every bell all need the same short list — the sounds this phone holds,
 * with "none" at the top — and the list is short enough that a dialog is the whole interaction. Kept
 * in one place so the wording and the order cannot drift apart between two screens.
 *
 * <p>"None" is first and is not a nothing: it means the built-in beep or chime, which is what every
 * cue did before sounds existed and what it goes back to when a file is deleted.
 */
final class SoundChoice {

    /** Told which sound was picked; an empty name means the built-in one. */
    interface Chosen {
        void chose(String name);
    }

    private SoundChoice() {
    }

    static void ask(final Activity activity, String current, final Chosen listener) {
        List<FontLibrary.Entry> stored = Settings.sounds(activity).list();
        final String[] names = new String[stored.size() + 1];
        String[] labels = new String[stored.size() + 1];
        names[0] = "";
        labels[0] = activity.getString(R.string.sound_pick_none);
        int chosen = 0;
        for (int i = 0; i < stored.size(); i++) {
            names[i + 1] = stored.get(i).name;
            labels[i + 1] = stored.get(i).name;
            if (stored.get(i).name.equals(current)) {
                chosen = i + 1;
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.sound_pick_title)
                .setSingleChoiceItems(labels, chosen, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        listener.chose(names[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
