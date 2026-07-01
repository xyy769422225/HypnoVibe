package com.hypno.hypnovibe.app.manager;

import android.util.Log;

import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;
import com.hypno.hypnovibe.domain.DeviceTypeDescriptor;
import com.hypno.hypnovibe.domain.entity.ConnectedDevice;
import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.entity.Playlist;

import java.util.*;

/**
 * 通道映射协调器。
 * - validateMapping(): 校验映射完整性，区分 physical/broadcast
 * - routeSnapshot(): 将 TimelineSnapshot 路由到正确的 Adapter（Phase 6 实现）
 */
public class ChannelMappingCoordinator {

    private static final String TAG = "ChannelMappingCoord";

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

    // ══════════════════════════════════════════════════════
    //  Phase 6: 快照路由
    // ══════════════════════════════════════════════════════

    /**
     * 将 TimelineSnapshot 按映射表路由到各设备 Adapter。
     * <p>
     * 路由规则：
     * <ul>
     *   <li>physical 映射：configChannelId → MAC → physicalChannelKey → adapter.updateKeyframe()</li>
     *   <li>broadcast 映射：configChannelId → deviceType → adapter.updateKeyframe()</li>
     * </ul>
     *
     * @param snapshot          TimelineEngine 查出的当前时刻快照
     * @param mapping           播放列表的通道映射（configChannelId → ChannelMappingEntry）
     * @param connectedByMac    MAC → ConnectedDevice（连接型设备）
     * @param broadcastByType   deviceType → DeviceProtocolAdapter（广播型设备）
     */
    public void routeSnapshot(
            TimelineEngine.TimelineSnapshot snapshot,
            Map<String, Playlist.ChannelMappingEntry> mapping,
            Map<String, ConnectedDevice> connectedByMac,
            Map<String, DeviceProtocolAdapter> broadcastByType) {

        for (Map.Entry<String, TimelineEngine.KeyframeResult> entry :
                snapshot.channelData.entrySet()) {
            String configChannelId = entry.getKey();
            TimelineEngine.KeyframeResult kf = entry.getValue();

            Playlist.ChannelMappingEntry mapEntry = mapping.get(configChannelId);
            if (mapEntry == null) continue; // 未映射，跳过

            if ("broadcast".equals(mapEntry.getMappingType())) {
                // 广播型：按设备类型查找 Adapter
                DeviceProtocolAdapter adapter = broadcastByType != null
                    ? broadcastByType.get(kf.deviceType) : null;
                if (adapter != null) {
                    adapter.updateKeyframe(configChannelId, kf);
                }
            } else {
                // 连接型：按 MAC 查找 Adapter
                String mac = mapEntry.getTargetDeviceMac();
                if (mac == null) continue;
                ConnectedDevice cd = connectedByMac != null
                    ? connectedByMac.get(mac) : null;
                if (cd == null) continue; // 设备未连接

                String physicalKey = mapEntry.getTargetPhysicalChannelKey();
                if (physicalKey == null) continue;
                cd.getAdapter().updateKeyframe(physicalKey, kf);
            }
        }
    }
}
