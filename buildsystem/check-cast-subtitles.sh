#!/usr/bin/env bash
set -euo pipefail

# Run the pure JVM subtitle tests without an Android SDK or native VLC build.
# Supply an unpacked Kotlin compiler and the repository's JUnit/Hamcrest versions.
: "${CAST_KOTLINC:?Set CAST_KOTLINC to the kotlinc executable}"
: "${CAST_JUNIT_JAR:?Set CAST_JUNIT_JAR to junit-4.13.2.jar}"
: "${CAST_HAMCREST_JAR:?Set CAST_HAMCREST_JAR to hamcrest-core-1.3.jar}"

cast_repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cast_kotlin_lib=$(cd "$(dirname "$CAST_KOTLINC")/../lib" && pwd)
cast_test_dir=$(mktemp -d)
trap 'rm -rf "$cast_test_dir"' EXIT

"$CAST_KOTLINC" \
    "$cast_repo_root/application/vlc-android/src/org/videolan/vlc/util/CastSubtitleDocument.kt" \
    "$cast_repo_root/application/vlc-android/test/org/videolan/vlc/util/CastSubtitleDocumentTest.kt" \
    -jvm-target 1.8 \
    -classpath "$CAST_JUNIT_JAR" \
    -d "$cast_test_dir/tests.jar"

java -cp "$cast_test_dir/tests.jar:$cast_kotlin_lib/kotlin-stdlib.jar:$CAST_JUNIT_JAR:$CAST_HAMCREST_JAR" \
    org.junit.runner.JUnitCore org.videolan.vlc.util.CastSubtitleDocumentTest
