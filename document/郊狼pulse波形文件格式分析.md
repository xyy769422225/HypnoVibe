# 郊狼 .pulse 波形文件格式分析文档

> 版本：v1.0  
> 来源：DG-LAB 官方 APP (com.bjsm.dungeonlabs) 反编译 + 官方波形样本 + DG-LAB 蓝牙协议开源仓库  
> 用途：为 HypnoVibe 实现 .pulse 波形导入和内置波形支持提供完整参考

---

## 一、文件格式概览

官方 APP 支持两种 .pulse 格式（由 `Pluse.java` 的 `pulseType` 字段区分）：

| pulseType | 格式标识 | 说明 |
|:---:|------|------|
| 0 | `Dungeonlab+pulse:` | 主格式（JSON区段型），频率+强度在同一个 section 内 |
| 2 | `Dungeonlab+csv:` | CSV 区段型，频率和强度分离到两个 points 序列 |

**导出文件名格式**：`pulse-{waveNameEn}-{7位随机数}.pulse`

---

## 二、Type 0 格式详解（Dungeonlab+pulse）

### 2.1 整体结构

```
Dungeonlab+pulse:L,playRate,ZY=A0,B0,J0,PC0,JIE0/point1,point2,...+section+...
```

每个 section 以 `+section+` 分隔，整个文件是一条不分行的文本。

### 2.2 文件头参数

```
Dungeonlab+pulse:L,playRate,ZY=
```

| 参数 | 范围 | 默认值 | 说明 |
|------|:---:|:---:|------|
| L | 0-100 | 0 | 节间间隔 |
| playRate | 1/2/4 | 1 | 播放倍速（3 视为 1） |
| ZY | 1-16 | 8 | 占空比 |

### 2.3 Section 段结构

```
A,B,J,PC,JIE/point1,point2,...,pointN
```

| 字段 | 范围 | 说明 |
|------|:---:|------|
| A | 0-83 | 起始频率索引（映射到 freqArray） |
| B | 0-83 | 结束频率索引 |
| J | 0-99 | 强度值 |
| PC | 1-4 | 渐变类型：1=无渐变，2=对数渐变，3=线性渐变，4=指数渐变 |
| JIE | 0/1 | 节标记：0=节内（跟随上一段），1=新节开始 |

### 2.4 波形点（point）

每个 point 格式：`yValue-anchor`

| 字段 | 范围 | 说明 |
|------|:---:|------|
| yValue | 0.00-100.00 | 波形强度百分比（保留2位小数） |
| anchor | 0/1 | 首尾点固定为1（必须锚定），中间点可以为0 |

**约束**：
- 每个 section 的点数范围：2-500
- 每个 section 的 `J`（强度）和 points 的 `yValue` 相乘得到最终输出强度
- 文件最多 10 个 section

### 2.5 实际样本

```
Dungeonlab+pulse:10,34,20,1,1/100.00-1,0.00-1,100.00-1,98.40-0,96.75-0,95.15-1,
71.95-0,48.75-0,25.60-0,2.40-1+section+26,20,20,1,1/95.40-1,96.90-0,98.45-0,100.00-1
+section+0,20,20,1,0/0.00-1,100.00-1
```

解释：
- L=10, playRate=34(→1表示默认), ZY=20
- Section 0：A=1, B=1, J=1, PC=1, JIE=1 → 10个point的新节
- Section 1：A=26, B=20, J=1, PC=1, JIE=1 → 4个point的新节
- Section 2：A=0, B=20, J=1, PC=0, JIE=0 → 2个point的节内延续

---

## 三、Type 2 格式详解（Dungeonlab+csv）

### 3.1 整体结构

```
Dungeonlab+csv:L=freqPoints/strengthPoints+section+freqPoints/strengthPoints+...
```

频率和强度分离到两个独立的点坐标数组。

### 3.2 Section 结构

```
freq1-anchor,freq2-anchor,.../strength1-anchor,strength2-anchor,...
```

与 type 0 不同，type 2 **没有 A/B/J/PC/JIE 参数**，渐变和节控制通过点坐标数组长度隐式表达。

---

## 四、频率数组（freqArray）

