package cn.sdh.backend.rag;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 日志观测 Advisor
 * 记录聊天请求和响应的详细信息，用于监控和调试
 */
@Slf4j
@Getter
public class LoggingAdvisor implements BaseAdvisor {

    public static final String START_TIME = "loggingStartTime";
    public static final String REQUEST_ID = "loggingRequestId";

    private final int order;
    private final boolean logRequestBody;
    private final boolean logResponseBody;
    private final int maxContentLength;

    public LoggingAdvisor() {
        this(1000, true, false, 200);
    }

    public LoggingAdvisor(int order, boolean logRequestBody, boolean logResponseBody, int maxContentLength) {
        this.order = order;
        this.logRequestBody = logRequestBody;
        this.logResponseBody = logResponseBody;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, org.springframework.ai.chat.client.advisor.api.AdvisorChain chain) {
        String requestId = generateRequestId();
        Instant startTime = Instant.now();

        // 记录请求信息
        if (log.isInfoEnabled()) {
            logRequest(request, requestId);
        }

        return request.mutate()
                .context(START_TIME, startTime)
                .context(REQUEST_ID, requestId)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, org.springframework.ai.chat.client.advisor.api.AdvisorChain chain) {
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Instant startTime = request.context().get(START_TIME, Instant.class);
        String requestId = request.context().get(REQUEST_ID, String.class);

        // 用于收集响应统计
        AtomicLong tokenCount = new AtomicLong(0);
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicReference<StringBuilder> contentBuilder = new AtomicReference<>(new StringBuilder());

        return chain.nextStream(request)
                .doOnNext(response -> {
                    chunkCount.incrementAndGet();

                    // 统计 token
                    if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
                        ChatResponse chatResponse = response.chatResponse();
                        Generation result = chatResponse.getResult();

                        // 收集内容
                        String content = result.getOutput().getText();
                        if (content != null) {
                            contentBuilder.get().append(content);
                        }

                        // 统计 token
                        if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                            tokenCount.addAndGet(chatResponse.getMetadata().getUsage().getTotalTokens().longValue());
                        }
                    }
                })
                .doOnComplete(() -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    String content = contentBuilder.get().toString();

                    log.info("[{}] 聊天请求完成 - 耗时: {}ms, chunks: {}, tokens: {}, 内容长度: {}",
                            requestId,
                            duration.toMillis(),
                            chunkCount.get(),
                            tokenCount.get(),
                            content.length());

                    // 记录响应内容摘要
                    if (logResponseBody && log.isDebugEnabled()) {
                        log.debug("[{}] 响应内容: {}", requestId, truncate(content, maxContentLength));
                    }
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("[{}] 聊天请求失败 - 耗时: {}ms, 错误: {}",
                            requestId, duration.toMillis(), error.getMessage());
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

    // ==================== 私有方法 ====================

    private String generateRequestId() {
        return Long.toHexString(System.currentTimeMillis()) + "-" +
               Integer.toHexString((int) (Math.random() * 0xFFFF));
    }

    private void logRequest(ChatClientRequest request, String requestId) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(requestId).append("] 聊天请求开始");

        if (request.prompt() != null) {
            // 系统提示词
            String systemPrompt = extractSystemPrompt(request);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                sb.append("\n  系统提示: ").append(truncate(systemPrompt, maxContentLength));
            }

            // 用户问题
            String userMessage = extractUserMessage(request);
            if (userMessage != null && !userMessage.isEmpty()) {
                sb.append("\n  用户问题: ").append(truncate(userMessage, maxContentLength));
            }
        }

        log.info(sb.toString());
    }

    private String extractSystemPrompt(ChatClientRequest request) {
        if (request.prompt() != null && request.prompt().getInstructions() != null) {
            return request.prompt().getInstructions().stream()
                    .filter(msg -> msg instanceof SystemMessage)
                    .map(msg -> ((SystemMessage) msg).getText())
                    .filter(text -> text != null && !text.isEmpty())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String extractUserMessage(ChatClientRequest request) {
        if (request.prompt() != null && request.prompt().getUserMessages() != null) {
            return request.prompt().getUserMessages().stream()
                    .filter(msg -> msg instanceof UserMessage)
                    .map(msg -> ((UserMessage) msg).getText())
                    .filter(text -> text != null && !text.isEmpty())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...(truncated)";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int order = 1000;
        private boolean logRequestBody = true;
        private boolean logResponseBody = false;
        private int maxContentLength = 200;

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder logRequestBody(boolean logRequestBody) {
            this.logRequestBody = logRequestBody;
            return this;
        }

        public Builder logResponseBody(boolean logResponseBody) {
            this.logResponseBody = logResponseBody;
            return this;
        }

        public Builder maxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
            return this;
        }

        public LoggingAdvisor build() {
            return new LoggingAdvisor(order, logRequestBody, logResponseBody, maxContentLength);
        }
    }
}
