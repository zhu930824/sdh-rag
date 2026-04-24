package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.MemoryOverviewResponse;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.memory.layer.SemanticMemoryLayer;
import cn.sdh.backend.memory.layer.AbstractMemoryLayer;
import cn.sdh.backend.memory.layer.PatternMemoryLayer;
import cn.sdh.backend.memory.orchestrator.MemoryOrchestrator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆API控制器
 */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryOrchestrator memoryOrchestrator;
    private final SemanticMemoryLayer semanticMemoryLayer;
    private final PatternMemoryLayer patternMemoryLayer;
    private final AbstractMemoryLayer abstractMemoryLayer;

    /**
     * 获取用户记忆概览
     */
    @GetMapping("/overview")
    public MemoryOverviewResponse getOverview() {
        Long userId = UserContext.getCurrentUserId();

        List<MemoryOverviewResponse.MemoryItem> preferences =
            semanticMemoryLayer.getUserPreferences(userId).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());

        List<MemoryOverviewResponse.MemoryItem> facts =
            semanticMemoryLayer.getUserFacts(userId).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());

        List<MemoryOverviewResponse.MemoryItem> patterns =
            patternMemoryLayer.getBehaviorPatterns(userId).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());

        List<MemoryOverviewResponse.MemoryItem> summaries =
            abstractMemoryLayer.recall(MemoryQuery.builder()
                .userId(userId)
                .limit(10)
                .build()).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());

        return MemoryOverviewResponse.builder()
            .preferences(preferences)
            .facts(facts)
            .patterns(patterns)
            .recentSummaries(summaries)
            .build();
    }

    /**
     * 手动添加偏好
     */
    @PostMapping("/preference")
    public void addPreference(@RequestBody AddPreferenceRequest request) {
        Long userId = UserContext.getCurrentUserId();

        MemoryEntry entry = MemoryEntry.builder()
            .userId(userId)
            .type(MemoryType.PREFERENCE)
            .layer(MemoryLayerType.SEMANTIC)
            .content(request.getContent())
            .importance(request.getImportance() != null ? request.getImportance() : 7)
            .build();

        semanticMemoryLayer.store(entry);
    }

    /**
     * 删除记忆
     */
    @DeleteMapping("/{memoryId}")
    public void deleteMemory(@PathVariable String memoryId,
                            @RequestParam MemoryLayerType layer) {
        switch (layer) {
            case SEMANTIC -> semanticMemoryLayer.delete(memoryId);
            case ABSTRACT -> abstractMemoryLayer.delete(memoryId);
            default -> throw new IllegalArgumentException("Unsupported layer: " + layer);
        }
    }

    /**
     * 搜索记忆
     */
    @GetMapping("/search")
    public List<MemoryOverviewResponse.MemoryItem> search(
            @RequestParam String query,
            @RequestParam(required = false) MemoryType type,
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getCurrentUserId();

        MemoryQuery memoryQuery = MemoryQuery.builder()
            .userId(userId)
            .query(query)
            .type(type)
            .limit(limit)
            .build();

        return semanticMemoryLayer.recall(memoryQuery).stream()
            .map(this::toMemoryItem)
            .collect(Collectors.toList());
    }

    /**
     * 清除用户所有记忆
     */
    @DeleteMapping("/all")
    public void clearAll() {
        Long userId = UserContext.getCurrentUserId();
        memoryOrchestrator.clearUserMemory(userId);
    }

    private MemoryOverviewResponse.MemoryItem toMemoryItem(MemoryEntry entry) {
        return MemoryOverviewResponse.MemoryItem.builder()
            .id(entry.getId())
            .type(entry.getType().getCode())
            .content(entry.getContent())
            .importance(entry.getImportance())
            .accessCount(entry.getAccessCount())
            .createdAt(entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null)
            .build();
    }

    @Data
    public static class AddPreferenceRequest {
        private String content;
        private Integer importance;
    }
}
