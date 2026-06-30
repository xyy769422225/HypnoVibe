package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

/**
 * 郊狼设备测试面板统一控制接口。
 *
 * <p>V2 和 V3 适配器都实现此接口，测试面板通过此接口操作设备，
 * 不感知协议版本差异。强度范围统一为 0-200（与 DG-LAB 官方 APP 一致），
 * 各适配器内部自行换算为协议值：
 * <ul>
 *   <li>V3: 1:1 直接写入（协议范围 0-200）</li>
 *   <li>V2: ×7 写入（协议范围 0-1400，对齐官方 APP 行为）</li>
 * </ul>
 *
 * <p><b>安全性</b>：V2 没有 B1 强度反馈机制，{@link #getDeviceStrengthA()}
 * 和 {@link #getDeviceStrengthB()} 返回的是最后一次写入的目标值，不是设备真实值。
 * 如果用户物理拨动 V2 主机滚轮，APP 无法感知。
 */
public interface CoyoteController {

    /** 手动设置双通道目标强度（范围 0-200） */
    void setManualStrength(int a, int b);

    /** 获取设备实际强度（UI 值 0-200）。V3 来自 B1 反馈，V2 来自本地记录 */
    int getDeviceStrengthA();
    int getDeviceStrengthB();

    /** 安全开关状态。开启时定时器不发送强度修改 */
    boolean isSafetyOn();

    /** 解锁安全开关，允许发送强度修改 */
    void unlockSafety();

    /** 设备是否已连接 */
    boolean isConnected();

    /** 紧急停止：强度归零 */
    void emergencyStop();

    /** 设置测试面板回调 */
    void setCoyoteListener(CoyoteListener listener);

    /** 测试面板回调接口 */
    interface CoyoteListener {
        /** 强度反馈（V3 来自 B1，V2 无此回调） */
        void onStrengthFeedback(int a, int b);
        /** BLE 连接成功 */
        void onConnected();
        /** BLE 断开 */
        void onDisconnected();
    }
}
