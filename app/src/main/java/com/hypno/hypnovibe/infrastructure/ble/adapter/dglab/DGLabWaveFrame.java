package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

/**
 * DG-LAB 波形帧数据模型。
 * <p>
 * 表示单个波形采样点，包含频率和强度两个维度。
 * package-private，仅 dglab 子包内部使用。
 */
final class DGLabWaveFrame {

    /** 帧内相对时间(ms) */
    final int timeMs;

    /** 波形频率，用户值 10-1000ms（发送前由 FrequencyConverter 换算为协议值） */
    final int frequency;

    /** 波形强度，V3:0-100, V2:0-20(映射到Z值) */
    final int strength;

    DGLabWaveFrame(int timeMs, int frequency, int strength) {
        this.timeMs = timeMs;
        this.frequency = frequency;
        this.strength = strength;
    }

    /** 创建频率/强度均为0的静默帧 */
    static DGLabWaveFrame silent(int timeMs) {
        return new DGLabWaveFrame(timeMs, 0, 0);
    }

    @Override
    public String toString() {
        return "WaveFrame{t=" + timeMs + "ms, f=" + frequency + ", s=" + strength + "}";
    }
}
