package com.hypno.hypnovibe.domain.entity;

import java.util.*;

public class StrengthPreset {
    private String id;
    private String name;
    private int channelAStrength;
    private int channelBStrength;

    public StrengthPreset() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getChannelAStrength() { return channelAStrength; }
    public void setChannelAStrength(int channelAStrength) { this.channelAStrength = channelAStrength; }

    public int getChannelBStrength() { return channelBStrength; }
    public void setChannelBStrength(int channelBStrength) { this.channelBStrength = channelBStrength; }
}
