package com.hypno.hypnovibe.ui.screen.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hypno.hypnovibe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelMappingScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通道映射", color = GoldAncient) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("请先在播放列表中打开一个播放列表", color = DarkGray)
        }
    }
}
