package com.hypno.hypnovibe.ui.screen.config

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hypno.hypnovibe.app.viewmodel.ConfigVM
import com.hypno.hypnovibe.domain.entity.DeviceConfig
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigListScreen(navController: NavController) {
    val vm: ConfigVM = viewModel()
    val configs by vm.getConfigs().collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.loadConfigs() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备配置列表", color = GoldAncient) },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.ConfigEditor.route.replace("{configId}", "new"))
                    }) {
                        Icon(Icons.Filled.Add, "新建", tint = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        if (configs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("暂无配置，点击 + 新建", color = DarkGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(configs, key = { it.id }) { config ->
                    StoneCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(
                                        Screen.ConfigEditor.route.replace("{configId}", config.id)
                                    )
                                }
                                .padding(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(config.name, color = GoldAncient)
                                Text(
                                    "${config.channels?.size ?: 0} 个通道",
                                    color = DarkGray
                                )
                            }
                            IconButton(onClick = {
                                vm.deleteConfig(config.id)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.Close, "删除", tint = AlertRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
