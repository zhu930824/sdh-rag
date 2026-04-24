package cn.sdh.backend.memory.layer;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 情景记忆层 - MySQL实现
 * 存储完整对话记录，按语义分块
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpisodicMemoryLayer implements MemoryLayer {

    private final ChatHistoryMapper chatHistoryMapper;
    private final MemoryConfig config;

    @Override
    public void store(MemoryEntry entry) {
        log.debug("Episodic memory is stored via ChatService");
    }

    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getUserId() == null) {
            return Collections.emptyList();
        }

        try {
            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getUserId, query.getUserId());

            if (query.getSessionId() != null) {
                wrapper.eq(ChatHistory::getSessionId, query.getSessionId());
            }

            wrapper.orderByDesc(ChatHistory::getCreateTime);

            int limit = query.getLimit() > 0 ? query.getLimit() : 50;
            wrapper.last("LIMIT " + limit);

            List<ChatHistory> histories = chatHistoryMapper.selectList(wrapper);

            return histories.stream()
                .map(this::convertToEntry)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to recall episodic memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void update(MemoryEntry entry) {
        log.debug("Episodic memory update is handled by ChatService");
    }

    @Override
    public void delete(String id) {
        try {
            chatHistoryMapper.deleteById(Long.parseLong(id));
        } catch (Exception e) {
            log.warn("Failed to delete episodic memory: {}", e.getMessage());
        }
    }

    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.EPISODIC;
    }

    @Override
    public void clearByUserId(Long userId) {
        log.warn("Episodic memory cannot be cleared by userId - history is preserved");
    }

    /**
     * 获取会话的对话历史
     */
    public List<MemoryEntry> getSessionHistory(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }

        try {
            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getSessionId, sessionId)
                   .orderByAsc(ChatHistory::getCreateTime);

            List<ChatHistory> histories = chatHistoryMapper.selectList(wrapper);

            return histories.stream()
                .map(this::convertToEntry)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get session history: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 标记情景块
     */
    public void markEpisode(List<String> historyIds, String episodeId, String topicTag) {
        log.debug("Marking episode {} with topic {} for {} messages", episodeId, topicTag, historyIds.size());
    }

    /**
     * 生成新的情景块ID
     */
    public String generateEpisodeId() {
        return "episode_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MemoryEntry convertToEntry(ChatHistory history) {
        return MemoryEntry.builder()
            .id(String.valueOf(history.getId()))
            .userId(history.getUserId())
            .sessionId(history.getSessionId())
            .layer(MemoryLayerType.EPISODIC)
            .type(MemoryType.EPISODE)
            .content("Q: " + history.getQuestion() + "\nA: " + history.getAnswer())
            .createdAt(history.getCreateTime())
            .importance(5)
            .build();
    }
}
