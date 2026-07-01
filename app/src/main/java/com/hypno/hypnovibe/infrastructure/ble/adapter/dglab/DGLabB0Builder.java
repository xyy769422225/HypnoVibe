package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

/**
 * DG-LAB V3 协议指令构造器。
 * 负责 B0（核心控制）和 BF（软上限与平衡参数）指令的字节构造。
 * 完全对齐官方 NewESTMExecutionData + NewPulseDataPL.bytesPackage。
 * package-private，仅 dglab 子包内部使用。
 */
final class DGLabB0Builder {

    /** B0 指令头 */
    static final byte B0_HEADER = (byte) 0xB0;
    /** BF 指令头 */
    static final byte BF_HEADER = (byte) 0xBF;

    // 强度解读方式（对齐官方 strengthOutPut）
    static final int MODE_UNCHANGED = 0;    // 不修改
    static final int MODE_RELATIVE_INC = 1;  // 相对增加
    static final int MODE_RELATIVE_DEC = 2;  // 相对减少
    static final int MODE_ABSOLUTE = 3;      // 绝对设置

    /** 默认波形数据（对齐官方 combinedData$default: new byte[]{0,0,0,0,0,0,0,-1}） */
    private static final int[] DEFAULT_WAVE_DATA = {0, 0, 0, 0, 0, 0, 0, 255};

    private DGLabB0Builder() {}

    /**
     * 构造 B0 指令（20 bytes）。
     * 对齐官方 combinedData(strengthData, waveDataA, waveDataB) = "B0" + strength + waveA + waveB。
     *
     * @param changeStrength 是否修改通道强度（false 时 seqNo/mode/strength 无效）
     * @param seqNo          序列号 0-15（0=不要求设备反馈）
     * @param modeA          A通道强度解读方式 0-3
     * @param modeB          B通道强度解读方式 0-3
     * @param strengthA      A通道强度设定值 0-200（绝对值 或 增量值，取决于 modeA）
     * @param strengthB      B通道强度设定值 0-200
     * @param waveDataA      A通道波形数据 8 bytes（对齐 bytesPackage 输出）
     * @param waveDataB      B通道波形数据 8 bytes
     */
    static byte[] buildB0(boolean changeStrength, int seqNo,
                          int modeA, int modeB,
                          int strengthA, int strengthB,
                          int[] waveDataA, int[] waveDataB) {
        byte[] cmd = new byte[20];
        cmd[0] = B0_HEADER;

        int effectiveSeq = changeStrength ? seqNo : 0;
        int effectiveModeA = changeStrength ? modeA : MODE_UNCHANGED;
        int effectiveModeB = changeStrength ? modeB : MODE_UNCHANGED;

        // 字节1: 控制字节（对齐官方 strengthOutPut）
        cmd[1] = (byte) ((effectiveSeq << 4) | ((effectiveModeA << 2) | effectiveModeB));
        // 字节2-3: 强度值（对齐官方 Math.abs(delta)）
        cmd[2] = (byte) strengthA;
        cmd[3] = (byte) strengthB;

        // 字节4-11: A通道波形（8 bytes）
        int[] aw = (waveDataA != null && waveDataA.length == 8) ? waveDataA : DEFAULT_WAVE_DATA;
        for (int i = 0; i < 8; i++) cmd[4 + i] = (byte) aw[i];

        // 字节12-19: B通道波形（8 bytes）
        int[] bw = (waveDataB != null && waveDataB.length == 8) ? waveDataB : DEFAULT_WAVE_DATA;
        for (int i = 0; i < 8; i++) cmd[12 + i] = (byte) bw[i];

        return cmd;
    }

    /**
     * 波形数据打包（对齐官方 NewPulseDataPL.bytesPackage）。
     * 输入频率数组和强度数组，输出 8 字节波形数据。
     *
     * @param freqs     频率值数组（1/2/4 元素），为 freqArray 协议值
     * @param strengths 强度值数组（1/2/4 元素），为协议强度值
     * @return 8 字节波形数据
     */
    static int[] packWaveData(int[] freqs, int[] strengths) {
        if (freqs == null || strengths == null) return DEFAULT_WAVE_DATA;
        int fl = freqs.length;
        int sl = strengths.length;

        if (fl == 1 && sl == 1) {
            // 1 元素：重复 4 次
            return new int[]{freqs[0], freqs[0], freqs[0], freqs[0],
                             strengths[0], strengths[0], strengths[0], strengths[0]};
        } else if (fl == 4 && sl == 4) {
            // 4 元素：直接拼接
            return new int[]{freqs[0], freqs[1], freqs[2], freqs[3],
                             strengths[0], strengths[1], strengths[2], strengths[3]};
        } else if (fl == 2 && sl == 2) {
            // 2 元素：交替扩展
            return new int[]{freqs[0], freqs[0], freqs[1], freqs[1],
                             strengths[0], strengths[0], strengths[1], strengths[1]};
        }
        // 无效输入：对齐官方 fallback
        return DEFAULT_WAVE_DATA;
    }

    /**
     * 构造 BF 指令（7 bytes）。
     * 对齐官方 packageMaxAndStep 格式。
     */
    static byte[] buildBF(int softLimitA, int softLimitB,
                          int balance1A, int balance1B,
                          int balance2A, int balance2B) {
        return new byte[] {
            BF_HEADER,
            (byte) softLimitA,
            (byte) softLimitB,
            (byte) balance1A,
            (byte) balance1B,
            (byte) balance2A,
            (byte) balance2B
        };
    }

    /** 默认 BF 配置：不限制软上限，balance1=160, balance2=0 */
    static byte[] buildDefaultBF() {
        return buildBF(200, 200, 160, 160, 0, 0);
    }

    /** 默认波形数据 8 字节（对齐官方 combinedData$default） */
    static int[] defaultWaveData() {
        return DEFAULT_WAVE_DATA.clone();
    }
}
