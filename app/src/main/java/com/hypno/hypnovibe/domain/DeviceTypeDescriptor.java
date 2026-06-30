package com.hypno.hypnovibe.domain;

import java.util.Collections;
import java.util.List;

/**
 * 设备类型的元信息描述符。
 * 每种设备类型在系统启动时注册一个不可变实例。
 * UI 和业务逻辑通过此描述符动态适配，而非硬编码。
 */
public class DeviceTypeDescriptor {

    private final String deviceType;
    private final String displayName;
    private final List<PhysicalChannelDef> physicalChannels;
    private final int strengthMin;
    private final int strengthMax;

    public enum ConnectionModel { CONNECTION, BROADCAST }
    private final ConnectionModel connectionModel;
    private final boolean requiresMapping;
    private final int safeStrength;

    public DeviceTypeDescriptor(
            String deviceType, String displayName,
            List<PhysicalChannelDef> physicalChannels,
            int strengthMin, int strengthMax,
            ConnectionModel connectionModel,
            boolean requiresMapping, int safeStrength) {
        this.deviceType = deviceType;
        this.displayName = displayName;
        this.physicalChannels = Collections.unmodifiableList(physicalChannels);
        this.strengthMin = strengthMin;
        this.strengthMax = strengthMax;
        this.connectionModel = connectionModel;
        this.requiresMapping = requiresMapping;
        this.safeStrength = safeStrength;
    }

    public String getDeviceType() { return deviceType; }
    public String getDisplayName() { return displayName; }
    public List<PhysicalChannelDef> getPhysicalChannels() { return physicalChannels; }
    public int getStrengthMin() { return strengthMin; }
    public int getStrengthMax() { return strengthMax; }
    public ConnectionModel getConnectionModel() { return connectionModel; }
    public boolean requiresMapping() { return requiresMapping; }
    public int getSafeStrength() { return safeStrength; }
    public int getPhysicalChannelCount() { return physicalChannels.size(); }

    public static class PhysicalChannelDef {
        private final String channelKey;
        private final String displayName;

        public PhysicalChannelDef(String channelKey, String displayName) {
            this.channelKey = channelKey;
            this.displayName = displayName;
        }

        public String getChannelKey() { return channelKey; }
        public String getDisplayName() { return displayName; }
    }
}
