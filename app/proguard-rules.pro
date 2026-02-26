# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Bluetooth related classes
-keep class android.bluetooth.** { *; }

# Keep printer configuration classes
-keep class com.sunmi.printerconfig.** { *; }

# Keep Sunmi SDK callback interfaces/classes used during Wi-Fi configuration.
-keep class com.sunmi.cloudprinter.** { *; }
-keep interface com.sunmi.cloudprinter.** { *; }
