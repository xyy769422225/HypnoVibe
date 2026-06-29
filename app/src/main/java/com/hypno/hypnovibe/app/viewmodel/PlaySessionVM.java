package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import android.net.Uri;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import com.hypno.hypnovibe.app.manager.PlaylistManager;
import com.hypno.hypnovibe.domain.entity.Playlist;
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
    private int currentTrackIndex = -1;
    private ScheduledExecutorService positionPoller;
    private volatile boolean completedFired = false;

    private final MutableStateFlow<Long> positionMs = StateFlowKt.MutableStateFlow(0L);
    private final MutableStateFlow<Long> durationMs = StateFlowKt.MutableStateFlow(0L);
    private final MutableStateFlow<Boolean> isPlaying = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<Boolean> isLoading = StateFlowKt.MutableStateFlow(false);
    private final MutableStateFlow<String> errorMsg = StateFlowKt.MutableStateFlow((String) null);

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
    }

    // ── StateFlow getters ──
    public StateFlow<List<Playlist>> getPlaylists() { return playlists; }
    public StateFlow<Playlist> getCurrentPlaylist() { return currentPlaylist; }
    public StateFlow<Boolean> getLoading() { return loading; }
    public StateFlow<Long> getPositionMs() { return positionMs; }
    public StateFlow<Long> getDurationMs() { return durationMs; }
    public StateFlow<Boolean> getIsPlayingState() { return isPlaying; }
    public StateFlow<Boolean> getIsLoading() { return isLoading; }
    public StateFlow<String> getErrorMsg() { return errorMsg; }
    public int getCurrentTrackIndex() { return currentTrackIndex; }

    // ── 播放列表操作 ──
    public void loadPlaylists() { playlists.setValue(manager.listPlaylists()); }
    public void openPlaylist(String id) { Playlist p = manager.findById(id); if (p != null) currentPlaylist.setValue(p); }
    public void createPlaylist(String name, String configId) { Playlist p = manager.createPlaylist(name, configId); currentPlaylist.setValue(p); loadPlaylists(); }

    public void addTrack(String playlistId, String audioPath, String title, long durationMs) {
        // 在后台线程把 content:// URI 持久化到内部存储
        ioExecutor.execute(() -> {
            String storedPath = persistToInternal(audioPath, title);
            Playlist p = manager.addTrack(playlistId, storedPath, title, durationMs);
            if (p != null) currentPlaylist.setValue(p);
        });
    }

    public void removeTrack(String playlistId, String trackId) { Playlist p = manager.removeTrack(playlistId, trackId); if (p != null) currentPlaylist.setValue(p); }
    public void setPlayMode(String mode) { Playlist p = currentPlaylist.getValue(); if (p != null) { p.setPlayMode(mode); manager.save(p); currentPlaylist.setValue(p); } }
    public void renamePlaylist(String id, String newName) { Playlist p = manager.findById(id); if (p != null) { manager.rename(p, newName); loadPlaylists(); } }
    public void deletePlaylist(String id) { manager.deletePlaylist(id); currentPlaylist.setValue(null); loadPlaylists(); }

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

                // 2. 创建新引擎
                audioEngine = new AudioEngine();

                // 3. 加载音频文件（流式解码，一步到位）
                if (!audioEngine.loadFile(getApplication(), audioPath)) {
                    Log.e(TAG, "loadFile failed: " + audioPath);
                    audioEngine.release();
                    audioEngine = null;
                    isLoading.setValue(false);
                    errorMsg.setValue("无法加载音频文件，请删除后重新添加");
                    return;
                }

                // 4. 开始播放
                currentTrackIndex = trackIndex;
                durationMs.setValue(audioEngine.getDurationMs());
                audioEngine.play();
                isPlaying.setValue(true);
                isLoading.setValue(false);
                completedFired = false;
                startPositionPoller();
                Log.d(TAG, "playing: duration=" + audioEngine.getDurationMs() + "ms");
            } catch (Throwable t) {
                Log.e(TAG, "playTrack crashed", t);
                if (audioEngine != null) {
                    try { audioEngine.release(); } catch (Exception ignored) {}
                    audioEngine = null;
                }
                isLoading.setValue(false);
                errorMsg.setValue("播放出错: " + t.getMessage());
            }
        });
    }

    public void togglePlayPause() {
        if (audioEngine == null) {
            // 未加载 → 自动播放第一首
            Playlist pl = currentPlaylist.getValue();
            if (pl != null && !pl.getTracks().isEmpty()) {
                playTrack(0);
            }
            return;
        }
        if (audioEngine.isPlaying()) {
            audioEngine.pause();
            isPlaying.setValue(false);
        } else {
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
        }
    }

    public void playNext() {
        Playlist pl = currentPlaylist.getValue();
        if (pl == null || currentTrackIndex < 0) return;
        int next = currentTrackIndex + 1;
        String mode = pl.getPlayMode();
        if (next >= pl.getTracks().size()) {
            if ("LOOP_LIST".equals(mode)) next = 0;
            else { isPlaying.setValue(false); return; }
        }
        playTrack(next);
    }

    public void playPrevious() {
        if (currentTrackIndex <= 0) { seek(0); return; }
        playTrack(currentTrackIndex - 1);
    }

    private void startPositionPoller() {
        if (positionPoller != null) return;
        completedFired = false;
        positionPoller = Executors.newSingleThreadScheduledExecutor();
        positionPoller.scheduleAtFixedRate(() -> {
            AudioEngine eng = audioEngine;
            if (eng == null) return;
            long pos = eng.getPositionMs();
            long dur = eng.getDurationMs();
            positionMs.setValue(pos);

            if (!eng.isPlaying() && dur > 0 && pos >= dur && !completedFired) {
                completedFired = true;
                isPlaying.setValue(false);
                Playlist pl = currentPlaylist.getValue();
                if (pl != null && "LOOP_LIST".equals(pl.getPlayMode())) {
                    playNext();
                }
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void stopPositionPoller() {
        if (positionPoller != null) {
            positionPoller.shutdownNow();
            positionPoller = null;
        }
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
