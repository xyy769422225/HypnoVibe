package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import android.net.Uri;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import com.hypno.hypnovibe.app.manager.ChannelMappingCoordinator;
import com.hypno.hypnovibe.app.manager.DeviceTypeRegistry;
import com.hypno.hypnovibe.app.manager.PlaybackCoordinator;
import com.hypno.hypnovibe.app.manager.PlaylistManager;
import com.hypno.hypnovibe.app.manager.TimelineEngine;
import com.hypno.hypnovibe.app.manager.TimelineManager;
import com.hypno.hypnovibe.domain.entity.ConnectedDevice;
import com.hypno.hypnovibe.domain.entity.Playlist;
import com.hypno.hypnovibe.domain.entity.TimelineScript;
import com.hypno.hypnovibe.domain.repository.IPlaylistRepository;
import com.hypno.hypnovibe.infrastructure.audio.AudioEngine;
import com.hypno.hypnovibe.infrastructure.io.JsonFileRepository;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public class PlaySessionVM extends AndroidViewModel {
    private static final String TAG = "PlaySessionVM";

    private final PlaylistManager manager;
    private final MutableStateFlow<List<Playlist>> playlists;
    private final MutableStateFlow<Playlist> currentPlaylist;
    private final MutableStateFlow<Boolean> loading;

    // ── 音频状态 ──
    private AudioEngine audioEngine;
    private final MutableStateFlow<Integer> currentTrackIndex = StateFlowKt.MutableStateFlow(-1);
    private ScheduledExecutorService positionPoller;
    private volatile boolean completedFired = false;

    // ── 播放锁与预加载（Phase 5.5）──
    private volatile boolean playbackLocked = false;
    private volatile boolean pendingReload = false;
    private final MutableStateFlow<Boolean> isLocked = StateFlowKt.MutableStateFlow(false);
    private TimelineManager timelineManager;
    private ChannelMappingCoordinator channelMappingCoordinator;
    private TimelineEngine timelineEngine;

    // ── Phase 6: PlaybackCoordinator ──
    private final PlaybackCoordinator playbackCoordinator;

    /**
     * 位置更新监听器，供 PlaybackCoordinator 接入驱动设备链路。
     * positionPoller 每 ~33ms 调用一次，传入当前音频播放位置。
     * Phase 6: PlaybackCoordinator 已注入，完整链路已打通。
     */
    public interface PositionListener {
        void onPositionUpdate(long positionMs);
    }
    private volatile PositionListener positionListener;
    public void setPositionListener(PositionListener l) { this.positionListener = l; }

    private final MutableStateFlow<Long> positionMs = StateFlowKt.MutableStateFlow(0L);
    private final MutableStateFlow<Long> durationMs = StateFlowKt.MutableStateFlow(0L);
    private final MutableStateFlow<Boolean> isPlaying = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<Boolean> isLoading = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<String> errorMsg = StateFlowKt.MutableStateFlow((String) null);
    private final MutableStateFlow<String> currentPlayingPlaylistId = StateFlowKt.MutableStateFlow((String) null);

    // 后台线程池：所有解码/IO 都在这里执行，绝不阻塞主线程
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audio-io");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    public PlaySessionVM(Application app) {
        super(app);
        playlists = StateFlowKt.MutableStateFlow(new ArrayList<>());
        currentPlaylist = StateFlowKt.MutableStateFlow((Playlist) null);
        loading = StateFlowKt.MutableStateFlow(false);
        JsonFileRepository jsonRepo = new JsonFileRepository(app, "playlists");
        this.manager = new PlaylistManager(new PlaylistRepo(jsonRepo));
        this.timelineManager = new TimelineManager(new JsonFileRepository(app, "timelines"));
        this.channelMappingCoordinator = new ChannelMappingCoordinator(new DeviceTypeRegistry());
        // Phase 6: 创建 PlaybackCoordinator 并注入为 PositionListener
        this.playbackCoordinator = new PlaybackCoordinator(channelMappingCoordinator);
        setPositionListener(playbackCoordinator);
    }

    // ── StateFlow getters ──
    public StateFlow<List<Playlist>> getPlaylists() { return playlists; }
    public StateFlow<Playlist> getCurrentPlaylist() { return currentPlaylist; }
    public StateFlow<Boolean> getLoading() { return loading; }
    public StateFlow<Long> getPositionMs() { return positionMs; }
    public StateFlow<Long> getDurationMs() { return durationMs; }
    public StateFlow<Boolean> getIsPlayingState() { return isPlaying; }
    public StateFlow<Boolean> getIsLoading() { return isLoading; }
    public StateFlow<Boolean> getIsLocked() { return isLocked; }
    public StateFlow<String> getErrorMsg() { return errorMsg; }
    public StateFlow<Integer> getCurrentTrackIndex() { return currentTrackIndex; }
    public StateFlow<String> getCurrentPlayingPlaylistId() { return currentPlayingPlaylistId; }

    // ── 播放列表操作 ──
    public void loadPlaylists() { playlists.setValue(manager.listPlaylists()); }
    public void openPlaylist(String id) { Playlist p = manager.findById(id); if (p != null) currentPlaylist.setValue(p); }
    public void createPlaylist(String name, String configId) { Playlist p = manager.createPlaylist(name, configId); currentPlaylist.setValue(p); loadPlaylists(); }

    public void addTrack(String playlistId, String audioPath, String title, long durationMs) {
        if (playbackLocked) {
            errorMsg.setValue("播放中无法添加曲目，请先暂停");
            return;
        }
        // 在后台线程把 content:// URI 持久化到内部存储
        ioExecutor.execute(() -> {
            String storedPath = persistToInternal(audioPath, title);
            Playlist p = manager.addTrack(playlistId, storedPath, title, durationMs);
            if (p != null) {
                // Phase 6: 自动匹配同文件夹的 .hvscript
                Playlist.Track newTrack = p.getTracks().get(p.getTracks().size() - 1);
                String scriptPath = PlaylistManager.autoMatchScript(newTrack);
                if (scriptPath != null) {
                    newTrack.setTimelineScriptPath(scriptPath);
                    newTrack.setAutoMatched(true);
                    // 校验 configId 匹配
                    TimelineScript script = timelineManager.load(scriptPath);
                    if (script != null) {
                        String err = manager.validateConfigMatch(p, script);
                        if (err != null) {
                            // configId 不匹配，清除脚本关联
                            newTrack.setTimelineScriptPath(null);
                            newTrack.setAutoMatched(false);
                            Log.w(TAG, "autoMatch rejected: " + err);
                        } else {
                            // 检查时长匹配
                            boolean match = manager.checkDurationMatch(newTrack, script);
                            newTrack.setHasMismatch(!match);
                            newTrack.setScriptDurationMs(script.getTotalDurationMs());
                        }
                    }
                    manager.save(p);
                }
                currentPlaylist.setValue(p);
                pendingReload = true;
            }
        });
    }

    public void removeTrack(String playlistId, String trackId) { Playlist p = manager.removeTrack(playlistId, trackId); if (p != null) currentPlaylist.setValue(p); }
    public void reorderTrack(String playlistId, int from, int to) { manager.reorderTracks(playlistId, from, to); Playlist p = manager.findById(playlistId); if (p != null) currentPlaylist.setValue(p); }
    public void setPlayMode(String mode) { Playlist p = currentPlaylist.getValue(); if (p != null) { p.setPlayMode(mode); manager.save(p); currentPlaylist.setValue(p); } }
    public void renamePlaylist(String id, String newName) { Playlist p = manager.findById(id); if (p != null) { manager.rename(p, newName); loadPlaylists(); } }
    public void deletePlaylist(String id) {
        manager.deletePlaylist(id);
        if (id != null && id.equals(currentPlayingPlaylistId.getValue())) {
            stopPositionPoller();
            if (audioEngine != null) { audioEngine.release(); audioEngine = null; }
            isPlaying.setValue(false);
            currentTrackIndex.setValue(-1);
            currentPlayingPlaylistId.setValue(null);
            positionMs.setValue(0L);
            durationMs.setValue(0L);
        }
        currentPlaylist.setValue(null);
        loadPlaylists();
    }
    public void savePlaylist(Playlist p) { manager.save(p); }

    // ══════════════════════════════════════════════════════
    //  音频播放 — 全异步，主线程零阻塞
    // ══════════════════════════════════════════════════════

    /** 异步加载并播放指定曲目 */
    public void playTrack(int index) {
        Playlist pl = currentPlaylist.getValue();
        if (pl == null || index < 0 || index >= pl.getTracks().size()) {
            Log.w(TAG, "playTrack rejected: index=" + index);
            return;
        }

        isLoading.setValue(true);
        errorMsg.setValue(null);
        final int trackIndex = index;
        final String audioPath = pl.getTracks().get(index).getAudioFilePath();

        ioExecutor.execute(() -> {
            Log.d(TAG, "playTrack bg: index=" + trackIndex + " path=" + audioPath);
            try {
                // 1. 释放旧引擎
                stopPositionPoller();
                if (audioEngine != null) {
                    audioEngine.release();
                    audioEngine = null;
                }
                positionMs.setValue(0L);
                durationMs.setValue(0L);
                isPlaying.setValue(false);

                // 2. 创建新引擎并加载文件（loadFile 内自动获取格式信息）
                audioEngine = new AudioEngine();
                if (!audioEngine.loadFile(getApplication(), audioPath)) {
                    Log.e(TAG, "loadFile failed: " + audioPath);
                    audioEngine = null;
                    setPlaybackLocked(false);
                    isLoading.setValue(false);
                    errorMsg.setValue("无法加载音频文件，请删除后重新添加");
                    return;
                }

                // 3. 开始播放
                currentTrackIndex.setValue(trackIndex);
                currentPlayingPlaylistId.setValue(pl.getId());
                durationMs.setValue(audioEngine.getDurationMs());
                audioEngine.play();
                isPlaying.setValue(true);
                isLoading.setValue(false);
                completedFired = false;
                setPlaybackLocked(true);
                startPositionPoller();
                Log.d(TAG, "playing: duration=" + audioEngine.getDurationMs() + "ms");
            } catch (Throwable t) {
                Log.e(TAG, "playTrack crashed", t);
                if (audioEngine != null) {
                    try { audioEngine.release(); } catch (Exception ignored) {}
                    audioEngine = null;
                }
                setPlaybackLocked(false);
                isLoading.setValue(false);
                errorMsg.setValue("播放出错: " + t.getMessage());
            }
        });
    }

    public void togglePlayPause() {
        if (audioEngine == null) {
            // 未加载 → 预加载时间轴索引 → 自动播放第一首
            Playlist pl = currentPlaylist.getValue();
            if (pl != null && !pl.getTracks().isEmpty()) {
                if (timelineEngine == null || pendingReload) {
                    preloadAll(pl);
                    pendingReload = false;
                }
                playTrack(0);
            }
            return;
        }
        if (audioEngine.isPlaying()) {
            // 暂停 → 解锁允许变更
            audioEngine.pause();
            isPlaying.setValue(false);
            stopPositionPoller();
            setPlaybackLocked(false);
        } else {
            // 恢复 → 如已变更则重新预加载
            if (pendingReload) {
                Playlist pl = currentPlaylist.getValue();
                if (pl != null) preloadAll(pl);
                pendingReload = false;
            }
            setPlaybackLocked(true);
            audioEngine.play();
            isPlaying.setValue(true);
            completedFired = false;
            startPositionPoller();
        }
    }

    public void seek(long ms) {
        if (audioEngine != null) {
            audioEngine.seek(ms);
            positionMs.setValue(ms);
            // Phase 6: seek 时刷新所有 Adapter，下一 tick 用新位置数据覆盖
            playbackCoordinator.seekNotify();
        }
    }

    public void playNext() {
        Playlist pl = currentPlaylist.getValue();
        int idx = currentTrackIndex.getValue();
        if (pl == null || idx < 0) return;
        int next = idx + 1;
        String mode = pl.getPlayMode();
        if (next >= pl.getTracks().size()) {
            if ("LOOP_LIST".equals(mode)) next = 0;
            else if ("LOOP_LAST".equals(mode)) { playTrack(idx); return; }
            else { setPlaybackLocked(false); isPlaying.setValue(false); currentPlayingPlaylistId.setValue(null); return; }
        }
        playTrack(next);
    }

    public void playPrevious() {
        int idx = currentTrackIndex.getValue();
        if (idx <= 0) { seek(0); return; }
        playTrack(idx - 1);
    }

    private void startPositionPoller() {
        if (positionPoller != null) return;
        completedFired = false;
        positionPoller = Executors.newSingleThreadScheduledExecutor();
        // 全局节拍器：33ms（~30Hz）轮询音频位置
        // 职责1: 更新 UI 进度条（positionMs StateFlow）
        // 职责2: 驱动设备链路（PositionListener → 后续 TimelineEngine → Coordinator → Adapter）
        positionPoller.scheduleAtFixedRate(() -> {
            AudioEngine eng = audioEngine;
            if (eng == null) return;
            long pos = eng.getPositionMs();
            long dur = eng.getDurationMs();
            positionMs.setValue(pos);

            // 驱动设备链路（拉取模型：Coordinator 推送快照，Adapter 内部 100ms 自取）
            if (positionListener != null) {
                positionListener.onPositionUpdate(pos);
            }

            if (!eng.isPlaying() && dur > 0 && pos >= dur && !completedFired) {
                completedFired = true;
                isPlaying.setValue(false);
                Playlist pl = currentPlaylist.getValue();
                if (pl != null && ("LOOP_LIST".equals(pl.getPlayMode()) || "LOOP_LAST".equals(pl.getPlayMode()))) {
                    playNext();
                } else {
                    // 播放完毕，解锁
                    setPlaybackLocked(false);
                    currentPlayingPlaylistId.setValue(null);
                }
            }
        }, 0, 33, TimeUnit.MILLISECONDS);
    }

    private void stopPositionPoller() {
        if (positionPoller != null) {
            positionPoller.shutdownNow();
            positionPoller = null;
        }
        // Phase 6: 停止时所有设备归零
        playbackCoordinator.stopAll();
    }

    // ══════════════════════════════════════════════════════
    //  播放锁与预加载（Phase 5.5）
    // ══════════════════════════════════════════════════════

    /** 完整预加载：加载所有时间轴脚本 + 构建二分查找索引 */
    private void preloadAll(Playlist playlist) {
        TimelineEngine engine = new TimelineEngine();
        for (Playlist.Track track : playlist.getTracks()) {
            String scriptPath = track.getTimelineScriptPath();
            if (scriptPath == null) continue;
            TimelineScript script = timelineManager.load(scriptPath);
            if (script == null) {
                Log.w(TAG, "preloadAll: script not found: " + scriptPath);
                continue;
            }
            for (TimelineScript.ChannelTimeline ct : script.getChannels()) {
                engine.registerChannel(ct);
            }
        }
        this.timelineEngine = engine;
        // Phase 6: 同步 coordinator 的引擎和通道映射
        playbackCoordinator.setTimelineEngine(engine);
        playbackCoordinator.setChannelMapping(playlist.getChannelMapping());
        Log.d(TAG, "preloadAll: " + engine.getChannelCount() + " channels indexed");
    }

    private void setPlaybackLocked(boolean locked) {
        this.playbackLocked = locked;
        isLocked.setValue(locked);
    }

    /** 切换时间轴脚本（仅暂停时允许） */
    public void setTimelineScript(String playlistId, String trackId, String scriptPath) {
        if (playbackLocked) {
            errorMsg.setValue("播放中无法切换时间轴，请先暂停");
            return;
        }
        manager.setTimelineScript(playlistId, trackId, scriptPath);
        pendingReload = true;
        Playlist pl = currentPlaylist.getValue();
        if (pl != null && pl.getId().equals(playlistId)) {
            currentPlaylist.setValue(manager.findById(playlistId));
        }
    }

    /** 获取当前播放列表的通道映射 */
    public Map<String, Playlist.ChannelMappingEntry> getChannelMapping() {
        Playlist pl = currentPlaylist.getValue();
        if (pl == null) return Collections.emptyMap();
        return pl.getChannelMapping();
    }

    /** 设置通道映射并持久化 */
    public void updateChannelMapping(String playlistId,
                                     Map<String, Playlist.ChannelMappingEntry> mapping) {
        if (playbackLocked) {
            errorMsg.setValue("播放中无法修改通道映射，请先暂停");
            return;
        }
        manager.setChannelMapping(playlistId, mapping);
        // Phase 6: 同步 coordinator 的映射
        playbackCoordinator.setChannelMapping(mapping);
        Playlist pl = currentPlaylist.getValue();
        if (pl != null && pl.getId().equals(playlistId)) {
            currentPlaylist.setValue(manager.findById(playlistId));
        }
    }

    // ══════════════════════════════════════════════════════
    //  Phase 6: 设备生命周期（供 DeviceManagerVM 调用）
    // ══════════════════════════════════════════════════════

    /** 设备连接成功时，注册到 PlaybackCoordinator */
    public void onDeviceConnected(ConnectedDevice device) {
        playbackCoordinator.registerDevice(device);
    }

    /** 设备断开时，从 PlaybackCoordinator 注销 */
    public void onDeviceDisconnected(String mac) {
        playbackCoordinator.unregisterDevice(mac);
    }

    /** 获取 PlaybackCoordinator（供 DeviceManagerVM 等外部组件访问） */
    public PlaybackCoordinator getPlaybackCoordinator() {
        return playbackCoordinator;
    }

    // ══════════════════════════════════════════════════════
    //  文件持久化
    // ══════════════════════════════════════════════════════

    /** content:// URI → app 内部存储文件路径 */
    private String persistToInternal(String uriString, String title) {
        if (uriString == null || uriString.isEmpty()) return uriString;
        if (uriString.startsWith("/")) return uriString;
        try {
            Uri uri = Uri.parse(uriString);
            if (!"content".equals(uri.getScheme())) return uriString;

            InputStream is = getApplication().getContentResolver().openInputStream(uri);
            if (is == null) return uriString;

            String suffix = ".mp3";
            if (title != null) {
                int dot = title.lastIndexOf('.');
                if (dot >= 0) suffix = title.substring(dot);
            } else {
                String last = uri.getLastPathSegment();
                if (last != null) {
                    int dot = last.lastIndexOf('.');
                    if (dot >= 0) suffix = last.substring(dot);
                }
            }

            File dir = new File(getApplication().getFilesDir(), "audio");
            dir.mkdirs();
            File out = new File(dir, "track_" + System.currentTimeMillis() + suffix);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();
            return out.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "persistToInternal failed", e);
            return uriString;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopPositionPoller();
        ioExecutor.shutdownNow();
        if (audioEngine != null) { audioEngine.release(); audioEngine = null; }
        // Phase 6: 清理 coordinator
        playbackCoordinator.clearAll();
    }

    static class PlaylistRepo implements IPlaylistRepository {
        private final JsonFileRepository r;
        PlaylistRepo(JsonFileRepository r) { this.r = r; }
        public List<Playlist> listAll() { return r.listAll("*.json", Playlist.class); }
        public Optional<Playlist> findById(String id) { return r.findById(id, Playlist.class); }
        public void save(Playlist p) { p.touch(); r.save(p.getId(), p); }
        public void delete(String id) { r.delete(id); }
    }
}
