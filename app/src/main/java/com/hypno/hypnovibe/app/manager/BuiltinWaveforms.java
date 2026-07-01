package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.pulse.PulseData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置官方波形（呼吸、潮汐等），基于官方 V3 example 的 .pulse 格式。
 */
public class BuiltinWaveforms {

    /** DG-LAB 官方呼吸波形 (V3) */
    public static final String RESPIRATION = "Dungeonlab+pulse:0,0,8=0,0,0,3,1/0.00-1,100.00-1,100.00-1,100.00-1,100.00-1,100.00-1,100.00-1,0.00-1,0.00-1,0.00-1,0.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1";

    /** DG-LAB 官方潮汐波形 (V3) - 频率+强度同时渐变 */
    public static final String TIDE = "Dungeonlab+pulse:0,0,8=10,42,0,3,1/0.00-1,16.00-0,33.00-0,50.00-0,66.00-0,83.00-0,100.00-0,92.00-0,84.00-0,76.00-0,68.00-0,0.00-1,16.00-0,33.00-0,50.00-0,66.00-0,83.00-0,100.00-0,92.00-0,84.00-0,76.00-0,68.00-0,0.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1+section+0,20,20,1,0/0.00-1,20.00-1";

    /** 返回所有内置波形（名称 → JSON 文本） */
    public static Map<String, String> getAllBuiltin() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("呼吸 (V3, 官方内置)", RESPIRATION);
        map.put("潮汐 (V3, 官方内置)", TIDE);
        return map;
    }

    /** 解析所有内置波形 */
    public static PulseData[] parseAllBuiltin() {
        Map<String, String> all = getAllBuiltin();
        PulseData[] result = new PulseData[all.size()];
        int i = 0;
        for (Map.Entry<String, String> e : all.entrySet()) {
            PulseData pd = PulseFileParser.parse(e.getValue(), e.getKey());
            if (pd != null) {
                pd.name = e.getKey();
                result[i++] = pd;
            }
        }
        return result;
    }
}
