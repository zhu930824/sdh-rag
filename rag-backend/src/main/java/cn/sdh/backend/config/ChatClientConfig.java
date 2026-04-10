package cn.sdh.backend.config;

import cn.sdh.backend.rag.MySQLChatMemoryRepository;
import cn.sdh.backend.rag.TokenUsageAdvisor;
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
     * 用于统计和持久化 token 消耗
     */
    @Bean
    public TokenUsageAdvisor tokenUsageAdvisor(TokenUsageService tokenUsageService) {
        log.info("初始化 TokenUsageAdvisor");
        return TokenUsageAdvisor.builder()
                .order(900)
                .tokenUsageService(tokenUsageService)
                .build();
    }
}
