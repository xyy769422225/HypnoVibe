package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

/**
 * DG-LAB 协议常量定义。
 * <p>
 * 集中管理所有UUID、指令头、取值范围等常量，避免魔法数字散落各处。
 * package-private，仅 dglab 子包内部使用。
 */
final class DGLabConstants {

    private DGLabConstants() {}

    // ===== V3 UUID（标准16-bit扩展） =====

    /** V3 主服务: 0x180C */
    static final String SERVICE_V3 = "0000180c-0000-1000-8000-00805f9b34fb";

    /** V3 写入特性: 0x150A（所有指令写入） */
    static final String CHAR_WRITE_V3 = "0000150a-0000-1000-8000-00805f9b34fb";

    /** V3 通知特性: 0x150B（所有回应消息） */
    static final String CHAR_NOTIFY_V3 = "0000150b-0000-1000-8000-00805f9b34fb";

    /** 电池服务: 0x180A */
    static final String SERVICE_BATTERY = "0000180a-0000-1000-8000-00805f9b34fb";

    /** 电池特性: 0x1500 */
    static final String CHAR_BATTERY = "00001500-0000-1000-8000-00805f9b34fb";

    // ===== V2 UUID（自定义基地址 955Axxxx...） =====

    /** V2 设备信息服务: 0x180A */
    static final String SERVICE_V2_INFO = "955a180a-0fe2-f5aa-a094-84b8d4f3e8ad";

    /** V2 控制服务: 0x180B */
    static final String SERVICE_V2_CTRL = "955a180b-0fe2-f5aa-a094-84b8d4f3e8ad";

    /** V2 PWM_AB2 特性（AB通道强度, 3 bytes） */
    static final String CHAR_PWM_AB2 = "955a1504-0fe2-f5aa-a094-84b8d4f3e8ad";

    /** V2 PWM_A34 特性（B通道波形数据, 3 bytes, 注意命名交叉） */
    static final String CHAR_PWM_A34 = "955a1505-0fe2-f5aa-a094-84b8d4f3e8ad";

    /** V2 PWM_B34 特性（A通道波形数据, 3 bytes, 注意命名交叉） */
    static final String CHAR_PWM_B34 = "955a1506-0fe2-f5aa-a094-84b8d4f3e8ad";

    // ===== V3 指令头 =====

    /** B0: 核心控制指令（通道强度 + 波形数据, 20 bytes） */
    static final byte HEAD_B0 = (byte) 0xB0;

    /** B1: 强度反馈消息（4 bytes, 来自设备 Notify） */
    static final byte HEAD_B1 = (byte) 0xB1;

    /** BF: 软上限与平衡参数（7 bytes, 断电保存） */
    static final byte HEAD_BF = (byte) 0xBF;

    // ===== 指令长度 =====

    static final int B0_LENGTH = 20;
    static final int B1_LENGTH = 4;
    static final int BF_LENGTH = 7;
    static final int V2_PWM_LENGTH = 3;

    // ===== V3 强度范围 =====

    static final int STRENGTH_V3_MIN = 0;
    static final int STRENGTH_V3_MAX = 200;

    // ===== V2 强度范围 =====

    static final int STRENGTH_V2_MIN = 0;
    static final int STRENGTH_V2_MAX = 2047;

    // ===== V3 波形参数范围 =====

    /** 波形频率协议值范围: 10-240 */
    static final int WAVE_FREQ_MIN = 10;
    static final int WAVE_FREQ_MAX = 240;

    /** 波形强度范围: 0-100 */
    static final int WAVE_STRENGTH_MIN = 0;
    static final int WAVE_STRENGTH_MAX = 100;

    /** 用户输入频率范围: 10-1000ms */
    static final int USER_FREQ_MIN = 10;
    static final int USER_FREQ_MAX = 1000;

    // ===== V2 波形参数范围 =====

    /** 连续脉冲数: 0-31 */
    static final int V2_X_MAX = 31;

    /** 脉冲间隔: 0-1023 */
    static final int V2_Y_MAX = 1023;

    /** 脉冲宽度: 0-31, 实际宽度=Z*5μs */
    static final int V2_Z_MAX = 31;

    // ===== 序列号 =====

    /** 序列号=0: 不要求设备反馈 */
    static final int SEQ_NO_FEEDBACK = 0;

    static final int SEQ_NO_MIN = 1;
    static final int SEQ_NO_MAX = 15;

    // ===== 定时周期 =====

    /** V3/V2 输出窗口: 100ms */
    static final long OUTPUT_WINDOW_MS = 100;

    /** V3 每窗口 4 个 25ms 槽位 */
    static final int SLOTS_PER_WINDOW = 4;

    /** 重连间隔: 1s / 3s / 5s */
    static final long[] RETRY_DELAYS_MS = {1000L, 3000L, 5000L};

    /** GATT 连接超时: 15000ms（对齐官方 timeout(15000)） */
    static final long CONNECT_TIMEOUT_MS = 15000;

    // ===== BF 默认值 =====

    /** 软上限默认值: 200（不限制） */
    static final int BF_DEFAULT_SOFT_LIMIT = 200;

    /** 平衡参数默认值: 128（中性） */
    static final int BF_DEFAULT_BALANCE = 128;

    // ===== 强度解读方式（B0 字节1 低4bit） =====

    /** bit1-0 / bit3-2 = 00: 强度不变 */
    static final int MODE_UNCHANGED = 0b00;

    /** bit1-0 / bit3-2 = 01: 相对增加 */
    static final int MODE_INCREMENT = 0b01;

    /** bit1-0 / bit3-2 = 10: 相对减少 */
    static final int MODE_DECREMENT = 0b10;

    /** bit1-0 / bit3-2 = 11: 绝对设置 */
    static final int MODE_ABSOLUTE = 0b11;

    // ===== 设备类型标识 =====

    static final String DEVICE_TYPE_V3 = "dglab_v3";
    static final String DEVICE_TYPE_V2 = "dglab_v2";

    // ===== V2 蓝牙广播名称 =====

    /** V2 设备广播名称 */
    static final String V2_BROADCAST_NAME = "D-LAB ESTIM01";

    /** V3 设备广播名称前缀 */
    static final String V3_BROADCAST_NAME_PREFIX = "47L121";
}
