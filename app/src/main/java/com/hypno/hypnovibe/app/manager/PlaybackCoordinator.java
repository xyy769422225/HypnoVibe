package com.hypno.hypnovibe.app.manager;

import android.util.Log;

import com.hypno.hypnovibe.app.viewmodel.PlaySessionVM;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;
import com.hypno.hypnovibe.domain.entity.ConnectedDevice;
import com.hypno.hypnovibe.domain.entity.Playlist;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 播放协调器 — 作为 PositionListener 注入 PlaySessionVM，打通完整播放链路：
 * <pre>
 *   positionPoller (33ms)
 *     → onPositionUpdate(posMs)
 *       → TimelineEngine.query(posMs)
 *         → ChannelMappingCoordinator.routeSnapshot()
 *           → Adapter.updateKeyframe()  …  各设备独立定时器编码发送
 * </pre>
 *
 * 线程安全：使用 ConcurrentHashMap + volatile 保证多线程访问安全。
 */
public class PlaybackCoordinator implements PlaySessionVM.PositionListener {

    private static final String TAG = "PlaybackCoordinator";

    private volatile TimelineEngine timelineEngine;
    private final ChannelMappingCoordinator mappingCoordinator;

    // MAC → ConnectedDevice（设备连接/断开时增减）
    private final Map<String, ConnectedDevice> connectedDeviceByMac = new ConcurrentHashMap<>();
    // deviceId → DeviceProtocolAdapter（所有设备的 adapter 引用）
    private final Map<String, DeviceProtocolAdapter> adapterById = new ConcurrentHashMap<>();
    // deviceType → DeviceProtocolAdapter（广播型设备快速查找）
    private final Map<String, DeviceProtocolAdapter> broadcastByType = new ConcurrentHashMap<>();

    // 当前播放列表的通道映射（每轮播放前更新）
    private volatile Map<String, Playlist.ChannelMappingEntry> currentMapping;

    public PlaybackCoordinator(ChannelMappingCoordinator mappingCoordinator) {
        this.mappingCoordinator = mappingCoordinator;
    }

    /** 更新当前持有的 TimelineEngine（预加载完成后调用） */
    public void setTimelineEngine(TimelineEngine engine) {
        this.timelineEngine = engine;
    }

    /** 更新当前通道映射（切换播放列表/通道映射变更时调用） */
    public void setChannelMapping(Map<String, Playlist.ChannelMappingEntry> mapping) {
        this.currentMapping = mapping;
    }

    // ══════════════════════════════════════════════════════
    //  PositionListener — 33ms 定时器回调
    // ══════════════════════════════════════════════════════

    @Override
    public void onPositionUpdate(long positionMs) {
        TimelineEngine engine = timelineEngine;
        Map<String, Playlist.ChannelMappingEntry> mapping = currentMapping;
        if (engine == null || mapping == null || mapping.isEmpty()) return;

        // 1. 查询当前时刻快照
        TimelineEngine.TimelineSnapshot snapshot = engine.query(positionMs);
        if (snapshot.isEmpty()) return;

        // 2. 路由快照到各设备 Adapter
        mappingCoordinator.routeSnapshot(snapshot, mapping,
                connectedDeviceByMac, broadcastByType);
    }

    // ══════════════════════════════════════════════════════
    //  设备生命周期
    // ══════════════════════════════════════════════════════

    /** BLE 连接成功时注册设备 */
    public void registerDevice(ConnectedDevice device) {
        if (device == null) return;
        String mac = device.getMac();
        if (mac != null && !mac.isEmpty()) {
            connectedDeviceByMac.put(mac, device);
        }
        adapterById.put(device.getDeviceId(), device.getAdapter());
        // 广播型设备按 deviceType 索引（如 love_spouse）
        String deviceType = device.getAdapter().getDeviceType();
        broadcastByType.put(deviceType, device.getAdapter());
        Log.d(TAG, "registerDevice: " + mac + " type=" + deviceType);
    }

    /** BLE 断开时注销设备 */
    public void unregisterDevice(String mac) {
        ConnectedDevice removed = connectedDeviceByMac.remove(mac);
        if (removed != null) {
            adapterById.remove(removed.getDeviceId());
            String deviceType = removed.getAdapter().getDeviceType();
            broadcastByType.remove(deviceType);
            Log.d(TAG, "unregisterDevice: " + mac + " type=" + deviceType);
        }
    }

    /** 获取已注册设备数量 */
    public int getRegisteredDeviceCount() {
        return connectedDeviceByMac.size();
    }

    // ══════════════════════════════════════════════════════
    //  seek / 停止 联动
    // ══════════════════════════════════════════════════════

    /** seek 时刷新所有 Adapter 内部缓冲区，并在下一个 tick 用新位置数据覆盖 */
    public void seekNotify() {
        for (DeviceProtocolAdapter adapter : adapterById.values()) {
            try { adapter.flush(); } catch (Exception e) {
                Log.e(TAG, "seekNotify flush failed: " + adapter.getDeviceId(), e);
            }
        }
    }

    /** 播放停止/暂停 → 所有设备紧急归零 */
    public void stopAll() {
        for (DeviceProtocolAdapter adapter : adapterById.values()) {
            try { adapter.emergencyStop(); } catch (Exception e) {
                Log.e(TAG, "stopAll emergencyStop failed: " + adapter.getDeviceId(), e);
            }
        }
    }

    /** 清除所有已注册设备 */
    public void clearAll() {
        connectedDeviceByMac.clear();
        adapterById.clear();
        broadcastByType.clear();
        currentMapping = null;
    }
}
