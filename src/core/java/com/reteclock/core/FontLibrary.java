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
 * app's private directory and the bytes a document picker produced. Nothing here is font-specific
 * — it is a file store with safe names — so the background image is kept in one of these too,
 * pointed at its own directory.
 *
 * Names come from a picker, which is free to return anything at all — a path, a colon, an empty
 * string. Every name is sanitised down to a single harmless file name, and every lookup is checked
 * against the directory afterwards, so a crafted name cannot reach a file outside it.
 */
public final class FontLibrary {

    /** One stored file. */
    public static final class Entry {
        /** The file name inside the directory, which is also what the setting stores. */
        public final String name;
        /** Its size on disk. */
        public final long bytes;
        /** When it landed in the directory — import time, since nothing edits a stored file. */
        public final long modifiedMs;

        Entry(String name, long bytes, long modifiedMs) {
            this.name = name;
            this.bytes = bytes;
            this.modifiedMs = modifiedMs;
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
                entries.add(new Entry(f.getName(), f.length(), f.lastModified()));
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
        String name = available(sanitise(suggestedName), content);
        File target = new File(dir, name);
        if (target.isFile()) {
            // available() only hands back an occupied name when that file already holds exactly
            // these bytes: the same file imported again is the file it already is.
            return name;
        }
        OutputStream out = new FileOutputStream(target);
        try {
            out.write(content);
        } finally {
            out.close();
        }
        return name;
    }

    /**
     * Moves an outside file into the library — migration, not import: the bytes are already on
     * this filesystem, so they are renamed in rather than copied, which also keeps the stored
     * date. Collisions follow {@code add}'s rules: the same bytes are recognised and reused (the
     * source is then deleted), a different file steps to the next free name. Returns the stored
     * name, or null when the move fails.
     */
    public String absorb(File source) throws IOException {
        if (source == null || !source.isFile()) {
            return null;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        byte[] content = readAll(source);
        if (content == null) {
            return null;
        }
        String name = available(sanitise(source.getName()), content);
        File target = new File(dir, name);
        if (target.isFile()) {
            // Already held, byte for byte; the source is now just a duplicate.
            return source.delete() || !source.exists() ? name : name;
        }
        if (!source.renameTo(target)) {
            return null;
        }
        return name;
    }

    private static byte[] readAll(File file) {
        try {
            long length = file.length();
            if (length <= 0 || length > Integer.MAX_VALUE) {
                return null;
            }
            byte[] bytes = new byte[(int) length];
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = in.read(bytes, offset, bytes.length - offset);
                    if (read < 0) {
                        return null;
                    }
                    offset += read;
                }
            } finally {
                in.close();
            }
            return bytes;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Renames one stored file, and returns the name it now has — or null when the source does not
     * exist, the sanitised target is already another file's name, or the filesystem refuses. The
     * new name goes through the same sanitiser an import does, so a rename cannot reach outside
     * the directory either. A rename is not an edit: the stored date rides along, so a date sort
     * does not reshuffle.
     */
    public String rename(String name, String suggestedNewName) {
        File from = file(name);
        if (from == null) {
            return null;
        }
        String target = sanitise(suggestedNewName);
        if (target.equals(name)) {
            return name;
        }
        File to = new File(dir, target);
        if (to.exists() || !from.renameTo(to)) {
            return null;
        }
        return target;
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

    /**
     * {@code name}, or the first variation of it that is free — or that already holds exactly
     * {@code content}, because the same file arriving again should be recognised, not copied. An
     * import therefore never overwrites anything: a taken name either matches (and is reused) or
     * is stepped past, the way a desktop file manager renames a colliding copy.
     */
    private String available(String name, byte[] content) {
        String stem = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            extension = name.substring(dot);
        }
        for (int n = 1; n < 1000; n++) {
            String candidate = n == 1 ? name : stem + "-" + n + extension;
            File file = new File(dir, candidate);
            if (!file.exists() || (file.isFile() && sameContent(file, content))) {
                return candidate;
            }
        }
        throw new IllegalStateException("too many files named " + name);
    }

    /**
     * Whether this file holds exactly these bytes.
     *
     * Staged so a mismatch is caught as early as it can be: the length first, then the head and
     * the tail (where formats put their headers and their tables of contents), then a handful of
     * sampled positions through the middle, and only then — when the file has matched everywhere
     * it was probed — the whole thing. The samples are seeded from the length, so the same
     * comparison always probes the same places and a flaky pass cannot exist.
     */
    static boolean sameContent(File file, byte[] content) {
        if (file.length() != content.length) {
            return false;
        }
        try {
            java.io.RandomAccessFile in = new java.io.RandomAccessFile(file, "r");
            try {
                int edge = Math.min(4096, content.length);
                if (!matches(in, content, 0, edge)
                        || !matches(in, content, content.length - edge, edge)) {
                    return false;
                }
                java.util.Random sampler = new java.util.Random(content.length);
                for (int i = 0; i < 8 && content.length > 2 * 4096; i++) {
                    int at = 4096 + sampler.nextInt(content.length - 2 * 4096);
                    if (!matches(in, content, at, Math.min(512, content.length - at))) {
                        return false;
                    }
                }
                in.seek(0);
                byte[] buffer = new byte[8192];
                int offset = 0;
                while (offset < content.length) {
                    int read = in.read(buffer, 0, Math.min(buffer.length, content.length - offset));
                    if (read <= 0) {
                        return false;
                    }
                    for (int i = 0; i < read; i++) {
                        if (buffer[i] != content[offset + i]) {
                            return false;
                        }
                    }
                    offset += read;
                }
                return true;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // A file that cannot be read cannot be called the same file.
            return false;
        }
    }

    private static boolean matches(java.io.RandomAccessFile in, byte[] content, int at, int count)
            throws IOException {
        byte[] chunk = new byte[count];
        in.seek(at);
        in.readFully(chunk);
        for (int i = 0; i < count; i++) {
            if (chunk[i] != content[at + i]) {
                return false;
            }
        }
        return true;
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
