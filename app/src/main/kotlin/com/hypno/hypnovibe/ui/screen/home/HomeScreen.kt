package com.hypno.hypnovibe.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hypno.hypnovibe.ui.component.DungeonButton
import com.hypno.hypnovibe.ui.component.StoneCard
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*

@Composable
fun HomeScreen(navController: NavController? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "HypnoVibe",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BloodRed,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("蓝牙玩具时间轴控制器", color = SilverGray, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(48.dp))

        StoneCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("播放列表", color = GoldAncient)
                Spacer(modifier = Modifier.height(4.dp))
                Text("管理音频和时间轴脚本", color = DarkGray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                DungeonButton(text = "打开", onClick = {
                    navController?.navigate(Screen.Playlist.route)
                })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        StoneCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("波形管理", color = GoldAncient)
                Spacer(modifier = Modifier.height(4.dp))
                Text("导入和测试波形", color = DarkGray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                DungeonButton(text = "打开", onClick = {
                    navController?.navigate(Screen.Waveform.route)
                })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        StoneCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("设备配置", color = GoldAncient)
                Spacer(modifier = Modifier.height(4.dp))
                Text("管理通道和设备设置", color = DarkGray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                DungeonButton(text = "打开", onClick = {
                    navController?.navigate(Screen.ConfigList.route)
                })
            }
        }
    }
}
