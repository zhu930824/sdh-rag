package cn.sdh.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootApplication(exclude = {
        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class
})
@MapperScan("cn.sdh.backend.mapper")
public class RagBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagBackendApplication.class, args);
    }

    @Bean
    @Primary
    public ChatModel defaultChatModel(ChatModel dashscopeChatModel) {
        return dashscopeChatModel;
    }

}
