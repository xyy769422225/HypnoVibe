package com.hypno.hypnovibe.domain;

import android.content.Context;
import java.util.Map;

/**
 * 设备协议适配器统一接口。
 * 每种设备类型实现此接口，完全封装其通信协议、数据格式、时序控制。
 * 上层只通过此接口与设备交互，不感知协议细节。
 */
public interface DeviceProtocolAdapter {

    // ===== 标识 =====
    /** 设备类型标识（如 "coyote_v3", "coyote_v2", "lovense_vibrate"） */
    String getDeviceType();
    /** 设备实例唯一ID（用于日志/统计） */
    String getDeviceId();

    // ===== 生命周期 =====
    /** 建立连接（传输层细节由实现类封装，可以是 BLE/USB/模拟） */
    void connect(Context context, String address, AdapterStatus status);
    /** 主动断开 */
    void disconnect();
    /** 销毁适配器，释放所有资源（定时器、BLE连接、缓冲区） */
    void release();

    // ===== 数据通道 =====
    /**
     * 接收最新的时间轴快照数据（由 PlaybackCoordinator 推送，~33ms）。
     * Phase 5 填充实现，Phase 4 空实现。
     */
    void updateSnapshot(Map<String, byte[]> channelData,
                        Map<String, Long> offsetsInSegment);
    /** seek 时清空内部缓冲区，立即用新位置数据覆盖 */
    void flush();
    /** 安全停止，强度归零 */
    void emergencyStop();

    // ===== 校验 =====
    /** 校验段数据是否属于本设备类型。Phase 5 填充，Phase 4 返回 false。 */
    boolean validateSegmentData(byte[] protobufBytes);
}
