#!/bin/bash
set -e

# Build script for Iffy
# Creates a standalone .app bundle you can run without Xcode

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build/release"
APP_NAME="Iffy"
APP_BUNDLE="$SCRIPT_DIR/$APP_NAME.app"

# Check for full Xcode installation (Command Line Tools alone are not sufficient)
XCODE_PATH=$(xcode-select -p 2>/dev/null || true)
if [[ -z "$XCODE_PATH" ]] || [[ "$XCODE_PATH" != *"Xcode"* ]]; then
    echo "❌ Error: Full Xcode installation is required but was not found."
    echo "   Current developer path: ${XCODE_PATH:-not set}"
    echo ""
    echo "   Please install Xcode from the Mac App Store or https://developer.apple.com/xcode/"
    echo "   Then run: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer"
    exit 1
fi

echo "🔨 Building $APP_NAME..."
cd "$SCRIPT_DIR"
swift build -c release

echo "📦 Creating app bundle..."
# Clean up old bundle
rm -rf "$APP_BUNDLE"

# Create bundle structure
mkdir -p "$APP_BUNDLE/Contents/MacOS"
mkdir -p "$APP_BUNDLE/Contents/Resources"

# Copy executable
cp "$BUILD_DIR/$APP_NAME" "$APP_BUNDLE/Contents/MacOS/"

# Copy Info.plist
cp "$SCRIPT_DIR/Sources/Iffy/Resources/Info.plist" "$APP_BUNDLE/Contents/"

# Create PkgInfo
echo -n "APPL????" > "$APP_BUNDLE/Contents/PkgInfo"

echo "🔐 Code signing (ad-hoc)..."
codesign --force --deep --sign - "$APP_BUNDLE"

echo "✅ Done! Built: $APP_BUNDLE"
echo ""
echo "To run:  open $APP_BUNDLE"
echo "To install: cp -r $APP_BUNDLE /Applications/"
