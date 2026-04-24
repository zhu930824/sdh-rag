package cn.sdh.backend.memory.extractor;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 偏好提取器
 * 从对话中提取用户偏好
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreferenceExtractor implements MemoryExtractor {

    private final ChatClient.Builder chatClientBuilder;
    private final MemoryConfig config;

    private static final String EXTRACTION_PROMPT = """
        从以下对话中提取用户偏好，返回JSON数组。

        对话：
        用户：%s
        助手：%s

        提取规则：
        1. 语言偏好（中文/英文）
        2. 回复风格偏好（简洁/详细/代码示例）
        3. 技术栈偏好
        4. 其他显式偏好表达

        返回格式：[{"content": "偏好内容", "importance": 1-10}]
        无偏好则返回空数组 []
        只返回JSON数组，不要有其他内容。
        """;

    @Override
    public CompletableFuture<List<MemoryEntry>> extract(Long userId, String question, String answer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(EXTRACTION_PROMPT, question, answer);

                String response = chatClientBuilder.build()
                    .prompt(prompt)
                    .call()
                    .content();

                List<MemoryEntry> entries = parseResponse(userId, response);

                return entries.stream()
                    .filter(e -> e.getImportance() >= config.getPreferenceImportanceThreshold())
                    .toList();
            } catch (Exception e) {
                log.error("Failed to extract preferences: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }

    @Override
    public MemoryType getMemoryType() {
        return MemoryType.PREFERENCE;
    }

    @Override
    public MemoryLayerType getTargetLayer() {
        return MemoryLayerType.SEMANTIC;
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
                String content = obj.getString("content");
                int importance = obj.getIntValue("importance", 5);

                if (content != null && !content.isBlank()) {
                    entries.add(MemoryEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .type(MemoryType.PREFERENCE)
                        .layer(MemoryLayerType.SEMANTIC)
                        .content(content)
                        .importance(importance)
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse preference extraction response: {}", e.getMessage());
        }

        return entries;
    }
}
