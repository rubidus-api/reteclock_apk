package com.reteclock;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.reteclock.core.TimerLogRow;
import com.reteclock.core.TimerLogSpace;
import com.reteclock.core.TimerPreset;

/**
 * The timer log on disc: one CSV line per run, packed away as it grows, thrown away as it fills.
 *
 * Everything is inside the app's own files directory, which no other app can read and which goes
 * when the app is uninstalled. Nothing here leaves the phone; the only way out is the export the
 * user asks for on the *Timer settings* screen.
 *
 * The shape is one open file — `timer-log.csv` — and any number of packed ones — `timer-log-<unix
 * seconds>.zip`, each holding the CSV it was made from. When the open file passes 100 KB it is
 * zipped and a fresh one started; when everything kept passes the ceiling the user set, the oldest
 * zips go until it is under the floor. See {@link TimerLogSpace} and RFC-0006.
 *
 * Every method here swallows its own failures. A log is a convenience, and a timer that stopped
 * working because a disc was full would be the feature doing harm.
 */
public final class TimerLog {

    private static final String FOLDER = "timer-log";
    private static final String OPEN_FILE = "timer-log.csv";
    private static final String PACKED_PREFIX = "timer-log-";
    private static final String PACKED_SUFFIX = ".zip";

    private TimerLog() {
    }

