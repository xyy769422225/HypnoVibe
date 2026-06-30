package com.hypno.hypnovibe.infrastructure.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BLE 扫描器。仅负责扫描和按设备名前缀过滤，不负责连接。
 * 连接由各设备的 Adapter.connect() 内部完成。
 *
 * 说明：{@link BluetoothLeScanner} 实例不缓存。蓝牙开关状态会使其失效
 *（关闭后变 null，重开后系统会返回新实例），因此在每次 startScan 时实时获取，
 * 避免蓝牙重开之后仍持有旧引用导致 "setting is null" 或误报不可用。
 */
public class BleScanner {

    private static final String TAG = "BleScanner";

    /** 郊狼 V3 广播名前缀：47L121（含型号码 47L121000 及序列号） */
    public static final String COYOTE_V3_PREFIX = "47L121";
    /** 郊狼 V2 广播名：D-LAB ESTIM01（精确匹配） */
    public static final String COYOTE_V2_NAME = "D-LAB ESTIM01";
    /** 郊狼全系列扫描前缀列表 */
    public static final List<String> COYOTE_ALL_PREFIXES =
            java.util.Arrays.asList(COYOTE_V3_PREFIX, COYOTE_V2_NAME);

    /** 扫描超时（毫秒） */
    private static final long SCAN_TIMEOUT_MS = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 当前扫描使用的 scanner 引用（仅扫描期间有效，用于 stopScan） */
    private BluetoothLeScanner activeScanner;
    private ScanWrapper callbackWrapper;
    private volatile boolean scanning = false;

    /** 扫描结果去重：mac → rssi */
    private final Map<String, Integer> foundDevices = new HashMap<>();

    /** 上层扫描回调接口（不与 android.bluetooth.le.ScanCallback 重名） */
    public interface DeviceScanCallback {
        void onDeviceFound(String mac, String name, int rssi);
        void onScanComplete();
        void onError(String msg);
    }

    public BleScanner() {
    }

    /** 实时获取 BluetoothAdapter（系统单例） */
    private BluetoothAdapter getAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    /** 实时获取 BluetoothLeScanner，蓝牙未开启或不可用时返回 null */
    private BluetoothLeScanner getLeScanner() {
        BluetoothAdapter adapter = getAdapter();
        if (adapter == null || !adapter.isEnabled()) return null;
        return adapter.getBluetoothLeScanner();
    }

    /** 蓝牙当前是否可用（适配器存在且已开启） */
    public boolean isAvailable() {
        return getLeScanner() != null;
    }

    /** 蓝牙适配器是否已开启 */
    public boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    public boolean isScanning() {
        return scanning;
    }

    /** 开始扫描，过滤郊狼 V3 设备 */
    public void startScan(DeviceScanCallback callback) {
        startScan(Collections.singletonList(COYOTE_V3_PREFIX), callback);
    }

    /**
     * 开始扫描，按给定设备名前缀列表过滤。
     * 任一前缀命中即上报。前缀列表为空表示不过滤（上报全部设备）。
     */
    @SuppressLint("MissingPermission")
    public void startScan(List<String> namePrefixes, DeviceScanCallback callback) {
        if (scanning) stopScan();

        BluetoothLeScanner scanner = getLeScanner();
        if (scanner == null) {
            BluetoothAdapter adapter = getAdapter();
            String msg = adapter == null
                    ? "本机不支持蓝牙"
                    : "蓝牙未开启，请先打开蓝牙";
            callback.onError(msg);
            return;
        }

        foundDevices.clear();
        activeScanner = scanner;
        callbackWrapper = new ScanWrapper(namePrefixes, callback);
        scanning = true;

        // ScanSettings 不能为 null，部分 ROM（如 vivo）会抛 "setting is null"
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            scanner.startScan(null, settings, callbackWrapper);
        } catch (Exception e) {
            Log.e(TAG, "startScan failed", e);
            scanning = false;
            activeScanner = null;
            callbackWrapper = null;
            callback.onError("扫描启动失败: " + e.getMessage());
            return;
        }

        // 扫描超时自动停止
        mainHandler.postDelayed(this::stopScanInternal, SCAN_TIMEOUT_MS);
    }

    /** 停止扫描 */
    @SuppressLint("MissingPermission")
    public void stopScan() {
        stopScanInternal();
    }

    private void stopScanInternal() {
        if (!scanning) return;
        scanning = false;
        mainHandler.removeCallbacksAndMessages(null);

        if (activeScanner != null && callbackWrapper != null) {
            try {
                activeScanner.stopScan(callbackWrapper);
            } catch (Exception ignored) {}
        }
        activeScanner = null;
        if (callbackWrapper != null) {
            callbackWrapper.callback.onScanComplete();
            callbackWrapper = null;
        }
    }

    /** 包装系统的 android.bluetooth.le.ScanCallback，按设备名前缀过滤并去重 */
    private class ScanWrapper extends android.bluetooth.le.ScanCallback {
        final DeviceScanCallback callback;
        final List<String> namePrefixes;

        ScanWrapper(List<String> namePrefixes, DeviceScanCallback callback) {
            this.namePrefixes = namePrefixes;
            this.callback = callback;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (results == null) return;
            for (ScanResult r : results) {
                handleResult(r);
            }
        }

        private void handleResult(ScanResult result) {
            // 优先从广播记录取名（更可靠），回退到设备对象
            String name = null;
            if (result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }
            if (name == null && result.getDevice().getName() != null) {
                name = result.getDevice().getName();
            }
            if (name == null) return;

            // 前缀过滤：无前缀列表则不过滤
            if (namePrefixes != null && !namePrefixes.isEmpty()) {
                boolean match = false;
                for (String p : namePrefixes) {
                    if (name.startsWith(p)) { match = true; break; }
                }
                if (!match) return;
            }

            String mac = result.getDevice().getAddress();
            int rssi = result.getRssi();

            // 去重：同一 mac 只上报一次
            Integer prev = foundDevices.get(mac);
            if (prev == null) {
                foundDevices.put(mac, rssi);
                callback.onDeviceFound(mac, name, rssi);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            activeScanner = null;
            String reason = describeErrorCode(errorCode);
            callback.onError("扫描失败: " + reason);
        }
    }

    /** 将 Android 原生错误码翻译为可读原因 */
    private static String describeErrorCode(int errorCode) {
        switch (errorCode) {
            case android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "扫描已在进行中";
            case android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "蓝牙内部错误，请重试或重启蓝牙";
            case android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "本机不支持 BLE 扫描";
            case android.bluetooth.le.ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                return "硬件资源不足，请稍后重试";
            case android.bluetooth.le.ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                return "扫描过于频繁，请稍后重试";
            default:
                return "未知错误码 " + errorCode;
        }
    }
}
