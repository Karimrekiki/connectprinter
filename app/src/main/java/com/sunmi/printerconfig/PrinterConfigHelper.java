package com.sunmi.printerconfig;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PrinterConfigHelper {
    private static final String TAG = "PrinterConfigHelper";
    // Standard SPP UUID for Bluetooth Serial Port Profile
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context context;

    public PrinterConfigHelper(Context context) {
        this.context = context;
    }

    /**
     * Configure Sunmi printer Wi-Fi settings via Bluetooth
     * @param device The Bluetooth device (printer)
     * @param ssid Wi-Fi network SSID
     * @param password Wi-Fi network password
     * @return true if configuration was sent successfully
     */
    public boolean configurePrinterWifi(BluetoothDevice device, String ssid, String password) {
        BluetoothSocket socket = null;
        OutputStream outputStream = null;

        try {
            // Check Bluetooth permission
            if (!checkBluetoothPermission()) {
                Log.e(TAG, "Bluetooth permission not granted");
                return false;
            }

            // Create Bluetooth socket
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);

            // Connect to the device
            Log.d(TAG, "Connecting to printer...");
            socket.connect();
            Log.d(TAG, "Connected successfully");

            // Get output stream
            outputStream = socket.getOutputStream();

            // Send Wi-Fi configuration commands
            boolean success = sendWifiConfig(outputStream, ssid, password);

            Log.d(TAG, "Configuration sent: " + success);
            return success;

        } catch (IOException e) {
            Log.e(TAG, "Error configuring printer: " + e.getMessage(), e);
            return false;
        } finally {
            // Clean up resources
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Send Wi-Fi configuration to the printer
     * This uses ESC/POS commands specific to Sunmi printers
     */
    private boolean sendWifiConfig(OutputStream out, String ssid, String password) {
        try {
        // ESC/POS command header for Sunmi Wi-Fi configuration
        // Format: ESC @ (initialize printer)
        // Then: Custom command for Wi-Fi setup

        // Initialize printer
        out.write(new byte[]{0x1B, 0x40}); // ESC @
        Thread.sleep(100);

        // Sunmi Wi-Fi configuration command format:
        // Command structure: 0x1F 0x1B 0x1F [subcommand] [data length] [SSID] [password]

        // Method 1: Using Sunmi's proprietary Wi-Fi config command
        // Header: 0x1F 0x1B 0x1F 0x91 (Wi-Fi config command)
        byte[] header = new byte[]{0x1F, 0x1B, 0x1F, 0x91};
        out.write(header);

        // SSID length (1 byte) + SSID
        byte[] ssidBytes = ssid.getBytes(StandardCharsets.UTF_8);
        out.write(ssidBytes.length);
        out.write(ssidBytes);

        // Password length (1 byte) + Password
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        out.write(passwordBytes.length);
        out.write(passwordBytes);

        // Security type (1 byte): 3 = WPA2-PSK (most common)
        out.write(0x03);

        out.flush();
        Thread.sleep(500);

        // Method 2: Alternative format using AT-style commands
        // Some Sunmi printers accept AT commands for configuration
        String atCommand = String.format("AT+WIFI_CONF=\"%s\",\"%s\"\r\n", ssid, password);
        out.write(atCommand.getBytes(StandardCharsets.UTF_8));
        out.flush();
        Thread.sleep(500);

        // Method 3: ESC/POS extension command format
        // Some models use this format
        String configCommand = String.format("\u001B\u001F\u0010WIFI:%s,%s\n", ssid, password);
        out.write(configCommand.getBytes(StandardCharsets.UTF_8));
        out.flush();
        Thread.sleep(500);

        // Send end marker
        out.write(new byte[]{0x0A}); // Line feed
        out.flush();

        Log.d(TAG, "Wi-Fi configuration commands sent to printer");
        Log.d(TAG, "SSID: " + ssid);

        return true;

        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error sending Wi-Fi config: " + e.getMessage());
            return false;
        }
    }

    private boolean checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // Helper method to convert string to hex for debugging
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
