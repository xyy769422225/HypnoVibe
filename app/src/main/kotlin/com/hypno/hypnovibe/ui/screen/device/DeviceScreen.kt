package com.hypno.hypnovibe.ui.screen.device

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备管理", color = GoldAncient) },
                actions = {
                    IconButton(onClick = {
                        Toast.makeText(context, "即将开放", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.Add, "添加", tint = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connected
            Text("已连接设备", color = SilverGray)
            StoneCard {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text("(暂无已连接设备)", color = DarkGray) }
            }

            // History
            Text("历史设备", color = SilverGray)
            StoneCard {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text("(暂无历史设备)", color = DarkGray) }
            }

            GothicDivider()

            // Sub-page shortcuts
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
