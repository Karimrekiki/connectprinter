#!/bin/bash
set -euo pipefail

# Build script for Sunmi Printer Configuration App

echo "======================================="
echo "Sunmi Printer Config - Build Script"
echo "======================================="
echo ""

# Check if Android SDK is set
if [ -z "$ANDROID_HOME" ] && [ ! -f "local.properties" ]; then
    echo "ERROR: Android SDK not found!"
    echo "Please either:"
    echo "  1. Set ANDROID_HOME environment variable, or"
    echo "  2. Create local.properties with sdk.dir=/path/to/android/sdk"
    echo ""
    exit 1
fi

BUILD_TYPE="${1:-debug}"

echo "Cleaning previous build..."
./gradlew clean

if [ "$BUILD_TYPE" = "release" ]; then
    echo ""
    echo "Building release AAB..."
    ./gradlew bundleRelease
    echo ""
    echo "======================================="
    echo "Build Successful!"
    echo "======================================="
    echo "Bundle Location: app/build/outputs/bundle/release/app-release.aab"
    echo ""
    echo "For Play upload steps, see PLAY_STORE_RELEASE.md"
    echo ""
else
    echo ""
    echo "Building debug APK..."
    ./gradlew assembleDebug
    echo ""
    echo "======================================="
    echo "Build Successful!"
    echo "======================================="
    echo "APK Location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on device:"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
fi
