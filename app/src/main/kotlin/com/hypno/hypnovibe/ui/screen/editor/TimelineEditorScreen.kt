package com.hypno.hypnovibe.ui.screen.editor

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hypno.hypnovibe.app.viewmodel.ConfigVM
import com.hypno.hypnovibe.app.viewmodel.TimelineEditorVM
import com.hypno.hypnovibe.domain.DeviceTypeDescriptor
import com.hypno.hypnovibe.domain.entity.DeviceConfig
import com.hypno.hypnovibe.domain.entity.TimelineScript
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*

/**
 * 时间轴编辑器 — 最简模式。
 * 表单操作：选配置 → 选通道 → 添加段（效果+起止时间+强度） → 保存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineEditorScreen(navController: NavController) {
    val editorVm: TimelineEditorVM = viewModel()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogChannelId by remember { mutableStateOf("") }
    var addDialogDeviceType by remember { mutableStateOf("dglab_v3") }

    val script by editorVm.getCurrentScript().collectAsState()
    val selectedConfig by editorVm.getSelectedConfig().collectAsState()
    val dirty by editorVm.getDirty().collectAsState()

    // 加载配置列表
    var configs by remember { mutableStateOf<List<DeviceConfig>>(emptyList()) }
    LaunchedEffect(Unit) {
        editorVm.getConfigVM().loadConfigs()
        configs = editorVm.getConfigVM().getConfigs().value
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("符阵 — 时间轴编辑", color = GoldAncient) },
                actions = {
                    if (dirty) {
                        Text("●", color = BloodRed)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = {
                        if (script == null) {
                            Toast.makeText(context, "请先选择配置", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        editorVm.saveScript()
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存", color = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        },
        containerColor = AbyssBlack
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 配置选择
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        selectedConfig?.name ?: "选择配置",
                        color = if (selectedConfig != null) GoldAncient else DarkGray
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    configs.forEach { cfg ->
                        DropdownMenuItem(
                            text = { Text("${cfg.name} (${cfg.channels.size}通道)") },
                            onClick = {
                                expanded = false
                                editorVm.selectConfig(cfg)
                                configs = editorVm.getConfigVM().getConfigs().value
                            }
                        )
                    }
                }
            }

            if (selectedConfig != null && script != null) {
                GothicDivider()

                // 按通道分组显示
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(script.channels) { ct ->
                        val chDef = selectedConfig!!.channels.find { it.channelId == ct.channelId }
                        val typeInfo = ConfigVM.SHARED_REGISTRY.getTypeInfo(ct.deviceType)
                        val kfs = ct.keyframes
                        val label = chDef?.channelName ?: ct.channelId
                        val strengthRange = if (typeInfo != null) "${typeInfo.strengthMin}-${typeInfo.strengthMax}" else "?"

                        StoneCard {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$label (${typeInfo?.displayName ?: ct.deviceType}, $strengthRange)",
                                        color = GoldAncient)
                                    IconButton(onClick = {
                                        editorVm.clearChannel(ct.channelId)
                                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Filled.Delete, "清空", tint = DarkGray, modifier = Modifier.size(18.dp))
                                    }
                                }

                                if (kfs.isEmpty()) {
                                    Text("（无波形段）", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(4.dp))
                                } else {
                                    // 显示段摘要（合并相邻同标签的关键帧组）
                                    val groups = groupByLabel(kfs)
                                    groups.forEach { (lbl, start, end) ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(lbl ?: "", color = SilverGray,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f))
                                            Text(formatTimeRange(start, end),
                                                color = DarkGray, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }

                                // 添加段按钮
                                DungeonButton(
                                    text = "+ 添加段",
                                    variant = ButtonVariant.SECONDARY,
                                    onClick = {
                                        addDialogChannelId = ct.channelId
                                        addDialogDeviceType = ct.deviceType
                                        showAddDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("选择设备配置以开始编辑时间轴", color = DarkGray)
                }
            }
        }

        // 添加段对话框
        if (showAddDialog) {
            SegmentAddDialog(
                deviceType = addDialogDeviceType,
                onDismiss = { showAddDialog = false },
                onConfirm = { effect, startMs, endMs, strength, freq, label ->
                    editorVm.addSegment(addDialogChannelId, effect, startMs, endMs, strength, freq, label)
                    showAddDialog = false
                }
            )
        }
    }
}

/** 按 label 将关键帧分组为 (label, startMs, endMs) */
private fun groupByLabel(kfs: List<TimelineScript.Keyframe>): List<Triple<String?, Long, Long>> {
    if (kfs.isEmpty()) return emptyList()
    val result = mutableListOf<Triple<String?, Long, Long>>()
    var currentLabel = kfs[0].label
    var startMs = kfs[0].timeMs
    for (i in 1 until kfs.size) {
        if (kfs[i].label != currentLabel) {
            result.add(Triple(currentLabel, startMs, kfs[i - 1].timeMs))
            currentLabel = kfs[i].label
            startMs = kfs[i].timeMs
        }
    }
    result.add(Triple(currentLabel, startMs, kfs.last().timeMs))
    return result
}

