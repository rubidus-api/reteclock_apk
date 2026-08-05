# reteclock

A full-screen digital clock and dock screensaver for Android, built for old devices first.

Plug the phone into a charger, put it on a stand, and it shows the time in large digits with the
weekday and date. It keeps the screen on, adapts to landscape and portrait, and slowly shifts what
it draws so an OLED panel does not burn in.

## Download

**[⬇ Get it on F-Droid](https://f-droid.org/packages/com.reteclock/)** — recommended.

**[⬇ Download reteclock-0.6.0.apk](https://github.com/rubidus-api/reteclock_apk/releases/latest/download/reteclock-0.6.0.apk)** — 91 KB, installs on Android 2.3 and newer.

Open the file on the phone to install it. On Android 4.4, enable Settings > Security > Unknown
sources first. All releases: [Releases](https://github.com/rubidus-api/reteclock_apk/releases).

### Why F-Droid is the safer place to get it

Downloading an APK from anywhere means trusting whoever built it. There is nothing in a compiled
APK that shows it was made from the source you can read here, and nothing that shows the account
that uploaded it was not taken over.

F-Droid removes that trust from the equation. The listing is a
[reproducible build](https://f-droid.org/docs/Reproducible_Builds): F-Droid checks out this
repository on its own server, compiles it there, and compares the bytes against the APK published
above. It publishes only if they match. So the file F-Droid gives you is one that an independent
party has confirmed comes from the source in front of you — not just one that someone with push
access to this repository uploaded. Their client checks the signature on every update too.

Both places serve the exact same signed file, so you can move between the two without uninstalling.
If you have any doubt about the download here, use F-Droid.

The one cost is time: F-Droid rebuilds and republishes on its own schedule, so their copy is
sometimes a release or two behind this page. Waiting for it is the trade you make for having
someone else check the build.

Details of the recipe and how it is verified: [`docs/fdroid/README.md`](docs/fdroid/README.md).

## Status

Published on F-Droid. Latest release 0.6.0. Reference platform: Android 4.4 KitKat (API 19).

## Supported Android versions

| | |
|---|---|
| Minimum | **Android 2.3 Gingerbread (API 9)** |
| Built and tested on | **Android 4.4 KitKat (API 19)** — the reference platform |
| Maximum | **no upper limit**: `targetSdkVersion 28` keeps current Android versions (14, 15, 16 …) willing to install the APK |

The settings screen shows the same range on the device, together with the Android version it is
running on.

## What it is for

An old Android phone in a drawer is still a perfectly good screen with a perfectly good clock in it.
This app is for exactly that: put the phone on a stand, plug in the charger, and let it be a bedside
or desk clock.

Old phones are hard to use for much else. Google has been retiring the old Android versions step by
step — signing in to a Google account stopped working on Android 2.3.7 and older in September 2021,
Google Play services stopped shipping updates for Android 4.4 KitKat in 2023, and most apps in the
Play Store now require a much newer Android. A phone that can no longer install anything can still
show the time.

So reteclock asks for nothing: no Play Store, no Google account, no network, no permissions beyond
keeping the screen awake. Copy the APK to the phone, open it, and the phone has a job again.

## The name

**rete** is Latin for *net*, and *-clock* is just a clock.

The intended pronunciation is the Latin one: **RAY-teh** (two syllables, `rē-te`; the first vowel is
the long *e* of *they*, and the final *e* is pronounced, never silent).

If you would rather say it the way English usually treats this word, that is fine too. English
borrowed *rete* as an anatomical term and pronounces it **REE-tee**, so "REE-tee-clock" is a
perfectly good reading. Say it however you like; the clock does not mind.

## What it looks like

Screenshots taken on Android 4.4.2.

| Landscape (wide) | Portrait (tall) |
|---|---|
| <img src="docs/screenshots/landscape.png" alt="Landscape: a large bold HH:MM on the left, with seconds, weekday, date and year in a column on the right" width="420"> | <img src="docs/screenshots/portrait.png" alt="Portrait: bold hour and minute stacked, then the weekday with the date, then the year and the seconds" width="210"> |

The hour and the minute take every pixel the other lines do not need. The remaining lines are
scaled to the space that is left, so nothing is ever clipped. The text and background colours are
yours to pick. Weight is one of the
per-field decorations; the hour and the minute have it on to begin with, and you can turn it off.

Every field can be drawn in a font of your own, with its own bold, italic and underline. Below, the
hour is a TrueType face, the minute a font collection, the weekday and date an OpenType one, the
year is italic and the seconds are underlined — and the app worked out every size.

<img src="docs/screenshots/fonts.png" alt="Portrait clock with a different user font on the hour, the minute and the date line, an italic year and underlined seconds" width="210">

You can also put your own pictures behind the clock — several take turns, animated GIFs play —
fill the digits themselves with a picture, and decide how much of the screen the time takes.
See Settings below.

## Compatibility

- `minSdkVersion 9` (Android 2.3) through current Android; built and verified against the
  Android 4.4 (API 19) platform.
- Framework APIs only: no AndroidX, no support library, no Kotlin runtime, no third-party
  dependency, a single `classes.dex`, and an APK well under 100 KB.
- Signed with the v1 (JAR) scheme so old devices accept it, plus v2 and v3 so current Android
  versions accept it.
- Only a normal permission (`WAKE_LOCK`); nothing is requested at runtime.

## Settings

Press and hold the clock to open the settings screen. The clock says so once when you open it, and
stops saying it once you have been there — and never says it when the charger started the clock, so
it stays quiet on a bedside stand.

<img src="docs/screenshots/settings.png" alt="Settings: show seconds, date format, and the font library listing each font's size and the total" width="240"> <img src="docs/screenshots/fields.png" alt="Settings, further down: each field on two lines — its name with bold, italic and underline, then its font — and the accepted file kinds" width="240">


In the order they appear on the screen:

- **Show seconds** — on or off. With the seconds off, the hour and the minute grow into the freed space.
- **Date format** — `Jul 12` (abbreviated month name) or `07-12` (numeric).
- **Colours** — the text colour and the background colour, each from a palette. The two can never
  be the same (a clock in its own background colour is no clock), and the background colour shows
  wherever no image does — including the letterbox bars of the show-it-all fit.
- **Font** — the clock draws in the system font unless you add your own. *Add a font from a file*
  opens the system file picker; the file is copied into the app, so reteclock never asks for
  storage permission and the font keeps working if you move or delete the original. TrueType
  (`.ttf`), OpenType (`.otf`) and font collections (`.ttc`) all load — a collection was tried on
  Android 4.4 rather than assumed — and anything the device cannot read is refused on the spot
  rather than left in the list. The list shows each font's size and the total
  they occupy, and any of them can be deleted.
- **Each field: font and decoration** — the hour, the minute, the seconds, the weekday, the month
  and day, and the year each choose their own font, and their own **B**old, **I**talic and
  **U**nderline. The hour and the minute start bold; you can turn that off. Sizes stay worked out
  by the app. Deleting a font that a field is using drops that field back to the system font. A
  font file holds one weight and one slant, so bold and italic are synthesised.
- **Room for the time** — two sliders, one per orientation: how much of the width the hour and
  minute take in landscape, how much of the height in portrait. Everything sizes itself to fill
  its share, and stops when it would not fit.
- **Images** — one pool for every picture, imported the same no-permission way the fonts arrive.
  Each image carries two exclusive ticks: **BG** puts it behind the clock, **Text** shows it
  inside the digits — the glyphs become a window onto it — and with neither it is held, kept but
  unused. Each tick column has a select-all; the first column selects for deleting many at once.
  The background images take turns: an animated GIF plays through once, a still holds for a time
  you choose (5 seconds to 5 minutes), one cross-fades into the next unless you turn the fade
  off, and six fit modes cover every shape. The text images cycle on the same rules. Sort the
  pool by name or by the date each file was added, either way round, arrange it by hand with the
  arrows, or tap a name to rename it — `001 …`, `002 …` makes the A-to-Z sort your order. A file
  the pool already holds is recognised on import and not stored twice.
- **Start when the charger is connected** — on or off.

## How it starts

- From the launcher, like any app.
- Automatically when the charger is connected (turn this on or off in the settings).
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
export JAVA_HOME=/path/to/jdk-21              # only --release insists on this exact major version
export ANDROID_SDK_ROOT=/path/to/android-sdk   # build-tools 35.0.0 + platforms;android-19

scripts/build.sh             # dist/reteclock-<version>-debug.apk, signed with a local dev key
scripts/build.sh --release   # signed with your own release key (RETECLOCK_KEYSTORE)
scripts/build.sh --unsigned  # dist/reteclock-<version>-unsigned.apk, for F-Droid to sign
```

`scripts/build.sh` runs `aapt2` → `javac` (source 8, against the API 19 platform) → `d8`
(`--min-api 9`) → `zipalign` → `apksigner` (v1 + v2 + v3). It needs a JDK, the Android SDK
command-line tools, and Python 3. Any JDK will do for a debug build; `--release` requires JDK 21,
because F-Droid rebuilds the app and compares the result against the published APK, and javac
versions do not agree on bytecode. Nothing else — no Gradle, no downloads, no dependencies.
See `scripts/env.sh` for every variable it reads.

This repository publishes only what is needed to build the app, plus this README and its
screenshots and the F-Droid submission material (`fastlane/`, `docs/fdroid/`). Working notes, plans
and test scaffolding are kept out of it on purpose.

## License

MIT. See `LICENSE`.
