package cn.sdh.backend.rag;

import cn.sdh.backend.service.SensitiveWordService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 敏感词检测 Advisor
 * 在用户输入被发送到模型之前检测敏感词
 */
@Slf4j
@Getter
public class SensitiveWordAdvisor implements BaseAdvisor {

    public static final String SENSITIVE_WORDS_FOUND = "sensitiveWordsFound";
    public static final String SENSITIVE_WORDS = "sensitiveWords";

    private final SensitiveWordService sensitiveWordService;
    private final int order;

    public SensitiveWordAdvisor(SensitiveWordService sensitiveWordService) {
        this(sensitiveWordService, 0);
    }

    public SensitiveWordAdvisor(SensitiveWordService sensitiveWordService, int order) {
        this.sensitiveWordService = sensitiveWordService;
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, org.springframework.ai.chat.client.advisor.api.AdvisorChain chain) {
        // 获取用户输入文本
        String userText = extractUserText(request);

        if (userText != null && !userText.isEmpty()) {
            // 检测敏感词
            List<String> sensitiveWords = sensitiveWordService.findSensitiveWords(userText);

            if (!sensitiveWords.isEmpty()) {
                log.warn("检测到敏感词: {}", sensitiveWords);
                // 将敏感词信息存储到请求上下文中
                return request.mutate()
                        .context(SENSITIVE_WORDS_FOUND, true)
                        .context(SENSITIVE_WORDS, sensitiveWords)
                        .build();
            }
        }

        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, org.springframework.ai.chat.client.advisor.api.AdvisorChain chain) {
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // 检查是否有敏感词
        Object found = request.context().get(SENSITIVE_WORDS_FOUND);
        Boolean hasSensitiveWords = found instanceof Boolean ? (Boolean) found : Boolean.FALSE;

        if (hasSensitiveWords) {
            @SuppressWarnings("unchecked")
            List<String> sensitiveWords = (List<String>) request.context().get(SENSITIVE_WORDS);
            String errorMessage = "您的问题包含敏感词，请修改后重试";
            log.warn("拦截包含敏感词的请求: {}", sensitiveWords);

            // 返回错误响应
            return Flux.error(new SensitiveWordException(errorMessage, sensitiveWords));
        }
        return chain.nextStream( request);
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.boundedElastic();
    }

    @Override
    public int getOrder() {
        return order;
    }

    /**
     * 从请求中提取用户输入文本
     */
    private String extractUserText(ChatClientRequest request) {
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

    /**
     * 敏感词异常
     */
    public static class SensitiveWordException extends RuntimeException {
        private final List<String> sensitiveWords;

        public SensitiveWordException(String message, List<String> sensitiveWords) {
            super(message);
            this.sensitiveWords = sensitiveWords;
        }

        public List<String> getSensitiveWords() {
            return sensitiveWords;
        }
    }

    /**
     * Builder 模式
     */
    public static Builder builder(SensitiveWordService sensitiveWordService) {
        return new Builder(sensitiveWordService);
    }

    public static class Builder {
        private final SensitiveWordService sensitiveWordService;
        private int order = 0;

        public Builder(SensitiveWordService sensitiveWordService) {
            this.sensitiveWordService = sensitiveWordService;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public SensitiveWordAdvisor build() {
            return new SensitiveWordAdvisor(sensitiveWordService, order);
        }
    }
}
