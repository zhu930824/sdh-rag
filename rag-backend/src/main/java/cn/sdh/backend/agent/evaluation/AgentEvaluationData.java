package cn.sdh.backend.agent.evaluation;

import java.time.LocalDateTime;
import java.util.List;

/** Immutable import and archive contracts. No provider credentials cross this boundary. */
public final class AgentEvaluationData {
    private AgentEvaluationData() {}
    public enum Risk { LOW, MEDIUM, HIGH }
    public record Scenario(String id, String task, String initialContext, List<String> allowedTools,
                           List<String> forbiddenTools, String expectedOutcome, List<String> criticalSteps, Risk risk) {}
    public record ImportRequest(String name, String parentId, List<Scenario> scenarios) {}
    public record Dataset(String id, String name, int version, String parentId, String sha256,
                          List<Scenario> scenarios, LocalDateTime createdAt) {}
    public record DatasetSummary(String id, String name, int version, String parentId, String sha256,
                                 int scenarioCount, LocalDateTime createdAt) {}
    public record RecordRequest(String datasetId, String scenarioId, String runKey) {}
    public record EvaluationRecord(String id, String datasetId, String scenarioId, String runKey,
                                   String status, String datasetSha256, String snapshotSha256,
                                   String scenarioJson, String runSnapshotJson, LocalDateTime createdAt) {}
    public record RecordSummary(String id, String datasetId, String scenarioId, String runKey,
                                String status, LocalDateTime createdAt) {}
}
