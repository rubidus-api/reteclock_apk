#!/bin/sh
# End-to-end check on a real Android 4.4 (API 19) system image.
#
#   scripts/verify-kitkat.sh [path-to-apk]
#
# Boots a headless API 19 emulator, installs the APK, starts the clock in portrait and in
# landscape, and pulls a screenshot of each. Screenshots land in build/verify/.
#
# The x86 system images need KVM; this script therefore uses the armeabi-v7a image, which runs
# under plain emulation. It is slow (several minutes to boot) but needs no special host support.
#
# Requirements: the emulator package, system-images;android-19;default;armeabi-v7a, and an AVD.
#
#   sdkmanager "emulator" "system-images;android-19;default;armeabi-v7a"
#   avdmanager create avd -n kitkat -k "system-images;android-19;default;armeabi-v7a" -d "Nexus 4"

set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/env.sh"

require_jdk
require_sdk

AVD="${RETECLOCK_AVD:-kitkat}"
APK="${1:-}"
if [ -z "$APK" ]; then
    APK=$(ls -t "$ROOT"/dist/*.apk 2>/dev/null | head -1)
fi
[ -n "$APK" ] && [ -f "$APK" ] || fail "no APK found; run scripts/build.sh first"

ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
[ -x "$EMULATOR" ] || fail "emulator package is not installed"

OUT="$ROOT/build/verify"
rm -rf "$OUT"
mkdir -p "$OUT"

cleanup() {
    "$ADB" emu kill >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> booting $AVD (armeabi-v7a, no KVM: this takes a few minutes)"
"$EMULATOR" -avd "$AVD" -no-window -no-audio -no-snapshot -no-boot-anim -gpu off \
    >"$OUT/emulator.log" 2>&1 &

"$ADB" wait-for-device
i=0
while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != "1" ]; do
    i=$((i + 1))
    [ "$i" -gt 180 ] && fail "emulator did not finish booting"
    sleep 10
done
echo "    booted: Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r\n')" \
     "(API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r\n'))"

echo "==> install"
"$ADB" install -r "$APK"

"$ADB" shell settings put system accelerometer_rotation 0

shoot() {
    rotation=$1
    name=$2
    "$ADB" shell settings put system user_rotation "$rotation"
    "$ADB" shell am start -n com.reteclock/.ClockActivity >/dev/null
    sleep 6
    "$ADB" shell screencap -p /sdcard/reteclock.png
    "$ADB" pull /sdcard/reteclock.png "$OUT/$name.png" >/dev/null
    echo "    wrote build/verify/$name.png"
    "$ADB" shell am force-stop com.reteclock
}

echo "==> screenshots"
shoot 0 portrait
shoot 1 landscape

echo "==> crash check"
if "$ADB" logcat -d | grep -q "FATAL EXCEPTION"; then
    "$ADB" logcat -d | grep -A 12 "FATAL EXCEPTION" | head -20
    fail "the app crashed on API 19"
fi

echo
echo "verified on API 19: installed, started, no fatal exception"
