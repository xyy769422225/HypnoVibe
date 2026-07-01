package com.hypno.hypnovibe.ui.screen.device

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hypno.hypnovibe.app.viewmodel.DGLabTestVM
import com.hypno.hypnovibe.infrastructure.ble.adapter.dglab.DGLabController
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * DG-LAB 测试面板。
 * Tab 页切换 A/B 通道，每通道独立：开关、强度、波形选择播放。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DGLabTestScreen(deviceId: String, navController: NavController) {
    val deviceVm = rememberDeviceManagerVM()
    val testVm: DGLabTestVM = viewModel()
    val context = LocalContext.current

    val targetA by testVm.getTargetStrengthA().collectAsState()
    val targetB by testVm.getTargetStrengthB().collectAsState()
    val deviceA by testVm.getDeviceStrengthA().collectAsState()
    val deviceB by testVm.getDeviceStrengthB().collectAsState()
    val isConnected by testVm.getIsConnected().collectAsState()
    val deviceName by testVm.getDeviceName().collectAsState()
    val channelAEnabled by testVm.getChannelAEnabled().collectAsState()
    val channelBEnabled by testVm.getChannelBEnabled().collectAsState()
    val waveforms by testVm.getWaveforms().collectAsState()
    val selectedWaveA by testVm.getSelectedWaveIndexA().collectAsState()
    val selectedWaveB by testVm.getSelectedWaveIndexB().collectAsState()
    val playingA by testVm.getIsPlayingA().collectAsState()
    val playingB by testVm.getIsPlayingB().collectAsState()
    val progressA by testVm.getProgressA().collectAsState()
    val progressB by testVm.getProgressB().collectAsState()

    val softLimitA by testVm.getSoftLimitA().collectAsState()
    val softLimitB by testVm.getSoftLimitB().collectAsState()
    val bal1A by testVm.getBalance1A().collectAsState()
    val bal1B by testVm.getBalance1B().collectAsState()
    val bal2A by testVm.getBalance2A().collectAsState()
    val bal2B by testVm.getBalance2B().collectAsState()

    var tabIndex by remember { mutableIntStateOf(0) }
    var showBfSettings by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) {
        val connected = deviceVm.findDevice(deviceId)
        if (connected != null && connected.adapter is DGLabController) {
            testVm.setController(connected.adapter as DGLabController, connected.name)
        } else {
            Toast.makeText(context, "设备未找到", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    DisposableEffect(Unit) {
        onDispose { testVm.emergencyStop() }
    }

    // 文件导入
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()
            val name = uri.lastPathSegment ?: "导入波形"
            if (testVm.importPulse(content, name)) {
                Toast.makeText(context, "导入成功: $name", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "解析失败，请确认是 .pulse 文件", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName ?: "DG-LAB 测试", color = GoldAncient) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = SilverGray)
                    }
                },
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.FileOpen, "导入波形", tint = GoldAncient)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isConnected) {
                StoneCard(modifier = Modifier.padding(16.dp)) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("设备未连接", color = AlertRed)
                    }
                }
                return@Scaffold
            }

            // A/B Tab 切换
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = DarkStoneBrown,
                contentColor = GoldAncient
            ) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 },
                    text = { Text("⚡ A 通道") },
                    selectedContentColor = BloodRed,
                    unselectedContentColor = DarkGray)
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 },
                    text = { Text("⚡ B 通道") },
                    selectedContentColor = BloodRed,
                    unselectedContentColor = DarkGray)
            }

            // BF 参数调节（可折叠）
            BfSettingsSection(
                testVm = testVm,
                showBfSettings = showBfSettings,
                onToggle = { showBfSettings = !showBfSettings },
                softLimitA = softLimitA, softLimitB = softLimitB,
                bal1A = bal1A, bal1B = bal1B,
                bal2A = bal2A, bal2B = bal2B
            )

            when (tabIndex) {
                0 -> ChannelTabA(testVm, targetA, deviceA, channelAEnabled,
                    waveforms, selectedWaveA, playingA, progressA)
                1 -> ChannelTabB(testVm, targetB, deviceB, channelBEnabled,
                    waveforms, selectedWaveB, playingB, progressB)
            }
        }
    }
}

/**
 * BF 参数调节面板（可折叠）。
 * 软上限 0-200，平衡参数 0-255。修改后立即持久化并写入设备。
 */
