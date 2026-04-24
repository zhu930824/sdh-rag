package cn.sdh.backend.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 记忆上下文 - 传递给LLM的记忆信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryContext {

    /** 最近对话轮次 */
    private List<MemoryEntry> recentMessages;

    /** 用户偏好列表 */
    private List<String> preferences;

    /** 已知事实列表 */
    private List<String> facts;

    /** 行为模式列表 */
    private List<String> patterns;

    /** 相关历史摘要 */
    private List<String> relevantSummaries;

    /** 当前话题标签 */
    private String currentTopic;

    /**
     * 构建系统提示词
     */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能助手。\n\n");

        if (preferences != null && !preferences.isEmpty()) {
            sb.append("## 用户偏好\n");
            for (String pref : preferences) {
                sb.append("- ").append(pref).append("\n");
            }
            sb.append("\n");
        }

        if (facts != null && !facts.isEmpty()) {
            sb.append("## 已知用户信息\n");
            for (String fact : facts) {
                sb.append("- ").append(fact).append("\n");
            }
            sb.append("\n");
        }

        if (patterns != null && !patterns.isEmpty()) {
            sb.append("## 用户交互风格\n");
            for (String pattern : patterns) {
                sb.append("- ").append(pattern).append("\n");
            }
            sb.append("\n");
        }

        if (relevantSummaries != null && !relevantSummaries.isEmpty()) {
            sb.append("## 相关历史对话摘要\n");
            for (String summary : relevantSummaries) {
                sb.append("- ").append(summary).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 是否有有效记忆
     */
    public boolean hasMemory() {
        return (preferences != null && !preferences.isEmpty())
            || (facts != null && !facts.isEmpty())
            || (patterns != null && !patterns.isEmpty())
            || (relevantSummaries != null && !relevantSummaries.isEmpty());
    }
}
