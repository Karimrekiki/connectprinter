package com.sunmi.printerconfig;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sunmi.cloudprinter.bean.PrinterDevice;
import com.sunmi.cloudprinter.bean.Router;
import com.sunmi.cloudprinter.presenter.SunmiPrinterClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements SunmiPrinterClient.IPrinterClient {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int PRINTER_SCAN_TIMEOUT_MS = 12_000;
    private static final int PRINTER_CONNECTION_TIMEOUT_MS = 10_000;

    private BluetoothAdapter bluetoothAdapter;
    private Button scanButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView devicesRecyclerView;
    private DiscoveredPrinterAdapter deviceAdapter;
    private final List<DiscoveredPrinter> deviceList = new ArrayList<>();
    private final Set<String> discoveredAddresses = new HashSet<>();

    private SunmiPrinterClient sunmiPrinterClient;
    private final Handler scanTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Handler connectionTimeoutHandler = new Handler(Looper.getMainLooper());

    private String pendingPrinterAddress;
    private String pendingPrinterName;
    private boolean scanInProgress = false;
    private boolean waitingForPrinterConnection = false;

    private final Runnable scanTimeoutRunnable = () -> {
        if (!scanInProgress || waitingForPrinterConnection) {
            return;
        }

        stopPrinterScan(true);
        if (deviceList.isEmpty()) {
            statusText.setText(R.string.no_compatible_printers_found);
        }
    };

    private final Runnable connectionTimeoutRunnable = () -> {
        if (waitingForPrinterConnection) {
            handlePrinterConnectionFailure(getString(R.string.printer_connection_timeout));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanButton = findViewById(R.id.scanButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView);

        deviceAdapter = new DiscoveredPrinterAdapter(deviceList, this::onDeviceClick);
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(deviceAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sunmiPrinterClient = new SunmiPrinterClient(
            new ReceiverSafeContext(getApplicationContext()),
            this
        );

        scanButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startBluetoothScan();
            } else {
                requestPermissions();
            }
        });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                },
                PERMISSION_REQUEST_CODE);
            return;
        }

        ActivityCompat.requestPermissions(this,
            new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            },
            PERMISSION_REQUEST_CODE);
    }

    private void startBluetoothScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        waitingForPrinterConnection = false;
        stopPrinterScan(false);

        scanInProgress = true;
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable);
        connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);

        discoveredAddresses.clear();
        deviceList.clear();
        deviceAdapter.notifyDataSetChanged();
        statusText.setText(R.string.scanning);

        scanButton.setEnabled(false);
        scanButton.setText(R.string.scanning);
        progressBar.setVisibility(View.VISIBLE);

        try {
            sunmiPrinterClient.startScan();
            scanTimeoutHandler.postDelayed(scanTimeoutRunnable, PRINTER_SCAN_TIMEOUT_MS);
        } catch (Throwable t) {
            stopPrinterScan(true);
            statusText.setText(getString(R.string.error, getString(R.string.printer_scan_failed)));
        }
    }

    private void stopPrinterScan(boolean resetUi) {
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable);
        if (scanInProgress) {
            scanInProgress = false;
            try {
                sunmiPrinterClient.stopScan();
            } catch (Throwable ignored) {
            }
        }
        if (resetUi && !waitingForPrinterConnection) {
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText(R.string.scan_bluetooth);
        }
    }

    private void onDeviceClick(DiscoveredPrinter device) {
        stopPrinterScan(false);

        pendingPrinterAddress = device.getAddress();
        pendingPrinterName = device.getName().isEmpty()
            ? getString(R.string.unknown_device)
            : device.getName();

        if (pendingPrinterAddress.isEmpty()) {
            Toast.makeText(this, R.string.printer_address_unavailable, Toast.LENGTH_LONG).show();
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText(R.string.scan_bluetooth);
            return;
        }

        waitingForPrinterConnection = true;
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        statusText.setText(R.string.connecting_to_printer);

        connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, PRINTER_CONNECTION_TIMEOUT_MS);

        try {
            // Match the known stable flow: establish BLE session before Wi-Fi config screen.
            sunmiPrinterClient.getPrinterSn(pendingPrinterAddress);
        } catch (Throwable t) {
            handlePrinterConnectionFailure(getString(R.string.printer_connection_failed));
        }
    }

    private void openWifiConfigScreen() {
        Intent intent = new Intent(this, WifiConfigActivity.class);
        intent.putExtra("device_address", pendingPrinterAddress);
        intent.putExtra("device_name", pendingPrinterName);
        startActivity(intent);
    }

    private void handlePrinterConnectionFailure(String message) {
        waitingForPrinterConnection = false;
        connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText(R.string.scan_bluetooth);
            statusText.setText(getString(R.string.error, message));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPrinterFount(PrinterDevice printerDevice) {
        if (printerDevice == null) {
            return;
        }

        String address = printerDevice.getAddress();
        if (address == null || address.trim().isEmpty()) {
            return;
        }

        String name = printerDevice.getName();
        runOnUiThread(() -> {
            if (!scanInProgress || !discoveredAddresses.add(address)) {
                return;
            }

            deviceList.add(new DiscoveredPrinter(address, name));
            deviceAdapter.notifyDataSetChanged();
            statusText.setText(getString(R.string.printers_found, deviceList.size()));
        });
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
        // Not used in this activity.
    }

    @Override
    public void wifiConfigSuccess() {
        // Not used in this activity.
    }

    @Override
    public void onWifiConfigFail() {
        // Not used in this activity.
    }

    @Override
    public void sendDataFail(int code, String msg) {
        String failure = getString(R.string.wifi_push_error_with_code, code, msg == null ? "Unknown" : msg);
        handlePrinterConnectionFailure(failure);
    }

    @Override
    public void getSnRequestSuccess() {
        // Wait for onSnReceived callback.
    }

    @Override
    public void onSnReceived(String sn) {
        stopPrinterScan(false);
        waitingForPrinterConnection = false;
        connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText(R.string.scan_bluetooth);
            statusText.setText("");
            openWifiConfigScreen();
        });
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
                startBluetoothScan();
            } else {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPrinterScan(false);
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable);
        connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);

        if (sunmiPrinterClient != null && pendingPrinterAddress != null && !pendingPrinterAddress.isEmpty()) {
            try {
                sunmiPrinterClient.disconnect(pendingPrinterAddress);
            } catch (Throwable ignored) {
            }
        }
    }
}