@Composable
private fun BfSettingsSection(
    testVm: DGLabTestVM,
    showBfSettings: Boolean,
    onToggle: () -> Unit,
    softLimitA: Int, softLimitB: Int,
    bal1A: Int, bal1B: Int,
    bal2A: Int, bal2B: Int
) {
    Column {
        // 折叠/展开按钮
        StoneCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable { onToggle() }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BF 参数设置",
                    color = GoldAncient,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (showBfSettings) "▲ 收起" else "▼ 展开",
                    color = SilverGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        AnimatedVisibility(
            visible = showBfSettings,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 软上限
                Text("通道强度软上限", color = SilverGray, style = MaterialTheme.typography.labelMedium)
                BfSliderRow("A通道上限", softLimitA, 0..200, "200") { testVm.setSoftLimitA(it) }
                BfSliderRow("B通道上限", softLimitB, 0..200, "200") { testVm.setSoftLimitB(it) }

                GothicDivider()

                // 频率平衡参数 1
                Text("频率平衡参数 1（低频冲击感）", color = SilverGray, style = MaterialTheme.typography.labelMedium)
                BfSliderRow("A通道", bal1A, 0..255, "255") { testVm.setBalance1A(it) }
                BfSliderRow("B通道", bal1B, 0..255, "255") { testVm.setBalance1B(it) }

                GothicDivider()

                // 频率平衡参数 2
                Text("频率平衡参数 2（低频刺激感）", color = SilverGray, style = MaterialTheme.typography.labelMedium)
                BfSliderRow("A通道", bal2A, 0..255, "255") { testVm.setBalance2A(it) }
                BfSliderRow("B通道", bal2B, 0..255, "255") { testVm.setBalance2B(it) }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** BF 单行滑条 */
@Composable
private fun BfSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    maxLabel: String,
    onValueChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = DarkGray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = GoldAncient,
                activeTrackColor = GoldAncient,
                inactiveTrackColor = LeatherBrown
            )
        )
        Text(
            "$value/$maxLabel",
            color = SilverGray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun ChannelTabA(
    testVm: DGLabTestVM, target: Int, device: Int,
    enabled: Boolean,
    waveforms: List<DGLabTestVM.WaveEntry>, selectedIdx: Int,
    playing: Boolean, progress: Float
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 控制面板：启动/停止 + 强度
        StoneCard {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("A 通道", color = GoldAncient, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { if (it) testVm.startChannelA() else testVm.stopChannelA() },
                        colors = SwitchDefaults.colors(checkedThumbColor = BloodRed, checkedTrackColor = BloodRed.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 启动 / 停止按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DungeonButton(
                        text = "▶ 启动",
                        onClick = { testVm.startChannelA() },
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                        enabled = !enabled
                    )
                    DungeonButton(
                        text = "■ 停止",
                        onClick = { testVm.stopChannelA() },
                        variant = ButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                        enabled = enabled
                    )
                }

                // 强度控制（通道启动后可见）
                if (enabled) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AccelHoldButton(
                            text = "－",
                            onChange = { testVm.decreaseChannelA() },
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Slider(
                                value = target.toFloat(),
                                onValueChange = { testVm.setStrengthA(it.toInt()) },
                                valueRange = 0f..200f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = BloodRed,
                                    activeTrackColor = BloodRed,
                                    inactiveTrackColor = LeatherBrown
                                )
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "$target/200",
                                    color = SilverGray,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    "回报: $device",
                                    color = DarkGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        AccelHoldButton(
                            text = "＋",
                            onChange = { testVm.increaseChannelA() },
                            modifier = Modifier.size(44.dp),
                            isPrimary = true
                        )
                    }
                }
            }
        }

        GothicDivider()

        // 波形列表
        ChannelWaveformList(
            waveforms = waveforms,
            selectedIdx = selectedIdx,
            playing = playing,
            progress = progress,
            onPlay = { testVm.selectAndPlayWaveformA(it) }
        )
    }
}

