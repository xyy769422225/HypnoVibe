package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.TimelineScript;
import java.util.*;

/**
 * 时间轴运行时引擎 — 预加载阶段构建二分查找索引。
 * Phase 6: query() + TimelineSnapshot + KeyframeResult + binarySearchFloor()。
 */
public class TimelineEngine {

    /** channelId → 预计算的时间索引 + 参数数组 */
    private final Map<String, ChannelRuntime> runtimeMap = new LinkedHashMap<>();

    /**
     * 注册一个通道时间轴，构建预计算索引。
     * 将 Keyframe 列表展开为并行数组，供 O(log N) 二分查找。
     */
    public void registerChannel(TimelineScript.ChannelTimeline ct) {
        List<TimelineScript.Keyframe> kfs = ct.getKeyframes();
        if (kfs == null || kfs.isEmpty()) return;

        int n = kfs.size();
        long[] timeIndex = new long[n];
        int[] strengthIndex = new int[n];
        int[] freqIndex = new int[n];
        String[] waveModeIndex = new String[n];
        int[] levelIndex = new int[n];

        for (int i = 0; i < n; i++) {
            TimelineScript.Keyframe kf = kfs.get(i);
            timeIndex[i] = kf.getTimeMs();
            strengthIndex[i] = kf.getStrength();
            freqIndex[i] = kf.getFreq();
            waveModeIndex[i] = kf.getWaveMode();
            levelIndex[i] = kf.getLevel();
        }

        ChannelRuntime rt = new ChannelRuntime();
        rt.timeIndex = timeIndex;
        rt.strengthIndex = strengthIndex;
        rt.freqIndex = freqIndex;
        rt.waveModeIndex = waveModeIndex;
        rt.levelIndex = levelIndex;
        rt.deviceType = ct.getDeviceType();
        runtimeMap.put(ct.getChannelId(), rt);
    }

    /** 获取已注册的通道数 */
    public int getChannelCount() {
        return runtimeMap.size();
    }

    /** 清除所有已注册通道 */
    public void clear() {
        runtimeMap.clear();
    }

    // ══════════════════════════════════════════════════════
    //  Phase 6: 时间轴查询
    // ══════════════════════════════════════════════════════

    /**
     * 查询指定时刻各通道的目标关键帧。
     * 对每个已注册通道执行二分查找，返回 TimelineSnapshot。
     *
     * @param positionMs 当前播放位置（毫秒）
     * @return 当前时刻的快照。若所有通道均为静默帧，返回空快照。
     */
    public TimelineSnapshot query(long positionMs) {
        TimelineSnapshot snapshot = new TimelineSnapshot();
        for (Map.Entry<String, ChannelRuntime> entry : runtimeMap.entrySet()) {
            String channelId = entry.getKey();
            ChannelRuntime rt = entry.getValue();
            int idx = rt.binarySearchFloor(positionMs);
            if (idx < 0) continue; // 当前时刻无关键帧 → 静默

            KeyframeResult kf = new KeyframeResult();
            kf.timeMs = rt.timeIndex[idx];
            kf.strength = rt.strengthIndex[idx];
            kf.freq = rt.freqIndex[idx];
            kf.waveMode = rt.waveModeIndex[idx];
            kf.level = rt.levelIndex[idx];
            kf.deviceType = rt.deviceType;
            snapshot.channelData.put(channelId, kf);
        }
        return snapshot;
    }

    // ══════════════════════════════════════════════════════
    //  内部类：快照 & 关键帧结果
    // ══════════════════════════════════════════════════════

    /** 某一时刻的时间轴快照 — channelId → KeyframeResult */
    public static class TimelineSnapshot {
        /** channelId → 该时刻匹配到的关键帧参数 */
        public final Map<String, KeyframeResult> channelData = new LinkedHashMap<>();

        public boolean isEmpty() {
            return channelData.isEmpty();
        }
    }

    /** 单个通道在某一时刻的关键帧数据 */
    public static class KeyframeResult {
        /** 匹配到的关键帧时间（ms） */
        public long timeMs;
        /** 强度 0-100% */
        public int strength;
        /** 频率 10-1000（仅 dglab 设备使用） */
        public int freq;
        /** 波形模式（仅 dglab 设备使用，如 "breath", "pulse" 等） */
        public String waveMode;
        /** 振动等级 0-9（仅 love_spouse 设备使用） */
        public int level;
        /** 所属设备类型（如 "dglab_v3", "love_spouse"） */
        public String deviceType;
    }

    // ══════════════════════════════════════════════════════
    //  预计算索引结构
    // ══════════════════════════════════════════════════════

    public static class ChannelRuntime {
        long[] timeIndex;         // timeMs 升序数组
        int[] strengthIndex;      // 平行强度数组
        int[] freqIndex;          // 平行频率数组
        String[] waveModeIndex;   // 平行波形模式数组
        int[] levelIndex;         // 平行振动等级数组（love_spouse）
        String deviceType;

        /**
         * 二分查找：返回 last timeMs <= positionMs 的索引。
         * 若所有关键帧 timeMs > positionMs，返回 -1（静默帧）。
         */
        int binarySearchFloor(long positionMs) {
            int lo = 0, hi = timeIndex.length - 1;
            int result = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1; // 无符号右移防溢出
                if (timeIndex[mid] <= positionMs) {
                    result = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return result;
        }
    }
}
