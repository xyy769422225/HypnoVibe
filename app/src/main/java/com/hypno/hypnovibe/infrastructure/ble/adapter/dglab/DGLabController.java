package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

/**
 * DG-LAB 设备测试面板统一控制接口。
 *
 * <p>V2 和 V3 适配器都实现此接口，测试面板通过此接口操作设备，
 * 不感知协议版本差异。强度范围统一为 0-200（与 DG-LAB 官方 APP 一致）。
 */
public interface DGLabController {

    /** 手动设置双通道目标强度（范围 0-200） */
    void setManualStrength(int a, int b);

    /** 获取设备实际强度（UI 值 0-200）。V3 来自 B1 反馈，V2 来自本地记录 */
    int getDeviceStrengthA();
    int getDeviceStrengthB();

    /** 安全开关状态 */
    boolean isSafetyOn();

    /** 解锁安全开关 */
    void unlockSafety();

    /** 设备是否已连接 */
    boolean isConnected();

    /** 紧急停止：强度归零 */
    void emergencyStop();

    /** 设置测试面板回调 */
    void setDGLabListener(DGLabListener listener);

    /** 测试面板回调接口 */
    interface DGLabListener {
        /** 强度反馈（V3 来自 B1，V2 无此回调） */
        void onStrengthFeedback(int a, int b);
        /** BLE 连接成功 */
        void onConnected();
        /** BLE 断开 */
        void onDisconnected();
    }
}
