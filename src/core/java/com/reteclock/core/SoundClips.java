package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The clips, one per stored sound that has been adjusted.
 *
 * Only the adjusted ones are written down. A file nobody has touched plays whole and once, which is
 * what {@link SoundClip#whole} answers, so an empty list means "every sound plays as it is" rather
 * than "no sounds". That keeps the stored text short, and it keeps a newly imported file from
 * arriving with settings somebody else's phone chose for it.
 *
 * <pre>
 *   clips := clip ('\n' clip)*
 *   clip  := name '|' startTenths '|' endTenths '|' loops
 * </pre>
 *
 * A stored name has been through {@link FontLibrary}'s sanitiser and cannot hold a separator, but
 * the text can also arrive hand-edited from another phone, so it is escaped and unescaped anyway.
 */
public final class SoundClips {

    private static final char FIELD = '|';
    private static final char LINE = '\n';

    /** Nothing adjusted. */
    public static final SoundClips NONE = new SoundClips(new ArrayList<SoundClip>());

    private final List<SoundClip> clips;

    private SoundClips(List<SoundClip> clips) {
        this.clips = Collections.unmodifiableList(clips);
    }

    /** The adjusted clips, in the order they were stored. */
    public List<SoundClip> list() {
        return clips;
    }

    /** How this sound plays: what was stored for it, or the whole file once. */
    public SoundClip of(String name) {
        if (name == null || name.isEmpty()) {
            return SoundClip.whole("");
        }
        for (int i = 0; i < clips.size(); i++) {
            if (clips.get(i).name.equals(name)) {
                return clips.get(i);
            }
        }
        return SoundClip.whole(name);
    }

    /** Whether this sound has settings of its own, as opposed to playing whole and once. */
    public boolean isAdjusted(String name) {
        return !of(name).isWhole();
    }

    /**
     * The same set with this clip in it — or, when the clip is the plain one, without it. A sound
     * returned to its whole self stops being written down, which is what makes the stored text
     * describe only what somebody actually chose.
     */
    public SoundClips with(SoundClip clip) {
        List<SoundClip> out = new ArrayList<SoundClip>(clips.size() + 1);
        boolean replaced = false;
        for (int i = 0; i < clips.size(); i++) {
            SoundClip existing = clips.get(i);
            if (clip != null && existing.name.equals(clip.name)) {
                if (!clip.isWhole()) {
                    out.add(clip);
                }
                replaced = true;
            } else {
                out.add(existing);
            }
        }
        if (!replaced && clip != null && !clip.isWhole() && !clip.name.isEmpty()) {
            out.add(clip);
        }
        return new SoundClips(out);
    }

    /** The same set without this sound's settings. */
    public SoundClips without(String name) {
        return with(SoundClip.whole(name == null ? "" : name));
    }

    /**
     * The same set with names remapped — what an import does when a carried file lands under a
     * different name because the one it wanted was taken.
     */
    public SoundClips renamed(Map<String, String> renames) {
        if (renames == null || renames.isEmpty()) {
            return this;
        }
        List<SoundClip> out = new ArrayList<SoundClip>(clips.size());
        for (int i = 0; i < clips.size(); i++) {
            SoundClip clip = clips.get(i);
            String landed = renames.get(clip.name);
            out.add(landed == null ? clip : clip.withName(landed));
        }
        return new SoundClips(out);
    }

    /**
     * The same set, minus settings for sounds that are no longer stored.
     *
     * A setting can outlive the file it names — a deletion here, a package there — and a clip for a
     * file nobody has is a line that will never do anything and that the user cannot see to remove.
     */
    public SoundClips keeping(Collection<String> storedNames) {
        if (storedNames == null) {
            return this;
        }
        List<SoundClip> out = new ArrayList<SoundClip>(clips.size());
        for (int i = 0; i < clips.size(); i++) {
            if (storedNames.contains(clips.get(i).name)) {
                out.add(clips.get(i));
            }
        }
        return out.size() == clips.size() ? this : new SoundClips(out);
    }

    public String text() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < clips.size(); i++) {
            SoundClip clip = clips.get(i);
            if (i > 0) {
                out.append(LINE);
            }
            out.append(escape(clip.name)).append(FIELD);
            out.append(clip.startTenths).append(FIELD);
            out.append(clip.endTenths).append(FIELD);
            out.append(clip.loops ? '1' : '0');
        }
        return out.toString();
    }

    /** Reads the stored text. Anything unreadable costs its own line and nothing else. */
    public static SoundClips parse(String text) {
        List<SoundClip> out = new ArrayList<SoundClip>();
        if (text == null || text.isEmpty()) {
            return NONE;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> fields = TimerPreset.split(line, FIELD);
            if (fields.size() < 4) {
                continue;
            }
            String name = TimerPreset.unescape(fields.get(0));
            if (name.isEmpty()) {
                continue;
            }
            SoundClip clip = new SoundClip(name, number(fields.get(1)), number(fields.get(2)),
                    "1".equals(fields.get(3).trim()));
            if (!clip.isWhole()) {
                out.add(clip);
            }
        }
        return out.isEmpty() ? NONE : new SoundClips(out);
    }

    private static int number(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return value < 0 ? 0 : (int) Math.min(value, SoundClip.MAX_TENTHS);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == FIELD || c == '\n' || c == '\r') {
                out.append('\\').append(c == '\n' ? 'n' : c == '\r' ? 'r' : c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
