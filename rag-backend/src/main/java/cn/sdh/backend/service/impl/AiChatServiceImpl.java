package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.AiChatService;
import cn.sdh.backend.service.ModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI聊天服务实现
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    @Autowired
    private ModelConfigService modelConfigService;

    // 默认使用DashScope（通义千问）
    @Autowired(required = false)
    private ChatModel dashscopeChatModel;


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
        ModelConfig config = null;

        // 如果指定了模型ID，从数据库获取配置
        if (modelId != null && !modelId.isEmpty()) {
            try {
                config = modelConfigService.getById(Long.parseLong(modelId));
            } catch (NumberFormatException e) {
                log.warn("无效的模型ID: {}", modelId);
            }
        }

        // 如果没有指定，获取默认模型
        if (config == null) {
            config = modelConfigService.getDefault();
        }

        // 如果数据库也没有配置，使用DashScope默认配置
        if (config == null) {
            log.info("使用默认DashScope模型");
            return dashscopeChatModel;
        }

        // 根据提供商创建对应的ChatModel
        return createChatModel(config);
    }

    /**
     * 根据配置创建ChatModel
     */
    private ChatModel createChatModel(ModelConfig config) {
        String provider = config.getProvider();
        String apiKey = config.getApiKey();
        String baseUrl = config.getBaseUrl();
        String modelName = config.getModelId();
        Double temperature = config.getTemperature();
        Integer maxTokens = config.getMaxTokens();

        // 对于本地模型或自定义配置，使用OpenAI兼容接口
        OpenAiApi openAiApi;

        if (baseUrl != null && !baseUrl.isEmpty()) {
            openAiApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey != null ? apiKey : "no-key")
                    .build();
        } else {
            // 使用提供商的默认配置
            switch (provider.toLowerCase()) {
                case "openai":
                    openAiApi = OpenAiApi.builder()
                            .apiKey(apiKey)
                            .build();
                    break;
                case "deepseek":
                    openAiApi = OpenAiApi.builder()
                            .baseUrl("https://api.deepseek.com")
                            .apiKey(apiKey)
                            .build();
                    break;
                case "moonshot":
                    openAiApi = OpenAiApi.builder()
                            .baseUrl("https://api.moonshot.cn/v1")
                            .apiKey(apiKey)
                            .build();
                    break;
                case "silicon":
                    openAiApi = OpenAiApi.builder()
                            .baseUrl("https://api.siliconflow.cn/v1")
                            .apiKey(apiKey)
                            .build();
                    break;
                case "alibaba":
                case "dashscope":
                case "qwen":
                    // 阿里云百炼使用DashScope
                    return dashscopeChatModel;
                default:
                    // 默认使用DashScope
                    if (dashscopeChatModel != null) {
                        return dashscopeChatModel;
                    }
                    // 如果没有DashScope，尝试使用OpenAI兼容接口
                    if (apiKey != null) {
                        openAiApi = OpenAiApi.builder()
                                .apiKey(apiKey)
                                .build();
                    } else {
                        log.error("无法创建模型，缺少API Key");
                        return null;
                    }
            }
        }

        // 构建ChatOptions
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(modelName);

        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
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
