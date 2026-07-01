package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.Playlist;
import com.hypno.hypnovibe.domain.entity.TimelineScript;
import com.hypno.hypnovibe.domain.repository.IPlaylistRepository;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PlaylistManager {
    private final IPlaylistRepository repo;

    public PlaylistManager(IPlaylistRepository repo) { this.repo = repo; }

    public List<Playlist> listPlaylists() { return repo.listAll(); }

    public Playlist findById(String id) {
        Optional<Playlist> opt = repo.findById(id);
        return opt.orElse(null);
    }

    public Playlist createPlaylist(String name, String configId) {
        Playlist p = new Playlist(name, configId);
        repo.save(p);
        return p;
    }

    public Playlist addTrack(String playlistId, String audioPath, String title, long durationMs) {
        Optional<Playlist> opt = repo.findById(playlistId);
        if (!opt.isPresent()) return null;
        Playlist p = opt.get();
        p.getTracks().add(new Playlist.Track(audioPath, title, durationMs));
        p.touch();
        repo.save(p);
        return p;
    }

    public void reorderTracks(String playlistId, int from, int to) {
        Optional<Playlist> opt = repo.findById(playlistId);
        if (!opt.isPresent()) return;
        Playlist p = opt.get();
        List<Playlist.Track> tracks = p.getTracks();
        if (from < 0 || from >= tracks.size() || to < 0 || to >= tracks.size()) return;
        tracks.add(to, tracks.remove(from));
        p.touch();
        repo.save(p);
    }

    public void save(Playlist p) { p.touch(); repo.save(p); }

    public Playlist removeTrack(String playlistId, String trackId) {
        Optional<Playlist> opt = repo.findById(playlistId);
        if (!opt.isPresent()) return null;
        Playlist p = opt.get();
        p.getTracks().removeIf(t -> t.getTrackId().equals(trackId));
        p.touch();
        repo.save(p);
        return p;
    }

    public void rename(Playlist p, String newName) {
        p.setName(newName);
        save(p);
    }

    public void deletePlaylist(String id) { repo.delete(id); }

    /** 为播放列表中的指定曲目设置时间轴脚本路径 */
    public void setTimelineScript(String playlistId, String trackId, String scriptPath) {
        Optional<Playlist> opt = repo.findById(playlistId);
        if (!opt.isPresent()) return;
        Playlist p = opt.get();
        for (Playlist.Track t : p.getTracks()) {
            if (t.getTrackId().equals(trackId)) {
                t.setTimelineScriptPath(scriptPath);
                p.touch();
                repo.save(p);
                return;
            }
        }
    }

    /** 设置播放列表的通道映射 */
    public void setChannelMapping(String playlistId,
                                  Map<String, Playlist.ChannelMappingEntry> mapping) {
        Optional<Playlist> opt = repo.findById(playlistId);
        if (!opt.isPresent()) return;
        Playlist p = opt.get();
        p.setChannelMapping(mapping != null ? mapping : new HashMap<>());
        p.touch();
        repo.save(p);
    }

    /** 获取播放列表的通道映射 */
    public Map<String, Playlist.ChannelMappingEntry> getChannelMapping(String playlistId) {
        Optional<Playlist> opt = repo.findById(playlistId);
        if (!opt.isPresent()) return Collections.emptyMap();
        Map<String, Playlist.ChannelMappingEntry> mapping = opt.get().getChannelMapping();
        return mapping != null ? mapping : Collections.emptyMap();
    }

    public boolean validateConfigIdMatch(String a, String b) {
        return a != null && a.equals(b);
    }

    // ══════════════════════════════════════════════════════
    //  Phase 6: 校验方法
    // ══════════════════════════════════════════════════════

    /**
     * 校验时间轴脚本的 configId 是否与播放列表的 configId 匹配。
     * @return null 表示匹配，否则返回错误信息
     */
    public String validateConfigMatch(Playlist playlist, TimelineScript script) {
        if (script == null) return null;
        String scriptConfigId = script.getConfigId();
        String playlistConfigId = playlist.getConfigId();
        if (scriptConfigId == null || playlistConfigId == null) return null;
        if (!scriptConfigId.equals(playlistConfigId)) {
            return "时间轴脚本的配置 ID 与此播放列表不匹配";
        }
        return null;
    }

    /**
     * 检查音频时长与时间轴脚本时长是否匹配（允许 3 秒误差）。
     * @return true 表示时长匹配（或无法判断），false 表示不匹配
     */
    public boolean checkDurationMatch(Playlist.Track track, TimelineScript script) {
        if (script == null || track.getDurationMs() <= 0) return true;
        long scriptDuration = script.getTotalDurationMs();
        if (scriptDuration <= 0) return true;
        long diff = Math.abs(track.getDurationMs() - scriptDuration);
        return diff < 3000;
    }

    /**
     * 在曲目同文件夹下自动查找匹配的 .hvscript 文件。
     * @return 匹配到的脚本文件路径，未找到返回 null
     */
    public static String autoMatchScript(Playlist.Track track) {
        if (track == null) return null;
        String audioPath = track.getAudioFilePath();
        if (audioPath == null) return null;
        File audioFile = new File(audioPath);
        File dir = audioFile.getParentFile();
        if (dir == null || !dir.isDirectory()) return null;
        File[] scripts = dir.listFiles((d, name) -> name.endsWith(".hvscript"));
        if (scripts != null && scripts.length > 0) {
            // 取第一个匹配的 .hvscript
            return scripts[0].getAbsolutePath();
        }
        return null;
    }
}
