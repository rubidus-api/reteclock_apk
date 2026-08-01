package com.reteclock.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The fonts the user has imported, held as files in one directory.
 *
 * Pure Java: no android.* imports, so it is unit tested on a JVM. The Android layer hands it the
 * app's private directory and the bytes a document picker produced.
 *
 * Names come from a picker, which is free to return anything at all — a path, a colon, an empty
 * string. Every name is sanitised down to a single harmless file name, and every lookup is checked
 * against the directory afterwards, so a crafted name cannot reach a file outside it.
 */
public final class FontLibrary {

    /** One stored font. */
    public static final class Entry {
        /** The file name inside the directory, which is also what the setting stores. */
        public final String name;
        /** Its size on disk. */
        public final long bytes;

        Entry(String name, long bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    /** Used when sanitising leaves nothing usable. */
    private static final String FALLBACK_NAME = "font";

    private final File dir;

    public FontLibrary(File dir) {
        this.dir = dir;
    }

    /** The stored fonts, by name, so the settings screen does not reshuffle between visits. */
    public List<Entry> list() {
        File[] files = dir.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<Entry> entries = new ArrayList<Entry>(files.length);
        for (File f : files) {
            if (f.isFile()) {
                entries.add(new Entry(f.getName(), f.length()));
            }
        }
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.name.compareTo(b.name);
            }
        });
        return entries;
    }

    /** What every stored font occupies together. */
    public long totalBytes() {
        long total = 0;
        for (Entry e : list()) {
            total += e.bytes;
        }
        return total;
    }

    /** Whether a font by this name is still stored. A setting can outlive the file it names. */
    public boolean exists(String name) {
        return file(name) != null;
    }

    /**
     * The file holding this font, or null when there is no such font. Null rather than a path into
     * nowhere, so a caller cannot accidentally create the file by writing to what it got back.
     */
    public File file(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        File f = new File(dir, name);
        if (!f.isFile() || !inDirectory(f)) {
            return null;
        }
        return f;
    }

    /**
     * Stores these bytes under a name derived from {@code suggestedName}, and returns the name it
     * actually used — which differs when the suggestion was unusable or already taken.
     *
     * @throws IOException if the bytes are empty, or the directory or file cannot be written
     */
    public String add(String suggestedName, byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw new IOException("a font file cannot be empty");
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        String name = available(sanitise(suggestedName));
        File target = new File(dir, name);
        OutputStream out = new FileOutputStream(target);
        try {
            out.write(content);
        } finally {
            out.close();
        }
        return name;
    }

    /** Removes one font. Returns whether there was one to remove. */
    public boolean delete(String name) {
        File f = file(name);
        return f != null && f.delete();
    }

    /**
     * One plain file name: no directories, no separators, nothing a shell or a path walker would
     * read as structure. Everything outside a small allowed set becomes an underscore.
     */
    static String sanitise(String suggestedName) {
        if (suggestedName == null) {
            return FALLBACK_NAME;
        }
        // A picker may hand back a whole path; only the last segment can be a file name.
        String base = suggestedName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        StringBuilder clean = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_' || c == ' ';
            clean.append(keep ? c : '_');
        }
        String name = clean.toString().trim();
        // "." and ".." are directories, not names, however they arrived.
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return FALLBACK_NAME;
        }
        // Long names are a filesystem problem, not a user problem; keep the tail, which carries
        // the extension.
        if (name.length() > 100) {
            name = name.substring(name.length() - 100);
        }
        return name;
    }

    /** {@code name}, or the first free variation of it, so an import never overwrites a font. */
    private String available(String name) {
        if (!new File(dir, name).exists()) {
            return name;
        }
        String stem = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            extension = name.substring(dot);
        }
        for (int n = 2; n < 1000; n++) {
            String candidate = stem + "-" + n + extension;
            if (!new File(dir, candidate).exists()) {
                return candidate;
            }
        }
        throw new IllegalStateException("too many fonts named " + name);
    }

    /** Whether this file really sits in our directory, after any "..'" has been resolved away. */
    private boolean inDirectory(File f) {
        try {
            return f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    /** A size a person can read: "12 KB", "1.4 MB". */
    public static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes + 512) / 1024 + " KB";
        }
        long tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024);
        return (tenths / 10) + "." + (tenths % 10) + " MB";
    }
}
