package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;

import com.hypno.hypnovibe.domain.AdapterStatus;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;
import com.hypno.hypnovibe.domain.entity.ConnectedDevice;
import com.hypno.hypnovibe.domain.entity.PairedDevice;
import com.hypno.hypnovibe.infrastructure.ble.BleScanner;
import com.hypno.hypnovibe.infrastructure.ble.adapter.coyote.CoyoteV3Adapter;
import com.hypno.hypnovibe.infrastructure.persistence.DevicePrefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * 设备管理 ViewModel。
 * <p>
 * 职责：
 * 1. 持久化已配对设备（{@link PairedDevice}/{@link DevicePrefs}）。
 * 2. 维护运行时的 BLE 连接（{@link ConnectedDevice}，按 mac 索引）。
 * 3. 合并「已保存设备 + 实时连接状态」对外暴露统一的 {@link DeviceItem} 列表。
 * 4. 按 {@code deviceType} 驱动扫描与连接。
 * <p>
 * 该 VM 应在 Activity 级共享（见 {@code rememberDeviceManagerVM}），以保证跨页面后
 * adapter 连接实例不丢失。
 */
public class DeviceManagerVM extends AndroidViewModel {
    private static final String TAG = "DeviceManagerVM";

    /** 唯一已实现的设备类型 */
    public static final String TYPE_COYOTE_V3 = "coyote_v3";

    private final BleScanner bleScanner;
    private final DevicePrefs devicePrefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 运行时已连接设备：mac -> ConnectedDevice */
    private final ConcurrentMap<String, ConnectedDevice> connectedMap = new ConcurrentHashMap<>();

    /** 持久化的配对设备列表 */
    private final MutableStateFlow<List<PairedDevice>> savedDevices;
    /** 对外暴露的合并视图（已保存 + 实时状态） */
    private final MutableStateFlow<List<DeviceItem>> deviceList =
            StateFlowKt.MutableStateFlow(new ArrayList<>());

    private final MutableStateFlow<Boolean> isScanning = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<String> errorMsg = StateFlowKt.MutableStateFlow((String) null);
    private final MutableStateFlow<List<ScanResultItem>> scanResults =
            StateFlowKt.MutableStateFlow(new ArrayList<>());

    /** 扫描发现的设备（mac, name, rssi） */
    public static class ScanResultItem {
        public final String mac;
        public final String name;
        public final int rssi;
        public ScanResultItem(String mac, String name, int rssi) {
            this.mac = mac; this.name = name; this.rssi = rssi;
        }
    }

    /** 设备管理页展示用的合并数据项 */
    public static class DeviceItem {
        public final String mac;
        public final String name;
        public final String deviceType;
        public final boolean connected;
        /** 已连接时非空，用于跳转测试面板 */
        public final String deviceId;
        /** 已连接时的实时状态；未连接为 null */
        public final AdapterStatus.State state;
        public DeviceItem(String mac, String name, String deviceType,
                          boolean connected, String deviceId, AdapterStatus.State state) {
            this.mac = mac; this.name = name; this.deviceType = deviceType;
            this.connected = connected; this.deviceId = deviceId; this.state = state;
        }
    }

    public DeviceManagerVM(Application app) {
        super(app);
        bleScanner = new BleScanner();
        devicePrefs = new DevicePrefs(app);
        savedDevices = StateFlowKt.MutableStateFlow(devicePrefs.loadAll());
        rebuildList();
    }

    public StateFlow<List<DeviceItem>> getDeviceList() { return deviceList; }
    public StateFlow<Boolean> getIsScanning() { return isScanning; }
    public StateFlow<String> getErrorMsg() { return errorMsg; }
    public StateFlow<List<ScanResultItem>> getScanResults() { return scanResults; }

    public boolean isBluetoothEnabled() {
        return bleScanner.isBluetoothEnabled();
    }

    // ══════════════════════════════════════════════════════
    //  扫描
    // ══════════════════════════════════════════════════════

