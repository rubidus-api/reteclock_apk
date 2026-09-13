package com.reteclock;

import android.content.Context;
import android.content.res.Configuration;

import com.reteclock.core.layout.LayoutBook;
import com.reteclock.core.layout.LayoutSlides;

import java.util.Set;

/**
 * Says, once a second, whether the slide in force has changed (issue #53, RFC-0013).
 *
 * <p>The list and the shelf are read when the screen appears and kept, rather than parsed out of the
 * settings every second. The answer is the row, not the layout: the same layout can be listed twice,
 * once with a background and once without, and going from one to the other is a change to redraw.
 * The work in between is one subtraction — {@link #nextChangeMs} — so a clock with slides off pays
 * nothing.
 */
final class SlideWatch {

    private final Context context;
    private LayoutSlides slides = LayoutSlides.NONE;
    private Set<String> portraitShelf;
    private Set<String> landscapeShelf;
    private boolean sideways;
    private int row = -1;
    private long nextChangeMs = Long.MAX_VALUE;

    SlideWatch(Context context) {
        this.context = context;
        reload();
    }

    /** Reads the list and the shelves again, and takes the row in force now as the starting point. */
    void reload() {
        slides = Settings.slides(context);
        LayoutBook book = Settings.layouts(context);
        portraitShelf = Settings.shelfNames(book, false);
        landscapeShelf = Settings.shelfNames(book, true);
        sideways = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        long now = System.currentTimeMillis();
        row = slides.currentRow(sideways, now, shelf());
        nextChangeMs = slides.nextChange(sideways, now, shelf());
    }

    /** Whether a different row is in force than the last time this was asked. */
    boolean changed(long nowMs) {
        boolean turned = (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) != sideways;
        if (!turned && nowMs < nextChangeMs && nowMs > nextChangeMs - 25L * 60 * 60 * 1000) {
            return false;
        }
        int before = row;
        boolean wasSideways = sideways;
        reload();
        return row != before || sideways != wasSideways;
    }

    private Set<String> shelf() {
        return sideways ? landscapeShelf : portraitShelf;
    }
}
