package cn.sdh.backend.memory.extractor;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 模式提取器
 * 从多轮对话中分析用户行为模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternExtractor implements MemoryExtractor {

    private final ChatClient.Builder chatClientBuilder;
    private final StringRedisTemplate redisTemplate;
    private final MemoryConfig config;

    private static final String INTERACTION_KEY_PREFIX = "memory:interaction:";
    private static final String EXTRACTION_PROMPT = """
        分析用户交互模式：

        最近交互记录：%s

        识别模式：
        1. 提问风格（detail_first: 先概述后细节 / direct: 直接深入 / example_oriented: 偏好示例）
        2. 关注点类型（theory: 理论 / practice: 实践 / troubleshooting: 排错）
        3. 技术水平（beginner / intermediate / advanced）

        返回格式：[{"type": "behavior/preference/topic", "name": "模式名称", "description": "描述", "confidence": 0.0-1.0}]
        只返回JSON数组。
        """;

    @Override
    public CompletableFuture<List<MemoryEntry>> extract(Long userId, String question, String answer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 记录本次交互
                recordInteraction(userId, question);

                // 检查是否有足够的观察次数
                String key = INTERACTION_KEY_PREFIX + userId;
                Long count = redisTemplate.opsForList().size(key);

                if (count == null || count < config.getPatternMinObservations()) {
                    log.debug("Not enough observations for pattern extraction: {}", count);
                    return List.of();
                }

                // 获取最近交互记录
                List<String> interactions = redisTemplate.opsForList().range(key, 0, 19);
                if (interactions == null || interactions.isEmpty()) {
                    return List.of();
                }

                String prompt = String.format(EXTRACTION_PROMPT, String.join("\n", interactions));

                String response = chatClientBuilder.build()
                    .prompt(prompt)
                    .call()
                    .content();

                return parseResponse(userId, response);
            } catch (Exception e) {
                log.error("Failed to extract patterns: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }

    @Override
    public MemoryType getMemoryType() {
        return MemoryType.PATTERN;
    }

    @Override
    public MemoryLayerType getTargetLayer() {
        return MemoryLayerType.PATTERN;
    }

    private void recordInteraction(Long userId, String question) {
        String key = INTERACTION_KEY_PREFIX + userId;
        redisTemplate.opsForList().rightPush(key, question);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);

        // 保持最近20条
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 20) {
            redisTemplate.opsForList().trim(key, size - 20, -1);
        }
    }

    private List<MemoryEntry> parseResponse(Long userId, String response) {
        List<MemoryEntry> entries = new ArrayList<>();

        try {
            String jsonStr = response;
            if (response.contains("[")) {
                jsonStr = response.substring(response.indexOf("["), response.lastIndexOf("]") + 1);
            }

            JSONArray array = JSON.parseArray(jsonStr);
            if (array == null || array.isEmpty()) {
                return entries;
            }

            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String type = obj.getString("type");
                String name = obj.getString("name");
                String description = obj.getString("description");
                Double confidence = obj.getDouble("confidence");

                if (name != null && description != null) {
                    entries.add(MemoryEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .type(MemoryType.PATTERN)
                        .layer(MemoryLayerType.PATTERN)
                        .content(JSON.toJSONString(obj))
                        .importance((int) ((confidence != null ? confidence : 0.5) * 10))
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse pattern extraction response: {}", e.getMessage());
        }

        return entries;
    }
}
