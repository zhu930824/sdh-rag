package cn.sdh.backend.agent.evaluation;

import cn.sdh.backend.entity.*;
import java.time.LocalDateTime;
import java.util.*;

/** Observed terminal evidence, not a reconstruction of missing execution-time configuration. */
final class AgentEvaluationSnapshot {
    private AgentEvaluationSnapshot() {}
    static String capture(AgentRun run, ChatHistory history, List<AgentStep> steps, LocalDateTime time) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1); snapshot.put("capturedAt", time.toString());
        snapshot.put("provenance", "TERMINAL_RUN_ARCHIVE");
        snapshot.put("runKey", run.getRunKey()); snapshot.put("status", run.getStatus());
        snapshot.put("engine", run.getEngine()); snapshot.put("engineVersion", run.getEngineVersion());
        snapshot.put("modelId", run.getModelId()); snapshot.put("knowledgeId", run.getKnowledgeId());
        snapshot.put("memoryEnabled", run.getMemoryEnabled()); snapshot.put("maxSteps", run.getMaxSteps());
        snapshot.put("modelConfigurationVersion", "UNKNOWN"); snapshot.put("promptVersion", "UNKNOWN");
        snapshot.put("knowledgePublicationVersion", "UNKNOWN"); snapshot.put("toolDefinitionVersion", "UNKNOWN");
        snapshot.put("question", cap(run.getQuestion(), 20000)); snapshot.put("answer", cap(history == null ? null : history.getAnswer(), 64000));
        snapshot.put("evidencePreview", cap(run.getEvidenceText(), 12000)); snapshot.put("errorPreview", cap(run.getErrorMessage(), 4000));
        snapshot.put("stepCount", run.getStepCount()); snapshot.put("promptTokens", run.getPromptTokens());
        snapshot.put("completionTokens", run.getCompletionTokens()); snapshot.put("totalTokens", run.getTotalTokens());
        snapshot.put("createdAt", run.getCreatedAt()); snapshot.put("completedAt", run.getCompletedAt());
        snapshot.put("recordedStepCount", steps.size());
        List<Map<String, Object>> previews = steps.stream().limit(50).map(step -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", step.getSeq()); item.put("type", step.getStepType()); item.put("status", step.getStatus());
            item.put("toolCode", step.getToolCode()); item.put("actionId", step.getActionId());
            item.put("latencyMs", step.getLatencyMs()); item.put("tokens", step.getTokens());
            item.put("inputPreview", cap(step.getInputJson(), 2048)); item.put("outputPreview", cap(step.getOutputJson(), 6144));
            item.put("truncated", longer(step.getInputJson(), 2048) || longer(step.getOutputJson(), 6144));
            return item;
        }).toList();
        snapshot.put("steps", previews);
        snapshot.put("truncated", steps.size() > 50 || previews.stream().anyMatch(p -> Boolean.TRUE.equals(p.get("truncated")))
                || longer(run.getQuestion(), 20000) || longer(history == null ? null : history.getAnswer(), 64000)
                || longer(run.getEvidenceText(), 12000) || longer(run.getErrorMessage(), 4000));
        return AgentEvaluationValidation.boundedJson(snapshot, 4 * 1024 * 1024);
    }
    private static boolean longer(String value, int limit) { return value != null && value.length() > limit; }
    private static String cap(String value, int limit) { return longer(value, limit) ? value.substring(0, limit) : value; }
}