官方 APP 定义了一个 84 个元素的频率数组，A/B 字段是数组索引（0-83），映射到实际的波形频率值（10-1000Hz）。

索引 0-83 对应的频率值（Hz），按以下规则生成：

```
A/B 转换为频率：
- A/B 值本身即为协议频率值（10-240 范围），直接作为 V3 B0 指令的波形频率
- 但实际输出时，APP 将协议频率值通过 Frequency 公式（X=频率, Y=0）转换为 V2 格式
```

**关键发现**：A/B 的值域是 0-83，直接用作**协议频率值**（10-240 范围的压缩映射）。播放引擎（`classicP0`）`this.freqArray[pulseSection.getA()]` 取到的就是协议频率值。

---

## 五、播放引擎核心逻辑（classicP0）

```java
// PulseDataPL.classicP0(Pluse pluse) 伪代码重构
public void classicP0(Pluse pulse) {
    // 1. 获取当前 section
    PulseSection section = pulse.getPulseData().get(currentSectionIndex);

    // 2. 取出频率 A → B，计算渐变插值
    int startFreq = freqArray[section.getA()];   // 协议频率值 (10-240)
    int endFreq = freqArray[section.getB()];

    // 3. 取出强度（J 字段）
    int strength = restArray[section.getJ()];     // J映射到实际强度值

    // 4. 根据 PC 渐变类型计算当前帧频率
    int currentFreq;
    switch (section.getPC()) {
        case 1: currentFreq = startFreq; break;           // 无渐变
        case 2: currentFreq = logInterpolate(...); break;  // 对数渐变
        case 3: currentFreq = linearInterpolate(...); break; // 线性渐变
        case 4: currentFreq = expInterpolate(...); break;   // 指数渐变
    }

    // 5. 从 points 列表取当前帧强度
    float waveStrength = section.getPoints().get(frameIndex).getY();

    // 6. 组合为最终输出字节
    int x = (int) Math.pow(currentFreq / 1000.0, 0.5) * pulse.getZY();
    int y = currentFreq - x;
    int z = (int) waveStrength / 5;
    byte[] output = buildPwmBytes(x, y, z);  // 转换为 V2 PWM 指令字节
}
```

**播放时序**：
- 每个 section 的 `C`（点数）为播放总帧数
- 每帧在 100ms 定时器中执行一次（对齐郊狼输出窗口）
- JIE=1 处开始新节，控制跳到下一节

---

## 六、官方内置波形（资源文件）

官方 APK 没有将 .pulse 波形内嵌在 `res/raw` 中，而是通过以下方式提供：

1. **云服务器拉取**：`CloudPulseManager` 从 `https://dungeon-server.com/` 下载波形数据
2. **数据库初始化**：`DataBaseHelper.initPluse()` 首次启动时初始化
3. **文件系统**：波形存储在 `{appDir}/pulses/`，临时文件在 `{appDir}/pulses/temp/`

内置波形数据硬编码在 `Constants.DEFAULT_DEVICE_UPDATE_DATA` 等常量中（JSON 格式的配置），不在本项目范围内。

---

## 七、导入流程

```
1. 用户选择文件（GET_CONTENT，不限制 .pulse 后缀）
   ↓
2. UtilExKt.copyFileToInternalStorage() 复制到 PULSE_CREATE_TEMP
   ↓
3. Pluse.createByFile() 读取文件内容
   ↓
4. 检测文件头：
   - startsWith("Dungeonlab+pulse:") → analysisPulse0() → pulseType=0
   - startsWith("Dungeonlab+csv:")   → analysisPulse2() → pulseType=2
   ↓
5. 解析为 PulseSection 列表存入数据库（Room, tableName="pluse"）
   ↓
6. PulseDataPL.setPulse() 设置到播放引擎
   ↓
7. classicP0()/classicP2() 每 100ms 生成一帧波形数据
   ↓
8. 通过 BLE (0x150A) 发送到设备
```

---

## 八、HypnoVibe 集成建议

### 8.1 .pulse 文件导入

需实现解析器，支持 `pulseType=0` 格式的最低限度：

