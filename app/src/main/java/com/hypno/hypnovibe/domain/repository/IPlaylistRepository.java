package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.Playlist;
import java.util.*;

public interface IPlaylistRepository {
    List<Playlist> listAll();
    Optional<Playlist> findById(String id);
    void save(Playlist p);
    void delete(String id);
}
