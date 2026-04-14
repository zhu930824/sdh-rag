package cn.sdh.backend.config;

import cn.sdh.backend.advisor.common.HotwordAdvisor;
import cn.sdh.backend.advisor.common.MySQLChatMemoryRepository;
import cn.sdh.backend.advisor.common.TokenUsageAdvisor;
import cn.sdh.backend.service.HotwordService;
import cn.sdh.backend.service.TokenUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置类
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    private final RagConfig ragConfig;

    /**
     * 配置 ChatMemory Bean
     * 使用 MySQL 存储对话记忆
     */
    @Bean
    public ChatMemory chatMemory(MySQLChatMemoryRepository repository) {
        log.info("初始化 ChatMemory, 窗口大小: {}", ragConfig.getMemoryWindowSize());
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .build();
    }

    /**
     * 配置 ChatClient.Builder Bean
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel dashscopeChatModel) {
        log.info("初始化 ChatClient.Builder");
        return ChatClient.builder(dashscopeChatModel);
    }

    /**
     * 配置 TokenUsageAdvisor Bean
     *
     * <p>利用 Spring AI 的 Usage 接口统计每次对话的 token 使用量。
     * Spring AI 通过 Usage 接口的 getNativeUsage() 方法和 DefaultUsage 实现，
     * 简化了不同 AI 模型跟踪和报告用量指标的流程。</p>
     */
    @Bean
    public TokenUsageAdvisor tokenUsageAdvisor(TokenUsageService tokenUsageService) {
        log.info("初始化 TokenUsageAdvisor");
        return TokenUsageAdvisor.builder()
                .order(900)
                .tokenUsageService(tokenUsageService)
                .build();
    }

    /**
     * 配置 HotwordAdvisor Bean
     *
     * <p>使用 IK 分词器从用户问题中提取关键词，并记录到热点词统计中。</p>
     */
    @Bean
    public HotwordAdvisor hotwordAdvisor(HotwordService hotwordService) {
        log.info("初始化 HotwordAdvisor");
        return HotwordAdvisor.builder()
                .order(800)
                .hotwordService(hotwordService)
                .maxKeywords(10)
                .minWordLength(2)
                .useSmartMode(true)
                .build();
    }

}
