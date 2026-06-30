package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

/**
 * DG-LAB V3 协议编解码器。
 * <p>
 * 纯静态方法，零状态。负责 V3 所有蓝牙指令的字节级编解码。
 * package-private，仅 dglab 子包内部使用。
 */
final class DGLabV3Protocol {

    private DGLabV3Protocol() {}

    // ===== B0 指令 =====

    static byte[] buildB0(int seqNo,
                          int strengthModeA, int strengthModeB,
                          int strengthA, int strengthB,
                          int[] freqA, int[] strengthWaveA,
                          int[] freqB, int[] strengthWaveB) {
        byte[] cmd = new byte[DGLabConstants.B0_LENGTH];
        cmd[0] = DGLabConstants.HEAD_B0;
        cmd[1] = (byte) ((seqNo << 4) | ((strengthModeA << 2) | strengthModeB));
        cmd[2] = (byte) clampStrength(strengthA);
        cmd[3] = (byte) clampStrength(strengthB);

        for (int i = 0; i < DGLabConstants.SLOTS_PER_WINDOW; i++) {
            cmd[4 + i] = (byte) clampFreq(freqA[i]);
        }
        for (int i = 0; i < DGLabConstants.SLOTS_PER_WINDOW; i++) {
            cmd[8 + i] = (byte) clampWaveStrength(strengthWaveA[i]);
        }
        for (int i = 0; i < DGLabConstants.SLOTS_PER_WINDOW; i++) {
            cmd[12 + i] = (byte) clampFreq(freqB[i]);
        }
        for (int i = 0; i < DGLabConstants.SLOTS_PER_WINDOW; i++) {
            cmd[16 + i] = (byte) clampWaveStrength(strengthWaveB[i]);
        }

        return cmd;
    }

    static byte[] buildB0WaveformOnly(int[] freqA, int[] strengthWaveA,
                                       int[] freqB, int[] strengthWaveB) {
        return buildB0(DGLabConstants.SEQ_NO_FEEDBACK,
                DGLabConstants.MODE_UNCHANGED, DGLabConstants.MODE_UNCHANGED,
                0, 0, freqA, strengthWaveA, freqB, strengthWaveB);
    }

    static byte[] buildB0SingleChannel(boolean channelA,
                                        int seqNo, int strengthMode, int strength,
                                        int[] freq, int[] strengthWave) {
        if (channelA) {
            return buildB0(seqNo,
                    strengthMode, DGLabConstants.MODE_UNCHANGED,
                    strength, 0,
                    freq, strengthWave,
                    new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 101});
        } else {
            return buildB0(seqNo,
                    DGLabConstants.MODE_UNCHANGED, strengthMode,
                    0, strength,
                    new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 101},
                    freq, strengthWave);
        }
    }

    // ===== BF 指令 =====

    static byte[] buildBF(int softLimitA, int softLimitB,
                           int balance1A, int balance1B,
                           int balance2A, int balance2B) {
        return new byte[]{
                DGLabConstants.HEAD_BF,
                (byte) softLimitA,
                (byte) softLimitB,
                (byte) balance1A,
                (byte) balance1B,
                (byte) balance2A,
                (byte) balance2B
        };
    }

    static byte[] buildDefaultBF() {
        return buildBF(
                DGLabConstants.BF_DEFAULT_SOFT_LIMIT, DGLabConstants.BF_DEFAULT_SOFT_LIMIT,
                DGLabConstants.BF_DEFAULT_BALANCE, DGLabConstants.BF_DEFAULT_BALANCE,
                DGLabConstants.BF_DEFAULT_BALANCE, DGLabConstants.BF_DEFAULT_BALANCE
        );
    }

    // ===== B1 消息解析 =====

    static DGLabB1Message parseB1(byte[] data) {
        if (data == null || data.length < DGLabConstants.B1_LENGTH) return null;
        if ((data[0] & 0xFF) != (DGLabConstants.HEAD_B1 & 0xFF)) return null;
        int seqNo = data[1] & 0xFF;
        int strengthA = data[2] & 0xFF;
        int strengthB = data[3] & 0xFF;
        return new DGLabB1Message(seqNo, strengthA, strengthB);
    }

    // ===== 安全停止 =====

    static byte[] buildEmergencyStop() {
        return buildB0(1,
                DGLabConstants.MODE_ABSOLUTE, DGLabConstants.MODE_ABSOLUTE,
                0, 0,
                new int[]{10, 10, 10, 10}, new int[]{0, 0, 0, 0},
                new int[]{10, 10, 10, 10}, new int[]{0, 0, 0, 0}
        );
    }

    // ===== 100ms 窗口拆分为 4 个 25ms 槽位 =====

    static void splitToSlots(DGLabWaveFrame[] frames, long offsetMs,
                             int[] outFreq, int[] outStrength) {
        if (frames == null || frames.length == 0) {
            fillSilent(outFreq, outStrength);
            return;
        }
        long slotDur = DGLabConstants.OUTPUT_WINDOW_MS / DGLabConstants.SLOTS_PER_WINDOW;
        for (int i = 0; i < DGLabConstants.SLOTS_PER_WINDOW; i++) {
            long sampleTimeMs = offsetMs + i * slotDur;
            DGLabWaveFrame frame = findNearestFrame(frames, sampleTimeMs);
            if (frame != null) {
                outFreq[i] = DGLabFrequencyConverter.toProtocol(frame.frequency);
                outStrength[i] = clampWaveStrength(frame.strength);
            } else {
                outFreq[i] = DGLabConstants.WAVE_FREQ_MIN;
                outStrength[i] = 0;
            }
        }
    }

    private static DGLabWaveFrame findNearestFrame(DGLabWaveFrame[] frames, long timeMs) {
        DGLabWaveFrame nearest = frames[0];
        long minDiff = Math.abs(frames[0].timeMs - timeMs);
        for (DGLabWaveFrame f : frames) {
            long diff = Math.abs(f.timeMs - timeMs);
            if (diff < minDiff) {
                minDiff = diff;
                nearest = f;
            }
        }
        return nearest;
    }

    static void fillSilent(int[] outFreq, int[] outStrength) {
        for (int i = 0; i < DGLabConstants.SLOTS_PER_WINDOW; i++) {
            outFreq[i] = DGLabConstants.WAVE_FREQ_MIN;
            outStrength[i] = 0;
        }
    }

    // ===== 范围钳制 =====

    private static int clampStrength(int value) {
        return Math.max(DGLabConstants.STRENGTH_V3_MIN,
                Math.min(DGLabConstants.STRENGTH_V3_MAX, value));
    }

    private static int clampFreq(int value) {
        return Math.max(DGLabConstants.WAVE_FREQ_MIN,
                Math.min(DGLabConstants.WAVE_FREQ_MAX, value));
    }

    private static int clampWaveStrength(int value) {
        return Math.max(0, Math.min(DGLabConstants.WAVE_STRENGTH_MAX, value));
    }
}
