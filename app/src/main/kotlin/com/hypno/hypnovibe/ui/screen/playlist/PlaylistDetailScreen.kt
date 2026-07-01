package com.hypno.hypnovibe.ui.screen.playlist

import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(playlistId: String, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: PlaySessionVM = viewModel(viewModelStoreOwner = activity)
    val current by vm.getCurrentPlaylist().collectAsState()
    val currentTrackIdx by vm.getCurrentTrackIndex().collectAsState()
    val isPlaying by vm.getIsPlayingState().collectAsState()
    val context = LocalContext.current

    // ── 时长不匹配详情弹窗状态 ──
    var mismatchDialogTrack by remember { mutableStateOf<com.hypno.hypnovibe.domain.entity.Playlist.Track?>(null) }

    // ── 手动选择时间轴脚本弹窗状态 ──
    var scriptPickerTrack by remember { mutableStateOf<com.hypno.hypnovibe.domain.entity.Playlist.Track?>(null) }

    // ── configId 不匹配警告弹窗 ──
    var configMismatchMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) { vm.openPlaylist(playlistId) }

    // 音频文件选择器
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

    // 时间轴脚本文件选择器
    val scriptPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        scriptPickerTrack?.let { track ->
            if (uri != null) {
                val scriptPath = uri.toString()
                // 校验 configId 匹配
                val playlistConfigId = current?.configId
                if (playlistConfigId != null) {
                    try {
                        val parser = org.json.JSONObject(
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                        )
                        val scriptConfigId = parser.optString("configId", null)
                        if (scriptConfigId != null && playlistConfigId != scriptConfigId) {
                            configMismatchMsg = "时间轴脚本的配置 ID 与此播放列表不匹配，无法关联"
                            scriptPickerTrack = null
                            return@let
                        }
                    } catch (_: Exception) {}
                }
                vm.setTimelineScript(playlistId, track.trackId, scriptPath)
                Toast.makeText(context, "已关联时间轴脚本", Toast.LENGTH_SHORT).show()
            }
            scriptPickerTrack = null
        }
    }

    // ── Scaffold ──
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
                    IconButton(onClick = {
                        navController.navigate("channel_mapping/$playlistId")
                    }) {
                        Icon(Icons.Filled.Settings, "通道映射", tint = SilverGray)
                    }
                    IconButton(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        enabled = !isPlaying
                    ) {
                        Icon(Icons.Filled.Add, "添加曲目", tint = if (isPlaying) DarkGray else BloodRed)
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
                itemsIndexed(tracks, key = { _, t -> t.trackId }) { index, track ->
                    val isCurrent = index == currentTrackIdx
                    val isFirst = index == 0
                    val isLast = index == tracks.size - 1

                    StoneCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.playTrack(index) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 当前播放曲目：显示播放/暂停图标
                                if (isCurrent) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "播放中" else "已暂停",
                                        tint = BloodRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track.audioTitle,
                                        color = if (isCurrent) GoldAncient else SilverGray
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (track.durationMs > 0) {
                                            Text(
                                                "${track.durationMs / 60000}:${String.format("%02d", (track.durationMs / 1000) % 60)}",
                                                color = if (isCurrent) BloodRed else DarkGray
                                            )
                                        }
                                        if (track.timelineScriptPath != null) Text("时间轴", color = DarkGreen)
                                        else Text("无时间轴", color = DarkGray)
                                        if (track.hasMismatch()) {
                                            Text(
                                                "⚠",
                                                color = AlertRed,
                                                modifier = Modifier.combinedClickable(
                                                    onClick = {},
                                                    onLongClick = { mismatchDialogTrack = track }
                                                )
                                            )
                                        }
                                    }
                                }
                                // 手动选择时间轴脚本
                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            Toast.makeText(context, "请先暂停播放", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scriptPickerTrack = track
                                            scriptPicker.launch(arrayOf("*/*"))
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        "选择时间轴",
                                        tint = DarkGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { vm.removeTrack(playlistId, track.trackId) }) {
                                    Icon(Icons.Filled.Close, "移除", tint = DarkGray, modifier = Modifier.size(18.dp))
                                }
                            }

                            // ── 排序按钮 ──
                            if (!isPlaying) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#${index + 1}", color = DarkGray, style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.width(8.dp))
                                    if (!isFirst) {
                                        IconButton(
                                            onClick = { vm.reorderTrack(playlistId, index, index - 1) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowUp, "上移", tint = SilverGray, modifier = Modifier.size(18.dp))
                                        }
                                    } else {
                                        Spacer(Modifier.size(28.dp))
                                    }
                                    if (!isLast) {
                                        IconButton(
                                            onClick = { vm.reorderTrack(playlistId, index, index + 1) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowDown, "下移", tint = SilverGray, modifier = Modifier.size(18.dp))
                                        }
                                    } else {
                                        Spacer(Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 时长不匹配详情弹窗 ──
    mismatchDialogTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { mismatchDialogTrack = null },
            containerColor = DarkStoneBrown,
            title = { Text("时长不匹配", color = GoldAncient) },
            text = {
                Column {
                    Text("音频时长:  ${formatTime(track.durationMs)}", color = SilverGray)
                    Text("脚本时长:  ${formatTime(track.scriptDurationMs)}", color = SilverGray)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "差异:  ${formatTime(kotlin.math.abs(track.durationMs - track.scriptDurationMs))}",
                        color = AlertRed
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "播放时将以音频实际时长为准，超出脚本范围的部分不输出设备指令。",
                        color = DarkGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { mismatchDialogTrack = null }) {
                    Text("知道了", color = GoldAncient)
                }
            }
        )
    }

    // ── configId 不匹配警告弹窗 ──
    configMismatchMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { configMismatchMsg = null },
            containerColor = DarkStoneBrown,
            title = { Text("配置不匹配", color = AlertRed) },
            text = { Text(msg, color = SilverGray) },
            confirmButton = {
                TextButton(onClick = { configMismatchMsg = null }) {
                    Text("知道了", color = GoldAncient)
                }
            }
        )
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
            if (errorMsg != null) {
                Text("⚠ $errorMsg", color = AlertRed, style = MaterialTheme.typography.labelSmall)
            }

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

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                var modeExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { modeExpanded = true }) {
                        Icon(Icons.Filled.Repeat, "模式", tint = GoldAncient, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(playModeLabel(vm), color = GoldAncient, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                        listOf("LOOP_LIST" to "列表循环", "LOOP_LAST" to "终曲循环", "STOP_AT_END" to "播完停止").forEach { (v, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { vm.setPlayMode(v); modeExpanded = false }
                            )
                        }
                    }
                }

                IconButton(onClick = { vm.playPrevious() }) {
                    Icon(Icons.Filled.ChevronLeft, "上一首", tint = SilverGray)
                }

                Button(
                    onClick = { vm.togglePlayPause() },
                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = SilverGray, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text(if (playing) "\u23F8" else "\u25B6", fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                    }
                }

                IconButton(onClick = { vm.playNext() }) {
                    Icon(Icons.Filled.ChevronRight, "下一首", tint = SilverGray)
                }

                Text(trackIndexLabel(vm), color = DarkGray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun playModeLabel(vm: PlaySessionVM): String {
    val current by vm.getCurrentPlaylist().collectAsState()
    return when (current?.playMode) {
        "LOOP_LAST" -> "终曲循环"
        "STOP_AT_END" -> "播完停止"
        else -> "列表循环"
    }
}

@Composable
private fun trackIndexLabel(vm: PlaySessionVM): String {
    val current by vm.getCurrentPlaylist().collectAsState()
    val tracks = current?.tracks ?: emptyList()
    val idx = vm.getCurrentTrackIndex().collectAsState().value
    return if (idx >= 0 && tracks.isNotEmpty()) "${idx + 1}/${tracks.size}" else ""
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "${totalSec / 60}:${String.format("%02d", totalSec % 60)}"
}
