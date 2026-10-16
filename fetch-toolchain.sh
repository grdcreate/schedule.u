#!/bin/bash
# Downloads the JDK 17 + Android SDK pieces the build needs into ./.toolchain (git-ignored, large).
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
T="$ROOT/.cache/toolchain"
mkdir -p "$T/dl" "$T/sdk"

if [ ! -x "$T/jdk17/bin/java" ]; then
  echo "== fetching JDK 17"
  curl -L -o "$T/dl/jdk17.tar.gz" \
    "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz"
  mkdir -p "$T/jdk17"
  tar -xzf "$T/dl/jdk17.tar.gz" -C "$T/jdk17" --strip-components=1
fi

if [ ! -x "$T/sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "== fetching Android command line tools"
  curl -L -o "$T/dl/cmdline.zip" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "$T/sdk/cmdline-tools"
  unzip -q "$T/dl/cmdline.zip" -d "$T/sdk/cmdline-tools"
  mv "$T/sdk/cmdline-tools/cmdline-tools" "$T/sdk/cmdline-tools/latest"
fi

export JAVA_HOME="$T/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
if [ ! -f "$T/sdk/platforms/android-34/android.jar" ] || [ ! -x "$T/sdk/build-tools/34.0.0/aapt2" ]; then
  echo "== fetching platform 34 + build-tools 34"
  yes | "$T/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$T/sdk" --licenses >/dev/null 2>&1 || true
  "$T/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$T/sdk" "platforms;android-34" "build-tools;34.0.0"
fi
rm -rf "$T/dl"
echo "== toolchain ready at $T"
