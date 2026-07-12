# Building reteclock

The build uses the Android SDK command-line tools directly. There is no Gradle and no IDE project.

## Requirements

- A JDK, 17 or newer (for `javac`, `keytool`, and the SDK tool wrappers).
- The Android SDK with:
  - `build-tools;34.0.0` (any recent build-tools works; set the version below),
  - `platforms;android-19` (the APK is compiled against this platform on purpose),
  - `platform-tools` and `emulator` plus `system-images;android-19;default;armeabi-v7a` if you also
    want the emulator check.
- `junit-platform-console-standalone` (one jar) for the unit tests.
- Python 3, used to add `classes.dex` to the APK because `aapt2` cannot and `zip` is not installed
  everywhere.

## Configuration

The scripts read the toolchain from the environment, so nothing machine-specific is committed:

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
export JUNIT_JAR=/path/to/junit-platform-console-standalone.jar
```

Optional: `ANDROID_BUILD_TOOLS_VERSION` (default `34.0.0`), `ANDROID_COMPILE_API` (default `19`).

Instead of exporting them, you can put the same lines in `scripts/env.local.sh`, which is untracked
and sourced automatically.

## Commands

```sh
scripts/test.sh              # unit tests for the clock core
scripts/build.sh             # dist/reteclock-<version>-debug.apk, signed with a local dev key
scripts/build.sh --release   # dist/reteclock-<version>.apk, signed with your release key
scripts/verify-kitkat.sh     # boot an API 19 emulator, install, screenshot both orientations
scripts/project-check.sh     # documentation and structure checks
```

## Release signing

`--release` needs a keystore that is kept outside the project tree:

```sh
export RETECLOCK_KEYSTORE=/path/outside/the/repo/reteclock.keystore
export RETECLOCK_KEY_ALIAS=reteclock
export RETECLOCK_KEYSTORE_PASS=...
```

Create one once with `keytool -genkeypair -keyalg RSA -keysize 2048 -validity 10000`. Keep the
keystore and its password in a private location; never commit them.

## What the build does

1. `aapt2 compile` + `aapt2 link` turn `src/android/res` and the manifest into `resources.apk`
   and generate `R.java`.
2. `javac -source 8 -target 8` compiles `src/core/java`, `src/android/java` and `R.java` against
   `platforms/android-19/android.jar`.
3. `d8 --min-api 9` produces a single `classes.dex`.
4. The dex is added to the APK, which is then zipaligned.
5. `apksigner` signs it with v1, v2 and v3, and the build prints the verification result.

## Icons

`tools/make-icons.py` regenerates the launcher PNGs from a drawn design. Run it only when the icon
changes; the PNGs are part of the source tree, so a normal build needs neither Python imaging nor
any download.
