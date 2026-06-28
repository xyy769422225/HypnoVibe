package com.hypno.hypnovibe.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hypno.hypnovibe.ui.screen.config.ConfigEditorScreen
import com.hypno.hypnovibe.ui.screen.config.ConfigListScreen
import com.hypno.hypnovibe.ui.screen.device.DeviceScreen
import com.hypno.hypnovibe.ui.screen.editor.TimelineEditorScreen
import com.hypno.hypnovibe.ui.screen.home.HomeScreen
import com.hypno.hypnovibe.ui.screen.playlist.ChannelMappingScreen
import com.hypno.hypnovibe.ui.screen.playlist.PlaylistDetailScreen
import com.hypno.hypnovibe.ui.screen.playlist.PlaylistScreen
import com.hypno.hypnovibe.ui.screen.waveform.WaveformScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Playlist.route) { PlaylistScreen(navController) }
        composable(Screen.Editor.route) { TimelineEditorScreen(navController) }
        composable(Screen.Device.route) { DeviceScreen(navController) }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { })
        ) { entry ->
            PlaylistDetailScreen(entry.arguments?.getString("playlistId") ?: "", navController)
        }
        composable(Screen.ChannelMapping.route) { ChannelMappingScreen() }
        composable(Screen.ConfigList.route) { ConfigListScreen(navController) }
        composable(
            route = Screen.ConfigEditor.route,
            arguments = listOf(navArgument("configId") { defaultValue = "new" })
        ) { entry ->
            ConfigEditorScreen(entry.arguments?.getString("configId") ?: "new")
        }
        composable(Screen.Waveform.route) { WaveformScreen() }
    }
}
