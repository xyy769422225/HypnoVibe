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
    }

    public static class ChannelMappingEntry {
        private String deviceAlias;
        private String macAddress;
        private String timelineChannelId;
        private String timelineChannelName;

        public String getDeviceAlias() { return deviceAlias; }
        public void setDeviceAlias(String deviceAlias) { this.deviceAlias = deviceAlias; }

        public String getMacAddress() { return macAddress; }
        public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

        public String getTimelineChannelId() { return timelineChannelId; }
        public void setTimelineChannelId(String timelineChannelId) { this.timelineChannelId = timelineChannelId; }

        public String getTimelineChannelName() { return timelineChannelName; }
        public void setTimelineChannelName(String timelineChannelName) { this.timelineChannelName = timelineChannelName; }
    }
}
