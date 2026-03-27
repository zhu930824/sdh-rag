package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.DocumentComparison;
import cn.sdh.backend.service.DocumentComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/document-comparison")
@RequiredArgsConstructor
public class DocumentComparisonController {

    private final DocumentComparisonService documentComparisonService;

    @PostMapping("/compare")
    public Result<DocumentComparison> compare(@RequestBody Map<String, Object> request) {
        Long docId1 = ((Number) request.get("documentId1")).longValue();
        Long docId2 = ((Number) request.get("documentId2")).longValue();
        String comparisonType = (String) request.getOrDefault("comparisonType", "text");

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        try {
            DocumentComparison comparison = documentComparisonService.compareDocuments(
                docId1, docId2, userId, comparisonType);
            return Result.success(comparison);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<DocumentComparison> getById(@PathVariable Long id) {
        DocumentComparison comparison = documentComparisonService.getById(id);
        if (comparison == null) {
            return Result.notFound("对比记录不存在");
        }
        return Result.success(comparison);
    }

    @GetMapping("/document/{documentId}")
    public Result<List<DocumentComparison>> getByDocumentId(@PathVariable Long documentId) {
        return Result.success(documentComparisonService.getByDocumentId(documentId));
    }

    @GetMapping("/similarity")
    public Result<Map<String, Object>> calculateSimilarity(@RequestParam Long docId1, @RequestParam Long docId2) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        try {
            DocumentComparison comparison = documentComparisonService.compareDocuments(
                docId1, docId2, userId, "text");
            
            Map<String, Object> result = Map.of(
                "similarityScore", comparison.getSimilarityScore(),
                "summary", comparison.getSummary()
            );
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/text-diff")
    public Result<Map<String, Object>> getTextDiff(@RequestBody Map<String, String> request) {
        String text1 = request.get("text1");
        String text2 = request.get("text2");

        Map<String, Object> diff = documentComparisonService.getTextDiff(text1, text2);
        return Result.success(diff);
    }
}
