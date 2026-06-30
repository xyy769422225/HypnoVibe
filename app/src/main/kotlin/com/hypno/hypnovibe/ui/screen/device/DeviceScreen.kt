package com.hypno.hypnovibe.ui.screen.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hypno.hypnovibe.R
import com.hypno.hypnovibe.app.viewmodel.DeviceManagerVM
import com.hypno.hypnovibe.domain.AdapterStatus
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(navController: NavController) {
    val vm = rememberDeviceManagerVM()
    val deviceList by vm.getDeviceList().collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备管理", color = GoldAncient) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.DeviceTypePicker.route) }) {
                        Icon(Icons.Filled.Add, "添加设备", tint = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        },
        containerColor = AbyssBlack
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (deviceList.isEmpty()) {
                StoneCard {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.BluetoothDisabled, "无设备", tint = DarkGray, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("暂无保存的设备", color = DarkGray)
                            Spacer(Modifier.height(4.dp))
                            Text("点击右上角 + 添加设备", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(deviceList, key = { it.mac }) { item ->
                        SavedDeviceItem(
                            item = item,
                            onClick = {
                                // 已连接：进入测试面板（当前仅郊狼）
                                if (item.connected && item.deviceId != null) {
                                    navController.navigate(
                                        Screen.CoyoteTest.route.replace("{deviceId}", item.deviceId)
                                    )
                                } else {
                                    vm.reconnectSaved(item.mac)
                                }
                            },
                            onDisconnect = { vm.disconnectDevice(item.mac) },
                            onDelete = { vm.removeSavedDevice(item.mac) }
                        )
                    }
                }
            }

            GothicDivider()

            // 管理快捷入口
            Text("管理", color = SilverGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DungeonButton(
                    text = "设备配置",
                    onClick = { navController.navigate(Screen.ConfigList.route) },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                DungeonButton(
                    text = "波形管理",
                    onClick = { navController.navigate(Screen.Waveform.route) },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SavedDeviceItem(
    item: DeviceManagerVM.DeviceItem,
    onClick: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit
) {
    val typeEntry = SUPPORTED_DEVICE_TYPES.firstOrNull { it.typeId == item.deviceType }

    val stateText: String = when {
        !item.connected -> "未连接"
        item.state == AdapterStatus.State.CONNECTED -> "已连接"
        item.state == AdapterStatus.State.CONNECTING -> "连接中..."
        item.state == AdapterStatus.State.RETRYING -> "重连中..."
        item.state == AdapterStatus.State.ERROR -> "错误"
        else -> "未连接"
    }
    val stateColor = when {
        item.state == AdapterStatus.State.CONNECTED -> DarkGreen
        item.state == AdapterStatus.State.ERROR -> AlertRed
        item.connected -> GoldAncient
        else -> DarkGray
    }

    StoneCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设备类型图标
            if (typeEntry != null) {
                Image(
                    painter = painterResource(id = typeEntry.iconRes),
                    contentDescription = typeEntry.displayName,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Icon(Icons.Filled.Bluetooth, "设备", tint = GoldAncient, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(item.name, color = GoldAncient)
                Text(stateText, color = stateColor, style = MaterialTheme.typography.labelSmall)
            }
            when {
                item.connected -> IconButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.Close, "断开", tint = DarkGray, modifier = Modifier.size(18.dp))
                }
                else -> IconButton(onClick = onClick) {
                    Icon(Icons.Filled.Link, "连接", tint = BloodRed, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = DarkGray, modifier = Modifier.size(18.dp))
            }
        }
    }
}
