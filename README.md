# reteclock

A full-screen digital clock and dock screensaver for Android, built for old devices first.

Plug the phone into a charger, put it on a stand, and it shows the time in large digits with the
weekday and date. It keeps the screen on, adapts to landscape and portrait, and slowly shifts what
it draws so an OLED panel does not burn in.

## Status

Working APK, version 0.1.0. Reference platform: Android 4.4 KitKat (API 19).

## What it looks like

Wide screen (landscape):

```text
+------------------------------+-------------+
|                              |   25s       |
|          13:45               |   Sun       |
|                              |   July 10   |
|                              |   2026      |
+------------------------------+-------------+
```

Tall screen (portrait):

```text
        13
        45
   Sun, July 10
    2026   25s
```

## Compatibility

- `minSdkVersion 9` (Android 2.3) through current Android; built and verified against the
  Android 4.4 (API 19) platform.
- Framework APIs only: no AndroidX, no support library, no Kotlin runtime, no third-party
  dependency, a single `classes.dex`, and an APK well under 100 KB.
- Signed with the v1 (JAR) scheme so old devices accept it, plus v2 and v3 so current Android
  versions accept it.
- Only a normal permission (`WAKE_LOCK`); nothing is requested at runtime.

## How it starts

- From the launcher, like any app.
- Automatically when the charger is connected (long press the clock to turn that on or off).
  Android 10 and newer block starting an activity from the background, so on those devices use
  the launcher or the screensaver instead.
- As a system screensaver (Daydream) on Android 4.2 and newer:
  Settings > Display > Daydream > reteclock.
- From a desk dock, if the device reports one.

## Stack

Java, Android framework only. Built with the Android SDK command-line tools (`aapt2`, `javac`,
`d8`, `zipalign`, `apksigner`) driven by POSIX shell scripts. No Gradle.

## Build

```sh
scripts/test.sh          # JVM unit tests for the clock core
scripts/build.sh         # dist/reteclock-<version>-debug.apk
scripts/build.sh --release
```

Set `JAVA_HOME`, `ANDROID_SDK_ROOT` and `JUNIT_JAR` first; see `scripts/env.sh` and
`docs/manual/build.md`.

## Documentation

- `docs/manual/` — build and install instructions
- `docs/agents/` — working rules for AI-assisted sessions

## License

MIT. See `LICENSE`.
