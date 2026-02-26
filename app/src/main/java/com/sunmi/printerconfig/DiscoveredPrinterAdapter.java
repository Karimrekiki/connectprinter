package com.sunmi.printerconfig;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DiscoveredPrinterAdapter extends RecyclerView.Adapter<DiscoveredPrinterAdapter.ViewHolder> {
    private final List<DiscoveredPrinter> devices;
    private final OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(DiscoveredPrinter device);
    }

    public DiscoveredPrinterAdapter(List<DiscoveredPrinter> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_bluetooth_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiscoveredPrinter device = devices.get(position);

        String baseName = device.getName().isEmpty()
            ? holder.itemView.getContext().getString(R.string.unknown_device)
            : device.getName();

        if (PrinterDeviceClassifier.isLikelySunmi(device.getName())) {
            baseName = baseName + " \u2022 " + holder.itemView.getContext().getString(R.string.likely_sunmi);
        }

        holder.deviceName.setText(baseName);
        holder.deviceAddress.setText(device.getAddress());
        holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView deviceName;
        private final TextView deviceAddress;

        ViewHolder(View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceAddress = itemView.findViewById(R.id.deviceAddress);
        }
    }
}
