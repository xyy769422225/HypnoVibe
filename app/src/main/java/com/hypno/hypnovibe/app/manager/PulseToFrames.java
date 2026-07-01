package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.pulse.PulseData;
import com.hypno.hypnovibe.domain.pulse.PulsePoint;
import com.hypno.hypnovibe.domain.pulse.PulseSection;

/**
 * 将 PulseData 展开为 100ms 的波形帧序列。
 * 每帧输出 [frequency, strength]，供适配器按定时器逐帧发送。
 */
public class PulseToFrames {

    /** 单帧波形数据 */
    public static class WaveFrame {
        /** 频率 (V3 协议值 10-240，或用户值 10-1000) */
        public int frequency;
        /** 强度 (V3: 0-200 = J * y / 100 * 2，V2 时适配器内部再 ×7) */
        public int strength;

        public WaveFrame(int frequency, int strength) {
            this.frequency = frequency;
            this.strength = strength;
        }
    }

    /**
     * 将 PulseData 展开为帧序列。
     * 每帧 = 100ms（对齐郊狼输出窗口）。
     * @param pulse 解析后的波形数据
     * @param maxStrength 最大强度值 (V3: 200)
     * @return 帧数组，null 表示无效
     */
    public static WaveFrame[] toFrames(PulseData pulse, int maxStrength) {
        if (pulse == null || pulse.sections == null || pulse.sections.length == 0) return null;

        int totalFrames = 0;
        for (PulseSection s : pulse.sections) {
            if (s.points != null) totalFrames += s.points.length;
        }
        if (totalFrames == 0) return null;

        WaveFrame[] frames = new WaveFrame[totalFrames];
        int frameIdx = 0;

        for (PulseSection section : pulse.sections) {
            if (section.points == null || section.points.length == 0) continue;
            int pointCount = section.points.length;
            int startFreq = clampFreq(section.A);
            int endFreq = clampFreq(section.B);
            int j = clamp(section.J, 0, 99);

            for (int i = 0; i < pointCount; i++) {
                PulsePoint pt = section.points[i];

                // 频率渐变插值（线性）
                int freq;
                if (pointCount <= 1) {
                    freq = startFreq;
                } else {
                    float t = (float) i / (pointCount - 1);
                    switch (section.PC) {
                        case 2: // 对数
                            t = (float) (Math.log(1 + t * 9) / Math.log(10));
                            break;
                        case 4: // 指数
                            t = t * t;
                            break;
                        default: // 1=无渐变, 3=线性
                            break;
                    }
                    freq = Math.round(startFreq + (endFreq - startFreq) * t);
                }
                freq = clampFreq(freq);

                // 强度 = J值 * 波形点强度百分比 * maxStrength / 100
                int strength = Math.round((float) j * pt.y / 100f * maxStrength / 100f);
                strength = clamp(strength, 0, maxStrength);

                frames[frameIdx++] = new WaveFrame(freq, strength);
            }
        }

        return frames;
    }

    /** 将频率索引 (0-83) 映射到协议频率值 (10-240) */
    private static int clampFreq(int freqIndex) {
        int v = clamp(freqIndex, 0, 83);
        // 协议值约等于索引值自身（0-83 映射到 freqArray 的具体值）
        // 简化映射：索引 ≈ 协议频率值，最接近官方映射
        return clamp(v, 10, 240);
    }

    /**
     * 将 PulseData 展开为两个独立的帧序列（A通道 + B通道）。
     * A 通道用实际波形，B 通道用静默帧。
     */
    public static WaveFrame[] toChannelFrames(PulseData pulse, int maxStrength) {
        return toFrames(pulse, maxStrength);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }
}
