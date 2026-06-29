package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hypno.hypnovibe.infrastructure.ble.adapter.AdapterStatus;
import com.hypno.hypnovibe.infrastructure.ble.adapter.DeviceProtocolAdapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 郊狼 V3 设备协议适配器。
 * <p>
 * 完整封装 V3 的 BLE 通信协议：100ms定时器、B0/BF/B1编解码、序列号流控、自动重连。
 * 对外实现 {@link DeviceProtocolAdapter} 接口，是郊狼子包的两个 public 入口之一。
 *
 * <h3>内部架构</h3>
 * <pre>
 * connect() → GATT连接 → 发现服务 → 绑定Notify → 写BF → 启动100ms定时器
 * 100ms定时器 → 取最新快照 → 拆槽位 → 构造B0 → writeCharacteristic
 * disconnect → B1序列号匹配 → 更新本地强度 → 解除流控等待
 * 断连 → 静默重连(最多3次) → onFatalError
 * </pre>
 */
public class CoyoteV3Adapter implements DeviceProtocolAdapter {

    private static final UUID UUID_180C = UUID.fromString(CoyoteConstants.SERVICE_V3);
    private static final UUID UUID_150A = UUID.fromString(CoyoteConstants.CHAR_WRITE_V3);
    private static final UUID UUID_150B = UUID.fromString(CoyoteConstants.CHAR_NOTIFY_V3);
    private static final UUID UUID_CLIENT_CHAR_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final String deviceId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // BLE
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothGattCharacteristic notifyChar;
    private AdapterStatus statusCallback;

    // 设备状态
    private int strengthA = 0;
    private int strengthB = 0;
    private volatile boolean isConnected = false;

    // 流控
    private int pendingSeqNo = 0;
    private boolean waitingConfirm = false;
    private int seqNoCounter = 0;
    // 累积的强度变化（等待发送，用于流控期间暂存）
    private int accumulatedDeltaA = 0;
    private int accumulatedDeltaB = 0;

    // 定时器
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> timerFuture;

    // 最新快照缓存 (volatile: Audio线程写, BLE线程读)
    private volatile byte[][] latestSnapshotChannels = null;
    private volatile long[] latestSnapshotOffsets = null;

    // 重连
    private String lastAddress;
    private Context lastContext;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS = CoyoteConstants.RETRY_DELAYS_MS;

    public CoyoteV3Adapter() {
        this.deviceId = "coyote_v3_" + System.nanoTime();
    }

    // ===== 标识 =====

