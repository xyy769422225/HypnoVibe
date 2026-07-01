package com.hypno.hypnovibe.domain.entity;

import java.util.*;

public class Playlist {
    private String id;
    private String name;
    private String configId;
    private String playMode = "LOOP_LIST";
    private List<Track> tracks = new ArrayList<>();
    private Map<String, ChannelMappingEntry> channelMapping = new HashMap<>();
    private long createdAt;
    private long updatedAt;

    public Playlist() {}

    public Playlist(String name, String configId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.configId = configId;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getConfigId() { return configId; }
    public void setConfigId(String configId) { this.configId = configId; }

    public String getPlayMode() { return playMode; }
    public void setPlayMode(String playMode) { this.playMode = playMode; }

    public List<Track> getTracks() { return tracks; }
    public void setTracks(List<Track> tracks) { this.tracks = tracks; }

    public Map<String, ChannelMappingEntry> getChannelMapping() { return channelMapping; }
    public void setChannelMapping(Map<String, ChannelMappingEntry> channelMapping) { this.channelMapping = channelMapping; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public void touch() { this.updatedAt = System.currentTimeMillis(); }

    public static class Track {
        private String trackId;
        private String audioFilePath;
        private String audioTitle;
        private String timelineScriptPath;
        private boolean autoMatched;
        private long durationMs;
        private boolean mismatch;
        private long scriptDurationMs; // Phase 6: 关联时间轴脚本的总时长

        public Track() {}

        public Track(String audioFilePath, String audioTitle, long durationMs) {
            this.trackId = UUID.randomUUID().toString();
            this.audioFilePath = audioFilePath;
            this.audioTitle = audioTitle;
            this.durationMs = durationMs;
        }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getAudioFilePath() { return audioFilePath; }
        public void setAudioFilePath(String audioFilePath) { this.audioFilePath = audioFilePath; }

        public String getAudioTitle() { return audioTitle; }
        public void setAudioTitle(String audioTitle) { this.audioTitle = audioTitle; }

        public String getTimelineScriptPath() { return timelineScriptPath; }
        public void setTimelineScriptPath(String timelineScriptPath) { this.timelineScriptPath = timelineScriptPath; }

        public boolean isAutoMatched() { return autoMatched; }
        public void setAutoMatched(boolean autoMatched) { this.autoMatched = autoMatched; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

        public boolean hasMismatch() { return mismatch; }
        public void setHasMismatch(boolean hasMismatch) { this.mismatch = hasMismatch; }

        public long getScriptDurationMs() { return scriptDurationMs; }
        public void setScriptDurationMs(long scriptDurationMs) { this.scriptDurationMs = scriptDurationMs; }
    }

    public static class ChannelMappingEntry {
        /** 配置逻辑通道 ID（对应 DeviceConfig.ChannelDef.channelId） */
        private String configChannelId;

        /** "physical"（连接型需映射）或 "broadcast"（广播型自动生效） */
        private String mappingType;

        /** 目标设备 MAC（仅 physical） */
        private String targetDeviceMac;
        /** 目标物理通道 key，如 "A"/"B"（仅 physical） */
        private String targetPhysicalChannelKey;

        // 旧字段，保留反序列化兼容
        @Deprecated private String deviceAlias;
        @Deprecated private String macAddress;
        @Deprecated private String timelineChannelId;
        @Deprecated private String timelineChannelName;

        public ChannelMappingEntry() {}

        public static ChannelMappingEntry physical(
                String configChannelId, String targetDeviceMac,
                String targetPhysicalChannelKey) {
            ChannelMappingEntry e = new ChannelMappingEntry();
            e.configChannelId = configChannelId;
            e.mappingType = "physical";
            e.targetDeviceMac = targetDeviceMac;
            e.targetPhysicalChannelKey = targetPhysicalChannelKey;
            return e;
        }

        public static ChannelMappingEntry broadcast(String configChannelId) {
            ChannelMappingEntry e = new ChannelMappingEntry();
            e.configChannelId = configChannelId;
            e.mappingType = "broadcast";
            return e;
        }

        public String getConfigChannelId() { return configChannelId; }
        public void setConfigChannelId(String configChannelId) { this.configChannelId = configChannelId; }

        public String getMappingType() { return mappingType; }
        public void setMappingType(String mappingType) { this.mappingType = mappingType; }

        public String getTargetDeviceMac() { return targetDeviceMac; }
        public void setTargetDeviceMac(String targetDeviceMac) { this.targetDeviceMac = targetDeviceMac; }

        public String getTargetPhysicalChannelKey() { return targetPhysicalChannelKey; }
        public void setTargetPhysicalChannelKey(String targetPhysicalChannelKey) { this.targetPhysicalChannelKey = targetPhysicalChannelKey; }

        @Deprecated public String getDeviceAlias() { return deviceAlias; }
        @Deprecated public void setDeviceAlias(String deviceAlias) { this.deviceAlias = deviceAlias; }
        @Deprecated public String getMacAddress() { return macAddress; }
        @Deprecated public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
        @Deprecated public String getTimelineChannelId() { return timelineChannelId; }
        @Deprecated public void setTimelineChannelId(String timelineChannelId) { this.timelineChannelId = timelineChannelId; }
        @Deprecated public String getTimelineChannelName() { return timelineChannelName; }
        @Deprecated public void setTimelineChannelName(String timelineChannelName) { this.timelineChannelName = timelineChannelName; }
    }
}
