package com.hypno.hypnovibe.ui.screen.device

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
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
 * DG-LAB 强度测试面板。
 * A/B 双通道独立调节，按住按钮连续增减（每秒最多+2），安全开关一键归零。
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

    LaunchedEffect(deviceId) {
        val connected = deviceVm.findDevice(deviceId)
        if (connected != null && connected.adapter is DGLabController) {
            testVm.setController(connected.adapter as DGLabController, connected.name)
        } else {
            Toast.makeText(context, "设备未找到", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isConnected) {
                StoneCard {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("设备未连接", color = AlertRed)
                    }
                }
            }

            ChannelControl(
                title = "⚡ A 通道",
                targetStrength = targetA,
                deviceStrength = deviceA,
                onIncrease = { testVm.increaseChannelA() },
                onDecrease = { testVm.decreaseChannelA() },
                enabled = isConnected && !safetyOn
            )

            ChannelControl(
                title = "⚡ B 通道",
                targetStrength = targetB,
                deviceStrength = deviceB,
                onIncrease = { testVm.increaseChannelB() },
                onDecrease = { testVm.decreaseChannelB() },
                enabled = isConnected && !safetyOn
            )

            GothicDivider()

            DungeonButton(
                text = if (safetyOn) "⚡ 解锁强度" else "🔴 安全停止",
                variant = if (safetyOn) ButtonVariant.SECONDARY else ButtonVariant.DANGER,
                onClick = {
                    if (safetyOn) {
                        testVm.unlockSafety()
                        Toast.makeText(context, "已解锁，请谨慎调节", Toast.LENGTH_SHORT).show()
                    } else {
                        testVm.emergencyStop()
                        Toast.makeText(context, "强度已归零", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "⚠ 按住按钮不松将连续增加，每秒最多+2强度",
                color = DarkGray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ChannelControl(
    title: String,
    targetStrength: Int,
    deviceStrength: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    enabled: Boolean
) {
    StoneCard {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, color = GoldAncient, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldButton(
                    text = "－",
                    enabled = enabled,
                    onChange = onDecrease,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = targetStrength / 200f,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = BloodRed,
                        trackColor = LeatherBrown
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$targetStrength/200",
                        color = SilverGray,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "设备回报: $deviceStrength",
                        color = DarkGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(Modifier.width(8.dp))

                HoldButton(
                    text = "＋",
                    enabled = enabled,
                    onChange = onIncrease,
                    modifier = Modifier.size(48.dp),
                    isPrimary = true
                )
            }
        }
    }
}

@Composable
private fun HoldButton(
    text: String,
    enabled: Boolean,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    val backgroundColor = when {
        !enabled -> DarkGray
        isPrimary -> BloodRed
        else -> DarkCopper
    }

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    onChange()
                    val job = scope.launch(Dispatchers.Default) {
                        delay(500)
                        while (isActive && pressed) {
                            onChange()
                            delay(500)
                        }
                    }
                    tryAwaitRelease()
                    pressed = false
                    job.cancel()
                }
            )
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = SilverGray, fontSize = 20.sp)
        }
    }
}
