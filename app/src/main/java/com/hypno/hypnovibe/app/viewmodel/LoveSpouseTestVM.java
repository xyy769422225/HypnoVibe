package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;

import com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse.LoveSpouseAdapter;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * Love Spouse 测试面板 ViewModel。
 * <p>
 * 负责广播开关和振动等级控制。adapter 由 DeviceManagerVM 统一管理生命周期。
 */
public class LoveSpouseTestVM extends AndroidViewModel {

    private LoveSpouseAdapter adapter;
    private DeviceManagerVM deviceManagerVM;

    private final MutableStateFlow<Integer> currentLevel = StateFlowKt.MutableStateFlow(0);
    private final MutableStateFlow<Boolean> isAdvertising = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<String> deviceName = StateFlowKt.MutableStateFlow((String) null);
    private final MutableStateFlow<String> errorMsg = StateFlowKt.MutableStateFlow((String) null);

    public LoveSpouseTestVM(Application app) {
        super(app);
    }

    public StateFlow<Integer> getCurrentLevel() { return currentLevel; }
    public StateFlow<Boolean> getIsAdvertising() { return isAdvertising; }
    public StateFlow<String> getDeviceName() { return deviceName; }
    public StateFlow<String> getErrorMsg() { return errorMsg; }

    /** 初始化：绑定 DeviceManagerVM 并尝试查找已存在的 adapter */
    public void init(DeviceManagerVM dmVm, String deviceId, String name) {
        this.deviceManagerVM = dmVm;
        this.deviceName.setValue(name);
        tryBindAdapter(deviceId);
    }

    private void tryBindAdapter(String deviceId) {
        if (deviceManagerVM == null) return;
        var connected = deviceManagerVM.findDevice(deviceId);
        if (connected != null && connected.getAdapter() instanceof LoveSpouseAdapter) {
            this.adapter = (LoveSpouseAdapter) connected.getAdapter();
            isAdvertising.setValue(adapter.isAdvertising());
            currentLevel.setValue(adapter.getCurrentLevel());
        } else {
            this.adapter = null;
            isAdvertising.setValue(false);
        }
    }

    /** 开启 BLE 广播 */
    public void startBroadcast() {
        if (deviceManagerVM == null) return;
        clearError();

        // 检查是否已有已连接的虚拟设备
        var items = deviceManagerVM.getDeviceList().getValue();
        if (items != null) {
            for (var item : items) {
                if (item.isVirtual && item.deviceType.equals(DeviceManagerVM.TYPE_LOVE_SPOUSE)
                        && item.connected && item.deviceId != null) {
                    tryBindAdapter(item.deviceId);
                    if (adapter != null) return;
                }
            }
        }

        // 创建新广播实例
        deviceManagerVM.addBroadcastDevice(DeviceManagerVM.TYPE_LOVE_SPOUSE);
        // 延迟绑定（addBroadcastDevice 内部 connect 是同步的，回调在 connectedMap 中）
        mainHandler.postDelayed(() -> tryBindAdapterFromList(), 300);
    }

    private void tryBindAdapterFromList() {
        var items = deviceManagerVM.getDeviceList().getValue();
        if (items != null) {
            for (var item : items) {
                if (item.isVirtual && item.deviceType.equals(DeviceManagerVM.TYPE_LOVE_SPOUSE)
                        && item.connected && item.deviceId != null) {
                    tryBindAdapter(item.deviceId);
                    if (adapter != null) return;
                }
            }
        }
    }

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** 停止广播 */
    public void stopBroadcast() {
        if (deviceManagerVM == null) return;
        clearError();
        deviceManagerVM.disconnectDevice(DeviceManagerVM.VIRTUAL_MAC_PREFIX_FOR_LOVE_SPOUSE);
        adapter = null;
        isAdvertising.setValue(false);
        currentLevel.setValue(0);
    }

    /** 紧急停止（归零但不停止广播） */
    public void emergencyStop() {
        if (adapter != null) {
            adapter.emergencyStop();
        }
        currentLevel.setValue(0);
    }

    /** 设置振动等级（0-9） */
    public void setStrength(int level) {
        int clamped = Math.max(0, Math.min(9, level));
        if (adapter != null) {
            adapter.setManualStrength(clamped);
        }
        currentLevel.setValue(clamped);
    }

    public void clearError() {
        errorMsg.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
