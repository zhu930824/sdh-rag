package cn.sdh.backend.memory.boundary;

import cn.sdh.backend.memory.core.MemoryEntry;
import cn.sdh.backend.memory.layer.AbstractMemoryLayer;
import cn.sdh.backend.memory.layer.EpisodicMemoryLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 摘要触发器
 * 在语义边界触发时生成摘要
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizationTrigger {

    private final AbstractMemoryLayer abstractMemoryLayer;
    private final EpisodicMemoryLayer episodicMemoryLayer;

    /**
     * 触发摘要生成
     */
    public CompletableFuture<Void> trigger(BoundaryResult boundary, Long userId,
                                            String sessionId, String episodeId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 获取当前episode的所有消息
                List<MemoryEntry> messages = episodicMemoryLayer.getSessionHistory(sessionId);

                if (messages.isEmpty()) {
                    log.debug("No messages to summarize for session: {}", sessionId);
                    return;
                }

                // 生成摘要
                MemoryEntry summary = abstractMemoryLayer.generateSummary(
                    userId,
                    sessionId,
                    episodeId,
                    boundary.getTopicTag(),
                    messages
                );

                if (summary != null) {
                    abstractMemoryLayer.store(summary);
                    log.info("Generated summary for episode: {}", episodeId);
                }
            } catch (Exception e) {
                log.error("Failed to trigger summarization: {}", e.getMessage(), e);
            }
        });
    }
}
