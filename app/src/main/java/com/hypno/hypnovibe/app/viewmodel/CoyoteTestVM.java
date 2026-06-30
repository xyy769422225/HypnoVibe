package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;

import com.hypno.hypnovibe.infrastructure.ble.adapter.coyote.CoyoteV3Adapter;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * 郊狼测试面板 ViewModel。
 * 仅负责当前选中设备的强度测试，不负责扫描/连接（由 DeviceManagerVM 负责）。
 */
public class CoyoteTestVM extends AndroidViewModel {
    private static final String TAG = "CoyoteTestVM";
    private static final int MAX_STRENGTH = 200;

    private CoyoteV3Adapter adapter;

    private final MutableStateFlow<Integer> targetStrengthA = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Integer> targetStrengthB = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Integer> deviceStrengthA = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Integer> deviceStrengthB = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Boolean> safetyOn = StateFlowKt.MutableStateFlow(true);
    private final MutableStateFlow<Boolean> isConnected = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<String> deviceName = StateFlowKt.MutableStateFlow((String) null);

    public CoyoteTestVM(Application app) {
        super(app);
    }

    // ── StateFlow getters ──
    public StateFlow<Integer> getTargetStrengthA() { return targetStrengthA; }
    public StateFlow<Integer> getTargetStrengthB() { return targetStrengthB; }
    public StateFlow<Integer> getDeviceStrengthA() { return deviceStrengthA; }
    public StateFlow<Integer> getDeviceStrengthB() { return deviceStrengthB; }
    public StateFlow<Boolean> getSafetyOn() { return safetyOn; }
    public StateFlow<Boolean> getIsConnected() { return isConnected; }
    public StateFlow<String> getDeviceName() { return deviceName; }

    /** 设置要测试的 adapter（从 DeviceManagerVM 获取） */
    public void setAdapter(CoyoteV3Adapter adapter, String name) {
        this.adapter = adapter;
        this.deviceName.setValue(name);

        adapter.setCoyoteListener(new CoyoteV3Adapter.CoyoteListener() {
            @Override
            public void onStrengthFeedback(int a, int b) {
                deviceStrengthA.setValue(a);
                deviceStrengthB.setValue(b);
            }

            @Override
            public void onConnected() {
                isConnected.setValue(true);
                safetyOn.setValue(adapter.isSafetyOn());
            }

            @Override
            public void onDisconnected() {
                isConnected.setValue(false);
                safetyOn.setValue(true);
                targetStrengthA.setValue(0);
                targetStrengthB.setValue(0);
            }
        });

        isConnected.setValue(adapter.isConnected());
        safetyOn.setValue(adapter.isSafetyOn());
        deviceStrengthA.setValue(adapter.getDeviceStrengthA());
        deviceStrengthB.setValue(adapter.getDeviceStrengthB());
    }

    // ── 强度控制 ──

    public void increaseChannelA() {
        int v = clamp(targetStrengthA.getValue() + 1);
        targetStrengthA.setValue(v);
        if (adapter != null) adapter.setManualStrength(v, targetStrengthB.getValue());
    }

    public void decreaseChannelA() {
        int v = clamp(targetStrengthA.getValue() - 1);
        targetStrengthA.setValue(v);
        if (adapter != null) adapter.setManualStrength(v, targetStrengthB.getValue());
    }

    public void increaseChannelB() {
        int v = clamp(targetStrengthB.getValue() + 1);
        targetStrengthB.setValue(v);
        if (adapter != null) adapter.setManualStrength(targetStrengthA.getValue(), v);
    }

    public void decreaseChannelB() {
        int v = clamp(targetStrengthB.getValue() - 1);
        targetStrengthB.setValue(v);
        if (adapter != null) adapter.setManualStrength(targetStrengthA.getValue(), v);
    }

    // ── 安全开关 ──

    /** 解锁安全开关 */
    public void unlockSafety() {
        if (adapter != null) adapter.unlockSafety();
        safetyOn.setValue(false);
    }

    /** 紧急停止（一键归零） */
    public void emergencyStop() {
        if (adapter != null) adapter.emergencyStop();
        safetyOn.setValue(true);
        targetStrengthA.setValue(0);
        targetStrengthB.setValue(0);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // 注意：不释放 adapter，由 DeviceManagerVM 统一管理生命周期
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > MAX_STRENGTH) return MAX_STRENGTH;
        return v;
    }
}
