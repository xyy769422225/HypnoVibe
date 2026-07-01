package com.hypno.hypnovibe.ui.screen.device

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hypno.hypnovibe.app.viewmodel.LoveSpouseTestVM
import com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse.LoveSpouseConstants
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*

/**
 * Love Spouse 测试面板（重构版）。
 * <p>
 * 三个控制区：
 * 1. 广播开关
 * 2. 振动强度 (0-9)
 * 3. 振动模式 (CateId 选择 + 命令按钮)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoveSpouseTestScreen(deviceId: String, navController: NavController) {
    val deviceVm = rememberDeviceManagerVM()
    val testVm: LoveSpouseTestVM = viewModel()
    val context = LocalContext.current

    val currentLevel by testVm.getCurrentLevel().collectAsState()
    val isAdvertising by testVm.getIsAdvertising().collectAsState()
    val deviceName by testVm.getDeviceName().collectAsState()
    val errorMsg by testVm.getErrorMsg().collectAsState()
    val selectedCateId by testVm.getSelectedCateId().collectAsState()
    val modeCommands by testVm.getCurrentModeCommands().collectAsState()
    val activeMode by testVm.getActiveMode().collectAsState()

    LaunchedEffect(deviceId) {
        val connected = deviceVm.findDevice(deviceId)
        val name = connected?.name ?: "Love Spouse"
        testVm.init(deviceVm, deviceId, name)
    }

    LaunchedEffect(errorMsg) {
        errorMsg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); testVm.clearError() }
    }

    // 离开页面时停止当前功能（不关闭广播）
    DisposableEffect(Unit) {
        onDispose {
            testVm.emergencyStop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName ?: "Love Spouse 测试", color = GoldAncient) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = SilverGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══ 1. 广播状态 + 开关 ═══
            StoneCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isAdvertising) "广播已开启" else "广播未开启",
                        color = if (isAdvertising) DarkGreen else DarkGray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isAdvertising) "设备正在接收信号" else "点击按钮开启广播",
                        color = DarkGray, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            DungeonButton(
                text = if (isAdvertising) "关闭广播" else "开启广播",
                variant = if (isAdvertising) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY,
                onClick = {
                    if (isAdvertising) testVm.stopBroadcast() else testVm.startBroadcast()
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (isAdvertising) {
                GothicDivider()

                // ═══ 2. 振动强度 ═══
                SectionTitle("振动强度")
                StoneCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (i in 0..9) {
                                Text(
                                    text = if (i == currentLevel) "●" else "○",
                                    color = if (i <= currentLevel) BloodRed else LeatherBrown,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        DungeonSlider(
                            value = currentLevel.toFloat(),
                            onValueChange = { testVm.setStrength(it.toInt()) },
                            valueRange = 0f..9f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("当前: $currentLevel / 9", color = SilverGray, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DungeonButton("弱", { testVm.setStrength(2) },
                        variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                    DungeonButton("中", { testVm.setStrength(5) },
                        variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                    DungeonButton("强", { testVm.setStrength(8) },
                        variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                }

                GothicDivider()

                // ═══ 3. 振动模式 ═══
                SectionTitle("振动模式")
                Text("设备类型 (CateId)", color = DarkGray, style = MaterialTheme.typography.labelSmall)

                // CateId 选择器
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in LoveSpouseConstants.getKnownCateIds()) {
                        val isSelected = id == selectedCateId
                        Surface(
                            onClick = { testVm.setCateId(id) },
                            color = if (isSelected) BloodRed else DarkStoneBrown,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "$id",
                                color = if (isSelected) SilverGray else DarkGray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 模式命令按钮网格
                val config = LoveSpouseConstants.getModeConfig(selectedCateId)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (cmd in modeCommands) {
                        val isActive = cmd == activeMode
                        val label = if (config.end < 10) cmd.substring(1) else cmd  // "01"→"1", "41"→"41"
                        ModeButton(
                            label = label,
                            isActive = isActive,
                            onClick = { testVm.toggleMode(cmd) }
                        )
                    }
                }

                if (activeMode != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前模式: $activeMode  |  停止命令: ${config.stop}",
                        color = BloodRed, style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ═══ 紧急停止 ═══
                DungeonButton(
                    text = "紧急停止",
                    variant = ButtonVariant.DANGER,
                    onClick = {
                        testVm.emergencyStop()
                        Toast.makeText(context, "已停止", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "模式与强度互斥：发送模式后强度滑块失效，需停止后重新用强度控制",
                    color = DarkGray, style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ═══ 小组件 ═══

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = GoldAncient, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ModeButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isActive) BloodRed else DarkStoneBrown,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            color = if (isActive) GoldAncient else SilverGray,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
