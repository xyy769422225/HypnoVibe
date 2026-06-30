# Phase 5.5 详细设计：通道体系重构 + 最简脚本创建

> 阶段目标：(1) 重构通道/配置/映射体系，使郊狼与 LoveSpouse 的通道行为与协议吻合；(2) 在打轴编辑器界面提供最简脚本创建能力，产出可播放测试的 `.hvscript`  
> 前置条件：Phase 4/5 已完成（DeviceProtocolAdapter、CoyoteV3Adapter、LoveSpouseAdapter 已实现）  
> 产出物：可端到端测试的 APK——创建设备配置（含郊狼+LoveSpouse 通道）→ 创建最简时间轴脚本 → 通道映射 → 连接设备并播放，验证郊狼 A/B 和 LoveSpouse 同时被时间轴驱动

---

## 目录

1. [设计目标与测试闭环](#1-设计目标与测试闭环)
2. [协议差异总结](#2-协议差异总结)
3. [设备类型描述符体系](#3-设备类型描述符体系)
4. [实体层重构](#4-实体层重构)
5. [通道映射重新设计](#5-通道映射重新设计)
6. [PlaybackCoordinator 通道路由](#6-playbackcoordinator-通道路由)
7. [时间轴脚本设计（自包含关键帧 + 标签）](#7-时间轴脚本设计自包含关键帧--标签)
8. [预加载与播放锁机制](#8-预加载与播放锁机制)
9. [waveMode 槽位填充规则（DG-LAB 协议利用）](#9-wavemode-槽位填充规则dg-lab-协议利用)
10. [端到端测试流程](#10-端到端测试流程)
11. [UI 变更](#11-ui-变更)
12. [数据迁移](#12-数据迁移)
13. [文件清单](#13-文件清单)
14. [验证标准](#14-验证标准)

---

## 1. 设计目标与测试闭环

### 1.1 为什么需要最简脚本创建

当前开发计划的依赖链：

```
通道重构 → 时间轴引擎 → 需要 .hvscript 文件 → 完整打轴编辑器 (Phase 8)
                                                      ↑
                                        中间还隔着 Phase 7（波形导入）
```

如果 Phase 6 时间轴引擎写完了但没有脚本文件，只能干瞪眼。因此本阶段在 `TimelineEditorScreen`（符阵）中提供一个**最简脚本创建模式**：

- 不依赖 Canvas 拖拽、波形预览、撤销重做
- 纯表单式操作：选通道 → 选波形类型 → 设起止时间 → 设强度
- 产出合法的 `.hvscript` JSON 文件
- 后续 Phase 7/8 可无缝升级为完整编辑器

### 1.2 端到端测试闭环

```
创建 DeviceConfig                    → 通道体系重构
  ├── "后穴" (coyote_v3)
  ├── "前部" (coyote_v3)
  └── "全局振动" (love_spouse)
        │
        ▼
创建 Playlist + 添加音频
        │
        ▼
TimelineEditorScreen（最简模式）     → 最简脚本创建
  ├── 为每个通道添加波形段
  └── 保存 .hvscript
        │
        ▼
ChannelMappingScreen                → 通道映射
  ├── "后穴" → 郊狼#1 A 通道
  ├── "前部" → 郊狼#1 B 通道
  └── "全局振动" → LoveSpouse（自动广播）
        │
        ▼
开启 LoveSpouse 广播 + 连接郊狼
        │
        ▼
播放                                → 所有通道同时驱动
  ├── 郊狼 A/B 通道按时间轴输出
  └── LoveSpouse 按时间轴振动
```

---

## 2. 协议差异总结

### 2.1 郊狼 (coyote_v3)

| 属性 | 值 |
|------|-----|
| 连接方式 | BLE GATT（配对→连接→服务发现） |
| 物理通道数 | 2（A 通道、B 通道） |
| 强度范围 | 0-200（每通道独立） |
| 控制粒度 | 每通道独立强度 + 4×25ms 波形槽位（频率+波形强度） |
| 通信周期 | 100ms（B0 指令携带 A+B 全部数据） |
| 反馈 | B1 Notify（序列号确认 + 强度回报） |
| **通道映射** | **必须**：配置通道 → 具体设备的 A 或 B |

### 2.2 Love Spouse (love_spouse)

| 属性 | 值 |
|------|-----|
| 连接方式 | BLE Advertising（无配对、无 GATT） |
| 物理通道数 | 1（单振动通道） |
| 强度范围 | 0-9 |
| 控制粒度 | 单一振动等级 |
| 通信周期 | 50ms |
| 反馈 | 无（纯广播） |
| **通道映射** | **无需**：广播开启后自动对所有玩具生效 |

### 2.3 对比总表

| 维度 | 郊狼 coyote_v3 | Love Spouse love_spouse |
|------|:---:|:---:|
| 连接模型 | GATT 连接 | BLE Advertising |
| 物理通道 | A + B（2 通道） | 振动（1 通道） |
| 强度范围 | 0-200 | 0-9 |
| 通道映射 | 需要（→ 物理 A/B） | 不需要（自动广播） |
| 多设备 | 每台独立 | 一对多广播 |
| 波形能力 | 频率+强度双维度 | 仅强度 |

---

## 3. 设备类型描述符体系

### 3.1 DeviceTypeDescriptor（新增，domain 层）

```java
package com.hypno.hypnovibe.domain;

import java.util.Collections;
import java.util.List;

/**
 * 设备类型的元信息描述符。
 * 每种设备类型在系统启动时注册一个不可变实例。
 * UI 通过此描述符动态生成界面元素（通道数、强度范围、标签等）。
 */
public class DeviceTypeDescriptor {

    private final String deviceType;                       // "coyote_v3", "love_spouse"
    private final String displayName;                      // "郊狼 DG-LAB V3"
    private final List<PhysicalChannelDef> physicalChannels;
    private final int strengthMin;
    private final int strengthMax;

    public enum ConnectionModel { CONNECTION, BROADCAST }
    private final ConnectionModel connectionModel;

    private final boolean requiresMapping;                 // BROADCAST 始终为 false
    private final int safeStrength;

    public DeviceTypeDescriptor(
            String deviceType, String displayName,
            List<PhysicalChannelDef> physicalChannels,
            int strengthMin, int strengthMax,
            ConnectionModel connectionModel,
            boolean requiresMapping, int safeStrength) {
        this.deviceType = deviceType;
        this.displayName = displayName;
        this.physicalChannels = Collections.unmodifiableList(physicalChannels);
        this.strengthMin = strengthMin;
        this.strengthMax = strengthMax;
        this.connectionModel = connectionModel;
        this.requiresMapping = requiresMapping;
        this.safeStrength = safeStrength;
    }

    public String getDeviceType() { return deviceType; }
    public String getDisplayName() { return displayName; }
    public List<PhysicalChannelDef> getPhysicalChannels() { return physicalChannels; }
    public int getStrengthMin() { return strengthMin; }
    public int getStrengthMax() { return strengthMax; }
    public ConnectionModel getConnectionModel() { return connectionModel; }
    public boolean requiresMapping() { return requiresMapping; }
    public int getSafeStrength() { return safeStrength; }
    public int getPhysicalChannelCount() { return physicalChannels.size(); }

    public static class PhysicalChannelDef {
        private final String channelKey;    // "A", "B", "vibrate"
        private final String displayName;   // "A 通道", "B 通道", "振动"

        public PhysicalChannelDef(String channelKey, String displayName) {
            this.channelKey = channelKey;
            this.displayName = displayName;
        }

        public String getChannelKey() { return channelKey; }
        public String getDisplayName() { return displayName; }
    }
}
```

### 3.2 预注册的描述符

```java
// DeviceTypeRegistry 中

/** 郊狼 V3 — BLE GATT 连接型，2 物理通道，需映射 */
COYOTE_V3 = new DeviceTypeDescriptor(
    "coyote_v3", "郊狼 DG-LAB V3",
    List.of(new PhysicalChannelDef("A", "A 通道"),
            new PhysicalChannelDef("B", "B 通道")),
    0, 200,
    DeviceTypeDescriptor.ConnectionModel.CONNECTION,
    true,   // requiresMapping
    0
);

/** Love Spouse — BLE 广播型，1 物理通道，无需映射 */
LOVE_SPOUSE = new DeviceTypeDescriptor(
    "love_spouse", "Love Spouse 震动玩具",
    List.of(new PhysicalChannelDef("vibrate", "振动")),
    0, 9,
    DeviceTypeDescriptor.ConnectionModel.BROADCAST,
    false,  // requiresMapping = false
    0
);
```

---

## 4. 实体层重构

### 4.1 DeviceConfig.ChannelDef 简化

移除 `minStrength`/`maxStrength`，这些值从 `DeviceTypeDescriptor` 动态获取。

```java
// DeviceConfig.java 中的 ChannelDef（重构后）

public static class ChannelDef {
    private String channelId;           // UUID
    private String channelName;         // 用户命名，如 "后穴"、"全局振动"
    private String deviceType;          // "coyote_v3" 或 "love_spouse"
    private int defaultStrength;        // 默认强度

    // 保留 @Deprecated minStrength/maxStrength 用于旧 JSON 兼容反序列化
    @Deprecated private int minStrength;
    @Deprecated private int maxStrength;
}
```

### 4.2 Playlist.ChannelMappingEntry 重构

区分物理映射和广播映射：

```java
// Playlist.java 中的 ChannelMappingEntry

public static class ChannelMappingEntry {
    private String configChannelId;            // DeviceConfig.ChannelDef.channelId
    private String mappingType;                // "physical" | "broadcast"

    // 仅 mappingType = "physical"
    private String targetDeviceMac;
    private String targetPhysicalChannelKey;   // "A", "B"

    // 仅 mappingType = "broadcast" — 无需额外字段

    /** 创建物理映射（郊狼等） */
    public static ChannelMappingEntry physical(
            String configChannelId, String targetDeviceMac,
            String targetPhysicalChannelKey) { ... }

    /** 创建广播映射（LoveSpouse 等） */
    public static ChannelMappingEntry broadcast(String configChannelId) { ... }
}
```

### 4.3 旧实体清理

- `ChannelMapping.java` → 标记 `@Deprecated`，由 `Playlist.ChannelMappingEntry` 替代
- Phase 2 缺失的值对象 `ConfigId.java`/`ChannelId.java`/`Strength.java`/`TimeRange.java` 一并补上

---

## 5. 通道映射重新设计

### 5.1 映射策略

```
配置中的逻辑通道                    物理设备
┌──────────────────┐              ┌─────────────────────┐
│ "后穴"            │──映射到──▶  │ 郊狼#1 MAC:AA:BB  │
│ coyote_v3         │              │   A 通道            │
├──────────────────┤              │   B 通道            │
│ "前部"            │──映射到──▶  │                     │
│ coyote_v3         │              └─────────────────────┘
├──────────────────┤
│ "全局振动"         │──自动广播─▶  LoveSpouse 广播
│ love_spouse       │   (无需映射)   (覆盖范围内所有玩具)
└──────────────────┘
```

### 5.2 郊狼映射规则

- 一个郊狼设备的 A/B 各只能绑定一个配置通道
- N 个 coyote 通道需要 ceil(N/2) 台郊狼
- 未映射的郊狼通道：播放时静默跳过

### 5.3 LoveSpouse 广播规则

- 自动广播，无需映射（`mappingType = "broadcast"`）
- 每个配置最多 1 个 `love_spouse` 通道（协议限制）
- 广播开启即对所有范围内的玩具生效

### 5.4 ChannelMappingCoordinator（新增）

```java
package com.hypno.hypnovibe.app.manager;

/**
 * 通道映射协调器。
 * - validateMapping(): 校验映射完整性，区分 physical/broadcast
 * - routeSnapshot(): 将 TimelineSnapshot 路由到正确的 Adapter
 */
public class ChannelMappingCoordinator {

    private final DeviceTypeRegistry typeRegistry;

    /** 校验映射是否完整。返回未映射的通道名称列表，空 = 就绪。 */
    public List<String> validateMapping(
            List<DeviceConfig.ChannelDef> configChannels,
            Map<String, Playlist.ChannelMappingEntry> mapping,
            List<ConnectedDevice> connectedDevices) { ... }

    /** 将 TimelineSnapshot 按映射表路由到各 Adapter */
    public void routeSnapshot(
            TimelineSnapshot snapshot,
            Map<String, Playlist.ChannelMappingEntry> mapping,
            Map<String, ConnectedDevice> connectedDevices) { ... }
}
```

---

## 6. PlaybackCoordinator 通道路由

### 6.1 数据流

```
positionPoller (33ms)
    │
    └──► TimelineEngine.query(posMs)
            │
            ▼
         TimelineSnapshot { configChannelId → (data, offset) }
            │
            ▼
         ChannelMappingCoordinator.routeSnapshot()
            │
            ├── 郊狼#1 MAC:AA:BB  → adapter.updateSnapshot({"A": data_A, "B": data_B})
            └── LoveSpouse 广播   → adapter.updateSnapshot({"vibrate": data_vibrate})
```

- TimelineSnapshot 的 key 是 `configChannelId`（时间轴引用的逻辑通道 ID）
- ChannelMappingCoordinator 将逻辑通道 ID 转换为物理通道 key（"A"/"B"/"vibrate"）
- Adapter 内部将物理通道 key 的数据编码为协议指令

---

## 7. 时间轴脚本设计（自包含关键帧 + 标签）

### 7.1 设计原则

- **自包含**：关键帧直接嵌入所有参数值（strength、freq、waveMode、level），不引用外部波形名或预设。一个 `.hvscript` 文件即可在任何设备上独立播放
- **标签辅助编辑**：每个关键帧保留 `label` 字段，仅用于打轴编辑器识别和修改，不影响播放
- **运算效率优先**：关键帧按 `timeMs` 升序排列，运行时二分查找 O(log N)，无实时算法计算

### 7.2 脚本数据结构（TimelineScript）

```java
// domain/entity/TimelineScript.java（Phase 2 已有空壳，补充实现）

public class TimelineScript {
    private String scriptId;                  // UUID
    private String configId;                  // 引用的 DeviceConfig.id
    private long totalDurationMs;             // 关联音频的时长
    private List<ChannelTimeline> channels;   // 每个通道的关键帧序列
    private long createdAt;
    private long updatedAt;

    /** 单个通道的完整时间轴 — 按 timeMs 升序排列的关键帧数组 */
    public static class ChannelTimeline {
        private String channelId;                 // 作用于哪个配置通道
        private String deviceType;                // "dglab_v3" | "love_spouse"
        private List<Keyframe> keyframes;         // 必须按 timeMs 升序排列

        // 预加载时构建的索引（不序列化到 JSON）
        private transient long[] timeIndex;       // timeMs 数组用于二分查找
    }

    /** 单个关键帧 — 自包含所有运行参数 */
    public static class Keyframe {
        private long timeMs;             // 时间点（毫秒）
        private int strength;            // 强度 (0-100%，运行时映射到设备范围)
        private int freq;                // 频率 (10-1000, 仅 dglab 使用, love_spouse 忽略)
        private String waveMode;         // 槽位填充模式 (仅 dglab): "constant"|"pulse"|"rise"|"fall"|"wave"
        private int level;               // 振动等级 (0-9, 仅 love_spouse 使用, dglab 忽略)
        private String label;            // 标签（可选，编辑器用，播放时忽略）
    }
}
```

**关键帧自包含示例**：任何人拿到这个 `.hvscript`，不需要任何外部波形文件即可播放。

```json
{
  "scriptId": "uuid-script",
  "configId": "uuid-config",
  "totalDurationMs": 300000,
  "channels": [
    {
      "channelId": "ch-001",
      "deviceType": "dglab_v3",
      "keyframes": [
        { "timeMs": 0,     "strength": 0,   "freq": 100, "waveMode": "constant", "label": "开始静默" },
        { "timeMs": 10000, "strength": 80,  "freq": 80,  "waveMode": "breath",   "label": "渐入呼吸" },
        { "timeMs": 25000, "strength": 120, "freq": 60,  "waveMode": "pulse",    "label": "脉冲高潮" },
        { "timeMs": 40000, "strength": 0,   "freq": 100, "waveMode": "constant", "label": "结束" }
      ]
    },
    {
      "channelId": "ch-002",
      "deviceType": "dglab_v3",
      "keyframes": [
        { "timeMs": 0,     "strength": 0,   "freq": 120, "waveMode": "constant", "label": "开始" },
        { "timeMs": 5000,  "strength": 60,  "freq": 120, "waveMode": "rise",     "label": "渐强" },
        { "timeMs": 20000, "strength": 60,  "freq": 100, "waveMode": "fall",     "label": "渐弱" },
        { "timeMs": 35000, "strength": 0,   "freq": 100, "waveMode": "constant", "label": "结束" }
      ]
    },
    {
      "channelId": "ch-003",
      "deviceType": "love_spouse",
      "keyframes": [
        { "timeMs": 0,     "level": 0, "label": "开始" },
        { "timeMs": 3000,  "level": 4, "label": "中等振动" },
        { "timeMs": 15000, "level": 9, "label": "最大振动" },
        { "timeMs": 30000, "level": 0, "label": "结束" }
      ]
    }
  ]
}
```

### 7.3 运行时查询（O(log N) 二分查找）

预加载时构建索引数组：

```java
// TimelineEngine.java

public class TimelineEngine {
    // channelId → 预计算的时间索引 + 参数数组
    private Map<String, ChannelRuntime> runtimeMap;

    public static class ChannelRuntime {
        long[] timeIndex;         // 预加载时从 keyframes 提取，升序
        int[] strengthIndex;      // 同上
        int[] freqIndex;          // 同上（love_spouse 为 null）
        String[] waveModeIndex;   // 同上（love_spouse 为 null）
        int[] levelIndex;         // 同上（dglab 为 null）
        String deviceType;
    }

    /**
     * 查询 positionMs 时刻各通道的当前值。
     * 对每个通道：二分查找 timeIndex 中 ≤ positionMs 的最大 timeMs，
     * 返回该索引处的所有参数值。
     * 复杂度：O(log N) × 通道数，每 33ms 调用一次。
     */
    public TimelineSnapshot query(long positionMs) {
        TimelineSnapshot snapshot = new TimelineSnapshot();
        for (var entry : runtimeMap.entrySet()) {
            String channelId = entry.getKey();
            ChannelRuntime rt = entry.getValue();

            // 二分查找
            int idx = binarySearchFloor(rt.timeIndex, positionMs);
            if (idx < 0) continue; // positionMs 在第一个关键帧之前

            snapshot.put(channelId,
                rt.strengthIndex[idx],
                rt.freqIndex[idx],
                rt.waveModeIndex != null ? rt.waveModeIndex[idx] : null,
                rt.levelIndex != null ? rt.levelIndex[idx] : -1);
        }
        return snapshot;
    }

    /** 二分查找 ≤ target 的最大索引 */
    private int binarySearchFloor(long[] arr, long target) {
        int lo = 0, hi = arr.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] <= target) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return hi; // hi = -1 表示 target 小于所有元素
    }
}
```

### 7.4 关键帧的值保持行为（Step Hold）

相邻关键帧之间，值**保持不变**直到下一个关键帧覆盖：

```
关键帧:
  timeMs=0:     strength=0,  freq=100, waveMode="constant"
  timeMs=10000: strength=80, freq=80,  waveMode="breath"
  timeMs=25000: strength=0,  freq=100, waveMode="constant"

运行结果:
  0~10000ms:   strength=0,  freq=100, constant    ← 保持第一个关键帧的值
  10000~25000: strength=80, freq=80,  breath       ← 切换到第二个
  25000~end:   strength=0,  freq=100, constant     ← 切换到第三个
```

### 7.5 keyframe 生成：用户在编辑器中添加段

用户添加的不是单个关键帧，而是**段**（起始→结束 + 波形效果）。编辑器自动将段展开为多个关键帧：

```
用户输入:
  类型: 呼吸波形,  起始: 10:00,  结束: 15:00,  强度: 60%,  freq: 80

编辑器自动生成关键帧（每 500ms 一个采样点）:
  timeMs=600000:  strength=72, freq=80, waveMode="constant", label="渐入呼吸"
      （呼吸波形正弦曲线的采样值，每 500ms 一帧）
  timeMs=605000:  strength=68, freq=80, waveMode="constant", label="渐入呼吸"
  timeMs=610000:  strength=60, freq=80, waveMode="constant", label="渐入呼吸"
  ...
  timeMs=900000:  strength=0,  freq=80, waveMode="constant", label="渐入呼吸"

关键帧采样间隔: 500ms（== 5 个 B0 窗口），在数据量和精度间取平衡
```

### 7.6 最简脚本创建 UI（TimelineEditorScreen）

放在现有的 `TimelineEditorScreen`（符阵 Tab），当前是空壳。纯表单模式，不需要 Canvas 拖拽。

```
┌─────────────────────────────────────────────────┐
│  符阵 — 时间轴编辑                    [保存]      │
├─────────────────────────────────────────────────┤
│  配置: [测试配置 (3通道) ▼]                      │  ← 选择 DeviceConfig
│  音频时长: 10:30                                 │
├─────────────────────────────────────────────────┤
│                                                 │
│  ── 通道 "后穴" (DG-LAB V3, 0-200) ──           │
│  ┌───────────────────────────────────────────┐  │
│  │ 标签: 渐入呼吸  恒定  00:00→02:00  60%    │  │  ← 段列表
│  │       ████████████████████████░  [✕ 删除] │  │
│  │ 标签: 脉冲高潮  脉冲  02:00→05:00  80%    │  │
│  │       ██░░░██░░░██░░░██░░░██░░░  [✕ 删除] │  │
│  │                              [+ 添加段]   │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ── 通道 "全局振动" (Love Spouse, 0-9) ──       │
│  ┌───────────────────────────────────────────┐  │
│  │ 标签: 中等振动  恒定  00:00→10:30  50%    │  │
│  │       ████████████████████████░  [✕ 删除] │  │
│  │                              [+ 添加段]   │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
└─────────────────────────────────────────────────┘
```

### 7.7 添加段的弹窗

```
┌──────────────────────────────┐
│  添加波形段                   │
│                              │
│  标签: [渐入高潮__________]   │  ← 编辑器用，播放时忽略
│                              │
│  效果: [呼吸波形      ▼]     │  ← 效果决定展开后的关键帧值
│         ├─ 恒定              │
│         ├─ 呼吸              │
│         ├─ 脉冲              │
│         ├─ 渐强              │
│         └─ 渐弱              │
│                              │
│  起始时间: [02:00]           │
│  结束时间: [05:00]           │
│                              │
│  强度:     [====○======] 60% │  ← 基础强度 0-100%
│                              │
│  (仅 DG-LAB)                 │
│  频率:     [====○======] 80  │  ← 10-1000
│                              │
│              [取消]  [添加]   │
└──────────────────────────────┘
```

### 7.8 效果 → 关键帧展开规则

| 效果 | 关键帧生成方式 |
|------|---------------|
| **恒定** | 起止各 1 帧：起始帧=目标值，结束帧=0（归零） |
| **呼吸** | 每 500ms 采样一次正弦曲线 (period=4s)，值在 0~目标之间波动 |
| **脉冲** | 每 500ms 采样一次方波 (period=1s, duty=20%)，高/低交替 |
| **渐强** | 从 0 线性增长到目标值，每 500ms 一个阶梯 |
| **渐弱** | 从目标值线性衰减到 0，每 500ms 一个阶梯 |

> 效果只在编辑器层面是"用户选择的模板"。保存时展开为具体关键帧值。`.hvscript` 文件中不存在 `effect` 字段——只有展开后的 `keyframes` 数组。后续 Phase 8 可在加载时从关键帧反推标签和效果，实现编辑回显。

---

## 8. 预加载与播放锁机制

### 8.1 预加载流程

播放列表开始播放前，必须完整加载所有时间轴脚本到内存，构建高效索引。

```
PlaySessionVM.play()
    │
    ├── 0. 检查播放锁：isPlayingLocked() → 若锁住则拒绝
    │
    ├── 1. 加载所有时间轴脚本
    │       │
    │       ├── 遍历 playlist.tracks[]
    │       ├── 对每个 track: 读取 .hvscript JSON → TimelineScript 对象
    │       ├── 对每个 ChannelTimeline:
    │       │   ├── 提取 keyframes → 构建 timeIndex[] (long数组, 升序)
    │       │   ├── 构建 strengthIndex[] / freqIndex[] / waveModeIndex[] / levelIndex[]
    │       │   └── 存入 ChannelRuntime
    │       │
    │       └── 所有 channel 的 ChannelRuntime → TimelineEngine.runtimeMap
    │
    ├── 2. 加锁：setPlaybackLocked(true)
    │       ├── UI: 禁用 [+ 添加音频] 按钮
    │       ├── UI: 禁用时间轴切换
    │       └── UI: 禁用通道映射修改
    │
    ├── 3. 校验通道映射 → ChannelMappingCoordinator.validateMapping()
    │       └── 未映射 → 提示用户, 阻止播放
    │
    ├── 4. 启动 AudioEngine
    │
    └── 5. 启动 positionPoller (33ms)
            └── 每帧: AudioEngine.getPositionMs()
                      → TimelineEngine.query(positionMs)
                      → ChannelMappingCoordinator.routeSnapshot()
                      → Adapter.updateSnapshot()
```

### 8.2 播放锁状态机

```
锁状态: UNLOCKED ↔ LOCKED

UNLOCKED (允许变更):
  ├── 可以添加/删除曲目
  ├── 可以切换时间轴脚本
  ├── 可以修改通道映射
  └── 点击播放 → preload → LOCKED

LOCKED (禁止变更):
  ├── [+ 添加音频] 按钮禁用
  ├── 时间轴切换入口禁用
  ├── 通道映射入口禁用
  ├── 点击暂停 → UNLOCKED
  └── 曲目播放完毕/停止 → UNLOCKED
```

### 8.3 暂停时变更的处理

```
正在播放 (LOCKED)
    │
    ├── 用户点击暂停
    │   ├── AudioEngine.pause()
    │   ├── positionPoller 暂停
    │   └── setPlaybackLocked(false)  → UNLOCKED
    │
    ├── 用户添加了曲目 / 切换了时间轴
    │   └── 标记 pendingReload = true
    │
    └── 用户点击播放/恢复
        ├── if (pendingReload):
        │   ├── 重新执行完整 preload 流程
        │   ├── 使用 AudioEngine.getPositionMs() 作为起始位置
        │   └── pendingReload = false
        ├── setPlaybackLocked(true)  → LOCKED
        ├── AudioEngine.play()
        └── positionPoller 恢复
```

### 8.4 PlaySessionVM 锁相关 API

```java
// PlaySessionVM.java

public class PlaySessionVM extends ViewModel {

    private volatile boolean playbackLocked = false;
    private volatile boolean pendingReload = false;
    private final MutableStateFlow<Boolean> isLocked = StateFlowKt.MutableStateFlow(false);

    /** UI 订阅此状态控制按钮可用性 */
    public StateFlow<Boolean> getIsLocked() { return isLocked; }

    /** 完整预加载：加载所有时间轴脚本 + 构建索引 */
    private void preloadAll(Playlist playlist) {
        TimelineEngine engine = new TimelineEngine();
        for (Track track : playlist.getTracks()) {
            if (track.getTimelineScriptPath() == null) continue;
            TimelineScript script = timelineManager.load(track.getTimelineScriptPath());
            for (ChannelTimeline ct : script.getChannels()) {
                engine.registerChannel(ct);  // 内部构建 timeIndex 等数组
            }
        }
        this.timelineEngine = engine;
    }

    /** 播放（入口） */
    public void play() {
        if (playbackLocked) return;

        if (timelineEngine == null || pendingReload) {
            preloadAll(currentPlaylist.getValue());
            pendingReload = false;
        }

        // 校验映射
        List<String> unmapped = coordinator.validateMapping(...);
        if (!unmapped.isEmpty()) {
            errorMsg.setValue("以下通道未映射: " + String.join(", ", unmapped));
            return;
        }

        setPlaybackLocked(true);
        audioEngine.play();
        startPositionPoller();
    }

    /** 暂停 */
    public void pause() {
        audioEngine.pause();
        stopPositionPoller();
        setPlaybackLocked(false);
    }

    /** 添加曲目（暂停时允许，标记需要重新加载） */
    public void addTrack(String audioPath) {
        if (playbackLocked) {
            errorMsg.setValue("播放中无法添加曲目，请先暂停");
            return;
        }
        playlistManager.addTrack(currentPlaylistId, audioPath);
        pendingReload = true;
    }

    /** 切换时间轴（暂停时允许） */
    public void setTimelineScript(String trackId, String scriptPath) {
        if (playbackLocked) {
            errorMsg.setValue("播放中无法切换时间轴，请先暂停");
            return;
        }
        playlistManager.setTimelineScript(currentPlaylistId, trackId, scriptPath);
        pendingReload = true;
    }

    private void setPlaybackLocked(boolean locked) {
        this.playbackLocked = locked;
        isLocked.setValue(locked);
    }
}
```

### 8.5 预加载时的内存估算

一个 30 分钟的音频，按 500ms 采样间隔，每个通道约 3600 个关键帧：

```
单通道: 3600 帧 × (8+4+4+4+8_avg_label) ≈ 100KB
3 通道: ~300KB
索引数组: (3600 × 4 × 8) ≈ 115KB
总计: < 1MB
```

对于手机内存完全可忽略，运算效率优先于存储精简。

### 8.6 为什么不允许播放中变更

- **连续性**：时间轴索引在播放前一次性构建为数组，播放中修改会破坏二分查找的一致性
- **安全性**：如果在 B0 发送周期内切换时间轴，设备会收到不同脚本的混合指令
- **用户体验**：播放中切歌/切换时间轴应该在暂停时完成，避免体感跳跃

---

## 9. waveMode 槽位填充规则（DG-LAB 协议利用）

### 9.1 5 种 waveMode

DG-LAB B0 指令的每通道 4 个 25ms 槽位支持独立的波形强度值。`waveMode` 控制这 4 个槽位的强度分配模式。

以 `strength=100, freq=80` 为例（值均为协议换算后）：

| waveMode | 槽0 | 槽1 | 槽2 | 槽3 | B0 实际发送 |
|----------|:---:|:---:|:---:|:---:|------|
| `constant` | 100 | 100 | 100 | 100 | [80,80,80,80]×[100,100,100,100] |
| `pulse` | 100 | 50 | 25 | 0 | [80,80,80,80]×[100,50,25,0] |
| `rise` | 25 | 50 | 75 | 100 | [80,80,80,80]×[25,50,75,100] |
| `fall` | 100 | 75 | 50 | 25 | [80,80,80,80]×[100,75,50,25] |
| `wave` | 70 | 100 | 70 | 0 | [80,80,80,80]×[70,100,70,0] |

### 9.2 Adapter 内部换算

```java
// DGLabV3Adapter.onTimerTick() 内部

int protocolFreq = DGLabFrequencyConverter.toProtocol(keyframeFreq); // 10-1000 → 10-240
int waveStrength = keyframeStrength * 2;  // 0-100% → 0-200

int[] slotFreqs = {protocolFreq, protocolFreq, protocolFreq, protocolFreq};
int[] slotStrengths;

switch (waveMode) {
    case "constant": slotStrengths = new int[]{ws, ws, ws, ws}; break;
    case "pulse":    slotStrengths = new int[]{ws, ws/2, ws/4, 0}; break;
    case "rise":     slotStrengths = new int[]{ws/4, ws/2, ws*3/4, ws}; break;
    case "fall":     slotStrengths = new int[]{ws, ws*3/4, ws/2, ws/4}; break;
    case "wave":     slotStrengths = new int[]{ws*7/10, ws, ws*7/10, 0}; break;
    default:         slotStrengths = new int[]{ws, ws, ws, ws};
}

byte[] b0 = DGLabB0Builder.buildB0(
    true, seqNo, MODE_ABSOLUTE, MODE_ABSOLUTE,
    strengthA, strengthB,
    slotFreqs, slotStrengths,  // A 通道 4 槽位
    slotFreqsB, slotStrengthsB // B 通道 4 槽位
);
```

---

## 10. 端到端测试流程

### 10.1 测试准备

1. 确保有一台郊狼 V3 设备（名称前缀 `47L121000`）
2. 确保有一台或多台 LoveSpouse 兼容玩具（如 MuSe）
3. 准备一个 5-10 分钟的 MP3 音频文件

### 10.2 测试步骤

```
步骤1: 创建设备配置
  ├── 导航到 ConfigListScreen → [+ 新建]
  ├── 配置名称: "测试配置"
  ├── 通道 1: 名称 "后穴", 设备类型 "郊狼 DG-LAB V3", 默认强度 0
  ├── 通道 2: 名称 "前部", 设备类型 "郊狼 DG-LAB V3", 默认强度 0
  ├── 通道 3: 名称 "全局振动", 设备类型 "Love Spouse 震动玩具", 默认强度 0
  └── 保存

步骤2: 创建播放列表
  ├── 导航到 PlaylistScreen → [+ 新建]
  ├── 列表名: "测试播放列表"
  ├── 关联配置: "测试配置"
  ├── 添加音频: 选择准备好的 MP3
  └── 保存

步骤3: 创建时间轴脚本 (TimelineEditorScreen)
  ├── 进入符阵 Tab
  ├── 选择配置 "测试配置"
  ├── 为每个通道添加波形段:
  │   ├── "后穴": 恒定波形, 00:00→05:00, 强度 50%
  │   ├── "前部": 呼吸波形, 00:00→05:00, 强度 40%
  │   └── "全局振动": 脉冲波形, 00:00→05:00, 强度 30%
  └── [保存] → 生成 .hvscript 文件

步骤4: 通道映射
  ├── 从 PlaylistDetailScreen → ChannelMappingScreen
  ├── "后穴" → 选择已连接的郊狼 → A 通道
  ├── "前部" → 同一台郊狼 → B 通道
  └── "全局振动" → 自动广播（只读提示）

步骤5: 连接设备
  ├── 导航到 DeviceScreen
  ├── 扫描并连接郊狼 V3
  ├── 开启 LoveSpouse 广播
  └── 确认两台设备状态均为绿色

步骤6: 播放测试
  ├── 回到 PlaylistDetailScreen
  ├── 点击 ▶ 播放
  ├── 验证: 郊狼 A 通道有恒定体感
  ├── 验证: 郊狼 B 通道有呼吸般起伏体感
  ├── 验证: LoveSpouse 有脉冲振动
  ├── 拖动进度条 → seek → 三种体感同步跳变
  ├── 暂停 → 所有强度归零
  └── 恢复 → 三种体感恢复
```

### 10.3 验证检查点

| # | 检查项 | 预期结果 |
|:--:|------|------|
| 1 | 郊狼 A 通道恒定波形 | 恒定体感，与强度设定一致 |
| 2 | 郊狼 B 通道呼吸波形 | 约 4 秒周期起伏体感 |
| 3 | LoveSpouse 脉冲振动 | 每秒一下脉冲振动 |
| 4 | 拖动进度条 seek | 三种输出立即同步到新位置 |
| 5 | 暂停 | 所有设备强度归零，体感消失 |
| 6 | 恢复播放 | 从暂停位置继续，体感恢复 |
| 7 | B1 反馈 | 郊狼测试面板显示设备实际强度回报 |

---

## 11. UI 变更

### 11.1 ConfigEditorScreen 变更

- 设备类型下拉从 `DeviceTypeRegistry.getConfigurableTypes()` 动态生成
- 选择类型后展示元信息（物理通道数、强度范围、需映射）
- 移除 minStrength/maxStrength 手动输入
- 添加一个通道时检测是否已有 LoveSpouse，有则下拉中禁用该项

### 11.2 ChannelMappingScreen 重写

- 分组显示："需要映射的通道"（郊狼）↕ "自动广播通道"（LoveSpouse）
- 郊狼通道：设备下拉（已连接列表）+ 物理通道下拉（A/B）
- LoveSpouse 通道：灰色只读提示"广播型设备，开启后自动生效"
- 保存时生成 `ChannelMappingEntry` 列表

### 10.3 TimelineEditorScreen 重写

当前是空壳，重写为最简模式：
- 顶部：选择配置下拉 + 显示音频时长 + [保存]按钮
- 中部：按通道分组，每个通道列出其段列表
- 段列表项：显示波形类型图标、时间范围、强度条、删除按钮
- FAB 或 [+ 添加段] 按钮弹出段编辑对话框
- 段编辑对话框：波形类型下拉、起止时间输入、强度滑块

### 11.4 PlaySessionVM 增强

- 新增 `loadTimelineScript(String scriptPath)` 预加载脚本
- 新增 `getChannelMapping()` 获取当前播放列表的映射
- 播放时调用 `ChannelMappingCoordinator.routeSnapshot()`

---

## 12. 数据迁移

### 12.1 旧配置兼容

加载旧 `DeviceConfig` JSON 时：
- `minStrength`/`maxStrength` 字段被忽略（Gson 反序列化到 `@Deprecated` 字段）
- `deviceType` 保持不变
- `defaultStrength` 如果超出 `DeviceTypeDescriptor` 范围，钳制到合法值

### 12.2 旧 ChannelMapping 兼容

旧版 `Playlist.ChannelMappingEntry` 使用不同的字段名。加载时进行迁移：

```java
private void migrateOldMapping(Playlist playlist) {
    Map<String, ChannelMappingEntry> oldMapping = playlist.getChannelMapping();
    if (oldMapping == null || oldMapping.isEmpty()) return;
    // 旧字段: timelineChannelId + macAddress → 新字段: configChannelId + targetDeviceMac
    Map<String, ChannelMappingEntry> newMapping = new HashMap<>();
    for (var entry : oldMapping.entrySet()) {
        ChannelMappingEntry old = entry.getValue();
        if (old.getTimelineChannelId() != null && old.getMacAddress() != null) {
            ChannelMappingEntry ne = ChannelMappingEntry.physical(
                old.getTimelineChannelId(),
                old.getMacAddress(),
                "A"  // 默认占位，需用户重新指定
            );
            newMapping.put(old.getTimelineChannelId(), ne);
        }
    }
    playlist.setChannelMapping(newMapping);
}
```

---

## 13. 文件清单

### 13.1 新增文件（7 个）

```
app/src/main/java/com/hypno/hypnovibe/
├── domain/
│   ├── DeviceTypeDescriptor.java                # 设备类型描述符
│   └── value/
│       ├── ConfigId.java                         # 值对象（补 Phase 2 缺失）
│       ├── ChannelId.java                        # 值对象
│       ├── Strength.java                         # 值对象
│       └── TimeRange.java                        # 值对象
└── infrastructure/
    └── audio/
        └── WaveformGenerator.java                # 内置波形生成器
└── app/
    └── manager/
        └── ChannelMappingCoordinator.java         # 通道映射协调器
```

### 13.2 修改文件（13 个）

```
app/src/main/java/com/hypno/hypnovibe/
├── domain/
│   └── entity/
│       ├── DeviceConfig.java                     # ChannelDef 移除 minStrength/maxStrength
│       ├── Playlist.java                         # ChannelMappingEntry 重构
│       ├── TimelineScript.java                   # 补充 ScriptSegment 实现
│       └── ChannelMapping.java                   # 标记 @Deprecated
├── app/
│   ├── viewmodel/
│   │   ├── ConfigVM.java                         # 创建通道时使用 DeviceTypeDescriptor
│   │   ├── PlaySessionVM.java                    # 新增 preload/playbackLock 逻辑
│   │   └── TimelineEditorVM.java                 # 新增：脚本编辑状态管理
│   └── manager/
│       ├── ConfigManager.java                    # validate() 使用 DeviceTypeDescriptor
│       ├── PlaylistManager.java                  # 支持新映射结构
│       ├── DeviceTypeRegistry.java               # 注册 DeviceTypeDescriptor
│       └── TimelineManager.java                  # 新增：脚本保存/加载/展开效果
└── kotlin/.../ui/
    ├── screen/
    │   ├── config/
    │   │   └── ConfigEditorScreen.kt             # 设备类型选择动态化
    │   ├── playlist/
    │   │   └── ChannelMappingScreen.kt           # 重写：区分 physical/broadcast
    │   └── editor/
    │       ├── TimelineEditorScreen.kt           # 重写：最简脚本创建
    │       └── SegmentEditDialog.kt              # 新增：段编辑弹窗
    └── navigation/
        └── NavGraph.kt                           # TimelineEditorScreen 接入 ViewModel
```

---

## 14. 验证标准

### 14.1 通道体系重构

| # | 检查项 | 方法 |
|:--:|------|------|
| 1 | 创建配置 → 选择"郊狼 V3" → 显示"2 通道 (A/B)"、强度 0-200 | 手动 |
| 2 | 创建配置 → 选择"LoveSpouse" → 显示"1 通道 (振动)"、强度 0-9 | 手动 |
| 3 | 同一配置中不能添加超过 1 个 LoveSpouse 通道 | 手动 |
| 4 | 旧版配置 JSON 加载后强度范围自动适配 | 手动 |
| 5 | 通道映射页：郊狼通道显示设备+物理通道下拉 | 手动 |
| 6 | 通道映射页：LoveSpouse 通道显示"自动广播"提示 | 手动 |

### 14.2 最简脚本创建

| # | 检查项 | 方法 |
|:--:|------|------|
| 7 | 进入符阵 Tab → 可选择已有配置 → 显示配置的通道列表 | 手动 |
| 8 | 为每个通道添加波形段（恒定/呼吸/脉冲）→ 段出现在列表中 | 手动 |
| 9 | 段起止时间可编辑、可删除 | 手动 |
| 10 | 保存 → 生成 .hvscript 文件 → 文件存在于 {filesDir}/timelines/ | 手动 |
| 11 | 重新打开编辑器 → 上次保存的段完整显示 | 手动 |

### 14.3 预加载与播放锁

| # | 检查项 | 方法 |
|:--:|------|------|
| 12 | 播放时预加载完成 → 时间轴脚本已构建索引 → 二分查找可用 | 日志验证 |
| 13 | 播放中点击 [+ 添加音频] → 按钮禁用 | 手动 |
| 14 | 播放中尝试切换时间轴 → 提示"请先暂停" | 手动 |
| 15 | 暂停后添加曲目 → resume 时重新预加载 → 新曲目生效 | 手动 |
| 16 | 播放列表关联脚本 + 通道映射完成 → 点击播放 | 手动 |
| 17 | DG-LAB A 通道输出与脚本关键帧设定一致 | 体感验证 |
| 18 | DG-LAB B 通道输出与脚本关键帧设定一致 | 体感验证 |
| 19 | LoveSpouse 振动与脚本关键帧设定一致 | 体感验证 |
| 20 | 拖动进度条 seek → 所有设备立即同步到新位置 | 体感验证 |
| 21 | 暂停 → 所有设备归零 | 体感验证 |
| 22 | 恢复 → 所有设备恢复，时间轴从当前位置继续 | 体感验证 |

---

## 附录 A：DeviceTypeRegistry 完整代码

```java
package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.DeviceTypeDescriptor;
import com.hypno.hypnovibe.domain.DeviceTypeDescriptor.PhysicalChannelDef;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;
import com.hypno.hypnovibe.infrastructure.ble.adapter.coyote.CoyoteV3Adapter;
import com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse.LoveSpouseAdapter;

import java.util.*;
import java.util.function.Function;

public class DeviceTypeRegistry {

    private final Map<String, DeviceTypeDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, Function<String, DeviceProtocolAdapter>> factories = new LinkedHashMap<>();

    public DeviceTypeRegistry() {
        register(
            new DeviceTypeDescriptor(
                "coyote_v3", "郊狼 DG-LAB V3",
                List.of(new PhysicalChannelDef("A", "A 通道"),
                        new PhysicalChannelDef("B", "B 通道")),
                0, 200,
                DeviceTypeDescriptor.ConnectionModel.CONNECTION,
                true, 0
            ),
            CoyoteV3Adapter::new
        );

        register(
            new DeviceTypeDescriptor(
                "love_spouse", "Love Spouse 震动玩具",
                List.of(new PhysicalChannelDef("vibrate", "振动")),
                0, 9,
                DeviceTypeDescriptor.ConnectionModel.BROADCAST,
                false, 0
            ),
            LoveSpouseAdapter::new
        );
    }

    public void register(DeviceTypeDescriptor descriptor,
                         Function<String, DeviceProtocolAdapter> factory) {
        descriptors.put(descriptor.getDeviceType(), descriptor);
        factories.put(descriptor.getDeviceType(), factory);
    }

    public DeviceTypeDescriptor getTypeInfo(String deviceType) {
        return descriptors.get(deviceType);
    }

    public DeviceProtocolAdapter createAdapter(String deviceType, String deviceId) {
        Function<String, DeviceProtocolAdapter> f = factories.get(deviceType);
        return f != null ? f.apply(deviceId) : null;
    }

    public Collection<DeviceTypeDescriptor> getRegisteredTypes() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    public List<DeviceTypeDescriptor> getConfigurableTypes() {
        return List.of(
            descriptors.get("coyote_v3"),
            descriptors.get("love_spouse")
        );
    }
}
```

## 附录 B：ConfigManager.validate() 更新

```java
public List<String> validate(DeviceConfig config, DeviceTypeRegistry registry) {
    List<String> errors = new ArrayList<>();

    if (config.getName() == null || config.getName().trim().isEmpty()) {
        errors.add("配置名称不能为空");
    }

    if (config.getChannels() == null || config.getChannels().isEmpty()) {
        errors.add("至少需要定义一个通道");
        return errors;
    }

    Set<String> channelIds = new HashSet<>();
    Set<String> channelNames = new HashSet<>();
    int loveSpouseCount = 0;

    for (DeviceConfig.ChannelDef ch : config.getChannels()) {
        if (ch.getChannelName() == null || ch.getChannelName().trim().isEmpty()) {
            errors.add("通道名称不能为空");
        }

        DeviceTypeDescriptor desc = registry.getTypeInfo(ch.getDeviceType());
        if (desc == null) {
            errors.add("通道 \"" + ch.getChannelName() + "\" 的设备类型无效: " + ch.getDeviceType());
            continue;
        }

        if (ch.getDefaultStrength() < desc.getStrengthMin()
                || ch.getDefaultStrength() > desc.getStrengthMax()) {
            errors.add("通道 \"" + ch.getChannelName() + "\" 默认强度必须在 "
                + desc.getStrengthMin() + " 到 " + desc.getStrengthMax() + " 之间");
        }

        if ("love_spouse".equals(ch.getDeviceType())) {
            loveSpouseCount++;
            if (loveSpouseCount > 1) {
                errors.add("一个配置中最多只能有 1 个 LoveSpouse 通道");
            }
        }

        if (ch.getChannelId() != null && !channelIds.add(ch.getChannelId())) {
            errors.add("通道ID重复: " + ch.getChannelId());
        }
        if (ch.getChannelName() != null && !channelNames.add(ch.getChannelName())) {
            errors.add("通道名称重复: " + ch.getChannelName());
        }
    }

    return errors;
}
```

## 附录 C：测试用配置示例

```
配置名称: "测试配置"
通道 1: channelId=auto, channelName="后穴", deviceType="coyote_v3", defaultStrength=0
通道 2: channelId=auto, channelName="前部", deviceType="coyote_v3", defaultStrength=0
通道 3: channelId=auto, channelName="全局振动", deviceType="love_spouse", defaultStrength=0
```

保存后的 JSON (`configs/{id}.hvconfig`):
```json
{
  "id": "uuid-config",
  "name": "测试配置",
  "channels": [
    {"channelId":"ch-001","channelName":"后穴","deviceType":"coyote_v3","defaultStrength":0},
    {"channelId":"ch-002","channelName":"前部","deviceType":"coyote_v3","defaultStrength":0},
    {"channelId":"ch-003","channelName":"全局振动","deviceType":"love_spouse","defaultStrength":0}
  ],
  "createdAt": 1719000000000,
  "updatedAt": 1719000000000
}
```

测试用时间轴脚本 (`timelines/{id}.hvscript`):
```json
{
  "scriptId": "uuid-script",
  "configId": "uuid-config",
  "totalDurationMs": 300000,
  "segments": [
    {"segmentId":"seg-1","channelId":"ch-001","startTimeMs":0,"endTimeMs":300000,
     "waveformType":"constant","strength":50,"frequency":100},
    {"segmentId":"seg-2","channelId":"ch-002","startTimeMs":0,"endTimeMs":300000,
     "waveformType":"breath","strength":40,"frequency":80},
    {"segmentId":"seg-3","channelId":"ch-003","startTimeMs":0,"endTimeMs":300000,
     "waveformType":"pulse","strength":30,"frequency":100}
  ],
  "createdAt": 1719000000000,
  "updatedAt": 1719000000000
}
```