    /** 按 deviceType 启动扫描 */
    public void startScan(String deviceType) {
        if (!bleScanner.isAvailable()) {
            errorMsg.setValue("蓝牙不可用，请确认蓝牙已开启");
            return;
        }
        scanResults.setValue(new ArrayList<>());
        isScanning.setValue(true);
        errorMsg.setValue(null);

        List<String> prefixes = prefixesFor(deviceType);
        bleScanner.startScan(prefixes, new BleScanner.DeviceScanCallback() {
            @Override
            public void onDeviceFound(String mac, String name, int rssi) {
                List<ScanResultItem> list = new ArrayList<>(scanResults.getValue());
                list.add(new ScanResultItem(mac, name, rssi));
                scanResults.setValue(list);
            }

            @Override
            public void onScanComplete() {
                isScanning.setValue(false);
            }

            @Override
            public void onError(String msg) {
                isScanning.setValue(false);
                errorMsg.setValue(msg);
            }
        });
    }

    /** 停止扫描 */
    public void stopScan() {
        bleScanner.stopScan();
        isScanning.setValue(false);
    }

    // ══════════════════════════════════════════════════════
    //  连接 / 断开
    // ══════════════════════════════════════════════════════

    /** 连接设备（创建对应类型 Adapter 并连接，同时写入持久化） */
    public void connectDevice(String deviceType, String mac, String name) {
        if (connectedMap.containsKey(mac)) {
            errorMsg.setValue("该设备已连接");
            return;
        }
        String deviceId = UUID.randomUUID().toString();
        DeviceProtocolAdapter adapter = createAdapter(deviceType, deviceId);
        AdapterStatus status = buildStatus(mac);

        ConnectedDevice device = new ConnectedDevice(deviceId, name, mac, adapter,
                AdapterStatus.State.CONNECTING);
        connectedMap.put(mac, device);

        // 写入持久化（upsert by mac）
        PairedDevice pd = findSaved(mac);
        if (pd == null) pd = new PairedDevice();
        pd.setMacAddress(mac);
        pd.setDeviceType(deviceType);
        pd.setOriginalName(name);
        if (pd.getUserAlias() == null) pd.setUserAlias(name);
        pd.setConnected(false);
        pd.setLastConnectedAt(System.currentTimeMillis());
        devicePrefs.saveDevice(pd);
        savedDevices.setValue(devicePrefs.loadAll());
        rebuildList();

        adapter.connect(getApplication(), mac, status);
    }

    /** 重新连接一个已保存但未连接的设备 */
    public void reconnectSaved(String mac) {
        PairedDevice pd = findSaved(mac);
        if (pd == null) return;
        connectDevice(pd.getDeviceType(), mac,
                pd.getOriginalName() != null ? pd.getOriginalName() : pd.getUserAlias());
    }

    /** 断开但保留配对记录（设备仍显示在列表，标记为未连接） */
    public void disconnectDevice(String mac) {
        ConnectedDevice cd = connectedMap.remove(mac);
        if (cd != null) cd.getAdapter().release(); // 触发回调 DISCONNECTED，幂等处理
        markSavedConnected(mac, false);
        rebuildList();
    }

    /** 删除已保存的设备（同时断开） */
    public void removeSavedDevice(String mac) {
        ConnectedDevice cd = connectedMap.remove(mac);
        if (cd != null) cd.getAdapter().release();
        devicePrefs.removeDevice(mac);
        savedDevices.setValue(devicePrefs.loadAll());
        rebuildList();
    }

    /** 按 deviceId 查找运行时设备（供测试面板使用） */
    public ConnectedDevice findDevice(String deviceId) {
        if (deviceId == null) return null;
        for (ConnectedDevice cd : connectedMap.values()) {
            if (cd.getDeviceId().equals(deviceId)) return cd;
        }
        return null;
    }

    /** 清除一次性错误消息 */
    public void clearError() {
        errorMsg.setValue(null);
    }

