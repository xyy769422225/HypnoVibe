package com.hypno.hypnovibe.domain.entity;

import java.util.*;

public class WaveformFile {
    private String waveformId;
    private String name;
    private String deviceType;
    private String sourceFormat;
    private long totalDurationMs;
    private int sampleRate;
    private List<String> tags = new ArrayList<>();

    public WaveformFile() {}

    public String getWaveformId() { return waveformId; }
    public void setWaveformId(String waveformId) { this.waveformId = waveformId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public int getSampleRate() { return sampleRate; }
    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
