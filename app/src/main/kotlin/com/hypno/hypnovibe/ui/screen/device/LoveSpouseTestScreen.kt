package com.hypno.hypnovibe.ui.screen.device

import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*

/**
 * Love Spouse 测试面板。
 * <p>
 * 单通道 0-9 等级滑块 + 广播开关 + 紧急停止。
 * 与郊狼不同：无配对/连接概念，开/关的是 BLE 广播。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoveSpouseTestScreen(deviceId: String, navController: NavController) {
    val deviceVm = rememberDeviceManagerVM()
    val testVm: LoveSpouseTestVM = viewModel()
    val context = LocalContext.current

    val currentLevel by testVm.getCurrentLevel().collectAsState()
    val isAdvertising by testVm.getIsAdvertising().collectAsState()
    val deviceName by testVm.getDeviceName().collectAsState()
    val errorMsg by testVm.getErrorMsg().collectAsState()

    // 初始化：从 DeviceManagerVM 获取 adapter 状态
    LaunchedEffect(deviceId) {
        val connected = deviceVm.findDevice(deviceId)
        val name = connected?.name ?: "Love Spouse"
        testVm.init(deviceVm, deviceId, name)
    }

    // 错误提示
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            testVm.clearError()
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            StoneCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val (statusText, statusColor) = when {
                        isAdvertising -> "广播已开启" to DarkGreen
                        else -> "广播未开启" to DarkGray
                    }
                    Text(statusText, color = statusColor)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isAdvertising) "玩具正在接收振动信号" else "点击下方按钮开启广播",
                        color = DarkGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 广播开关按钮
            DungeonButton(
                text = if (isAdvertising) "关闭广播" else "开启广播",
                variant = if (isAdvertising) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY,
                onClick = {
                    if (isAdvertising) {
                        testVm.stopBroadcast()
                    } else {
                        testVm.startBroadcast()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 广播开启后才显示控制面板
            if (isAdvertising) {
                GothicDivider()

                // 振动等级
                StoneCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("振动等级", color = GoldAncient)
                        Spacer(Modifier.height(8.dp))

                        // 等级圆点指示
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
                        Text(
                            "当前等级: $currentLevel / 9",
                            color = SilverGray,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // 快速预设
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DungeonButton("弱", { testVm.setStrength(2) },
                        variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                    DungeonButton("中", { testVm.setStrength(5) },
                        variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                    DungeonButton("强", { testVm.setStrength(8) },
                        variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(8.dp))

                // 紧急停止
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
                    "Love Spouse 仅支持振动强度 (0-9)，不支持频率/波形控制",
                    color = DarkGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
