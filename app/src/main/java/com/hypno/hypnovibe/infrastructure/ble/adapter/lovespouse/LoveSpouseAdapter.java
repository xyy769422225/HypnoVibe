package com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
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
 * 基于官方 Love Spouse APK v4.0.0 逆向实现。
 * <p>
 * 协议特点：设备端锁存最后收到的命令，因此只需在参数变化时发送一次，
 * 无需持续重发。
 * <p>
 * 支持三类命令：
 * <ul>
 *   <li>强度 (0-9): 持续振动，等价于官方滑块</li>
 *   <li>模式 ("01"-"09" 等): 内置振动 pattern，等价于模式选择</li>
 *   <li>停止 ("00"): 立即停止所有振动</li>
 * </ul>
 */
public class LoveSpouseAdapter implements DeviceProtocolAdapter {

    private static final String TAG = "LoveSpouseAdapter";

    private static final UUID SERVICE_UUID =
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

    // ===== 状态 =====
    private volatile int currentStrength = 0;
    private volatile String lastCommand = null;  // 记录最后发送的命令

    public LoveSpouseAdapter(String deviceId) {
        this.deviceId = deviceId;
    }

    // ══════════════════════════════════════════════════════
    //  DeviceProtocolAdapter 实现
    // ══════════════════════════════════════════════════════

    @Override public String getDeviceType() { return LoveSpouseConstants.DEVICE_TYPE; }
    @Override public String getDeviceId() { return deviceId; }

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

        // 初始发一次停止（安全）
        sendCommand(LoveSpouseConstants.STOP_ALL, LoveSpouseConstants.CommandType.STOP);
        notifyState(AdapterStatus.State.CONNECTED, "广播已就绪");
    }

    @SuppressLint("MissingPermission")
    @Override
    public void disconnect() {
        released = true;
        sendCommand(LoveSpouseConstants.STOP_ALL, LoveSpouseConstants.CommandType.STOP);
        notifyState(AdapterStatus.State.DISCONNECTED, "已停止");
    }

    @Override
    public void release() {
        released = true;
        stopAdvertising();
        advertiser = null;
    }

    @Override public void updateSnapshot(Map<String, byte[]> channelData,
                                          Map<String, Long> offsetsInSegment) { /* Phase 5 */ }

    @Override public void flush() { /* Phase 5 */ }

    @SuppressLint("MissingPermission")
    @Override
    public void emergencyStop() {
        sendCommand(LoveSpouseConstants.STOP_ALL, LoveSpouseConstants.CommandType.STOP);
        currentStrength = 0;
    }

    @Override public boolean validateSegmentData(byte[] protobufBytes) { return false; }

    // ══════════════════════════════════════════════════════
    //  公共控制 API
    // ══════════════════════════════════════════════════════

    /** 设置强度 (0-9)，官方滑块等价 */
    public void setStrength(int level) {
        int clamped = clamp(level, LoveSpouseConstants.STRENGTH_MIN, LoveSpouseConstants.STRENGTH_MAX);
        if (clamped == currentStrength && lastCommand != null) return; // unchanged
        currentStrength = clamped;
        sendCommand(LoveSpouseConstants.STRENGTH_COMMANDS[clamped],
                    LoveSpouseConstants.CommandType.STRENGTH);
    }

    /** 切换模式 (hex 字符串如 "01", "35", "42") */
    public void sendMode(String commandHex) {
        sendCommand(commandHex, LoveSpouseConstants.CommandType.MODE);
    }

    /** 停止 */
    public void stop() {
        sendCommand(LoveSpouseConstants.STOP_ALL, LoveSpouseConstants.CommandType.STOP);
        currentStrength = 0;
    }

    /** 获取最后发送的命令 */
    public String getLastCommand() { return lastCommand; }

    public int getCurrentStrength() { return currentStrength; }

    public boolean isAdvertising() { return advertising && !released; }

    // ══════════════════════════════════════════════════════
    //  内部：统一命令发送
    // ══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private void sendCommand(String hexCmd, LoveSpouseConstants.CommandType type) {
        if (advertiser == null || released) return;

        // 去重：同一命令不重复发
        if (hexCmd.equals(lastCommand)) return;

        stopAdvertising();

        // JNI 编码
        byte[] payload = encode(hexCmd);

        // 广播数据
        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addManufacturerData(LoveSpouseConstants.MANUFACTURER_ID, payload)
                .build();

        // 广播设置：强度/模式用低延迟，停止用均衡
        int mode = (type == LoveSpouseConstants.CommandType.STOP)
                ? LoveSpouseConstants.ADVERTISE_MODE_STOP
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
            @Override public void onStartSuccess(AdvertiseSettings s) { advertising = true; }
            @Override public void onStartFailure(int code) {
                advertising = false;
                Log.e(TAG, "ad failed: " + code);
            }
        };

        try {
            advertiser.startAdvertising(settings, data, currentCallback);
            advertising = true;
            lastCommand = hexCmd;
        } catch (Exception e) {
            Log.e(TAG, "ad exception", e);
            advertising = false;
        }
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        advertising = false;
        if (advertiser != null && currentCallback != null) {
            try { advertiser.stopAdvertising(currentCallback); } catch (Exception ignored) {}
        }
        currentCallback = null;
    }

    // ══════════════════════════════════════════════════════
    //  JNI 编码
    // ══════════════════════════════════════════════════════

    /**
     * 对任意 hex 命令字符串进行 JNI 编码。
     * <p>
     * 输出 = get_rf_payload(prefix, payload, output)
     * payload 为单字节 hexCmd 的值。
     */
    static byte[] encode(String hexCmd) {
        byte[] prefix = {0x77, 0x62, 0x4D, 0x53, 0x45}; // "wbMSE"
        byte cmdByte = (byte) Integer.parseInt(hexCmd, 16);
        byte[] output = new byte[prefix.length + 1 + LoveSpouseConstants.RF_PAYLOAD_OVERHEAD];
        BLEUtil.get_rf_payload(prefix, prefix.length, new byte[]{cmdByte}, 1, output);
        return output;
    }

    // ══════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════

    private void notifyState(AdapterStatus.State state, String detail) {
        if (statusCallback != null) statusCallback.onStateChanged(state, deviceId, detail);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
