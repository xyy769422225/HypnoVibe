package com.hypno.hypnovibe.domain.value;

/** 时间范围值对象 */
public class TimeRange {
    private final long startMs;
    private final long endMs;

    public TimeRange(long startMs, long endMs) {
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public long getStartMs() { return startMs; }
    public long getEndMs() { return endMs; }
    public long getDurationMs() { return endMs - startMs; }

    public boolean contains(long ms) {
        return ms >= startMs && ms <= endMs;
    }

    @Override
    public String toString() {
        return startMs + "ms-" + endMs + "ms";
    }
}
