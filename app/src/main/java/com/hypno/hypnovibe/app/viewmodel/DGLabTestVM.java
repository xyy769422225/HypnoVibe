package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;

import com.hypno.hypnovibe.app.manager.BuiltinWaveforms;
import com.hypno.hypnovibe.app.manager.PulseFileParser;
import com.hypno.hypnovibe.app.manager.PulseToFrames;
import com.hypno.hypnovibe.domain.pulse.PulseData;
import com.hypno.hypnovibe.infrastructure.ble.adapter.dglab.DGLabController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * DG-LAB 测试面板 ViewModel。
 * 支持手动强度调节 + 波形播放模式，A/B 通道独立控制。
 */
public class DGLabTestVM extends AndroidViewModel {
    private static final int MAX_STRENGTH = 200;

    /** 波形条目（内置 + 导入） */
    public static class WaveEntry {
        public final String name;
        public final PulseData data;

        public WaveEntry(String name, PulseData data) {
            this.name = name;
            this.data = data;
        }
    }

    private DGLabController controller;

    // === 手动模式状态 ===
    private final MutableStateFlow<Integer> targetStrengthA = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Integer> targetStrengthB = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Integer> deviceStrengthA = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Integer> deviceStrengthB = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Boolean> safetyOn = StateFlowKt.MutableStateFlow(true);
    private final MutableStateFlow<Boolean> isConnected = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<String> deviceName = StateFlowKt.MutableStateFlow((String) null);

    // === 通道开关（独立控制） ===
    private final MutableStateFlow<Boolean> channelAEnabled = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<Boolean> channelBEnabled = StateFlowKt.MutableStateFlow(false);

    // === 波形播放模式 ===
    private final MutableStateFlow<List<WaveEntry>> waveforms = StateFlowKt.MutableStateFlow(new ArrayList<>());
    private final MutableStateFlow<Integer> selectedWaveIndexA = StateFlowKt.MutableStateFlow(-1);
    private final MutableStateFlow<Integer> selectedWaveIndexB = StateFlowKt.MutableStateFlow(-1);
    private final MutableStateFlow<Boolean> isPlayingA = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<Boolean> isPlayingB = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<Float> progressA = StateFlowKt.MutableStateFlow(0f);
    private final MutableStateFlow<Float> progressB = StateFlowKt.MutableStateFlow(0f);

    // 波形播放器（独立 timer）
    private ScheduledExecutorService waveTimerA;
    private ScheduledExecutorService waveTimerB;
    private PulseToFrames.WaveFrame[] framesA;
    private PulseToFrames.WaveFrame[] framesB;
    private final AtomicInteger frameIndexA = new AtomicInteger(0);
    private final AtomicInteger frameIndexB = new AtomicInteger(0);

    public DGLabTestVM(Application app) {
        super(app);
        loadBuiltinWaveforms();
    }

    // ── Getters ──
    public StateFlow<Integer> getTargetStrengthA() { return targetStrengthA; }
    public StateFlow<Integer> getTargetStrengthB() { return targetStrengthB; }
    public StateFlow<Integer> getDeviceStrengthA() { return deviceStrengthA; }
    public StateFlow<Integer> getDeviceStrengthB() { return deviceStrengthB; }
    public StateFlow<Boolean> getSafetyOn() { return safetyOn; }
    public StateFlow<Boolean> getIsConnected() { return isConnected; }
    public StateFlow<String> getDeviceName() { return deviceName; }
    public StateFlow<Boolean> getChannelAEnabled() { return channelAEnabled; }
    public StateFlow<Boolean> getChannelBEnabled() { return channelBEnabled; }
    public StateFlow<List<WaveEntry>> getWaveforms() { return waveforms; }
    public StateFlow<Integer> getSelectedWaveIndexA() { return selectedWaveIndexA; }
    public StateFlow<Integer> getSelectedWaveIndexB() { return selectedWaveIndexB; }
    public StateFlow<Boolean> getIsPlayingA() { return isPlayingA; }
    public StateFlow<Boolean> getIsPlayingB() { return isPlayingB; }
    public StateFlow<Float> getProgressA() { return progressA; }
    public StateFlow<Float> getProgressB() { return progressB; }

    // ── Controller ──

    public void setController(DGLabController controller, String name) {
        this.controller = controller;
        this.deviceName.setValue(name);
        controller.setDGLabListener(new DGLabController.DGLabListener() {
            @Override
            public void onStrengthFeedback(int a, int b) {
                deviceStrengthA.setValue(a);
                deviceStrengthB.setValue(b);
            }
            @Override public void onConnected() {
                isConnected.setValue(true);
                safetyOn.setValue(controller.isSafetyOn());
            }
            @Override public void onDisconnected() {
                isConnected.setValue(false);
                safetyOn.setValue(true);
                stopWaveformA();
                stopWaveformB();
                targetStrengthA.setValue(0);
                targetStrengthB.setValue(0);
            }
        });
        isConnected.setValue(controller.isConnected());
        safetyOn.setValue(controller.isSafetyOn());
        deviceStrengthA.setValue(controller.getDeviceStrengthA());
        deviceStrengthB.setValue(controller.getDeviceStrengthB());
    }

    // ── 手动强度 ──
    public void increaseChannelA() { setStrengthA(targetStrengthA.getValue() + 1); }
    public void decreaseChannelA() { setStrengthA(targetStrengthA.getValue() - 1); }
    public void increaseChannelB() { setStrengthB(targetStrengthB.getValue() + 1); }
    public void decreaseChannelB() { setStrengthB(targetStrengthB.getValue() - 1); }

