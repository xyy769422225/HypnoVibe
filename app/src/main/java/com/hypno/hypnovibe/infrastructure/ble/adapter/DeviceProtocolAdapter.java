package com.hypno.hypnovibe.infrastructure.ble.adapter;

import android.content.Context;

import java.util.Map;

/**
 * 设备协议适配器统一接口。
 * <p>
 * 每种设备类型（郊狼V3/V2、Lovense等）实现此接口，
 * 内部封装其专属的BLE通信协议、数据格式和时序控制。
 * 上层通过此接口操作设备，不感知协议细节。
 */
public interface DeviceProtocolAdapter {

    // ===== 标识 =====

    /** 设备类型标识（如 "coyote_v3", "coyote_v2", "lovense_vibrate"） */
    String getDeviceType();

    /** 设备实例唯一ID（用于日志/统计） */
    String getDeviceId();

    // ===== 生命周期 =====

    /**
     * 建立连接。
     * <p>
     * 传输层细节由实现类封装（BLE/USB/模拟），
     * 调用方只需传入 Context + 地址字符串。
     */
    void connect(Context context, String address, AdapterStatus status);

    /** 主动断开 */
    void disconnect();

    /** 销毁适配器，释放所有资源（定时器、BLE连接、缓冲区） */
    void release();

    // ===== 数据通道 =====

    /**
     * 接收最新的时间轴快照数据（由 PlaybackCoordinator 推送，~33ms）。
     * <p>
     * 适配器内部定时器（100ms）从最近一次推送的快照中取数据构造指令。
     *
     * @param channelData  通道ID -> 波形数据字节
     * @param offsets      通道ID -> 段内偏移量(ms)
     */
    void updateSnapshot(Map<String, byte[]> channelData, Map<String, Long> offsets);

    /** seek 时清空内部缓冲区，立即用新位置数据覆盖 */
    void flush();

    /** 安全停止，强度归零 */
    void emergencyStop();

    // ===== 校验 =====

    /** 校验段数据是否属于本设备类型 */
    boolean validateSegmentData(byte[] data);
}