1. 读取文件内容为字符串
2. 检测 `Dungeonlab+pulse:` 头
3. 解析 `L,playRate,ZY=` 参数
4. 按 `+section+` 分割各段
5. 每段按 `/` 分割参数与波形点
6. 参数按 `,` 分割得到 A,B,J,PC,JIE
7. 波形点按 `,` 分割，每个 `yValue-anchor` 转换为 `(float, int)`

### 8.2 波形到郊狼指令的转换

播放时需要将 .pulse 数据转换为 V2/V3 协议指令：

**V3 模式**（推荐，我们的主要路径）：
```
每 100ms 取一帧：
- 频率 = freqArray[A]（起始）→ freqArray[B]（结束），按 PC 类型渐变插值
- 强度 = J值 × points[yIndex].yValue / 100.0  （J和波形强度叠乘）
- 直接构造 B0 指令（20 bytes），频率填入 4 个 25ms 槽位
```

**V2 模式**：
```
每 100ms 取一帧：
- 同样的频率/强度计算
- 通过 Frequency公式 转换为 X,Y：
  X = pow(freq/1000, 0.5) * ZY
  Y = freq - X
  Z = strength / 5  （脉冲宽度，受 V2 限制 0-31）
- 写入 PWM_AB2(强度) + PWM_A34/PWM_B34(波形)
```

### 8.3 内置波形

建议将部分常用波形（呼吸、潮汐等）的 .pulse 数据硬编码为字符串常量，通过相同解析器加载。

### 8.4 关键数据模型

```kotlin
// Pulse 文件整体
data class PulseData(
    val L: Int,                    // 节间间隔 0-100
    val playRate: Int,             // 播放倍速 1/2/4
    val ZY: Int,                   // 占空比 1-16
    val pulseType: Int,            // 0=Dungeonlab+pulse, 2=Dungeonlab+csv
    val waveName: String,          // 波形名称
    val sections: List<PulseSection>
)

// 波形段
data class PulseSection(
    val A: Int,                    // 起始频率索引 0-83
    val B: Int,                    // 结束频率索引 0-83
    val J: Int,                    // 强度值 0-99
    val PC: Int,                   // 渐变类型 1-4
    val JIE: Int,                  // 节标记 0/1
    val points: List<PulsePoint>   // 波形点列表
)

// 波形点
data class PulsePoint(
    val y: Float,                  // 波形强度 0.0-100.0
    val anchor: Int                // 锚点标记 0/1
)
```

### 8.5 文件选择

导入时不需要限制 `.pulse` 后缀（官方 APP 也不限制），直接 `GET_CONTENT` + `*/*` MIME type，由文件头 `Dungeonlab+pulse:` 自动识别。

---

## 九、参考代码位置

| 组件 | 文件路径 |
|------|---------|
| Pulse 实体 | `F:\JadxProject\dglab\src\main\java\com\bjsm\base\database\entity\Pluse.java` |
| PulseSection 内部类 | `Pluse.java:158-291` |
| PulseShapeBean | `F:\JadxProject\dglab\src\main\java\com\bjsm\base\database\entity\PulseShapeBean.java` |
| analysisPulse0() | `Pluse.java:560-804` |
| data2FileContent() | `Pluse.java:1027-1115` |
| classicP0() 播放 | `F:\JadxProject\dglab\src\main\java\com\bjsm\base\execution\PulseDataPL.java:296-460` |
| classicP2() 播放 | `F:\JadxProject\dglab\src\main\java\com\bjsm\base\execution\PulseDataPL.java:1919-1972` |
| 文件导入入口 | `F:\JadxProject\dglab\src\main\java\com\bjsm\dungeonlabs\ui\fragment\PluseFragment.java:2306` |
| 文件复制 | `F:\JadxProject\dglab\src\main\java\com\bjsm\dungeonlabs\global\UtilExKt.java:121` |
| 云端 Pulse 管理 | `F:\JadxProject\dglab\src\main\java\com\bjsm\dungeonlabs\cloud\CloudPulseManager.java` |
| 官方波形样本 | `C:\Users\pc\Downloads\狼的波形\*.pulse` |
| V3 协议文档 | `F:\GitProject\dglab-bluetooth-protocol\coyote\v3\README.md` |
| V2 协议文档 | `F:\GitProject\dglab-bluetooth-protocol\coyote\v2\README.md` |
