package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

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

import com.hypno.hypnovibe.infrastructure.ble.adapter.AdapterStatus;
import com.hypno.hypnovibe.infrastructure.ble.adapter.DeviceProtocolAdapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 郊狼 V2 设备协议适配器。
 * <p>
 * 完整封装 V2 的 BLE 通信协议：多特性 GATT管理、100ms定时器、大小端转换、PWM指令构造。
 * 对外实现 {@link DeviceProtocolAdapter} 接口，是郊狼子包的两个 public 入口之一。
 *
 * <h3>V2 与 V3 的关键差异</h3>
 * <ul>
 *   <li>UUID 基地址不同: V2 使用 955Axxxx 自定义基地址</li>
 *   <li>特性分离: PWM_AB2(强度) + PWM_A34/PWM_B34(波形)，强度和波形分开写入</li>
 *   <li>大小端转换: V2 数据需要大小端转换</li>
 *   <li>命名交叉: PWM_A34 写入B通道波形，PWM_B34 写入A通道波形</li>
 *   <li>无反馈机制: 没有 V3 的 B1 序列号确认</li>
 * </ul>
 */
public class CoyoteV2Adapter implements DeviceProtocolAdapter {

    private static final UUID UUID_180A = UUID.fromString(CoyoteConstants.SERVICE_V2_INFO);
    private static final UUID UUID_180B = UUID.fromString(CoyoteConstants.SERVICE_V2_CTRL);
    private static final UUID UUID_PWM_AB2 = UUID.fromString(CoyoteConstants.CHAR_PWM_AB2);
    private static final UUID UUID_PWM_A34 = UUID.fromString(CoyoteConstants.CHAR_PWM_A34);
    private static final UUID UUID_PWM_B34 = UUID.fromString(CoyoteConstants.CHAR_PWM_B34);

    private final String deviceId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // BLE
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic charAB2;  // 强度
    private BluetoothGattCharacteristic charA34;  // 波形 (B通道, 命名交叉!)
    private BluetoothGattCharacteristic charB34;  // 波形 (A通道, 命名交叉!)
    private AdapterStatus statusCallback;

    // 设备状态
    private int strengthA = 0;
    private int strengthB = 0;
    private volatile boolean isConnected = false;

    // 定时器
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> timerFuture;

    // 最新快照缓存
    private volatile byte[][] latestSnapshotChannels = null;

    // 重连
    private String lastAddress;
    private Context lastContext;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS = CoyoteConstants.RETRY_DELAYS_MS;

    public CoyoteV2Adapter() {
        this.deviceId = "coyote_v2_" + System.nanoTime();
    }

    // ===== 标识 =====

    @Override
    public String getDeviceType() {
        return CoyoteConstants.DEVICE_TYPE_V2;
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
        retryCount = MAX_RETRIES;
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
        charAB2 = null;
        charA34 = null;
        charB34 = null;
    }

    // ===== 数据通道 =====

    @Override
    public void updateSnapshot(Map<String, byte[]> channelData, Map<String, Long> offsets) {
        // 留空实现：等待 protobuf 基础设施后填充
    }

    @Override
    public void flush() {
        // V2: 立即发送当前波形数据
        if (isConnected && gatt != null) {
            sendWaveformCycle();
        }
    }

    @Override
    public void emergencyStop() {
        // V2 安全停止：强度归零
        if (isConnected && gatt != null && charAB2 != null) {
            byte[] cmd = CoyoteV2Protocol.buildPwmAB2(0, 0);
            try {
                charAB2.setValue(cmd);
                gatt.writeCharacteristic(charAB2);
            } catch (SecurityException ignored) {}
        }
        strengthA = 0;
        strengthB = 0;
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

            // 获取控制服务 (0x180B)
            BluetoothGattService ctrlService = gatt.getService(UUID_180B);
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

            // 启动 100ms 定时器
            startTimer();
            notifyStatus(AdapterStatus.State.CONNECTED, "已连接: " + lastAddress);
        }
    };

    // ===== 内部: 100ms 定时器 =====

    private void startTimer() {
        stopTimer();
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "coyote_v2_timer");
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
     * V2 每100ms写入 PWM_A34(实际B通道波形) + PWM_B34(实际A通道波形)。
     * 通道强度(PWM_AB2)不需要周期发送，仅强度变化时写入。
     */
    private void onTimerTick() {
        if (!isConnected || gatt == null) {
            return;
        }
        sendWaveformCycle();
    }

    private void sendWaveformCycle() {
        if (gatt == null) return;

        // 静默波形：X=1,Y=99(频率100ms), Z=0(无脉冲)
        byte[] cmdA34 = CoyoteV2Protocol.buildPwmA34(1, 99, 0);
        byte[] cmdB34 = CoyoteV2Protocol.buildPwmB34(1, 99, 0);

        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            if (charA34 != null) {
                charA34.setValue(cmdA34);
                gatt.writeCharacteristic(charA34);
            }
            if (charB34 != null) {
                charB34.setValue(cmdB34);
                gatt.writeCharacteristic(charB34);
            }
            success = true;
        } catch (SecurityException ignored) {
        }

        long latency = System.currentTimeMillis() - startTime;
        if (statusCallback != null) {
            statusCallback.onCycleStats(deviceId, latency, success);
        }
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