@Composable
private fun ChannelTabB(
    testVm: DGLabTestVM, target: Int, device: Int,
    enabled: Boolean,
    waveforms: List<DGLabTestVM.WaveEntry>, selectedIdx: Int,
    playing: Boolean, progress: Float
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 控制面板：启动/停止 + 强度
        StoneCard {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("B 通道", color = GoldAncient, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { if (it) testVm.startChannelB() else testVm.stopChannelB() },
                        colors = SwitchDefaults.colors(checkedThumbColor = BloodRed, checkedTrackColor = BloodRed.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 启动 / 停止按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DungeonButton(
                        text = "▶ 启动",
                        onClick = { testVm.startChannelB() },
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                        enabled = !enabled
                    )
                    DungeonButton(
                        text = "■ 停止",
                        onClick = { testVm.stopChannelB() },
                        variant = ButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                        enabled = enabled
                    )
                }

                // 强度控制（通道启动后可见）
                if (enabled) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AccelHoldButton(
                            text = "－",
                            onChange = { testVm.decreaseChannelB() },
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Slider(
                                value = target.toFloat(),
                                onValueChange = { testVm.setStrengthB(it.toInt()) },
                                valueRange = 0f..200f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = BloodRed,
                                    activeTrackColor = BloodRed,
                                    inactiveTrackColor = LeatherBrown
                                )
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "$target/200",
                                    color = SilverGray,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    "回报: $device",
                                    color = DarkGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        AccelHoldButton(
                            text = "＋",
                            onChange = { testVm.increaseChannelB() },
                            modifier = Modifier.size(44.dp),
                            isPrimary = true
                        )
                    }
                }
            }
        }

        GothicDivider()

        // 波形列表
        ChannelWaveformList(
            waveforms = waveforms,
            selectedIdx = selectedIdx,
            playing = playing,
            progress = progress,
            onPlay = { testVm.selectAndPlayWaveformB(it) }
        )
    }
}

/**
 * 加速长按按钮：点击变化1，按住500ms后开始重复，
 * 从500ms/次逐渐加速到200ms/次（约3秒内达到最快速度）。
 */
@Composable
private fun AccelHoldButton(
    text: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val bg = if (isPrimary) BloodRed else DarkCopper
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    onChange()
                    // 初始等待 500ms 后开始重复
                    val released = withTimeoutOrNull(500) { tryAwaitRelease(); true }
                    if (released == null) {
                        val job = scope.launch(Dispatchers.Default) {
                            val startTime = System.currentTimeMillis()
                            val initialDelay = 500L
                            val minDelay = 200L
                            val rampDuration = 3000L // 3秒内加速到最快
                            while (isActive && pressed) {
                                val elapsed = System.currentTimeMillis() - startTime
                                val progress = (elapsed.toFloat() / rampDuration).coerceIn(0f, 1f)
                                val delayMs = (initialDelay - (initialDelay - minDelay) * progress).toLong()
                                onChange()
                                delay(delayMs)
                            }
                        }
                        tryAwaitRelease()
                        pressed = false
                        job.cancel()
                    } else {
                        pressed = false
                    }
                }
            )
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = SilverGray, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 紧凑型波形列表：小条目、播放中波形换色高亮。
 */
@Composable
private fun ChannelWaveformList(
    waveforms: List<DGLabTestVM.WaveEntry>,
    selectedIdx: Int,
    playing: Boolean,
    progress: Float,
    onPlay: (Int) -> Unit
) {
    Text("波形选择", color = SilverGray, style = MaterialTheme.typography.titleMedium)

    if (waveforms.isEmpty()) {
        Text("点击右上角导入 .pulse 文件", color = DarkGray, style = MaterialTheme.typography.labelSmall)
        return
    }

    // 播放进度条
    if (playing) {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            color = GoldAncient,
            trackColor = LeatherBrown
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        waveforms.forEachIndexed { idx, wave ->
            val isSelected = idx == selectedIdx
            val isActive = isSelected && playing
            CompactWaveItem(
                wave = wave,
                isActive = isActive,
                isSelected = isSelected,
                onClick = { onPlay(idx) }
            )
        }
    }
}

@Composable
private fun CompactWaveItem(
    wave: DGLabTestVM.WaveEntry,
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isActive -> BloodRed.copy(alpha = 0.18f)
        isSelected -> DarkCopper.copy(alpha = 0.12f)
        else -> StoneGray.copy(alpha = 0.3f)
    }
    val borderColor = when {
        isActive -> BloodRed
        isSelected -> GoldAncient.copy(alpha = 0.4f)
        else -> DarkCopper.copy(alpha = 0.3f)
    }
    val nameColor = when {
        isActive -> GoldAncient
        isSelected -> SilverGray
        else -> DarkGray
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconColor = if (isActive) BloodRed else DarkGray
            Text(
                text = if (isActive) "▶" else "●",
                color = iconColor,
                fontSize = 11.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = wave.name,
                color = nameColor,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            val durationSec = wave.data.getTotalFrames() / 10
            Text(
                text = "~${durationSec}s",
                color = DarkGray,
                fontSize = 11.sp
            )
        }
    }
}
