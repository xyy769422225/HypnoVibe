package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.PairedDevice;
import java.util.*;

public interface IDeviceRepository {
    List<PairedDevice> listAll();
    Optional<PairedDevice> findById(String id);
    void save(PairedDevice d);
    void delete(String id);
}
