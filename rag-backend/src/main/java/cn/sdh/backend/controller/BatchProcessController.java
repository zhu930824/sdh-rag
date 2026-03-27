package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.service.BatchProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchProcessController {

    private final BatchProcessService batchProcessService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> batchUpload(@RequestBody Map<String, Object> request) {
        Long knowledgeId = ((Number) request.get("knowledgeId")).longValue();
        @SuppressWarnings("unchecked")
        List<String> fileUrls = (List<String>) request.get("fileUrls");
        String category = (String) request.get("category");

        return Result.success(batchProcessService.batchUpload(knowledgeId, fileUrls, category));
    }

    @PostMapping("/delete")
    public Result<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> documentIds = ((List<Number>) request.get("documentIds")).stream()
            .map(Number::longValue)
            .toList();

        return Result.success(batchProcessService.batchDelete(documentIds));
    }

    @PostMapping("/move")
    public Result<Map<String, Object>> batchMove(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> documentIds = ((List<Number>) request.get("documentIds")).stream()
            .map(Number::longValue)
            .toList();
        Long targetKnowledgeId = ((Number) request.get("targetKnowledgeId")).longValue();

        return Result.success(batchProcessService.batchMove(documentIds, targetKnowledgeId));
    }

    @PostMapping("/update-category")
    public Result<Map<String, Object>> batchUpdateCategory(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> documentIds = ((List<Number>) request.get("documentIds")).stream()
            .map(Number::longValue)
            .toList();
        Long categoryId = ((Number) request.get("categoryId")).longValue();

        return Result.success(batchProcessService.batchUpdateCategory(documentIds, categoryId));
    }

    @PostMapping("/process")
    public Result<Map<String, Object>> batchProcess(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> documentIds = ((List<Number>) request.get("documentIds")).stream()
            .map(Number::longValue)
            .toList();
        String processType = (String) request.get("processType");

        return Result.success(batchProcessService.batchProcess(documentIds, processType));
    }

    @PostMapping("/embed")
    public Result<Map<String, Object>> batchEmbed(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> documentIds = ((List<Number>) request.get("documentIds")).stream()
            .map(Number::longValue)
            .toList();

        return Result.success(batchProcessService.batchEmbed(documentIds));
    }

    @PostMapping("/export")
    public Result<Map<String, Object>> batchExport(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> documentIds = ((List<Number>) request.get("documentIds")).stream()
            .map(Number::longValue)
            .toList();
        String format = (String) request.getOrDefault("format", "json");

        return Result.success(batchProcessService.batchExport(documentIds, format));
    }

    @GetMapping("/status/{batchId}")
    public Result<Map<String, Object>> getBatchStatus(@PathVariable Long batchId) {
        return Result.success(batchProcessService.getBatchStatus(batchId));
    }

    @PostMapping("/cancel/{batchId}")
    public Result<Void> cancelBatch(@PathVariable Long batchId) {
        batchProcessService.cancelBatch(batchId);
        return Result.success();
    }
}
