package com.sunmi.printerconfig;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiConfigActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 3;

    private BluetoothDevice device;
    private TextView printerNameText;
    private RecyclerView wifiNetworksRecycler;
    private Button scanWifiButton;
    private Button manualEntryButton;
    private ProgressBar scanProgressBar;
    private TextView statusText;
    private WifiManager wifiManager;
    private PrinterConfigHelper printerHelper;
    private WifiNetworkAdapter wifiAdapter;
    private List<String> wifiNetworks;

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                scanSuccess();
            } else {
                scanFailure();
            }
        }
    };

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
        wifiNetworksRecycler = findViewById(R.id.wifiNetworksRecycler);
        scanWifiButton = findViewById(R.id.scanWifiButton);
        manualEntryButton = findViewById(R.id.manualEntryButton);
        scanProgressBar = findViewById(R.id.scanProgressBar);
        statusText = findViewById(R.id.statusText);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        printerHelper = new PrinterConfigHelper(this);

        wifiNetworks = new ArrayList<>();
        wifiAdapter = new WifiNetworkAdapter(wifiNetworks, this::onNetworkSelected);
        wifiNetworksRecycler.setLayoutManager(new LinearLayoutManager(this));
        wifiNetworksRecycler.setAdapter(wifiAdapter);

        boolean hasPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        if (hasPermission) {
            String deviceName = device.getName();
            printerNameText.setText(getString(R.string.connected_to, deviceName != null ? deviceName : "Unknown"));
        }

        scanWifiButton.setOnClickListener(v -> {
            if (checkWifiPermissions()) {
                startWifiScan();
            } else {
                requestWifiPermissions();
            }
        });

        manualEntryButton.setOnClickListener(v -> showManualEntryDialog());

        // Register WiFi scan receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // Request permissions and start scan automatically
        if (checkWifiPermissions()) {
            startWifiScan();
        } else {
            requestWifiPermissions();
        }
    }

    private boolean checkWifiPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE
                }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        }
    }

    private void startWifiScan() {
        scanWifiButton.setEnabled(false);
        scanProgressBar.setVisibility(View.VISIBLE);
        statusText.setText("Scanning for Wi-Fi networks...");
        wifiNetworks.clear();
        wifiAdapter.notifyDataSetChanged();

        boolean success = wifiManager.startScan();
        if (!success) {
            scanFailure();
        }
    }

    private void scanSuccess() {
        List<ScanResult> results = wifiManager.getScanResults();
        Set<String> uniqueNetworks = new HashSet<>();

        for (ScanResult result : results) {
            if (result.SSID != null && !result.SSID.isEmpty()) {
                uniqueNetworks.add(result.SSID);
            }
        }

        wifiNetworks.clear();
        wifiNetworks.addAll(uniqueNetworks);
        wifiAdapter.notifyDataSetChanged();

        scanProgressBar.setVisibility(View.GONE);
        scanWifiButton.setEnabled(true);

        if (wifiNetworks.isEmpty()) {
            statusText.setText("No networks found. Try Manual Entry.");
        } else {
            statusText.setText("Found " + wifiNetworks.size() + " network(s). Tap to select.");
        }
    }

    private void scanFailure() {
        scanProgressBar.setVisibility(View.GONE);
        scanWifiButton.setEnabled(true);
        statusText.setText("Scan failed. Use Manual Entry below.");
        Toast.makeText(this, "WiFi scan failed. Please use Manual Entry.", Toast.LENGTH_SHORT).show();
    }

    private void onNetworkSelected(String ssid) {
        showPasswordDialog(ssid);
    }

    private void showManualEntryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Wi-Fi Details");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_wifi, null);
        EditText ssidInput = dialogView.findViewById(R.id.ssidInput);
        EditText passwordInput = dialogView.findViewById(R.id.passwordInput);

        builder.setView(dialogView);
        builder.setPositiveButton("Configure", (dialog, which) -> {
            String ssid = ssidInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (ssid.isEmpty()) {
                Toast.makeText(this, "Please enter network name", Toast.LENGTH_SHORT).show();
                return;
            }

            configurePrinter(ssid, password);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showPasswordDialog(String ssid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Password for: " + ssid);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_wifi_password, null);
        EditText passwordInput = dialogView.findViewById(R.id.passwordInput);

        builder.setView(dialogView);
        builder.setPositiveButton("Configure", (dialog, which) -> {
            String password = passwordInput.getText().toString();
            configurePrinter(ssid, password);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void configurePrinter(String ssid, String password) {
        scanWifiButton.setEnabled(false);
        manualEntryButton.setEnabled(false);
        scanProgressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.configuring);

        // Run configuration in background thread
        new Thread(() -> {
            try {
                boolean success = printerHelper.configurePrinterWifi(device, ssid, password);

                runOnUiThread(() -> {
                    scanProgressBar.setVisibility(View.GONE);
                    scanWifiButton.setEnabled(true);
                    manualEntryButton.setEnabled(true);

                    if (success) {
                        statusText.setText(R.string.success);
                        Toast.makeText(this, "Printer configured successfully!\nIt should connect to Wi-Fi shortly.", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        statusText.setText("Configuration sent. Check printer.");
                        Toast.makeText(this, "Configuration sent to printer. Please check if it connects to Wi-Fi.",
                            Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    scanProgressBar.setVisibility(View.GONE);
                    scanWifiButton.setEnabled(true);
                    manualEntryButton.setEnabled(true);
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
                startWifiScan();
            } else {
                Toast.makeText(this, "WiFi permissions required. Use Manual Entry instead.", Toast.LENGTH_LONG).show();
                statusText.setText("Use Manual Entry to configure");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
