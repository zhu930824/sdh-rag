package cn.sdh.backend.memory.extractor;

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
 * 事实提取器
 * 从对话中提取客观事实
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FactExtractor implements MemoryExtractor {

    private final ChatClient.Builder chatClientBuilder;

    private static final String EXTRACTION_PROMPT = """
        从以下对话中提取用户提到的客观事实，返回JSON数组。

        对话：
        用户：%s
        助手：%s

        提取规则：
        1. 用户项目信息（技术栈、版本、架构）
        2. 用户已确认的知识点
        3. 用户提到的时间、地点、人物
        4. 用户的业务背景信息

        返回格式：[{"content": "事实内容", "importance": 1-10}]
        无事实则返回空数组 []
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

                return parseResponse(userId, response);
            } catch (Exception e) {
                log.error("Failed to extract facts: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }

    @Override
    public MemoryType getMemoryType() {
        return MemoryType.FACT;
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
                        .type(MemoryType.FACT)
                        .layer(MemoryLayerType.SEMANTIC)
                        .content(content)
                        .importance(importance)
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse fact extraction response: {}", e.getMessage());
        }

        return entries;
    }
}
