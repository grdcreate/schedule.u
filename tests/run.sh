#!/bin/bash
# Runs the pure-logic tests (scheduling math, action migration, step stacks)
# on the desktop JVM against the real app classes.  Usage: ./tests/run.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK=${JDK:-$ROOT/.cache/toolchain/jdk17}
PLATFORM=${PLATFORM:-$ROOT/.cache/toolchain/sdk/platforms/android-34/android.jar}
OUT=$(mktemp -d)

cd "$ROOT"
[ -d build/classes ] || { echo "compiling app first..."; ./build.sh >/dev/null; }
"$JDK/bin/javac" -nowarn -d "$OUT" -classpath "$PLATFORM:$ROOT/build/classes" tests/Harness.java
"$JDK/bin/java" -classpath "$PLATFORM:$OUT:$ROOT/build/classes" Harness
