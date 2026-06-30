package com.hypno.hypnovibe.domain.value;

import java.util.UUID;

/** 配置ID值对象 */
public class ConfigId {
    private final String value;

    public ConfigId(String value) {
        this.value = value;
    }

    public static ConfigId generate() {
        return new ConfigId(UUID.randomUUID().toString());
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigId)) return false;
        return value.equals(((ConfigId) o).value);
    }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}
