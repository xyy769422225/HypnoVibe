package com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Love Spouse 2.4G BLE 广播协议常量。
 */
public final class LoveSpouseConstants {

    private LoveSpouseConstants() {}

    public static final String DEVICE_TYPE = "love_spouse";
    static final int MANUFACTURER_ID = 0x00FF;
    static final int RF_PAYLOAD_OVERHEAD = 5;

    // ===== 命令类型 =====
    enum CommandType { STRENGTH, MODE, STOP }

    // ===== 强度命令 =====
    static final String[] STRENGTH_COMMANDS = {
        "00", "11", "12", "13", "14", "15", "16", "17", "18", "19"
    };
    static final int STRENGTH_MIN = 0;
    static final int STRENGTH_MAX = 9;

    // ===== 停止命令 =====
    public static final String STOP_ALL = "00";

    // ===== CateId → 模式配置 =====

    /**
     * 模式配置条目。
     */
    public static class ModeConfig {
        public final int start;
        public final int end;
        public final String stop;

        ModeConfig(int start, int end, String stop) {
            this.start = start;
            this.end = end;
            this.stop = stop;
        }
    }

    /** CateId → 模式配置（来源: NormalMode.H()） */
    private static final Map<Integer, ModeConfig> MODE_CONFIGS = new LinkedHashMap<>();
    static {
        MODE_CONFIGS.put(1,  new ModeConfig(1,  9,  STOP_ALL));
        MODE_CONFIGS.put(2,  new ModeConfig(31, 39, "30"));
        MODE_CONFIGS.put(3,  new ModeConfig(41, 49, "40"));
        MODE_CONFIGS.put(16, new ModeConfig(1,  6,  STOP_ALL));
        MODE_CONFIGS.put(21, new ModeConfig(31, 36, "30"));
        MODE_CONFIGS.put(23, new ModeConfig(31, 33, "30"));
    }

    /** 获取指定 CateId 的模式配置 */
    public static ModeConfig getModeConfig(int cateId) {
        return MODE_CONFIGS.getOrDefault(cateId, MODE_CONFIGS.get(1));
    }

    /** 获取所有已知 CateId（供 UI 选择器） */
    public static int[] getKnownCateIds() {
        return MODE_CONFIGS.keySet().stream().mapToInt(i -> i).toArray();
    }

    // ===== 广播设置 =====
    static final int ADVERTISE_MODE_POWER = 1;
    static final int ADVERTISE_MODE_STOP = 2;
}
