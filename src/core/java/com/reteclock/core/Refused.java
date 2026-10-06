package com.reteclock.core;

import java.io.IOException;

/**
 * An import or an edit that was refused, in words meant for the person who asked for it.
 *
 * An import is all or nothing (owner's decision, 2026-10-06): one line that is not a setting, one
 * value out of range, one unsafe entry in an archive, and the whole file is refused and nothing is
 * changed. That is only fair if the person is told which line, key or entry it was — the device
 * used to say "Could not read the file" and the browser "Request or file rejected", which sends
 * somebody who edited a settings file by hand back to it with nothing to look for.
 *
 * <p>The message is written here, by the code that refused, and never built from an exception's own
 * text: it may be shown in a browser, and a path or a platform's wording is not for there.
 */
public final class Refused extends IOException {

    public Refused(String message) {
        super(message);
    }

    /** A value quoted in a message: short, on one line, and plainly marked where it was cut. */
    public static String quote(String value) {
        if (value == null) {
            return "nothing";
        }
        String flat = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return "“" + (flat.length() > 40 ? flat.substring(0, 40) + "…" : flat) + "”";
    }
}
