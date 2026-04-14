package cn.sdh.backend.advisor.common;

import cn.sdh.backend.service.HotwordService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热点词提取 Advisor
 *
 * <p>使用 IK 分词器从用户问题中提取关键词，并记录到热点词统计中。</p>
 *
 * <h3>⚠️ 重要：线程安全问题</h3>
 * <p>流式请求会在不同线程执行（Reactor 的 boundedElastic 线程池），
 * 因此 <strong>不能</strong>依赖 ThreadLocal（如 UserContext）传递 userId。
 * 必须通过 {@code adviseContext} 显式传递。</p>
 *
 * <h3>使用方式</h3>
 * <pre>
 * // 在 HTTP 请求线程中（此时 UserContext 可用）
 * Long userId = UserContext.getCurrentUserId();
 *
 * requestSpec = requestSpec.advisors(spec -> {
 *     spec.advisors(hotwordAdvisor);
 *     // 必须显式传递 userId，因为 advisor 可能在不同线程执行
 *     spec.adviseContext(HotwordAdvisor.USER_ID, userId);
 *     spec.adviseContext(HotwordAdvisor.SESSION_ID, sessionId);
 * });
 * </pre>
 *
 * <h3>Context 参数</h3>
 * <ul>
 *   <li>{@link #USER_ID} - 用户ID（必须传递）</li>
 *   <li>{@link #SESSION_ID} - 会话ID</li>
 * </ul>
 */
@Slf4j
@Getter
public class HotwordAdvisor implements BaseAdvisor {

    // ==================== Context Keys ====================

    public static final String USER_ID = "hotwordUserId";
    public static final String SESSION_ID = "hotwordSessionId";
    public static final String QUESTION_PROCESSED = "hotwordQuestionProcessed";

    // ==================== 停用词集合 ====================

    private static final Set<String> STOP_WORDS = Set.of(
            // 中文停用词
            "的", "是", "在", "有", "和", "了", "不", "我", "你", "他", "她", "它",
            "这", "那", "什么", "怎么", "为什么", "如何", "可以", "能", "会", "想",
            "要", "请", "帮", "帮忙", "告诉", "知道", "一下", "一个", "一些",
            "吗", "呢", "吧", "啊", "呀", "嗯", "哦", "哈", "喂", "唉",
            "就", "也", "都", "又", "还", "把", "被", "让", "给", "向", "从",
            "到", "着", "过", "起", "来", "去", "上", "下", "里", "外", "前", "后",
            "很", "太", "更", "最", "已", "正", "将", "应", "该", "得", "地",
            // 英文停用词
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "can",
            "to", "of", "in", "for", "on", "with", "at", "by", "from",
            "about", "as", "into", "through", "during", "before", "after",
            "i", "you", "he", "she", "it", "we", "they", "what", "which",
            "who", "this", "that", "these", "those", "am"
    );

    // ==================== Fields ====================

    private final int order;
    private final HotwordService hotwordService;
    private final int maxKeywords;
    private final int minWordLength;
    private final boolean useSmartMode;

    // ==================== Constructor ====================

    public HotwordAdvisor(HotwordService hotwordService) {
        this(800, hotwordService, 10, 2, true);
    }

    public HotwordAdvisor(int order, HotwordService hotwordService,
                          int maxKeywords, int minWordLength, boolean useSmartMode) {
        this.order = order;
        this.hotwordService = hotwordService;
        this.maxKeywords = maxKeywords;
        this.minWordLength = minWordLength;
        this.useSmartMode = useSmartMode;
    }

    // ==================== BaseAdvisor 实现 ====================

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 提取用户问题并记录热点词
        String question = extractUserQuestion(request);
        if (question != null && !question.isEmpty()) {
            Map<String, Object> context = request.context();
            Long userId = getLongFromContext(context, USER_ID);
            String sessionId = getStringFromContext(context, SESSION_ID);

            // 异步记录热点词（不阻塞请求）
            recordHotwordsAsync(question, userId, sessionId);
        }

        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // 提取用户问题并记录热点词
        String question = extractUserQuestion(request);
        if (question != null && !question.isEmpty()) {
            // 在闭包中捕获 context
            Map<String, Object> context = new ConcurrentHashMap<>(request.context());
            Long userId = getLongFromContext(context, USER_ID);
            String sessionId = getStringFromContext(context, SESSION_ID);

            // 异步记录热点词（不阻塞流式响应）
            recordHotwordsAsync(question, userId, sessionId);
        }

        return chain.nextStream(request);
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.boundedElastic();
    }

    @Override
    public int getOrder() {
        return order;
    }

    // ==================== 热点词提取方法 ====================

    /**
     * 从请求中提取用户问题
     */
    private String extractUserQuestion(ChatClientRequest request) {
        if (request.prompt() == null) {
            return null;
        }

        // 获取用户消息
        List<UserMessage> userMessages = request.prompt().getUserMessages();
        if (userMessages == null || userMessages.isEmpty()) {
            return null;
        }

        // 取第一条用户消息
        return userMessages.stream()
                .map(UserMessage::getText)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 使用 IK 分词器提取关键词
     */
    private List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keywords = new ArrayList<>();

        try {
            // 使用 IK 分词器
            // useSmartMode: true = 智能分词，false = 细粒度分词
            StringReader reader = new StringReader(text);
            IKSegmenter segmenter = new IKSegmenter(reader, useSmartMode);

            Lexeme lexeme;
            while ((lexeme = segmenter.next()) != null) {
                String word = lexeme.getLexemeText();

                // 过滤条件
                if (isValidKeyword(word)) {
                    keywords.add(word.toLowerCase());
                }
            }

            // 去重并限制数量
            return keywords.stream()
                    .distinct()
                    .limit(maxKeywords)
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            log.warn("IK 分词失败，使用简单分词: {}", e.getMessage());
            // 降级为简单分词
            return simpleExtract(text);
        }
    }

    /**
     * 简单分词（降级方案）
     */
    private List<String> simpleExtract(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 移除标点符号，按空格分割
        String cleaned = text.replaceAll("[\\p{Punct}\\s]+", " ");

        return java.util.Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> word != null && !word.isEmpty())
                .filter(word -> word.length() >= minWordLength)
                .filter(word -> !isStopWord(word))
                .map(String::toLowerCase)
                .distinct()
                .limit(maxKeywords)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 判断是否为有效的关键词
     */
    private boolean isValidKeyword(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        // 长度检查
        if (word.length() < minWordLength) {
            return false;
        }

        // 停用词检查
        if (isStopWord(word)) {
            return false;
        }

        // 纯数字检查
        if (word.matches("^\\d+$")) {
            return false;
        }

        // 纯标点符号检查
        if (word.matches("^[\\p{Punct}]+$")) {
            return false;
        }

        return true;
    }

    /**
     * 判断是否为停用词
     */
    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word.toLowerCase());
    }

    /**
     * 异步记录热点词
     */
    private void recordHotwordsAsync(String question, Long userId, String sessionId) {
        try {
            List<String> keywords = extractKeywords(question);

            if (keywords.isEmpty()) {
                return;
            }

            log.debug("提取热点词: {} -> {}", truncate(question, 50), keywords);

            // 异步记录
            if (hotwordService != null) {
                Schedulers.boundedElastic().schedule(() -> {
                    try {
                        hotwordService.recordWords(keywords, userId, sessionId);
                    } catch (Exception e) {
                        log.warn("记录热点词失败: {}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("提取热点词失败: {}", e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private Long getLongFromContext(Map<String, Object> context, String key) {
        if (context == null) return null;
        Object value = context.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getStringFromContext(Map<String, Object> context, String key) {
        if (context == null) return null;
        Object value = context.get(key);
        return value != null ? value.toString() : null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int order = 800;
        private HotwordService hotwordService;
        private int maxKeywords = 10;
        private int minWordLength = 2;
        private boolean useSmartMode = true;

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder hotwordService(HotwordService hotwordService) {
            this.hotwordService = hotwordService;
            return this;
        }

        public Builder maxKeywords(int maxKeywords) {
            this.maxKeywords = maxKeywords;
            return this;
        }

        public Builder minWordLength(int minWordLength) {
            this.minWordLength = minWordLength;
            return this;
        }

        public Builder useSmartMode(boolean useSmartMode) {
            this.useSmartMode = useSmartMode;
            return this;
        }

        public HotwordAdvisor build() {
            return new HotwordAdvisor(order, hotwordService, maxKeywords, minWordLength, useSmartMode);
        }
    }
}
