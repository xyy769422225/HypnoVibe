package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.TimelineScript;
import java.util.*;

/**
 * 时间轴运行时引擎 — 预加载阶段构建二分查找索引。
 * Phase 5: 实现 registerChannel（构建索引），query() 留待 Phase 6 接入。
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

    // ── 预计算索引结构 ──

    public static class ChannelRuntime {
        long[] timeIndex;         // timeMs 升序数组
        int[] strengthIndex;      // 平行强度数组
        int[] freqIndex;          // 平行频率数组
        String[] waveModeIndex;   // 平行波形模式数组
        int[] levelIndex;         // 平行振动等级数组（love_spouse）
        String deviceType;
    }
}
