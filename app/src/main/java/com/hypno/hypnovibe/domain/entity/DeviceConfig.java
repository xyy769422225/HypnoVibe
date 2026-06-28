package com.hypno.hypnovibe.domain.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeviceConfig {
    private String id;
    private String name;
    private List<ChannelDef> channels;
    private long createdAt;
    private long updatedAt;

    public DeviceConfig() {}

    public DeviceConfig(String name, List<ChannelDef> channels) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.channels = new ArrayList<>(channels);
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ChannelDef> getChannels() { return channels; }
    public void setChannels(List<ChannelDef> channels) { this.channels = channels; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public void touch() { this.updatedAt = System.currentTimeMillis(); }

    public static class ChannelDef {
        private String channelId;
        private String channelName;
        private String deviceType;
        private int minStrength;
        private int maxStrength;
        private int defaultStrength;

        public ChannelDef() {}

        public ChannelDef(String channelName, String deviceType, int min, int max) {
            this.channelId = UUID.randomUUID().toString();
            this.channelName = channelName;
            this.deviceType = deviceType;
            this.minStrength = min;
            this.maxStrength = max;
            this.defaultStrength = min;
        }

        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }

        public String getChannelName() { return channelName; }
        public void setChannelName(String channelName) { this.channelName = channelName; }

        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

        public int getMinStrength() { return minStrength; }
        public void setMinStrength(int minStrength) { this.minStrength = minStrength; }

        public int getMaxStrength() { return maxStrength; }
        public void setMaxStrength(int maxStrength) { this.maxStrength = maxStrength; }

        public int getDefaultStrength() { return defaultStrength; }
        public void setDefaultStrength(int defaultStrength) { this.defaultStrength = defaultStrength; }
    }
}
