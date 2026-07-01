package com.example.nirjon.bledemo4_advertising.util;

/**
 * BLE RF 编码 JNI 包装。
 * <p>
 * 加载 libble.so（从 Love Spouse 官方 APK 提取），提供 BLE 广播数据的编码功能。
 * 包名必须与原始 APK 完全一致，否则 JNI 无法关联。
 */
public class BLEUtil {
    static {
        System.loadLibrary("ble");
    }

    /**
     * 对 BLE 广播数据进行 RF 编码。
     *
     * @param prefix    前缀字节数组（如 "wbMSE" → {0x77,0x62,0x4D,0x53,0x45}）
     * @param prefixLen 前缀长度
     * @param payload   负载字节数组（如强度 "15" → {0x15}）
     * @param payloadLen 负载长度
     * @param output    输出缓冲区（长度 = prefixLen + payloadLen + 5）
     */
    public static native void get_rf_payload(
        byte[] prefix, int prefixLen,
        byte[] payload, int payloadLen,
        byte[] output
    );
}
