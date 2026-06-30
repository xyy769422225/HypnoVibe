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
import com.hypno.hypnovibe.infrastructure.ble.adapter.dglab.DGLabV2Adapter;
import com.hypno.hypnovibe.infrastructure.ble.adapter.dglab.DGLabV3Adapter;
import com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse.LoveSpouseAdapter;
import com.hypno.hypnovibe.infrastructure.persistence.DevicePrefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 已实现的设备类型 */
    public static final String TYPE_DGLAB_V3 = "dglab_v3";
    /** 广播型设备：Love Spouse（无需扫描配对，直接开启 BLE 广播控制） */
    public static final String TYPE_LOVE_SPOUSE = "love_spouse";

    /** 虚拟设备（广播型）的 MAC 占位前缀 */
    private static final String VIRTUAL_MAC_PREFIX = "virtual:";

    /** Love Spouse 虚拟设备的 MAC 标识（供 disconnect 等操作） */
    public static final String VIRTUAL_MAC_PREFIX_FOR_LOVE_SPOUSE = VIRTUAL_MAC_PREFIX + TYPE_LOVE_SPOUSE;

    private final BleScanner bleScanner;
    private final DevicePrefs devicePrefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 运行时已连接设备：mac -> ConnectedDevice */
    private final ConcurrentMap<String, ConnectedDevice> connectedMap = new ConcurrentHashMap<>();

    /**
     * 可用虚拟设备类型注册表：deviceType → displayName。
     * 虚拟设备无需扫描配对，用户一键开启。
     * 插入顺序决定 UI 展示顺序。
     */
    private final Map<String, String> virtualDeviceTypes = new LinkedHashMap<>();

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
        /** 是否为虚拟设备（广播型，无需扫描配对） */
        public final boolean isVirtual;

        public DeviceItem(String mac, String name, String deviceType,
                          boolean connected, String deviceId, AdapterStatus.State state,
                          boolean isVirtual) {
            this.mac = mac; this.name = name; this.deviceType = deviceType;
            this.connected = connected; this.deviceId = deviceId; this.state = state;
            this.isVirtual = isVirtual;
        }
    }

    public DeviceManagerVM(Application app) {
        super(app);
        bleScanner = new BleScanner();
        devicePrefs = new DevicePrefs(app);
        savedDevices = StateFlowKt.MutableStateFlow(devicePrefs.loadAll());

        // 注册可用虚拟设备类型
        virtualDeviceTypes.put(TYPE_LOVE_SPOUSE, "Love Spouse 震动玩具");

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
        DeviceProtocolAdapter adapter = createAdapter(deviceType, name, deviceId);
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

    // ══════════════════════════════════════════════════════
    //  虚拟/广播设备（无需扫描配对）
    // ══════════════════════════════════════════════════════

    /** 开启虚拟设备广播 */
    public void addBroadcastDevice(String deviceType) {
        String mac = VIRTUAL_MAC_PREFIX + deviceType;

        // 每种广播设备只允许一个实例
        if (connectedMap.containsKey(mac)) {
            errorMsg.setValue("广播已开启，无需重复添加");
            return;
        }

        String name = virtualDeviceTypes.get(deviceType);
        if (name == null) {
            errorMsg.setValue("不支持的设备类型: " + deviceType);
            return;
        }

        String deviceId = UUID.randomUUID().toString();
        DeviceProtocolAdapter adapter = createAdapter(deviceType, name, deviceId);
        AdapterStatus status = buildStatus(mac);

        ConnectedDevice device = new ConnectedDevice(deviceId, name, mac, adapter,
                AdapterStatus.State.CONNECTING);
        connectedMap.put(mac, device);
        rebuildList();

        // address 对广播型设备无意义，传特殊值
        adapter.connect(getApplication(), mac, status);
    }

    /** 获取已注册的虚拟设备类型列表（供 UI 动态生成） */
    public Map<String, String> getVirtualDeviceTypes() {
        return Collections.unmodifiableMap(virtualDeviceTypes);
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

    private DeviceProtocolAdapter createAdapter(String deviceType, String name, String deviceId) {
        if (TYPE_LOVE_SPOUSE.equals(deviceType)) {
            return new LoveSpouseAdapter(deviceId);
        }
        // 郊狼：根据广播名自动识别 V2/V3
        if (BleScanner.COYOTE_V2_NAME.equals(name)) {
            return new DGLabV2Adapter(deviceId);
        }
        // 默认 DG-LAB V3
        return new DGLabV3Adapter(deviceId);
    }

    private List<String> prefixesFor(String deviceType) {
        if (TYPE_DGLAB_V3.equals(deviceType)) {
            // 郊狼类型同时扫描 V2("D-LAB ESTIM01") 和 V3("47L121") 前缀
            return BleScanner.COYOTE_ALL_PREFIXES;
        }
        // 默认按郊狼全系列扫描
        return BleScanner.COYOTE_ALL_PREFIXES;
    }

    private void rebuildList() {
        List<DeviceItem> items = new ArrayList<>();

        // 1. 已配对的实体设备（来自 DevicePrefs）
        for (PairedDevice pd : savedDevices.getValue()) {
            ConnectedDevice cd = connectedMap.get(pd.getMacAddress());
            String displayName = pd.getUserAlias() != null ? pd.getUserAlias() : pd.getOriginalName();
            if (cd != null) {
                items.add(new DeviceItem(pd.getMacAddress(), displayName, pd.getDeviceType(),
                        true, cd.getDeviceId(), cd.getState(), false));
            } else {
                items.add(new DeviceItem(pd.getMacAddress(), displayName, pd.getDeviceType(),
                        false, null, null, false));
            }
        }

        // 2. 虚拟设备（广播型，无需配对，按注册顺序展示）
        for (Map.Entry<String, String> entry : virtualDeviceTypes.entrySet()) {
            String deviceType = entry.getKey();
            String displayName = entry.getValue();
            String vMac = VIRTUAL_MAC_PREFIX + deviceType;
            ConnectedDevice cd = connectedMap.get(vMac);
            if (cd != null) {
                items.add(new DeviceItem(vMac, displayName, deviceType,
                        true, cd.getDeviceId(), cd.getState(), true));
            } else {
                items.add(new DeviceItem(vMac, displayName, deviceType,
                        false, null, null, true));
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
