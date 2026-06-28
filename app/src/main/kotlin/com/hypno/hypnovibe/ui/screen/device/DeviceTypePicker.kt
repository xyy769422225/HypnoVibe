package com.hypno.hypnovibe.ui.screen.device

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hypno.hypnovibe.ui.theme.DarkGray

@Composable
fun DeviceTypePicker() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("设备类型选择 (Phase 4)", color = DarkGray)
    }
}
