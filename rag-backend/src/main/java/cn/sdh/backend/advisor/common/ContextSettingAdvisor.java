package cn.sdh.backend.advisor.common;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context 设置 Advisor
 *
 * <p>用于在 Advisor 链中传递上下文信息。解决了流式请求中线程切换导致 ThreadLocal 丢失的问题。</p>
 *
 * <p>使用方式：</p>
 * <pre>
 * ContextSettingAdvisor contextAdvisor = ContextSettingAdvisor.create()
 *     .set(TokenUsageAdvisor.USER_ID, userId)
 *     .set(TokenUsageAdvisor.SESSION_ID, sessionId)
 *     .set(TokenUsageAdvisor.MODEL_ID, modelId)
 *     .set(TokenUsageAdvisor.KNOWLEDGE_ID, knowledgeId);
 *
 * requestSpec = requestSpec.advisors(spec -> spec.advisors(contextAdvisor));
 * </pre>
 */
public class ContextSettingAdvisor implements BaseAdvisor {

    private final Map<String, Object> contextValues;
    private final int order;

    private ContextSettingAdvisor(Map<String, Object> contextValues, int order) {
        this.contextValues = contextValues;
        this.order = order;
    }

    /**
     * 创建一个新的 ContextSettingAdvisor
     */
    public static ContextSettingAdvisor create() {
        return new ContextSettingAdvisor(new ConcurrentHashMap<>(), Integer.MIN_VALUE);
    }

    /**
     * 设置一个 context 值
     */
    public ContextSettingAdvisor set(String key, Object value) {
        if (value != null) {
            contextValues.put(key, value);
        }
        return this;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 将所有 context 值添加到 request 中
        ChatClientRequest.Builder mutate = request.mutate();
        for (Map.Entry<String, Object> entry : contextValues.entrySet()) {
            mutate = mutate.context(entry.getKey(), entry.getValue());
        }
        return mutate.build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // 将所有 context 值添加到 request 中
        ChatClientRequest.Builder mutate = request.mutate();
        for (Map.Entry<String, Object> entry : contextValues.entrySet()) {
            mutate = mutate.context(entry.getKey(), entry.getValue());
        }
        return chain.nextStream(mutate.build());
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.immediate();
    }

    @Override
    public int getOrder() {
        return order;
    }
}
