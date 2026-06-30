package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hypno.hypnovibe.domain.AdapterStatus;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * DG-LAB V2 设备协议适配器。
 *
 * <p>完整封装 V2 的 BLE 通信协议，并实现 {@link DGLabController} 供测试面板使用。
 */
public class DGLabV2Adapter implements DeviceProtocolAdapter, DGLabController {

    private static final String TAG = "DGLabV2Adapter";

    private static final UUID UUID_180A = UUID.fromString(DGLabConstants.SERVICE_V2_INFO);
    private static final UUID UUID_180B = UUID.fromString(DGLabConstants.SERVICE_V2_CTRL);
    private static final UUID UUID_PWM_AB2 = UUID.fromString(DGLabConstants.CHAR_PWM_AB2);
    private static final UUID UUID_PWM_A34 = UUID.fromString(DGLabConstants.CHAR_PWM_A34);
    private static final UUID UUID_PWM_B34 = UUID.fromString(DGLabConstants.CHAR_PWM_B34);

    private static final int STRENGTH_SCALE = 7;

    private final String deviceId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context context;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic charAB2;
    private BluetoothGattCharacteristic charA34;
    private BluetoothGattCharacteristic charB34;
    private AdapterStatus statusCallback;

    private volatile int targetStrengthA = 0;
    private volatile int targetStrengthB = 0;
    private volatile int deviceStrengthA = 0;
    private volatile int deviceStrengthB = 0;

    private volatile boolean safetyOn = true;
    private volatile boolean connected = false;
    private volatile boolean released = false;

    private ScheduledExecutorService timer;
    private ScheduledFuture<?> timerFuture;

    private String lastAddress;
    private Context lastContext;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS = DGLabConstants.RETRY_DELAYS_MS;

    private DGLabController.DGLabListener dglabListener;

