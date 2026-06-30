package com.hypno.hypnovibe.ui.screen.playlist

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hypno.hypnovibe.app.viewmodel.ConfigVM
import com.hypno.hypnovibe.app.viewmodel.PlaySessionVM
import com.hypno.hypnovibe.domain.entity.DeviceConfig
import com.hypno.hypnovibe.domain.entity.Playlist
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.screen.device.rememberDeviceManagerVM
import com.hypno.hypnovibe.ui.theme.*

/**
 * 通道映射界面。
 * 区分需要映射的通道（DG-LAB）和自动广播通道（LoveSpouse）。
 * 映射结果持久化到播放列表 JSON。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelMappingScreen(playlistId: String, navController: NavController) {
    val deviceVm = rememberDeviceManagerVM()
    val playSessionVm: PlaySessionVM = viewModel()
    val deviceList by deviceVm.getDeviceList().collectAsState()
    val context = LocalContext.current

    // 加载播放列表和配置
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var config by remember { mutableStateOf<DeviceConfig?>(null) }

    LaunchedEffect(playlistId) {
        playSessionVm.loadPlaylists()
        val pl = playSessionVm.getPlaylists().value.find { it.id == playlistId }
        playlist = pl
        if (pl != null) {
            val configVm = ConfigVM(context.applicationContext as android.app.Application)
            configVm.loadConfigs()
            config = configVm.getConfigs().value.find { it.id == pl.configId }
        }
    }

    val connectedDGLab = deviceList.filter { !it.isVirtual && it.connected && it.deviceId != null }
    val loveSpouseConnected = deviceList.any { it.isVirtual && it.connected && it.deviceType == "love_spouse" }

    var mapping by remember { mutableStateOf<Map<String, Playlist.ChannelMappingEntry>>(emptyMap()) }

    // 初始化映射
    LaunchedEffect(config, playlist) {
        val cfg = config ?: return@LaunchedEffect
        val init = mutableMapOf<String, Playlist.ChannelMappingEntry>()
        for (ch in cfg.channels) {
            val desc = ConfigVM.SHARED_REGISTRY.getTypeInfo(ch.deviceType)
            if (desc != null && !desc.requiresMapping()) {
                init[ch.channelId] = Playlist.ChannelMappingEntry.broadcast(ch.channelId)
            }
        }
        // 从 playlist 已有的映射中恢复 physical 映射
        playlist?.channelMapping?.forEach { (_, entry) ->
            if (entry.mappingType == "physical" && entry.configChannelId != null) {
                init[entry.configChannelId] = entry
            }
        }
        mapping = init
    }

    val cfg = config
    val physicalChannels = cfg?.channels?.filter {
        val desc = ConfigVM.SHARED_REGISTRY.getTypeInfo(it.deviceType)
        desc != null && desc.requiresMapping()
    } ?: emptyList()

    val broadcastChannels = cfg?.channels?.filter {
        val desc = ConfigVM.SHARED_REGISTRY.getTypeInfo(it.deviceType)
        desc != null && !desc.requiresMapping()
    } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通道映射", color = GoldAncient) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = SilverGray)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val pl = playlist ?: return@TextButton
                        // 持久化映射到 playlist
                        val newMapping = linkedMapOf<String, Playlist.ChannelMappingEntry>()
                        mapping.forEach { (chId, entry) -> newMapping[chId] = entry }
                        pl.channelMapping = newMapping
                        pl.touch()
                        playSessionVm.savePlaylist(pl)
                        Toast.makeText(context, "映射已保存", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }) {
                        Text("保存", color = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        },
        containerColor = AbyssBlack
    ) { padding ->
        if (cfg == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("加载中...", color = DarkGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (physicalChannels.isNotEmpty()) {
                    item { SectionHeader("需要映射的通道", "选择已连接的 DG-LAB 设备并指定 A/B 通道") }
                    items(physicalChannels) { ch ->
                        StoneCard {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(ch.channelName, color = GoldAncient)
                                Spacer(Modifier.height(4.dp))

                                if (connectedDGLab.isEmpty()) {
                                    Text("（无已连接的 DG-LAB 设备）", color = AlertRed, style = MaterialTheme.typography.labelSmall)
                                } else {
                                    var selectedDev by remember { mutableStateOf(mapping[ch.channelId]?.targetDeviceMac ?: "") }
                                    var selectedCh by remember { mutableStateOf(mapping[ch.channelId]?.targetPhysicalChannelKey ?: "A") }

                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text("设备: ", color = SilverGray)
                                        var expanded by remember { mutableStateOf(false) }
                                        Box {
                                            TextButton(onClick = { expanded = true }) {
                                                val name = connectedDGLab.find { it.mac == selectedDev }?.name ?: "选择设备"
                                                Text(name, color = if (selectedDev.isEmpty()) DarkGray else SilverGray)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                connectedDGLab.forEach { dev ->
                                                    DropdownMenuItem(
                                                        text = { Text(dev.name ?: dev.mac) },
                                                        onClick = {
                                                            selectedDev = dev.mac; expanded = false
                                                            mapping = mapping.toMutableMap().apply {
                                                                put(ch.channelId, Playlist.ChannelMappingEntry.physical(ch.channelId, dev.mac, selectedCh))
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (selectedDev.isNotEmpty()) {
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Text("通道: ", color = SilverGray)
                                            var expanded by remember { mutableStateOf(false) }
                                            Box {
                                                TextButton(onClick = { expanded = true }) {
                                                    Text(if (selectedCh == "A") "A 通道" else "B 通道", color = SilverGray)
                                                }
                                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                    listOf("A", "B").forEach { chKey ->
                                                        DropdownMenuItem(
                                                            text = { Text(if (chKey == "A") "A 通道" else "B 通道") },
                                                            onClick = {
                                                                selectedCh = chKey; expanded = false
                                                                mapping = mapping.toMutableMap().apply {
                                                                    put(ch.channelId, Playlist.ChannelMappingEntry.physical(ch.channelId, selectedDev, chKey))
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (selectedDev.isNotEmpty()) {
                                        val devName = connectedDGLab.find { it.mac == selectedDev }?.name ?: selectedDev
                                        Text("$devName → $selectedCh 通道", color = DarkGreen, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (broadcastChannels.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)); SectionHeader("自动广播通道", "开启广播后自动生效，无需手动映射") }
                    items(broadcastChannels) { ch ->
                        StoneCard {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(ch.channelName, color = GoldAncient)
                                    Spacer(Modifier.width(8.dp))
                                    Text("(LoveSpouse)", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("ℹ 广播型设备，开启后自动对所有兼容玩具生效", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    if (loveSpouseConnected) "✅ 广播已开启" else "⚠ LoveSpouse 广播未开启",
                                    color = if (loveSpouseConnected) DarkGreen else AlertRed,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = SilverGray, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, color = DarkGray, style = MaterialTheme.typography.labelSmall)
    }
}
