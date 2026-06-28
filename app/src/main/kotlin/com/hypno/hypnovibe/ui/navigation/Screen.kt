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
    object ChannelMapping : Screen("channel_mapping", "", Icons.Filled.Settings)
    object ConfigList : Screen("config_list", "", Icons.Filled.List)
    object ConfigEditor : Screen("config_editor/{configId}", "", Icons.Filled.Edit)
    object Waveform : Screen("waveform", "", Icons.Filled.GraphicEq)
}
