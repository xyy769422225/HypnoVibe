package com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.example.nirjon.bledemo4_advertising.util.BLEUtil;
import com.hypno.hypnovibe.domain.AdapterStatus;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;

import java.util.Map;
import java.util.UUID;

/**
 * Love Spouse 2.4G 设备适配器。
 * <p>
 * 通过 libble.so JNI 编码 BLE 广播数据，控制 MuSe/Love Spouse 兼容玩具。
 * 基于官方 Love Spouse APK v4.0.0 逆向实现，无需 GATT 配对。
 * <p>
 * 广播机制：每次命令发送后约 1 秒自动停止，因此内部定时器每 900ms 重发
 * 当前等级的广播包以维持持续振动。
 */
public class LoveSpouseAdapter implements DeviceProtocolAdapter {

    private static final String TAG = "LoveSpouseAdapter";

    /** 广播标识 UUID（官方 APK 从资源加载，此处用固定值） */
    private static final UUID ADVERTISE_SERVICE_UUID =
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");

    // ===== 标识 =====
    private final String deviceId;

    // ===== BLE 广播 =====
    private Context context;
    private AdapterStatus statusCallback;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback currentCallback;
    private volatile boolean advertising = false;
    private volatile boolean released = false;

    // ===== 强度状态 =====
    private volatile int currentLevel = -1;  // -1 = 未初始化
    private volatile int targetLevel = 0;

    // ===== 定时器 =====
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable keepAliveRunnable;
    private long lastBroadcastTime = 0;

    public LoveSpouseAdapter(String deviceId) {
        this.deviceId = deviceId;
    }

    // ══════════════════════════════════════════════════════
    //  DeviceProtocolAdapter 实现
    // ══════════════════════════════════════════════════════

    @Override
    public String getDeviceType() {
        return LoveSpouseConstants.DEVICE_TYPE;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void connect(Context ctx, String address, AdapterStatus status) {
        this.context = ctx.getApplicationContext();
        this.statusCallback = status;
        notifyState(AdapterStatus.State.CONNECTING, "正在开启广播...");

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            notifyState(AdapterStatus.State.ERROR, "蓝牙不可用或未开启");
            return;
        }
        this.advertiser = adapter.getBluetoothLeAdvertiser();
        if (this.advertiser == null) {
            notifyState(AdapterStatus.State.ERROR, "本机不支持 BLE 广播");
            return;
        }

        // 初始发送停止命令（确保安全）
        sendBroadcast(0);
        currentLevel = 0;
        targetLevel = 0;

        // 启动持续广播保活定时器
        startKeepAlive();

        notifyState(AdapterStatus.State.CONNECTED, "广播已开启");
    }

    @SuppressLint("MissingPermission")
    @Override
    public void disconnect() {
        released = true;
        stopKeepAlive();
        stopAdvertising();
        sendBroadcast(0); // 最后发送一次停止
        notifyState(AdapterStatus.State.DISCONNECTED, "广播已停止");
    }

    @Override
    public void release() {
        released = true;
        stopKeepAlive();
        stopAdvertising();
        advertiser = null;
    }

    @Override
    public void updateSnapshot(Map<String, byte[]> channelData,
                               Map<String, Long> offsetsInSegment) {
        // Phase 5 填充
    }

    @Override
    public void flush() {
        // Phase 5 填充
    }

    @SuppressLint("MissingPermission")
    @Override
    public void emergencyStop() {
        targetLevel = 0;
        sendBroadcast(0);
        currentLevel = 0;
    }

    @Override
    public boolean validateSegmentData(byte[] protobufBytes) {
        return false;
    }

    // ══════════════════════════════════════════════════════
    //  测试面板手动控制 API
    // ══════════════════════════════════════════════════════

    /** 设置振动等级（0-9） */
    public void setManualStrength(int level) {
        targetLevel = clamp(level, LoveSpouseConstants.STRENGTH_MIN, LoveSpouseConstants.STRENGTH_MAX);
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public boolean isAdvertising() {
        return advertising && !released;
    }

    // ══════════════════════════════════════════════════════
    //  内部：BLE 广播发送
    // ══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private void sendBroadcast(int level) {
        if (advertiser == null || released) return;

        // 速率限制：同一等级 100ms 内不重发
        long now = System.currentTimeMillis();
        if (level == currentLevel && now - lastBroadcastTime < LoveSpouseConstants.BROADCAST_INTERVAL_MS) {
            return;
        }

        // 停止旧广播
        stopAdvertising();

        // JNI 编码
        byte[] payload = buildRfPayload(level);

        // 构建广播数据
        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(ADVERTISE_SERVICE_UUID))
                .addManufacturerData(LoveSpouseConstants.MANUFACTURER_ID, payload)
                .build();

