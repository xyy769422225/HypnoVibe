package com.hypno.hypnovibe.infrastructure.ble.adapter.dglab;

/**
 * DG-LAB V3 波形频率换算。
 * 用户输入值（10-1000ms）转换为协议值（10-240）。
 * package-private，仅 dglab 子包内部使用。
 */
final class DGLabFrequencyConverter {

    private DGLabFrequencyConverter() {}

    /**
     * @param inputMs 用户输入的波形周期（毫秒），范围 10-1000
     * @return 协议频率值 10-240
     */
    static int toProtocol(int inputMs) {
        if (inputMs <= 0) return 10;
        if (inputMs >= 10 && inputMs <= 100) return inputMs;
        if (inputMs <= 600) return (inputMs - 100) / 5 + 100;
        if (inputMs <= 1000) return (inputMs - 600) / 10 + 200;
        return 240;
    }
}
