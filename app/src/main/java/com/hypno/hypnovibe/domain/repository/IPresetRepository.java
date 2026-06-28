package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.StrengthPreset;
import java.util.*;

public interface IPresetRepository {
    List<StrengthPreset> listAll();
    Optional<StrengthPreset> findById(String id);
    void save(StrengthPreset s);
    void delete(String id);
}
