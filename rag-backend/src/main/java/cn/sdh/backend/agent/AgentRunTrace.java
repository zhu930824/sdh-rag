package cn.sdh.backend.agent;

import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.AgentStep;
import com.alibaba.fastjson2.JSON;
import java.util.*;

/** Preserve complete JSON and complete steps when limiting the persisted replay summary. */
public final class AgentRunTrace {
    private static final int CAP = 50 * 1024;
    private AgentRunTrace() {}
    public static String encode(AgentRun run, List<AgentStep> steps) {
        List<Map<String, Object>> trace = new ArrayList<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", run.getRunKey()); summary.put("status", run.getStatus()); summary.put("steps", trace);
        String result = JSON.toJSONString(summary);
        for (AgentStep step : steps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", step.getStepType()); item.put("toolCode", step.getToolCode()); item.put("actionId", step.getActionId());
            item.put("status", step.getStatus()); item.put("seq", step.getSeq());
            if ("tool".equals(step.getStepType())) {
                item.put("argsPreview", preview(step.getInputJson(), 2 * 1024));
                item.put("resultPreview", preview(step.getOutputJson(), 6 * 1024));
            }
            trace.add(item);
            String candidate = JSON.toJSONString(summary);
            if (candidate.length() > CAP) { trace.removeLast(); break; }
            result = candidate;
            if (trace.size() == 50) break;
        }
        return result;
    }
    private static String preview(String value, int cap) {
        return value == null || value.isBlank() ? null : value.substring(0, Math.min(cap, value.length()));
    }
}
