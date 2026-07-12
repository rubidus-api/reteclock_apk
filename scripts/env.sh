#!/bin/sh
# Toolchain locations for the build and test scripts.
#
# Nothing here points at a machine-specific path. Set the variables in the environment, or put
# them in scripts/env.local.sh (untracked), which this file sources when it exists:
#
#   JAVA_HOME          JDK 17 or newer (javac, keytool)
#   ANDROID_SDK_ROOT   Android SDK root (also accepted: ANDROID_HOME)
#   ANDROID_BUILD_TOOLS_VERSION  build-tools directory name, default 34.0.0
#   ANDROID_COMPILE_API          platform whose android.jar is compiled against, default 19
#   JUNIT_JAR          junit-platform-console-standalone jar, for scripts/test.sh
#   RETECLOCK_KEYSTORE, RETECLOCK_KEY_ALIAS, RETECLOCK_KEYSTORE_PASS
#                      release signing key; when unset, build.sh creates a local development
#                      key under build/ so a fresh checkout can still produce an installable APK.

set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)

if [ -f "$ROOT/scripts/env.local.sh" ]; then
    . "$ROOT/scripts/env.local.sh"
fi

: "${ANDROID_SDK_ROOT:=${ANDROID_HOME:-}}"
: "${ANDROID_BUILD_TOOLS_VERSION:=34.0.0}"
: "${ANDROID_COMPILE_API:=19}"

fail() {
    echo "reteclock: $1" >&2
    exit 1
}

require_jdk() {
    [ -n "${JAVA_HOME:-}" ] || fail "JAVA_HOME is not set (see scripts/env.sh)"
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
    KEYTOOL="$JAVA_HOME/bin/keytool"
    [ -x "$JAVAC" ] || fail "no javac in JAVA_HOME"
    # d8, apksigner and zipalign are shell wrappers that call plain `java`.
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH JAVA_HOME
}

require_sdk() {
    [ -n "$ANDROID_SDK_ROOT" ] || fail "ANDROID_SDK_ROOT is not set (see scripts/env.sh)"
    BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION"
    ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-$ANDROID_COMPILE_API/android.jar"
    AAPT2="$BUILD_TOOLS/aapt2"
    D8="$BUILD_TOOLS/d8"
    ZIPALIGN="$BUILD_TOOLS/zipalign"
    APKSIGNER="$BUILD_TOOLS/apksigner"
    [ -d "$BUILD_TOOLS" ] || fail "missing build-tools $ANDROID_BUILD_TOOLS_VERSION"
    [ -f "$ANDROID_JAR" ] || fail "missing platform android-$ANDROID_COMPILE_API"
}
