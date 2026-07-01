package com.hypno.hypnovibe.ui.screen.device

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
    val scope = rememberCoroutineScope()

    val targetA by testVm.getTargetStrengthA().collectAsState()
    val targetB by testVm.getTargetStrengthB().collectAsState()
    val deviceA by testVm.getDeviceStrengthA().collectAsState()
    val deviceB by testVm.getDeviceStrengthB().collectAsState()
    val safetyOn by testVm.getSafetyOn().collectAsState()
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

    var tabIndex by remember { mutableIntStateOf(0) }

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

            when (tabIndex) {
                0 -> ChannelTabA(testVm, targetA, deviceA, channelAEnabled, safetyOn,
                    waveforms, selectedWaveA, playingA, progressA, context, scope)
                1 -> ChannelTabB(testVm, targetB, deviceB, channelBEnabled, safetyOn,
                    waveforms, selectedWaveB, playingB, progressB, context, scope)
            }
        }
    }
}

@Composable
private fun ChannelTabA(
    testVm: DGLabTestVM, target: Int, device: Int,
    enabled: Boolean, safetyOn: Boolean,
    waveforms: List<DGLabTestVM.WaveEntry>, selectedIdx: Int,
    playing: Boolean, progress: Float,
    context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 开关 + 强度条
        StoneCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("A 通道", color = GoldAncient, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { testVm.toggleChannelA() },
                    colors = SwitchDefaults.colors(checkedThumbColor = BloodRed, checkedTrackColor = BloodRed.copy(alpha = 0.3f)))
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HoldButton2(text = "－", enabled = !safetyOn, onChange = { testVm.decreaseChannelA() }, Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        LinearProgressIndicator(progress = target / 200f, modifier = Modifier.fillMaxWidth().height(6.dp), color = BloodRed, trackColor = LeatherBrown)
                        Text("$target/200", color = SilverGray, style = MaterialTheme.typography.labelMedium)
                        Text("回报: $device", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    HoldButton2(text = "＋", enabled = !safetyOn, onChange = { testVm.increaseChannelA() }, Modifier.size(40.dp), isPrimary = true)
                }
            }
        }

        GothicDivider()

        // 波形列表
        Text("波形选择", color = SilverGray, style = MaterialTheme.typography.titleMedium)
        if (waveforms.isEmpty()) {
            Text("点击右上角导入 .pulse 文件", color = DarkGray, style = MaterialTheme.typography.labelSmall)
        } else {
            // 当前播放进度
            if (playing) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth(), color = GoldAncient, trackColor = LeatherBrown)
                Text("播放中...", color = GoldAncient, style = MaterialTheme.typography.labelSmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 300.dp)) {
                itemsIndexed(waveforms) { idx, wave ->
                    val isSelected = idx == selectedIdx
                    StoneCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier
                                else Modifier
                            )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSelected) Icons.Filled.MusicNote else Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = if (isSelected) BloodRed else DarkGray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(wave.name, color = if (isSelected) GoldAncient else SilverGray)
                                val frames = wave.data.getTotalFrames()
                                val durationSec = frames / 10
                                Text("${frames}帧 / ~${durationSec}s", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { testVm.selectAndPlayWaveformA(idx) }) {
                                Text(if (isSelected && playing) "播放中" else "播放", color = BloodRed)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        DungeonButton(
            text = if (safetyOn) "🔓 解锁安全锁" else "🔴 紧急停止",
            variant = if (safetyOn) ButtonVariant.SECONDARY else ButtonVariant.DANGER,
            onClick = {
                if (safetyOn) { testVm.unlockSafety(); Toast.makeText(context, "已解锁", Toast.LENGTH_SHORT).show() }
                else { testVm.emergencyStop(); Toast.makeText(context, "已停止", Toast.LENGTH_SHORT).show() }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChannelTabB(
    testVm: DGLabTestVM, target: Int, device: Int,
    enabled: Boolean, safetyOn: Boolean,
    waveforms: List<DGLabTestVM.WaveEntry>, selectedIdx: Int,
    playing: Boolean, progress: Float,
    context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StoneCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("B 通道", color = GoldAncient, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { testVm.toggleChannelB() },
                    colors = SwitchDefaults.colors(checkedThumbColor = BloodRed, checkedTrackColor = BloodRed.copy(alpha = 0.3f)))
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HoldButton2(text = "－", enabled = !safetyOn, onChange = { testVm.decreaseChannelB() }, Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        LinearProgressIndicator(progress = target / 200f, modifier = Modifier.fillMaxWidth().height(6.dp), color = BloodRed, trackColor = LeatherBrown)
                        Text("$target/200", color = SilverGray, style = MaterialTheme.typography.labelMedium)
                        Text("回报: $device", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    HoldButton2(text = "＋", enabled = !safetyOn, onChange = { testVm.increaseChannelB() }, Modifier.size(40.dp), isPrimary = true)
                }
            }
        }

        GothicDivider()

        Text("波形选择", color = SilverGray, style = MaterialTheme.typography.titleMedium)
        if (waveforms.isEmpty()) {
            Text("点击右上角导入 .pulse 文件", color = DarkGray, style = MaterialTheme.typography.labelSmall)
        } else {
            if (playing) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth(), color = GoldAncient, trackColor = LeatherBrown)
                Text("播放中...", color = GoldAncient, style = MaterialTheme.typography.labelSmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 300.dp)) {
                itemsIndexed(waveforms) { idx, wave ->
                    val isSelected = idx == selectedIdx
                    StoneCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isSelected) Icons.Filled.MusicNote else Icons.Filled.GraphicEq,
                                contentDescription = null, tint = if (isSelected) BloodRed else DarkGray,
                                modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(wave.name, color = if (isSelected) GoldAncient else SilverGray)
                                Text("${wave.data.getTotalFrames()}帧 / ~${wave.data.getTotalFrames() / 10}s", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { testVm.selectAndPlayWaveformB(idx) }) {
                                Text(if (isSelected && playing) "播放中" else "播放", color = BloodRed)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        DungeonButton(
            text = if (safetyOn) "🔓 解锁安全锁" else "🔴 紧急停止",
            variant = if (safetyOn) ButtonVariant.SECONDARY else ButtonVariant.DANGER,
            onClick = {
                if (safetyOn) { testVm.unlockSafety(); Toast.makeText(context, "已解锁", Toast.LENGTH_SHORT).show() }
                else { testVm.emergencyStop(); Toast.makeText(context, "已停止", Toast.LENGTH_SHORT).show() }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HoldButton2(
    text: String, enabled: Boolean, onChange: () -> Unit,
    modifier: Modifier = Modifier, isPrimary: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val bg = when { !enabled -> DarkGray; isPrimary -> BloodRed; else -> DarkCopper }
    Surface(color = bg, shape = MaterialTheme.shapes.small,
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                pressed = true; onChange()
                val job = scope.launch(Dispatchers.Default) { delay(500); while (isActive && pressed) { onChange(); delay(500) } }
                tryAwaitRelease(); pressed = false; job.cancel()
            })
        }) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = SilverGray, fontSize = 16.sp) }
    }
}
