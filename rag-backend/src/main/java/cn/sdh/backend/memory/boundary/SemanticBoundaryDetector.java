package cn.sdh.backend.memory.boundary;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.MemoryEntry;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 语义边界检测器
 * 检测对话中的话题切换点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticBoundaryDetector {

    private final ChatClient.Builder chatClientBuilder;
    private final MemoryConfig config;

    /** 显式过渡词 */
    private static final List<String> TRANSITION_KEYWORDS = List.of(
        "换个话题", "顺便问一下", "对了", "另外", "还有个问题",
        "说点别的", "不说这个了", "换个问题"
    );

    private static final String DETECTION_PROMPT = """
        分析以下对话，判断是否有话题切换。

        最近对话：
        %s

        当前问题：%s

        返回JSON：{ "isTransition": true/false, "topicTag": "当前话题标签", "reason": "判断原因" }
        只返回JSON，不要有其他内容。
        """;

    /**
     * 检测语义边界
     */
    public BoundaryResult detect(String currentQuestion, List<MemoryEntry> recentMessages, int currentMessageCount) {
        // 1. 检查长度限制
        if (currentMessageCount >= config.getSummaryMaxMessages()) {
            log.debug("Length limit reached: {}", currentMessageCount);
            return BoundaryResult.lengthLimit();
        }

        // 2. 检查显式过渡词
        if (hasExplicitTransition(currentQuestion)) {
            log.debug("Explicit transition detected in: {}", currentQuestion);
            return BoundaryResult.transition("新话题", "检测到显式过渡词");
        }

        // 3. LLM语义判断
        if (recentMessages != null && !recentMessages.isEmpty()) {
            try {
                return detectByLLM(currentQuestion, recentMessages);
            } catch (Exception e) {
                log.warn("LLM boundary detection failed: {}", e.getMessage());
            }
        }

        return BoundaryResult.none();
    }

    /**
     * 检查是否包含显式过渡词
     */
    private boolean hasExplicitTransition(String question) {
        if (question == null) return false;
        String lowerQuestion = question.toLowerCase();
        return TRANSITION_KEYWORDS.stream()
            .anyMatch(lowerQuestion::contains);
    }

    /**
     * LLM语义边界检测
     */
    private BoundaryResult detectByLLM(String currentQuestion, List<MemoryEntry> recentMessages) {
        // 取最近N条对话
        int windowSize = Math.min(config.getBoundaryDetectionWindowSize(), recentMessages.size());
        List<MemoryEntry> windowMessages = recentMessages.subList(
            Math.max(0, recentMessages.size() - windowSize),
            recentMessages.size()
        );

        String recentDialogues = windowMessages.stream()
            .map(MemoryEntry::getContent)
            .collect(Collectors.joining("\n"));

        String prompt = String.format(DETECTION_PROMPT, recentDialogues, currentQuestion);

        try {
            String response = chatClientBuilder.build()
                .prompt(prompt)
                .call()
                .content();

            return parseLLMResponse(response);
        } catch (Exception e) {
            log.error("LLM boundary detection error: {}", e.getMessage());
            return BoundaryResult.none();
        }
    }

    private BoundaryResult parseLLMResponse(String response) {
        try {
            String jsonStr = response;
            if (response.contains("{")) {
                jsonStr = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
            }

            JSONObject obj = JSON.parseObject(jsonStr);
            boolean isTransition = obj.getBooleanValue("isTransition");
            String topicTag = obj.getString("topicTag");
            String reason = obj.getString("reason");

            if (isTransition) {
                return BoundaryResult.transition(topicTag, reason);
            }

            return BoundaryResult.none();
        } catch (Exception e) {
            log.warn("Failed to parse LLM boundary response: {}", e.getMessage());
            return BoundaryResult.none();
        }
    }
}
