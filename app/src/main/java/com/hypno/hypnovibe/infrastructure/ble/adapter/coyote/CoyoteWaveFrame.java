package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

/**
 * 郊狼波形帧数据模型。
 * <p>
 * 表示单个波形采样点，包含频率和强度两个维度。
 * package-private，仅郊狼子包内部使用。
 */
final class CoyoteWaveFrame {

    /** 帧内相对时间(ms) */
    final int timeMs;

    /** 波形频率，用户值 10-1000ms（发送前由 FrequencyConverter 换算为协议值） */
    final int frequency;

    /** 波形强度，V3:0-100, V2:0-20(映射到Z值) */
    final int strength;

    CoyoteWaveFrame(int timeMs, int frequency, int strength) {
        this.timeMs = timeMs;
        this.frequency = frequency;
        this.strength = strength;
    }

    /** 创建频率/强度均为0的静默帧 */
    static CoyoteWaveFrame silent(int timeMs) {
        return new CoyoteWaveFrame(timeMs, 0, 0);
    }

    @Override
    public String toString() {
        return "WaveFrame{t=" + timeMs + "ms, f=" + frequency + ", s=" + strength + "}";
    }
}
