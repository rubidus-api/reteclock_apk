package com.reteclock;

import android.content.Context;
import android.provider.Settings;

/**
 * Whether Android's own high-contrast text is switched on.
 *
 * It is an accessibility setting — Settings → Accessibility → High contrast text — and while it is
 * on the framework draws every string twice: the text, and a heavy outline behind it in black or
 * white. That is done inside the platform's own text rendering, so an app cannot ask not to have it,
 * and it has two consequences here:
 *
 * <ul>
 *   <li>The clock looks outlined whatever this app's own outline setting says, because the outline
 *       is not this app's.</li>
 *   <li>A picture used to fill the writing disappears: the platform's high-contrast path draws the
 *       glyphs in a flat colour of its own choosing and the paint's shader never reaches the
 *       screen — which leaves letters that look like empty outlines.</li>
 * </ul>
 *
 * <p>The way out is not to fight it but to stop drawing <em>text</em>: a glyph handed to the canvas
 * as a {@link android.graphics.Path} is a shape like any other, so it keeps its shader and gets no
 * outline it was not asked for. {@link ClockView} does that, and only while this is on — every other
 * phone keeps the rendering it always had.
 *
 * <p>The setting's key is not public API. Reading it by name is safe in the way that matters: an
 * unknown key reads as its default, so a platform that does not have it answers "off".
 */
final class HighContrastText {

    /** {@code Settings.Secure.HIGH_TEXT_CONTRAST_ENABLED}, which is hidden. */
    private static final String KEY = "high_text_contrast_enabled";

    private HighContrastText() {
    }

    static boolean isOn(Context context) {
        if (context == null) {
            return false;
        }
        try {
            return Settings.Secure.getInt(context.getContentResolver(), KEY, 0) == 1;
        } catch (RuntimeException e) {
            // A device that will not answer is a device that has not asked for it.
            return false;
        }
    }
}