    /** Where the log lives; made on the way if it is not there yet. */
    private static File folder(Context context) {
        File dir = new File(context.getFilesDir(), FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File openFile(Context context) {
        return new File(folder(context), OPEN_FILE);
    }

    /**
     * Writes down a run that has just ended.
     *
     * @param startEpochMs when it began, by the wall clock
     * @param elapsedMs    how long it ran, with time paused already taken out
     */
    public static void write(Context context, TimerPreset preset, long startEpochMs,
            long elapsedMs) {
        if (context == null || preset == null || !Settings.timerLogKept(context)) {
            return;
        }
        int offsetMinutes = TimeZone.getDefault().getOffset(startEpochMs) / 60000;
        TimerLogRow row = TimerLogRow.of(preset, startEpochMs, offsetMinutes, elapsedMs);
        append(context, row.line());
    }

    private static void append(Context context, String line) {
        File file = openFile(context);
        OutputStream out = null;
        try {
            boolean fresh = !file.exists() || file.length() == 0L;
            out = new FileOutputStream(file, true);
            if (fresh) {
                out.write((TimerLogRow.HEADER + "\n").getBytes("UTF-8"));
            }
            out.write((line + "\n").getBytes("UTF-8"));
        } catch (IOException cannotWrite) {
            // A log that cannot be written is a log that is not written. The timer carries on.
            return;
        } catch (RuntimeException cannotWrite) {
            return;
        } finally {
            close(out);
        }
        if (TimerLogSpace.shouldRotate(file.length())) {
            rotate(context, file);
        }
        trim(context);
    }

    /** Packs the open file away under the moment it was closed, and starts a fresh one. */
    private static void rotate(Context context, File open) {
        File packed = new File(folder(context),
                PACKED_PREFIX + (System.currentTimeMillis() / 1000L) + PACKED_SUFFIX);
        if (packed.exists()) {
            // Two rotations inside one second: the second waits for the next line rather than
            // writing over the first.
            return;
        }
        ZipOutputStream zip = null;
        InputStream in = null;
        try {
            zip = new ZipOutputStream(new FileOutputStream(packed));
            zip.putNextEntry(new ZipEntry(nameFor(packed)));
            in = new FileInputStream(open);
            copy(in, zip);
            zip.closeEntry();
            zip.finish();
        } catch (IOException cannotPack) {
            close(in);
            close(zip);
            packed.delete();
            return;
        } catch (RuntimeException cannotPack) {
            close(in);
            close(zip);
            packed.delete();
            return;
        } finally {
            close(in);
            close(zip);
        }
        open.delete();
    }

    /** `timer-log-1787807109.csv` — the member inside a packed file, named after the file. */
    private static String nameFor(File packed) {
        String name = packed.getName();
        if (name.endsWith(PACKED_SUFFIX)) {
            name = name.substring(0, name.length() - PACKED_SUFFIX.length());
        }
        return name + ".csv";
    }

    /** The packed files, oldest first. Their names hold the moment they were packed. */
    private static List<File> packed(Context context) {
        File[] all = folder(context).listFiles();
        List<File> out = new ArrayList<File>();
        if (all == null) {
            return out;
        }
        for (File file : all) {
            if (file.isFile() && file.getName().startsWith(PACKED_PREFIX)
                    && file.getName().endsWith(PACKED_SUFFIX)) {
                out.add(file);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                // By name, which is by the moment packed — and by name rather than by the file's
                // own timestamp, because a copy or a restore rewrites a timestamp and cannot
                // rewrite a name.
                return a.getName().compareTo(b.getName());
            }
        });
        return out;
    }

    /** Deletes the oldest packed files until what is kept is back under the floor. */
    private static void trim(Context context) {
        List<File> files = packed(context);
        List<Long> sizes = new ArrayList<Long>();
        for (int i = 0; i < files.size(); i++) {
            sizes.add(files.get(i).length());
        }
        List<File> going = TimerLogSpace.toDelete(files, sizes,
                Settings.timerLogCeilingMb(context), Settings.timerLogFloorMb(context));
        for (int i = 0; i < going.size(); i++) {
            going.get(i).delete();
        }
    }

    /** How much of the phone the log is using, packed files and the open one together. */
    public static long bytesKept(Context context) {
        long total = openFile(context).length();
        List<File> files = packed(context);
        for (int i = 0; i < files.size(); i++) {
            total += files.get(i).length();
        }
        return total;
    }

    /** Whether there is anything written down at all. */
    public static boolean isEmpty(Context context) {
        return bytesKept(context) <= 0L;
    }

    /** Throws the whole log away. */
    public static void deleteAll(Context context) {
        List<File> files = packed(context);
        for (int i = 0; i < files.size(); i++) {
            files.get(i).delete();
        }
        openFile(context).delete();
    }

    /**
     * Writes the whole log out as one zip of CSV files.
     *
     * The packed files are unpacked into it rather than nested inside it: a zip of zips is a thing
     * somebody has to unpack twice before a spreadsheet will look at it.
     */
    public static void exportTo(Context context, OutputStream sink) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(sink);
        try {
            List<File> files = packed(context);
            for (int i = 0; i < files.size(); i++) {
                copyMembers(files.get(i), zip);
            }
            File open = openFile(context);
            if (open.exists() && open.length() > 0L) {
                zip.putNextEntry(new ZipEntry(OPEN_FILE));
                InputStream in = new FileInputStream(open);
                try {
                    copy(in, zip);
                } finally {
                    close(in);
                }
                zip.closeEntry();
            }
            zip.finish();
        } finally {
            zip.flush();
        }
    }

    /** Copies what is inside one packed file into the export, entry by entry. */
    private static void copyMembers(File packed, ZipOutputStream out) throws IOException {
        ZipInputStream in = new ZipInputStream(new FileInputStream(packed));
        try {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    out.putNextEntry(new ZipEntry(entry.getName()));
                    copy(in, out);
                    out.closeEntry();
                }
                entry = in.getNextEntry();
            }
        } catch (IOException damaged) {
            // A packed file that cannot be read is left out rather than stopping the export: the
            // rest of the log is still worth having.
            return;
        } finally {
            close(in);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read = in.read(buffer);
        while (read > 0) {
            out.write(buffer, 0, read);
            read = in.read(buffer);
        }
    }

    private static void close(java.io.Closeable what) {
        if (what == null) {
            return;
        }
        try {
            what.close();
        } catch (IOException ignored) {
            // Nothing useful to do about a file that will not close.
        }
    }

    /** A name for an exported log: reteclock-timer-log-20260827-1405.zip. */
    public static String exportName() {
        return "reteclock-timer-log-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                        .format(new java.util.Date())
                + ".zip";
    }
}