    private void setStrengthA(int v) {
        v = clamp(v, 0, MAX_STRENGTH);
        targetStrengthA.setValue(v);
        if (controller != null) controller.setManualStrength(v, targetStrengthB.getValue());
    }
    private void setStrengthB(int v) {
        v = clamp(v, 0, MAX_STRENGTH);
        targetStrengthB.setValue(v);
        if (controller != null) controller.setManualStrength(targetStrengthA.getValue(), v);
    }

    // ── 通道开关 ──
    public void toggleChannelA() {
        boolean newVal = !channelAEnabled.getValue();
        channelAEnabled.setValue(newVal);
        if (controller != null) {
            if (newVal) controller.setManualStrength(targetStrengthA.getValue(), targetStrengthB.getValue());
            else { stopWaveformA(); controller.setManualStrength(0, targetStrengthB.getValue()); }
        }
    }
    public void toggleChannelB() {
        boolean newVal = !channelBEnabled.getValue();
        channelBEnabled.setValue(newVal);
        if (controller != null) {
            if (newVal) controller.setManualStrength(targetStrengthA.getValue(), targetStrengthB.getValue());
            else { stopWaveformB(); controller.setManualStrength(targetStrengthA.getValue(), 0); }
        }
    }

    // ── 安全 ──
    public void unlockSafety() { if (controller != null) controller.unlockSafety(); safetyOn.setValue(false); }
    public void emergencyStop() {
        stopWaveformA();
        stopWaveformB();
        if (controller != null) { controller.sendSilentFrame(); controller.emergencyStop(); }
        safetyOn.setValue(true);
        targetStrengthA.setValue(0);
        targetStrengthB.setValue(0);
        channelAEnabled.setValue(false);
        channelBEnabled.setValue(false);
    }

    // ── 波形管理 ──
    private void loadBuiltinWaveforms() {
        PulseData[] builtins = BuiltinWaveforms.parseAllBuiltin();
        List<WaveEntry> list = new ArrayList<>(waveforms.getValue());
        for (PulseData pd : builtins) {
            if (pd != null && pd.sections != null) {
                list.add(new WaveEntry(pd.name, pd));
            }
        }
        waveforms.setValue(list);
    }

    /** 导入 .pulse 文件 */
    public boolean importPulse(String content, String name) {
        PulseData pd = PulseFileParser.parse(content, name);
        if (pd == null) return false;
        List<WaveEntry> list = new ArrayList<>(waveforms.getValue());
        list.add(new WaveEntry(name, pd));
        waveforms.setValue(list);
        return true;
    }

    // ── 波形播放（点击即播放/切换，不停止） ──
    public void selectAndPlayWaveformA(int index) {
        if (index < 0 || index >= waveforms.getValue().size()) return;
        selectedWaveIndexA.setValue(index);
        startWaveformA();
    }
    public void selectAndPlayWaveformB(int index) {
        if (index < 0 || index >= waveforms.getValue().size()) return;
        selectedWaveIndexB.setValue(index);
        startWaveformB();
    }

    private void startWaveformA() {
        if (controller == null) return;
        int idx = selectedWaveIndexA.getValue();
        if (idx < 0) return;
        PulseData pd = waveforms.getValue().get(idx).data;
        framesA = PulseToFrames.toFrames(pd, MAX_STRENGTH);
        if (framesA == null || framesA.length == 0) return;
        frameIndexA.set(0);
        stopWaveTimerA();
        waveTimerA = Executors.newSingleThreadScheduledExecutor();
        isPlayingA.setValue(true);
        progressA.setValue(0f);
        channelAEnabled.setValue(true);
        waveTimerA.scheduleAtFixedRate(() -> {
            int i = frameIndexA.getAndIncrement();
            if (i >= framesA.length) {
                // 播放完毕，发送静默帧，但不停止（保持选中）
                if (controller != null) controller.sendSilentFrame();
                isPlayingA.setValue(false);
                progressA.setValue(1f);
                stopWaveTimerA();
                return;
            }
            PulseToFrames.WaveFrame f = framesA[i];
            if (controller != null) controller.sendChannelWaveFrame(0, f.frequency, f.strength);
            progressA.setValue((float) i / framesA.length);
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void startWaveformB() {
        if (controller == null) return;
        int idx = selectedWaveIndexB.getValue();
        if (idx < 0) return;
        PulseData pd = waveforms.getValue().get(idx).data;
        framesB = PulseToFrames.toFrames(pd, MAX_STRENGTH);
        if (framesB == null || framesB.length == 0) return;
        frameIndexB.set(0);
        stopWaveTimerB();
        waveTimerB = Executors.newSingleThreadScheduledExecutor();
        isPlayingB.setValue(true);
        progressB.setValue(0f);
        channelBEnabled.setValue(true);
        waveTimerB.scheduleAtFixedRate(() -> {
            int i = frameIndexB.getAndIncrement();
            if (i >= framesB.length) {
                if (controller != null) controller.sendSilentFrame();
                isPlayingB.setValue(false);
                progressB.setValue(1f);
                stopWaveTimerB();
                return;
            }
            PulseToFrames.WaveFrame f = framesB[i];
            if (controller != null) controller.sendChannelWaveFrame(1, f.frequency, f.strength);
            progressB.setValue((float) i / framesB.length);
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void stopWaveformA() {
        stopWaveTimerA();
        isPlayingA.setValue(false);
        if (controller != null) controller.sendSilentFrame();
    }
    private void stopWaveformB() {
        stopWaveTimerB();
        isPlayingB.setValue(false);
        if (controller != null) controller.sendSilentFrame();
    }

    private void stopWaveTimerA() {
        if (waveTimerA != null) { waveTimerA.shutdownNow(); waveTimerA = null; }
    }
    private void stopWaveTimerB() {
        if (waveTimerB != null) { waveTimerB.shutdownNow(); waveTimerB = null; }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopWaveTimerA();
        stopWaveTimerB();
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }
}