    @Override
    public String getDeviceType() {
        return CoyoteConstants.DEVICE_TYPE_V3;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    // ===== 生命周期 =====

    @Override
    public void connect(Context context, String address, AdapterStatus status) {
        this.lastContext = context.getApplicationContext();
        this.lastAddress = address;
        this.statusCallback = status;
        this.retryCount = 0;

        notifyStatus(AdapterStatus.State.CONNECTING, "开始连接: " + address);

        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                notifyStatus(AdapterStatus.State.ERROR, "设备不支持蓝牙");
                return;
            }
            BluetoothDevice device = adapter.getRemoteDevice(address);
            gatt = device.connectGatt(lastContext, false, gattCallback);
        } catch (SecurityException e) {
            notifyStatus(AdapterStatus.State.ERROR, "缺少蓝牙权限: " + e.getMessage());
        } catch (Exception e) {
            notifyStatus(AdapterStatus.State.ERROR, "连接异常: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        retryCount = MAX_RETRIES; // 主动断开，不重连
        stopTimer();
        if (gatt != null) {
            try {
                gatt.disconnect();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void release() {
        disconnect();
        if (gatt != null) {
            try {
                gatt.close();
            } catch (Exception ignored) {}
            gatt = null;
        }
        writeChar = null;
        notifyChar = null;
        latestSnapshotChannels = null;
        latestSnapshotOffsets = null;
    }

    // ===== 数据通道 =====

    @Override
    public void updateSnapshot(Map<String, byte[]> channelData, Map<String, Long> offsets) {
        // 留空实现：等待 protobuf 基础设施后填充
        // 当前存储原始引用，子包内部的波形拆分需要 protobuf 反序列化
    }

    @Override
    public void flush() {
        // 清空累积缓冲
        accumulatedDeltaA = 0;
        accumulatedDeltaB = 0;
        // 如果已连接，立即触发一次 B0 发送
        if (isConnected && writeChar != null && gatt != null) {
            sendWaveformOnly();
        }
    }

    @Override
    public void emergencyStop() {
        if (isConnected && writeChar != null && gatt != null) {
            byte[] cmd = CoyoteV3Protocol.buildEmergencyStop();
            try {
                writeChar.setValue(cmd);
                gatt.writeCharacteristic(writeChar);
            } catch (SecurityException ignored) {}
        }
        strengthA = 0;
        strengthB = 0;
        pendingSeqNo = 0;
        waitingConfirm = false;
    }

    @Override
    public boolean validateSegmentData(byte[] data) {
        // 留空实现：等待 protobuf 基础设施后填充
        return false;
    }

    // ===== 内部: BLE GATT 回调 =====

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                retryCount = 0;
                notifyStatus(AdapterStatus.State.CONNECTED, "GATT 已连接, 发现服务中...");
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    notifyStatus(AdapterStatus.State.ERROR, "发现服务失败: " + e.getMessage());
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                stopTimer();
                if (retryCount < MAX_RETRIES) {
                    notifyStatus(AdapterStatus.State.RETRYING,
                            "连接断开, 第" + (retryCount + 1) + "次重试...");
                    scheduleRetry();
                } else {
                    notifyStatus(AdapterStatus.State.DISCONNECTED, "连接已断开");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                notifyStatus(AdapterStatus.State.ERROR, "服务发现失败: " + status);
                return;
            }

            BluetoothGattService service = gatt.getService(UUID_180C);
            if (service == null) {
                notifyStatus(AdapterStatus.State.ERROR, "未找到主服务 0x180C");
                return;
            }

            writeChar = service.getCharacteristic(UUID_150A);
            notifyChar = service.getCharacteristic(UUID_150B);

            if (writeChar == null || notifyChar == null) {
                notifyStatus(AdapterStatus.State.ERROR, "未找到 0x150A 或 0x150B 特性");
                return;
            }

            // 绑定 Notify
            try {
                gatt.setCharacteristicNotification(notifyChar, true);
                BluetoothGattDescriptor descriptor = notifyChar.getDescriptor(UUID_CLIENT_CHAR_CONFIG);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            } catch (SecurityException e) {
                notifyStatus(AdapterStatus.State.ERROR, "绑定 Notify 失败: " + e.getMessage());
                return;
            }

            // 写入 BF 默认值（必须先于 B0 循环）
            byte[] bfCmd = CoyoteV3Protocol.buildDefaultBF();
            try {
                writeChar.setValue(bfCmd);
                gatt.writeCharacteristic(writeChar);
            } catch (SecurityException e) {
                notifyStatus(AdapterStatus.State.ERROR, "写入 BF 失败: " + e.getMessage());
                return;
            }

            // BF 写入后延迟一点再启动 B0 循环
            mainHandler.postDelayed(() -> {
                startTimer();
                notifyStatus(AdapterStatus.State.CONNECTED, "已连接: " + lastAddress);
            }, 100);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (!CoyoteConstants.CHAR_NOTIFY_V3.equals(characteristic.getUuid().toString())) {
                return;
            }

            byte[] data = characteristic.getValue();
            if (data == null || data.length < CoyoteConstants.B1_LENGTH) {
                return;
            }

            // 检查 B1 消息头
            if ((data[0] & 0xFF) != (CoyoteConstants.HEAD_B1 & 0xFF)) {
                return;
            }

            CoyoteB1Message msg = CoyoteV3Protocol.parseB1(data);
            if (msg != null) {
                strengthA = msg.strengthA;
                strengthB = msg.strengthB;

                // 序列号匹配 → 解除流控
                if (msg.seqNo != 0 && msg.seqNo == pendingSeqNo) {
                    waitingConfirm = false;
                    pendingSeqNo = 0;
                }
            }
        }
    };

    // ===== 内部: 100ms 定时器 =====

    private void startTimer() {
        stopTimer();
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "coyote_v3_timer");
            t.setDaemon(true);
            return t;
        });
        timerFuture = timer.scheduleAtFixedRate(
                this::onTimerTick,
                0,
                CoyoteConstants.OUTPUT_WINDOW_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void stopTimer() {
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
        if (timer != null) {
            timer.shutdown();
            timer = null;
        }
    }

    /**
     * 100ms 定时器回调。
     * <p>
     * 从最新快照中取数据，拆分为4个25ms槽位，构造B0指令发送。
     * 当前快照数据为空时发送静默帧（仅维持100ms周期）。
     */
    private void onTimerTick() {
        if (!isConnected || gatt == null || writeChar == null) {
            return;
        }

        // 当前阶段：快照数据为空，发送静默波形维持100ms周期
        byte[] cmd;
        if (waitingConfirm) {
            // 流控等待中：发送"仅波形、强度不变"的 B0
            cmd = buildTimerB0(false);
            pendingSeqNo = 0;
        } else if (accumulatedDeltaA != 0 || accumulatedDeltaB != 0) {
            // 有累积的强度变化，发送强度修改
            cmd = buildTimerB0(true);
            pendingSeqNo = nextSeqNo();
            waitingConfirm = true;
            accumulatedDeltaA = 0;
            accumulatedDeltaB = 0;
        } else {
            // 纯波形输出
            cmd = buildTimerB0(false);
        }

        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            writeChar.setValue(cmd);
            success = gatt.writeCharacteristic(writeChar);
        } catch (SecurityException ignored) {
        }

        long latency = System.currentTimeMillis() - startTime;
        if (statusCallback != null) {
            statusCallback.onCycleStats(deviceId, latency, success);
        }
    }

