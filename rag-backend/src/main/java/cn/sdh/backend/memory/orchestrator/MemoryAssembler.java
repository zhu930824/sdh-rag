package cn.sdh.backend.memory.orchestrator;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.memory.layer.*;
import cn.sdh.backend.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆上下文组装器
 * 从各层收集记忆并组装成上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAssembler {

    private final WorkingMemoryLayer workingMemoryLayer;
    private final SemanticMemoryLayer semanticMemoryLayer;
    private final PatternMemoryLayer patternMemoryLayer;
    private final AbstractMemoryLayer abstractMemoryLayer;
    private final EmbeddingService embeddingService;
    private final MemoryConfig config;

    @Value("${knowledge.default.embeddingModel:text-embedding-v2}")
    private String defaultEmbeddingModel;

    /**
     * 组装记忆上下文
     */
    public MemoryContext assemble(Long userId, String sessionId, String currentQuestion) {
        MemoryContext.MemoryContextBuilder builder = MemoryContext.builder();

        try {
            // 1. 工作记忆：最近对话
            MemoryQuery workingQuery = MemoryQuery.builder()
                .sessionId(sessionId)
                .limit(config.getWorkingWindowSize())
                .build();
            builder.recentMessages(workingMemoryLayer.recall(workingQuery));

            // 2. 语义记忆：相关偏好和事实
            float[] questionEmbedding = null;
            try {
                questionEmbedding = embeddingService.getEmbeddingByModel(currentQuestion, defaultEmbeddingModel);
            } catch (Exception e) {
                log.warn("Failed to embed question for semantic recall: {}", e.getMessage());
            }

            if (questionEmbedding != null) {
                MemoryQuery semanticQuery = MemoryQuery.builder()
                    .userId(userId)
                    .queryEmbedding(questionEmbedding)
                    .limit(10)
                    .build();

                List<MemoryEntry> semanticMemories = semanticMemoryLayer.recall(semanticQuery);

                builder.preferences(semanticMemories.stream()
                    .filter(m -> m.getType() == MemoryType.PREFERENCE)
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));

                builder.facts(semanticMemories.stream()
                    .filter(m -> m.getType() == MemoryType.FACT)
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));
            } else {
                // 降级：直接获取所有偏好和事实
                builder.preferences(semanticMemoryLayer.getUserPreferences(userId).stream()
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));

                builder.facts(semanticMemoryLayer.getUserFacts(userId).stream()
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));
            }

            // 3. 模式记忆：用户行为模式
            MemoryQuery patternQuery = MemoryQuery.builder()
                .userId(userId)
                .limit(10)
                .build();
            builder.patterns(patternMemoryLayer.recall(patternQuery).stream()
                .map(MemoryEntry::getContent)
                .collect(Collectors.toList()));

            // 4. 抽象记忆：相关历史摘要
            MemoryQuery abstractQuery = MemoryQuery.builder()
                .userId(userId)
                .limit(5)
                .build();
            builder.relevantSummaries(abstractMemoryLayer.recall(abstractQuery).stream()
                .map(MemoryEntry::getContent)
                .collect(Collectors.toList()));

        } catch (Exception e) {
            log.error("Failed to assemble memory context: {}", e.getMessage(), e);
        }

        return builder.build();
    }
}
