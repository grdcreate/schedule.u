#!/bin/bash
# schedule.u - one command that fetches the toolchain, builds, tests, verifies and publishes.
# Called by .github/workflows/build.yml, but you can also just run:  bash ci.sh
set -euo pipefail
cd "$(dirname "$0")"

JDK="${JDK:-$PWD/.cache/toolchain/jdk17}"
SDK="${SDK:-$PWD/.cache/toolchain/sdk}"
BT="$SDK/build-tools/34.0.0"

echo "== toolchain"
[ -x "$JDK/bin/java" ] || bash fetch-toolchain.sh
export JAVA_HOME="$JDK" PATH="$JDK/bin:$BT:$PATH"

# Optional: a real release keystore, so CI builds share the signature of dist/.
if [ -n "${KEYSTORE_JKS_BASE64:-}" ]; then
  printf '%s' "$KEYSTORE_JKS_BASE64" | base64 -d > keystore.jks
  echo "== keystore restored from the KEYSTORE_JKS_BASE64 secret"
fi

echo "== build"
bash build.sh

echo "== tests"
bash tests/run.sh

echo "== verify the package"
APK=ControlScheduler.apk
"$BT/aapt2" dump badging "$APK" | grep -E "^package|application-label:"
"$BT/apksigner" verify --print-certs -v "$APK" | grep -E "^Verifie|SHA-256 digest" | head -6
"$BT/zipalign" -c 4 "$APK" && echo "zipalign OK"
for d in classes.dex classes2.dex classes3.dex; do
  unzip -Z1 "$APK" | grep -qx "$d" || { echo "::error::$d missing at the APK root - Android would reject this package"; exit 1; }
done
echo "3 dex at the APK root OK"
unzip -t "$APK" | tail -1

mkdir -p dist
cp "$APK" dist/scheduleu-CI.apk
cp "$APK" /tmp/scheduleu-CI.apk
sha256sum dist/scheduleu-CI.apk | tee dist/scheduleu-CI.apk.sha256 | sed 's/^/   /'

SIZE=$(stat -c%s dist/scheduleu-CI.apk 2>/dev/null || stat -f%z dist/scheduleu-CI.apk)
SHA=$(cut -d' ' -f1 dist/scheduleu-CI.apk.sha256)
BADGING=$("$BT/aapt2" dump badging "$APK" | head -1)

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### schedule.u - CI build"
    echo
    echo "| | |"
    echo "|---|---|"
    echo "| APK | \`dist/scheduleu-CI.apk\` - ${SIZE} bytes |"
    echo "| SHA-256 | \`${SHA}\` |"
    echo "| manifest | ${BADGING#package: } |"
    echo "| commit | \`${GITHUB_SHA:0:7}\` on \`${GITHUB_REF_NAME:-local}\` |"
    echo
    echo "Download: https://github.com/grdcreate/schedule.u/releases/download/ci-latest/scheduleu-CI.apk"
  } >> "$GITHUB_STEP_SUMMARY"
fi

echo "== publish"
if command -v gh >/dev/null 2>&1 && [ -n "${GH_TOKEN:-}" ]; then
  if [[ "${GITHUB_REF:-}" == refs/tags/v* ]]; then
    # a version tag: real release, notes generated from the commits
    gh release create "${GITHUB_REF_NAME}" dist/scheduleu-CI.apk dist/scheduleu-CI.apk.sha256 \
      --title "schedule.u ${GITHUB_REF_NAME#v}" --generate-notes 2>/dev/null \
      || gh release upload "${GITHUB_REF_NAME}" dist/scheduleu-CI.apk dist/scheduleu-CI.apk.sha256 --clobber
    echo "   release ${GITHUB_REF_NAME} updated"
  elif [ "${GITHUB_REF:-}" = "refs/heads/main" ]; then
    # every push to main: rolling pre-release, so there is always a fresh APK URL
    gh release view ci-latest >/dev/null 2>&1 \
      || gh release create ci-latest --prerelease --title "Latest CI build" \
           --notes "Freshly built APK from the latest commit on main. Download the asset below; the checksum is in dist/SHA256SUMS / the .sha256 asset."
    gh release upload ci-latest dist/scheduleu-CI.apk dist/scheduleu-CI.apk.sha256 --clobber >/dev/null
    echo "   rolling release ci-latest updated -> releases/download/ci-latest/scheduleu-CI.apk"
  else
    echo "   not main and not a version tag - build only, nothing published"
  fi
else
  echo "   gh CLI or GH_TOKEN missing (local run) - skipping publish"
fi

echo "== done: dist/scheduleu-CI.apk (${SIZE} bytes)"
