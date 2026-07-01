package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import androidx.lifecycle.AndroidViewModel;

import com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse.LoveSpouseAdapter;
import com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse.LoveSpouseConstants;

import java.util.ArrayList;
import java.util.List;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * Love Spouse 测试面板 ViewModel。
 * <p>
 * 管理广播开关、强度控制、CateId 选择和模式命令。
 */
public class LoveSpouseTestVM extends AndroidViewModel {

    private LoveSpouseAdapter adapter;
    private DeviceManagerVM deviceManagerVM;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── 广播 ──
    private final MutableStateFlow<Boolean> isAdvertising = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<String> deviceName = StateFlowKt.MutableStateFlow((String) null);
    private final MutableStateFlow<String> errorMsg = StateFlowKt.MutableStateFlow((String) null);

    // ── 强度 ──
    private final MutableStateFlow<Integer> currentLevel = StateFlowKt.MutableStateFlow(0);

    // ── 模式 ──
    private final MutableStateFlow<Integer> selectedCateId = StateFlowKt.MutableStateFlow(1);
    private final MutableStateFlow<List<String>> currentModeCommands = StateFlowKt.MutableStateFlow(new ArrayList<>());
    /** 当前激活的模式命令（null = 无激活） */
    private final MutableStateFlow<String> activeMode = StateFlowKt.MutableStateFlow((String) null);
    private String activeModeStopCmd = LoveSpouseConstants.STOP_ALL;

    private static final String PREF_CATE_ID = "ls_test_cate_id";

    public LoveSpouseTestVM(Application app) {
        super(app);
        int saved = PreferenceManager.getDefaultSharedPreferences(app)
                .getInt(PREF_CATE_ID, 1);
        selectedCateId.setValue(saved);
        updateModeList(saved);
    }

    // ── StateFlow getters ──
    public StateFlow<Integer> getCurrentLevel() { return currentLevel; }
    public StateFlow<Boolean> getIsAdvertising() { return isAdvertising; }
    public StateFlow<String> getDeviceName() { return deviceName; }
    public StateFlow<String> getErrorMsg() { return errorMsg; }
    public StateFlow<Integer> getSelectedCateId() { return selectedCateId; }
    public StateFlow<List<String>> getCurrentModeCommands() { return currentModeCommands; }
    public StateFlow<String> getActiveMode() { return activeMode; }

    public void init(DeviceManagerVM dmVm, String deviceId, String name) {
        this.deviceManagerVM = dmVm;
        this.deviceName.setValue(name);
        tryBind(deviceId);
    }

    private void tryBind(String deviceId) {
        if (deviceManagerVM == null) return;
        var connected = deviceManagerVM.findDevice(deviceId);
        if (connected != null && connected.getAdapter() instanceof LoveSpouseAdapter) {
            this.adapter = (LoveSpouseAdapter) connected.getAdapter();
            isAdvertising.setValue(adapter.isAdvertising());
            currentLevel.setValue(adapter.getCurrentStrength());
        } else {
            this.adapter = null;
            isAdvertising.setValue(false);
        }
    }

    // ── 广播控制 ──

    public void startBroadcast() {
        if (deviceManagerVM == null) return;
        clearError();
        deviceManagerVM.addBroadcastDevice(DeviceManagerVM.TYPE_LOVE_SPOUSE);
        mainHandler.postDelayed(() -> tryBindFromList(), 300);
    }

    private void tryBindFromList() {
        var items = deviceManagerVM.getDeviceList().getValue();
        if (items != null) {
            for (var item : items) {
                if (item.isVirtual && item.deviceType.equals(DeviceManagerVM.TYPE_LOVE_SPOUSE)
                        && item.connected && item.deviceId != null) {
                    tryBind(item.deviceId);
                    if (adapter != null) return;
                }
            }
        }
    }

    public void stopBroadcast() {
        if (deviceManagerVM == null) return;
        clearError();
        deviceManagerVM.disconnectDevice(DeviceManagerVM.VIRTUAL_MAC_PREFIX_FOR_LOVE_SPOUSE);
        adapter = null;
        isAdvertising.setValue(false);
        currentLevel.setValue(0);
        activeMode.setValue(null);
    }

    public void emergencyStop() {
        if (adapter != null) adapter.emergencyStop();
        currentLevel.setValue(0);
        activeMode.setValue(null);
    }

    // ── 强度控制 ──

    public void setStrength(int level) {
        int clamped = Math.max(0, Math.min(9, level));
        if (adapter != null) adapter.setStrength(clamped);
        currentLevel.setValue(clamped);
    }

    // ── CateId 切换 ──

    public void setCateId(int cateId) {
        selectedCateId.setValue(cateId);
        activeMode.setValue(null);
        updateModeList(cateId);
        // 持久化
        PreferenceManager.getDefaultSharedPreferences(getApplication())
                .edit().putInt(PREF_CATE_ID, cateId).apply();
    }

    private void updateModeList(int cateId) {
        var config = LoveSpouseConstants.getModeConfig(cateId);
        List<String> cmds = new ArrayList<>();
        for (int i = config.start; i <= config.end; i++) {
            cmds.add(String.format("%02d", i));
        }
        currentModeCommands.setValue(cmds);
        activeModeStopCmd = config.stop;
    }

    // ── 模式切换（toggle: 点一次激活, 再点取消） ──

    public void toggleMode(String commandHex) {
        if (adapter == null) return;
        String current = activeMode.getValue();
        if (commandHex.equals(current)) {
            // 同一模式 → 停止
            adapter.sendMode(activeModeStopCmd);
            activeMode.setValue(null);
        } else {
            // 新模式 → 发送
            adapter.sendMode(commandHex);
            activeMode.setValue(commandHex);
        }
    }

    public void clearError() { errorMsg.setValue(null); }
}
