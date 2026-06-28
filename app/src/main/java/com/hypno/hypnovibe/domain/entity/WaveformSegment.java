package com.hypno.hypnovibe.domain.entity;

import java.util.*;

public class WaveformSegment {
    private String id;
    private String channelId;
    private String label;
    private long startTimeMs;
    private long endTimeMs;
    private String waveformRef;
    private float strengthScale = 1.0f;
    private int loopCount = 1;
    private boolean disabled = false;

    public WaveformSegment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; }

    public long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(long endTimeMs) { this.endTimeMs = endTimeMs; }

    public String getWaveformRef() { return waveformRef; }
    public void setWaveformRef(String waveformRef) { this.waveformRef = waveformRef; }

    public float getStrengthScale() { return strengthScale; }
    public void setStrengthScale(float strengthScale) { this.strengthScale = strengthScale; }

    public int getLoopCount() { return loopCount; }
    public void setLoopCount(int loopCount) { this.loopCount = loopCount; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }
}