    public DGLabV2Adapter(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override public String getDeviceType() { return DGLabConstants.DEVICE_TYPE_V2; }
    @Override public String getDeviceId() { return deviceId; }

    @Override
    public void connect(Context ctx, String address, AdapterStatus status) {
        this.lastContext = ctx.getApplicationContext();
        this.context = lastContext;
        this.lastAddress = address;
        this.statusCallback = status;
        this.retryCount = 0;
        notifyStatus(AdapterStatus.State.CONNECTING, "开始连接: " + address);
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                notifyStatus(AdapterStatus.State.ERROR, "蓝牙不可用");
                return;
            }
            BluetoothDevice device = adapter.getRemoteDevice(address);
            gatt = device.connectGatt(context, false, gattCallback);
        } catch (SecurityException e) {
            notifyStatus(AdapterStatus.State.ERROR, "缺少蓝牙权限");
        } catch (Exception e) {
            notifyStatus(AdapterStatus.State.ERROR, "连接异常: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        retryCount = MAX_RETRIES;
        stopTimer();
        connected = false;
        safetyOn = true;
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
        charAB2 = null; charA34 = null; charB34 = null;
        notifyStatus(AdapterStatus.State.DISCONNECTED, "已断开");
        if (dglabListener != null) dglabListener.onDisconnected();
    }

    @Override public void release() { released = true; disconnect(); }

    @Override public void updateSnapshot(Map<String, byte[]> channelData, Map<String, Long> offsets) {}
    @Override public void flush() {}
    @Override public boolean validateSegmentData(byte[] data) { return false; }

    @Override
    public void setManualStrength(int a, int b) {
        targetStrengthA = clampStrength(a);
        targetStrengthB = clampStrength(b);
        writeStrength();
    }

    @Override public int getDeviceStrengthA() { return deviceStrengthA; }
    @Override public int getDeviceStrengthB() { return deviceStrengthB; }
    @Override public boolean isSafetyOn() { return safetyOn; }
    @Override public void unlockSafety() { safetyOn = false; }
    @Override public boolean isConnected() { return connected && !released; }

    @Override
    public void setDGLabListener(DGLabController.DGLabListener listener) {
        this.dglabListener = listener;
    }

    @Override
    public void emergencyStop() {
        targetStrengthA = 0; targetStrengthB = 0;
        deviceStrengthA = 0; deviceStrengthB = 0;
        safetyOn = true;
        if (connected && gatt != null && charAB2 != null) {
            try {
                byte[] cmd = DGLabV2Protocol.buildPwmAB2(0, 0);
                charAB2.setValue(cmd);
                gatt.writeCharacteristic(charAB2);
            } catch (SecurityException ignored) {}
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                retryCount = 0;
                notifyStatus(AdapterStatus.State.CONNECTED, "GATT 已连接, 发现服务中...");
                try { g.discoverServices(); } catch (SecurityException e) {
                    notifyStatus(AdapterStatus.State.ERROR, "发现服务失败");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                safetyOn = true;
                stopTimer();
                if (retryCount < MAX_RETRIES && !released) {
                    notifyStatus(AdapterStatus.State.RETRYING,
                            "连接断开, 第" + (retryCount + 1) + "次重试...");
                    scheduleRetry();
                } else {
                    notifyStatus(AdapterStatus.State.DISCONNECTED, "连接已断开");
                    if (dglabListener != null) dglabListener.onDisconnected();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int gattStatus) {
            if (gattStatus != BluetoothGatt.GATT_SUCCESS) {
                notifyStatus(AdapterStatus.State.ERROR, "服务发现失败: " + gattStatus);
                return;
            }
            BluetoothGattService ctrlService = g.getService(UUID_180B);
            if (ctrlService == null) {
                notifyStatus(AdapterStatus.State.ERROR, "未找到控制服务 0x180B");
                return;
            }
            charAB2 = ctrlService.getCharacteristic(UUID_PWM_AB2);
            charA34 = ctrlService.getCharacteristic(UUID_PWM_A34);
            charB34 = ctrlService.getCharacteristic(UUID_PWM_B34);
            if (charAB2 == null || charA34 == null || charB34 == null) {
                notifyStatus(AdapterStatus.State.ERROR, "未找到 PWM 特性");
                return;
            }
            writeStrength();
            startTimer();
            notifyStatus(AdapterStatus.State.CONNECTED, "已连接: " + lastAddress);
            if (dglabListener != null) dglabListener.onConnected();
        }
    };

    private void writeStrength() {
        if (gatt == null || charAB2 == null) return;
        int sa = Math.min(targetStrengthA * STRENGTH_SCALE, DGLabConstants.STRENGTH_V2_MAX);
        int sb = Math.min(targetStrengthB * STRENGTH_SCALE, DGLabConstants.STRENGTH_V2_MAX);
        try {
            byte[] cmd = DGLabV2Protocol.buildPwmAB2(sa, sb);
            charAB2.setValue(cmd);
            gatt.writeCharacteristic(charAB2);
            deviceStrengthA = targetStrengthA;
            deviceStrengthB = targetStrengthB;
        } catch (SecurityException ignored) {}
    }

    private void startTimer() {
        stopTimer();
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dglab_v2_timer");
            t.setDaemon(true);
            return t;
        });
        timerFuture = timer.scheduleAtFixedRate(
                this::onTimerTick, 0, DGLabConstants.OUTPUT_WINDOW_MS, TimeUnit.MILLISECONDS);
    }

    private void stopTimer() {
        if (timerFuture != null) { timerFuture.cancel(false); timerFuture = null; }
        if (timer != null) { timer.shutdown(); timer = null; }
    }

    private void onTimerTick() {
        if (!connected || gatt == null) return;
        byte[] cmdA34 = DGLabV2Protocol.buildPwmA34(1, 99, 0);
        byte[] cmdB34 = DGLabV2Protocol.buildPwmB34(1, 99, 0);
        try {
            if (charA34 != null) { charA34.setValue(cmdA34); gatt.writeCharacteristic(charA34); }
            if (charB34 != null) { charB34.setValue(cmdB34); gatt.writeCharacteristic(charB34); }
            if (statusCallback != null) statusCallback.onCycleStats(deviceId, 0, true);
        } catch (SecurityException ignored) {}
    }

    private void scheduleRetry() {
        if (retryCount >= MAX_RETRIES) {
            if (statusCallback != null)
                statusCallback.onFatalError(deviceId, "重连失败，已重试" + MAX_RETRIES + "次");
            return;
        }
        long delay = RETRY_DELAYS[retryCount];
        retryCount++;
        mainHandler.postDelayed(() -> {
            if (lastAddress != null && lastContext != null && !connected && !released)
                connect(lastContext, lastAddress, statusCallback);
        }, delay);
    }

    private void notifyStatus(AdapterStatus.State state, String detail) {
        mainHandler.post(() -> {
            if (statusCallback != null) statusCallback.onStateChanged(state, deviceId, detail);
        });
    }

    private static int clampStrength(int v) {
        if (v < 0) return 0;
        if (v > 200) return 200;
        return v;
    }
}
