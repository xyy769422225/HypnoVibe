# Phase 4 详细设计：郊狼测试面板

> 阶段目标：定义设备适配层契约 → 实现 BLE 扫描与 CoyoteV3Adapter → 测试面板验证 A/B 通道强度控制（按钮+-/安全开关/基础波形）  
> 产出物：可编译安装的 APK，连接郊狼后通过面板控制 A/B 通道强度并体感验证

---

## 目录

1. [架构分层与组件职责](#1-架构分层与组件职责)
2. [设备适配层契约定义](#2-设备适配层契约定义)
3. [交互流程](#3-交互流程)
4. [测试面板 UI 设计](#4-测试面板-ui-设计)
5. [强度控制逻辑](#5-强度控制逻辑)
6. [安全开关设计](#6-安全开关设计)
7. [BLE 通信层](#7-ble-通信层)
8. [数据模型与状态](#8-数据模型与状态)
9. [文件清单](#9-文件清单)
10. [验证标准](#10-验证标准)

---

## 1. 架构分层与组件职责

### 1.1 分层总览

```
domain 层（契约，Phase 4 最先落地）
├── DeviceProtocolAdapter.java      ← 所有设备适配器的统一接口
└── AdapterStatus.java              ← 适配器状态回调接口

infrastructure/ble 层
├── BleScanner.java                 ← 仅扫描，返回 ScanResult 列表（无连接逻辑）
└── adapter/coyote/                  ← 郊狼子包（完整自治）
    ├── CoyoteV3Adapter.java        ← 完整实现 DeviceProtocolAdapter
    │   ├── connect/disconnect/release/emergencyStop  (Phase 4 完整实现)
    │   ├── updateSnapshot                            (Phase 4 空实现，Phase 5 填充)
    │   ├── validateSegmentData                       (Phase 4 返回 false，Phase 5 实现)
    │   └── setManualStrength(a, b)                   (Phase 4 测试面板专用)
    ├── CoyoteB0Builder.java         ← package-private，B0/BF 指令构造
    └── CoyoteFrequencyConverter.java ← package-private，频率换算

app 层（ViewModel 拆分，职责单一）
├── DeviceManagerVM.java            ← 扫描/连接/已连接列表/历史设备
└── CoyoteTestVM.java               ← 测试面板（持有 adapter 引用，仅管强度测试）

UI 层
├── DeviceScreen.kt                 ← 改造：接入 BLE 扫描/连接
├── BleScanSheet.kt                 ← 新建：扫描结果 BottomSheet
└── CoyoteTestScreen.kt             ← 新建：测试面板
```

### 1.2 关键设计决策（修正点）

| 问题 | 错误做法 | 正确做法 |
|------|---------|---------|
| Adapter 定位 | 写"简化版"，Phase 5 重写 | Phase 4 就实现完整 `DeviceProtocolAdapter`，`updateSnapshot` 留空 |
| 扫描与连接 | BleManager 既扫描又管 GATT | `BleScanner` 仅扫描；`CoyoteV3Adapter.connect()` 内部自行 GATT |
| ViewModel | 单个 VM 管所有 | 拆为 `DeviceManagerVM`（设备管理）+ `CoyoteTestVM`（测试） |
| 接口定义 | 直接写实现 | 先在 domain 层定义 `DeviceProtocolAdapter` + `AdapterStatus` 契约 |

> **原则**：Phase 4 实现的代码在 Phase 5/6 必须 100% 复用，不允许"用完即扔"。`updateSnapshot` 是唯一留空的占位方法。

---

## 2. 设备适配层契约定义

### 2.1 DeviceProtocolAdapter（架构层接口）

位于 `domain` 层，所有设备适配器必须实现。与架构设计文档 2.5.2 完全一致：

```java
public interface DeviceProtocolAdapter {
    // ===== 标识 =====
    String getDeviceType();          // "coyote_v3", "coyote_v2", "lovense_vibrate"
    String getDeviceId();            // 实例唯一ID

    // ===== 生命周期 =====
    void connect(Context context, String address, AdapterStatus status);
    void disconnect();
    void release();

    // ===== 数据通道 =====
    void updateSnapshot(Map<String, byte[]> channelData,
                        Map<String, Long> offsetsInSegment);  // Phase 5 填充
    void flush();
    void emergencyStop();

    // ===== 校验 =====
    boolean validateSegmentData(byte[] protobufBytes);  // Phase 5 填充
}
```

### 2.2 AdapterStatus（状态回调接口）

与架构设计文档 2.5.1 一致：

```java
public interface AdapterStatus {
    enum State { DISCONNECTED, CONNECTING, CONNECTED, RETRYING, ERROR }

    void onStateChanged(State state, String deviceId, String detail);
    void onCycleStats(String deviceId, long writeLatencyMs, boolean success);
    void onFatalError(String deviceId, String error);
}
```

### 2.3 CoyoteV3Adapter 额外接口（测试面板专用）

`DeviceProtocolAdapter` 是面向时间轴驱动的接口。测试面板需要手动设置强度，因此 CoyoteV3Adapter 额外暴露：

```java
public class CoyoteV3Adapter implements DeviceProtocolAdapter {
    // ... 实现 DeviceProtocolAdapter 全部方法 ...

    // ===== 测试面板专用（Phase 4） =====
    /** 手动设置目标强度（0-200），由内部 100ms 定时器统一发送 */
    public void setManualStrength(int strengthA, int strengthB);

    /** 获取设备回报的实际强度（B1 更新） */
    public int getDeviceStrengthA();
    public int getDeviceStrengthB();

    /** 测试回调（B1 强度反馈、连接状态） */
    public interface CoyoteListener {
        void onStrengthFeedback(int a, int b);
        void onConnected();
        void onDisconnected();
    }
    public void setCoyoteListener(CoyoteListener listener);
}
```

> `setManualStrength` 不在 `DeviceProtocolAdapter` 接口中，是 CoyoteV3Adapter 的具体方法。测试面板直接持有 `CoyoteV3Adapter` 引用（而非接口引用）来调用。Phase 5 的 PlaybackCoordinator 通过接口引用调用 `updateSnapshot`。

---

## 3. 交互流程

```
DeviceScreen（设备管理）
    │
    ├── [+] 按钮 → BleScanSheet（BottomSheet）
    │                ├── BleScanner.startScan() 过滤 "47L121000" 前缀
    │                ├── 显示扫描结果列表（名称 + MAC + 信号强度）
    │                └── 点击设备 → DeviceManagerVM.connectDevice(mac, name)
    │                                   │
    │                                   ▼
    │                             CoyoteV3Adapter.connect(ctx, mac, status)
    │                                   │
    │                             AdapterStatus.onStateChanged(CONNECTED)
    │                                   │
    │                             加入 connectedDevices 列表
    │
    ├── 已连接设备列表（可多设备）
    │   │
    │   └── 点击某个已连接设备
    │         │
    │         ▼
    │     navController.navigate("coyote_test/$deviceId")
    │         │
    │         ▼
    │     CoyoteTestScreen（测试面板）
    │         ├── CoyoteTestVM 持有对应 adapter 引用
    │         ├── A 通道: [－][＋] 按钮 + 进度条 + 设备回报
    │         ├── B 通道: [－][＋] 按钮 + 进度条 + 设备回报
    │         ├── 基础波形: 固定低频恒定
    │         └── 安全停止: 一键归零
    │
    └── 长按已连接设备 → 断开连接
```

> Phase 4 不涉及音频时间轴驱动。测试面板是**纯手动面板**，用于验证 BLE 链路和强度控制正确性。

---

## 4. 测试面板 UI 设计

### 4.1 整体布局

```
┌─────────────────────────────────────────────┐
│  ← 返回          郊狼测试          设备名称   │
├─────────────────────────────────────────────┤
│                                             │
│  ⚡ A 通道                                   │
│  ┌─────────────────────────────────────────┐│
│  │ [－]  ████████████░░░░░░  120/200  [＋] ││
│  │         设备回报: 118                    ││
│  └─────────────────────────────────────────┘│
│                                             │
│  ⚡ B 通道                                   │
│  ┌─────────────────────────────────────────┐│
│  │ [－]  ██████░░░░░░░░░░░░  80/200   [＋] ││
│  │         设备回报: 79                     ││
│  └─────────────────────────────────────────┘│
│                                             │
│  ┌────────────────────────────────────────┐ │
│  │            🔴 安全停止 / ⚡ 解锁         │ │
│  └────────────────────────────────────────┘ │
│                                             │
│  ⚠ 按住按钮不松将连续增加，每秒最多+2强度     │
└─────────────────────────────────────────────┘
```

### 4.2 元素规格

| 元素 | 样式 | 说明 |
|------|------|------|
| 通道标题 | `Cinzel Bold 18sp, GoldAncient` | "⚡ A 通道" |
| **［－］按钮** | `DungeonButton(SECONDARY)`, 宽48dp | 按住连续减少，每秒最多-2 |
| **［＋］按钮** | `DungeonButton(PRIMARY, BloodRed)`, 宽48dp | 按住连续增加，每秒最多+2 |
| 强度进度条 | `DungeonSlider(0..200)`, BloodRed 填充 | 非拖动型，纯展示当前设定值 |
| 数值标签 | `JetBrains Mono 13sp, SilverGray` | "120/200" |
| 设备回报 | `JetBrains Mono 11sp, DarkGray` | 显示 B1 反馈的实际强度 |
| **安全开关** | `DungeonButton(DANGER/SECONDARY)`, fillMaxWidth | 见第 6 节 |
| 底部提示 | `Spectral 11sp, DarkGray` | 按住说明文字 |

### 4.3 按钮按住行为（核心交互）

Compose 使用 `pointerInput` + `detectTapGestures.onPress`：

| 参数 | 值 | 说明 |
|------|:--:|------|
| 首次触发延迟 | 0ms | 按下即响应一次 |
| 连续触发间隔 | 500ms | 每秒 2 次 = 每秒最多 +2 |
| 回弹保护 | 0..200 clamp | 超出范围自动截断 |

```kotlin
// 伪代码
Modifier.pointerInput(Unit) {
    detectTapGestures(
        onPress = {
            onChange(delta)                          // 立即响应一次
            job = scope.launch {
                delay(500)
                while (isActive) {
                    onChange(delta)
                    delay(500)                       // 每500ms一次
                }
            }
            tryAwaitRelease()
            job?.cancel()
        }
    )
}
```

---

## 5. 强度控制逻辑

### 5.1 状态分布

```
CoyoteTestVM (UI 状态)
├── targetStrengthA/B     ← 本地目标值（按钮+/-修改）
├── safetyOn              ← 安全开关
└── 持有 CoyoteV3Adapter 引用

CoyoteV3Adapter (协议状态)
├── deviceStrengthA/B     ← 设备回报值（B1 回调更新）
├── waitingConfirm        ← 流控：等待 B1 确认
├── pendingSeqNo          ← 当前待确认的序列号
└── 100ms 定时器           ← 统一发送 B0
```

### 5.2 按钮 → Adapter 流程

```java
// CoyoteTestVM
void increaseChannelA() {
    targetStrengthA = clamp(targetStrengthA + 1, 0, 200);
    adapter.setManualStrength(targetStrengthA, targetStrengthB);
}

// CoyoteV3Adapter
void setManualStrength(int a, int b) {
    this.targetStrengthA = a;
    this.targetStrengthB = b;
    // 不立即发送，由 100ms 定时器取最新值
}
```

### 5.3 100ms 定时器（强度 + 波形合一）

```java
// CoyoteV3Adapter.onTimerTick()，每 100ms
void onTimerTick() {
    if (safetyOn) {
        sendEmergencyStop();
        return;
    }

    if (waitingConfirm) {
        // 等待 B1 确认中，强度部分填"不变"，仅维持波形
        sendB0StrengthUnchanged();
        return;
    }

    boolean needChangeA = targetStrengthA != deviceStrengthA;
    boolean needChangeB = targetStrengthB != deviceStrengthB;

    if (needChangeA || needChangeB) {
        pendingSeqNo = (pendingSeqNo % 15) + 1;
        waitingConfirm = true;
        int modeA = needChangeA ? 3 : 0;  // 3=绝对设置
        int modeB = needChangeB ? 3 : 0;
        sendB0(modeA, modeB, targetStrengthA, targetStrengthB);
    } else {
        sendB0StrengthUnchanged();        // 仅波形
    }
}
```

### 5.4 基础波形

固定低频恒定波形，两通道相同：

| 槽位 | 频率（协议值） | 波形强度 |
|:--:|:--:|:--:|
| 0-25ms | 100 | 50 |
| 25-50ms | 100 | 50 |
| 50-75ms | 100 | 50 |
| 75-100ms | 100 | 50 |

---

## 6. 安全开关设计

### 6.1 核心原则

**安全第一**：任何时候点击安全停止按钮，设备强度必须立即归零。

### 6.2 行为

| 时机 | safetyOn | 行为 |
|------|:--:|------|
| 进入面板 | true | 强度为 0，按钮+-可点但定时器只发归零指令 |
| 点击"⚡ 解锁强度" | → false | 允许定时器正常发送强度 |
| 点击"🔴 安全停止" | → true | **立即**（绕开定时器）发送归零 B0 + 清除目标值 |
| BLE 断连 | → true | 自动触发安全停止 |

### 6.3 按钮样式

```kotlin
DungeonButton(
    text = if (safetyOn) "⚡ 解锁强度" else "🔴 安全停止",
    variant = if (safetyOn) ButtonVariant.SECONDARY else ButtonVariant.DANGER,
    onClick = {
        if (safetyOn) {
            // 解锁：允许调节
            vm.unlockSafety()
        } else {
            // 紧急停止：立即归零
            vm.emergencyStop()
        }
    }
)
```

### 6.4 emergencyStop 实现（绕开定时器）

```java
// CoyoteV3Adapter
public void emergencyStop() {
    targetStrengthA = 0;
    targetStrengthB = 0;
    // 立即发送归零 B0，不等下一个 100ms tick
    writeCharacteristic(buildB0(true, nextSeqNo(), 3, 3, 0, 0, ...));
}
```

---

## 7. BLE 通信层

### 7.1 BleScanner（仅扫描）

```java
public class BleScanner {
    /** 开始扫描，过滤郊狼 V3 设备（名称前缀 "47L121000"） */
    void startScan(ScanCallback callback);

    /** 停止扫描 */
    void stopScan();

    interface ScanCallback {
        void onDeviceFound(String mac, String name, int rssi);
        void onScanComplete();
        void onError(String msg);
    }
}
```

> BleScanner **不负责连接**。连接由 `CoyoteV3Adapter.connect()` 内部完成。

### 7.2 CoyoteV3Adapter.connect() 内部流程

```
connect(ctx, mac, status):
  1. BluetoothAdapter.getRemoteDevice(mac)
  2. device.connectGatt(ctx, false, gattCallback)
  3. onConnectionStateChange(STATE_CONNECTED)
  4. gatt.discoverServices()
  5. onServicesDiscovered:
     a. 获取 0x150A (write) / 0x150B (notify) 特征值
     b. gatt.setCharacteristicNotification(0x150B, true)
     c. gatt.writeCharacteristic(0x150A, buildBF(200,200,128,128,128,128))
     d. 启动 100ms 定时器
     e. status.onStateChanged(CONNECTED, deviceId, null)
```

### 7.3 UUID 常量

```java
UUID SERVICE_UUID = UUID.fromString("0000180c-0000-1000-8000-00805f9b34fb");
UUID CHAR_WRITE   = UUID.fromString("0000150a-0000-1000-8000-00805f9b34fb");
UUID CHAR_NOTIFY  = UUID.fromString("0000150b-0000-1000-8000-00805f9b34fb");
```

### 7.4 错误容错

| 场景 | 处理方式 |
|------|------|
| BLE 断连 | safetyOn=true → 停定时器 → status.onStateChanged(DISCONNECTED) |
| GATT write 失败 | retry 1 次后 status.onCycleStats(success=false) |
| B1 超时（500ms 未收到） | waitingConfirm=false，允许下次强度修改 |

---

## 8. 数据模型与状态

### 8.1 DeviceManagerVM（设备管理）

```java
class DeviceManagerVM extends AndroidViewModel {
    StateFlow<List<ConnectedDevice>> connectedDevices;  // 已连接列表
    StateFlow<Boolean> isScanning;
    StateFlow<String> errorMsg;

    void startScan(BleScanner.ScanCallback cb);
    void stopScan();
    void connectDevice(String mac, String name);   // 创建 Adapter 并 connect
    void disconnectDevice(String deviceId);        // adapter.disconnect() + release
    void onCleared();
}

/** 已连接设备的运行时数据 */
class ConnectedDevice {
    String deviceId;        // UUID 生成
    String name;
    String mac;
    DeviceProtocolAdapter adapter;  // 持有 adapter 引用
    AdapterStatus.State state;
}
```

### 8.2 CoyoteTestVM（测试面板）

```java
class CoyoteTestVM extends AndroidViewModel {
    StateFlow<Integer> targetStrengthA;     // 本地目标值 0-200
    StateFlow<Integer> targetStrengthB;
    StateFlow<Integer> deviceStrengthA;     // 设备回报值
    StateFlow<Integer> deviceStrengthB;
    StateFlow<Boolean> safetyOn;
    StateFlow<Boolean> isConnected;

    private CoyoteV3Adapter adapter;        // 从 DeviceManagerVM 获取

    void setAdapter(CoyoteV3Adapter adapter);
    void increaseChannelA();                // +1 → adapter.setManualStrength
    void decreaseChannelA();                // -1
    void increaseChannelB();
    void decreaseChannelB();
    void unlockSafety();                    // safetyOn = false
    void emergencyStop();                   // safetyOn = true + adapter.emergencyStop()
}
```

### 8.3 导航流程

```kotlin
// Screen.kt 新增路由
object CoyoteTest : Screen("coyote_test/{deviceId}", "强度测试", Icons.Filled.Bolt)

// NavGraph.kt
composable(
    route = Screen.CoyoteTest.route,
    arguments = listOf(navArgument("deviceId") {})
) { backStackEntry ->
    val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
    CoyoteTestScreen(deviceId, navController)
}
```

---

## 9. 文件清单

### domain 层（契约）

| 文件 | 类型 | 说明 |
|------|:--:|------|
| `domain/DeviceProtocolAdapter.java` | 新增 | 设备适配器统一接口 |
| `domain/AdapterStatus.java` | 新增 | 状态回调接口 |

### infrastructure 层

| 文件 | 类型 | 说明 |
|------|:--:|------|
| `infrastructure/ble/BleScanner.java` | 新增 | 仅扫描，不连接 |
| `infrastructure/ble/adapter/coyote/CoyoteV3Adapter.java` | 新增 | 完整实现 DeviceProtocolAdapter + setManualStrength |
| `infrastructure/ble/adapter/coyote/CoyoteB0Builder.java` | 新增 | package-private，B0/BF 指令构造 |
| `infrastructure/ble/adapter/coyote/CoyoteFrequencyConverter.java` | 新增 | package-private，频率换算 |

### app 层

| 文件 | 类型 | 说明 |
|------|:--:|------|
| `app/viewmodel/DeviceManagerVM.java` | 新增 | 扫描/连接/已连接列表 |
| `app/viewmodel/CoyoteTestVM.java` | 新增 | 测试面板强度控制 |
| `app/manager/ConnectedDevice.java`（或 domain/entity） | 新增 | 已连接设备运行时数据 |

### UI 层

| 文件 | 类型 | 说明 |
|------|:--:|------|
| `ui/screen/device/DeviceScreen.kt` | 修改 | 接入扫描/连接/已连接列表 |
| `ui/screen/device/BleScanSheet.kt` | 新增 | 扫描结果 BottomSheet |
| `ui/screen/device/CoyoteTestScreen.kt` | 新增 | 测试面板 |

---

## 10. 验证标准

| # | 检查项 |
|:--:|------|
| 1 | 授权蓝牙权限后，DeviceScreen 可扫描到附近郊狼 V3 设备 |
| 2 | 点击扫描结果中的设备 → 连接成功 → B0 定时器启动 |
| 3 | 点已连接设备 → 跳转 CoyoteTestScreen |
| 4 | A 通道 [+] 按住 → 强度连续增加（每秒+2），松手停止 |
| 5 | B 通道 [－] 按住 → 强度连续减少，最低到 0 |
| 6 | 强度变化时有实际体感反馈 |
| 7 | 进度条和数值标签及时更新（≤100ms 延迟） |
| 8 | 点击安全停止 → 两通道强度立即归零 → 体感消失 |
| 9 | 点击解锁 → 恢复可调节 |
| 10 | B1 设备回报强度显示与实际一致 |
| 11 | 断连时面板自动 safetyOn + 提示"设备已断开" |
| 12 | 退出面板或切换设备 → 前设备 B0 定时器停止 |
| 13 | 反复连接/断开 5 次无崩溃、无内存泄漏 |
| 14 | CoyoteV3Adapter 实现 DeviceProtocolAdapter 接口（Phase 5 可直接复用） |
