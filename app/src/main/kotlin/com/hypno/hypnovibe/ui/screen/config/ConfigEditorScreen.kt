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
import com.hypno.hypnovibe.domain.DeviceTypeDescriptor
import com.hypno.hypnovibe.domain.entity.DeviceConfig
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(configId: String) {
    val vm: ConfigVM = viewModel()
    val configs: List<DeviceConfig> by vm.getConfigs().collectAsState()
    val deviceTypes = remember { vm.getConfigurableDeviceTypes() }
    val context = LocalContext.current
    val isNew = configId == "new"

    val existing: DeviceConfig? = configs.find { it.id == configId }
    var name: String by remember { mutableStateOf(existing?.name ?: "") }
    var channels: List<DeviceConfig.ChannelDef> by remember {
        mutableStateOf(existing?.channels?.toMutableList() ?: mutableListOf())
    }

    // 编辑通道时的状态
    var editingIndex by remember { mutableStateOf(-1) }
    var editName by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf("dglab_v3") }
    var editDefaultStrength by remember { mutableFloatStateOf(0f) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // 添加新通道时的初始设备类型
    var addDeviceType by remember { mutableStateOf("dglab_v3") }
    var showAddTypeDropdown by remember { mutableStateOf(false) }

    if (showEditDialog) {
        val typeInfo = vm.getDeviceTypeInfo(editType)
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (editingIndex < 0) "添加通道" else "编辑通道", color = GoldAncient) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it },
                        label = { Text("通道名称") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BloodRed, focusedLabelColor = GoldAncient,
                            unfocusedBorderColor = DarkCopper, unfocusedLabelColor = DarkGray, cursorColor = SilverGray))

                    // 设备类型下拉
                    Text("设备类型", color = SilverGray)
                    Box {
                        TextButton(onClick = { showTypeDropdown = true }) {
                            Text(typeInfo?.displayName ?: editType, color = GoldAncient)
                        }
                        DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                            deviceTypes.forEach { dt ->
                                val disabled = dt.deviceType == "love_spouse" &&
                                        editingIndex < 0 &&
                                        channels.any { it.deviceType == "love_spouse" }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            dt.displayName + if (disabled) " (已达上限)" else "",
                                            color = if (disabled) DarkGray else SilverGray)
                                    },
                                    onClick = {
                                        if (!disabled) { editType = dt.deviceType; showTypeDropdown = false
                                            editDefaultStrength = 0f }
                                    }
                                )
                            }
                        }
                    }

                    // 设备类型信息
                    typeInfo?.let { ti ->
                        StoneCard {
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text("物理通道: ${ti.physicalChannels.joinToString(" + ") { it.displayName }}",
                                    color = DarkGray, style = MaterialTheme.typography.labelSmall)
                                Text("强度范围: ${ti.strengthMin} ~ ${ti.strengthMax}",
                                    color = DarkGray, style = MaterialTheme.typography.labelSmall)
                                Text("需要映射: ${if (ti.requiresMapping()) "是" else "否（自动广播）"}",
                                    color = DarkGray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // 默认强度
                    typeInfo?.let { ti ->
                        val strengthRange = ti.strengthMax - ti.strengthMin
                        Text("默认强度: ${(editDefaultStrength * strengthRange + ti.strengthMin).toInt()}",
                            color = SilverGray)
                        Slider(
                            value = editDefaultStrength,
                            onValueChange = { editDefaultStrength = it },
                            colors = SliderDefaults.colors(
                                thumbColor = BloodRed, activeTrackColor = BloodRed, inactiveTrackColor = LeatherBrown))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isBlank()) { return@TextButton }
                    val ti = vm.getDeviceTypeInfo(editType)
                    val defStr = if (ti != null)
                        (editDefaultStrength * (ti.strengthMax - ti.strengthMin) + ti.strengthMin).toInt()
                    else 0

                    if (editingIndex >= 0) {
                        val updated = channels.toMutableList()
                        val old = updated[editingIndex]
                        val ch = DeviceConfig.ChannelDef(editName, editType, defStr)
                        ch.channelId = old.channelId
                        updated[editingIndex] = ch
                        channels = updated
                    } else {
                        channels = channels + DeviceConfig.ChannelDef(editName, editType, defStr)
                    }
                    showEditDialog = false
                }) { Text("确定", color = BloodRed) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消", color = DarkGray) }
            },
            containerColor = DarkStoneBrown
        )
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
                        val filtered = channels.filter { it.channelName.isNotBlank() }
                        if (filtered.isEmpty()) {
                            Toast.makeText(context, "请至少添加一个通道", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        // 校验
                        val errors = vm.validate(
                            DeviceConfig().apply { this.name = name; this.channels = filtered.map {
                                DeviceConfig.ChannelDef(it.channelName, it.deviceType, it.defaultStrength).also { c -> c.channelId = it.channelId }
                            }}
                        )
                        if (errors.isNotEmpty()) {
                            Toast.makeText(context, errors.first(), Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        if (isNew) vm.createConfig(name, filtered)
                        else if (existing != null) { existing.name = name; existing.channels = filtered; existing.touch(); vm.updateConfig(existing) }
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }) { Text("保存", color = BloodRed) }
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
                label = { Text("配置名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BloodRed, focusedLabelColor = GoldAncient,
                    unfocusedBorderColor = DarkCopper, unfocusedLabelColor = DarkGray, cursorColor = SilverGray))

            Text("通道列表 (${channels.size})", color = GoldAncient)
            for (i in channels.indices) {
                val ch = channels[i]
                val ti = vm.getDeviceTypeInfo(ch.deviceType)
                StoneCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ch.channelName.ifBlank { "(未命名)" }, color = GoldAncient)
                            IconButton(onClick = {
                                channels = channels.toMutableList().also { it.removeAt(i) }
                            }) {
                                Icon(Icons.Filled.Close, "删除", tint = AlertRed, modifier = Modifier.size(20.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(ti?.displayName ?: ch.deviceType, color = SilverGray, style = MaterialTheme.typography.labelMedium)
                            Text("强度: ${ch.defaultStrength}", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                        }
                        if (ti != null) {
                            Text(
                                "${ti.physicalChannels.joinToString(" + ") { it.displayName }} | ${ti.strengthMin}-${ti.strengthMax} | ${if (ti.requiresMapping()) "需映射" else "自动广播"}",
                                color = DarkGray, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            editingIndex = i
                            editName = ch.channelName
                            editType = ch.deviceType
                            val range = (ti?.strengthMax ?: 200) - (ti?.strengthMin ?: 0)
                            editDefaultStrength = if (range > 0) ch.defaultStrength.toFloat() / range else 0f
                            showEditDialog = true
                        }) { Text("编辑", color = DarkPurple, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            TextButton(onClick = {
                editingIndex = -1
                editName = ""
                editType = addDeviceType
                editDefaultStrength = 0f
                showEditDialog = true
            }) {
                Icon(Icons.Filled.Add, "添加通道", tint = BloodRed)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加通道", color = BloodRed)
            }
        }
    }
}
