package com.reteclock.core;

/**
 * What the touch menu says about the app underneath its choices: what this is, which version, where
 * it lives, and what it runs on.
 *
 * Here rather than in a resource because it is assembled from parts and worth testing. The
 * addresses are deliberately written out in full: issue #22 came from somebody running the app on a
 * device that F-Droid no longer reaches, over a charging port that cannot carry data. For them the
 * text on the screen is the only way back to the project.
 */
public final class AboutText {

    public static final String GITHUB = "github.com/rubidus-api/reteclock_apk";
    public static final String FDROID = "f-droid.org/packages/com.reteclock";
    /** The oldest Android the APK installs on; see the manifest's minSdkVersion. */
    public static final String OLDEST = "Android 2.3";

    private AboutText() {
    }

    /**
     * @param version what this build calls itself, or null when it cannot be read
     * @param androidRelease the version of Android this device is running, or null
     */
    public static String of(String version, String androidRelease) {
        StringBuilder out = new StringBuilder("ReteClock");
        if (version != null && !version.isEmpty()) {
            out.append(' ').append(version);
        }
        out.append('\n').append(GITHUB);
        out.append('\n').append(FDROID);
        out.append('\n').append(OLDEST).append(" and newer");
        if (androidRelease != null && !androidRelease.isEmpty()) {
            out.append("  ·  this device: Android ").append(androidRelease);
        }
        return out.toString();
    }
}
