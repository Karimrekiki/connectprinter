package com.sunmi.printerconfig;

import java.util.Objects;

public final class DiscoveredPrinter {
    private final String address;
    private final String name;

    public DiscoveredPrinter(String address, String name) {
        this.address = address == null ? "" : address.trim();
        this.name = name == null ? "" : name.trim();
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiscoveredPrinter)) {
            return false;
        }
        DiscoveredPrinter that = (DiscoveredPrinter) other;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
