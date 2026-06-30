package com.hypno.hypnovibe.ui.screen.device

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hypno.hypnovibe.R
import com.hypno.hypnovibe.app.viewmodel.DeviceManagerVM

/**
 * 可添加的设备类型定义（设备类型选择页使用）。
 * 郊狼类型同时扫描 V2 和 V3 前缀，连接时根据广播名自动识别版本。
 */
data class DeviceTypeEntry(
    val typeId: String,
    val displayName: String,
    val description: String,
    val iconRes: Int,
    val available: Boolean
)

/** 设备类型选择页展示的列表 */
val SUPPORTED_DEVICE_TYPES: List<DeviceTypeEntry> = listOf(
    DeviceTypeEntry(
        typeId = DeviceManagerVM.TYPE_COYOTE_V3,
        displayName = "郊狼 DG-LAB (V2/V3)",
        description = "脉冲主机 V2 & V3（双通道电刺激）",
        iconRes = R.drawable.ic_dglab,
        available = true
    )
)

/**
 * 获取 Activity 级共享的 [DeviceManagerVM]。
 *
 * 设备连接（adapter 实例）是运行时状态，需要在「设备连接页」连接后、回退到
 * 「设备管理页」、再进入「测试面板」时保持存活，因此将该 VM 提升到 Activity 作用域，
 * 而非各导航目的地独立持有。
 */
@Composable
fun rememberDeviceManagerVM(): DeviceManagerVM {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: error("DeviceManagerVM 需要在 ComponentActivity 上下文中使用")
    return viewModel(activity)
}
