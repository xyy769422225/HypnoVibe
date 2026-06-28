package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.WaveformFile;
import java.util.*;

public interface IWaveformRepository {
    List<WaveformFile> listAll();
    Optional<WaveformFile> findById(String id);
    void save(WaveformFile w);
    void delete(String id);
}
