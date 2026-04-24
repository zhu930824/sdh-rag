package cn.sdh.backend.memory.orchestrator;

import cn.sdh.backend.memory.boundary.BoundaryResult;
import cn.sdh.backend.memory.boundary.SemanticBoundaryDetector;
import cn.sdh.backend.memory.boundary.SummarizationTrigger;
import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.memory.extractor.*;
import cn.sdh.backend.memory.layer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆编排器
 * 统一调度各层记忆的存取和提取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryOrchestrator {

    private final MemoryAssembler memoryAssembler;
    private final WorkingMemoryLayer workingMemoryLayer;
    private final EpisodicMemoryLayer episodicMemoryLayer;
    private final SemanticMemoryLayer semanticMemoryLayer;
    private final PatternMemoryLayer patternMemoryLayer;
    private final AbstractMemoryLayer abstractMemoryLayer;

    private final PreferenceExtractor preferenceExtractor;
    private final FactExtractor factExtractor;
    private final PatternExtractor patternExtractor;

    private final SemanticBoundaryDetector boundaryDetector;
    private final SummarizationTrigger summarizationTrigger;
    private final MemoryConfig config;

    private static final ThreadLocal<String> currentEpisodeId = new ThreadLocal<>();

    /**
     * 组装记忆上下文（用户发消息时调用）
     */
    public MemoryContext assembleContext(Long userId, String sessionId, String currentQuestion) {
        if (!config.isEnabled()) {
            return MemoryContext.builder().build();
        }

        return memoryAssembler.assemble(userId, sessionId, currentQuestion);
    }

    /**
     * 处理并存储记忆（AI回复后异步调用）
     */
    public void processAndStore(Long userId, String sessionId, String question, String answer) {
        if (!config.isEnabled()) {
            return;
        }

        // 异步处理，不阻塞主流程
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 存储工作记忆
                storeWorkingMemory(userId, sessionId, question, answer);

                // 2. 检测语义边界
                List<MemoryEntry> recentMessages = workingMemoryLayer.recall(
                    MemoryQuery.builder().sessionId(sessionId).limit(20).build()
                );

                BoundaryResult boundary = boundaryDetector.detect(question, recentMessages, recentMessages.size());

                // 3. 如果触发边界，生成摘要
                if (boundary.needsSummarization()) {
                    String episodeId = getOrCreateEpisodeId();
                    summarizationTrigger.trigger(boundary, userId, sessionId, episodeId);

                    // 重置episode
                    currentEpisodeId.set(null);
                }

                // 4. 并行提取记忆
                extractAndStore(userId, question, answer);

            } catch (Exception e) {
                log.error("Failed to process and store memory: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 存储工作记忆
     */
    private void storeWorkingMemory(Long userId, String sessionId, String question, String answer) {
        // 存储用户问题
        MemoryEntry userEntry = MemoryEntry.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .sessionId(sessionId)
            .type(MemoryType.EPISODE)
            .layer(MemoryLayerType.WORKING)
            .content("用户：" + question)
            .importance(5)
            .createdAt(LocalDateTime.now())
            .build();
        workingMemoryLayer.store(userEntry);

        // 存储AI回复
        MemoryEntry assistantEntry = MemoryEntry.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .sessionId(sessionId)
            .type(MemoryType.EPISODE)
            .layer(MemoryLayerType.WORKING)
            .content("助手：" + answer)
            .importance(5)
            .createdAt(LocalDateTime.now())
            .build();
        workingMemoryLayer.store(assistantEntry);
    }

    /**
     * 并行提取并存储记忆
     */
    private void extractAndStore(Long userId, String question, String answer) {
        // 并行执行三个提取器
        CompletableFuture<List<MemoryEntry>> preferences = preferenceExtractor.extract(userId, question, answer);
        CompletableFuture<List<MemoryEntry>> facts = factExtractor.extract(userId, question, answer);
        CompletableFuture<List<MemoryEntry>> patterns = patternExtractor.extract(userId, question, answer);

        // 等待所有提取完成
        CompletableFuture.allOf(preferences, facts, patterns).join();

        // 存储提取结果
        try {
            List<MemoryEntry> prefResults = preferences.get();
            prefResults.forEach(semanticMemoryLayer::store);
            log.debug("Stored {} preferences", prefResults.size());

            List<MemoryEntry> factResults = facts.get();
            factResults.forEach(semanticMemoryLayer::store);
            log.debug("Stored {} facts", factResults.size());

            List<MemoryEntry> patternResults = patterns.get();
            patternResults.forEach(patternMemoryLayer::store);
            log.debug("Stored {} patterns", patternResults.size());
        } catch (Exception e) {
            log.error("Failed to store extracted memories: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取或创建EpisodeId
     */
    private String getOrCreateEpisodeId() {
        String episodeId = currentEpisodeId.get();
        if (episodeId == null) {
            episodeId = episodicMemoryLayer.generateEpisodeId();
            currentEpisodeId.set(episodeId);
        }
        return episodeId;
    }

    /**
     * 清除用户所有记忆
     */
    public void clearUserMemory(Long userId) {
        workingMemoryLayer.clearByUserId(userId);
        semanticMemoryLayer.clearByUserId(userId);
        patternMemoryLayer.clearByUserId(userId);
        abstractMemoryLayer.clearByUserId(userId);
        log.info("Cleared all memory for user: {}", userId);
    }

    /**
     * 清除会话工作记忆
     */
    public void clearSessionMemory(String sessionId) {
        workingMemoryLayer.clearSession(sessionId);
        log.debug("Cleared working memory for session: {}", sessionId);
    }
}
