package com.reteclock;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.reteclock.core.AboutText;

/**
 * The menu a tap on the clock opens: where to go, and what this app is.
 *
 * A dialog rather than a screen, because it is two choices and a paragraph. Built in code in the
 * same idiom as the settings — a rounded card on black with one accent — so it belongs to the app
 * rather than to whatever the platform's default dialog looks like on a given phone.
 *
 * The foot of it is not decoration. Somebody running this on a device F-Droid no longer reaches,
 * with a charging port that cannot carry data, has no other way to find the project again.
 */
final class ClockMenu {

    private static final int TEXT_WHITE = 0xFFF2F2F2;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF4DB6AC;
    private static final int CARD = 0xFF161616;
    private static final int CARD_STROKE = 0xFF262626;
    private static final int PRESSED = 0x334DB6AC;

    private ClockMenu() {
    }

    /** Opens the menu over this activity. */
    static void show(final Activity activity) {
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

        // The menu has a name now, because it is referred to by name — in the readmes, in the
        // settings, and by anybody explaining where a thing lives.
        TextView heading = new TextView(activity);
        heading.setText(R.string.menu_title);
        heading.setTextColor(TEXT_DIM);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        heading.setPadding(0, 0, 0, dp(activity, 8));
        card.addView(heading);

        card.addView(choice(activity, activity.getString(R.string.menu_settings),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.startActivity(new Intent(activity, SettingsActivity.class));
                    }
                }));
        card.addView(choice(activity, activity.getString(R.string.menu_timer_settings),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.startActivity(new Intent(activity, TimerSettingsActivity.class));
                    }
                }));

        card.addView(choice(activity, activity.getString(R.string.menu_timedate_settings),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.startActivity(
                                new Intent(activity, TimeDateSettingsActivity.class));
                    }
                }));

        // The fonts and the pictures are two more of these categories rather than two buttons
        // buried in the first one: this menu is where the app says what its settings are divided
        // into, and a list of forty photographs is not a paragraph of the clock's own screen.
        card.addView(choice(activity, activity.getString(R.string.menu_fonts),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.startActivity(page(activity, SettingsActivity.PAGE_FONTS));
                    }
                }));
        card.addView(choice(activity, activity.getString(R.string.menu_pictures),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.startActivity(page(activity, SettingsActivity.PAGE_PICTURES));
                    }
                }));
        card.addView(choice(activity, activity.getString(R.string.menu_sounds),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.startActivity(
                                new Intent(activity, SoundSettingsActivity.class));
                    }
                }));
        card.addView(choice(activity, activity.getString(R.string.menu_carry),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.startActivity(page(activity, SettingsActivity.PAGE_CARRY));
                    }
                }));

        View rule = new View(activity);
        rule.setBackgroundColor(CARD_STROKE);
        rule.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        ((LinearLayout.LayoutParams) rule.getLayoutParams()).topMargin = dp(activity, 8);
        card.addView(rule);

        TextView about = new TextView(activity);
        about.setText(AboutText.of(versionName(activity), Build.VERSION.RELEASE));
        about.setTextColor(TEXT_DIM);
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        about.setLineSpacing(0f, 1.2f);
        about.setPadding(0, dp(activity, 10), 0, 0);
        card.addView(about);

        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /** One page of the settings, addressed by name rather than by scrolling to it. */
    private static Intent page(Activity activity, int which) {
        Intent intent = new Intent(activity, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_PAGE, which);
        return intent;
    }

    /** One line of the menu: large, in the accent, and pressable across the whole card. */
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

        view.setClickable(true);
        view.setOnClickListener(onClick);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** What this build calls itself, or null when the package manager will not say. */
    private static String versionName(Activity activity) {
        try {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return null;
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics());
    }
}
