package com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse;

/**
 * Love Spouse 2.4G BLE 广播协议常量。
 * <p>
 * 基于官方 Love Spouse APK (v4.0.0) 逆向分析，通过 libble.so JNI 编码。
 * <p>
 * 协议特点：无需 GATT 配对，纯 BLE 广播控制；
 * 广播自动超时 1 秒，需周期性重发维持控制。
 */
final class LoveSpouseConstants {

    private LoveSpouseConstants() {}

    // ===== 设备类型标识 =====
    static final String DEVICE_TYPE = "love_spouse";

    // ===== BLE 广播参数 =====

    /** Manufacturer ID（官方 APK 使用 0x00FF = 255） */
    static final int MANUFACTURER_ID = 0x00FF;

    /**
     * 广播前缀（官方 APK 默认值）。
     * ASCII "wbMSE" = {0x77, 0x62, 0x4D, 0x53, 0x45}，5 字节。
     * 设备 BarCode=5342/3747 使用特殊格式，5787 使用此默认值。
     */
    static final byte[] PREFIX = {
        (byte) 0x77, (byte) 0x62, (byte) 0x4D, (byte) 0x53, (byte) 0x45
    };

    /** JNI 编码额外输出字节数（CRC/校验码） */
    static final int RF_PAYLOAD_OVERHEAD = 5;

    // ===== 振动等级映射 =====

    /** 强度 0-9 对应的 hex 命令字符串（官方 APK CommonClassicMode.V() + s()） */
    static final String[] STRENGTH_COMMANDS = {
        "00",   // 0 = 停止
        "11",   // 1
        "12",   // 2
        "13",   // 3
        "14",   // 4
        "15",   // 5
        "16",   // 6
        "17",   // 7
        "18",   // 8
        "19",   // 9
    };

    static final int STRENGTH_MIN = 0;
    static final int STRENGTH_MAX = 9;

    // ===== 广播参数 =====

    /** 广播超时（ms），0=无限，官方 Timer 在 1000ms 后停止 */
    static final long ADVERTISE_STOP_DELAY_MS = 1000;

    /** 广播更新间隔（ms），至少 60ms 以上 */
    static final long BROADCAST_INTERVAL_MS = 100;

    /** 广播模式 type：1=低延迟, 2=均衡（官方强度用 1，停止用 2） */
    static final int ADVERTISE_MODE_POWER = 1;
    static final int ADVERTISE_MODE_STOP = 2;
}
