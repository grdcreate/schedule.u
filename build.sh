#!/bin/bash
# Builds ControlScheduler.apk with the Android SDK command line tools only (no Gradle).
# Toolchain lives in ./.toolchain - run ./fetch-toolchain.sh first if it is missing.
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

SDK=${SDK:-$ROOT/.cache/toolchain/sdk}
JDK=${JDK:-$ROOT/.cache/toolchain/jdk17}
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"

if [ ! -x "$JDK/bin/java" ] || [ ! -x "$BT/aapt2" ]; then
  echo "!! toolchain missing - run ./fetch-toolchain.sh" >&2
  exit 1
fi
export PATH="$JDK/bin:$BT:$PATH"

LIBS="libs/shizuku-api.jar:libs/shizuku-shared.jar:libs/shizuku-provider.jar"

rm -rf build && mkdir -p build/compiled build/gen build/classes build/dex

echo "[1/7] compiling resources"
aapt2 compile --dir res -o build/compiled/res.zip

echo "[2/7] linking resources + manifest"
aapt2 link -o build/base.apk \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -R build/compiled/res.zip \
  --java build/gen \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 9 --version-name 2.5 \
  --auto-add-overlay

echo "[3/7] compiling java (app + Shizuku api)"
find src build/gen -name '*.java' > build/sources.txt
javac -nowarn -Xlint:-options -source 11 -target 11 -encoding UTF-8 \
  -classpath "$PLATFORM:$LIBS" -d build/classes @build/sources.txt

echo "[4/7] dexing app classes"
find build/classes -name '*.class' > build/classes.txt
d8 --lib "$PLATFORM" --min-api 26 --release --output build/dex @build/classes.txt

echo "[5/7] translating the Shizuku provider library"
mkdir -p libs/classes libs/classes2
(cd libs/classes && unzip -oq ../shizuku-api.jar && unzip -oq ../shizuku-shared.jar)
(cd libs/classes2 && unzip -oq ../shizuku-provider.jar)
find libs/classes -name '*.class' > build/lib1.txt
find libs/classes2 -name '*.class' > build/lib2.txt
mkdir -p build/libdex1 build/libdex2
d8 --lib "$PLATFORM" --min-api 26 --release --output build/libdex1 @build/lib1.txt
d8 --lib "$PLATFORM" --min-api 26 --release --output build/libdex2 @build/lib2.txt

echo "[6/7] packaging"
cp build/base.apk build/unsigned.apk
# dex files MUST land at the APK root as classes.dex / classes2.dex / classes3.dex.
# Zipping them under a "dex/" directory makes Android reject the package as invalid.
rm -rf build/pkg && mkdir -p build/pkg
cp build/dex/classes.dex build/pkg/classes.dex
cp build/libdex1/classes.dex build/pkg/classes2.dex
cp build/libdex2/classes.dex build/pkg/classes3.dex
(cd build/pkg && zip -q -X ../unsigned.apk classes.dex classes2.dex classes3.dex)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk

echo "[7/7] signing"
if [ ! -f keystore.jks ]; then
  keytool -genkeypair -keystore keystore.jks -alias cs -keyalg RSA -keysize 2048 \
    -validity 10950 -storepass controlscheduler -keypass controlscheduler \
    -dname "CN=Control Scheduler, O=Control Scheduler, C=IN" >/dev/null 2>&1
fi
apksigner sign --ks keystore.jks --ks-pass pass:controlscheduler --key-pass pass:controlscheduler \
  --min-sdk-version 26 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out ControlScheduler.apk build/aligned.apk
apksigner verify --print-certs ControlScheduler.apk | head -3

# guard: the APK must have real code at the root, and no stray dex/ directory
for d in classes.dex classes2.dex classes3.dex; do
  unzip -Z1 ControlScheduler.apk | grep -qx "$d" || { echo "!! $d missing at APK root - package would be invalid" >&2; exit 1; }
done
if unzip -Z1 ControlScheduler.apk | grep -q '^dex/'; then echo "!! stray dex/ directory in package" >&2; exit 1; fi
echo "package layout OK: 3 dex at root"
ls -lh ControlScheduler.apk
