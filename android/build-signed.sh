#!/usr/bin/env bash
# Builds a signed release APK of the Iffy Android app.
#
# On first run, this creates a release keystore from the credentials in
# keystore.properties. Subsequent runs reuse the existing keystore.
#
# Usage: bash android/build-signed.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PROPS_FILE="$SCRIPT_DIR/keystore.properties"
if [[ ! -f "$PROPS_FILE" ]]; then
  echo "ERROR: $PROPS_FILE not found."
  echo "Copy keystore.properties.example to keystore.properties and set your password."
  exit 1
fi

# Load keystore.properties into env
get_prop() {
  grep -E "^$1=" "$PROPS_FILE" | head -n1 | cut -d'=' -f2-
}

STORE_FILE="$(get_prop storeFile)"
STORE_PASSWORD="$(get_prop storePassword)"
KEY_ALIAS="$(get_prop keyAlias)"
KEY_PASSWORD="$(get_prop keyPassword)"

if [[ -z "$STORE_FILE" || -z "$STORE_PASSWORD" || -z "$KEY_ALIAS" || -z "$KEY_PASSWORD" ]]; then
  echo "ERROR: keystore.properties is missing required fields."
  exit 1
fi

KEYSTORE_PATH="$SCRIPT_DIR/$STORE_FILE"

# Generate keystore if missing
if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo ">>> Generating release keystore at $KEYSTORE_PATH"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 9125 \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=Iffy, OU=Self-Sign, O=Iffy, L=Local, ST=Local, C=US"
else
  echo ">>> Using existing keystore at $KEYSTORE_PATH"
fi

# Locate a gradle binary: prefer Android Studio cache, fall back to system gradle
GRADLE_BIN=""
if command -v gradle >/dev/null 2>&1; then
  GRADLE_BIN="$(command -v gradle)"
else
  CACHED="$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-*-bin/*/gradle-*/bin/gradle 2>/dev/null | sort | tail -n1 || true)"
  if [[ -n "$CACHED" ]]; then
    GRADLE_BIN="$CACHED"
  fi
fi

if [[ -z "$GRADLE_BIN" ]]; then
  echo "ERROR: No gradle binary found. Install gradle (brew install gradle) or open the project"
  echo "in Android Studio once so its gradle wrapper cache is populated."
  exit 1
fi

echo ">>> Using gradle: $GRADLE_BIN"
echo ">>> Building signed release APK..."
"$GRADLE_BIN" --project-dir "$SCRIPT_DIR" clean :app:assembleRelease

APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "ERROR: Build finished but APK not found at expected path: $APK_PATH"
  exit 1
fi

# Verify signature with apksigner if available
APKSIGNER="$(ls -t "$HOME"/Library/Android/sdk/build-tools/*/apksigner 2>/dev/null | head -n1 || true)"
if [[ -n "$APKSIGNER" ]]; then
  echo ">>> Verifying signature with $APKSIGNER"
  "$APKSIGNER" verify --verbose "$APK_PATH" || {
    echo "WARNING: apksigner verification failed."
    exit 1
  }
fi

echo ""
echo ">>> Success!"
echo "    Signed APK: $APK_PATH"
echo ""
echo "To install on a connected device via ADB:"
echo "    ~/Library/Android/sdk/platform-tools/adb install -r \"$APK_PATH\""
echo ""
echo "Or transfer the APK to your phone and open it with a file manager."
echo "You'll need to allow the file manager to \"Install unknown apps\" once."
