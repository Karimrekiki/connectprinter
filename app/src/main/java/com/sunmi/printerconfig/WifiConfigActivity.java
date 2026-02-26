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

import com.sunmi.cloudprinter.bean.PrinterDevice;
import com.sunmi.cloudprinter.bean.Router;
import com.sunmi.cloudprinter.presenter.SunmiPrinterClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class WifiConfigActivity extends AppCompatActivity implements SunmiPrinterClient.IPrinterClient {
    private static final int PERMISSION_REQUEST_CODE = 3;

    private String printerAddress;
    private String printerName;

    private TextView printerNameText;
    private Spinner wifiSpinner;
    private EditText passwordInput;
    private EditText manualSsidInput;
    private LinearLayout manualSsidContainer;
    private Button configureButton;
    private ProgressBar progressBar;
    private TextView statusText;

    private WifiManager wifiManager;
    private SunmiPrinterClient sunmiPrinterClient;

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

        printerAddress = getIntent().getStringExtra("device_address");
        printerName = getIntent().getStringExtra("device_name");

        // Backward-compatibility fallback for old intents.
        if ((printerAddress == null || printerAddress.isEmpty()) || printerName == null) {
            BluetoothDevice device = getIntent().getParcelableExtra("device");
            if (device != null) {
                printerAddress = safeGetDeviceAddress(device);
                if (printerName == null || printerName.isEmpty()) {
                    printerName = safeGetDeviceName(device);
                }
            }
        }

        if (printerAddress == null || printerAddress.isEmpty()) {
            Toast.makeText(this, R.string.printer_address_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (printerName == null || printerName.isEmpty()) {
            printerName = getString(R.string.unknown_device);
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
        sunmiPrinterClient = new SunmiPrinterClient(this, this);

        printerNameText.setText(getString(R.string.connected_to, printerName));

        setupWifiSpinner();

        configureButton.setOnClickListener(v -> {
            String selectedSsid = resolveSelectedSsid();
            String password = passwordInput.getText().toString();

            if (selectedSsid.isEmpty()) {
                Toast.makeText(this, R.string.select_or_enter_wifi_network, Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.isEmpty()) {
                Toast.makeText(this, R.string.enter_wifi_password, Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (sunmiPrinterClient != null && printerAddress != null && !printerAddress.isEmpty()) {
            try {
                sunmiPrinterClient.disconnect(printerAddress);
            } catch (Throwable ignored) {
            }
        }
    }

    private String safeGetDeviceAddress(BluetoothDevice device) {
        try {
            return device.getAddress() == null ? "" : device.getAddress();
        } catch (SecurityException e) {
            return "";
        }
    }

    private String safeGetDeviceName(BluetoothDevice device) {
        try {
            return device.getName() == null ? getString(R.string.unknown_device) : device.getName();
        } catch (SecurityException e) {
            return getString(R.string.unknown_device);
        }
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
            if (!availableSsids.isEmpty()) {
                return availableSsids.get(0);
            }
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
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
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

        if (wifiAdapter.getCount() == 0) {
            return;
        }

        if (availableSsids.isEmpty()) {
            wifiSpinner.setSelection(wifiAdapter.getCount() - 1);
            manualSsidContainer.setVisibility(View.VISIBLE);
        } else {
            wifiSpinner.setSelection(0);
            manualSsidContainer.setVisibility(View.GONE);
        }
    }

    private void showManualEntryOnly(String message) {
        progressBar.setVisibility(View.GONE);
        availableSsids.clear();
        updateWifiSpinner();

        statusText.setText(message);
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
        statusText.setText(R.string.sending_wifi_to_printer);

        try {
            byte[] ssidBytes = ssid.getBytes(StandardCharsets.UTF_8);
            sunmiPrinterClient.setPrinterWifi(printerAddress, ssidBytes, password);
        } catch (Throwable t) {
            handleConfigurationFailure(getString(R.string.wifi_push_failed_try_24g));
        }
    }

    private void handleConfigurationSuccess() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            configureButton.setEnabled(true);
            statusText.setText(R.string.success);
            Toast.makeText(this, R.string.success, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void handleConfigurationFailure(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            configureButton.setEnabled(true);
            statusText.setText(getString(R.string.error, message));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPrinterFount(PrinterDevice printerDevice) {
        // Not used in this activity.
    }

    @Override
    public void routerFound(Router router) {
        // Not used in this activity.
    }

    @Override
    public void onGetWifiListFinish() {
        // Not used in this activity.
    }

    @Override
    public void onGetWifiListFail() {
        // Not used in this activity.
    }

    @Override
    public void onSetWifiSuccess() {
        runOnUiThread(() -> statusText.setText(R.string.configuring));
    }

    @Override
    public void wifiConfigSuccess() {
        handleConfigurationSuccess();
    }

    @Override
    public void onWifiConfigFail() {
        handleConfigurationFailure(getString(R.string.wifi_push_failed_try_24g));
    }

    @Override
    public void sendDataFail(int code, String msg) {
        String failureMessage = getString(R.string.wifi_push_error_with_code, code, msg == null ? "Unknown" : msg);
        handleConfigurationFailure(failureMessage);
    }

    @Override
    public void getSnRequestSuccess() {
        // Not used in this activity.
    }

    @Override
    public void onSnReceived(String sn) {
        // Not used in this activity.
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
                Toast.makeText(this, R.string.wifi_scan_permissions_required, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
