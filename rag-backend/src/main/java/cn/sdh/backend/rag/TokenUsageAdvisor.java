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
 * 记录每次请求的 token 消耗情况并持久化到数据库
 */
@Slf4j
@Getter
public class TokenUsageAdvisor implements BaseAdvisor {

    public static final String START_TIME = "tokenUsageStartTime";
    public static final String REQUEST_ID = "tokenUsageRequestId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String MODEL_ID = "modelId";
    public static final String MODEL_NAME = "modelName";
    public static final String KNOWLEDGE_ID = "knowledgeId";

    private final int order;
    private final TokenUsageService tokenUsageService;
    private final TokenUsageRecorder recorder;

    // 内存统计（用于快速查询）
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    // 临时存储请求上下文（用于 after 方法获取请求信息）
    private final Map<String, Map<String, Object>> requestContextCache = new ConcurrentHashMap<>();

    public TokenUsageAdvisor(TokenUsageService tokenUsageService) {
        this(900, tokenUsageService, null);
    }

    public TokenUsageAdvisor(int order, TokenUsageService tokenUsageService, TokenUsageRecorder recorder) {
        this.order = order;
        this.tokenUsageService = tokenUsageService;
        this.recorder = recorder;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Instant startTime = Instant.now();
        String requestId = generateRequestId();

        // 缓存请求上下文，供 after 方法使用
        requestContextCache.put(requestId, request.context());

        return request.mutate()
                .context(START_TIME, startTime)
                .context(REQUEST_ID, requestId)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 从响应上下文获取请求ID
        Map<String, Object> responseContext = response.context();
        String requestId = getStringFromContext(responseContext, REQUEST_ID);

        // 获取缓存的请求上下文
        Map<String, Object> requestContext = requestId != null ?
                requestContextCache.remove(requestId) : null;

        // 使用缓存的请求上下文或响应上下文
        Map<String, Object> context = requestContext != null ? requestContext : responseContext;

        recordUsageFromResponse(response.chatResponse(), context);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        AtomicLong promptTokens = new AtomicLong(0);
        AtomicLong completionTokens = new AtomicLong(0);
        Map<String, Object> context = request.context();

        return chain.nextStream(request)
                .doOnNext(response -> {
                    ChatResponse chatResponse = response.chatResponse();
                    if (chatResponse != null && chatResponse.getMetadata() != null) {
                        Usage usage = chatResponse.getMetadata().getUsage();
                        if (usage != null) {
                            if (usage.getPromptTokens() != null) {
                                promptTokens.set(usage.getPromptTokens().longValue());
                            }
                            if (usage.getCompletionTokens() != null) {
                                completionTokens.addAndGet(usage.getCompletionTokens().longValue());
                            }
                        }
                    }
                })
                .doOnComplete(() -> {
                    long prompt = promptTokens.get();
                    long completion = completionTokens.get();
                    long total = prompt + completion;

                    if (total > 0) {
                        recordUsageFromStream(prompt, completion, context);
                    }

                    // 清理缓存
                    String requestId = getStringFromContext(context, REQUEST_ID);
                    if (requestId != null) {
                        requestContextCache.remove(requestId);
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

    // ==================== 统计方法 ====================

    private void recordUsageFromResponse(ChatResponse response, Map<String, Object> context) {
        if (response == null || response.getMetadata() == null) {
            return;
        }

        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }

        long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0;
        long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0;
        long total = promptTokens + completionTokens;

        // 获取模型名称
        String modelName = response.getMetadata().getModel();

        // 更新内存统计并持久化
        persistTokenUsage(promptTokens, completionTokens, total, context, modelName);
    }

    private void recordUsageFromStream(long promptTokens, long completionTokens, Map<String, Object> context) {
        long total = promptTokens + completionTokens;
        String modelName = getStringFromContext(context, MODEL_NAME);

        // 更新内存统计并持久化
        persistTokenUsage(promptTokens, completionTokens, total, context, modelName);
    }

    private void persistTokenUsage(long promptTokens, long completionTokens, long total,
                                    Map<String, Object> context, String modelName) {
        // 更新内存统计
        updateMemoryStats(promptTokens, completionTokens);

        // 计算耗时
        Long durationMs = null;
        Object startTime = context != null ? context.get(START_TIME) : null;
        if (startTime instanceof Instant) {
            durationMs = Duration.between((Instant) startTime, Instant.now()).toMillis();
        }

        // 记录日志
        log.info("Token 使用统计 - prompt: {}, completion: {}, total: {}, model: {}",
                promptTokens, completionTokens, total, modelName);

        // 持久化到数据库
        if (tokenUsageService != null) {
            TokenUsage usage = new TokenUsage();
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(total);
            usage.setDurationMs(durationMs);
            usage.setStatus("success");
            usage.setCreateTime(LocalDateTime.now());

            if (context != null) {
                usage.setUserId(getLongFromContext(context, USER_ID));
                usage.setSessionId(getStringFromContext(context, SESSION_ID));
                usage.setModelId(getLongFromContext(context, MODEL_ID));
                usage.setModelName(modelName);
                usage.setKnowledgeId(getLongFromContext(context, KNOWLEDGE_ID));
                usage.setRequestId(getStringFromContext(context, REQUEST_ID));
            }

            tokenUsageService.saveAsync(usage);
        }

        // 调用自定义记录器
        if (recorder != null) {
            try {
                recorder.record(promptTokens, completionTokens, total);
            } catch (Exception e) {
                log.warn("Token 记录器执行失败: {}", e.getMessage());
            }
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
        requestContextCache.clear();
        log.info("Token 内存统计已重置");
    }

    // ==================== 记录器接口 ====================

    @FunctionalInterface
    public interface TokenUsageRecorder {
        void record(long promptTokens, long completionTokens, long totalTokens);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int order = 900;
        private TokenUsageService tokenUsageService;
        private TokenUsageRecorder recorder;

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder tokenUsageService(TokenUsageService tokenUsageService) {
            this.tokenUsageService = tokenUsageService;
            return this;
        }

        public Builder recorder(TokenUsageRecorder recorder) {
            this.recorder = recorder;
            return this;
        }

        public TokenUsageAdvisor build() {
            return new TokenUsageAdvisor(order, tokenUsageService, recorder);
        }
    }
}
