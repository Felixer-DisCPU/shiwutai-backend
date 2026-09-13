package com.shiwutai.backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从前端动态信封（Map）中安全地取出字段。前端信封结构：{ action, openid, ...params }
 */
public final class Req {

    private Req() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> body(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    public static String str(Map<String, Object> m, String key) {
        Object v = (m == null) ? null : m.get(key);
        return v == null ? null : v.toString();
    }

    public static Long toLong(Map<String, Object> m, String key) {
        Object v = (m == null) ? null : m.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Boolean bool(Map<String, Object> m, String key) {
        Object v = (m == null) ? null : m.get(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(v.toString()) || "1".equals(v.toString());
    }

    @SuppressWarnings("unchecked")
    public static List<String> strList(Map<String, Object> m, String key) {
        Object v = (m == null) ? null : m.get(key);
        if (v == null) return new ArrayList<>();
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object o : (List<?>) v) if (o != null) out.add(o.toString());
            return out;
        }
        if (v instanceof String && !((String) v).isBlank()) {
            List<String> out = new ArrayList<>();
            out.add((String) v);
            return out;
        }
        return new ArrayList<>();
    }
}
