package com.sunmi.printerconfig;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class WifiConfigActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 3;

    private BluetoothDevice device;
    private TextView printerNameText;
    private Spinner wifiSpinner;
    private EditText passwordInput;
    private Button configureButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private WifiManager wifiManager;
    private PrinterConfigHelper printerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_config);

        device = getIntent().getParcelableExtra("device");
        if (device == null) {
            Toast.makeText(this, "Error: No device selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        printerNameText = findViewById(R.id.printerNameText);
        wifiSpinner = findViewById(R.id.wifiSpinner);
        passwordInput = findViewById(R.id.passwordInput);
        configureButton = findViewById(R.id.configureButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        printerHelper = new PrinterConfigHelper(this);

        boolean hasPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        if (hasPermission) {
            String deviceName = device.getName();
            printerNameText.setText(getString(R.string.connected_to, deviceName != null ? deviceName : "Unknown"));
        }

        loadWifiNetworks();

        configureButton.setOnClickListener(v -> {
            String selectedSsid = (String) wifiSpinner.getSelectedItem();
            String password = passwordInput.getText().toString();

            if (selectedSsid == null || selectedSsid.isEmpty()) {
                Toast.makeText(this, "Please select a Wi-Fi network", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.isEmpty()) {
                Toast.makeText(this, "Please enter Wi-Fi password", Toast.LENGTH_SHORT).show();
                return;
            }

            configurePrinter(selectedSsid, password);
        });
    }

    private void loadWifiNetworks() {
        if (!checkWifiPermissions()) {
            requestWifiPermissions();
            return;
        }

        List<String> networkList = new ArrayList<>();

        // Get configured networks (the tablet's saved networks)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasWifiStatePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;

            if (!hasLocationPermission) {
                requestWifiPermissions();
                return;
            }

            if (hasWifiStatePermission) {
                try {
                    List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
                    if (configs != null) {
                        for (WifiConfiguration config : configs) {
                            String ssid = config.SSID.replace("\"", "");
                            if (!networkList.contains(ssid)) {
                                networkList.add(ssid);
                            }
                        }
                    }
                } catch (SecurityException ignored) {
                    // Fall back to manual network entry below.
                }
            }
        }

        // Add some common network detection
        // Note: On Android 10+, scanning requires location and is limited
        if (networkList.isEmpty()) {
            // Add placeholder for manual entry if scanning fails
            networkList.add("Enter manually below");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, networkList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wifiSpinner.setAdapter(adapter);
    }

    private boolean checkWifiPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            loadWifiNetworks();
            return;
        }
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
    }

    private void configurePrinter(String ssid, String password) {
        configureButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.configuring);

        // Run configuration in background thread
        new Thread(() -> {
            try {
                boolean success = printerHelper.configurePrinterWifi(device, ssid, password);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    configureButton.setEnabled(true);

                    if (success) {
                        statusText.setText(R.string.success);
                        Toast.makeText(this, R.string.success, Toast.LENGTH_LONG).show();
                        // Return to main activity after success
                        finish();
                    } else {
                        statusText.setText(getString(R.string.error, "Configuration failed"));
                        Toast.makeText(this, "Configuration failed. Please try again.",
                            Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    configureButton.setEnabled(true);
                    statusText.setText(getString(R.string.error, e.getMessage()));
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                loadWifiNetworks();
            } else {
                Toast.makeText(this, "Permissions required to scan Wi-Fi networks",
                    Toast.LENGTH_SHORT).show();
            }
        }
    }
}
