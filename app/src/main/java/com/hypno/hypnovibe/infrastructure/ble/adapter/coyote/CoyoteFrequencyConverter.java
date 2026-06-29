package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

/**
 * 郊狼 V3 波形频率换算器。
 * <p>
 * V3 协议将用户输入的波形频率（10-1000ms）压缩转换为协议值（10-240）。
 * package-private，仅郊狼子包内部使用。
 */
final class CoyoteFrequencyConverter {

    private CoyoteFrequencyConverter() {}

    /**
     * 将用户输入的波形频率（ms）转换为 V3 协议值（10-240）。
     *
     * <pre>
     * 换算规则：
     *   10..100   → 直接使用输入值
     *   101..600  → (输入值-100)/5 + 100
     *   601..1000 → (输入值-600)/10 + 200
     *   其他      → 返回 10（最小值）
     * </pre>
     *
     * @param userFreqMs 用户频率值(ms)，范围 10-1000
     * @return 协议值，范围 10-240
     */
    static int toProtocol(int userFreqMs) {
        if (userFreqMs >= 10 && userFreqMs <= 100) {
            return userFreqMs;
        } else if (userFreqMs <= 600) {
            return (userFreqMs - 100) / 5 + 100;
        } else if (userFreqMs <= 1000) {
            return (userFreqMs - 600) / 10 + 200;
        }
        return CoyoteConstants.WAVE_FREQ_MIN;
    }

    /**
     * 逆换算：将协议值（10-240）还原为近似用户值（ms）。
     * 用于从协议数据反向解析（调试/波形导入）。
     *
     * @param protocolValue 协议值，范围 10-240
     * @return 近似用户频率值(ms)
     */
    static int fromProtocol(int protocolValue) {
        if (protocolValue >= 10 && protocolValue <= 100) {
            return protocolValue;
        } else if (protocolValue <= 200) {
            return (protocolValue - 100) * 5 + 100;
        } else if (protocolValue <= 240) {
            return (protocolValue - 200) * 10 + 600;
        }
        return CoyoteConstants.USER_FREQ_MIN;
    }
}
