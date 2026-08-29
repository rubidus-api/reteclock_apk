package com.reteclock;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.reteclock.core.layout.LayoutFiles;
import com.reteclock.core.layout.LayoutPreset;

/**
 * The pictures a layout carries, on disc: one folder per layout (issue #48, RFC-0010).
 *
 * A layout arranged around a photograph is half a design when the photograph is chosen somewhere
 * else, so a layout may bring its own — and it brings **copies**. A reference into the shared pool
 * would leave every layout hostage to it: rename a picture, delete it, import somebody else's
 * settings, and a layout designed around it quietly loses its ground. A copy costs disc and answers
 * the question once (D1).
 *
 *     files/layouts/<layout>/           the pictures this layout carries
 *
 * The folder is named after the layout and follows it: renamed with it, copied with it, deleted
 * with it. The export writes the same shape into the zip, so a layout travels whole and somebody
 * can open the zip, swap a picture, and put it back (D2).
 *
 * Everything here is best effort. A picture that will not copy is a picture the layout does not
 * carry; nothing about the clock depends on it.
 */
final class LayoutSkins {

    private static final String DIR = "layouts";

    private LayoutSkins() {
    }

    /** Where every layout's folder lives. */
    static File root(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** This layout's own folder, made on the way if it is not there yet. */
    static File folder(Context context, LayoutPreset preset) {
        return folder(context, preset.name, preset.landscape);
    }

    static File folder(Context context, String presetName, boolean landscape) {
        File dir = new File(root(context), LayoutFiles.folderName(presetName, landscape));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** One picture this layout carries, or null when the file is not there. */
    static File file(Context context, LayoutPreset preset, String fileName) {
        if (fileName == null || fileName.length() == 0) {
            return null;
        }
        File found = new File(folder(context, preset), fileName);
        return found.isFile() ? found : null;
    }

    /**
     * Copies a picture out of the shared pool into this layout's folder.
     *
     * @return the name it is kept under, or null when it could not be copied
     */
    static String take(Context context, LayoutPreset preset, String poolName) {
        File source = Settings.images(context).file(poolName);
        if (source == null) {
            return null;
        }
        File into = new File(folder(context, preset), poolName);
        if (into.isFile() && into.length() == source.length()) {
            return poolName;              // already carried; copying it again would change nothing
        }
        return copy(source, into) ? poolName : null;
    }

    /**
     * Puts a picture that arrived in a package into this layout's folder.
     *
     * @param file what the import staged; it is copied rather than moved, because staging is
     *             cleared as a whole and a moved file would leave a hole in it
     * @return whether it landed
     */
    static boolean bring(Context context, LayoutPreset preset, String fileName, File file) {
        if (fileName == null || fileName.length() == 0 || file == null || !file.isFile()) {
            return false;
        }
        return copy(file, new File(folder(context, preset), fileName));
    }

    /** Throws away a picture this layout carries. The pool's own copy is untouched. */
    static void drop(Context context, LayoutPreset preset, String fileName) {
        File found = file(context, preset, fileName);
        if (found != null) {
            found.delete();
        }
    }

    /** What is actually in this layout's folder, whatever the preset says it carries. */
    static List<String> inFolder(Context context, LayoutPreset preset) {
        List<String> out = new ArrayList<String>();
        File[] files = folder(context, preset).listFiles();
        if (files == null) {
            return out;
        }
        for (File file : files) {
            if (file.isFile() && !LayoutFiles.PRESET_FILE.equals(file.getName())) {
                out.add(file.getName());
            }
        }
        return out;
    }

    /** The folder follows a rename, so the layout does not lose what it was carrying. */
    static void renamed(Context context, String oldName, String newName, boolean landscape) {
        File from = new File(root(context), LayoutFiles.folderName(oldName, landscape));
        File to = new File(root(context), LayoutFiles.folderName(newName, landscape));
        if (!from.isDirectory() || from.equals(to) || to.exists()) {
            return;
        }
        if (!from.renameTo(to)) {
            copyFolder(from, to);
            deleteFolder(from);
        }
    }

    /** A copied layout — a duplicate, or one turned the other way up — carries copies of them. */
    static void copied(Context context, LayoutPreset from, LayoutPreset to) {
        copyFolder(folder(context, from), folder(context, to));
    }

    /** A deleted layout takes its pictures with it; a folder nobody owns is a cache that never empties. */
    static void forget(Context context, LayoutPreset preset) {
        deleteFolder(new File(root(context), LayoutFiles.folderName(preset.name,
                preset.landscape)));
    }

    /**
     * The folders no layout in this book owns any more.
     *
     * Deleting a layout should take its folder, and every path through the book is meant to. This
     * is the sweep that catches what a crash, an import or an older version left behind — asked for
     * when the Layout screen opens, which is the moment somebody is looking at the list anyway.
     */
    static void sweep(Context context, com.reteclock.core.layout.LayoutBook book) {
        File[] folders = root(context).listFiles();
        if (folders == null) {
            return;
        }
        List<String> wanted = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {
            boolean landscape = i == 1;
            for (int at = 0; at < book.size(landscape); at++) {
                wanted.add(LayoutFiles.folderName(book.get(landscape, at).name, landscape));
            }
        }
        for (File folder : folders) {
            if (folder.isDirectory() && !wanted.contains(folder.getName())) {
                deleteFolder(folder);
            }
        }
    }

    private static void copyFolder(File from, File to) {
        File[] files = from.listFiles();
        if (files == null) {
            return;
        }
        if (!to.exists()) {
            to.mkdirs();
        }
        for (File file : files) {
            if (file.isFile()) {
                copy(file, new File(to, file.getName()));
            }
        }
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        folder.delete();
    }

    private static boolean copy(File from, File to) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(from);
            out = new FileOutputStream(to);
            byte[] buffer = new byte[8192];
            int read = in.read(buffer);
            while (read > 0) {
                out.write(buffer, 0, read);
                read = in.read(buffer);
            }
            return true;
        } catch (IOException cannotCopy) {
            to.delete();
            return false;
        } catch (RuntimeException cannotCopy) {
            to.delete();
            return false;
        } finally {
            close(in);
            close(out);
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
}
