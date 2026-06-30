package com.hypno.hypnovibe.domain.value;

import java.util.UUID;

/** 通道ID值对象 */
public class ChannelId {
    private final String value;

    public ChannelId(String value) {
        this.value = value;
    }

    public static ChannelId generate() {
        return new ChannelId(UUID.randomUUID().toString());
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChannelId)) return false;
        return value.equals(((ChannelId) o).value);
    }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}
