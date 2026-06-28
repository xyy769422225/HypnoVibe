package com.hypno.hypnovibe.ui.screen.playlist

import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hypno.hypnovibe.app.viewmodel.PlaySessionVM
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.theme.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlistId: String, navController: NavController) {
    val vm: PlaySessionVM = viewModel()
    val current by vm.getCurrentPlaylist().collectAsState()
    val context = LocalContext.current

    LaunchedEffect(playlistId) { vm.openPlaylist(playlistId) }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            var title = "未知曲目"
            var durationMs = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) title = c.getString(idx)
                }
            }
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (d != null) durationMs = d.toLong()
                mmr.release()
            } catch (_: Exception) {}
            vm.addTrack(playlistId, uri.toString(), title, durationMs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "加载中...", color = GoldAncient) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = SilverGray)
                    }
                },
                actions = {
                    IconButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Filled.Add, "添加曲目", tint = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        },
        bottomBar = { PlayerBar(vm) }
    ) { padding ->
        val tracks = current?.tracks ?: emptyList()
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚无曲目", color = DarkGray)
                    Spacer(Modifier.height(8.dp))
                    Text("点击 + 添加音频文件", color = DarkGray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text("${tracks.size} 首曲目", color = SilverGray)
                    Spacer(Modifier.height(4.dp))
                }
                items(tracks, key = { it.trackId }) { track ->
                    val index = tracks.indexOf(track)
                    StoneCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.playTrack(index) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(track.audioTitle, color = SilverGray)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (track.durationMs > 0) {
                                        Text(
                                            "${track.durationMs / 60000}:${String.format("%02d", (track.durationMs / 1000) % 60)}",
                                            color = DarkGray
                                        )
                                    }
                                    if (track.timelineScriptPath != null) Text("时间轴", color = DarkGreen)
                                    else Text("无时间轴", color = DarkGray)
                                    if (track.hasMismatch()) Text("⚠", color = AlertRed)
                                }
                            }
                            IconButton(onClick = { vm.removeTrack(playlistId, track.trackId) }) {
                                Icon(Icons.Filled.Close, "移除", tint = DarkGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(vm: PlaySessionVM) {
    val position by vm.getPositionMs().collectAsState()
    val duration by vm.getDurationMs().collectAsState()
    val playing by vm.getIsPlayingState().collectAsState()
    val isLoading by vm.getIsLoading().collectAsState()
    val errorMsg by vm.getErrorMsg().collectAsState()

    Surface(color = DarkStoneBrown, shadowElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // 错误提示
            if (errorMsg != null) {
                Text("⚠ $errorMsg", color = AlertRed, style = MaterialTheme.typography.labelSmall)
            }

            // ── Seekbar ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), color = DarkGray, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { vm.seek((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = BloodRed,
                        activeTrackColor = BloodRed,
                        inactiveTrackColor = LeatherBrown
                    )
                )
                Text(formatTime(duration), color = DarkGray, style = MaterialTheme.typography.labelMedium)
            }

            // ── Controls + Play mode ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Play mode
                var modeExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { modeExpanded = true }) {
                        Icon(Icons.Filled.Repeat, "模式", tint = GoldAncient, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(playModeLabel(vm), color = GoldAncient, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                        listOf("LOOP_LIST" to "列表循环", "STOP_AT_END" to "播完停止").forEach { (v, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { vm.setPlayMode(v); modeExpanded = false }
                            )
                        }
                    }
                }

                // Prev
                IconButton(onClick = { vm.playPrevious() }) {
                    Icon(Icons.Filled.ChevronLeft, "上一首", tint = SilverGray)
                }

                // Play/Pause/Loading
                Button(
                    onClick = { vm.togglePlayPause() },
                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = SilverGray,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(if (playing) "⏸" else "▶", fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                    }
                }

                // Next
                IconButton(onClick = { vm.playNext() }) {
                    Icon(Icons.Filled.ChevronRight, "下一首", tint = SilverGray)
                }

                // Track index
                Text(
                    trackIndexLabel(vm),
                    color = DarkGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun playModeLabel(vm: PlaySessionVM): String {
    val current by vm.getCurrentPlaylist().collectAsState()
    return when (current?.playMode) {
        "STOP_AT_END" -> "播完停止"
        else -> "列表循环"
    }
}

@Composable
private fun trackIndexLabel(vm: PlaySessionVM): String {
    val current by vm.getCurrentPlaylist().collectAsState()
    val tracks = current?.tracks ?: emptyList()
    val idx = vm.getCurrentTrackIndex()
    return if (idx >= 0 && tracks.isNotEmpty()) "${idx + 1}/${tracks.size}" else ""
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "${totalSec / 60}:${String.format("%02d", totalSec % 60)}"
}
