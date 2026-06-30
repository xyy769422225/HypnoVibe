package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.TimelineScript;
import java.util.UUID;

/**
 * 关键帧展开器 — 将编辑器中的"段+效果"展开为具体关键帧数组。
 * 采样间隔 500ms，在数据量和精度间取平衡。
 */
public class KeyframeExpander {

    private static final long SAMPLE_INTERVAL_MS = 500;

    /**
     * 展开段为关键帧数组。
     * @param channelId   目标通道 ID
     * @param deviceType  设备类型
     * @param effect      效果名 (constant/breath/pulse/rise/fall)
     * @param startMs     段起始时间
     * @param endMs       段结束时间
     * @param strength    基础强度 0-100%
     * @param freq        频率 10-1000 (仅 dglab)
     * @param label       标签
     * @return 关键帧数组（按 timeMs 升序）
     */
    public static TimelineScript.Keyframe[] expand(
            String channelId, String deviceType,
            String effect, long startMs, long endMs,
            int strength, int freq, String label) {

        int frameCount = (int) ((endMs - startMs) / SAMPLE_INTERVAL_MS) + 1;
        TimelineScript.Keyframe[] frames = new TimelineScript.Keyframe[frameCount];

        boolean isDGLab = "dglab_v3".equals(deviceType);

        for (int i = 0; i < frameCount; i++) {
            long timeMs = startMs + i * SAMPLE_INTERVAL_MS;
            long progressMs = timeMs - startMs;
            double factor = computeFactor(effect, progressMs, endMs - startMs);

            int s = (int) (strength * factor);
            int lv = (int) (s * 9.0 / 100.0); // 0-100% → 0-9

            frames[i] = new TimelineScript.Keyframe(
                timeMs, s, freq,
                isDGLab ? effectToWaveMode(effect) : null,
                isDGLab ? 0 : lv,
                label
            );
        }

        return frames;
    }

    private static double computeFactor(String effect, long progressMs, long durationMs) {
        switch (effect) {
            case "constant":
                return 1.0;

            case "breath": {
                double periodMs = 4000.0;
                double phase = (progressMs % (long) periodMs) / periodMs * 2 * Math.PI;
                return (Math.sin(phase) + 1.0) / 2.0;
            }

            case "pulse": {
                long periodMs = 1000;
                long pos = progressMs % periodMs;
                return pos < 200 ? 1.0 : 0.15;
            }

            case "rise":
                return Math.min(1.0, (double) progressMs / durationMs);

            case "fall":
                return Math.max(0.0, 1.0 - (double) progressMs / durationMs);

            default:
                return 1.0;
        }
    }

    private static String effectToWaveMode(String effect) {
        switch (effect) {
            case "constant": return "constant";
            case "breath":   return "wave";
            case "pulse":    return "pulse";
            case "rise":     return "rise";
            case "fall":     return "fall";
            default:         return "constant";
        }
    }
}
