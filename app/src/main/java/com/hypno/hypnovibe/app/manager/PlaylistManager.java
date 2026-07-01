package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.Playlist;
import com.hypno.hypnovibe.domain.repository.IPlaylistRepository;
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
}
