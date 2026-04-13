package cn.sdh.backend.rag;

import cn.sdh.backend.entity.TokenUsage;
import cn.sdh.backend.service.TokenUsageService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 使用量统计 Advisor
 *
 * <p>利用 Spring AI 的 {@link Usage} 接口获取模型用量信息。
 * Spring AI 通过 Usage 接口的 getNativeUsage() 方法和 DefaultUsage 实现，
 * 简化了不同 AI 模型跟踪和报告用量指标的流程。</p>
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
 *     spec.advisors(tokenUsageAdvisor);
 *     // 必须显式传递 userId，因为 advisor 可能在不同线程执行
 *     spec.adviseContext(TokenUsageAdvisor.USER_ID, userId);
 *     spec.adviseContext(TokenUsageAdvisor.SESSION_ID, sessionId);
 *     spec.adviseContext(TokenUsageAdvisor.MODEL_ID, modelId);
 *     spec.adviseContext(TokenUsageAdvisor.KNOWLEDGE_ID, knowledgeId);
 * });
 * </pre>
 *
 * <h3>Context 参数</h3>
 * <ul>
 *   <li>{@link #USER_ID} - 用户ID（必须传递）</li>
 *   <li>{@link #SESSION_ID} - 会话ID</li>
 *   <li>{@link #MODEL_ID} - 模型ID</li>
 *   <li>{@link #MODEL_NAME} - 模型名称</li>
 *   <li>{@link #KNOWLEDGE_ID} - 知识库ID</li>
 * </ul>
 *
 * @see Usage
 * @see org.springframework.ai.chat.metadata.DefaultUsage
 */
@Slf4j
@Getter
public class TokenUsageAdvisor implements BaseAdvisor {

    // ==================== Context Keys ====================

    public static final String START_TIME = "tokenUsageStartTime";
    public static final String REQUEST_ID = "tokenUsageRequestId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String MODEL_ID = "modelId";
    public static final String MODEL_NAME = "modelName";
    public static final String KNOWLEDGE_ID = "knowledgeId";

    // ==================== Fields ====================

    private final int order;
    private final TokenUsageService tokenUsageService;

    // 内存统计
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    // ==================== Constructor ====================

    public TokenUsageAdvisor(TokenUsageService tokenUsageService) {
        this(900, tokenUsageService);
    }

    public TokenUsageAdvisor(int order, TokenUsageService tokenUsageService) {
        this.order = order;
        this.tokenUsageService = tokenUsageService;
    }

    // ==================== BaseAdvisor 实现 ====================

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Instant startTime = Instant.now();
        String requestId = generateRequestId();

        return request.mutate()
                .context(START_TIME, startTime)
                .context(REQUEST_ID, requestId)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Map<String, Object> context = response.context();
        recordUsage(response.chatResponse(), context);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        AtomicLong promptTokens = new AtomicLong(0);
        AtomicLong completionTokens = new AtomicLong(0);

        Instant startTime = Instant.now();
        String requestId = generateRequestId();

        // 在闭包中捕获 context
        Map<String, Object> context = new ConcurrentHashMap<>(request.context());
        context.put(START_TIME, startTime);
        context.put(REQUEST_ID, requestId);

        ChatClientRequest modifiedRequest = request.mutate()
                .context(START_TIME, startTime)
                .context(REQUEST_ID, requestId)
                .build();

        return chain.nextStream(modifiedRequest)
                .doOnNext(response -> {
                    // 从响应中累积 token 使用量
                    Usage usage = extractUsage(response.chatResponse());
                    if (usage != null) {
                        if (usage.getPromptTokens() != null) {
                            promptTokens.set(usage.getPromptTokens().longValue());
                        }
                        if (usage.getCompletionTokens() != null) {
                            completionTokens.addAndGet(usage.getCompletionTokens().longValue());
                        }
                    }
                })
                .doOnComplete(() -> {
                    long prompt = promptTokens.get();
                    long completion = completionTokens.get();
                    if (prompt > 0 || completion > 0) {
                        persistUsage(prompt, completion, context, null);
                    }
                });
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.boundedElastic();
    }

    @Override
    public int getOrder() {
        return order;
    }

    // ==================== Token 使用记录方法 ====================

    /**
     * 记录 token 使用情况
     */
    private void recordUsage(ChatResponse response, Map<String, Object> context) {
        if (response == null || response.getMetadata() == null) {
            return;
        }

        Usage usage = extractUsage(response);
        if (usage == null) {
            return;
        }

        long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0;
        long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0;
        String modelName = response.getMetadata().getModel();

        persistUsage(promptTokens, completionTokens, context, modelName);
    }

    /**
     * 从响应中提取 Usage 信息
     *
     * <p>Spring AI 的 Usage 接口提供了标准化的用量获取方式：
     * <ul>
     *   <li>{@link Usage#getPromptTokens()} - 输入 token 数</li>
     *   <li>{@link Usage#getCompletionTokens()} - 输出 token 数</li>
     *   <li>{@link Usage#getTotalTokens()} - 总 token 数</li>
     *   <li>{@link Usage#getNativeUsage()} - 获取原始模型的用量信息（可能包含更多细节）</li>
     * </ul>
     * </p>
     */
    private Usage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    /**
     * 持久化 token 使用记录
     *
     * <p>注意：userId 必须通过 context 传递，不能依赖 ThreadLocal。
     * 因为流式请求可能在不同线程执行。</p>
     */
    private void persistUsage(long promptTokens, long completionTokens,
                               Map<String, Object> context, String modelName) {
        long total = promptTokens + completionTokens;

        // 更新内存统计
        updateMemoryStats(promptTokens, completionTokens);

        // 计算耗时
        Long durationMs = null;
        Object startTime = context.get(START_TIME);
        if (startTime instanceof Instant) {
            durationMs = Duration.between((Instant) startTime, Instant.now()).toMillis();
        }

        // 记录日志
        log.info("Token 使用统计 - prompt: {}, completion: {}, total: {}, model: {}",
                promptTokens, completionTokens, total, modelName);

        // 持久化到数据库
        if (tokenUsageService != null) {
            // 从 context 获取 userId（由调用方通过 adviseContext 传递）
            Long userId = getLongFromContext(context, USER_ID);

            if (userId == null) {
                log.warn("Token 使用记录缺少 userId，跳过持久化。请确保通过 adviseContext 传递 userId");
                return;
            }

            TokenUsage usage = new TokenUsage();
            usage.setUserId(userId);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(total);
            usage.setDurationMs(durationMs);
            usage.setStatus("success");
            usage.setCreateTime(LocalDateTime.now());

            usage.setSessionId(getStringFromContext(context, SESSION_ID));
            usage.setModelId(getLongFromContext(context, MODEL_ID));
            usage.setModelName(modelName);
            usage.setKnowledgeId(getLongFromContext(context, KNOWLEDGE_ID));
            usage.setRequestId(getStringFromContext(context, REQUEST_ID));

            tokenUsageService.saveAsync(usage);
        }
    }

    private void updateMemoryStats(long promptTokens, long completionTokens) {
        this.totalPromptTokens.addAndGet(promptTokens);
        this.totalCompletionTokens.addAndGet(completionTokens);
        this.totalRequests.incrementAndGet();
    }

    private String generateRequestId() {
        return Long.toHexString(System.currentTimeMillis()) + "-" +
               Integer.toHexString((int) (Math.random() * 0xFFFF));
    }

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

    // ==================== 统计查询 ====================

    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        stats.put("totalRequests", totalRequests.get());
        stats.put("totalPromptTokens", totalPromptTokens.get());
        stats.put("totalCompletionTokens", totalCompletionTokens.get());
        stats.put("totalTokens", totalPromptTokens.get() + totalCompletionTokens.get());
        return stats;
    }

    public void resetStatistics() {
        totalPromptTokens.set(0);
        totalCompletionTokens.set(0);
        totalRequests.set(0);
        log.info("Token 内存统计已重置");
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int order = 900;
        private TokenUsageService tokenUsageService;

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder tokenUsageService(TokenUsageService tokenUsageService) {
            this.tokenUsageService = tokenUsageService;
            return this;
        }

        public TokenUsageAdvisor build() {
            return new TokenUsageAdvisor(order, tokenUsageService);
        }
    }
}
