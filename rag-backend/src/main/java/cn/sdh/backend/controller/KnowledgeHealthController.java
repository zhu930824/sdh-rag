package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.KnowledgeHealth;
import cn.sdh.backend.service.KnowledgeHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-health")
@RequiredArgsConstructor
public class KnowledgeHealthController {

    private final KnowledgeHealthService healthService;

    @GetMapping("/history/{knowledgeId}")
    public Result<List<KnowledgeHealth>> getHistory(@PathVariable Long knowledgeId) {
        return Result.success(healthService.getHealthHistory(knowledgeId));
    }

    @PostMapping("/check/{knowledgeId}")
    public Result<Map<String, Object>> runCheck(@PathVariable Long knowledgeId) {
        return Result.success(healthService.runFullHealthCheck(knowledgeId));
    }

    @GetMapping("/check/{knowledgeId}/document-count")
    public Result<KnowledgeHealth> checkDocumentCount(@PathVariable Long knowledgeId) {
        return Result.success(healthService.checkDocumentCount(knowledgeId));
    }

    @GetMapping("/check/{knowledgeId}/embedding-quality")
    public Result<KnowledgeHealth> checkEmbeddingQuality(@PathVariable Long knowledgeId) {
        return Result.success(healthService.checkEmbeddingQuality(knowledgeId));
    }

    @GetMapping("/check/{knowledgeId}/index-status")
    public Result<KnowledgeHealth> checkIndexStatus(@PathVariable Long knowledgeId) {
        return Result.success(healthService.checkIndexStatus(knowledgeId));
    }

    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> getSummary() {
        return Result.success(healthService.getHealthSummary());
    }

    @GetMapping("/score/{knowledgeId}")
    public Result<Map<String, Object>> getScore(@PathVariable Long knowledgeId) {
        double score = healthService.getOverallHealthScore(knowledgeId);
        return Result.success(Map.of(
            "knowledgeId", knowledgeId,
            "overallScore", score,
            "healthLevel", getHealthLevel(score)
        ));
    }

    private String getHealthLevel(double score) {
        if (score >= 90) return "优秀";
        if (score >= 75) return "良好";
        if (score >= 60) return "一般";
        if (score >= 40) return "较差";
        return "危险";
    }
}
