package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.AiChatService;
import cn.sdh.backend.service.factory.ChatModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI聊天服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final ChatModelFactory chatModelFactory;


    @Override
    public Flux<String> streamChat(String prompt, String modelId) {
        return streamChat("", prompt, modelId);
    }

    @Override
    public Flux<String> streamChat(String systemPrompt, String userPrompt, String modelId) {
        try {
            ChatModel chatModel = getChatModel(modelId);
            if (chatModel == null) {
                log.error("无法获取聊天模型，modelId: {}", modelId);
                return Flux.just("{\"type\":\"error\",\"message\":\"模型配置错误，请检查模型设置\"}");
            }

            // 构建消息
            Prompt prompt;
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                prompt = new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt)
                ));
            } else {
                prompt = new Prompt(userPrompt);
            }

            // 流式调用
            return chatModel.stream(prompt)
                    .map(this::extractContent)
                    .filter(content -> content != null && !content.isEmpty())
                    .map(content -> "{\"type\":\"content\",\"content\":\"" + escapeJson(content) + "\"}")
                    .concatWith(Flux.just("{\"type\":\"done\"}"))
                    .onErrorResume(e -> {
                        log.error("AI调用失败", e);
                        return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                    });

        } catch (Exception e) {
            log.error("创建聊天模型失败", e);
            return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    public String chat(String prompt, String modelId) {
        try {
            ChatModel chatModel = getChatModel(modelId);
            if (chatModel == null) {
                return "模型配置错误，请检查模型设置";
            }

            Prompt p = new Prompt(prompt);
            ChatResponse response = chatModel.call(p);
            return extractContent(response);
        } catch (Exception e) {
            log.error("AI调用失败", e);
            return "AI调用失败: " + e.getMessage();
        }
    }

    /**
     * 获取聊天模型
     */
    private ChatModel getChatModel(String modelId) {
        if (modelId != null && !modelId.isEmpty()) {
            try {
                return chatModelFactory.getModelById(Long.parseLong(modelId));
            } catch (NumberFormatException e) {
                log.warn("无效的模型ID: {}", modelId);
            }
        }
        return chatModelFactory.getModel(null);
    }

    /**
     * 从响应中提取内容
     */
    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 使用 ChatClient 进行流式对话
     * 新增方法，支持 ChatClient 的 Advisor 机制
     *
     * @param chatClient ChatClient 实例
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户问题
     * @return 流式响应（JSON格式）
     */
    public Flux<String> streamChat(ChatClient chatClient, String systemPrompt, String userPrompt) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .chatResponse()
                    .map(this::extractContent)
                    .filter(content -> content != null && !content.isEmpty())
                    .map(content -> "{\"type\":\"content\",\"content\":\"" + escapeJson(content) + "\"}")
                    .concatWith(Flux.just("{\"type\":\"done\"}"))
                    .onErrorResume(e -> {
                        log.error("AI调用失败", e);
                        return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                    });
        } catch (Exception e) {
            log.error("创建 ChatClient 调用失败", e);
            return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }


    /**
     * 从 Message 中提取内容
     */
    private String extractContentFromMessage(org.springframework.ai.chat.messages.Message message) {
        if (message == null) {
            return null;
        }
        if (message instanceof AssistantMessage) {
            return ((AssistantMessage) message).getText();
        }
        return message.getText();
    }

    /**
     * 转义JSON字符串
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
