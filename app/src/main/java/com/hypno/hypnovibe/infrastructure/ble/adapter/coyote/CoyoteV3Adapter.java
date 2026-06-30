package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 郊狼 V3 设备适配器。
 *
 * 完整实现 DeviceProtocolAdapter 接口，Phase 4 中 connect/disconnect/release/emergencyStop
 * 完整可用，updateSnapshot/validateSegmentData 留空（Phase 5 填充）。
 *
 * 测试面板通过 setManualStrength 手动设置目标强度，由内部 100ms 定时器统一发送 B0。
 */
public class CoyoteV3Adapter implements DeviceProtocolAdapter, CoyoteController {

    private static final String TAG = "CoyoteV3Adapter";
    private static final String DEVICE_TYPE = "coyote_v3";

    // GATT UUID
    private static final UUID SERVICE_UUID =
            UUID.fromString("0000180c-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_WRITE =
            UUID.fromString("0000150a-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_NOTIFY =
            UUID.fromString("0000150b-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long B1_TIMEOUT_MS = 500;
    private static final long TIMER_INTERVAL_MS = 100;

    // ===== 标识 =====
    private final String deviceId;

    // ===== BLE =====
    private Context context;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothGattCharacteristic notifyChar;
    private AdapterStatus statusCallback;

    // ===== 定时器 =====
    private ScheduledExecutorService timer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ===== 强度状态 =====
    private volatile int targetStrengthA = 0;
    private volatile int targetStrengthB = 0;
    private volatile int deviceStrengthA = 0;
    private volatile int deviceStrengthB = 0;
    private volatile boolean safetyOn = true;

    // ===== 流控状态 =====
    private final AtomicInteger pendingSeqNo = new AtomicInteger(0);
    private volatile boolean waitingConfirm = false;
    private long lastB1Time = 0;

    // ===== 波形（Phase 4 固定基础波形） =====
    private int[] freqA = CoyoteB0Builder.basicFreq();
    private int[] waveA = CoyoteB0Builder.basicWaveStrength();
    private int[] freqB = CoyoteB0Builder.basicFreq();
    private int[] waveB = CoyoteB0Builder.basicWaveStrength();

    // ===== 测试回调 =====
    private CoyoteController.CoyoteListener coyoteListener;
    private volatile boolean connected = false;
    private volatile boolean released = false;

    public CoyoteV3Adapter(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void setCoyoteListener(CoyoteController.CoyoteListener listener) {
        this.coyoteListener = listener;
    }

    // ══════════════════════════════════════════════════════
    //  DeviceProtocolAdapter 实现
    // ══════════════════════════════════════════════════════

    @Override public String getDeviceType() { return DEVICE_TYPE; }
    @Override public String getDeviceId() { return deviceId; }

    @SuppressLint("MissingPermission")
    @Override
    public void connect(Context ctx, String address, AdapterStatus status) {
        this.context = ctx.getApplicationContext();
        this.statusCallback = status;
        notifyState(AdapterStatus.State.CONNECTING, "正在连接...");

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            notifyState(AdapterStatus.State.ERROR, "BluetoothAdapter 不可用");
            return;
        }
        BluetoothDevice device = adapter.getRemoteDevice(address);
        gatt = device.connectGatt(ctx, false, gattCallback);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void disconnect() {
        stopTimer();
        connected = false;
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) {}
            gatt = null;
        }
        writeChar = null;
        notifyChar = null;
        safetyOn = true;
        notifyState(AdapterStatus.State.DISCONNECTED, "已断开");
        if (coyoteListener != null) coyoteListener.onDisconnected();
    }

    @Override
    public void release() {
        released = true;
        disconnect();
    }

    @Override
    public void updateSnapshot(Map<String, byte[]> channelData,
                               Map<String, Long> offsetsInSegment) {
        // Phase 5 填充：反序列化 protobuf，更新 freqA/waveA/freqB/waveB
    }

    @Override
    public void flush() {
        // Phase 5 填充：清空波形缓冲
    }

    @Override
    public void emergencyStop() {
        targetStrengthA = 0;
        targetStrengthB = 0;
        safetyOn = true;
        // 立即发送归零 B0，绕开定时器
        int seq = nextSeqNo();
        byte[] cmd = CoyoteB0Builder.buildB0(
            true, seq,
            CoyoteB0Builder.MODE_ABSOLUTE, CoyoteB0Builder.MODE_ABSOLUTE,
            0, 0,
            freqA, waveA, freqB, waveB);
        writeCharacteristic(cmd);
        waitingConfirm = true;
        pendingSeqNo.set(seq);
    }

    @Override
    public boolean validateSegmentData(byte[] protobufBytes) {
        return false; // Phase 5 填充
    }

    // ══════════════════════════════════════════════════════
    //  测试面板专用 API
    // ══════════════════════════════════════════════════════

    /** 手动设置目标强度（0-200），由 100ms 定时器统一发送 */
    public void setManualStrength(int a, int b) {
        targetStrengthA = clamp(a, 0, 200);
        targetStrengthB = clamp(b, 0, 200);
    }

    /** 获取设备回报的实际强度（B1 更新） */
    public int getDeviceStrengthA() { return deviceStrengthA; }
    public int getDeviceStrengthB() { return deviceStrengthB; }

    public boolean isSafetyOn() { return safetyOn; }

    /** 解锁安全开关，允许定时器发送强度 */
    public void unlockSafety() {
        safetyOn = false;
    }

    public boolean isConnected() { return connected && !released; }

    // ══════════════════════════════════════════════════════
    //  GATT 回调
    // ══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "GATT connected, discovering services");
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT disconnected (status=" + status + ")");
                connected = false;
                stopTimer();
                safetyOn = true;
                mainHandler.post(() -> {
                    notifyState(AdapterStatus.State.DISCONNECTED, "设备断开");
                    if (coyoteListener != null) coyoteListener.onDisconnected();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServicesDiscovered failed: " + status);
                notifyState(AdapterStatus.State.ERROR, "服务发现失败");
                return;
            }
            writeChar = g.getService(SERVICE_UUID).getCharacteristic(CHAR_WRITE);
            notifyChar = g.getService(SERVICE_UUID).getCharacteristic(CHAR_NOTIFY);
            if (writeChar == null || notifyChar == null) {
                notifyState(AdapterStatus.State.ERROR, "特征值未找到");
                return;
            }

            // 绑定 Notify
            g.setCharacteristicNotification(notifyChar, true);
            BluetoothGattDescriptor desc = notifyChar.getDescriptor(DESCRIPTOR_UUID);
            if (desc != null) {
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(desc);
            }

            // 写入 BF 默认值（延迟 200ms 确保 descriptor 写入完成）
            mainHandler.postDelayed(() -> {
                if (released || gatt == null) return;
                byte[] bf = CoyoteB0Builder.buildDefaultBF();
                writeChar.setValue(bf);
                gatt.writeCharacteristic(writeChar);

                // 启动 100ms 定时器
                startTimer();
                connected = true;
                notifyState(AdapterStatus.State.CONNECTED, "连接成功");
                if (coyoteListener != null) coyoteListener.onConnected();
            }, 200);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g,
                                            BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length >= 4 && data[0] == (byte) 0xB1) {
                onB1Received(data);
            }
        }
    };

