package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

/**
 * DG-LAB V2 协议编解码器。
 * <p>
 * 纯静态方法，零状态。负责 V2 所有蓝牙指令的字节级编解码。
 * 注意 V2 需要大小端转换，且 PWM_A34 写入B通道波形、PWM_B34 写入A通道波形（命名交叉）。
 * package-private，仅 dglab 子包内部使用。
 */
final class DGLabV2Protocol {

    private DGLabV2Protocol() {}

    // ===== PWM_AB2: 通道强度 =====

    /**
     * 构造 PWM_AB2 通道强度指令（3 bytes）。
     */
    static byte[] buildPwmAB2(int strengthA, int strengthB) {
        int sa = clampV2Strength(strengthA);
        int sb = clampV2Strength(strengthB);
        int value = (sa << 11) | sb;
        byte[] cmd = new byte[DGLabConstants.V2_PWM_LENGTH];
        cmd[0] = (byte) (value & 0xFF);
        cmd[1] = (byte) ((value >> 8) & 0xFF);
        cmd[2] = (byte) ((value >> 16) & 0xFF);
        return cmd;
    }

    /** 构造 PWM_A34 波形指令（3 bytes）。注意命名交叉：实际写入 B 通道。 */
    static byte[] buildPwmA34(int x, int y, int z) {
        return buildPwm34(x, y, z);
    }

    /** 构造 PWM_B34 波形指令（3 bytes）。注意命名交叉：实际写入 A 通道。 */
    static byte[] buildPwmB34(int x, int y, int z) {
        return buildPwm34(x, y, z);
    }

    private static byte[] buildPwm34(int x, int y, int z) {
        int xi = clampX(x);
        int yi = clampY(y);
        int zi = clampZ(z);
        int value = (zi << 15) | (yi << 5) | xi;
        byte[] cmd = new byte[DGLabConstants.V2_PWM_LENGTH];
        cmd[0] = (byte) (value & 0xFF);
        cmd[1] = (byte) ((value >> 8) & 0xFF);
        cmd[2] = (byte) ((value >> 16) & 0xFF);
        return cmd;
    }

    // ===== V2 → V3 转换公式 =====

    static int convertV2FreqToV3(int x, int y) {
        int userFreq = clampX(x) + clampY(y);
        return DGLabFrequencyConverter.toProtocol(userFreq);
    }

    static int convertV2StrengthToV3(int z) {
        return clampZ(z) * 5;
    }

    static DGLabWaveFrame toWaveFrame(int timeMs, int x, int y, int z) {
        int frequency = clampX(x) + clampY(y);
        int strength = clampZ(z) * 5;
        return new DGLabWaveFrame(timeMs, frequency, strength);
    }

    // ===== 范围钳制 =====

    private static int clampV2Strength(int value) {
        return Math.max(DGLabConstants.STRENGTH_V2_MIN,
                Math.min(DGLabConstants.STRENGTH_V2_MAX, value));
    }

    private static int clampX(int value) {
        return Math.max(0, Math.min(DGLabConstants.V2_X_MAX, value));
    }

    private static int clampY(int value) {
        return Math.max(0, Math.min(DGLabConstants.V2_Y_MAX, value));
    }

    private static int clampZ(int value) {
        return Math.max(0, Math.min(DGLabConstants.V2_Z_MAX, value));
    }
}
