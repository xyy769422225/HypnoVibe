package com.hypno.hypnovibe.domain;

/**
 * 适配器状态回调接口。由上层（ViewModel/Coordinator）实现，适配器回调通知。
 */
public interface AdapterStatus {

    enum State { DISCONNECTED, CONNECTING, CONNECTED, RETRYING, ERROR }

    /** 连接状态变化时回调 */
    void onStateChanged(State state, String deviceId, String detail);

    /** 发送统计（每次输出周期后回调，用于调试和监控） */
    void onCycleStats(String deviceId, long writeLatencyMs, boolean success);

    /** 致命错误（如3次重连失败） */
    void onFatalError(String deviceId, String error);
}
