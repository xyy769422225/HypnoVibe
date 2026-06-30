package com.hypno.hypnovibe.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Filled.Home)
    object Playlist : Screen("playlist", "播放", Icons.Filled.QueueMusic)
    object Editor : Screen("editor", "打轴", Icons.Filled.Edit)
    object Device : Screen("device", "设备", Icons.Filled.Bluetooth)

    object PlaylistDetail : Screen("playlist_detail/{playlistId}", "", Icons.Filled.List)
    object ChannelMapping : Screen("channel_mapping/{playlistId}", "", Icons.Filled.Settings)
    object ConfigList : Screen("config_list", "", Icons.Filled.List)
    object ConfigEditor : Screen("config_editor/{configId}", "", Icons.Filled.Edit)
    object Waveform : Screen("waveform", "", Icons.Filled.GraphicEq)
    object DGLabTest : Screen("dglab_test/{deviceId}", "", Icons.Filled.Bolt)
    object LoveSpouseTest : Screen("love_spouse_test/{deviceId}", "", Icons.Filled.Bluetooth)

    /** 设备类型选择（添加设备第一步） */
    object DeviceTypePicker : Screen("device_type_picker", "", Icons.Filled.Add)

    /** 设备连接（添加设备第二步：扫描并连接某类型设备） */
    object DeviceConnect : Screen("device_connect/{deviceType}", "", Icons.Filled.BluetoothSearching)
}
