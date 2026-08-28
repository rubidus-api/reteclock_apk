package com.reteclock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

/**
 * Makes a hand-made control reachable without a finger, and shows which one is being pointed at.
 *
 * Most of this app's buttons are `TextView`s that were made clickable. A clickable view answers a
 * finger; only a *focusable* one exists to a D-pad, a keyboard or a remote — so on a television
 * every one of them was a dead end, and a settings screen two steps in was a settings screen you
 * could not leave except by pressing Back (issue #45).
 *
 * The focus mark is drawn over whatever background the control already has rather than replacing
 * it: these controls carry state-list backgrounds of their own, in several shapes, and a helper
 * that knew about all of them would have to be edited whenever one changed. Here the resting look
 * is kept and a ring is laid on top of it.
 */
final class Focusable {

    /** The ring: the app's own accent, thick enough to see across a room. */
    private static final int RING = 0xFF4DB6AC;
    private static final int RING_WIDTH_PX = 3;
    private static final int RING_RADIUS_PX = 8;

    private Focusable() {
    }

    /**
     * Makes this view clickable *and* reachable, and returns it so it can be used inline.
     *
     * The two go together on purpose: a control that answers a tap and cannot be reached any other
     * way is the bug this exists to prevent, so there is one call that does both.
     */
    static <T extends View> T make(T view) {
        if (view == null) {
            return null;
        }
        view.setClickable(true);
        view.setFocusable(true);
        final Drawable resting = view.getBackground();
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(hasFocus ? ringOver(resting) : resting);
            }
        });
        return view;
    }

    /** The control's own background with a ring on top, or the ring alone when it has none. */
    private static Drawable ringOver(Drawable resting) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x00000000);
        ring.setCornerRadius(RING_RADIUS_PX);
        ring.setStroke(RING_WIDTH_PX, RING);
        if (resting == null) {
            return ring;
        }
        return new LayerDrawable(new Drawable[] {resting, ring});
    }
}
