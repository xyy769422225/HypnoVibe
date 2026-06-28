package com.hypno.hypnovibe.ui.screen.config

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hypno.hypnovibe.app.viewmodel.ConfigVM
import com.hypno.hypnovibe.domain.entity.DeviceConfig
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(configId: String) {
    val vm: ConfigVM = viewModel()
    val configs: List<DeviceConfig> by vm.getConfigs().collectAsState()
    val context = LocalContext.current
    val isNew = configId == "new"

    val existing: DeviceConfig? = configs.find { it.id == configId }
    var name: String by remember { mutableStateOf(existing?.name ?: "") }
    var channels: List<DeviceConfig.ChannelDef> by remember {
        mutableStateOf(existing?.channels?.toMutableList() ?: mutableListOf())
    }

    LaunchedEffect(Unit) {
        if (isNew && channels.isEmpty()) {
            channels = listOf(DeviceConfig.ChannelDef("", "coyote_v3", 0, 200))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建配置" else "编辑配置", color = GoldAncient) },
                actions = {
                    TextButton(onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "请输入配置名称", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val filtered: List<DeviceConfig.ChannelDef> =
                            channels.filter { it.channelName.isNotBlank() }
                        if (filtered.isEmpty()) {
                            Toast.makeText(context, "请至少添加一个通道", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (isNew) {
                            vm.createConfig(name, filtered)
                        } else if (existing != null) {
                            existing.name = name
                            existing.channels = filtered
                            existing.touch()
                            vm.updateConfig(existing)
                        }
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存", color = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("配置名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BloodRed, focusedLabelColor = GoldAncient,
                    unfocusedBorderColor = DarkCopper, unfocusedLabelColor = DarkGray,
                    cursorColor = SilverGray
                )
            )

            Text("通道列表", color = GoldAncient)
            for (i in channels.indices) {
                val ch = channels[i]
                StoneCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ch.channelName,
                            onValueChange = { newName: String ->
                                val updated = channels.toMutableList()
                                val oldCh = updated[i]
                                val replaced = DeviceConfig.ChannelDef(newName, oldCh.deviceType, oldCh.minStrength, oldCh.maxStrength)
                                replaced.channelId = oldCh.channelId
                                updated[i] = replaced
                                channels = updated
                            },
                            label = { Text("名称") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BloodRed, focusedLabelColor = GoldAncient,
                                unfocusedBorderColor = DarkCopper, unfocusedLabelColor = DarkGray,
                                cursorColor = SilverGray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            channels = channels.toMutableList().also { it.removeAt(i) }
                        }) {
                            Icon(Icons.Filled.Close, "删除", tint = AlertRed)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("类型: ${ch.deviceType}", color = DarkGray)
                        Text("强度: ${ch.minStrength} - ${ch.maxStrength}", color = DarkGray)
                    }
                }
            }

            TextButton(onClick = {
                val updated = channels.toMutableList()
                updated.add(DeviceConfig.ChannelDef("", "coyote_v3", 0, 200))
                channels = updated
            }) {
                Icon(Icons.Filled.Add, "添加通道", tint = BloodRed)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加通道", color = BloodRed)
            }
        }
    }
}
