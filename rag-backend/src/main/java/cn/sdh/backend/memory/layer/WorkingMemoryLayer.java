package cn.sdh.backend.memory.layer;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 工作记忆层 - Redis实现
 * 存储当前会话的最近N轮对话
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingMemoryLayer implements MemoryLayer {

    private final StringRedisTemplate redisTemplate;
    private final MemoryConfig config;

    @Override
    public void store(MemoryEntry entry) {
        if (entry.getSessionId() == null) {
            log.warn("Working memory requires sessionId");
            return;
        }

        String key = getRedisKey(entry.getSessionId());

        try {
            Map<String, Object> entryMap = new HashMap<>();
            entryMap.put("id", entry.getId());
            entryMap.put("userId", entry.getUserId());
            entryMap.put("content", entry.getContent());
            entryMap.put("type", entry.getType().getCode());
            entryMap.put("createdAt", entry.getCreatedAt().toString());

            String json = JSON.toJSONString(entryMap);

            redisTemplate.opsForList().rightPush(key, json);

            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > config.getWorkingWindowSize()) {
                long trimCount = size - config.getWorkingWindowSize();
                for (int i = 0; i < trimCount; i++) {
                    redisTemplate.opsForList().leftPop(key);
                }
            }

            redisTemplate.expire(key, config.getWorkingMemoryTtlHours(), TimeUnit.HOURS);

            log.debug("Stored working memory for session: {}", entry.getSessionId());
        } catch (Exception e) {
            log.error("Failed to store working memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getSessionId() == null) {
            return Collections.emptyList();
        }

        String key = getRedisKey(query.getSessionId());

        try {
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (jsonList == null || jsonList.isEmpty()) {
                return Collections.emptyList();
            }

            List<MemoryEntry> entries = new ArrayList<>();
            for (String json : jsonList) {
                try {
                    Map<String, Object> map = JSON.parseObject(json, Map.class);
                    MemoryEntry entry = MemoryEntry.builder()
                        .id((String) map.get("id"))
                        .userId(((Number) map.get("userId")).longValue())
                        .content((String) map.get("content"))
                        .type(MemoryType.valueOf(((String) map.get("type")).toUpperCase()))
                        .layer(MemoryLayerType.WORKING)
                        .createdAt(LocalDateTime.parse((String) map.get("createdAt")))
                        .build();
                    entries.add(entry);
                } catch (Exception e) {
                    log.warn("Failed to parse working memory entry: {}", e.getMessage());
                }
            }

            int limit = query.getLimit() > 0 ? query.getLimit() : config.getWorkingWindowSize();
            if (entries.size() > limit) {
                return entries.subList(entries.size() - limit, entries.size());
            }

            return entries;
        } catch (Exception e) {
            log.error("Failed to recall working memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void update(MemoryEntry entry) {
        store(entry);
    }

    @Override
    public void delete(String id) {
        log.warn("Working memory does not support single entry deletion");
    }

    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.WORKING;
    }

    @Override
    public void clearByUserId(Long userId) {
        log.warn("Working memory is session-based, cannot clear by userId");
    }

    @Override
    public boolean isAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis not available for working memory: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清除会话的工作记忆
     */
    public void clearSession(String sessionId) {
        String key = getRedisKey(sessionId);
        redisTemplate.delete(key);
        log.debug("Cleared working memory for session: {}", sessionId);
    }

    /**
     * 获取会话消息数量
     */
    public long getMessageCount(String sessionId) {
        String key = getRedisKey(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        return size != null ? size : 0;
    }

    private String getRedisKey(String sessionId) {
        return config.getWorkingMemoryKeyPrefix() + sessionId;
    }
}
