package com.reteclock;

import android.content.Context;
import android.content.SharedPreferences;

import com.reteclock.core.FontLibrary;
import com.reteclock.core.SafeName;
import com.reteclock.core.SettingsIni;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The whole arrangement in one file: the settings, the fonts and the pictures.
 *
 * A settings file on its own carries names — this font, that background — and lands on a phone that
 * has neither, so half of what was carried does nothing. The package carries the files themselves:
 *
 * <pre>
 *   settings.ini      the arrangement, in sections that match the settings pages
 *   fonts/…           the imported fonts, under their own names
 *   img/…             the pictures
 * </pre>
 *
 * Both plural and singular are accepted when reading (`font/`, `fonts/`, `img/`, `imgs/`), because
 * the point of a plain zip is that somebody can build one by hand. A bare `.ini` or `.txt` is
 * accepted too: it is the same thing without the files.
 *
 * <p><b>Names arriving here are not names, they are input.</b> Every entry is checked by
 * {@link SafeName} *before* it is read, and one that fails is refused whole and reported — no
 * silent renaming, because a name quietly repaired is a name the user cannot recognise in the list
 * afterwards.
 */
final class SettingsPackage {

    static final String SETTINGS_ENTRY = "settings.ini";
    private static final String[] FONT_FOLDERS = {"font", "fonts"};
    private static final String[] IMAGE_FOLDERS = {"img", "imgs"};

    /** As much of one file as will ever be read: past this it is not a font or a picture. */
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    /** And of the settings itself, which is a few thousand bytes of text. */
    private static final int MAX_SETTINGS_BYTES = 256 * 1024;
    /** A backstop against a zip that claims to hold a million files. */
    private static final int MAX_ENTRIES = 500;

    /** One file found inside a package. */
    static final class Carried {
        final String name;
        final byte[] content;

        Carried(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }

    /** What a package turned out to hold, before anything is applied. */
    static final class Preview {
        final SettingsIni.Reading settings;
        final List<Carried> fonts = new ArrayList<Carried>();
        final List<Carried> images = new ArrayList<Carried>();
        /** Entries refused by name, each with the reason, for showing to the user. */
        final List<String> refused = new ArrayList<String>();
        /** Whether this was a package rather than a bare settings file. */
        boolean packaged;

        Preview(SettingsIni.Reading settings) {
            this.settings = settings;
        }

        boolean isEmpty() {
            return settings.entries.isEmpty() && fonts.isEmpty() && images.isEmpty();
        }
    }

    /** What an import actually did. */
    static final class Result {
        int settingsApplied;
        int fontsAdded;
        int imagesAdded;
        int dropped;
    }

    private SettingsPackage() {
    }

    // ---- writing -----------------------------------------------------------------------

    /** The settings this phone holds, as INI, limited to the chosen pages. */
    static String settingsText(Context context, Set<String> sections) {
        Map<String, ?> all = new TreeMap<String, Object>(Settings.all(context));
        List<SettingsIni.Entry> entries = new ArrayList<SettingsIni.Entry>();
        for (Map.Entry<String, ?> setting : all.entrySet()) {
            String key = setting.getKey();
            if (!SettingsIni.isPortable(key)) {
                continue;
            }
            String section = SettingsIni.sectionOf(key);
            if (!sections.contains(section)) {
                continue;
            }
            Object value = setting.getValue();
            char kind = value instanceof Boolean ? SettingsIni.BOOLEAN
                    : value instanceof Integer ? SettingsIni.INT
                    : value instanceof Long ? SettingsIni.LONG
                    : value instanceof String ? SettingsIni.STRING
                    : 0;
            if (kind == 0 || kind != SettingsIni.kindOf(key)) {
                // Stored as something this build does not expect — carried wrongly is worse than
                // not carried.
                continue;
            }
            entries.add(new SettingsIni.Entry(key, kind, String.valueOf(value), section));
        }
        return SettingsIni.write(entries);
    }

