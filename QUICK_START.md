# Quick Start Guide - Sunmi Printer Wi-Fi Configuration

## For End Users (Installing the App)

### Download and Install

1. **Download the APK** file to your Android phone or tablet
2. **Open the APK** file from your device's Downloads folder
3. If prompted, **allow installation from unknown sources**
4. **Tap Install** and wait for installation to complete
5. **Open** the "Sunmi Printer Config" app

### Using the App

1. **Scan for Printers**
   - Open the app
   - Tap "Scan for Printers"
   - Grant permissions when asked (Bluetooth and Location)
   - Wait for nearby Sunmi printers to appear

2. **Connect to a Printer**
   - Tap on your printer from the list (e.g., "NT311-XXXX")
   - The app will connect via Bluetooth

3. **Configure Wi-Fi**
   - Select your store's Wi-Fi network from the dropdown
   - Enter the Wi-Fi password
   - Tap "Configure Printer"
   - Wait for success message

4. **Done!**
   - Your printer is now configured to connect to your Wi-Fi network
   - The printer should automatically connect

### Troubleshooting

- **Can't find printer?** → Make sure the printer is turned on and Bluetooth is enabled
- **Connection fails?** → Move closer to the printer and try again
- **Configuration fails?** → Double-check your Wi-Fi password

---

## For Developers (Building the App)

### Prerequisites

- Android Studio (or)
- Java JDK 8 or higher
- Android SDK

### Quick Build Steps

```bash
# Clone the repository
git clone <repository-url>
cd connectprinter

# Build the APK (Debug version)
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Install to Device

```bash
# Via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Build Release Version

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

For detailed Play Store instructions, see [PLAY_STORE_RELEASE.md](PLAY_STORE_RELEASE.md).

---

## Distribution Methods

### Method 1: Direct Download Link
1. Upload APK to Google Drive / Dropbox / File hosting service
2. Share the download link
3. Users download and install directly on their devices

### Method 2: QR Code
1. Generate a QR code with the download link
2. Users scan the QR code with their Android device
3. Download and install the APK

### Method 3: Google Play Store
1. Sign up for Google Play Developer account
2. Create signed release AAB (`./gradlew bundleRelease`)
3. Upload to Play Store Console
4. Users can install from Play Store

---

## Important Notes

- **Minimum Android Version**: Android 5.0 (Lollipop) or higher
- **Supported Printers**: Sunmi NT311, CloudPrinter, and other Sunmi Bluetooth-enabled printers
- **Permissions**: App requires Bluetooth and Location permissions (standard Android requirements)
- **No Internet Required**: App works completely offline once installed

## Support

For technical support or questions:
- Check the troubleshooting section in [README.md](README.md)
- Refer to Sunmi's official documentation at https://developer.sunmi.com/
