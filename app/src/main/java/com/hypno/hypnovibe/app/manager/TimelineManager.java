package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.TimelineScript;
import com.hypno.hypnovibe.infrastructure.io.JsonFileRepository;

import java.util.*;

/**
 * 时间轴脚本管理器 — 保存/加载/展开效果。
 */
public class TimelineManager {

    private final JsonFileRepository repo;

    public TimelineManager(JsonFileRepository repo) {
        this.repo = repo;
    }

    public TimelineScript load(String scriptId) {
        return repo.findById(scriptId, TimelineScript.class).orElse(null);
    }

    public void save(TimelineScript script) {
        if (script.getScriptId() == null) {
            script.setScriptId(UUID.randomUUID().toString());
        }
        long now = System.currentTimeMillis();
        if (script.getCreatedAt() == 0) script.setCreatedAt(now);
        script.setUpdatedAt(now);

        // 对每个通道的关键帧按 timeMs 排序
        for (TimelineScript.ChannelTimeline ct : script.getChannels()) {
            ct.getKeyframes().sort(Comparator.comparingLong(TimelineScript.Keyframe::getTimeMs));
        }

        repo.save(script.getScriptId(), script);
    }

    public void delete(String scriptId) {
        repo.delete(scriptId);
    }
}
