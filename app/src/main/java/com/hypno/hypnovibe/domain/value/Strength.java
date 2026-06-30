package com.hypno.hypnovibe.domain.value;

/** 强度值对象，含钳制方法 */
public class Strength {
    private final int value;

    public Strength(int value) {
        this.value = value;
    }

    public int getValue() { return value; }

    public int clamp(int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() { return String.valueOf(value); }
}
