package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

/**
 * 郊狼 V3 协议编解码器。
 * <p>
 * 纯静态方法，零状态。负责 V3 所有蓝牙指令的字节级编解码。
 * package-private，仅郊狼子包内部使用。
 *
 * <h3>指令结构参考</h3>
 * <pre>
 * B0 (20 bytes): HEAD + SeqMode + StrengthA/B + FreqA[4] + WaveA[4] + FreqB[4] + WaveB[4]
 * BF (7 bytes):  HEAD + SoftLimitA/B + Balance1A/B + Balance2A/B
 * B1 (4 bytes):  HEAD + SeqNo + StrengthA + StrengthB
 * </pre>
 */
final class CoyoteV3Protocol {

    private CoyoteV3Protocol() {}

    // ===== B0 指令 =====

    /**
     * 构造 B0 核心控制指令（20 bytes）。
     *
     * <pre>
     * 字节0:     0xB0 (指令头)
     * 字节1:     高4bit=序列号 | 低4bit=强度解读方式(bit3-2=A, bit1-0=B)
     * 字节2:     A通道强度设定值 (0-200)
     * 字节3:     B通道强度设定值 (0-200)
     * 字节4-7:   A通道波形频率 ×4 (25ms each, 10-240)
     * 字节8-11:  A通道波形强度 ×4 (25ms each, 0-100)
     * 字节12-15: B通道波形频率 ×4
     * 字节16-19: B通道波形强度 ×4
     * </pre>
     *
     * @param seqNo         序列号 0-15, 0=不要求反馈
     * @param strengthModeA A通道强度解读方式 (MODE_UNCHANGED/INCREMENT/DECREMENT/ABSOLUTE)
     * @param strengthModeB B通道强度解读方式
     * @param strengthA     A通道强度设定值 0-200
     * @param strengthB     B通道强度设定值 0-200
     * @param freqA         A通道波形频率×4 (已换算的协议值 10-240)
     * @param strengthWaveA A通道波形强度×4 (0-100)
     * @param freqB         B通道波形频率×4
     * @param strengthWaveB B通道波形强度×4
     * @return 20字节的B0指令
     */
    static byte[] buildB0(int seqNo,
                          int strengthModeA, int strengthModeB,
                          int strengthA, int strengthB,
                          int[] freqA, int[] strengthWaveA,
                          int[] freqB, int[] strengthWaveB) {
        byte[] cmd = new byte[CoyoteConstants.B0_LENGTH];
        cmd[0] = CoyoteConstants.HEAD_B0;

        // 字节1: 高4bit=序列号, 低4bit=强度解读方式
        // bit3-2=A通道, bit1-0=B通道
        cmd[1] = (byte) ((seqNo << 4) | ((strengthModeA << 2) | strengthModeB));

        // 字节2-3: 通道强度
        cmd[2] = (byte) clampStrength(strengthA);
        cmd[3] = (byte) clampStrength(strengthB);

        // 字节4-7: A通道波形频率 ×4
        for (int i = 0; i < CoyoteConstants.SLOTS_PER_WINDOW; i++) {
            cmd[4 + i] = (byte) clampFreq(freqA[i]);
        }

        // 字节8-11: A通道波形强度 ×4
        for (int i = 0; i < CoyoteConstants.SLOTS_PER_WINDOW; i++) {
            cmd[8 + i] = (byte) clampWaveStrength(strengthWaveA[i]);
        }

        // 字节12-15: B通道波形频率 ×4
        for (int i = 0; i < CoyoteConstants.SLOTS_PER_WINDOW; i++) {
            cmd[12 + i] = (byte) clampFreq(freqB[i]);
        }

        // 字节16-19: B通道波形强度 ×4
        for (int i = 0; i < CoyoteConstants.SLOTS_PER_WINDOW; i++) {
            cmd[16 + i] = (byte) clampWaveStrength(strengthWaveB[i]);
        }

        return cmd;
    }

    /**
     * 构造仅波形数据的 B0 指令（不修改通道强度）。
     * 强度解读方式设为不变(0b0000)，序列号=0。
     */
    static byte[] buildB0WaveformOnly(int[] freqA, int[] strengthWaveA,
                                       int[] freqB, int[] strengthWaveB) {
        return buildB0(CoyoteConstants.SEQ_NO_FEEDBACK,
                CoyoteConstants.MODE_UNCHANGED, CoyoteConstants.MODE_UNCHANGED,
                0, 0,
                freqA, strengthWaveA,
                freqB, strengthWaveB);
    }

