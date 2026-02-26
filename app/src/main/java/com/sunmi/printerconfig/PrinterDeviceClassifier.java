package com.sunmi.printerconfig;

import java.util.Locale;

public final class PrinterDeviceClassifier {
    private PrinterDeviceClassifier() {
    }

    public static boolean isLikelySunmi(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            return false;
        }

        String normalizedName = deviceName.toUpperCase(Locale.ROOT);
        return normalizedName.startsWith("NT311")
            || normalizedName.contains("CLOUDPRINTER")
            || normalizedName.contains("SUNMI");
    }
}
