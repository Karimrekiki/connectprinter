#!/bin/bash

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

# Clean previous build
echo "Cleaning previous build..."
./gradlew clean

# Build debug APK
echo ""
echo "Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================="
    echo "Build Successful!"
    echo "======================================="
    echo "APK Location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on device:"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
else
    echo ""
    echo "======================================="
    echo "Build Failed!"
    echo "======================================="
    echo "Please check the error messages above."
    echo ""
    exit 1
fi
