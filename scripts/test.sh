#!/bin/sh
# Runs the JVM unit tests for the pure-Java clock core.
#
#   scripts/test.sh
#
# The core has no android.* imports on purpose, so these tests need no emulator and no device.

set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/env.sh"

require_jdk

: "${JUNIT_JAR:=}"
[ -n "$JUNIT_JAR" ] || fail "JUNIT_JAR is not set (junit-platform-console-standalone; see scripts/env.sh)"
[ -f "$JUNIT_JAR" ] || fail "JUNIT_JAR does not exist"

OUT="$ROOT/build/tests"
rm -rf "$OUT"
mkdir -p "$OUT/classes"

find "$ROOT/src/core/java" "$ROOT/tests/java" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -classpath "$JUNIT_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

"$JAVA" -jar "$JUNIT_JAR" execute \
    --class-path "$OUT/classes" \
    --scan-class-path \
    --details=tree \
    --disable-banner
