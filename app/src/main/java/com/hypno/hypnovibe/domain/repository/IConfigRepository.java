package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import java.util.*;

public interface IConfigRepository {
    List<DeviceConfig> listAll();
    Optional<DeviceConfig> findById(String id);
    void save(DeviceConfig c);
    void delete(String id);
}
