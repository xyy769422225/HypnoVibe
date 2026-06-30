package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import com.hypno.hypnovibe.app.manager.KeyframeExpander;
import com.hypno.hypnovibe.app.manager.TimelineManager;
import com.hypno.hypnovibe.domain.DeviceTypeDescriptor;
import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.entity.TimelineScript;
import com.hypno.hypnovibe.infrastructure.io.JsonFileRepository;

import java.util.*;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * 时间轴编辑器 ViewModel（最简模式）。
 */
public class TimelineEditorVM extends AndroidViewModel {

    private final TimelineManager timelineManager;
    private final ConfigVM configVM;
    private final MutableStateFlow<TimelineScript> currentScript;
    private final MutableStateFlow<DeviceConfig> selectedConfig;
    private final MutableStateFlow<Boolean> dirty;

    public TimelineEditorVM(Application app) {
        super(app);
        JsonFileRepository jsonRepo = new JsonFileRepository(app, "timelines");
        this.timelineManager = new TimelineManager(jsonRepo);
        this.configVM = new ConfigVM(app);
        this.currentScript = StateFlowKt.MutableStateFlow(null);
        this.selectedConfig = StateFlowKt.MutableStateFlow(null);
        this.dirty = StateFlowKt.MutableStateFlow(false);
    }

    public StateFlow<TimelineScript> getCurrentScript() { return currentScript; }
    public StateFlow<DeviceConfig> getSelectedConfig() { return selectedConfig; }
    public StateFlow<Boolean> getDirty() { return dirty; }

    public ConfigVM getConfigVM() { return configVM; }
    public List<DeviceTypeDescriptor> getDeviceTypes() { return configVM.getConfigurableDeviceTypes(); }
    public DeviceTypeDescriptor getDeviceTypeInfo(String type) { return configVM.getDeviceTypeInfo(type); }

    /** 选择配置 → 初始化脚本骨架（每通道一个空 ChannelTimeline） */
    public void selectConfig(DeviceConfig config) {
        selectedConfig.setValue(config);
        TimelineScript script = new TimelineScript();
        script.setConfigId(config.getId());
        script.setChannels(new ArrayList<>());
        for (DeviceConfig.ChannelDef ch : config.getChannels()) {
            script.getChannels().add(new TimelineScript.ChannelTimeline(ch.getChannelId(), ch.getDeviceType()));
        }
        currentScript.setValue(script);
        dirty.setValue(false);
    }

    /** 加载已有脚本 */
    public void loadScript(String scriptId) {
        TimelineScript script = timelineManager.load(scriptId);
        if (script != null) {
            currentScript.setValue(script);
            dirty.setValue(false);
            // 尝试加载对应配置
            configVM.loadConfigs();
            for (DeviceConfig dc : configVM.getConfigs().getValue()) {
                if (dc.getId().equals(script.getConfigId())) {
                    selectedConfig.setValue(dc);
                    break;
                }
            }
        }
    }

    /** 为指定通道添加一段（展开为关键帧） */
    public void addSegment(String channelId, String effect,
                           long startMs, long endMs,
                           int strength, int freq, String label) {
        TimelineScript script = currentScript.getValue();
        if (script == null) return;

        for (TimelineScript.ChannelTimeline ct : script.getChannels()) {
            if (ct.getChannelId().equals(channelId)) {
                TimelineScript.Keyframe[] frames = KeyframeExpander.expand(
                    channelId, ct.getDeviceType(),
                    effect, startMs, endMs, strength, freq, label
                );
                ct.getKeyframes().addAll(Arrays.asList(frames));
                ct.getKeyframes().sort(Comparator.comparingLong(TimelineScript.Keyframe::getTimeMs));
                break;
            }
        }
        script.setUpdatedAt(System.currentTimeMillis());
        currentScript.setValue(script);
        dirty.setValue(true);
    }

    /** 删除通道的所有关键帧（清空该通道的时间轴） */
    public void clearChannel(String channelId) {
        TimelineScript script = currentScript.getValue();
        if (script == null) return;
        for (TimelineScript.ChannelTimeline ct : script.getChannels()) {
            if (ct.getChannelId().equals(channelId)) {
                ct.getKeyframes().clear();
                script.setUpdatedAt(System.currentTimeMillis());
                currentScript.setValue(script);
                dirty.setValue(true);
                break;
            }
        }
    }

    /** 保存脚本 */
    public void saveScript() {
        TimelineScript script = currentScript.getValue();
        if (script == null) return;
        script.setTotalDurationMs(computeTotalDuration(script));
        timelineManager.save(script);
        dirty.setValue(false);
    }

    /** 获取当前脚本的文件 ID（用于路径匹配） */
    public String getScriptId() {
        TimelineScript s = currentScript.getValue();
        return s != null ? s.getScriptId() : null;
    }

    private long computeTotalDuration(TimelineScript script) {
        long max = 0;
        for (TimelineScript.ChannelTimeline ct : script.getChannels()) {
            for (TimelineScript.Keyframe kf : ct.getKeyframes()) {
                if (kf.getTimeMs() > max) max = kf.getTimeMs();
            }
        }
        return max;
    }
}
