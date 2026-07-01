package com.hypno.hypnovibe.domain.pulse;

/**
 * .pulse 波形文件的完整内存模型。
 * 对应官方 APP 中 pulseType=0 的 Dungeonlab+pulse 格式。
 */
public class PulseData {
    /** 节间间隔 0-100 */
    public int L;
    /** 播放倍速 1/2/4 */
    public int playRate;
    /** 占空比 1-16 */
    public int ZY;
    /** 波形段列表 */
    public PulseSection[] sections;
    /** 波形名称 */
    public String name;

    /** 计算总节数（JIE=1 的 section 数） */
    public int getJieCount() {
        int count = 0;
        if (sections != null) {
            for (PulseSection s : sections) {
                if (s.JIE == 1) count++;
            }
        }
        return count == 0 && sections != null && sections.length > 0 ? 1 : count;
    }

    /** 计算总帧数（所有 section 的 points 数之和） */
    public int getTotalFrames() {
        int total = 0;
        if (sections != null) {
            for (PulseSection s : sections) {
                if (s.points != null) total += s.points.length;
            }
        }
        return total;
    }
}
