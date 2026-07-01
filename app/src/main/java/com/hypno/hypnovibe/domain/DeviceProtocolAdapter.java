package com.hypno.hypnovibe.domain;

import android.content.Context;
import com.hypno.hypnovibe.app.manager.TimelineEngine;
import java.util.Map;

/**
 * 设备协议适配器统一接口。
 * 每种设备类型实现此接口，完全封装其通信协议、数据格式、时序控制。
 * 上层只通过此接口与设备交互，不感知协议细节。
 */
public interface DeviceProtocolAdapter {

    // ===== 标识 =====
    /** 设备类型标识（如 "dglab_v3", "dglab_v2", "love_spouse"） */
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

    // ===== 数据通道（Phase 6）=====

    /**
     * 接收单个物理通道的关键帧数据（由 PlaybackCoordinator 推送，~33ms）。
     * Adapter 内部缓存此数据，在自身定时器中编码发送。
     */
    void updateKeyframe(String physicalChannelKey, TimelineEngine.KeyframeResult kf);

    /** seek / 暂停时清空内部缓存，Adapter 发送静默帧 */
    void flush();

    /** 安全停止，强度归零 */
    void emergencyStop();

    // ===== 校验 =====
    /** 校验段数据是否属于本设备类型。Phase 5 填充，Phase 4 返回 false。 */
    boolean validateSegmentData(byte[] protobufBytes);

    // ===== 废弃（保留兼容）=====
    /** @deprecated 使用 {@link #updateKeyframe} 替代 */
    @Deprecated
    default void updateSnapshot(Map<String, byte[]> channelData,
                                Map<String, Long> offsetsInSegment) {}
}
