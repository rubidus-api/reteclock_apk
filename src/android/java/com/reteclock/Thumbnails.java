package com.reteclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.reteclock.core.ThumbnailPlan;

/**
 * Small copies of the imported pictures, made once and kept, so a list of them can be looked at.
 *
 * A picture is chosen by looking at it, and looking must not cost what showing costs: a settings
 * screen listing twenty photographs cannot decode twenty photographs. Each is decoded once at a
 * step of two, four or eight — see {@link ThumbnailPlan} — written out as a small PNG, and held in
 * memory while the screen is open.
 *
 * **They live in the cache directory, and that is a decision rather than a convenience.** A
 * thumbnail is not something the user made: it can be rebuilt from the picture it came from in a
 * few milliseconds, and a picture it cannot be rebuilt from is a picture that is gone anyway. Kept
 * there, it is left out of the app's export — which already carries the originals, and which
 * somebody may well send over a telephone connection — and out of the platform's own backup, which
 * excludes the cache by rule. The system may also reclaim the space when the phone is short, and
 * the only cost of that is one decode.
 *
 * See RFC-0007.
 */
final class Thumbnails {

    private static final String DIR = "thumbs";
    /** How many are held in memory at once. A settings list shows a screenful; this is generous. */
    private static final int HELD = 24;

    private static final Map<String, Bitmap> held = new HashMap<String, Bitmap>();

    private Thumbnails() {
    }

    static File dir(Context context) {
        File dir = new File(context.getCacheDir(), DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** The file for one picture's thumbnail; the name is the picture's, which is already safe. */
    private static File fileFor(Context context, String imageName) {
        return new File(dir(context), imageName + ".thumb.png");
    }

    /**
     * The thumbnail for one imported picture, made if it is not there yet, or null if the picture
     * cannot be read.
     *
     * Everything here is best effort. A preview that cannot be drawn is a preview that is not
     * drawn; nothing about the clock depends on it.
     */
    static Bitmap of(Context context, String imageName) {
        if (context == null || imageName == null || imageName.length() == 0) {
            return null;
        }
        Bitmap known = held.get(imageName);
        if (known != null && !known.isRecycled()) {
            return known;
        }
        File thumb = fileFor(context, imageName);
        Bitmap made = read(thumb);
        if (made == null) {
            made = build(context, imageName, thumb);
        }
        if (made != null) {
            if (held.size() >= HELD) {
                held.clear();
            }
            held.put(imageName, made);
        }
        return made;
    }

    private static Bitmap read(File file) {
        if (!file.isFile() || file.length() == 0L) {
            return null;
        }
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (OutOfMemoryError tooLarge) {
            return null;
        } catch (RuntimeException damaged) {
            return null;
        }
    }

    /**
     * Decodes the picture small and keeps the result.
     *
     * The first pass reads the header only — `inJustDecodeBounds` — so the step is chosen from the
     * picture's real size rather than guessed. An animation is decoded by the same route, which
     * gives its first frame: a still of a moving picture is what a list of pictures wants.
     */
    private static Bitmap build(Context context, String imageName, File thumb) {
        File source = Settings.images(context).file(imageName);
        if (source == null || !source.isFile()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        } catch (RuntimeException unreadable) {
            return null;
        }
        ThumbnailPlan plan = ThumbnailPlan.of(bounds.outWidth, bounds.outHeight);
        if (plan == null) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = plan.sample;
        // A preview is drawn behind nothing and read at the size of a postage stamp; sixteen bits
        // a pixel is half the memory and no visible difference.
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap made;
        try {
            made = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        } catch (OutOfMemoryError tooLarge) {
            return null;
        } catch (RuntimeException damaged) {
            return null;
        }
        if (made == null) {
            return null;
        }
        write(made, thumb);
        return made;
    }

    private static void write(Bitmap bitmap, File file) {
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (IOException cannotWrite) {
            file.delete();
        } catch (RuntimeException cannotWrite) {
            file.delete();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Nothing useful to do about a file that will not close.
                }
            }
        }
    }

    /** Throws away what was made for one picture: it has been renamed, replaced or deleted. */
    static void forget(Context context, String imageName) {
        held.remove(imageName);
        fileFor(context, imageName).delete();
    }

    /** Throws away every thumbnail. They come back as they are looked at. */
    static void forgetAll(Context context) {
        held.clear();
        File[] files = dir(context).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }
}
