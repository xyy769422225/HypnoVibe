package com.hypno.hypnovibe.ui.screen.waveform

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformScreen() {
    val context = LocalContext.current
    val testStrengthA = remember { mutableFloatStateOf(0f) }
    val testStrengthB = remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("波形管理", color = GoldAncient) },
                actions = {
                    IconButton(onClick = {
                        Toast.makeText(context, "导入功能 Phase 7 启用", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.Add, "导入波形", tint = BloodRed)
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
            // === 波形库 ===
            Text("波形库", color = GoldAncient)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("呼吸", "脉冲", "持续", "高潮").forEach { category ->
                    DungeonButton(
                        text = category,
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
            StoneCard {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("(暂无波形预设) 点击 + 导入", color = DarkGray)
                }
            }

            GothicDivider()

            // === 快速测试 ===
            Text("快速测试 (需连接设备)", color = GoldAncient)

            Text("A 通道", color = SilverGray)
            DungeonSlider(
                value = testStrengthA.floatValue,
                onValueChange = { testStrengthA.floatValue = it },
                valueRange = 0f..200f,
                label = "强度: ${testStrengthA.floatValue.toInt()}"
            )

            Text("B 通道", color = SilverGray)
            DungeonSlider(
                value = testStrengthB.floatValue,
                onValueChange = { testStrengthB.floatValue = it },
                valueRange = 0f..200f,
                label = "强度: ${testStrengthB.floatValue.toInt()}"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DungeonButton(
                    text = "测试波形",
                    onClick = {
                        Toast.makeText(context, "连接设备后可使用 (Phase 4)", Toast.LENGTH_SHORT).show()
                    },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                DungeonButton(
                    text = "安全停止",
                    onClick = {
                        testStrengthA.floatValue = 0f
                        testStrengthB.floatValue = 0f
                    },
                    variant = ButtonVariant.DANGER,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
