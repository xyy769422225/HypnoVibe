package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.pulse.PulseData;
import com.hypno.hypnovibe.domain.pulse.PulsePoint;
import com.hypno.hypnovibe.domain.pulse.PulseSection;

import java.util.ArrayList;
import java.util.List;

/**
 * .pulse 文件解析器。
 * 完整实现官方 APP analysisPulse0() 的解析逻辑。
 */
public class PulseFileParser {

    private static final String PREFIX = "Dungeonlab+pulse:";

    /**
     * 解析 .pulse 文件内容。
     * @param content 文件完整文本内容
     * @param name 波形名称（用于展示）
     * @return 解析后的 PulseData，解析失败返回 null
     */
    public static PulseData parse(String content, String name) {
        if (content == null || !content.startsWith(PREFIX)) return null;

        PulseData data = new PulseData();
        data.name = name;
        data.L = 0;
        data.playRate = 1;
        data.ZY = 8;

        // 去掉前缀
        String body = content.substring(PREFIX.length());
        // 按 '=' 分割头部和段列表
        int eqIdx = body.indexOf('=');
        String header;
        String sectionsPart;

        if (eqIdx < 0) {
            // 没有 = ，整体就是 sections
            header = "";
            sectionsPart = body;
        } else {
            header = body.substring(0, eqIdx);
            sectionsPart = body.substring(eqIdx + 1);
        }

        // 解析头部 "L,playRate,ZY"
        if (!header.isEmpty()) {
            String[] headerParts = header.split(",");
            if (headerParts.length >= 1) data.L = parseIntSafe(headerParts[0], 0, 100, 0);
            if (headerParts.length >= 2) {
                int pr = parseIntSafe(headerParts[1], 1, 4, 1);
                data.playRate = pr == 3 ? 1 : pr;  // 3 被视为 1
            }
            if (headerParts.length >= 3) data.ZY = parseIntSafe(headerParts[2], 1, 16, 8);
        }

        // 按 "+section+" 分割各段
        String[] sectionStrs = sectionsPart.split("\\+section\\+");
        if (sectionStrs.length == 0 || (sectionStrs.length == 1 && sectionStrs[0].isEmpty())) {
            return null;
        }
        // 过滤空段
        List<String> validSections = new ArrayList<>();
        for (String s : sectionStrs) {
            if (s != null && !s.isEmpty()) validSections.add(s);
        }
        if (validSections.isEmpty() || validSections.size() > 10) return null;

        List<PulseSection> sections = new ArrayList<>();
        for (String secStr : validSections) {
            PulseSection section = parseSection(secStr);
            if (section == null) continue;
            sections.add(section);
        }
        if (sections.isEmpty()) return null;

        data.sections = sections.toArray(new PulseSection[0]);

        // 如果没有 JIE=1 的段，把第一段标记为节开始
        boolean hasJie = false;
        for (PulseSection s : data.sections) {
            if (s.JIE == 1) { hasJie = true; break; }
        }
        if (!hasJie) data.sections[0].JIE = 1;

        return data;
    }

    private static PulseSection parseSection(String secStr) {
        // 按 '/' 分割参数和点
        int slashIdx = secStr.indexOf('/');
        if (slashIdx < 0) return null;
        String paramsStr = secStr.substring(0, slashIdx);
        String pointsStr = secStr.substring(slashIdx + 1);

        String[] params = paramsStr.split(",");
        if (params.length < 5) return null;

        PulseSection section = new PulseSection();
        // A,B,J,PC,JIE
        section.A = parseIntSafe(params[0], 0, 83, 0);
        section.B = parseIntSafe(params[1], 0, 83, 0);
        section.J = parseIntSafe(params[2], 0, 99, 0);
        section.PC = parseIntSafe(params[3], 1, 4, 1);
        section.JIE = parseIntSafe(params[4], 0, 1, 0);

        // 解析波形点
        String[] pointStrs = pointsStr.split(",");
        if (pointStrs.length < 2 || pointStrs.length > 500) return null;

        PulsePoint[] points = new PulsePoint[pointStrs.length];
        for (int i = 0; i < pointStrs.length; i++) {
            String[] parts = pointStrs[i].split("-");
            if (parts.length < 2) return null;
            float y = parseFloatSafe(parts[0], -1f);
            int anchor = parseIntSafe(parts[1], -1, 1, -1);
            if (y < 0 || y > 100 || anchor < 0) return null;
            // 首尾点 anchor 必须为 1
            if ((i == 0 || i == pointStrs.length - 1) && anchor != 1) {
                anchor = 1;
            }
            points[i] = new PulsePoint(y, anchor);
        }
        section.points = points;
        return section;
    }

    private static int parseIntSafe(String s, int min, int max, int def) {
        try {
            int v = Integer.parseInt(s.trim());
            if (v < min || v > max) return def;
            return v;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float parseFloatSafe(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
