package com.sunmi.printerconfig;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.sunmi.cloudprinter.bean.PrinterDevice;
import com.sunmi.cloudprinter.bean.Router;
import com.sunmi.cloudprinter.presenter.SunmiPrinterClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WifiConfigActivity extends AppCompatActivity implements SunmiPrinterClient.IPrinterClient {
    private static final int WIFI_CONFIG_TIMEOUT_MS = 25_000;

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

    private SunmiPrinterClient sunmiPrinterClient;

    private ArrayAdapter<String> wifiAdapter;
    private final List<Router> availableRouters = new ArrayList<>();
    private boolean waitingForWifiConfigResult = false;

    private final Handler wifiConfigTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable wifiConfigTimeoutRunnable = () -> {
        if (waitingForWifiConfigResult) {
            handleConfigurationFailure(getString(R.string.printer_wifi_config_timeout));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_config);

        printerAddress = getIntent().getStringExtra("device_address");
        printerName = getIntent().getStringExtra("device_name");

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

        sunmiPrinterClient = new SunmiPrinterClient(
            new ReceiverSafeContext(getApplicationContext()),
            this
        );

        printerNameText.setText(getString(R.string.connected_to, printerName));

        setupWifiSpinner();

        configureButton.setOnClickListener(v -> {
            Router selectedRouter = resolveSelectedRouter();
            String manualSsid = manualSsidInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (selectedRouter == null) {
                if (manualSsid.isEmpty()) {
                    Toast.makeText(this, R.string.select_or_enter_wifi_network, Toast.LENGTH_SHORT).show();
                    return;
                }
                configurePrinter(buildManualRouter(manualSsid), password);
                return;
            }

            if (selectedRouter.isHasPwd() && password.isEmpty()) {
                Toast.makeText(this, R.string.enter_wifi_password, Toast.LENGTH_SHORT).show();
                return;
            }

            configurePrinter(selectedRouter, password);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadNetworksFromPrinter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        waitingForWifiConfigResult = false;
        wifiConfigTimeoutHandler.removeCallbacks(wifiConfigTimeoutRunnable);

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

    private Router resolveSelectedRouter() {
        int selectedPosition = wifiSpinner.getSelectedItemPosition();

        if (selectedPosition < 0 || selectedPosition >= availableRouters.size()) {
            return null;
        }

        if (isManualEntrySelected(selectedPosition)) {
            return null;
        }

        return availableRouters.get(selectedPosition);
    }

    private boolean isManualEntrySelected(int position) {
        return wifiAdapter != null && position == wifiAdapter.getCount() - 1;
    }

    private void updateWifiSpinner() {
        List<String> options = new ArrayList<>();
        for (Router router : availableRouters) {
            options.add(getRouterDisplayName(router));
        }
        options.add(getString(R.string.manual_entry_option));

        wifiAdapter.clear();
        wifiAdapter.addAll(options);
        wifiAdapter.notifyDataSetChanged();

        if (!availableRouters.isEmpty()) {
            wifiSpinner.setSelection(0);
            manualSsidContainer.setVisibility(View.GONE);
            configureButton.setEnabled(true);
        } else {
            wifiSpinner.setSelection(wifiAdapter.getCount() - 1);
            manualSsidContainer.setVisibility(View.VISIBLE);
            configureButton.setEnabled(true);
        }
    }

    private String getRouterDisplayName(Router router) {
        String name = router.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }

        byte[] essid = router.getEssid();
        if (essid == null || essid.length == 0) {
            return getString(R.string.unknown_device);
        }

        return new String(essid, StandardCharsets.UTF_8).trim();
    }

    private Router buildManualRouter(String ssid) {
        Router router = new Router();
        router.setName(ssid);
        router.setEssid(ssid.getBytes(StandardCharsets.UTF_8));
        router.setHasPwd(true);
        return router;
    }

    private void loadNetworksFromPrinter() {
        configureButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.printer_wifi_scanning);

        availableRouters.clear();
        updateWifiSpinner();

        try {
            sunmiPrinterClient.getPrinterWifiList(printerAddress);
        } catch (Throwable t) {
            progressBar.setVisibility(View.GONE);
            statusText.setText(R.string.printer_wifi_scan_failed);
            configureButton.setEnabled(true);
        }
    }

    private void configurePrinter(Router router, String password) {
        waitingForWifiConfigResult = true;
        wifiConfigTimeoutHandler.removeCallbacks(wifiConfigTimeoutRunnable);
        wifiConfigTimeoutHandler.postDelayed(wifiConfigTimeoutRunnable, WIFI_CONFIG_TIMEOUT_MS);

        configureButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.sending_wifi_to_printer);

        try {
            sunmiPrinterClient.setPrinterWifi(printerAddress, router.getEssid(), password == null ? "" : password);
        } catch (Throwable t) {
            handleConfigurationFailure(getString(R.string.wifi_push_failed_try_24g));
        }
    }

    private void handleConfigurationSuccess() {
        waitingForWifiConfigResult = false;
        wifiConfigTimeoutHandler.removeCallbacks(wifiConfigTimeoutRunnable);

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            configureButton.setEnabled(true);
            statusText.setText(R.string.success);
            Toast.makeText(this, R.string.success, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void handleConfigurationFailure(String message) {
        waitingForWifiConfigResult = false;
        wifiConfigTimeoutHandler.removeCallbacks(wifiConfigTimeoutRunnable);

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
        runOnUiThread(() -> {
            availableRouters.add(router);
            updateWifiSpinner();
        });
    }

    @Override
    public void onGetWifiListFinish() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            configureButton.setEnabled(true);

            if (availableRouters.isEmpty()) {
                statusText.setText(R.string.printer_wifi_no_networks_found);
                manualSsidContainer.setVisibility(View.VISIBLE);
            } else {
                statusText.setText(getString(R.string.wifi_networks_found, availableRouters.size()));
            }
        });
    }

    @Override
    public void onGetWifiListFail() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            configureButton.setEnabled(true);
            statusText.setText(R.string.printer_wifi_scan_failed);
            manualSsidContainer.setVisibility(View.VISIBLE);
        });
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
}
