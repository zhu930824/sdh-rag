package cn.sdh.backend.advisor.common;

import cn.sdh.backend.entity.TokenUsage;
import cn.sdh.backend.service.TokenUsageService;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型调用观测处理器
 * 拦截 Spring AI 的观测事件，记录 token 使用和性能数据
 */
@Slf4j
@Component
public class AIModelObservationHandler implements ObservationHandler<Observation.Context> {

    private final TokenUsageService tokenUsageService;

    // 存储观测上下文
    private final Map<String, ObservationContext> contextMap = new ConcurrentHashMap<>();

    public AIModelObservationHandler(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    @Override
    public void onStart(Observation.Context context) {
        String observationId = getObservationId(context);
        ObservationContext obsContext = new ObservationContext();
        obsContext.setStartTime(System.currentTimeMillis());
        obsContext.setRequestId(observationId);
        obsContext.setContext(context);

        // 从上下文获取额外信息
        extractContextInfo(context, obsContext);

        contextMap.put(observationId, obsContext);
        log.debug("[Observation] AI 调用开始: {}", observationId);
    }

    @Override
    public void onStop(Observation.Context context) {
        String observationId = getObservationId(context);
        ObservationContext obsContext = contextMap.remove(observationId);

        if (obsContext == null) {
            return;
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - obsContext.getStartTime();

        // 提取响应信息
        extractResponseInfo(context, obsContext);

        // 记录 token 使用
        if (obsContext.getTotalTokens() > 0) {
            TokenUsage usage = new TokenUsage();
            usage.setUserId(obsContext.getUserId());
            usage.setSessionId(obsContext.getSessionId());
            usage.setModelName(obsContext.getModelName());
            usage.setProvider(obsContext.getProvider());
            usage.setPromptTokens(obsContext.getPromptTokens());
            usage.setCompletionTokens(obsContext.getCompletionTokens());
            usage.setTotalTokens(obsContext.getTotalTokens());
            usage.setDurationMs(duration);
            usage.setStatus("success");
            usage.setRequestId(observationId);
            usage.setKnowledgeId(obsContext.getKnowledgeId());
            usage.setCreateTime(LocalDateTime.now());

            tokenUsageService.saveAsync(usage);

            log.info("[Observation] AI 调用完成: {}, 耗时: {}ms, tokens: {}",
                    observationId, duration, obsContext.getTotalTokens());
        } else {
            log.debug("[Observation] AI 调用完成: {}, 耗时: {}ms, 无 token 信息",
                    observationId, duration);
        }
    }

    @Override
    public void onError(Observation.Context context) {
        String observationId = getObservationId(context);
        ObservationContext obsContext = contextMap.remove(observationId);

        if (obsContext != null) {
            long duration = System.currentTimeMillis() - obsContext.getStartTime();
            Throwable error = context.getError();

            TokenUsage usage = new TokenUsage();
            usage.setUserId(obsContext.getUserId());
            usage.setSessionId(obsContext.getSessionId());
            usage.setModelName(obsContext.getModelName());
            usage.setProvider(obsContext.getProvider());
            usage.setPromptTokens(0L);
            usage.setCompletionTokens(0L);
            usage.setTotalTokens(0L);
            usage.setDurationMs(duration);
            usage.setStatus("failed");
            usage.setErrorMessage(error != null ? error.getMessage() : "Unknown error");
            usage.setRequestId(observationId);
            usage.setCreateTime(LocalDateTime.now());

            tokenUsageService.saveAsync(usage);

            log.error("[Observation] AI 调用失败: {}, 耗时: {}ms, 错误: {}",
                    observationId, duration, error != null ? error.getMessage() : "Unknown");
        }
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        // 只处理 Spring AI 相关的观测
        String name = context.getName();
        return name != null && (
                name.contains("chat") ||
                name.contains("embedding") ||
                name.contains("spring.ai") ||
                name.contains("model")
        );
    }

    // ==================== 私有方法 ====================

    private String getObservationId(Observation.Context context) {
        return context.getName() + "-" + System.identityHashCode(context);
    }

    private void extractContextInfo(Observation.Context context, ObservationContext obsContext) {
        // 从 KeyValues 中提取信息
        Iterable<KeyValue> keyValues = context.getHighCardinalityKeyValues();
        if (keyValues != null) {
            for (KeyValue kv : keyValues) {
                String key = kv.getKey();
                String value = kv.getValue();

                if ("spring.ai.model".equals(key) && value != null) {
                    obsContext.setModelName(value);
                } else if ("spring.ai.provider".equals(key) && value != null) {
                    obsContext.setProvider(value);
                }
            }
        }
    }

    private void extractResponseInfo(Observation.Context context, ObservationContext obsContext) {
        // 尝试从上下文获取 ChatResponse
        Object response = context.get("response");
        if (response instanceof ChatResponse) {
            ChatResponse chatResponse = (ChatResponse) response;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                Usage usage = chatResponse.getMetadata().getUsage();
                obsContext.setPromptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0);
                obsContext.setCompletionTokens(usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0);
                obsContext.setTotalTokens(obsContext.getPromptTokens() + obsContext.getCompletionTokens());
            }

            // 尝试获取模型信息
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getModel() != null) {
                obsContext.setModelName(chatResponse.getMetadata().getModel());
            }
        }
    }

    /**
     * 观测上下文
     */
    private static class ObservationContext {
        private long startTime;
        private String requestId;
        private Observation.Context context;
        private Long userId;
        private String sessionId;
        private String modelName;
        private String provider;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private Long knowledgeId;

        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public Observation.Context getContext() { return context; }
        public void setContext(Observation.Context context) { this.context = context; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public long getPromptTokens() { return promptTokens; }
        public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }
        public long getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }
        public long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
        public Long getKnowledgeId() { return knowledgeId; }
        public void setKnowledgeId(Long knowledgeId) { this.knowledgeId = knowledgeId; }
    }
}
