package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

/**
 * 郊狼 V2 协议编解码器。
 * <p>
 * 纯静态方法，零状态。负责 V2 所有蓝牙指令的字节级编解码。
 * 注意 V2 需要大小端转换，且 PWM_A34 写入B通道波形、PWM_B34 写入A通道波形（命名交叉）。
 * package-private，仅郊狼子包内部使用。
 *
 * <h3>特性参考</h3>
 * <pre>
 * PWM_AB2 (3 bytes): A/B通道强度 (0-2047)
 *   bits 23-22: 保留
 *   bits 21-11: A通道实际强度
 *   bits 10-0:  B通道实际强度
 *
 * PWM_A34 / PWM_B34 (3 bytes): 波形数据
 *   bits 23-20: 保留
 *   bits 19-15: Z值 (5 bits, 0-31) — 脉冲宽度, 实际宽度 = Z × 5μs
 *   bits 14-5:  Y值 (10 bits, 0-1023) — 脉冲间隔
 *   bits 4-0:   X值 (5 bits, 0-31) — 连续脉冲数
 * </pre>
 */
final class CoyoteV2Protocol {

    private CoyoteV2Protocol() {}

    // ===== PWM_AB2: 通道强度 =====

    /**
     * 构造 PWM_AB2 通道强度指令（3 bytes）。
     *
     * <pre>
     * 24-bit 布局 (big-endian, 需要大小端转换):
     *   bit 23-22: 保留 (0)
     *   bit 21-11: A通道强度 (0-2047)
     *   bit 10-0:  B通道强度 (0-2047)
     * </pre>
     */
    static byte[] buildPwmAB2(int strengthA, int strengthB) {
        int sa = clampV2Strength(strengthA);
        int sb = clampV2Strength(strengthB);

        // 构造 24-bit value: (A<<11) | B
        int value = (sa << 11) | sb;

        // V2 需要大小端转换: 大端 → 小端写入
        byte[] cmd = new byte[CoyoteConstants.V2_PWM_LENGTH];
        cmd[0] = (byte) (value & 0xFF);         // 低字节
        cmd[1] = (byte) ((value >> 8) & 0xFF);  // 中字节
        cmd[2] = (byte) ((value >> 16) & 0xFF); // 高字节
        return cmd;
    }

    /**
     * 构造 PWM_A34 波形指令（3 bytes）。
     * <p>
     * 注意命名交叉：PWM_A34 实际写入的是 <b>B 通道</b>的波形数据。
     */
    static byte[] buildPwmA34(int x, int y, int z) {
        return buildPwm34(x, y, z);
    }

    /**
     * 构造 PWM_B34 波形指令（3 bytes）。
     * <p>
     * 注意命名交叉：PWM_B34 实际写入的是 <b>A 通道</b>的波形数据。
     */
    static byte[] buildPwmB34(int x, int y, int z) {
        return buildPwm34(x, y, z);
    }

    /**
     * 内部: 构造 PWM_34 波形数据（3 bytes）。
     *
     * <pre>
     * 24-bit 布局 (big-endian, 需要大小端转换):
     *   bit 23-20: 保留 (0)
     *   bit 19-15: Z值 (5 bits, 0-31)
     *   bit 14-5:  Y值 (10 bits, 0-1023)
     *   bit 4-0:   X值 (5 bits, 0-31)
     * </pre>
     */
    private static byte[] buildPwm34(int x, int y, int z) {
        int xi = clampX(x);
        int yi = clampY(y);
        int zi = clampZ(z);

        // 构造 24-bit value: (Z<<15) | (Y<<5) | X
        int value = (zi << 15) | (yi << 5) | xi;

        // V2 需要大小端转换: 大端 → 小端写入
        byte[] cmd = new byte[CoyoteConstants.V2_PWM_LENGTH];
        cmd[0] = (byte) (value & 0xFF);
        cmd[1] = (byte) ((value >> 8) & 0xFF);
        cmd[2] = (byte) ((value >> 16) & 0xFF);
        return cmd;
    }

    // ===== V2 → V3 转换公式 =====

    /**
     * V2 波形频率(X+Y) 转换为 V3 协议频率。
     * V3频率 = convertFrequency(X + Y)
     */
    static int convertV2FreqToV3(int x, int y) {
        int userFreq = clampX(x) + clampY(y);
        return CoyoteFrequencyConverter.toProtocol(userFreq);
    }

    /**
     * V2 波形强度(Z) 转换为 V3 波形强度。
     * V3强度 = Z * 5
     */
    static int convertV2StrengthToV3(int z) {
        return clampZ(z) * 5;
    }

    /**
     * V2 X/Y/Z 参数转换为 CoyoteWaveFrame。
     * 频率 = X+Y (ms), 强度 = Z (映射到 0-100 范围, Z*5)
     */
    static CoyoteWaveFrame toWaveFrame(int timeMs, int x, int y, int z) {
        int frequency = clampX(x) + clampY(y);
        int strength = clampZ(z) * 5;
        return new CoyoteWaveFrame(timeMs, frequency, strength);
    }

    // ===== 范围钳制 =====

    private static int clampV2Strength(int value) {
        return Math.max(CoyoteConstants.STRENGTH_V2_MIN,
                Math.min(CoyoteConstants.STRENGTH_V2_MAX, value));
    }

    private static int clampX(int value) {
        return Math.max(0, Math.min(CoyoteConstants.V2_X_MAX, value));
    }

    private static int clampY(int value) {
        return Math.max(0, Math.min(CoyoteConstants.V2_Y_MAX, value));
    }

    private static int clampZ(int value) {
        return Math.max(0, Math.min(CoyoteConstants.V2_Z_MAX, value));
    }
}
