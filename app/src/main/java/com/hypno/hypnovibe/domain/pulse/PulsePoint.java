package com.hypno.hypnovibe.domain.pulse;

/**
 * .pulse 波形中的单个采样点。
 * 首尾点 anchor 固定为 1，中间点可以为 0。
 */
public class PulsePoint {
    /** 波形强度 0.0-100.0 */
    public float y;
    /** 锚点标记 0/1 */
    public int anchor;

    public PulsePoint() {}

    public PulsePoint(float y, int anchor) {
        this.y = y;
        this.anchor = anchor;
    }
}
