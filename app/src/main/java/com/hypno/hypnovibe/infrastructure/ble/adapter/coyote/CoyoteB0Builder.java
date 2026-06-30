package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

/**
 * 郊狼 V3 协议指令构造器。
 * 负责 B0（核心控制）和 BF（软上限与平衡参数）指令的字节构造。
 * package-private，仅郊狼子包内部使用。
 */
final class CoyoteB0Builder {

    /** B0 指令头 */
    static final byte B0_HEADER = (byte) 0xB0;
    /** BF 指令头 */
    static final byte BF_HEADER = (byte) 0xBF;

    // 强度解读方式
    static final int MODE_UNCHANGED = 0;  // 强度不变
    static final int MODE_RELATIVE_INC = 1;  // 相对增加
    static final int MODE_RELATIVE_DEC = 2;  // 相对减少
    static final int MODE_ABSOLUTE = 3;      // 绝对设置

    private CoyoteB0Builder() {}

    /**
     * 构造 B0 指令（20 bytes）。
     *
     * @param changeStrength 是否修改通道强度（false 时 seqNo/mode/strength 无效）
     * @param seqNo          序列号 0-15（0=不要求反馈）
     * @param modeA          A通道强度解读方式 0-3
     * @param modeB          B通道强度解读方式 0-3
     * @param strengthA      A通道强度设定值 0-200
     * @param strengthB      B通道强度设定值 0-200
     * @param freqA          A通道波形频率×4（协议值10-240）
     * @param waveStrengthA  A通道波形强度×4（0-100）
     * @param freqB          B通道波形频率×4
     * @param waveStrengthB  B通道波形强度×4
     */
    static byte[] buildB0(boolean changeStrength, int seqNo,
                          int modeA, int modeB,
                          int strengthA, int strengthB,
                          int[] freqA, int[] waveStrengthA,
                          int[] freqB, int[] waveStrengthB) {
        byte[] cmd = new byte[20];
        cmd[0] = B0_HEADER;

        int effectiveSeq = changeStrength ? seqNo : 0;
        int effectiveModeA = changeStrength ? modeA : MODE_UNCHANGED;
        int effectiveModeB = changeStrength ? modeB : MODE_UNCHANGED;

        cmd[1] = (byte) ((effectiveSeq << 4) | ((effectiveModeA << 2) | effectiveModeB));
        cmd[2] = (byte) strengthA;
        cmd[3] = (byte) strengthB;

        for (int i = 0; i < 4; i++) cmd[4 + i] = (byte) freqA[i];
        for (int i = 0; i < 4; i++) cmd[8 + i] = (byte) waveStrengthA[i];
        for (int i = 0; i < 4; i++) cmd[12 + i] = (byte) freqB[i];
        for (int i = 0; i < 4; i++) cmd[16 + i] = (byte) waveStrengthB[i];

        return cmd;
    }

    /**
     * 构造 BF 指令（7 bytes）。
     *
     * @param softLimitA A通道强度软上限 0-200
     * @param softLimitB B通道强度软上限 0-200
     * @param balance1A  A通道频率平衡参数1 0-255
     * @param balance1B  B通道频率平衡参数1 0-255
     * @param balance2A  A通道频率平衡参数2 0-255
     * @param balance2B  B通道频率平衡参数2 0-255
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

    /** 默认 BF 配置：不限制软上限，中性平衡 */
    static byte[] buildDefaultBF() {
        return buildBF(200, 200, 128, 128, 128, 128);
    }

    /** 基础波形（固定低频恒定）：频率100，波形强度50，4个槽位相同 */
    static int[] basicFreq() {
        return new int[]{100, 100, 100, 100};
    }

    static int[] basicWaveStrength() {
        return new int[]{50, 50, 50, 50};
    }
}
