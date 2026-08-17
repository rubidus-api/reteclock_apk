package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The bells, and the arithmetic of when one is due.
 *
 * The caller asks "what fell between the last time I looked and now", exactly as {@link TimerCues}
 * is asked, and for the same reason: a clock's tick is late whenever anything else happens on these
 * phones, and a bell that counts down towards its moment loses that moment. A window cannot miss a
 * bell and cannot ring one twice, however irregularly it is asked.
 *
 * <p>Time here is <em>local</em> and is carried as one number: {@code jdn * 1440 + minuteOfDay},
 * called a stamp. That makes a day boundary ordinary arithmetic rather than a special case, and it
 * keeps the weekday available, since the JDN is still in there — see {@link CivilTime#weekday}.
 *
 * <p>The offset the caller converts with is the app's own, summer time already folded in, which is
 * what the rest of the clock runs on. So a bell set for 07:00 rings at 07:00 as the clock reads,
 * which is the only definition a person looking at the clock would accept.
 */
public final class Bells {

    private static final char FIELD = '|';

    /**
     * How far back a window may reach before the bells in it are treated as missed.
     *
     * The clock is not always on screen: the settings are opened, the phone is picked up, the
     * activity pauses for ten minutes. Ringing the four bells that fell during that is not what a
     * bell is for — it is a chime at a moment, and the moment has gone. Anything older than this is
     * dropped silently.
     */
    public static final int CATCH_UP_MINUTES = 2;

    /** Nothing set. */
    public static final Bells NONE = new Bells(new ArrayList<Bell>());

    private final List<Bell> bells;

    private Bells(List<Bell> bells) {
        this.bells = Collections.unmodifiableList(bells);
    }

    public static Bells of(List<Bell> bells) {
        return bells == null || bells.isEmpty()
                ? NONE : new Bells(new ArrayList<Bell>(bells));
    }

    public List<Bell> list() {
        return bells;
    }

    public int size() {
        return bells.size();
    }

    public Bells with(Bell bell) {
        if (bell == null) {
            return this;
        }
        List<Bell> out = new ArrayList<Bell>(bells);
        out.add(bell);
        return new Bells(out);
    }

    /** The same bells with the one at this position replaced. */
    public Bells replacing(int index, Bell bell) {
        if (index < 0 || index >= bells.size() || bell == null) {
            return this;
        }
        List<Bell> out = new ArrayList<Bell>(bells);
        out.set(index, bell);
        return new Bells(out);
    }

    public Bells removing(int index) {
        if (index < 0 || index >= bells.size()) {
            return this;
        }
        List<Bell> out = new ArrayList<Bell>(bells);
        out.remove(index);
        return out.isEmpty() ? NONE : new Bells(out);
    }

    /** The same bells with sound names remapped, the way an import remaps a renamed file. */
    public Bells renamed(Map<String, String> renames) {
        if (renames == null || renames.isEmpty()) {
            return this;
        }
        List<Bell> out = new ArrayList<Bell>(bells.size());
        for (int i = 0; i < bells.size(); i++) {
            Bell bell = bells.get(i);
            String landed = renames.get(bell.sound);
            out.add(landed == null ? bell : bell.withSound(landed));
        }
        return new Bells(out);
    }

    /**
     * The same bells, with any naming a sound this phone does not have falling back to the chime.
     *
     * The bell is kept: somebody set a time and a set of weekdays, and dropping that because a file
     * did not travel would be dropping the part they had to think about. It rings the built-in
     * chime until they choose another sound.
     */
    public Bells soundsKeptTo(Collection<String> storedNames) {
        if (storedNames == null) {
            return this;
        }
        List<Bell> out = new ArrayList<Bell>(bells.size());
        boolean changed = false;
        for (int i = 0; i < bells.size(); i++) {
            Bell bell = bells.get(i);
            if (bell.sound.isEmpty() || storedNames.contains(bell.sound)) {
                out.add(bell);
            } else {
                out.add(bell.withSound(""));
                changed = true;
            }
        }
        return changed ? new Bells(out) : this;
    }

    // ---- when one is due ------------------------------------------------------------------

    /** The local stamp of an instant: the day counted in JDN, the minute counted from midnight. */
    public static long stampOf(long epochMillis, int offsetMinutes) {
        CivilTime now = CivilTime.of(epochMillis, offsetMinutes);
        return stamp(now.jdn, now.hour * 60 + now.minute);
    }

    public static long stamp(int jdn, int minuteOfDay) {
        return jdn * 1440L + minuteOfDay;
    }

    /**
     * The bells due after {@code fromStamp} and up to and including {@code toStamp}.
     *
     * Half-open at the start and closed at the end, so consecutive windows tile the timeline and
     * every ring belongs to exactly one of them — the same rule the timer's cues follow. A window
     * reaching further back than {@link #CATCH_UP_MINUTES} is shortened first: what is older than
     * that was missed, not delayed.
     */
    public List<Bell> due(long fromStamp, long toStamp) {
        List<Bell> out = new ArrayList<Bell>();
        if (toStamp <= fromStamp || bells.isEmpty()) {
            return out;
        }
        long from = Math.max(fromStamp, toStamp - CATCH_UP_MINUTES);
        long firstDay = dayOf(from);
        long lastDay = dayOf(toStamp);
        for (long day = firstDay; day <= lastDay; day++) {
            int weekday = CivilTime.weekday((int) day);
            for (int i = 0; i < bells.size(); i++) {
                Bell bell = bells.get(i);
                if (!bell.isLive() || !bell.ringsOn(weekday)) {
                    continue;
                }
                long at = day * 1440L + bell.minuteOfDay;
                if (at > from && at <= toStamp) {
                    out.add(bell);
                }
            }
        }
        return out;
    }

    /**
     * How many minutes until the next ring after this stamp, or -1 when no bell will ever ring.
     *
     * Only for showing on the settings screen. Nothing depends on it, so a week is far enough to
     * look: a bell that rings at all rings within seven days by construction.
     */
    public int minutesUntilNext(long stamp) {
        int best = -1;
        for (long day = dayOf(stamp); day <= dayOf(stamp) + 7; day++) {
            int weekday = CivilTime.weekday((int) day);
            for (int i = 0; i < bells.size(); i++) {
                Bell bell = bells.get(i);
                if (!bell.isLive() || !bell.ringsOn(weekday)) {
                    continue;
                }
                long at = day * 1440L + bell.minuteOfDay;
                if (at > stamp && (best < 0 || at - stamp < best)) {
                    best = (int) (at - stamp);
                }
            }
        }
        return best;
    }

    /**
     * The day a stamp falls in.
     *
     * Floor division, written out: {@code Math.floorDiv} is API 24, and the app compiles against
     * android-19. Stamps are positive for every date the app promises to be right about, but a
     * window that opens a minute before Julian day zero is still arithmetic, not a special case.
     */
    private static long dayOf(long stamp) {
        long q = stamp / 1440L;
        return stamp % 1440L != 0 && stamp < 0 ? q - 1 : q;
    }

    // ---- text ---------------------------------------------------------------------------

    /**
     * <pre>
     *   bells := bell ('\n' bell)*
     *   bell  := on '|' days '|' minuteOfDay '|' sound '|' label
     * </pre>
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bells.size(); i++) {
            Bell bell = bells.get(i);
            if (i > 0) {
                out.append('\n');
            }
            out.append(bell.on ? '1' : '0').append(FIELD);
            out.append(bell.days).append(FIELD);
            out.append(bell.minuteOfDay).append(FIELD);
            out.append(escape(bell.sound)).append(FIELD);
            out.append(escape(bell.label));
        }
        return out.toString();
    }

    /** Reads the stored text; one unreadable line costs only itself. */
    public static Bells parse(String text) {
        if (text == null || text.isEmpty()) {
            return NONE;
        }
        List<Bell> out = new ArrayList<Bell>();
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
            out.add(new Bell("1".equals(fields.get(0).trim()),
                    number(fields.get(1), Bell.EVERY_DAY),
                    number(fields.get(2), 0),
                    TimerPreset.unescape(fields.get(3)),
                    fields.size() > 4 ? TimerPreset.unescape(fields.get(4)) : ""));
        }
        return out.isEmpty() ? NONE : new Bells(out);
    }

    private static int number(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
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
