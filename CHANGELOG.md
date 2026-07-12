# Changelog

All notable changes to this project will be documented in this file.

This project follows Keep a Changelog.

## [Unreleased]

## [0.2.0] - 2026-07-12

### Added

- Settings screen, opened by long pressing the clock: show seconds on/off, date format
  (`Jul 12` or `07-12`), and start-when-charging on/off.
- The supported Android range is stated in the README and shown on the settings screen.

### Changed

- The hour and the minute are bold and as large as the screen allows: the layout reserves space for
  the smaller lines first and gives everything that is left to the big time. Lines that would be too
  wide are scaled down instead of clipped.
- Months are three-letter abbreviations (`Jul`) in both orientations.
- All text is white; the secondary lines are no longer dimmed.
- Turning the seconds off frees their line, and the hour and minute grow into it.

## [0.1.0] - 2026-07-12

### Added

- Full-screen 24-hour digital clock with wide and tall layouts.
- Burn-in protection: the drawing shifts within a small disc, one step per minute.
- Start when the charger is connected, toggled with a long press.
- Daydream screensaver service for Android 4.2 and newer.
- Command-line build (aapt2, javac, d8, zipalign, apksigner) producing a v1+v2+v3 signed APK.
- JVM unit tests for the clock core.
- Emulator check that installs the APK on a real Android 4.4 (API 19) system image.

### Compatibility

- minSdkVersion 9 (Android 2.3), targetSdkVersion 28, compiled against the API 19 platform.
- Verified on Android 4.4.2: installs, runs in both orientations, no fatal exception.
- Single dex, 54 KB, no AndroidX, no Kotlin runtime, no third-party dependency.
- Signed with the v1 (JAR), v2 and v3 schemes.
