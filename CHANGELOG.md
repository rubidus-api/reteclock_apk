# Changelog

All notable changes to this project will be documented in this file.

This project follows Keep a Changelog.

## [Unreleased]

### Added

### Changed

### Fixed

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
