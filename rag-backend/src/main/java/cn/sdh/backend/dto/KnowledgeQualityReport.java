package cn.sdh.backend.dto;

import java.util.List;

public record KnowledgeQualityReport(
        Long knowledgeId,
        int completedTasks,
        int totalQuestions,
        int chunkMisses,
        int documentMisses,
        int negativeFalsePositives,
        int openIssues,
        List<String> recommendations,
        List<QualityIssue> issues) {

    public record QualityIssue(
            Long qaId,
            Long taskId,
            String question,
            String expectedAnswer,
            Long sourceDocumentId,
            String reason,
            String severity,
            String status,
            String note) {}
}
