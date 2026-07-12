#!/bin/sh
# End-to-end check on a real Android 4.4 (API 19) system image.
#
#   scripts/verify-kitkat.sh [path-to-apk]
#
# Boots a headless API 19 emulator, installs the APK, starts the clock in portrait and in
# landscape, and pulls a screenshot of each. Screenshots land in build/verify/.
#
# Use the x86 system image. Current emulator releases no longer run 32-bit ARM guests
# ("CPU Architecture 'arm' is not supported by the QEMU2 emulator"), and the x86 image also boots
# without KVM, just slowly, under plain emulation.
#
# Requirements: the emulator package, system-images;android-19;default;x86, and an AVD. Note that
# avdmanager needs ANDROID_AVD_HOME to exist, and exits 0 without creating anything if it does not:
#
#   sdkmanager "emulator" "platform-tools" "system-images;android-19;default;x86"
#   export ANDROID_AVD_HOME="$HOME/.android/avd" && mkdir -p "$ANDROID_AVD_HOME"
#   avdmanager create avd -n kitkat_x86 -k "system-images;android-19;default;x86"

set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/env.sh"

require_jdk
require_sdk

AVD="${RETECLOCK_AVD:-kitkat_x86}"
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

# Without KVM the emulator refuses to start unless acceleration is explicitly turned off.
ACCEL=""
[ -w /dev/kvm ] || ACCEL="-accel off"

echo "==> booting $AVD (this takes several minutes without KVM)"
# shellcheck disable=SC2086
"$EMULATOR" -avd "$AVD" -no-window -no-audio -no-snapshot -no-boot-anim \
    -gpu swiftshader_indirect -partition-size 2048 -wipe-data $ACCEL \
    >"$OUT/emulator.log" 2>&1 &

"$ADB" wait-for-device
i=0
while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != "1" ]; do
    i=$((i + 1))
    [ "$i" -gt 120 ] && fail "emulator did not finish booting"
    sleep 15
done
echo "    booted: Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r\n')" \
     "(API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r\n'))"

# sys.boot_completed can be set before the package manager can serve installs, and adb install then
# fails with INSTALL_FAILED_INVALID_URI. Wait for pm itself.
echo "==> waiting for the package manager"
i=0
until "$ADB" shell pm list packages 2>/dev/null | grep -q "package:android"; do
    i=$((i + 1))
    [ "$i" -gt 40 ] && fail "package manager never came up"
    sleep 15
done

echo "==> install"
"$ADB" logcat -c || true
"$ADB" install -r "$APK"

"$ADB" shell settings put system accelerometer_rotation 0

shoot() {
    rotation=$1
    name=$2
    "$ADB" shell settings put system user_rotation "$rotation"
    "$ADB" shell am start -n com.reteclock/.ClockActivity >/dev/null
    sleep 25
    "$ADB" shell screencap -p /data/local/tmp/reteclock.png
    "$ADB" pull /data/local/tmp/reteclock.png "$OUT/$name.png" >/dev/null
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
