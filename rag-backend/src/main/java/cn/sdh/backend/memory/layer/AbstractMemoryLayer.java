package cn.sdh.backend.memory.layer;

import cn.sdh.backend.entity.MemoryAbstract;
import cn.sdh.backend.mapper.MemoryAbstractMapper;
import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽象记忆层 - MySQL摘要实现
 * 存储对话摘要，用于长期记忆压缩
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbstractMemoryLayer implements MemoryLayer {

    private final MemoryAbstractMapper memoryAbstractMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final MemoryConfig config;

    private static final String SUMMARIZATION_PROMPT = """
        请对以下对话生成摘要：

        %s

        返回JSON格式：
        {
          "summary": "对话摘要（2-3句话）",
          "keyPoints": ["关键结论1", "关键结论2", ...],
          "topicTag": "话题标签"
        }
        只返回JSON，不要有其他内容。
        """;

    @Override
    public void store(MemoryEntry entry) {
        if (entry.getUserId() == null || entry.getContent() == null) {
            return;
        }

        try {
            MemoryAbstract entity = new MemoryAbstract();
            entity.setUserId(entry.getUserId());
            entity.setSessionId(entry.getSessionId());
            entity.setEpisodeId(entry.getMetadata() != null ?
                (String) entry.getMetadata().get("episodeId") : null);
            entity.setTopicTag(entry.getMetadata() != null ?
                (String) entry.getMetadata().get("topicTag") : null);
            entity.setSummary(entry.getContent());
            entity.setKeyPoints(entry.getMetadata() != null ?
                (List<String>) entry.getMetadata().get("keyPoints") : null);
            entity.setImportance(entry.getImportance());
            entity.setNeedsSummary(false);
            entity.setCreatedAt(LocalDateTime.now());

            memoryAbstractMapper.insert(entity);
            log.debug("Stored abstract memory: {}", entity.getId());
        } catch (Exception e) {
            log.error("Failed to store abstract memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getUserId() == null) {
            return Collections.emptyList();
        }

        try {
            LambdaQueryWrapper<MemoryAbstract> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MemoryAbstract::getUserId, query.getUserId());

            if (query.getLimit() > 0) {
                wrapper.last("LIMIT " + query.getLimit());
            } else {
                wrapper.last("LIMIT 10");
            }

            wrapper.orderByDesc(MemoryAbstract::getCreatedAt);

            List<MemoryAbstract> abstracts = memoryAbstractMapper.selectList(wrapper);

            return abstracts.stream()
                .map(this::convertToEntry)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to recall abstract memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void update(MemoryEntry entry) {
        try {
            if (entry.getId() == null) return;

            MemoryAbstract entity = memoryAbstractMapper.selectById(Long.parseLong(entry.getId()));
            if (entity != null) {
                entity.setAccessCount(entity.getAccessCount() + 1);
                entity.setLastAccessAt(LocalDateTime.now());
                memoryAbstractMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to update abstract memory: {}", e.getMessage());
        }
    }

    @Override
    public void delete(String id) {
        try {
            memoryAbstractMapper.deleteById(Long.parseLong(id));
        } catch (Exception e) {
            log.warn("Failed to delete abstract memory: {}", e.getMessage());
        }
    }

    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.ABSTRACT;
    }

    @Override
    public void clearByUserId(Long userId) {
        try {
            LambdaQueryWrapper<MemoryAbstract> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MemoryAbstract::getUserId, userId);
            memoryAbstractMapper.delete(wrapper);
        } catch (Exception e) {
            log.error("Failed to clear abstract memory: {}", e.getMessage(), e);
        }
    }

    /**
     * 生成摘要
     */
    public MemoryEntry generateSummary(Long userId, String sessionId, String episodeId,
                                        String topicTag, List<MemoryEntry> messages) {
        try {
            String dialogues = messages.stream()
                .map(MemoryEntry::getContent)
                .collect(Collectors.joining("\n\n"));

            String prompt = String.format(SUMMARIZATION_PROMPT, dialogues);

            String response = chatClientBuilder.build()
                .prompt(prompt)
                .call()
                .content();

            JSONObject obj = parseJsonResponse(response);
            if (obj == null) {
                return null;
            }

            String summary = obj.getString("summary");
            JSONArray keyPointsArray = obj.getJSONArray("keyPoints");
            List<String> keyPoints = keyPointsArray != null ?
                keyPointsArray.toJavaList(String.class) : new ArrayList<>();
            String detectedTopic = obj.getString("topicTag");

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("episodeId", episodeId);
            metadata.put("topicTag", detectedTopic != null ? detectedTopic : topicTag);
            metadata.put("keyPoints", keyPoints);

            return MemoryEntry.builder()
                .userId(userId)
                .sessionId(sessionId)
                .type(MemoryType.SUMMARY)
                .layer(MemoryLayerType.ABSTRACT)
                .content(summary)
                .metadata(metadata)
                .importance(5)
                .build();
        } catch (Exception e) {
            log.error("Failed to generate summary: {}", e.getMessage(), e);
            return null;
        }
    }

    private JSONObject parseJsonResponse(String response) {
        try {
            String jsonStr = response;
            if (response.contains("{")) {
                jsonStr = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
            }
            return JSON.parseObject(jsonStr);
        } catch (Exception e) {
            log.warn("Failed to parse summary response: {}", e.getMessage());
            return null;
        }
    }

    private MemoryEntry convertToEntry(MemoryAbstract entity) {
        return MemoryEntry.builder()
            .id(String.valueOf(entity.getId()))
            .userId(entity.getUserId())
            .sessionId(entity.getSessionId())
            .type(MemoryType.SUMMARY)
            .layer(MemoryLayerType.ABSTRACT)
            .content(entity.getSummary())
            .importance(entity.getImportance() != null ? entity.getImportance() : 5)
            .accessCount(entity.getAccessCount() != null ? entity.getAccessCount() : 0)
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
