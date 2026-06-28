package com.hypno.hypnovibe.domain.entity;

import java.util.*;

public class TimelineScript {
    private String scriptId;
    private String name;
    private String configId;
    private long totalDurationMs;
    private List<String> disabledChannels = new ArrayList<>();
    private Map<String, String> metadata = new HashMap<>();

    public TimelineScript() {}

    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getConfigId() { return configId; }
    public void setConfigId(String configId) { this.configId = configId; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public List<String> getDisabledChannels() { return disabledChannels; }
    public void setDisabledChannels(List<String> disabledChannels) { this.disabledChannels = disabledChannels; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
