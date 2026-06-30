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

import com.hypno.hypnovibe.domain.AdapterStatus;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;

import java.util.Map;
import java.util.UUID;

/**
 * Love Spouse 设备适配器。
 * <p>
 * 通过 BLE Advertising 的 Manufacturer Data 控制 MuSe/Love Spouse 兼容玩具。
 * 与郊狼不同：不需要 GATT 连接，纯广播控制；玩具被动监听，一对一或一对多。
 * <p>
 * 协议来源：LS-Buttplug + LoveSpouse-Vibration-Controller 两个开源项目。
 */
public class LoveSpouseAdapter implements DeviceProtocolAdapter {

    private static final String TAG = "LoveSpouseAdapter";

    // 任意 UUID 用于广播标识（Android BLE 广播要求至少含 Service UUID 或 Manufacturer Data）
    private static final UUID ADVERTISE_SERVICE_UUID =
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");

    /** 广播超时设为 0 表示无限广播，由代码手动停止 */
    private static final int ADVERTISE_TIMEOUT = 0;

    // ===== 标识 =====
    private final String deviceId;

    // ===== BLE 广播 =====
    private Context context;
    private AdapterStatus statusCallback;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private volatile boolean advertising = false;
    private volatile boolean released = false;

    // ===== 强度状态 =====
    private volatile int currentLevel = 0;
    private volatile int targetLevel = 0;

    // ===== 定时广播更新 =====
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    /**
     * @param deviceId 设备实例唯一ID
     */
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

        startAdvertisingChannel(0); // 默认发送 Stop

        // 启动定时更新任务
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (released || !advertising) return;
                if (currentLevel != targetLevel) {
                    startAdvertisingChannel(targetLevel);
                    currentLevel = targetLevel;
                }
                mainHandler.postDelayed(this, LoveSpouseConstants.ADVERTISE_UPDATE_MS);
            }
        };
        mainHandler.post(updateRunnable);

        notifyState(AdapterStatus.State.CONNECTED, "广播已开启");
    }

    @SuppressLint("MissingPermission")
    @Override
    public void disconnect() {
        stopAdvertising();
        released = true;
        if (updateRunnable != null) {
            mainHandler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
        notifyState(AdapterStatus.State.DISCONNECTED, "广播已停止");
    }

    @Override
    public void release() {
        released = true;
        stopAdvertising();
        if (updateRunnable != null) {
            mainHandler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
        advertiser = null;
    }

    @Override
    public void updateSnapshot(Map<String, byte[]> channelData,
                               Map<String, Long> offsetsInSegment) {
        // Phase 5 填充：反序列化 protobuf 波形数据，映射为 0-9 强度等级
        // Phase 4：暂不处理，仅通过 setManualStrength 手动控制
    }

    @Override
    public void flush() {
        // Phase 5 填充：清空波形缓冲
    }

    @SuppressLint("MissingPermission")
    @Override
    public void emergencyStop() {
        targetLevel = 0;
        startAdvertisingChannel(0);
        currentLevel = 0;
    }

    @Override
    public boolean validateSegmentData(byte[] protobufBytes) {
        // Phase 5 填充：校验 LovenseVibrateWaveform protobuf
        return false;
    }

    // ══════════════════════════════════════════════════════
    //  测试面板手动控制 API
    // ══════════════════════════════════════════════════════

    /**
     * 手动设置振动等级（0-9）。
     * <p>
     * 定时器会在下一个周期（约50ms内）检测到变化并更新广播。
     */
    public void setManualStrength(int level) {
        targetLevel = clamp(level, LoveSpouseConstants.STRENGTH_MIN, LoveSpouseConstants.STRENGTH_MAX);
    }

    /** 获取当前广播的振动等级 */
    public int getCurrentLevel() {
        return currentLevel;
    }

    public boolean isAdvertising() {
        return advertising;
    }

    // ══════════════════════════════════════════════════════
    //  内部：BLE 广播
    // ══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private void startAdvertisingChannel(int channelIndex) {
        if (advertiser == null || released) return;

        // 停止旧广播
        if (advertising && advertiseCallback != null) {
            try { advertiser.stopAdvertising(advertiseCallback); } catch (Exception ignored) {}
        }

        // 构建广播数据
        byte[] manufacturerBytes = buildManufacturerData(channelIndex);

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(ADVERTISE_SERVICE_UUID))
                .addManufacturerData(LoveSpouseConstants.MANUFACTURER_ID, manufacturerBytes)
                .build();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(ADVERTISE_TIMEOUT)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                advertising = true;
                Log.d(TAG, "advertising started, channel=" + channelIndex);
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
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (Exception e) {
            Log.e(TAG, "startAdvertising exception", e);
            advertising = false;
            notifyState(AdapterStatus.State.ERROR, "广播启动失败: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        advertising = false;
        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception ignored) {}
        }
        advertiseCallback = null;
    }

    // ══════════════════════════════════════════════════════
    //  内部：数据构建
    // ══════════════════════════════════════════════════════

    /**
     * 构建 Manufacturer Data 字节数组。
     * <p>
     * 格式：[8字节前缀] + [3字节通道值] = 11 字节
     *
     * @param channelIndex 通道索引 0-9
     * @return 11 字节 manufacturer data
     */
    static byte[] buildManufacturerData(int channelIndex) {
        int idx = clamp(channelIndex, 0, LoveSpouseConstants.CHANNELS.length - 1);
        int channel = LoveSpouseConstants.CHANNELS[idx];

        byte[] data = new byte[LoveSpouseConstants.DATA_LENGTH];
        System.arraycopy(LoveSpouseConstants.PREFIX, 0, data, 0, LoveSpouseConstants.PREFIX.length);
        data[8]  = (byte) ((channel >> 16) & 0xFF);
        data[9]  = (byte) ((channel >> 8) & 0xFF);
        data[10] = (byte) (channel & 0xFF);

        return data;
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
