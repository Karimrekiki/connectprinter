# Sunmi Printer Wi-Fi Configuration App

An Android application for configuring Sunmi printers' Wi-Fi settings via Bluetooth. This app allows you to connect to Sunmi printers (NT311, CloudPrinter, etc.) over Bluetooth and configure their Wi-Fi network settings.

## Features

- **Bluetooth Scanning**: Automatically scan and discover Sunmi printers
- **Simple UI**: Easy-to-use interface with two main screens
  1. Connect to printer via Bluetooth
  2. Configure Wi-Fi network settings
- **Wide Compatibility**: Works on Android phones and tablets (Android 5.0+)
- **Sunmi Printer Support**: Supports NT311, CloudPrinter, and other Sunmi printer models

## Requirements

- Android device running Android 5.0 (API 21) or higher
- Bluetooth enabled on the device
- Location permissions (required by Android for Wi-Fi scanning)
- Sunmi printer with Bluetooth capability

## How to Build

### Option 1: Using Android Studio

1. Install [Android Studio](https://developer.android.com/studio)
2. Clone or download this repository
3. Open the project in Android Studio
4. Wait for Gradle sync to complete
5. Connect your Android device via USB (with USB debugging enabled) or use an emulator
6. Click "Run" (or press Shift+F10) to build and install the app

### Option 2: Using Command Line

1. Install [Android SDK Command Line Tools](https://developer.android.com/studio#command-tools)
2. Set up your environment variables:
   ```bash
   export ANDROID_HOME=/path/to/android/sdk
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   ```
3. Navigate to the project directory:
   ```bash
   cd connectprinter
   ```
4. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
5. Build the Play Store release bundle:
   ```bash
   ./gradlew bundleRelease
   ```
6. Outputs:
   - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   - Release AAB: `app/build/outputs/bundle/release/app-release.aab`

### Option 3: Build with Gradle Wrapper (Recommended)

```bash
# On Linux/Mac
./gradlew clean assembleDebug

# On Windows
gradlew.bat clean assembleDebug
```

The debug APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

For end-to-end Play upload steps, see [PLAY_STORE_RELEASE.md](PLAY_STORE_RELEASE.md).

## How to Install

### Method 1: Install via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Method 2: Download APK Directly to Device

1. Copy the APK file to your Android device (via USB, email, cloud storage, etc.)
2. On your Android device, navigate to the APK file using a file manager
3. Tap the APK file to install
4. You may need to enable "Install from Unknown Sources" in your device settings

### Method 3: Host APK for Download

1. Upload the APK to a file hosting service (Google Drive, Dropbox, etc.)
2. Share the download link with users
3. Users can download and install directly on their Android devices

## Usage Instructions

### Step 1: Launch the App
Open the "Sunmi Printer Config" app on your Android device.

### Step 2: Scan for Printers
1. Tap the "Scan for Printers" button
2. Grant Bluetooth and Location permissions when prompted
3. Wait for the app to discover nearby Sunmi printers
4. The app will show a list of available printers (filtered by name: NT311, CloudPrinter, SUNMI)

### Step 3: Select a Printer
Tap on the printer you want to configure from the list.

### Step 4: Configure Wi-Fi
1. Select your store's Wi-Fi network from the dropdown
2. Enter the Wi-Fi password
3. Tap "Configure Printer"
4. Wait for the configuration to complete

### Step 5: Verify
Once configuration is successful, the printer should automatically connect to the configured Wi-Fi network.

## Permissions Required

The app requires the following permissions:

- **Bluetooth**: To scan for and connect to printers
- **Bluetooth Admin**: Used on Android 11 and lower for legacy Bluetooth behavior
- **Location (Android 6-11 only)**: Required by Android system behavior for Bluetooth discovery
- **Wi-Fi State**: To read available Wi-Fi networks
- **Change Wi-Fi State**: To configure Wi-Fi settings

All permissions are requested at runtime when needed.

## Troubleshooting

### Printer Not Found
- Ensure the printer is powered on
- Make sure Bluetooth is enabled on both the device and printer
- Try moving closer to the printer
- Restart the printer and try scanning again

### Connection Failed
- Verify the printer is not already connected to another device
- Restart Bluetooth on your Android device
- Try turning the printer off and on again

### Configuration Failed
- Double-check the Wi-Fi password
- Ensure the Wi-Fi network is within range of the printer
- Some printers may require specific command formats - check your printer model documentation

### Wi-Fi Networks Not Showing
- Grant Location permissions when prompted
- Enable Location services on your device
- On Android 10+, Wi-Fi scanning has privacy restrictions

## Technical Details

### Printer Communication Protocol

The app uses the following methods to send Wi-Fi configuration to Sunmi printers:

1. **Sunmi Proprietary Command**:
   - Command format: `0x1F 0x1B 0x1F 0x91 [SSID length] [SSID] [Password length] [Password] [Security type]`

2. **AT-Style Commands**:
   - Format: `AT+WIFI_CONF="SSID","PASSWORD"`

3. **ESC/POS Extension**:
   - Format: `ESC 0x1F 0x10 WIFI:SSID,PASSWORD`

The app sends all three command formats to maximize compatibility across different Sunmi printer models.

### Bluetooth Connection

- Uses Bluetooth SPP (Serial Port Profile)
- UUID: `00001101-0000-1000-8000-00805F9B34FB`

## Development

### Project Structure

```
connectprinter/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/sunmi/printerconfig/
│   │       │   ├── MainActivity.java              # Bluetooth scanning screen
│   │       │   ├── WifiConfigActivity.java        # Wi-Fi configuration screen
│   │       │   ├── BluetoothDeviceAdapter.java    # RecyclerView adapter
│   │       │   └── PrinterConfigHelper.java       # Printer communication logic
│   │       ├── res/
│   │       │   ├── layout/                        # UI layouts
│   │       │   ├── values/                        # Strings and resources
│   │       │   └── mipmap/                        # App icons
│   │       └── AndroidManifest.xml
│   ├── build.gradle                                # App-level Gradle config
│   └── proguard-rules.pro
├── build.gradle                                    # Project-level Gradle config
├── settings.gradle
└── gradle.properties
```

### Key Classes

- **MainActivity**: Handles Bluetooth scanning and displays list of available printers
- **WifiConfigActivity**: Provides UI for selecting Wi-Fi network and entering password
- **BluetoothDeviceAdapter**: RecyclerView adapter for displaying Bluetooth devices
- **PrinterConfigHelper**: Core logic for connecting to printer via Bluetooth and sending Wi-Fi configuration commands

## License

This project is provided as-is for configuring Sunmi printers.

## Support

For issues related to:
- **App functionality**: Check the troubleshooting section above
- **Sunmi printer commands**: Refer to [Sunmi Developer Documentation](https://developer.sunmi.com/)
- **Android development**: See [Android Developer Docs](https://developer.android.com/)

## Notes

- The app filters for Sunmi printers by name (NT311, CloudPrinter, SUNMI)
- Wi-Fi security type is set to WPA2-PSK by default (most common)
- Configuration is sent over Bluetooth SPP connection
- The app is compatible with Android 5.0+ (API level 21 and above)

## Version History

- **v1.0**: Initial release with Bluetooth scanning and Wi-Fi configuration functionality
