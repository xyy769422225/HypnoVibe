package com.hypno.hypnovibe.domain.entity;

import java.util.*;

/**
 * @deprecated 由 Playlist.ChannelMappingEntry 替代（Phase 5 通道体系重构）
 */
@Deprecated
public class ChannelMapping {
    private String physicalChannelKey;
    private String deviceAlias;
    private String macAddress;
    private String timelineChannelId;
    private String timelineChannelName;

    public ChannelMapping() {}

    public String getPhysicalChannelKey() { return physicalChannelKey; }
    public void setPhysicalChannelKey(String physicalChannelKey) { this.physicalChannelKey = physicalChannelKey; }

    public String getDeviceAlias() { return deviceAlias; }
    public void setDeviceAlias(String deviceAlias) { this.deviceAlias = deviceAlias; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public String getTimelineChannelId() { return timelineChannelId; }
    public void setTimelineChannelId(String timelineChannelId) { this.timelineChannelId = timelineChannelId; }

    public String getTimelineChannelName() { return timelineChannelName; }
    public void setTimelineChannelName(String timelineChannelName) { this.timelineChannelName = timelineChannelName; }
}
