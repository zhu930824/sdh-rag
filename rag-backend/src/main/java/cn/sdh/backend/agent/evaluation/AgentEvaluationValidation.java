package cn.sdh.backend.agent.evaluation;

import cn.sdh.backend.common.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static cn.sdh.backend.agent.evaluation.AgentEvaluationData.*;

final class AgentEvaluationValidation {
    private AgentEvaluationValidation() {}
    static Long actor(Long actor) {
        if (actor == null || actor <= 0) throw new BusinessException(401, "请先登录");
        return actor;
    }
    static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) throw new BusinessException(400, field + "不能为空且不能超过 " + max + " 字符");
        return value.trim();
    }
    static String key(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new BusinessException(400, "场景 ID 必须为小写字母、数字、下划线或短横线，长度 1—64");
        return value;
    }
    static List<Scenario> scenarios(List<Scenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty() || scenarios.size() > 100) throw new BusinessException(400, "每个版本需包含 1—100 条场景");
        Set<String> ids = new HashSet<>();
        List<Scenario> result = new ArrayList<>();
        for (Scenario s : scenarios) {
            if (s == null || !ids.add(key(s.id()))) throw new BusinessException(400, "场景 ID 重复或场景为空");
            var allowed = strings(s.allowedTools(), 100, 100);
            var forbidden = strings(s.forbiddenTools(), 100, 100);
            if (!Collections.disjoint(allowed, forbidden)) throw new BusinessException(400, "允许工具与禁止工具不能重叠");
            if (s.risk() == null) throw new BusinessException(400, "必须指定风险等级");
            String initial = s.initialContext() == null ? null : text(s.initialContext(), 20000, "初始上下文");
            result.add(new Scenario(s.id(), text(s.task(), 10000, "任务"), initial, allowed, forbidden,
                    text(s.expectedOutcome(), 10000, "期望结果"), strings(s.criticalSteps(), 20, 1000), s.risk()));
        }
        boundedJson(result, 1024 * 1024);
        return List.copyOf(result);
    }
    private static List<String> strings(List<String> values, int count, int length) {
        if (values == null) return List.of();
        if (values.size() > count) throw new BusinessException(400, "列表项过多");
        var result = values.stream().map(s -> text(s, length, "列表项")).toList();
        if (new HashSet<>(result).size() != result.size()) throw new BusinessException(400, "列表项重复");
        return result;
    }
    static String boundedJson(Object value, int bytes) {
        String json = JSON.toJSONString(value);
        if (json.getBytes(StandardCharsets.UTF_8).length > bytes) throw new BusinessException(400, "快照超过大小上限");
        return json;
    }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
