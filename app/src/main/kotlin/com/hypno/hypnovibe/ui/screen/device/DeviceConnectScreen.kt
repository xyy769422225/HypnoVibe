package com.hypno.hypnovibe.ui.screen.device

import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.hypno.hypnovibe.R
import com.hypno.hypnovibe.app.viewmodel.DeviceManagerVM
import com.hypno.hypnovibe.domain.AdapterStatus
import com.hypno.hypnovibe.ui.component.StoneCard
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*

/**
 * 设备连接页（添加设备第二步）。
 *
 * 流程：进入页面 → 请求蓝牙权限 → 自动开始扫描选定类型的设备 →
 * 用户选择设备连接 → 连接成功后回退到设备管理页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectScreen(deviceType: String, navController: NavController) {
    val vm = rememberDeviceManagerVM()
    val context = LocalContext.current
    val isScanning by vm.getIsScanning().collectAsState()
    val scanResults by vm.getScanResults().collectAsState()
    val errorMsg by vm.getErrorMsg().collectAsState()
    val deviceList by vm.getDeviceList().collectAsState()

    // 当前正在连接的设备 mac，用于观察连接结果
    var connectingMac by remember { mutableStateOf<String?>(null) }

    val entry = SUPPORTED_DEVICE_TYPES.firstOrNull { it.typeId == deviceType }
    val titleName = entry?.displayName ?: "设备"

    /** 蓝牙权限请求 */
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.w("DeviceConnect", "permission result: $result")
        if (result.values.all { it }) {
            Log.w("DeviceConnect", "all permissions granted, starting scan")
            vm.startScan(deviceType)
        } else {
            Log.w("DeviceConnect", "permissions denied")
            Toast.makeText(context, "需要蓝牙权限才能扫描设备", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureBtPermissionAndScan() {
        val sdk = android.os.Build.VERSION.SDK_INT
        // 对齐官方：无论哪个 Android 版本，都请求定位权限
        // vivo/OPPO/小米 ROM 即使 Android 12+ 也需要定位权限才能 BLE 扫描
        val permissions = if (sdk >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        Log.w("DeviceConnect", "SDK=$sdk, permissions=${permissions.joinToString()}, granted=$granted, btEnabled=${vm.isBluetoothEnabled()}")
        if (granted) {
            if (!vm.isBluetoothEnabled()) {
                Log.w("DeviceConnect", "BT not enabled, showing toast")
                Toast.makeText(context, "请先开启蓝牙", Toast.LENGTH_SHORT).show()
                return
            }
            Log.w("DeviceConnect", "BT OK, starting scan")
            vm.startScan(deviceType)
        } else {
            Log.w("DeviceConnect", "requesting permissions: ${permissions.joinToString()}")
            btPermissionLauncher.launch(permissions)
        }
    }

    // 进入页面自动开始扫描
    LaunchedEffect(deviceType) {
        ensureBtPermissionAndScan()
    }

    // 离开页面时停止扫描
    DisposableEffect(Unit) {
        onDispose {
            vm.stopScan()
        }
    }

    // 错误提示
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            vm.clearError()
        }
    }

    // 观察连接结果：连接成功则回退到设备管理页
    LaunchedEffect(connectingMac, deviceList) {
        val mac = connectingMac ?: return@LaunchedEffect
        val item = deviceList.firstOrNull { it.mac == mac }
        if (item != null && item.connected && item.state == AdapterStatus.State.CONNECTED) {
            connectingMac = null
            navController.popBackStack(Screen.Device.route, inclusive = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接$titleName", color = GoldAncient) },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.stopScan()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = SilverGray)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.startScan(deviceType) }, enabled = !isScanning) {
                        Icon(Icons.Filled.Refresh, "重新扫描", tint = GoldAncient)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        },
        containerColor = AbyssBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 状态提示 + 图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = entry?.iconRes ?: R.drawable.ic_dglab),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isScanning) "正在搜索设备..." else if (scanResults.isEmpty()) "未发现设备" else "发现 ${scanResults.size} 个设备",
                    color = SilverGray,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (isScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BloodRed,
                    trackColor = LeatherBrown
                )
            } else if (scanResults.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("请确认设备已开机并处于蓝牙可发现范围", color = DarkGray, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            // 正在连接中的设备提示
            connectingMac?.let { mac ->
                val item = deviceList.firstOrNull { it.mac == mac }
                val stateText = when (item?.state) {
                    AdapterStatus.State.CONNECTING -> "连接中..."
                    AdapterStatus.State.RETRYING -> "重连中..."
                    AdapterStatus.State.ERROR -> "连接失败"
                    else -> "连接中..."
                }
                StoneCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = GoldAncient
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stateText, color = GoldAncient)
                    }
                }
            }

            // 扫描结果列表
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scanResults, key = { it.mac }) { item ->
                    ScanResultRow(
                        item = item,
                        enabled = connectingMac == null,
                        onConnect = {
                            vm.stopScan()
                            connectingMac = item.mac
                            vm.connectDevice(deviceType, item.mac, item.name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanResultRow(
    item: DeviceManagerVM.ScanResultItem,
    enabled: Boolean,
    onConnect: () -> Unit
) {
    StoneCard(modifier = Modifier.clickable(enabled = enabled, onClick = onConnect)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Bluetooth, "设备", tint = SilverGray, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, color = GoldAncient)
                Text("${item.mac} | ${item.rssi}dBm", color = DarkGray, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onConnect, enabled = enabled) {
                Text("连接", color = BloodRed)
            }
        }
    }
}
