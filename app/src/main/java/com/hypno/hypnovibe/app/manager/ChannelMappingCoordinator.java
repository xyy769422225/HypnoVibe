package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.DeviceTypeDescriptor;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;
import com.hypno.hypnovibe.domain.entity.ConnectedDevice;
import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.entity.Playlist;

import java.util.*;

/**
 * 通道映射协调器。
 * - validateMapping(): 校验映射完整性，区分 physical/broadcast
 * - routeSnapshot(): 将 TimelineSnapshot 路由到正确的 Adapter（Phase 6 使用）
 */
public class ChannelMappingCoordinator {

    private final DeviceTypeRegistry typeRegistry;

    public ChannelMappingCoordinator(DeviceTypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    /**
     * 校验播放列表的通道映射是否完整。
     * @return 缺失映射的通道名称列表。空列表表示所有通道映射完整。
     */
    public List<String> validateMapping(
            List<DeviceConfig.ChannelDef> configChannels,
            Map<String, Playlist.ChannelMappingEntry> mapping,
            Collection<ConnectedDevice> connectedDevices) {

        List<String> unmapped = new ArrayList<>();

        for (DeviceConfig.ChannelDef ch : configChannels) {
            DeviceTypeDescriptor desc = typeRegistry.getTypeInfo(ch.getDeviceType());
            if (desc == null) {
                unmapped.add(ch.getChannelName() + " (未知设备类型)");
                continue;
            }

            if (!desc.requiresMapping()) {
                continue; // 广播型无需映射
            }

            Playlist.ChannelMappingEntry entry = mapping.get(ch.getChannelId());
            if (entry == null || entry.getTargetDeviceMac() == null) {
                unmapped.add(ch.getChannelName() + " (需要映射到 " + desc.getDisplayName() + ")");
                continue;
            }

            boolean deviceConnected = false;
            for (ConnectedDevice cd : connectedDevices) {
                if (cd.getMac().equals(entry.getTargetDeviceMac())) {
                    deviceConnected = true;
                    break;
                }
            }
            if (!deviceConnected) {
                unmapped.add(ch.getChannelName() + " (目标设备未连接)");
            }
        }

        return unmapped;
    }
}
