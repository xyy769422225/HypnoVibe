package com.hypno.hypnovibe.domain.entity;

import java.util.*;

public class PairedDevice {
    private String macAddress;
    private String deviceType;
    private String userAlias;
    private String originalName;
    private long lastConnectedAt;
    private boolean isFavorite;
    private boolean isConnected;
    private int batteryLevel = -1;

    public PairedDevice() {}

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getUserAlias() { return userAlias; }
    public void setUserAlias(String userAlias) { this.userAlias = userAlias; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public long getLastConnectedAt() { return lastConnectedAt; }
    public void setLastConnectedAt(long lastConnectedAt) { this.lastConnectedAt = lastConnectedAt; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public boolean isConnected() { return isConnected; }
    public void setConnected(boolean connected) { isConnected = connected; }

    public int getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
}
