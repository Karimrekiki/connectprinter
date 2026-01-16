package com.sunmi.printerconfig;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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

import com.sunmi.cloudprinter.bean.PrinterDevice;
import com.sunmi.cloudprinter.bean.Router;
import com.sunmi.cloudprinter.presenter.SunmiPrinterClient;

import java.util.ArrayList;
import java.util.List;

public class WifiConfigActivity extends AppCompatActivity implements SunmiPrinterClient.IPrinterClient {
    private static final String TAG = "WifiConfigActivity";
    private static final int PERMISSION_REQUEST_CODE = 3;

    private String bleAddress;
    private TextView printerNameText;
    private RecyclerView wifiNetworksRecycler;
    private Button scanWifiButton;
    private Button manualEntryButton;
    private ProgressBar scanProgressBar;
    private TextView statusText;
    private SunmiPrinterClient sunmiPrinterClient;
    private WifiNetworkAdapter wifiAdapter;
    private List<Router> wifiNetworks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_config);

        bleAddress = getIntent().getStringExtra("device_address");
        String deviceName = getIntent().getStringExtra("device_name");

        if (bleAddress == null) {
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

        printerNameText.setText(getString(R.string.connected_to, deviceName != null ? deviceName : "Unknown"));

        // Initialize Sunmi Printer Client
        sunmiPrinterClient = new SunmiPrinterClient(this, this);

        wifiNetworks = new ArrayList<>();
        wifiAdapter = new WifiNetworkAdapter(wifiNetworks, this::onNetworkSelected);
        wifiNetworksRecycler.setLayoutManager(new LinearLayoutManager(this));
        wifiNetworksRecycler.setAdapter(wifiAdapter);

        scanWifiButton.setOnClickListener(v -> startPrinterWifiScan());
        manualEntryButton.setOnClickListener(v -> showManualEntryDialog());

        // Start scanning automatically
        startPrinterWifiScan();
    }

    private void startPrinterWifiScan() {
        Log.d(TAG, "Starting WiFi scan for printer: " + bleAddress);
        scanWifiButton.setEnabled(false);
        scanProgressBar.setVisibility(View.VISIBLE);
        statusText.setText("Getting Wi-Fi networks from printer...");
        wifiNetworks.clear();
        wifiAdapter.notifyDataSetChanged();

        // Use Sunmi SDK to get WiFi list from printer
        sunmiPrinterClient.getPrinterWifiList(bleAddress);
        Log.d(TAG, "Called getPrinterWifiList()");
    }

    private void onNetworkSelected(Router router) {
        if (router.isHasPwd()) {
            showPasswordDialog(router);
        } else {
            // Network without password
            configurePrinter(router, "");
        }
    }

    private void showManualEntryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manual Network Entry");
        builder.setMessage("⚠️ Important: The printer likely only supports 2.4GHz WiFi.\n\n" +
                "If your network is 5GHz only, the printer may not be able to connect even after configuration.");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_wifi, null);
        EditText ssidInput = dialogView.findViewById(R.id.ssidInput);
        EditText passwordInput = dialogView.findViewById(R.id.passwordInput);

        builder.setView(dialogView);
        builder.setPositiveButton("Configure Anyway", (dialog, which) -> {
            String ssid = ssidInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (ssid.isEmpty()) {
                Toast.makeText(this, "Please enter network name", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create a manual Router object
            Router manualRouter = new Router();
            manualRouter.setEssid(ssid.getBytes());
            manualRouter.setName(ssid);
            manualRouter.setHasPwd(!password.isEmpty());
            configurePrinter(manualRouter, password);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showPasswordDialog(Router router) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Password for: " + router.getName());

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_wifi_password, null);
        EditText passwordInput = dialogView.findViewById(R.id.passwordInput);

        builder.setView(dialogView);
        builder.setPositiveButton("Configure", (dialog, which) -> {
            String password = passwordInput.getText().toString();
            configurePrinter(router, password);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void configurePrinter(Router router, String password) {
        Log.d(TAG, "Configuring WiFi for network: " + router.getName());
        scanWifiButton.setEnabled(false);
        manualEntryButton.setEnabled(false);
        scanProgressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.configuring);

        // Use Sunmi SDK to configure printer WiFi
        sunmiPrinterClient.setPrinterWifi(bleAddress, router.getEssid(), password);
        Log.d(TAG, "Called setPrinterWifi()");
    }

    // IPrinterClient callback methods

    @Override
    public void onPrinterFount(PrinterDevice printerDevice) {
        // Called when printer is found during scanning
    }

    @Override
    public void routerFound(Router router) {
        // Called for each WiFi network found by the printer
        Log.d(TAG, "Router found: " + router.getName() + " (secured: " + router.isHasPwd() + ")");
        runOnUiThread(() -> {
            wifiNetworks.add(router);
            wifiAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onGetWifiListFinish() {
        // Called when WiFi list retrieval is complete
        Log.d(TAG, "WiFi list retrieval finished. Found " + wifiNetworks.size() + " networks");
        runOnUiThread(() -> {
            scanProgressBar.setVisibility(View.GONE);
            scanWifiButton.setEnabled(true);

            if (wifiNetworks.isEmpty()) {
                statusText.setText("No networks found. Use Manual Entry below.");
                showNetworkInfoDialog();
            } else {
                statusText.setText("Found " + wifiNetworks.size() + " network(s). Don't see yours? Use Manual Entry.");
            }
        });
    }

    private void showNetworkInfoDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About WiFi Networks")
            .setMessage("The printer only shows 2.4GHz WiFi networks it can detect.\n\n" +
                    "If your network isn't shown:\n" +
                    "• Your network might be 5GHz only\n" +
                    "• Use the 'Manual Entry' button below\n" +
                    "• Make sure your router has 2.4GHz enabled\n\n" +
                    "Note: Most Sunmi printers only support 2.4GHz WiFi.")
            .setPositiveButton("Got it", null)
            .setNegativeButton("Manual Entry", (dialog, which) -> showManualEntryDialog())
            .show();
    }

    @Override
    public void onGetWifiListFail() {
        // Called when WiFi list retrieval fails
        Log.e(TAG, "Failed to get WiFi list from printer");
        runOnUiThread(() -> {
            scanProgressBar.setVisibility(View.GONE);
            scanWifiButton.setEnabled(true);
            statusText.setText("Failed to get networks. Use Manual Entry.");
            Toast.makeText(this, "Failed to get WiFi list from printer. Please use Manual Entry.", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onSetWifiSuccess() {
        // Called when WiFi configuration command is sent successfully
        Log.d(TAG, "WiFi config command sent successfully");
        runOnUiThread(() -> {
            statusText.setText("Configuring WiFi...");
        });
    }

    @Override
    public void wifiConfigSuccess() {
        // Called when WiFi configuration is confirmed successful
        Log.d(TAG, "WiFi configuration confirmed successful!");
        runOnUiThread(() -> {
            new Handler().postDelayed(() -> {
                scanProgressBar.setVisibility(View.GONE);
                scanWifiButton.setEnabled(true);
                manualEntryButton.setEnabled(true);
                statusText.setText(R.string.success);
                Toast.makeText(this, "Printer configured successfully!\nIt should connect to Wi-Fi shortly.", Toast.LENGTH_LONG).show();
                finish();
            }, 1500);
        });
    }

    @Override
    public void onWifiConfigFail() {
        // Called when WiFi configuration fails
        Log.e(TAG, "WiFi configuration failed");
        runOnUiThread(() -> {
            scanProgressBar.setVisibility(View.GONE);
            scanWifiButton.setEnabled(true);
            manualEntryButton.setEnabled(true);
            statusText.setText("Configuration failed");
            Toast.makeText(this, "Failed to configure WiFi. Please try again.", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void sendDataFail(int code, String msg) {
        // Called when data send fails
        Log.e(TAG, "Send data failed - Code: " + code + ", Message: " + msg);
        runOnUiThread(() -> {
            scanProgressBar.setVisibility(View.GONE);
            scanWifiButton.setEnabled(true);
            manualEntryButton.setEnabled(true);
            statusText.setText("Connection error");
            Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void getSnRequestSuccess() {
        // Called when SN request succeeds
    }

    @Override
    public void onSnReceived(String sn) {
        // Called when SN is received
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sunmiPrinterClient != null) {
            sunmiPrinterClient.disconnect(bleAddress);
        }
    }
}
