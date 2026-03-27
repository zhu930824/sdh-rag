package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hybrid-search")
@RequiredArgsConstructor
public class HybridSearchController {

    private final HybridSearchService hybridSearchService;

    @PostMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestBody Map<String, Object> request) {
        Long knowledgeId = request.get("knowledgeId") != null ? 
            ((Number) request.get("knowledgeId")).longValue() : null;
        String query = (String) request.get("query");
        int topK = request.get("topK") != null ? (Integer) request.get("topK") : 10;
        double keywordWeight = request.get("keywordWeight") != null ? 
            ((Number) request.get("keywordWeight")).doubleValue() : 0.3;
        double semanticWeight = request.get("semanticWeight") != null ? 
            ((Number) request.get("semanticWeight")).doubleValue() : 0.7;

        return Result.success(hybridSearchService.hybridSearch(knowledgeId, query, topK, keywordWeight, semanticWeight));
    }

    @GetMapping("/keyword/{knowledgeId}")
    public Result<?> keywordSearch(
            @PathVariable Long knowledgeId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {
        return Result.success(hybridSearchService.keywordSearch(knowledgeId, query, topK));
    }

    @GetMapping("/semantic/{knowledgeId}")
    public Result<?> semanticSearch(
            @PathVariable Long knowledgeId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {
        return Result.success(hybridSearchService.semanticSearch(knowledgeId, query, topK));
    }

    @PostMapping("/filtered-search")
    public Result<Map<String, Object>> filteredSearch(@RequestBody Map<String, Object> request) {
        Long knowledgeId = ((Number) request.get("knowledgeId")).longValue();
        String query = (String) request.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> filters = (Map<String, Object>) request.get("filters");
        int page = request.get("page") != null ? (Integer) request.get("page") : 1;
        int pageSize = request.get("pageSize") != null ? (Integer) request.get("pageSize") : 10;

        return Result.success(hybridSearchService.searchWithFilters(knowledgeId, query, filters, page, pageSize));
    }

    @PostMapping("/build-index/{knowledgeId}")
    public Result<Void> buildIndex(@PathVariable Long knowledgeId) {
        hybridSearchService.buildKeywordIndex(knowledgeId);
        return Result.success();
    }

    @PutMapping("/update-index/{documentId}")
    public Result<Void> updateIndex(@PathVariable Long documentId) {
        hybridSearchService.updateKeywordIndex(documentId);
        return Result.success();
    }
}
