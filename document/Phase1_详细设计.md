# Phase 1 详细设计：项目骨架 + 界面框架

> 阶段目标：建立完整工程结构、黑色地牢主题系统、所有页面可导航浏览  
> 产出物：可安装的 APK，4 Tab 切换正常，10 个 Screen 显示标题和占位内容

---

## 目录

1. [工程搭建](#1-工程搭建)
2. [主题系统设计](#2-主题系统设计)
3. [通用组件设计](#3-通用组件设计)
4. [导航架构设计](#4-导航架构设计)
5. [各页面详细规格](#5-各页面详细规格)
6. [资源文件清单](#6-资源文件清单)
7. [Phase 1 完成检查清单](#7-phase-1-完成检查清单)

---

## 1. 工程搭建

### 1.1 包结构

```
app/src/main/
├── java/com/hypno/hypnovibe/          # Java 核心
│   ├── app/                           # 应用层（Phase 1 仅建空包）
│   │   ├── viewmodel/
│   │   ├── manager/
│   │   └── usecase/
│   ├── domain/                        # 领域层（Phase 1 仅建空包）
│   │   ├── entity/
│   │   ├── value/
│   │   └── repository/
│   ├── infrastructure/                # 基础设施层（Phase 1 仅建空包）
│   │   ├── audio/
│   │   ├── ble/adapter/
│   │   ├── io/
│   │   └── persistence/
│   ├── service/                       # Android Service（Phase 1 仅建空包）
│   └── di/                            # 依赖注入（Phase 1 仅建空包）
│
├── kotlin/com/hypno/hypnovibe/        # Kotlin UI
│   └── ui/
│       ├── theme/                     # 主题系统（Phase 1 重点）
│       │   ├── Color.kt
│       │   ├── Type.kt
│       │   ├── Shape.kt
│       │   └── DungeonTheme.kt
│       ├── navigation/                # 导航
│       │   ├── Screen.kt             # 路由枚举
│       │   ├── BottomNavBar.kt       # 底部导航栏
│       │   └── NavGraph.kt           # 路由图
│       ├── screen/                    # 页面（10 个 Screen）
│       │   ├── home/HomeScreen.kt
│       │   ├── playlist/
│       │   │   ├── PlaylistScreen.kt
│       │   │   ├── ChannelMappingScreen.kt
│       │   │   └── PlayerBar.kt
│       │   ├── editor/
│       │   │   ├── TimelineEditorScreen.kt
│       │   │   └── SegmentPropertySheet.kt
│       │   ├── device/
│       │   │   ├── DeviceScreen.kt
│       │   │   ├── DeviceTypePicker.kt
│       │   │   └── BleScanSheet.kt
│       │   ├── config/
│       │   │   ├── ConfigListScreen.kt
│       │   │   └── ConfigEditorScreen.kt
│       │   ├── preset/
│       │   │   ├── PresetListScreen.kt
│       │   │   └── PresetDetailScreen.kt
│       │   └── coyote/CoyoteTestScreen.kt
│       └── component/                 # 通用组件
│           ├── DungeonButton.kt
│           ├── DungeonSlider.kt
│           ├── RunicProgressBar.kt
│       │   ├── StoneCard.kt
│       │   └── GothicDivider.kt
│
├── proto/                             # Protobuf（Phase 1 仅占位目录）
│   └── .gitkeep
│
└── res/
    ├── font/
    │   ├── cinzel_bold.ttf
    │   ├── cinzel_regular.ttf
    │   ├── spectral_regular.ttf
    │   ├── spectral_semibold.ttf
    │   └── jetbrains_mono_regular.ttf
    ├── drawable/
    │   ├── stone_texture.png          # 石壁噪点纹理（128×128 tileable）
    │   └── ic_launcher_foreground.xml # 符文风格启动图标
    ├── values/
    │   └── strings.xml
    └── raw/
        └── .gitkeep
```

### 1.2 build.gradle.kts (app)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hypno.hypnovibe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hypno.hypnovibe"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase1"
    }

    buildFeatures { compose = true }
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Protobuf（Phase 6 启用，Phase 1 仅声明依赖）
    implementation(libs.protobuf.javalite)
}
```

### 1.3 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 蓝牙 (Phase 4 需要) -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH" 
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" />
    
    <!-- 前台服务 (Phase 9 需要) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <!-- 读取音频文件 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.HypnoVibe"
        android:supportsRtl="true">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <!-- Phase 9 启用 -->
        <!-- <service
            android:name=".service.PlaybackService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" /> -->
    </application>
</manifest>
```

---

## 2. 主题系统设计

### 2.1 Color.kt

```kotlin
package com.hypno.hypnovibe.ui.theme

import androidx.compose.ui.graphics.Color

// ===== 背景 =====
val AbyssBlack     = Color(0xFF0D0D0D)   // 主背景
val DarkStoneBrown = Color(0xFF1A1410)   // 卡片/面板
val StoneGray      = Color(0xFF231F1A)   // 次要面板

// ===== 边框/分割 =====
val DarkCopper     = Color(0xFF3A2A1A)   // 边框、分割线
val LeatherBrown   = Color(0xFF6B4423)   // 滑块轨道、标签

// ===== 强调色 =====
val BloodRed       = Color(0xFF8B0000)   // 主强调、播放、录制
val GoldAncient    = Color(0xFFD4A574)   // 标题、激活态
val DarkPurple     = Color(0xFF7B68EE)   // 波形段、次强调
val AlertRed       = Color(0xFFFF4444)   // 安全停止、错误
val DarkGreen      = Color(0xFF006400)   // 连接成功

// ===== 文本 =====
val SilverGray     = Color(0xFFC0C0C0)   // 次要文本、禁用
val DarkGray       = Color(0xFF808080)   // 占位符、非活跃
```

### 2.2 Type.kt

```kotlin
package com.hypno.hypnovibe.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hypno.hypnovibe.R

val CinzelFamily = FontFamily(
    Font(R.font.cinzel_regular, FontWeight.Normal),
    Font(R.font.cinzel_bold, FontWeight.Bold)
)

val SpectralFamily = FontFamily(
    Font(R.font.spectral_regular, FontWeight.Normal),
    Font(R.font.spectral_semibold, FontWeight.SemiBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

val DungeonTypography = Typography(
    // 标题 — 哥特体强调
    headlineLarge = TextStyle(
        fontFamily = CinzelFamily, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp, color = GoldAncient
    ),
    headlineMedium = TextStyle(
        fontFamily = CinzelFamily, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 26.sp, color = GoldAncient
    ),
    
    // 正文 — 优雅衬线
    bodyLarge = TextStyle(
        fontFamily = SpectralFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp, color = SilverGray
    ),
    bodyMedium = TextStyle(
        fontFamily = SpectralFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 20.sp, color = SilverGray
    ),
    
    // 标签/按钮 — 半粗衬线
    labelLarge = TextStyle(
        fontFamily = SpectralFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 18.sp, color = SilverGray
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 16.sp, color = DarkGray
    ),
)
```

### 2.3 Shape.kt

```kotlin
package com.hypno.hypnovibe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DungeonShapes = Shapes(
    // 卡片 — 微弧，仿石刻棱角
    extraSmall = RoundedCornerShape(2.dp),
    small      = RoundedCornerShape(4.dp),
    medium     = RoundedCornerShape(4.dp),
    large      = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
```

### 2.4 DungeonTheme.kt

```kotlin
package com.hypno.hypnovibe.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DungeonDarkColorScheme = darkColorScheme(
    primary            = BloodRed,
    onPrimary          = SilverGray,
    primaryContainer   = BloodRed.copy(alpha = 0.3f),
    secondary          = DarkPurple,
    onSecondary        = SilverGray,
    secondaryContainer = DarkPurple.copy(alpha = 0.3f),
    tertiary           = GoldAncient,
    background         = AbyssBlack,
    onBackground       = SilverGray,
    surface            = DarkStoneBrown,
    onSurface          = SilverGray,
    surfaceVariant     = StoneGray,
    onSurfaceVariant   = DarkGray,
    outline            = DarkCopper,
    error              = AlertRed,
    onError            = SilverGray,
)

@Composable
fun DungeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DungeonDarkColorScheme,
        typography  = DungeonTypography,
        shapes      = DungeonShapes,
        content     = content
    )
}
```

---

## 3. 通用组件设计

### 3.1 DungeonButton.kt

两种变体：血红色主要按钮 + 暗石棕次要按钮。

```kotlin
@Composable
fun DungeonButton(
    text: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
)

enum class ButtonVariant { PRIMARY, SECONDARY, DANGER }
```

| 变体 | 背景 | 文字 | 边框 |
|------|------|------|------|
| PRIMARY | BloodRed | SilverGray | 无 |
| SECONDARY | DarkStoneBrown | SilverGray | DarkCopper, 1dp |
| DANGER | AlertRed | SilverGray | 无 |

### 3.2 StoneCard.kt

带暗铜色边框的石刻质感卡片，用于分组展示内容。

```kotlin
@Composable
fun StoneCard(
    modifier: Modifier = Modifier,
    title: String? = null,          // 可选哥特风标题
    content: @Composable ColumnScope.() -> Unit
)
```

样式：`DarkStoneBrown` 背景 + `RoundedCornerShape(4.dp)` + 1dp `DarkCopper` 边框 + 8dp 内边距。有 title 时顶部显示 Cinzel 字体的小标题。

### 3.3 DungeonSlider.kt

皮革棕色轨道 + 血红色已填充部分 + 圆点拇指。

```kotlin
@Composable
fun DungeonSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    label: String = "",
    modifier: Modifier = Modifier
)
```

### 3.4 RunicProgressBar.kt

古铜色未填充 + 暗紫色已填充，带圆角端点。

```kotlin
@Composable
fun RunicProgressBar(
    progress: Float,        // 0.0 ~ 1.0
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
)
```

### 3.5 GothicDivider.kt

两端渐隐的暗铜色分割线。

```kotlin
@Composable
fun GothicDivider(modifier: Modifier = Modifier)
```

实现：`Brush.horizontalGradient(DarkCopper.copy(alpha=0f) → DarkCopper → DarkCopper.copy(alpha=0f))`

---

## 4. 导航架构设计

### 4.1 Screen 路由枚举

```kotlin
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    // === 4 个 Tab ===
    object Home     : Screen("home",     "祭坛", Icons.Filled.TempleBuddhist)
    object Playlist : Screen("playlist", "卷轴", Icons.Filled.AutoStories)
    object Editor   : Screen("editor",   "符阵", Icons.Filled.Draw)
    object Device   : Screen("device",   "法器", Icons.Filled.Bolt)

    // === 子页面（非 Tab，通过 NavGraph 导航） ===
    object ChannelMapping : Screen("channel_mapping", "", Icons.Filled.Settings)
    object ConfigList     : Screen("config_list",     "", Icons.Filled.List)
    object ConfigEditor   : Screen("config_editor/{configId}", "", Icons.Filled.Edit)
    object PresetList     : Screen("preset_list",     "", Icons.Filled.LibraryMusic)
    object PresetDetail   : Screen("preset_detail/{presetId}", "", Icons.Filled.Info)
    object CoyoteTest     : Screen("coyote_test",     "", Icons.Filled.FlashOn)
}
```

### 4.2 BottomNavBar.kt

```kotlin
@Composable
fun BottomNavBar(navController: NavController) {
    NavigationBar(
        containerColor = DarkStoneBrown,
        contentColor = SilverGray
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val tabs = listOf(Screen.Home, Screen.Playlist, Screen.Editor, Screen.Device)
        tabs.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BloodRed,
                    selectedTextColor = GoldAncient,
                    unselectedIconColor = DarkGray,
                    unselectedTextColor = DarkGray,
                    indicatorColor = BloodRed.copy(alpha = 0.2f)
                )
            )
        }
    }
}
```

### 4.3 NavGraph.kt

```kotlin
@Composable
fun NavGraph(navController: NavController) {
    NavHost(navController, startDestination = Screen.Home.route) {
        
        // === Tab 页面 ===
        composable(Screen.Home.route)     { HomeScreen() }
        composable(Screen.Playlist.route) { PlaylistScreen(navController) }
        composable(Screen.Editor.route)   { TimelineEditorScreen() }
        composable(Screen.Device.route)   { DeviceScreen(navController) }
        
        // === 子页面 ===
        composable(Screen.ChannelMapping.route) { ChannelMappingScreen() }
        composable(Screen.ConfigList.route)     { ConfigListScreen(navController) }
        composable(
            route = Screen.ConfigEditor.route,
            arguments = listOf(navArgument("configId") { defaultValue = "new" })
        ) { backStackEntry ->
            val configId = backStackEntry.arguments?.getString("configId") ?: "new"
            ConfigEditorScreen(configId)
        }
        composable(Screen.PresetList.route)   { PresetListScreen() }
        composable(
            route = Screen.PresetDetail.route,
            arguments = listOf(navArgument("presetId") { })
        ) { backStackEntry ->
            val presetId = backStackEntry.arguments?.getString("presetId") ?: ""
            PresetDetailScreen(presetId)
        }
        composable(Screen.CoyoteTest.route) { CoyoteTestScreen() }
    }
}
```

---

## 5. 各页面详细规格

### 5.1 MainActivity + 主框架

```
┌─────────────────────────────────────────────┐
│ Scaffold (AbyssBlack 背景)                  │
│                                             │
│  当前页面内容区                               │
│  (通过 NavGraph 切换)                        │
│                                             │
├─────────────────────────────────────────────┤
│  BottomNavBar: [祭坛] [卷轴] [符阵] [法器]   │
│  选中: 血红色图标+古金色文字                  │
│  未选中: 暗灰色                              │
└─────────────────────────────────────────────┘
```

### 5.2 HomeScreen（祭坛）

**布局**：居中欢迎页 + 快捷入口卡片。

```
┌─────────────────────────────┐
│                             │
│       ⚡ HypnoVibe          │  ← Cinzel Bold 24sp, GoldAncient
│   黑暗地牢祭坛              │  ← Spectral 14sp, SilverGray
│                             │
│  ┌─────────────────────────┐│
│  │ 📜 打开播放列表         ││  ← StoneCard + DungeonButton
│  │ 继续上次的仪式...       ││     Phase 2 接入实际功能
│  └─────────────────────────┘│
│  ┌─────────────────────────┐│
│  │ ⚡ 快速测试             ││
│  │ 直接控制郊狼            ││
│  └─────────────────────────┘│
│  ┌─────────────────────────┐│
│  │ ⚙️ 设备配置             ││
│  │ 管理通道和设备           ││
│  └─────────────────────────┘│
│                             │
└─────────────────────────────┘
```

Phase 1 状态：三个 StoneCard 显示文字但按钮不可点击（或 Toast "即将开放"）。

### 5.3 PlaylistScreen（卷轴）

**布局**：列表 + FAB（添加按钮）。

```
┌─────────────────────────────┐
│  播放列表          [+ 添加] │  ← TopAppBar, DungeonButton(PRIMARY) 做添加
├─────────────────────────────┤
│  ┌─────────────────────────┐│
│  │ 🎵 催眠引导              ││  ← StoneCard 列表项
│  │    00:00 / 10:00        ││     Phase 2 显示真实数据
│  │    ⚠️ 时长不匹配         ││
│  └─────────────────────────┘│
│  ┌─────────────────────────┐│
│  │ 🎵 深度恍惚              ││
│  │    00:00 / 15:00        ││
│  └─────────────────────────┘│
│  ┌─────────────────────────┐│
│  │ 🎵 环境背景音 (无时间轴)  ││
│  └─────────────────────────┘│
│                             │
│  GothicDivider              │
│  播放模式: LOOP_LIST  [▼]   │
│                             │
├─────────────────────────────┤
│  PlayerBar（底部常驻）       │  ← Phase 3 可交互
│  ⏮   ▶   ⏭   ═══进度═══   │
└─────────────────────────────┘
```

Phase 1 状态：空列表 + 居中文字"尚无播放列表，点击 + 创建"，+ 按钮 Toast "即将开放"。PlayerBar 显示但按钮禁用。

### 5.4 TimelineEditorScreen（符阵）

**布局**：顶部工具栏 + 空画布区域（Phase 8 实现真正编辑器）。

```
┌─────────────────────────────┐
│  [保存] [撤销] [重做] [缩放]│  ← 工具栏，按钮禁用
├──────────┬──────────────────┤
│  通道    │  时间标尺         │
│  列表    │  0:00  1:00 ...  │
│          │                  │
│  ┌────┐  │  (波形画布区域)   │  ← AbyssBlack 空区域
│  │后穴│  │                  │     Phase 8 实现 Canvas 绘制
│  │    │  │                  │
│  │前部│  │                  │
│  └────┘  │                  │
│          │                  │
│  波形    │                  │
│  预设库  │                  │
│  (空)    │                  │
├──────────┴──────────────────┤
│  [▶播放] [⏸暂停] ══进度══  │  ← 禁用
└─────────────────────────────┘
```

Phase 1 状态：工具栏可见但全部禁用，画布区域显示居中文字"选择音频文件和设备配置以开始编辑"。PlayerBar 显示但按钮禁用。

### 5.5 DeviceScreen（法器）

**布局**：三区显示（已连接/历史/扫描结果）。

```
┌─────────────────────────────┐
│  设备管理          [添加]   │  ← TopAppBar
├─────────────────────────────┤
│  ● 已连接设备               │
│  ┌─────────────────────────┐│
│  │ (暂无已连接设备)         ││  ← Phase 1 空状态
│  └─────────────────────────┘│
│                             │
│  ○ 历史设备                 │
│  ┌─────────────────────────┐│
│  │ (暂无历史设备)           ││
│  └─────────────────────────┘│
│                             │
│  ○ 扫描结果                 │
│  ┌─────────────────────────┐│
│  │ 点击 [添加] 开始扫描     ││
│  └─────────────────────────┘│
└─────────────────────────────┘
```

Phase 1 状态：三区显示空状态文字。[添加] 按钮 Toast "即将开放"。

### 5.6 子页面（所有子页面 Phase 1 仅空壳）

| Screen | Phase 1 内容 |
|--------|-------------|
| `ChannelMappingScreen` | 标题"通道映射" + 居中文字"请先在播放列表中打开一个播放列表" |
| `ConfigListScreen` | 标题"设备配置列表" + 空列表 + FAB → Toast |
| `ConfigEditorScreen` | 标题"编辑配置" + 灰显表单（通道名/设备类型/强度范围输入框禁用） |
| `PresetListScreen` | 标题"波形预设库" + 分类标签（呼吸/脉冲/持续/高潮，灰显） + 空列表 |
| `PresetDetailScreen` | 标题"波形详情" + 占位曲线图区域 |
| `CoyoteTestScreen` | 标题"强度测试" + 两个 DungeonSlider(0-200) + 安全停止红色按钮 + 预设区 |

---

## 6. 资源文件清单

### 6.1 字体文件（放入 `res/font/`）

| 文件 | 来源 | 用途 |
|------|------|------|
| `cinzel_bold.ttf` | Google Fonts | 标题 Bold |
| `cinzel_regular.ttf` | Google Fonts | 副标题 |
| `spectral_regular.ttf` | Google Fonts | 正文 |
| `spectral_semibold.ttf` | Google Fonts | 标签/按钮 |
| `jetbrains_mono_regular.ttf` | Google Fonts | 时间码/数值 |

### 6.2 纹理与图标

| 文件 | 说明 |
|------|------|
| `stone_texture.png` | 128×128 tileable 噪点纹理，半透明叠在背景上（可选，用渐变模拟也可） |
| `ic_launcher_foreground.xml` | 符文风格启动图标（简化版：圆圈+闪电符文） |

### 6.3 strings.xml

```xml
<resources>
    <string name="app_name">HypnoVibe</string>
    <string name="tab_home">祭坛</string>
    <string name="tab_playlist">卷轴</string>
    <string name="tab_editor">符阵</string>
    <string name="tab_device">法器</string>
    
    <string name="empty_playlist">尚无播放列表</string>
    <string name="empty_device">暂无已连接设备</string>
    <string name="empty_history">暂无历史设备</string>
    <string name="coming_soon">即将开放</string>
    <string name="select_audio_and_config">选择音频文件和设备配置以开始编辑</string>
    <string name="open_playlist_first">请先在播放列表中打开一个播放列表</string>
</resources>
```

---

## 7. Phase 1 完成检查清单

| # | 检查项 | 判定 |
|:--:|------|:--:|
| 1 | APK 可编译安装到设备 | ☐ |
| 2 | 启动后看到黑色地牢风格主界面（AbyssBlack 背景） | ☐ |
| 3 | 底部 4 个 Tab 可切换：祭坛/卷轴/符阵/法器 | ☐ |
| 4 | 选中 Tab 显示血红色图标 + 古金色文字 | ☐ |
| 5 | HomeScreen 显示欢迎标题 + 3 个快捷卡片 | ☐ |
| 6 | PlaylistScreen 显示空列表提示 + 底部 PlayerBar（禁用） | ☐ |
| 7 | TimelineEditorScreen 显示工具栏 + 空画布区域 | ☐ |
| 8 | DeviceScreen 显示三区空状态 | ☐ |
| 9 | 从 PlaylistScreen 可导航到 ChannelMappingScreen | ☐ |
| 10 | 从 DeviceScreen 可导航到 ConfigList → ConfigEditor | ☐ |
| 11 | 从 Editor 可导航到 PresetList → PresetDetail | ☐ |
| 12 | 从 DeviceScreen 可导航到 CoyoteTestScreen | ☐ |
| 13 | 所有子页面显示标题占位文字，无崩溃 | ☐ |
| 14 | GothicDivider 两端渐隐效果正常 | ☐ |
| 15 | StoneCard 显示暗石棕背景 + 暗铜色边框 | ☐ |
| 16 | 字体：标题 Cinzel Bold、正文 Spectral、时间码 JetBrains Mono | ☐ |
