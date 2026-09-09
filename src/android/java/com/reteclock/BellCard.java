package com.reteclock;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.reteclock.core.Bell;

/**
 * What comes up when a bell that can be put off rings: its name, and the two answers to a sound.
 *
 * <p>Built in code in the same idiom as {@link ClockMenu} — a rounded card on black with one accent
 * — so it belongs to this app rather than to whatever a given phone's dialogs look like. Both
 * buttons go through {@link Focusable}, because a clock on a television is answered with a remote
 * and a button a D-pad cannot reach is not a button (issue #45).
 *
 * <p><b>Why it closes by itself.</b> A card nobody answers would sit over the clock until somebody
 * came back to it, and a clock covered by a card has stopped being a clock. So it waits
 * {@link #UNANSWERED_MS}, which is long enough to wake up and reach for the phone, and then does
 * what it would have done if the sound had simply finished: nothing. Closing it is the same answer
 * as <em>Stop</em> — the bell rang, it is over, and its next ordinary time is unaffected.
 *
 * <p>This is the clock screen only. Android does not hand a screensaver its touches, so there the
 * bell rings and a touch stops it, exactly as it always did.
 */
final class BellCard {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int PRESSED = 0x334DB6AC;

    /** How long an unanswered card stays before it closes itself. */
    static final long UNANSWERED_MS = 60_000L;

    /** The two answers, and the one the card gives when nobody gives it either. */
    interface Answer {
        /** Ring this again in a few minutes. */
        void putOff();

        /** That is the end of it. Also what an unanswered card does when it closes. */
        void stop();
    }

    private BellCard() {
    }

    /**
     * Shows the card, and returns it so the caller can take it away when the screen goes.
     *
     * The caller owns the dialog: a bell put off while the settings are being opened must not leave
     * a card behind on a window that is no longer there.
     */
    static Dialog show(final Activity activity, final Bell bell, final Answer answer) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(0x00000000));
        }

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        card.setPadding(pad, pad, pad, dp(activity, 12));
        GradientDrawable face = new GradientDrawable();
        face.setColor(CARD);
        face.setCornerRadius(dp(activity, 10));
        face.setStroke(1, CARD_STROKE);
        card.setBackgroundDrawable(face);

        // The time it is, in the bell's own terms, because a person woken by a sound wants to know
        // which one it was before they want anything else.
        TextView heading = new TextView(activity);
        heading.setText(String.format("%02d:%02d", bell.hour(), bell.minute()));
        heading.setTextColor(TEXT_WHITE);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(heading);

        TextView name = new TextView(activity);
        name.setText(bell.label.isEmpty()
                ? activity.getString(R.string.bell_card_untitled) : bell.label);
        name.setTextColor(TEXT_DIM);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        name.setPadding(0, dp(activity, 2), 0, dp(activity, 10));
        card.addView(name);

        // The state the card is guarding: whichever answer happens first is the only one that
        // happens. A finger and the minute running out can both arrive, and they must not both act.
        final boolean[] answered = new boolean[] {false};

        View putOff = choice(activity,
                activity.getString(R.string.bell_card_put_off, bell.snoozeMinutes),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (answered[0]) {
                            return;
                        }
                        answered[0] = true;
                        answer.putOff();
                        dialog.dismiss();
                    }
                });
        card.addView(putOff);

        card.addView(choice(activity, activity.getString(R.string.bell_card_stop),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (answered[0]) {
                            return;
                        }
                        answered[0] = true;
                        answer.stop();
                        dialog.dismiss();
                    }
                }));

        // Back means Stop. It is the answer a person gives when they have already taken in that the
        // bell rang, and leaving the card up after Back would be the app arguing with them.
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface d) {
                if (answered[0]) {
                    return;
                }
                answered[0] = true;
                answer.stop();
            }
        });
        // A touch anywhere but the card is not an answer to it: somebody reaching past a card to
        // see the time has not said which of the two they meant, and the minute below will decide.
        dialog.setCanceledOnTouchOutside(false);

        dialog.setContentView(card);
        dialog.show();
        // The remote lands on the first button rather than nowhere, so the centre key means
        // something the moment the card appears.
        putOff.requestFocus();

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (answered[0]) {
                    return;
                }
                answered[0] = true;
                answer.stop();
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
        }, UNANSWERED_MS);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                handler.removeCallbacksAndMessages(null);
            }
        });
        return dialog;
    }

    /** One line of the card: large, in the accent, and reachable without a finger. */
    private static TextView choice(Activity activity, String label, View.OnClickListener onClick) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextColor(ACCENT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(activity, 12);
        view.setPadding(pad, pad, pad, pad);

        GradientDrawable resting = new GradientDrawable();
        resting.setColor(Color.TRANSPARENT);
        resting.setCornerRadius(dp(activity, 8));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(PRESSED);
        pressed.setCornerRadius(dp(activity, 8));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[] {}, resting);
        view.setBackgroundDrawable(states);

        Focusable.make(view);
        view.setOnClickListener(onClick);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private static int dp(Activity activity, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics());
    }
}
