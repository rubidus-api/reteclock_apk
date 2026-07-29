#!/bin/sh
# Builds the reteclock APK with the Android SDK command-line tools only: no Gradle, no AGP.
#
#   scripts/build.sh              debug-signed APK in dist/
#   scripts/build.sh --release    same pipeline, but requires RETECLOCK_KEYSTORE
#   scripts/build.sh --unsigned   same pipeline, stopping before signing
#
# --unsigned exists for F-Droid, which builds the app from source on its own server and signs the
# result with its own key. It refuses an APK that arrives already signed, so that build must not
# touch a keystore at all.
#
# The APK is signed with v1 (JAR) plus v2 and v3, so Android 2.3..4.4 accept the v1 signature and
# modern Android accepts v2/v3.

set -e

# A zip entry stores its timestamp as a local date with no timezone attached. aapt2 writes the
# fixed 1980-01-01 epoch, but writes it in the builder's local time, so the same source produces
# different bytes in Seoul and in UTC. Pin the zone and the APK stops depending on where it was
# built, which is what makes the build reproducible off this machine.
TZ=UTC
export TZ

ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/env.sh"

require_jdk
require_sdk

MIN_SDK=9
TARGET_SDK=28
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/src/android/AndroidManifest.xml")
[ -n "$VERSION" ] || fail "cannot read versionName from the manifest"

MODE=debug
case "${1:-}" in
    "")         MODE=debug ;;
    --release)  MODE=release ;;
    --unsigned) MODE=unsigned ;;
    *)          fail "unknown option: $1 (expected --release or --unsigned)" ;;
esac

# The F-Droid listing is a reproducible build: F-Droid compiles the app itself and compares the
# result against the APK published on the releases page. javac 17 and javac 21 emit different
# bytecode, so a release built with the wrong JDK produces a file F-Droid can never match, and the
# mismatch would only surface later, in someone else's build log. Refuse here instead.
# Set RETECLOCK_JDK_MAJOR=any to build a release with a different toolchain on purpose.
: "${RETECLOCK_JDK_MAJOR:=21}"
if [ "$MODE" = "release" ] && [ "$RETECLOCK_JDK_MAJOR" != "any" ]; then
    have=$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9][0-9]*\).*/\1/p')
    [ "$have" = "$RETECLOCK_JDK_MAJOR" ] || fail \
        "--release needs JDK $RETECLOCK_JDK_MAJOR, found ${have:-unknown} at $JAVA_HOME; the F-Droid reproducible build compares against this APK (RETECLOCK_JDK_MAJOR=any to override)"
fi

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

if [ "$MODE" = "unsigned" ]; then
    APK="$ROOT/dist/reteclock-$VERSION-unsigned.apk"
    cp "$STAGE/aligned.apk" "$APK"
    echo
    echo "built $(basename "$APK") ($(wc -c < "$APK") bytes), unsigned on purpose"
    exit 0
fi

if [ "$MODE" = "release" ]; then
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

# --alignment-preserved keeps the archive exactly as zipalign left it. apksigner from build-tools
# 35 re-aligns while signing unless told not to, and the v2/v3 signatures cover the whole archive,
# so the signature would no longer fit a rebuild of the same source. F-Droid verifies a
# reproducible build by copying this signature onto its own build, which then fails with a
# CHUNKED_SHA256 mismatch. T009 guards this.
echo "==> apksigner (v1 + v2 + v3, alignment preserved)"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$STOREPASS" \
    --key-pass "pass:$STOREPASS" \
    --min-sdk-version "$MIN_SDK" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --alignment-preserved \
    --out "$APK" \
    "$STAGE/aligned.apk"

"$APKSIGNER" verify --min-sdk-version "$MIN_SDK" --verbose "$APK" | sed 's/^/    /'

echo
echo "built $(basename "$APK") ($(wc -c < "$APK") bytes)"
