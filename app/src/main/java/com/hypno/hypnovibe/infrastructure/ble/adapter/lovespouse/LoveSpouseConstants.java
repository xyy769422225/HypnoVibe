package com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse;

/**
 * Love Spouse 协议常量。
 * <p>
 * 基于 LS-Buttplug 和 LoveSpouse-Vibration-Controller 两个开源项目逆向。
 * package-private，仅 lovespouse 子包内部使用。
 */
final class LoveSpouseConstants {

    private LoveSpouseConstants() {}

    // ===== 设备类型标识 =====
    static final String DEVICE_TYPE = "love_spouse";

    // ===== BLE 广播参数 =====

    /** Manufacturer ID */
    static final int MANUFACTURER_ID = 0xFFF0;

    /** 制造商数据固定前缀（协议标识，非设备ID） */
    static final byte[] PREFIX = {
        (byte) 0x6D, (byte) 0xB6, (byte) 0x43, (byte) 0xCE,
        (byte) 0x97, (byte) 0xFE, (byte) 0x42, (byte) 0x7C
    };

    /** 制造商数据总长度：前缀(8) + 命令(3) = 11 */
    static final int DATA_LENGTH = 11;

    // ===== 振动等级通道值（来源：LS-Buttplug LS.h） =====

    static final int CHANNEL_STOP = 0;  // 停止
    static final int CHANNEL_L1   = 1;
    static final int CHANNEL_L2   = 2;
    static final int CHANNEL_L3   = 3;
    static final int CHANNEL_L4   = 4;
    static final int CHANNEL_L5   = 5;
    static final int CHANNEL_L6   = 6;
    static final int CHANNEL_L7   = 7;
    static final int CHANNEL_L8   = 8;
    static final int CHANNEL_L9   = 9;

    /** 3字节通道值数组，按等级索引 */
    static final int[] CHANNELS = {
        0xE50000, // Stop
        0xF40000, // L1
        0xF70000, // L2
        0xF60000, // L3
        0xF10000, // L4
        0xF00000, // L5
        0xF30000, // L6
        0xE70000, // L7
        0xFC0000, // L8
        0xE60000, // L9
    };

    // ===== 振动等级范围 =====
    static final int STRENGTH_MIN = 0;
    static final int STRENGTH_MAX = 9;

    // ===== 广播更新周期 =====
    /** 广播数据更新间隔（ms），LS-Buttplug 原为 20ms，这里放宽到 50ms */
    static final long ADVERTISE_UPDATE_MS = 50;
}
