package com.hypno.hypnovibe.domain.entity;

import com.hypno.hypnovibe.domain.AdapterStatus;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;

/**
 * 已连接设备的运行时数据。持有 adapter 引用，供 UI 和测试面板使用。
 */
public class ConnectedDevice {
    private final String deviceId;
    private final String name;
    private final String mac;
    private final DeviceProtocolAdapter adapter;
    private volatile AdapterStatus.State state;

    public ConnectedDevice(String deviceId, String name, String mac,
                           DeviceProtocolAdapter adapter, AdapterStatus.State state) {
        this.deviceId = deviceId;
        this.name = name;
        this.mac = mac;
        this.adapter = adapter;
        this.state = state;
    }

    public String getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public String getMac() { return mac; }
    public DeviceProtocolAdapter getAdapter() { return adapter; }
    public AdapterStatus.State getState() { return state; }
    public void setState(AdapterStatus.State state) { this.state = state; }
}
