package com.hypno.hypnovibe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.hypno.hypnovibe.ui.navigation.BottomNavBar
import com.hypno.hypnovibe.ui.navigation.NavGraph
import com.hypno.hypnovibe.ui.theme.AbyssBlack
import com.hypno.hypnovibe.ui.theme.HypnoVibeTheme

class MainActivity : ComponentActivity() {

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置页返回，重新检查权限
        requestStoragePermission()
    }

    /** 蓝牙运行时权限请求（Android 12+） */
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // 无论授予与否都不阻塞，用户在设备页扫描时会再次提示
        val allGranted = result.values.all { it }
        if (!allGranted) {
            android.util.Log.w("MainActivity", "部分蓝牙权限未授予，扫描可能失败")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestStoragePermission()
        requestBluetoothPermission()

        setContent {
            HypnoVibeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AbyssBlack) {
                    val navController = rememberNavController()
                    androidx.compose.material3.Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = { BottomNavBar(navController) }
                    ) { padding ->
                        // 必须应用外层 Scaffold 的底部内边距，否则内层屏幕的 bottomBar
                        // (如播放控件 PlayerBar) 会被 BottomNavBar 遮挡。
                        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                            NavGraph(navController)
                        }
                    }
                }
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                storagePermissionLauncher.launch(intent)
            }
        }
    }

    /**
     * 请求蓝牙运行时权限（Android 12+ 必需）。
     * Android 11 及以下走 BLUETOOTH/BLUETOOTH_ADMIN/ACCESS_FINE_LOCATION。
     */
    private fun requestBluetoothPermission() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val needRequest = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest) {
            bluetoothPermissionLauncher.launch(permissionsToRequest)
        }
    }
}
