package com.sunmi.printerconfig;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class WifiNetworkAdapter extends RecyclerView.Adapter<WifiNetworkAdapter.ViewHolder> {
    private final List<String> networks;
    private final OnNetworkClickListener listener;

    public interface OnNetworkClickListener {
        void onNetworkClick(String ssid);
    }

    public WifiNetworkAdapter(List<String> networks, OnNetworkClickListener listener) {
        this.networks = networks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_wifi_network, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String ssid = networks.get(position);
        holder.networkName.setText(ssid);
        holder.itemView.setOnClickListener(v -> listener.onNetworkClick(ssid));
    }

    @Override
    public int getItemCount() {
        return networks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView networkName;

        ViewHolder(View itemView) {
            super(itemView);
            networkName = itemView.findViewById(R.id.networkName);
        }
    }
}
