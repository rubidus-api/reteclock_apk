# T004: KitKat Install

## Requirement

R1, R2: the APK installs and runs on Android 4.4 (API 19).

## Verification Method

`scripts/verify-kitkat.sh`: boots a headless API 19 armeabi-v7a system image (no KVM needed),
installs the APK, starts the clock in portrait and landscape, pulls a screenshot of each, and greps
logcat for a fatal exception.

## Assertions

- `adb install` succeeds on API 19, which requires a valid v1 (JAR) signature.
- The activity starts in both orientations.
- No `FATAL EXCEPTION` appears in logcat.
- The screenshots in `build/verify/` show the expected layouts.
