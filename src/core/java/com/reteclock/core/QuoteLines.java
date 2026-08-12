package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Breaking a saying into the one, two or three lines its strip has room for.
 *
 * Greedy: words go on the current line while they fit and start a new one when they do not. The
 * measuring is left to the caller, because only the view knows the font — and keeping the rule
 * here rather than there is what makes it testable at all.
 *
 * A saying too long for the lines available is not cut off mid-word: the last line is ended with an
 * ellipsis, so it reads as "there was more" rather than as a mistake.
 */
public final class QuoteLines {

    /** How wide a string is in whatever font the caller is drawing with. */
    public interface Width {
        float of(String text);
    }

    private QuoteLines() {
    }

    /**
     * @param text     the saying, already joined with its author
     * @param maxWidth how wide a line may be
     * @param maxLines how many lines there is room for
     */
    public static List<String> wrap(String text, float maxWidth, int maxLines, Width width) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.trim().isEmpty() || maxLines < 1 || maxWidth <= 0f) {
            return out;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
            if (width.of(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            out.add(line.toString());
            line.setLength(0);
            line.append(words[i]);
            if (out.size() == maxLines) {
                break;
            }
        }
        if (out.size() < maxLines && line.length() > 0) {
            out.add(line.toString());
            return out;
        }
        // What is left did not fit. Say so on the last line rather than stopping mid-sentence.
        if (!out.isEmpty()) {
            out.set(out.size() - 1, ellipsize(out.get(out.size() - 1), maxWidth, width));
        }
        return out;
    }

    /** Trims a line word by word until it and an ellipsis fit. */
    private static String ellipsize(String line, float maxWidth, Width width) {
        String at = line;
        while (width.of(at + " ...") > maxWidth && at.lastIndexOf(' ') > 0) {
            at = at.substring(0, at.lastIndexOf(' '));
        }
        return at + " ...";
    }
}