    /** Writes the package to an already-open stream, which the caller closes. */
    static void write(Context context, OutputStream raw, Set<String> sections,
            boolean withFonts, boolean withImages) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(raw);
        try {
            zip.putNextEntry(new ZipEntry(SETTINGS_ENTRY));
            zip.write(settingsText(context, sections).getBytes("UTF-8"));
            zip.closeEntry();
            if (withFonts) {
                copyInto(zip, Settings.fonts(context), "fonts/");
            }
            if (withImages) {
                copyInto(zip, Settings.images(context), "img/");
            }
        } finally {
            zip.finish();
        }
    }

    private static void copyInto(ZipOutputStream zip, FontLibrary library, String folder)
            throws IOException {
        List<FontLibrary.Entry> entries = library.list();
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i).name;
            if (!SafeName.isSafe(name)) {
                continue;                 // it cannot have got in here, but the rule is one rule
            }
            File file = library.file(name);
            if (file == null || !file.isFile()) {
                continue;
            }
            zip.putNextEntry(new ZipEntry(folder + name));
            InputStream in = new FileInputStream(file);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    zip.write(buffer, 0, read);
                }
            } finally {
                in.close();
            }
            zip.closeEntry();
        }
    }

    // ---- reading -----------------------------------------------------------------------

    /**
     * Reads a package — or a bare settings file — without changing anything.
     *
     * Nothing here touches the preferences or the file store. That is the whole point: the user is
     * shown what is inside and picks what to bring in, and a package that turns out to be somebody
     * else's holiday photographs can be walked away from.
     */
    static Preview read(InputStream raw) throws IOException {
        byte[] head = new byte[4];
        java.io.PushbackInputStream in = new java.io.PushbackInputStream(raw, head.length);
        int got = 0;
        while (got < head.length) {
            int read = in.read(head, got, head.length - got);
            if (read < 0) {
                break;
            }
            got += read;
        }
        if (got > 0) {
            in.unread(head, 0, got);
        }
        boolean isZip = got == 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3
                && head[3] == 4;
        if (!isZip) {
            String text = new String(readAll(in, MAX_SETTINGS_BYTES), "UTF-8");
            return new Preview(SettingsIni.isOldFormat(text)
                    ? SettingsIni.fromOldFormat(text) : SettingsIni.parse(text));
        }

        Preview preview = new Preview(SettingsIni.parse(""));
        ZipInputStream zip = new ZipInputStream(in);
        List<Carried> fonts = new ArrayList<Carried>();
        List<Carried> images = new ArrayList<Carried>();
        List<String> refused = new ArrayList<String>();
        SettingsIni.Reading settings = null;
        ZipEntry entry;
        int seen = 0;
        while ((entry = zip.getNextEntry()) != null && seen < MAX_ENTRIES) {
            seen++;
            if (entry.isDirectory()) {
                continue;
            }
            String path = entry.getName().replace('\\', '/');
            if (isSettingsEntry(path)) {
                settings = SettingsIni.parse(
                        new String(readAll(zip, MAX_SETTINGS_BYTES), "UTF-8"));
                continue;
            }
            String font = SafeName.insideFolder(path, FONT_FOLDERS);
            String image = font != null ? null : SafeName.insideFolder(path, IMAGE_FOLDERS);
            if (font == null && image == null) {
                refused.add(path + " — not in fonts/ or img/");
                continue;
            }
            String name = font != null ? font : image;
            // Checked before a single byte of it is read, and refused whole rather than repaired.
            String wrong = SafeName.complaint(name);
            if (wrong != null) {
                refused.add(name + " — " + wrong);
                continue;
            }
            byte[] content = readAll(zip, MAX_FILE_BYTES);
            if (content.length >= MAX_FILE_BYTES) {
                refused.add(name + " — larger than "
                        + FontLibrary.humanBytes(MAX_FILE_BYTES));
                continue;
            }
            (font != null ? fonts : images).add(new Carried(name, content));
        }
        Preview out = new Preview(settings == null ? SettingsIni.parse("") : settings);
        out.packaged = true;
        out.fonts.addAll(fonts);
        out.images.addAll(images);
        out.refused.addAll(refused);
        return out;
    }

    private static boolean isSettingsEntry(String path) {
        String lower = path.toLowerCase(java.util.Locale.US);
        return lower.equals(SETTINGS_ENTRY) || lower.equals("settings.txt")
                || lower.equals("reteclock.ini");
    }

    // ---- applying ----------------------------------------------------------------------

    /**
     * Brings in what the user ticked.
     *
     * The files go in first: a setting that names a font is only worth writing if the font is
     * there, and this is the order that makes the two agree.
     */
    static Result apply(Context context, Preview preview, Set<String> sections,
            boolean withFonts, boolean withImages) {
        Result result = new Result();
        if (withFonts) {
            result.fontsAdded = install(Settings.fonts(context), preview.fonts);
        }
        if (withImages) {
            result.imagesAdded = install(Settings.images(context), preview.images);
        }

        Set<String> fontNames = names(Settings.fonts(context));
        Set<String> imageNames = names(Settings.images(context));

        SharedPreferences.Editor editor = Settings.edit(context);
        for (int i = 0; i < preview.settings.entries.size(); i++) {
            SettingsIni.Entry entry = preview.settings.entries.get(i);
            if (!sections.contains(entry.section)) {
                continue;
            }
            String value = entry.value;
            if (entry.kind == SettingsIni.STRING && isNameList(entry.key)) {
                List<String> kept = new ArrayList<String>();
                String[] lines = value.split("\n");
                for (int j = 0; j < lines.length; j++) {
                    String name = lines[j].trim();
                    if (name.length() == 0) {
                        continue;
                    }
                    if (imageNames.contains(name)) {
                        kept.add(name);
                    } else {
                        result.dropped++;
                    }
                }
                value = join(kept);
            } else if (entry.kind == SettingsIni.STRING && isFontChoice(entry.key)
                    && value.length() > 0 && !fontNames.contains(value)) {
                // The clock falls back to its own face; a setting naming a font that is not here
                // would be a setting nobody can see the effect of.
                result.dropped++;
                continue;
            }
            switch (entry.kind) {
                case SettingsIni.BOOLEAN:
                    editor.putBoolean(entry.key, "true".equals(value));
                    break;
                case SettingsIni.INT:
                    editor.putInt(entry.key, (int) number(value));
                    break;
                case SettingsIni.LONG:
                    editor.putLong(entry.key, number(value));
                    break;
                default:
                    editor.putString(entry.key, value);
                    break;
            }
            result.settingsApplied++;
        }
        // A run belonging to this phone is stopped: the arrangement it was running under has just
        // been replaced underneath it.
        editor.putLong(Settings.KEY_RUN_ORIGIN, com.reteclock.core.TimerMemory.NONE);
        editor.putString(Settings.KEY_RUN_PRESET, "");
        editor.commit();
        return result;
    }

    private static int install(FontLibrary library, List<Carried> files) {
        int added = 0;
        for (int i = 0; i < files.size(); i++) {
            try {
                library.add(files.get(i).name, files.get(i).content);
                added++;
            } catch (IOException e) {
                // One file that cannot be written is not a reason to abandon the rest; the count
                // the user is shown is of what actually arrived.
                continue;
            }
        }
        return added;
    }

    private static Set<String> names(FontLibrary library) {
        Set<String> out = new HashSet<String>();
        List<FontLibrary.Entry> entries = library.list();
        for (int i = 0; i < entries.size(); i++) {
            out.add(entries.get(i).name);
        }
        return out;
    }

    private static boolean isNameList(String key) {
        return Settings.KEY_POOL_BACKGROUND.equals(key) || Settings.KEY_POOL_TEXT.equals(key)
                || Settings.KEY_BACKGROUND_ORDER.equals(key);
    }

    private static boolean isFontChoice(String key) {
        return Settings.KEY_FONT.equals(key) || key.startsWith(Settings.KEY_FONT + "_");
    }

    private static String join(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(names.get(i));
        }
        return out.toString();
    }

    private static long number(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Reads a stream to the end, or to the limit, whichever comes first.
     *
     * Only -1 ends it: a stream is entitled to hand back zero bytes and ask to be called again, and
     * treating that as the end would truncate the entry silently — a settings file missing its last
     * line, or a font arriving with its tail cut off.
     */
    static byte[] readAll(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        while (total < limit) {
            int read = in.read(buffer, 0, Math.min(buffer.length, limit - total));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            total += read;
        }
        return out.toByteArray();
    }
}
