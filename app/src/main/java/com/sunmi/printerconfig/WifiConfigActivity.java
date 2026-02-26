package com.sunmi.printerconfig;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class WifiConfigActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 3;

    private BluetoothDevice device;
    private TextView printerNameText;
    private Spinner wifiSpinner;
    private EditText passwordInput;
    private EditText manualSsidInput;
    private LinearLayout manualSsidContainer;
    private Button configureButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private WifiManager wifiManager;
    private PrinterConfigHelper printerHelper;

    private ArrayAdapter<String> wifiAdapter;
    private final List<String> availableSsids = new ArrayList<>();
    private boolean wifiReceiverRegistered = false;

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                updateNetworksFromScanResults();
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
        wifiSpinner = findViewById(R.id.wifiSpinner);
        passwordInput = findViewById(R.id.passwordInput);
        manualSsidInput = findViewById(R.id.manualSsidInput);
        manualSsidContainer = findViewById(R.id.manualSsidContainer);
        configureButton = findViewById(R.id.configureButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        printerHelper = new PrinterConfigHelper(this);

        boolean hasBluetoothPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        if (hasBluetoothPermission) {
            String deviceName = device.getName();
            printerNameText.setText(getString(R.string.connected_to, deviceName != null ? deviceName : "Unknown"));
        }

        setupWifiSpinner();

        configureButton.setOnClickListener(v -> {
            String selectedSsid = resolveSelectedSsid();
            String password = passwordInput.getText().toString();

            if (selectedSsid.isEmpty()) {
                Toast.makeText(this, "Please select or enter a Wi-Fi network", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.isEmpty()) {
                Toast.makeText(this, "Please enter Wi-Fi password", Toast.LENGTH_SHORT).show();
                return;
            }

            configurePrinter(selectedSsid, password);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerWifiScanReceiver();
        refreshWifiNetworks();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterWifiScanReceiver();
    }

    private void setupWifiSpinner() {
        wifiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        wifiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wifiSpinner.setAdapter(wifiAdapter);

        wifiSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean manualSelected = isManualEntrySelected(position);
                manualSsidContainer.setVisibility(manualSelected ? View.VISIBLE : View.GONE);
                if (!manualSelected) {
                    manualSsidInput.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                manualSsidContainer.setVisibility(View.GONE);
            }
        });

        updateWifiSpinner();
    }

    private String resolveSelectedSsid() {
        int selectedPosition = wifiSpinner.getSelectedItemPosition();
        if (selectedPosition < 0) {
            return "";
        }

        if (isManualEntrySelected(selectedPosition)) {
            return manualSsidInput.getText().toString().trim();
        }

        if (selectedPosition >= availableSsids.size()) {
            return "";
        }

        return availableSsids.get(selectedPosition);
    }

    private boolean isManualEntrySelected(int position) {
        return wifiAdapter != null && position == wifiAdapter.getCount() - 1;
    }

    private void refreshWifiNetworks() {
        if (!wifiManager.isWifiEnabled()) {
            showManualEntryOnly(getString(R.string.wifi_enable_required));
            return;
        }

        if (!checkWifiPermissions()) {
            requestWifiPermissions();
            return;
        }

        if (!isLocationEnabled()) {
            showManualEntryOnly(getString(R.string.wifi_enable_location_required));
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.wifi_scanning);

        boolean scanStarted;
        try {
            scanStarted = wifiManager.startScan();
        } catch (SecurityException e) {
            scanStarted = false;
        }

        if (!scanStarted) {
            updateNetworksFromScanResults();
            if (availableSsids.isEmpty()) {
                statusText.setText(R.string.wifi_scan_failed);
            }
        }
    }

    private void updateNetworksFromScanResults() {
        Set<String> uniqueSsids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            for (ScanResult result : scanResults) {
                if (result.SSID != null) {
                    String ssid = result.SSID.trim();
                    if (!ssid.isEmpty()) {
                        uniqueSsids.add(ssid);
                    }
                }
            }
        } catch (SecurityException e) {
            showManualEntryOnly(getString(R.string.wifi_scan_permissions_required));
            return;
        }

        availableSsids.clear();
        availableSsids.addAll(uniqueSsids);
        updateWifiSpinner();

        progressBar.setVisibility(View.GONE);
        if (availableSsids.isEmpty()) {
            statusText.setText(R.string.wifi_no_networks_found);
            manualSsidContainer.setVisibility(View.VISIBLE);
            wifiSpinner.setSelection(wifiAdapter.getCount() - 1);
        } else {
            statusText.setText(getString(R.string.wifi_networks_found, availableSsids.size()));
        }
    }

    private void updateWifiSpinner() {
        List<String> spinnerOptions = new ArrayList<>(availableSsids);
        spinnerOptions.add(getString(R.string.manual_entry_option));

        wifiAdapter.clear();
        wifiAdapter.addAll(spinnerOptions);
        wifiAdapter.notifyDataSetChanged();
    }

    private void showManualEntryOnly(String message) {
        progressBar.setVisibility(View.GONE);
        availableSsids.clear();
        updateWifiSpinner();

        statusText.setText(message);
        manualSsidContainer.setVisibility(View.VISIBLE);

        if (wifiAdapter.getCount() > 0) {
            wifiSpinner.setSelection(wifiAdapter.getCount() - 1);
        }
    }

    private boolean checkWifiPermissions() {
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasLocationPermission) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private void requestWifiPermissions() {
        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this,
                Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        if (permissions.isEmpty()) {
            refreshWifiNetworks();
            return;
        }

        ActivityCompat.requestPermissions(this,
            permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        }

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void registerWifiScanReceiver() {
        if (wifiReceiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wifiScanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(wifiScanReceiver, filter);
        }
        wifiReceiverRegistered = true;
    }

    private void unregisterWifiScanReceiver() {
        if (!wifiReceiverRegistered) {
            return;
        }

        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        wifiReceiverRegistered = false;
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
                refreshWifiNetworks();
            } else {
                Toast.makeText(this, R.string.wifi_scan_permissions_required,
                    Toast.LENGTH_SHORT).show();
            }
        }
    }
}