    // ══════════════════════════════════════════════════════
    //  B1 反馈处理
    // ══════════════════════════════════════════════════════

    private void onB1Received(byte[] data) {
        int seqNo = data[1] & 0xFF;
        int a = data[2] & 0xFF;
        int b = data[3] & 0xFF;

        deviceStrengthA = a;
        deviceStrengthB = b;
        lastB1Time = System.currentTimeMillis();

        if (seqNo == pendingSeqNo.get() && seqNo != 0) {
            waitingConfirm = false;
            pendingSeqNo.set(0);
        }

        if (coyoteListener != null) {
            coyoteListener.onStrengthFeedback(a, b);
        }
    }

    // ══════════════════════════════════════════════════════
    //  100ms 定时器
    // ══════════════════════════════════════════════════════

    private void startTimer() {
        if (timer != null) return;
        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> {
            try {
                onTimerTick();
            } catch (Exception e) {
                Log.e(TAG, "timer tick error", e);
            }
        }, 0, TIMER_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }

    private void onTimerTick() {
        if (released || !connected || gatt == null || writeChar == null) return;

        // B1 超时检查
        if (waitingConfirm && System.currentTimeMillis() - lastB1Time > B1_TIMEOUT_MS) {
            waitingConfirm = false;
            pendingSeqNo.set(0);
        }

        if (safetyOn) {
            // 安全模式：仅发波形，强度不变
            sendB0StrengthUnchanged();
            return;
        }

        if (waitingConfirm) {
            sendB0StrengthUnchanged();
            return;
        }

        boolean needChangeA = targetStrengthA != deviceStrengthA;
        boolean needChangeB = targetStrengthB != deviceStrengthB;

        if (needChangeA || needChangeB) {
            int seq = nextSeqNo();
            pendingSeqNo.set(seq);
            waitingConfirm = true;

            int modeA = needChangeA ? CoyoteB0Builder.MODE_ABSOLUTE : CoyoteB0Builder.MODE_UNCHANGED;
            int modeB = needChangeB ? CoyoteB0Builder.MODE_ABSOLUTE : CoyoteB0Builder.MODE_UNCHANGED;

            byte[] cmd = CoyoteB0Builder.buildB0(
                true, seq, modeA, modeB,
                targetStrengthA, targetStrengthB,
                freqA, waveA, freqB, waveB);
            writeCharacteristic(cmd);
        } else {
            sendB0StrengthUnchanged();
        }
    }

    private void sendB0StrengthUnchanged() {
        byte[] cmd = CoyoteB0Builder.buildB0(
            false, 0,
            CoyoteB0Builder.MODE_UNCHANGED, CoyoteB0Builder.MODE_UNCHANGED,
            0, 0,
            freqA, waveA, freqB, waveB);
        writeCharacteristic(cmd);
    }

    // ══════════════════════════════════════════════════════
    //  工具方法
    // ══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private void writeCharacteristic(byte[] value) {
        if (gatt == null || writeChar == null) return;
        writeChar.setValue(value);
        boolean ok = gatt.writeCharacteristic(writeChar);
        if (!ok) {
            Log.w(TAG, "writeCharacteristic failed");
            if (statusCallback != null) {
                statusCallback.onCycleStats(deviceId, 0, false);
            }
        }
    }

    private int nextSeqNo() {
        int s = pendingSeqNo.get();
        if (s == 0) return 1;
        return (s % 15) + 1;
    }

    private void notifyState(AdapterStatus.State state, String detail) {
        if (statusCallback != null) {
            statusCallback.onStateChanged(state, deviceId, detail);
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
