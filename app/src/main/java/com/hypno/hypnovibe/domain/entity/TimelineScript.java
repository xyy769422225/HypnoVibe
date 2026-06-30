package com.hypno.hypnovibe.domain.entity;

import java.util.*;

/**
 * 时间轴脚本 — 自包含所有关键帧参数。
 * 每个通道按 timeMs 升序排列的关键帧数组，运行时二分查找 O(log N)。
 */
public class TimelineScript {
    private String scriptId;
    private String configId;
    private long totalDurationMs;
    private List<ChannelTimeline> channels = new ArrayList<>();
    private long createdAt;
    private long updatedAt;

    public TimelineScript() {}

    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }

    public String getConfigId() { return configId; }
    public void setConfigId(String configId) { this.configId = configId; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public List<ChannelTimeline> getChannels() { return channels; }
    public void setChannels(List<ChannelTimeline> channels) { this.channels = channels; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 单个通道的完整时间轴 — 按 timeMs 升序排列的关键帧数组。
     */
    public static class ChannelTimeline {
        private String channelId;
        private String deviceType;
        private List<Keyframe> keyframes = new ArrayList<>();

        public ChannelTimeline() {}

        public ChannelTimeline(String channelId, String deviceType) {
            this.channelId = channelId;
            this.deviceType = deviceType;
        }

        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }

        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

        public List<Keyframe> getKeyframes() { return keyframes; }
        public void setKeyframes(List<Keyframe> keyframes) { this.keyframes = keyframes; }
    }

    /**
     * 单个关键帧 — 自包含所有运行参数。
     */
    public static class Keyframe {
        private long timeMs;
        private int strength;       // 0-100%, 运行时映射到设备范围
        private int freq;           // 10-1000, 仅 dglab
        private String waveMode;    // constant/pulse/rise/fall/wave, 仅 dglab
        private int level;          // 0-9, 仅 love_spouse
        private String label;       // 标签, 编辑器用

        public Keyframe() {}

        public Keyframe(long timeMs, int strength, int freq, String waveMode, int level, String label) {
            this.timeMs = timeMs;
            this.strength = strength;
            this.freq = freq;
            this.waveMode = waveMode;
            this.level = level;
            this.label = label;
        }

        public long getTimeMs() { return timeMs; }
        public void setTimeMs(long timeMs) { this.timeMs = timeMs; }

        public int getStrength() { return strength; }
        public void setStrength(int strength) { this.strength = strength; }

        public int getFreq() { return freq; }
        public void setFreq(int freq) { this.freq = freq; }

        public String getWaveMode() { return waveMode; }
        public void setWaveMode(String waveMode) { this.waveMode = waveMode; }

        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
}
