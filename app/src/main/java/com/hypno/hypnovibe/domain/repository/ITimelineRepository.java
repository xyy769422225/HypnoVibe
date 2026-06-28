package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.TimelineScript;
import java.util.*;

public interface ITimelineRepository {
    List<TimelineScript> listAll();
    Optional<TimelineScript> findById(String id);
    void save(TimelineScript t);
    void delete(String id);
}
