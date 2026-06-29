package com.hypno.hypnovibe.infrastructure.ble.adapter;

/**
 * 适配器状态回调接口。
 * <p>
 * 设备适配器通过此接口向上层报告连接状态、发送统计和致命错误。
 */
public interface AdapterStatus {

    /** 连接状态枚举 */
    enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RETRYING,
        ERROR
    }

    /**
     * 连接状态变化时回调。
     *
     * @param state    新状态
     * @param deviceId 设备实例ID
     * @param detail   附加详情（如错误原因）
     */
    void onStateChanged(State state, String deviceId, String detail);

    /**
     * 发送统计（每次输出周期后回调，用于调试和监控）。
     *
     * @param deviceId       设备实例ID
     * @param writeLatencyMs 写入延迟(ms)
     * @param success        是否成功
     */
    void onCycleStats(String deviceId, long writeLatencyMs, boolean success);

    /**
     * 致命错误（如3次重连失败）。
     *
     * @param deviceId 设备实例ID
     * @param error    错误描述
     */
    void onFatalError(String deviceId, String error);
}
