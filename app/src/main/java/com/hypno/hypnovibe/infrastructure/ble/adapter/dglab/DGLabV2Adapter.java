package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

import android.annotation.SuppressLint;
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

    /** 强度缩放系数：官方 packageStrengthBytesOld 中为 strA * 10 */
    private static final int STRENGTH_SCALE = 10;

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
    private Runnable connectTimeoutTask;

    private DGLabController.DGLabListener dglabListener;

    // === 波形播放模式 ===
    private volatile boolean waveModeA = false;
    private volatile int waveFreqA = 10;
    private volatile int waveStrengthA = 0;
    private volatile boolean waveModeB = false;
    private volatile int waveFreqB = 10;
    private volatile int waveStrengthB = 0;

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
        notifyStatus(AdapterStatus.State.CONNECTING, "开始连接: " + address);
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                notifyStatus(AdapterStatus.State.ERROR, "蓝牙不可用");
                return;
            }
            BluetoothDevice device = adapter.getRemoteDevice(address);
            // 连接超时看门狗：对齐官方 timeout(15000)
            cancelConnectTimeout();
            connectTimeoutTask = () -> {
                if (!connected && gatt != null) {
                    Log.w(TAG, "connection timeout");
                    try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {}
                    gatt = null;
                    notifyStatus(AdapterStatus.State.ERROR, "连接超时");
                    if (dglabListener != null) dglabListener.onDisconnected();
                }
            };
            mainHandler.postDelayed(connectTimeoutTask, 15000);
            gatt = device.connectGatt(context, false, gattCallback);
        } catch (SecurityException e) {
            notifyStatus(AdapterStatus.State.ERROR, "缺少蓝牙权限");
        } catch (Exception e) {
            notifyStatus(AdapterStatus.State.ERROR, "连接异常: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        cancelConnectTimeout();
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
    public void sendChannelWaveFrame(int channel, int frequency, int strength) {
        // V2 波形: 频率 → X/Y, 强度 → Z
        int freq = Math.max(10, Math.min(frequency, 240));
        int str = Math.round((float) Math.max(0, Math.min(strength, 200)) * 7f / 200f);
        if (channel == 0) {
            waveModeA = true;
            waveFreqA = freq;
            waveStrengthA = Math.min(str, 31);
        } else {
            waveModeB = true;
            waveFreqB = freq;
            waveStrengthB = Math.min(str, 31);
        }
    }

    @Override
    public void sendSilentFrame() {
        waveModeA = false;
        waveModeB = false;
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
                cancelConnectTimeout();
                connected = true;
                notifyStatus(AdapterStatus.State.CONNECTED, "GATT 已连接, 发现服务中...");
                // 对齐官方：无 requestMtu，直接 discoverServices
                try { g.discoverServices(); } catch (SecurityException e) {
                    notifyStatus(AdapterStatus.State.ERROR, "发现服务失败");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cancelConnectTimeout();
                connected = false;
                safetyOn = true;
                stopTimer();
                // 对齐官方：retry(0)，不重试，直接断开
                notifyStatus(AdapterStatus.State.DISCONNECTED, "连接已断开");
                if (dglabListener != null) dglabListener.onDisconnected();
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

        // 波形模式：频率 → X/Y, 强度 → Z, 直接写入波形特性
        if (waveModeA || waveModeB) {
            int x = 1, y = 99, z = 0;
            if (waveModeA) {
                y = waveFreqA - 1;
                z = waveStrengthA;
            }
            int xb = 1, yb = 99, zb = 0;
            if (waveModeB) {
                yb = waveFreqB - 1;
                zb = waveStrengthB;
            }
            try {
                if (charA34 != null) { charA34.setValue(DGLabV2Protocol.buildPwmA34(xb, yb > 0 ? yb : 1, zb)); gatt.writeCharacteristic(charA34); }
                if (charB34 != null) { charB34.setValue(DGLabV2Protocol.buildPwmB34(x, y > 0 ? y : 1, z)); gatt.writeCharacteristic(charB34); }
            } catch (SecurityException ignored) {}
            return;
        }

        byte[] cmdA34 = DGLabV2Protocol.buildPwmA34(1, 99, 0);
        byte[] cmdB34 = DGLabV2Protocol.buildPwmB34(1, 99, 0);
        try {
            if (charA34 != null) { charA34.setValue(cmdA34); gatt.writeCharacteristic(charA34); }
            if (charB34 != null) { charB34.setValue(cmdB34); gatt.writeCharacteristic(charB34); }
            if (statusCallback != null) statusCallback.onCycleStats(deviceId, 0, true);
        } catch (SecurityException ignored) {}
    }

    private void cancelConnectTimeout() {
        if (connectTimeoutTask != null) {
            mainHandler.removeCallbacks(connectTimeoutTask);
            connectTimeoutTask = null;
        }
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
