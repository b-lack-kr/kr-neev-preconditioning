#!/bin/sh
# Run locally. Does not connect to or modify a vehicle.
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
[ -n "$ANDROID_HOME" ] || { echo "Set ANDROID_HOME to your Android SDK directory." >&2; exit 1; }
BT="$ANDROID_HOME/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
[ -x "$BT/apksigner" ] || { echo "Install Android build-tools ${BUILD_TOOLS_VERSION:-36.0.0}." >&2; exit 1; }
: "${LAUNCHPAD_KEYSTORE:?Set LAUNCHPAD_KEYSTORE to your private signing keystore}"
: "${LAUNCHPAD_STORE_PASSWORD:?Set LAUNCHPAD_STORE_PASSWORD}"
: "${LAUNCHPAD_KEY_PASSWORD:=$LAUNCHPAD_STORE_PASSWORD}"
: "${LAUNCHPAD_KEY_ALIAS:=preconditioning}"
export LAUNCHPAD_STORE_PASSWORD LAUNCHPAD_KEY_PASSWORD
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
mkdir -p dist
python3 tools/kitkat_repackage.py app/build/outputs/apk/release/app-release-unsigned.apk dist/Preconditioning-signing.apk
# Explicit v1 SHA1 signing preserves the existing API 19 packaging procedure.
JARSIGNER=jarsigner
[ -z "${JAVA_HOME:-}" ] || JARSIGNER="$JAVA_HOME/bin/jarsigner"
"$JARSIGNER" -keystore "$LAUNCHPAD_KEYSTORE" -storepass:env LAUNCHPAD_STORE_PASSWORD \
    -keypass:env LAUNCHPAD_KEY_PASSWORD -digestalg SHA1 -sigalg SHA1withRSA \
    dist/Preconditioning-signing.apk "$LAUNCHPAD_KEY_ALIAS"
"$BT/zipalign" -f 4 dist/Preconditioning-signing.apk dist/Preconditioning.apk
"$BT/apksigner" verify --min-sdk-version 19 --verbose --print-certs dist/Preconditioning.apk
