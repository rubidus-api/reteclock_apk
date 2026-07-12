#!/bin/sh
# Builds the reteclock APK with the Android SDK command-line tools only: no Gradle, no AGP.
#
#   scripts/build.sh              debug-signed APK in dist/
#   scripts/build.sh --release    same pipeline, but requires RETECLOCK_KEYSTORE
#
# The APK is signed with v1 (JAR) plus v2 and v3, so Android 2.3..4.4 accept the v1 signature and
# modern Android accepts v2/v3.

set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/env.sh"

require_jdk
require_sdk

MIN_SDK=9
TARGET_SDK=28
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/src/android/AndroidManifest.xml")
[ -n "$VERSION" ] || fail "cannot read versionName from the manifest"

RELEASE=0
[ "${1:-}" = "--release" ] && RELEASE=1

OUT="$ROOT/build"
GEN="$OUT/gen"
CLASSES="$OUT/classes"
DEX="$OUT/dex"
FLAT="$OUT/res"
STAGE="$OUT/dist"

rm -rf "$GEN" "$CLASSES" "$DEX" "$FLAT" "$STAGE"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$FLAT" "$STAGE" "$ROOT/dist"

echo "==> aapt2 compile"
find "$ROOT/src/android/res" -type f | while read -r res; do
    "$AAPT2" compile -o "$FLAT" "$res"
done

echo "==> aapt2 link"
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest "$ROOT/src/android/AndroidManifest.xml" \
    --java "$GEN" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --no-version-vectors \
    -o "$STAGE/resources.apk" \
    "$FLAT"/*.flat

echo "==> javac (source/target 8, against android-$ANDROID_COMPILE_API)"
find "$ROOT/src/core/java" "$ROOT/src/android/java" "$GEN" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" \
    -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -classpath "$ANDROID_JAR" \
    -encoding UTF-8 \
    -nowarn \
    -d "$CLASSES" \
    @"$OUT/sources.txt"

echo "==> d8 (min-api $MIN_SDK)"
find "$CLASSES" -name '*.class' > "$OUT/classes.txt"
"$D8" \
    --release \
    --min-api "$MIN_SDK" \
    --lib "$ANDROID_JAR" \
    --output "$DEX" \
    @"$OUT/classes.txt"
[ -f "$DEX/classes2.dex" ] && fail "multiple dex files: the app must stay single-dex"

echo "==> package"
cp "$STAGE/resources.apk" "$STAGE/unsigned.apk"
"$ROOT/scripts/add-to-zip.py" "$STAGE/unsigned.apk" "$DEX/classes.dex" classes.dex

echo "==> zipalign"
"$ZIPALIGN" -f -p 4 "$STAGE/unsigned.apk" "$STAGE/aligned.apk"

if [ "$RELEASE" = "1" ]; then
    [ -n "${RETECLOCK_KEYSTORE:-}" ] || fail "--release needs RETECLOCK_KEYSTORE (see scripts/env.sh)"
    KEYSTORE="$RETECLOCK_KEYSTORE"
    KEY_ALIAS="${RETECLOCK_KEY_ALIAS:-reteclock}"
    STOREPASS="${RETECLOCK_KEYSTORE_PASS:?RETECLOCK_KEYSTORE_PASS is not set}"
    SUFFIX=""
else
    KEYSTORE="$OUT/dev.keystore"
    KEY_ALIAS="reteclock-dev"
    STOREPASS="reteclock"
    SUFFIX="-debug"
    if [ ! -f "$KEYSTORE" ]; then
        echo "==> creating local development key (build/dev.keystore, not for release)"
        "$KEYTOOL" -genkeypair -v \
            -keystore "$KEYSTORE" \
            -alias "$KEY_ALIAS" \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass "$STOREPASS" -keypass "$STOREPASS" \
            -dname "CN=reteclock development, OU=dev, O=reteclock, C=US" >/dev/null 2>&1
    fi
fi

APK="$ROOT/dist/reteclock-$VERSION$SUFFIX.apk"

echo "==> apksigner (v1 + v2 + v3)"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$STOREPASS" \
    --key-pass "pass:$STOREPASS" \
    --min-sdk-version "$MIN_SDK" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$APK" \
    "$STAGE/aligned.apk"

"$APKSIGNER" verify --min-sdk-version "$MIN_SDK" --verbose "$APK" | sed 's/^/    /'

echo
echo "built $(basename "$APK") ($(wc -c < "$APK") bytes)"
