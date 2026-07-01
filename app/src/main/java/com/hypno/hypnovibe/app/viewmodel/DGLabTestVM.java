package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import android.preference.PreferenceManager;

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

    // SharedPreferences keys
    private static final String PREF_SOFT_LIMIT_A = "dglab_soft_limit_a";
    private static final String PREF_SOFT_LIMIT_B = "dglab_soft_limit_b";
    private static final String PREF_BALANCE_1A = "dglab_balance_1a";
    private static final String PREF_BALANCE_1B = "dglab_balance_1b";
    private static final String PREF_BALANCE_2A = "dglab_balance_2a";
    private static final String PREF_BALANCE_2B = "dglab_balance_2b";

    // 默认值
    private static final int DEFAULT_SOFT_LIMIT = 200;
    private static final int DEFAULT_BALANCE_1 = 160;
    private static final int DEFAULT_BALANCE_2 = 0;

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

    // === BF 参数（持久化） ===
    private final MutableStateFlow<Integer> softLimitA = StateFlowKt.MutableStateFlow(DEFAULT_SOFT_LIMIT);
    private final MutableStateFlow<Integer> softLimitB = StateFlowKt.MutableStateFlow(DEFAULT_SOFT_LIMIT);
    private final MutableStateFlow<Integer> balance1A = StateFlowKt.MutableStateFlow(DEFAULT_BALANCE_1);
    private final MutableStateFlow<Integer> balance1B = StateFlowKt.MutableStateFlow(DEFAULT_BALANCE_1);
    private final MutableStateFlow<Integer> balance2A = StateFlowKt.MutableStateFlow(DEFAULT_BALANCE_2);
    private final MutableStateFlow<Integer> balance2B = StateFlowKt.MutableStateFlow(DEFAULT_BALANCE_2);

    // 波形播放器（独立 timer）
    private ScheduledExecutorService waveTimerA;
    private ScheduledExecutorService waveTimerB;
    private PulseToFrames.WaveFrame[] framesA;
    private PulseToFrames.WaveFrame[] framesB;
    private final AtomicInteger frameIndexA = new AtomicInteger(0);
    private final AtomicInteger frameIndexB = new AtomicInteger(0);

    public DGLabTestVM(Application app) {
        super(app);
        loadBfParams();
        loadBuiltinWaveforms();
    }

    private void loadBfParams() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        softLimitA.setValue(prefs.getInt(PREF_SOFT_LIMIT_A, DEFAULT_SOFT_LIMIT));
        softLimitB.setValue(prefs.getInt(PREF_SOFT_LIMIT_B, DEFAULT_SOFT_LIMIT));
        balance1A.setValue(prefs.getInt(PREF_BALANCE_1A, DEFAULT_BALANCE_1));
        balance1B.setValue(prefs.getInt(PREF_BALANCE_1B, DEFAULT_BALANCE_1));
        balance2A.setValue(prefs.getInt(PREF_BALANCE_2A, DEFAULT_BALANCE_2));
        balance2B.setValue(prefs.getInt(PREF_BALANCE_2B, DEFAULT_BALANCE_2));
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
    public StateFlow<Integer> getSoftLimitA() { return softLimitA; }
    public StateFlow<Integer> getSoftLimitB() { return softLimitB; }
    public StateFlow<Integer> getBalance1A() { return balance1A; }
    public StateFlow<Integer> getBalance1B() { return balance1B; }
    public StateFlow<Integer> getBalance2A() { return balance2A; }
    public StateFlow<Integer> getBalance2B() { return balance2B; }

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
        pushBfParams();
    }

    // ── BF 参数设置（持久化 + 实时写入设备） ──

    private void pushBfParams() {
        if (controller != null) {
            controller.updateBfParams(
                softLimitA.getValue(), softLimitB.getValue(),
                balance1A.getValue(), balance1B.getValue(),
                balance2A.getValue(), balance2B.getValue());
        }
    }

    public void setSoftLimitA(int v) {
        v = clamp(v, 0, 200);
        softLimitA.setValue(v);
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putInt(PREF_SOFT_LIMIT_A, v).apply();
        pushBfParams();
    }
    public void setSoftLimitB(int v) {
        v = clamp(v, 0, 200);
        softLimitB.setValue(v);
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putInt(PREF_SOFT_LIMIT_B, v).apply();
        pushBfParams();
    }
    public void setBalance1A(int v) {
        v = clamp(v, 0, 255);
        balance1A.setValue(v);
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putInt(PREF_BALANCE_1A, v).apply();
        pushBfParams();
    }
    public void setBalance1B(int v) {
        v = clamp(v, 0, 255);
        balance1B.setValue(v);
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putInt(PREF_BALANCE_1B, v).apply();
        pushBfParams();
    }
    public void setBalance2A(int v) {
        v = clamp(v, 0, 255);
        balance2A.setValue(v);
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putInt(PREF_BALANCE_2A, v).apply();
        pushBfParams();
    }
    public void setBalance2B(int v) {
        v = clamp(v, 0, 255);
        balance2B.setValue(v);
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putInt(PREF_BALANCE_2B, v).apply();
        pushBfParams();
    }

    // ── 手动强度 ──
    public void increaseChannelA() { setStrengthA(targetStrengthA.getValue() + 1); }
    public void decreaseChannelA() { setStrengthA(targetStrengthA.getValue() - 1); }
    public void increaseChannelB() { setStrengthB(targetStrengthB.getValue() + 1); }
    public void decreaseChannelB() { setStrengthB(targetStrengthB.getValue() - 1); }

    /** Slider 拖动直接设置强度 */
    public void setStrengthA(int v) {
        v = clamp(v, 0, MAX_STRENGTH);
        targetStrengthA.setValue(v);
        if (controller != null) {
            controller.setManualStrength(v, targetStrengthB.getValue());
        }
    }
    /** Slider 拖动直接设置强度 */
    public void setStrengthB(int v) {
        v = clamp(v, 0, MAX_STRENGTH);
        targetStrengthB.setValue(v);
        if (controller != null) {
            controller.setManualStrength(targetStrengthA.getValue(), v);
        }
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

    // ── 启动/停止（整合安全锁） ──
    public void startChannelA() {
        if (safetyOn.getValue() && controller != null) {
            controller.unlockSafety();
        }
        safetyOn.setValue(false);
        if (!channelAEnabled.getValue()) {
            channelAEnabled.setValue(true);
        }
        if (controller != null) controller.setManualStrength(targetStrengthA.getValue(), targetStrengthB.getValue());
    }
    public void startChannelB() {
        if (safetyOn.getValue() && controller != null) {
            controller.unlockSafety();
        }
        safetyOn.setValue(false);
        if (!channelBEnabled.getValue()) {
            channelBEnabled.setValue(true);
        }
        if (controller != null) controller.setManualStrength(targetStrengthA.getValue(), targetStrengthB.getValue());
    }

    public void stopChannelA() {
        stopWaveformA();
        targetStrengthA.setValue(0);
        channelAEnabled.setValue(false);
        if (controller != null) controller.setManualStrength(0, targetStrengthB.getValue());
    }
    public void stopChannelB() {
        stopWaveformB();
        targetStrengthB.setValue(0);
        channelBEnabled.setValue(false);
        if (controller != null) controller.setManualStrength(targetStrengthA.getValue(), 0);
    }

    // ── 安全（保留紧急停止用于 DisposableEffect 等场景） ──
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
        targetStrengthA.setValue(MAX_STRENGTH); // 波形启动默认满强度
        if (controller != null) controller.setManualStrength(MAX_STRENGTH, targetStrengthB.getValue());
        waveTimerA.scheduleAtFixedRate(() -> {
            int i = frameIndexA.getAndIncrement();
            if (i >= framesA.length) {
                // 播放完毕自动循环
                frameIndexA.set(0);
                PulseToFrames.WaveFrame f = framesA[0];
                int scaledStr = (int)((long)f.strength * targetStrengthA.getValue() / MAX_STRENGTH);
                if (controller != null) controller.sendChannelWaveFrame(0, f.frequency, scaledStr);
                progressA.setValue(0f);
                return;
            }
            PulseToFrames.WaveFrame f = framesA[i];
            int scaledStr = (int)((long)f.strength * targetStrengthA.getValue() / MAX_STRENGTH);
            if (controller != null) controller.sendChannelWaveFrame(0, f.frequency, scaledStr);
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
        targetStrengthB.setValue(MAX_STRENGTH); // 波形启动默认满强度
        if (controller != null) controller.setManualStrength(targetStrengthA.getValue(), MAX_STRENGTH);
        waveTimerB.scheduleAtFixedRate(() -> {
            int i = frameIndexB.getAndIncrement();
            if (i >= framesB.length) {
                // 播放完毕自动循环
                frameIndexB.set(0);
                PulseToFrames.WaveFrame f = framesB[0];
                int scaledStr = (int)((long)f.strength * targetStrengthB.getValue() / MAX_STRENGTH);
                if (controller != null) controller.sendChannelWaveFrame(1, f.frequency, scaledStr);
                progressB.setValue(0f);
                return;
            }
            PulseToFrames.WaveFrame f = framesB[i];
            int scaledStr = (int)((long)f.strength * targetStrengthB.getValue() / MAX_STRENGTH);
            if (controller != null) controller.sendChannelWaveFrame(1, f.frequency, scaledStr);
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
