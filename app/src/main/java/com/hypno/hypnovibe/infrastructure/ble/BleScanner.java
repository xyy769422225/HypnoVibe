package com.hypno.hypnovibe.infrastructure.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BLE 扫描器。仅负责扫描和按设备名过滤，不负责连接。
 *
 * 使用 BluetoothLeScanner（现代 API），不使用 startLeScan（弃用 API 在新 Android 上可能被静默拒绝）。
 */
public class BleScanner {

    private static final String TAG = "BleScanner";

    // ===== 扫描调试开关 =====

    /** 调试模式：true=上报所有设备（不限名称），用于排查扫描问题 */
    private static final boolean DEBUG_ALL_DEVICES = false;

    // ===== 设备名常量 =====

    public static final String COYOTE_V3_NAME = "47L121000";
    public static final String COYOTE_V2_NAME = "D-LAB ESTIM01";
    public static final List<String> COYOTE_VALID_NAMES =
            Arrays.asList(COYOTE_V3_NAME, COYOTE_V2_NAME);
    public static final String DFU_PREFIX = "47L121000_O";
    public static final String DFU_TAG = "DfuTarg";

    private static final long SCAN_TIMEOUT_MS = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private BluetoothLeScanner activeScanner;
    private ScanWrapper callbackWrapper;
    private volatile boolean scanning = false;

    /** 上次扫描启动时间，用于诊断 */
    private long scanStartTime = 0;
    /** 收到的回调总数，用于诊断 */
    private int callbackCount = 0;

    private final Map<String, Integer> foundDevices = new HashMap<>();

    public interface DeviceScanCallback {
        void onDeviceFound(String mac, String name, int rssi);
        void onScanComplete();
        void onError(String msg);
    }

    public BleScanner() {}