private fun formatTimeRange(startMs: Long, endMs: Long): String {
    val s = startMs / 1000
    val e = endMs / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')} → ${e / 60}:${(e % 60).toString().padStart(2, '0')}"
}

// ══════════════════════════════════════════════════════
//  添加段对话框
// ══════════════════════════════════════════════════════

@Composable
private fun SegmentAddDialog(
    deviceType: String,
    onDismiss: () -> Unit,
    onConfirm: (effect: String, startMs: Long, endMs: Long, strength: Int, freq: Int, label: String) -> Unit
) {
    val isDGLab = deviceType == "dglab_v3"
    var label by remember { mutableStateOf("") }
    var effect by remember { mutableStateOf("constant") }
    var startMin by remember { mutableStateOf("0") }
    var startSec by remember { mutableStateOf("00") }
    var endMin by remember { mutableStateOf("2") }
    var endSec by remember { mutableStateOf("00") }
    var strength by remember { mutableFloatStateOf(0.5f) }
    var freq by remember { mutableFloatStateOf(100f) }

    val effects = listOf("constant" to "恒定", "breath" to "呼吸", "pulse" to "脉冲", "rise" to "渐强", "fall" to "渐弱")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加波形段", color = GoldAncient) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    OutlinedTextField(value = label, onValueChange = { label = it },
                        label = { Text("标签") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BloodRed, focusedLabelColor = GoldAncient,
                            unfocusedBorderColor = DarkCopper, unfocusedLabelColor = DarkGray,
                            cursorColor = SilverGray))
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    Text("效果", color = SilverGray)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(effects.find { it.first == effect }?.second ?: effect, color = GoldAncient)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            effects.forEach { (key, name) ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { effect = key; expanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = startMin, onValueChange = { startMin = it },
                            label = { Text("起始 分") }, singleLine = true, modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BloodRed, unfocusedBorderColor = DarkCopper, cursorColor = SilverGray))
                        OutlinedTextField(value = startSec, onValueChange = { startSec = it },
                            label = { Text("秒") }, singleLine = true, modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BloodRed, unfocusedBorderColor = DarkCopper, cursorColor = SilverGray))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = endMin, onValueChange = { endMin = it },
                            label = { Text("结束 分") }, singleLine = true, modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BloodRed, unfocusedBorderColor = DarkCopper, cursorColor = SilverGray))
                        OutlinedTextField(value = endSec, onValueChange = { endSec = it },
                            label = { Text("秒") }, singleLine = true, modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BloodRed, unfocusedBorderColor = DarkCopper, cursorColor = SilverGray))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    Text("强度: ${(strength * 100).toInt()}%", color = SilverGray)
                    Slider(value = strength, onValueChange = { strength = it },
                        colors = SliderDefaults.colors(thumbColor = BloodRed, activeTrackColor = BloodRed,
                            inactiveTrackColor = LeatherBrown))
                    Spacer(Modifier.height(8.dp))
                }

                if (isDGLab) {
                    item {
                        Text("频率: ${freq.toInt()}", color = SilverGray)
                        Slider(value = freq, onValueChange = { freq = it },
                            valueRange = 10f..1000f,
                            colors = SliderDefaults.colors(thumbColor = DarkPurple, activeTrackColor = DarkPurple,
                                inactiveTrackColor = LeatherBrown))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sm = startMin.toIntOrNull() ?: 0
                val ss = startSec.toIntOrNull() ?: 0
                val em = endMin.toIntOrNull() ?: 0
                val es = endSec.toIntOrNull() ?: 0
                val startMs = (sm * 60L + ss) * 1000L
                val endMs = (em * 60L + es) * 1000L
                onConfirm(effect, startMs, endMs, (strength * 100).toInt(), freq.toInt(), label.ifBlank { effect })
            }) {
                Text("添加", color = BloodRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = DarkGray)
            }
        },
        containerColor = DarkStoneBrown
    )
}