    private byte[] buildTimerB0(boolean changeStrength) {
        int[] freqA = {10, 10, 10, 10};
        int[] waveA = {0, 0, 0, 0};
        int[] freqB = {10, 10, 10, 10};
        int[] waveB = {0, 0, 0, 0};

        if (changeStrength) {
            int absDeltaA = Math.abs(accumulatedDeltaA);
            int absDeltaB = Math.abs(accumulatedDeltaB);
            int modeA = accumulatedDeltaA > 0 ? CoyoteConstants.MODE_INCREMENT :
                    (accumulatedDeltaA < 0 ? CoyoteConstants.MODE_DECREMENT : CoyoteConstants.MODE_UNCHANGED);
            int modeB = accumulatedDeltaB > 0 ? CoyoteConstants.MODE_INCREMENT :
                    (accumulatedDeltaB < 0 ? CoyoteConstants.MODE_DECREMENT : CoyoteConstants.MODE_UNCHANGED);

            return CoyoteV3Protocol.buildB0(
                    nextSeqNoPending(), modeA, modeB,
                    absDeltaA, absDeltaB,
                    freqA, waveA, freqB, waveB
            );
        }
        return CoyoteV3Protocol.buildB0WaveformOnly(freqA, waveA, freqB, waveB);
    }

    /** 发送仅波形数据（不修改强度）的 B0 */
    private void sendWaveformOnly() {
        int[] freqA = {10, 10, 10, 10};
        int[] waveA = {0, 0, 0, 0};
        int[] freqB = {10, 10, 10, 10};
        int[] waveB = {0, 0, 0, 0};
        byte[] cmd = CoyoteV3Protocol.buildB0WaveformOnly(freqA, waveA, freqB, waveB);
        try {
            writeChar.setValue(cmd);
            gatt.writeCharacteristic(writeChar);
        } catch (SecurityException ignored) {}
    }

    // ===== 内部: 流控 =====

    private int nextSeqNo() {
        seqNoCounter++;
        if (seqNoCounter > CoyoteConstants.SEQ_NO_MAX) {
            seqNoCounter = CoyoteConstants.SEQ_NO_MIN;
        }
        return seqNoCounter;
    }

    private int nextSeqNoPending() {
        int seq = nextSeqNo();
        pendingSeqNo = seq;
        return seq;
    }

    // ===== 内部: 重连 =====

    private void scheduleRetry() {
        if (retryCount >= MAX_RETRIES) {
            if (statusCallback != null) {
                statusCallback.onFatalError(deviceId,
                        "重连失败，已重试" + MAX_RETRIES + "次");
            }
            return;
        }

        long delay = RETRY_DELAYS[retryCount];
        retryCount++;

        mainHandler.postDelayed(() -> {
            if (lastAddress != null && lastContext != null && !isConnected) {
                connect(lastContext, lastAddress, statusCallback);
            }
        }, delay);
    }

    // ===== 内部: 状态通知 =====

    private void notifyStatus(AdapterStatus.State state, String detail) {
        mainHandler.post(() -> {
            if (statusCallback != null) {
                statusCallback.onStateChanged(state, deviceId, detail);
            }
        });
    }
}
