package cn.sdh.backend.agent;

import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

public final class ToolInvocationKey {
    private ToolInvocationKey() {}
    public static String create(String runKey, String toolCode, Map<String, Object> arguments) {
        String canonical = toolCode + "::" + JSON.toJSONString(canonicalize(arguments));
        try {
            return runKey + "::" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("工具参数键必须为文本");
                sorted.put(key, canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(ToolInvocationKey::canonicalize).toList();
        return value;
    }
}
