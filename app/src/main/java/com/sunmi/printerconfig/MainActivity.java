package com.sunmi.printerconfig;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int SCAN_TIMEOUT = 15000; // 15 seconds

    private BluetoothAdapter bluetoothAdapter;
    private Button scanButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView devicesRecyclerView;
    private PrinterDeviceAdapter deviceAdapter;
    private List<PrinterDevice> deviceList;
    private Set<String> foundMacAddresses;

    private SunmiPrinterClient sunmiPrinterClient;
    private Handler scanTimeoutHandler;
    private Runnable scanTimeoutRunnable;
    private PrinterDevice selectedDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanButton = findViewById(R.id.scanButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView);

        deviceList = new ArrayList<>();
        foundMacAddresses = new HashSet<>();
        deviceAdapter = new PrinterDeviceAdapter(deviceList, this::onDeviceClick);

        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(deviceAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Sunmi Printer Client
        sunmiPrinterClient = new SunmiPrinterClient(this, this);

        scanTimeoutHandler = new Handler();
        scanTimeoutRunnable = () -> {
            stopScan();
            if (deviceList.isEmpty()) {
                statusText.setText(R.string.no_devices_found);
            } else {
                statusText.setText("Found " + deviceList.size() + " printer(s)");
            }
        };

        scanButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startPrinterScan();
            } else {
                requestPermissions();
            }
        });

        // Request permissions at startup
        if (!checkPermissions()) {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
        }
    }

    private void startPrinterScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        Log.d(TAG, "Starting Sunmi BLE printer scan");
        deviceList.clear();
        foundMacAddresses.clear();
        deviceAdapter.notifyDataSetChanged();
        statusText.setText(R.string.scanning);

        scanButton.setEnabled(false);
        scanButton.setText(R.string.scanning);
        progressBar.setVisibility(View.VISIBLE);

        // Start scan using Sunmi SDK
        sunmiPrinterClient.startScan();

        // Set timeout
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT);
    }

    private void stopScan() {
        Log.d(TAG, "Stopping printer scan");
        sunmiPrinterClient.stopScan();
        scanButton.setEnabled(true);
        scanButton.setText(R.string.scan_bluetooth);
        progressBar.setVisibility(View.GONE);
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable);
    }

    private void onDeviceClick(PrinterDevice device) {
        Log.d(TAG, "Device clicked: " + device.getName() + " - " + device.getAddress());
        stopScan();

        // Store selected device
        selectedDevice = device;

        // Show loading and establish BLE connection first
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Connecting to printer...");

        // CRITICAL: Call getPrinterSn to establish BLE connection
        // This MUST be called before any WiFi operations
        sunmiPrinterClient.getPrinterSn(device.getAddress());
        Log.d(TAG, "Called getPrinterSn() to establish BLE connection");
    }

    // SunmiPrinterClient.IPrinterClient callbacks

    @Override
    public void onPrinterFount(PrinterDevice printerDevice) {
        Log.d(TAG, "Printer found: " + printerDevice.getName() + " - " + printerDevice.getAddress());

        runOnUiThread(() -> {
            if (!foundMacAddresses.contains(printerDevice.getAddress())) {
                foundMacAddresses.add(printerDevice.getAddress());
                deviceList.add(printerDevice);
                deviceAdapter.notifyDataSetChanged();
                statusText.setText("Found " + deviceList.size() + " printer(s)");
            }
        });
    }

    @Override
    public void sendDataFail(int code, String msg) {
        Log.e(TAG, "Send data failed: " + code + " - " + msg);

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            statusText.setText("Connection failed: " + msg);
            Toast.makeText(this, "Failed to connect to printer: " + msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void getSnRequestSuccess() {
        Log.d(TAG, "SN request sent successfully");
    }

    @Override
    public void onSnReceived(String sn) {
        // BLE connection established! Now we can proceed to WiFi config
        Log.d(TAG, "BLE connection established! Printer SN: " + sn);

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);

            if (selectedDevice != null) {
                Intent intent = new Intent(this, WifiConfigActivity.class);
                intent.putExtra("device_address", selectedDevice.getAddress());
                intent.putExtra("device_name", selectedDevice.getName() != null ? selectedDevice.getName() : "Unknown");
                intent.putExtra("device_sn", sn);
                startActivity(intent);
                Log.d(TAG, "Navigating to WifiConfigActivity with BLE connection established");
            } else {
                Log.e(TAG, "selectedDevice is null in onSnReceived!");
                statusText.setText("Error: Device not selected");
            }
        });
    }

    @Override
    public void onGetWifiListFinish() {
        // Not used in scanning
    }

    @Override
    public void onGetWifiListFail() {
        // Not used in scanning
    }

    @Override
    public void onSetWifiSuccess() {
        // Not used in scanning
    }

    @Override
    public void wifiConfigSuccess() {
        // Not used in scanning
    }

    @Override
    public void onWifiConfigFail() {
        // Not used in scanning
    }

    @Override
    public void routerFound(Router router) {
        // Not used in scanning
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
                Toast.makeText(this, "Permissions granted! Tap Scan to find printers.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sunmiPrinterClient != null) {
            sunmiPrinterClient.stopScan();
        }
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable);
    }
}