        // 构建广播设置
        int mode = (level == 0) ? LoveSpouseConstants.ADVERTISE_MODE_STOP
                                : LoveSpouseConstants.ADVERTISE_MODE_POWER;
        int advertiseMode = (mode == 1) ? AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                                        : AdvertiseSettings.ADVERTISE_MODE_BALANCED;
        int timeout = (mode == 1) ? 2000 : 3000;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(advertiseMode)
                .setConnectable(true)
                .setTimeout(timeout)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        currentCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                advertising = true;
            }

            @Override
            public void onStartFailure(int errorCode) {
                advertising = false;
                Log.e(TAG, "advertising failed: " + errorCode);
                if (statusCallback != null) {
                    statusCallback.onCycleStats(deviceId, 0, false);
                }
            }
        };

        try {
            advertiser.startAdvertising(settings, data, currentCallback);
            advertising = true;
            currentLevel = level;
            lastBroadcastTime = now;
        } catch (Exception e) {
            Log.e(TAG, "startAdvertising exception", e);
            advertising = false;
        }
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        advertising = false;
        if (advertiser != null && currentCallback != null) {
            try {
                advertiser.stopAdvertising(currentCallback);
            } catch (Exception ignored) {}
        }
        currentCallback = null;
    }

    // ══════════════════════════════════════════════════════
    //  内部：持续广播保活
    // ══════════════════════════════════════════════════════

    /**
     * 启动保活定时器。因为 BLE 广播约 1 秒后自动停止，需要每 900ms 重发
     * 当前目标等级的广播包以维持持续振动。
     */
    private void startKeepAlive() {
        stopKeepAlive();
        keepAliveRunnable = new Runnable() {
            @Override
            public void run() {
                if (released) return;
                sendBroadcast(targetLevel);
                mainHandler.postDelayed(this, LoveSpouseConstants.ADVERTISE_STOP_DELAY_MS - 100);
            }
        };
        mainHandler.post(keepAliveRunnable);
    }

    private void stopKeepAlive() {
        if (keepAliveRunnable != null) {
            mainHandler.removeCallbacks(keepAliveRunnable);
            keepAliveRunnable = null;
        }
    }

    // ══════════════════════════════════════════════════════
    //  内部：JNI 编码
    // ══════════════════════════════════════════════════════

    /**
     * 通过 libble.so 的 get_rf_payload() 编码广播数据。
     * <p>
     * 数据格式：[5字节前缀 wbMSE] + [1字节命令] + [5字节 CRC/校验] = 11 字节。
     *
     * @param level 振动等级 0-9
     * @return 编码后的广播 payload
     */
    static byte[] buildRfPayload(int level) {
        int idx = clamp(level, 0, LoveSpouseConstants.STRENGTH_COMMANDS.length - 1);
        String hexStr = LoveSpouseConstants.STRENGTH_COMMANDS[idx];

        // 将 hex 字符串转换为单字节
        byte cmdByte = hexToByte(hexStr);
        byte[] cmd = new byte[] { cmdByte };

        int outputLen = LoveSpouseConstants.PREFIX.length + 1 + LoveSpouseConstants.RF_PAYLOAD_OVERHEAD;
        byte[] output = new byte[outputLen];

        BLEUtil.get_rf_payload(
            LoveSpouseConstants.PREFIX, LoveSpouseConstants.PREFIX.length,
            cmd, 1,
            output
        );

        return output;
    }

    /** hex 字符串 "11" → byte 0x11 */
    private static byte hexToByte(String hex) {
        if (hex.length() == 1) {
            hex = "0" + hex;
        }
        return (byte) Integer.parseInt(hex, 16);
    }

    // ══════════════════════════════════════════════════════
    //  内部：工具方法
    // ══════════════════════════════════════════════════════

    private void notifyState(AdapterStatus.State state, String detail) {
        if (statusCallback != null) {
            statusCallback.onStateChanged(state, deviceId, detail);
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
