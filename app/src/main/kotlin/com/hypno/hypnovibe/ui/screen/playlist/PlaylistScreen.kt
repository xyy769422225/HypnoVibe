package com.hypno.hypnovibe.ui.screen.playlist

import android.widget.Toast
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
import com.hypno.hypnovibe.domain.entity.Playlist
import com.hypno.hypnovibe.ui.component.*
import com.hypno.hypnovibe.ui.navigation.Screen
import com.hypno.hypnovibe.ui.theme.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(navController: NavController) {
    val vm: PlaySessionVM = viewModel()
    val playlists by vm.getPlaylists().collectAsState()
    val context = LocalContext.current

    var showNewDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(Unit) { vm.loadPlaylists() }

    // New playlist dialog
    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建播放列表", color = GoldAncient) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("名称") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BloodRed, focusedLabelColor = GoldAncient,
                        unfocusedBorderColor = DarkCopper, cursorColor = SilverGray
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        vm.createPlaylist(newName.trim(), "")
                        showNewDialog = false; newName = ""
                        Toast.makeText(context, "已创建", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("创建", color = BloodRed) }
            },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("取消") } },
            containerColor = DarkStoneBrown
        )
    }

    // Rename dialog
    showRenameDialog?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("重命名", color = GoldAncient) },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    label = { Text("新名称") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BloodRed, focusedLabelColor = GoldAncient,
                        unfocusedBorderColor = DarkCopper, cursorColor = SilverGray
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        vm.renamePlaylist(playlist.id, renameText.trim())
                        showRenameDialog = null
                    }
                }) { Text("确定", color = BloodRed) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("取消") } },
            containerColor = DarkStoneBrown
        )
    }

    // Delete confirm
    showDeleteConfirm?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除播放列表", color = AlertRed) },
            text = { Text("确定删除\"${playlist.name}\"？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(playlist.id)
                    showDeleteConfirm = null
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = AlertRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } },
            containerColor = DarkStoneBrown
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放列表", color = GoldAncient) },
                actions = {
                    IconButton(onClick = { showNewDialog = true; newName = "" }) {
                        Icon(Icons.Filled.Add, "新建", tint = BloodRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkStoneBrown)
            )
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无播放列表", color = DarkGray)
                    Spacer(Modifier.height(12.dp))
                    DungeonButton(text = "新建播放列表", onClick = { showNewDialog = true; newName = "" })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    StoneCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(
                                    Screen.PlaylistDetail.route.replace("{playlistId}", playlist.id)
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, color = GoldAncient)
                                Text(
                                    "${playlist.tracks?.size ?: 0} 首曲目",
                                    color = DarkGray
                                )
                            }
                            Box {
                                var expanded by remember { mutableStateOf(false) }
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Filled.MoreVert, "更多", tint = SilverGray)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        onClick = {
                                            renameText = playlist.name
                                            showRenameDialog = playlist
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除", color = AlertRed) },
                                        onClick = {
                                            showDeleteConfirm = playlist
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
