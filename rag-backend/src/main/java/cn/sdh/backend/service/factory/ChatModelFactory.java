package cn.sdh.backend.service.factory;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.ModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * Chat 模型工厂
 * 负责创建和管理 ChatModel 实例
 */
@Slf4j
@Component
public class ChatModelFactory extends ModelFactory<ChatModel> {

    private static final String DEFAULT_MODEL = "qwen-turbo";
    private static final String MODEL_TYPE = "chat";

    public ChatModelFactory(ModelConfigMapper modelConfigMapper) {
        super(modelConfigMapper);
    }

    @Override
    protected ChatModel createModel(ModelConfig config) {
        String provider = config.getProvider();
        log.info("创建 Chat 模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        String providerLower = provider.toLowerCase();
        switch (providerLower) {
            case "openai":
                return createOpenAiChatModel(config);
            case "dashscope":
            case "alibaba":
            case "qwen":
                return createDashScopeChatModel(config);
            case "deepseek":
                return createDeepSeekChatModel(config);
            case "moonshot":
                return createMoonshotChatModel(config);
            case "silicon":
            case "siliconflow":
                return createSiliconChatModel(config);
            default:
                log.warn("未知的模型提供者: {}, 使用默认配置", provider);
                return createDefaultModel(config.getModelId());
        }
    }

    @Override
    protected ChatModel createDefaultModel(String modelName) {
        log.info("使用默认配置创建 Chat 模型: {}", modelName);

        // 优先使用 DashScope
        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            return createOpenAiCompatibleModel(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    dashscopeApiKey,
                    modelName != null ? modelName : DEFAULT_MODEL
            );
        }

        // 其次使用 OpenAI
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            return createOpenAiCompatibleModel(
                    "https://api.openai.com",
                    openaiApiKey,
                    modelName != null ? modelName : "gpt-3.5-turbo"
            );
        }

        log.error("没有可用的 API Key 配置，无法创建 Chat 模型");
        return null;
    }

    @Override
    protected String getDefaultModelName() {
        return DEFAULT_MODEL;
    }

    @Override
    protected String getModelType() {
        return MODEL_TYPE;
    }

    // ==================== 具体创建方法 ====================

    private ChatModel createOpenAiChatModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }
        return createOpenAiCompatibleModel(baseUrl, config.getApiKey(), config.getModelId(), config);
    }

    private ChatModel createDashScopeChatModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        String apiKey = config.getApiKey() != null ? config.getApiKey() : dashscopeApiKey;
        return createOpenAiCompatibleModel(baseUrl, apiKey, config.getModelId(), config);
    }

    private ChatModel createDeepSeekChatModel(ModelConfig config) {
        return createOpenAiCompatibleModel("https://api.deepseek.com", config.getApiKey(), config.getModelId(), config);
    }

    private ChatModel createMoonshotChatModel(ModelConfig config) {
        return createOpenAiCompatibleModel("https://api.moonshot.cn/v1", config.getApiKey(), config.getModelId(), config);
    }

    private ChatModel createSiliconChatModel(ModelConfig config) {
        return createOpenAiCompatibleModel("https://api.siliconflow.cn/v1", config.getApiKey(), config.getModelId(), config);
    }

    /**
     * 创建 OpenAI 兼容的 Chat 模型
     */
    private ChatModel createOpenAiCompatibleModel(String baseUrl, String apiKey, String modelName) {
        return createOpenAiCompatibleModel(baseUrl, apiKey, modelName, null);
    }

    private ChatModel createOpenAiCompatibleModel(String baseUrl, String apiKey, String modelName, ModelConfig config) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(modelName);

        if (config != null) {
            if (config.getTemperature() != null) {
                optionsBuilder.temperature(config.getTemperature());
            }
            if (config.getMaxTokens() != null) {
                optionsBuilder.maxTokens(config.getMaxTokens());
            }
        }

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }
}
