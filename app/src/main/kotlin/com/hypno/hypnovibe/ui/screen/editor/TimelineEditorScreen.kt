package com.hypno.hypnovibe.ui.screen.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineEditorScreen(navController: NavController? = null) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("打轴编辑器", color = GoldAncient) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DungeonButton(text = "保存", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
                DungeonButton(text = "撤销", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
                DungeonButton(text = "重做", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
                DungeonButton(text = "缩放+", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (navController != null) {
                    DungeonButton(
                        text = "波形管理",
                        onClick = { navController.navigate(Screen.Waveform.route) },
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            GothicDivider()
            // Canvas placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("选择音频文件和设备配置以开始编辑", color = DarkGray)
            }
        }
    }
}
