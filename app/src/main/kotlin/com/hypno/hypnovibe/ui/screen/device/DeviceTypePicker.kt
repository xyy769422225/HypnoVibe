package com.hypno.hypnovibe.ui.screen.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hypno.hypnovibe.ui.component.StoneCard
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*

/**
 * 设备类型选择页（添加设备第一步）。
 * 列出当前支持的设备类型，选中后进入对应类型的设备连接页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceTypePickerScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择设备类型", color = GoldAncient) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = SilverGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        },
        containerColor = AbyssBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("请选择要添加的设备类型", color = SilverGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(SUPPORTED_DEVICE_TYPES, key = { it.typeId }) { entry ->
                    DeviceTypeRow(
                        entry = entry,
                        onClick = {
                            if (entry.available) {
                                navController.navigate(
                                    Screen.DeviceConnect.route.replace("{deviceType}", entry.typeId)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceTypeRow(entry: DeviceTypeEntry, onClick: () -> Unit) {
    StoneCard(modifier = Modifier.clickable(enabled = entry.available, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设备图标
            Image(
                painter = painterResource(id = entry.iconRes),
                contentDescription = entry.displayName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    color = if (entry.available) GoldAncient else DarkGray,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (entry.available) entry.description else "${entry.description}（即将支持）",
                    color = DarkGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (entry.available) {
                Icon(Icons.Filled.ChevronRight, "进入", tint = DarkCopper)
            }
        }
    }
}