    // ══════════════════════════════════════════════════════
    //  内部逻辑
    // ══════════════════════════════════════════════════════

    private AdapterStatus buildStatus(String mac) {
        return new AdapterStatus() {
            @Override
            public void onStateChanged(State state, String devId, String detail) {
                Log.d(TAG, "device " + devId + " state=" + state + " detail=" + detail);
                // 统一回到主线程处理，避免 BLE 回调线程与列表操作竞争
                mainHandler.post(() -> handleState(mac, devId, state, detail));
            }

            @Override
            public void onCycleStats(String devId, long writeLatencyMs, boolean success) {
            }

            @Override
            public void onFatalError(String devId, String error) {
                mainHandler.post(() -> {
                    errorMsg.setValue("设备致命错误: " + error);
                    connectedMap.values().removeIf(cd -> cd.getDeviceId().equals(devId));
                    markSavedConnected(mac, false);
                    rebuildList();
                });
            }
        };
    }

    private void handleState(String mac, String devId, AdapterStatus.State state, String detail) {
        switch (state) {
            case CONNECTED:
                updateRuntimeState(devId, state);
                markSavedConnected(mac, true);
                break;
            case DISCONNECTED:
                connectedMap.values().removeIf(cd -> cd.getDeviceId().equals(devId));
                markSavedConnected(mac, false);
                break;
            case ERROR:
                updateRuntimeState(devId, state);
                markSavedConnected(mac, false);
                errorMsg.setValue(detail);
                break;
            default: // CONNECTING / RETRYING
                updateRuntimeState(devId, state);
                break;
        }
        rebuildList();
    }

    private DeviceProtocolAdapter createAdapter(String deviceType, String deviceId) {
        // 当前仅实现郊狼 V3；其它类型预留
        return new CoyoteV3Adapter(deviceId);
    }

    private List<String> prefixesFor(String deviceType) {
        if (TYPE_COYOTE_V3.equals(deviceType)) {
            return Collections.singletonList(BleScanner.COYOTE_V3_PREFIX);
        }
        // 默认按郊狼 V3 扫描（当前唯一实现）
        return Collections.singletonList(BleScanner.COYOTE_V3_PREFIX);
    }

    private void rebuildList() {
        List<DeviceItem> items = new ArrayList<>();
        for (PairedDevice pd : savedDevices.getValue()) {
            ConnectedDevice cd = connectedMap.get(pd.getMacAddress());
            String displayName = pd.getUserAlias() != null ? pd.getUserAlias() : pd.getOriginalName();
            if (cd != null) {
                items.add(new DeviceItem(pd.getMacAddress(), displayName, pd.getDeviceType(),
                        true, cd.getDeviceId(), cd.getState()));
            } else {
                items.add(new DeviceItem(pd.getMacAddress(), displayName, pd.getDeviceType(),
                        false, null, null));
            }
        }
        deviceList.setValue(items);
    }

    private void updateRuntimeState(String deviceId, AdapterStatus.State state) {
        for (ConnectedDevice cd : connectedMap.values()) {
            if (cd.getDeviceId().equals(deviceId)) {
                cd.setState(state);
                return;
            }
        }
    }

    private void markSavedConnected(String mac, boolean connected) {
        PairedDevice pd = findSaved(mac);
        if (pd == null) return;
        pd.setConnected(connected);
        if (connected) pd.setLastConnectedAt(System.currentTimeMillis());
        devicePrefs.saveDevice(pd);
        savedDevices.setValue(devicePrefs.loadAll());
    }

    private PairedDevice findSaved(String mac) {
        for (PairedDevice pd : savedDevices.getValue()) {
            if (pd.getMacAddress() != null && pd.getMacAddress().equals(mac)) return pd;
        }
        return null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        bleScanner.stopScan();
        for (ConnectedDevice cd : connectedMap.values()) {
            cd.getAdapter().release();
        }
    }
}
