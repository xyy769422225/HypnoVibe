package com.hypno.hypnovibe.domain.pulse;

/**
 * .pulse 波形的单个段。
 * 字段含义与官方 APP Pluse.PulseSection 一致。
 */
public class PulseSection {
    /** 起始频率索引 0-83（映射 freqArray） */
    public int A;
    /** 结束频率索引 0-83 */
    public int B;
    /** 强度值 0-99 */
    public int J;
    /** 渐变类型 1=无, 2=对数, 3=线性, 4=指数 */
    public int PC;
    /** 节标记 0=节内, 1=新节开始 */
    public int JIE;
    /** 波形点数组 */
    public PulsePoint[] points;
}
