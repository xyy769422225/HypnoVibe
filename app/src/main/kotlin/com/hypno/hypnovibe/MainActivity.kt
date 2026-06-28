package com.hypno.hypnovibe

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestStoragePermission()

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
}