    /** 注入 ApplicationContext 用于检测定位服务状态 */
    public void setAppContext(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    /** 检测定位服务（GPS 开关）是否开启 */
    private boolean isLocationServiceEnabled() {
        if (appContext == null) return true; // 无法检测时不阻断
        LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return true;
        boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps || network;
    }

    private BluetoothAdapter getAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    private BluetoothLeScanner getLeScanner() {
        BluetoothAdapter adapter = getAdapter();
        if (adapter == null || !adapter.isEnabled()) return null;
        return adapter.getBluetoothLeScanner();
    }

    public boolean isAvailable() {
        return getLeScanner() != null;
    }

    public boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    public boolean isScanning() {
        return scanning;
    }

    @SuppressLint("MissingPermission")
    public void startScan(DeviceScanCallback callback) {
        startScan(COYOTE_VALID_NAMES, callback);
    }

    @SuppressLint("MissingPermission")
    public void startScan(List<String> validNames, DeviceScanCallback callback) {
        if (scanning) stopScan();

        // === 诊断日志：蓝牙状态 ===
        BluetoothAdapter adapter = getAdapter();
        Log.w(TAG, "═══════════════════════════════════════");
        Log.w(TAG, "scan request received");
        Log.w(TAG, "  SDK_INT = " + Build.VERSION.SDK_INT);
        Log.w(TAG, "  BluetoothAdapter = " + (adapter != null));
        Log.w(TAG, "  isEnabled = " + (adapter != null && adapter.isEnabled()));
        Log.w(TAG, "  isDiscovering = " + (adapter != null && adapter.isDiscovering()));
        Log.w(TAG, "  LocationService ON = " + isLocationServiceEnabled());

        BluetoothLeScanner scanner = getLeScanner();
        Log.w(TAG, "  BluetoothLeScanner = " + (scanner != null));

        if (scanner == null) {
            String msg = adapter == null
                    ? "本机不支持蓝牙"
                    : "蓝牙未开启，请先打开蓝牙";
            Log.e(TAG, "scan aborted: " + msg);
            callback.onError(msg);
            return;
        }

        // vivo/OPPO/小米等 ROM 要求定位服务物理开启才能 BLE 扫描
        if (!isLocationServiceEnabled()) {
            Log.e(TAG, "!!! Location service is OFF — BLE scan will silently return 0 results on this ROM");
            callback.onError("定位服务未开启。请在手机设置中打开定位（GPS）开关后再试。\n部分手机（vivo/OPPO/小米）要求定位服务开启才能扫描蓝牙设备。");
            return;
        }

        foundDevices.clear();
        activeScanner = scanner;
        callbackWrapper = new ScanWrapper(validNames, callback);
        scanning = true;
        scanStartTime = System.currentTimeMillis();
        callbackCount = 0;

        // 尝试方案1: 最简化的两参数 startScan（不传 filter、不传 settings）
        Log.w(TAG, "calling startScan(callback) — simplest 2-param version...");
        try {
            scanner.startScan(callbackWrapper);
            Log.w(TAG, "startScan(callback) returned OK. Waiting for callbacks...");
        } catch (SecurityException e) {
            Log.e(TAG, "startScan() threw SecurityException: " + e.getMessage(), e);
            scanning = false;
            activeScanner = null;
            callbackWrapper = null;
            callback.onError("缺少蓝牙扫描权限: " + e.getMessage());
            return;
        }

        mainHandler.postDelayed(() -> {
            long elapsed = System.currentTimeMillis() - scanStartTime;
            Log.w(TAG, "scan timeout after " + elapsed + "ms, callbacks received: " + callbackCount);
            stopScanInternal();
        }, SCAN_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        Log.d(TAG, "stopScan called from outside");
        stopScanInternal();
    }

    private void stopScanInternal() {
        if (!scanning) return;
        long elapsed = System.currentTimeMillis() - scanStartTime;
        Log.w(TAG, "stopScanInternal, elapsed=" + elapsed + "ms, callbacks=" + callbackCount);

        scanning = false;
        mainHandler.removeCallbacksAndMessages(null);

        if (activeScanner != null && callbackWrapper != null) {
            try {
                activeScanner.stopScan(callbackWrapper);
                Log.d(TAG, "stopScan(ScanCallback) OK");
            } catch (Exception e) {
                Log.e(TAG, "stopScan failed: " + e.getMessage());
            }
        }
        activeScanner = null;
        if (callbackWrapper != null) {
            callbackWrapper.callback.onScanComplete();
            callbackWrapper = null;
        }
    }

    // ══════════════════════════════════════════════════════
    //  ScanCallback 包装
    // ══════════════════════════════════════════════════════

    private class ScanWrapper extends android.bluetooth.le.ScanCallback {
        final DeviceScanCallback callback;
        final List<String> validNames;

        ScanWrapper(List<String> validNames, DeviceScanCallback callback) {
            this.validNames = validNames;
            this.callback = callback;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            callbackCount++;
            handleResult(callbackType, result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (results == null) return;
            callbackCount += results.size();
            for (ScanResult r : results) handleResult(-1, r);
        }

        private void handleResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            String mac = device.getAddress();

            String name = device.getName();
            if (name == null && result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }

            // 调试模式：上报所有设备
            if (DEBUG_ALL_DEVICES) {
                String displayName = (name != null && !name.isEmpty()) ? name : "(no name)";
                Integer prev = foundDevices.get(mac);
                if (prev == null) {
                    foundDevices.put(mac, rssi);
                    Log.d(TAG, "[DEBUG] #" + callbackCount + ": " + displayName
                            + " | " + mac + " | RSSI=" + rssi + " | cbType=" + callbackType);
                    callback.onDeviceFound(mac, displayName, rssi);
                }
                return;
            }

            if (name == null || name.isEmpty()) return;

            if (isDfuDevice(name)) return;

            if (validNames != null && !validNames.isEmpty()
                    && !validNames.contains(name)) return;

            Integer prev = foundDevices.get(mac);
            if (prev == null) {
                foundDevices.put(mac, rssi);
                callback.onDeviceFound(mac, name, rssi);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            // ★ 关键诊断：扫描失败时的错误码
            String reason;
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    reason = "ALREADY_STARTED"; break;
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    reason = "APP_REGISTRATION_FAILED"; break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    reason = "INTERNAL_ERROR"; break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    reason = "FEATURE_UNSUPPORTED"; break;
                case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                    reason = "OUT_OF_HARDWARE_RESOURCES"; break;
                case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                    reason = "SCANNING_TOO_FREQUENTLY"; break;
                default:
                    reason = "UNKNOWN(" + errorCode + ")"; break;
            }
            Log.e(TAG, "!!! onScanFailed: errorCode=" + errorCode + " (" + reason + ")");

            long elapsed = System.currentTimeMillis() - scanStartTime;
            Log.e(TAG, "!!! scan failed after " + elapsed + "ms, callbacks before fail: " + callbackCount);

            scanning = false;
            activeScanner = null;
            callback.onError("扫描失败: " + reason + " (错误码=" + errorCode + ")");
        }
    }

    static boolean isDfuDevice(String name) {
        return DFU_TAG.equals(name)
                || name.startsWith(DFU_PREFIX)
                || name.contains(DFU_PREFIX);
    }
}