    /**
     * 构造单通道 B0 指令（另一通道强度>100使其被设备放弃）。
     * 仅 A 通道输出波形时，B 通道某个强度值设为 101。
     */
    static byte[] buildB0SingleChannel(boolean channelA,
                                        int seqNo, int strengthMode, int strength,
                                        int[] freq, int[] strengthWave) {
        if (channelA) {
            return buildB0(seqNo,
                    strengthMode, CoyoteConstants.MODE_UNCHANGED,
                    strength, 0,
                    freq, strengthWave,
                    new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 101});
        } else {
            return buildB0(seqNo,
                    CoyoteConstants.MODE_UNCHANGED, strengthMode,
                    0, strength,
                    new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 101},
                    freq, strengthWave);
        }
    }

    // ===== BF 指令 =====

    /**
     * 构造 BF 软上限与平衡参数指令（7 bytes）。
     *
     * <pre>
     * 字节0:     0xBF (指令头)
     * 字节1:     A通道强度软上限 (0-200, 超出不变)
     * 字节2:     B通道强度软上限 (0-200, 超出不变)
     * 字节3:     A通道频率平衡参数1 (0-255)
     * 字节4:     B通道频率平衡参数1 (0-255)
     * 字节5:     A通道频率平衡参数2 (0-255)
     * 字节6:     B通道频率平衡参数2 (0-255)
     * </pre>
     */
    static byte[] buildBF(int softLimitA, int softLimitB,
                           int balance1A, int balance1B,
                           int balance2A, int balance2B) {
        return new byte[]{
                CoyoteConstants.HEAD_BF,
                (byte) softLimitA,
                (byte) softLimitB,
                (byte) balance1A,
                (byte) balance1B,
                (byte) balance2A,
                (byte) balance2B
        };
    }

    /** 构造默认 BF 指令（软上限200不限制, 平衡128中性） */
    static byte[] buildDefaultBF() {
        return buildBF(
                CoyoteConstants.BF_DEFAULT_SOFT_LIMIT, CoyoteConstants.BF_DEFAULT_SOFT_LIMIT,
                CoyoteConstants.BF_DEFAULT_BALANCE, CoyoteConstants.BF_DEFAULT_BALANCE,
                CoyoteConstants.BF_DEFAULT_BALANCE, CoyoteConstants.BF_DEFAULT_BALANCE
        );
    }

    // ===== B1 消息解析 =====

    /**
     * 解析 B1 强度反馈消息（4 bytes）。
     *
     * <pre>
     * 字节0: 0xB1 (消息头)
     * 字节1: 序列号
     * 字节2: A通道当前实际强度
     * 字节3: B通道当前实际强度
     * </pre>
     *
     * @param data Notify 收到的原始数据
     * @return B1Message，若数据无效则返回 null
     */
    static CoyoteB1Message parseB1(byte[] data) {
        if (data == null || data.length < CoyoteConstants.B1_LENGTH) {
            return null;
        }
        if ((data[0] & 0xFF) != (CoyoteConstants.HEAD_B1 & 0xFF)) {
            return null;
        }
        int seqNo = data[1] & 0xFF;
        int strengthA = data[2] & 0xFF;
        int strengthB = data[3] & 0xFF;
        return new CoyoteB1Message(seqNo, strengthA, strengthB);
    }

    // ===== 安全停止 =====

    /**
     * 构造安全停止 B0 指令。
     * 绝对设置模式 + 强度归零 + 序列号=1 要求确认。
     */
    static byte[] buildEmergencyStop() {
        return buildB0(1,
                CoyoteConstants.MODE_ABSOLUTE, CoyoteConstants.MODE_ABSOLUTE,
                0, 0,
                new int[]{10, 10, 10, 10}, new int[]{0, 0, 0, 0},
                new int[]{10, 10, 10, 10}, new int[]{0, 0, 0, 0}
        );
    }

    // ===== 100ms 窗口拆分为 4 个 25ms 槽位 =====

    /**
     * 将波形帧数组按 offsetMs 偏移后，均匀拆分为 4 个 25ms 槽位。
     * <p>
     * 使用简单线性插值从帧序列中采样当前100ms窗口对应的4个点。
     * 频率值会通过 FrequencyConverter 换算为协议值。
     *
     * @param frames     波形帧序列（按时间升序排列）
     * @param offsetMs   当前段内的播放偏移(ms)
     * @param outFreq    输出：4个槽位的频率（已换算的协议值）
     * @param outStrength 输出：4个槽位的强度
     */
    static void splitToSlots(CoyoteWaveFrame[] frames, long offsetMs,
                             int[] outFreq, int[] outStrength) {
        if (frames == null || frames.length == 0) {
            fillSilent(outFreq, outStrength);
            return;
        }

        long slotDur = CoyoteConstants.OUTPUT_WINDOW_MS / CoyoteConstants.SLOTS_PER_WINDOW; // 25ms

        for (int i = 0; i < CoyoteConstants.SLOTS_PER_WINDOW; i++) {
            long sampleTimeMs = offsetMs + i * slotDur;

            CoyoteWaveFrame frame = findNearestFrame(frames, sampleTimeMs);
            if (frame != null) {
                outFreq[i] = CoyoteFrequencyConverter.toProtocol(frame.frequency);
                outStrength[i] = clampWaveStrength(frame.strength);
            } else {
                outFreq[i] = CoyoteConstants.WAVE_FREQ_MIN;
                outStrength[i] = 0;
            }
        }
    }

    /** 找到最接近指定时间的帧 */
    private static CoyoteWaveFrame findNearestFrame(CoyoteWaveFrame[] frames, long timeMs) {
        CoyoteWaveFrame nearest = frames[0];
        long minDiff = Math.abs(frames[0].timeMs - timeMs);

        for (CoyoteWaveFrame f : frames) {
            long diff = Math.abs(f.timeMs - timeMs);
            if (diff < minDiff) {
                minDiff = diff;
                nearest = f;
            }
        }
        return nearest;
    }

    // ===== 静默槽位 =====

    /** 填充静默槽位（频率=最小值10, 强度=0） */
    static void fillSilent(int[] outFreq, int[] outStrength) {
        for (int i = 0; i < CoyoteConstants.SLOTS_PER_WINDOW; i++) {
            outFreq[i] = CoyoteConstants.WAVE_FREQ_MIN;
            outStrength[i] = 0;
        }
    }

    // ===== 范围钳制 =====

    private static int clampStrength(int value) {
        return Math.max(CoyoteConstants.STRENGTH_V3_MIN,
                Math.min(CoyoteConstants.STRENGTH_V3_MAX, value));
    }

    private static int clampFreq(int value) {
        return Math.max(CoyoteConstants.WAVE_FREQ_MIN,
                Math.min(CoyoteConstants.WAVE_FREQ_MAX, value));
    }

    private static int clampWaveStrength(int value) {
        return Math.max(0,
                Math.min(CoyoteConstants.WAVE_STRENGTH_MAX, value));
    }
}
