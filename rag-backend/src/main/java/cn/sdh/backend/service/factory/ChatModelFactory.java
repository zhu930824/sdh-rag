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

        ModelProvider modelProvider = ModelProvider.fromString(provider);

        if (modelProvider == null) {
            log.warn("未知的模型提供者: {}, 使用默认配置", provider);
            return createDefaultModel(config.getModelId());
        }

        return switch (modelProvider) {
            case OPENAI -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.OPENAI),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
            case DASHSCOPE -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.DASHSCOPE),
                    resolveApiKey(config, dashscopeApiKey),
                    config.getModelId(),
                    config
            );
            case DEEPSEEK -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.DEEPSEEK),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
            case MOONSHOT -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.MOONSHOT),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
            case SILICON -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.SILICON),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
            case ZHIPU -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.ZHIPU),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
            case BAICHUAN -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.BAICHUAN),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
            case MINIMAX -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.MINIMAX),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
            case LOCAL -> createOpenAiCompatibleModel(
                    resolveBaseUrl(config, ModelProvider.LOCAL),
                    config.getApiKey() != null ? config.getApiKey() : "ollama",
                    config.getModelId(),
                    config
            );
            case CUSTOM -> createOpenAiCompatibleModel(
                    config.getBaseUrl(),
                    config.getApiKey(),
                    config.getModelId(),
                    config
            );
        };
    }

    @Override
    protected ChatModel createDefaultModel(String modelName) {
        log.info("使用默认配置创建 Chat 模型: {}", modelName);

        // 优先使用 DashScope
        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            return createOpenAiCompatibleModel(
                    ModelProvider.DASHSCOPE.getDefaultBaseUrl(),
                    dashscopeApiKey,
                    modelName != null ? modelName : DEFAULT_MODEL
            );
        }

        // 其次使用 OpenAI
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            return createOpenAiCompatibleModel(
                    ModelProvider.OPENAI.getDefaultBaseUrl(),
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

    // ==================== 工具方法 ====================

    /**
     * 解析 baseUrl，优先使用配置中的，否则使用默认值
     */
    private String resolveBaseUrl(ModelConfig config, ModelProvider provider) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        return provider.getDefaultBaseUrl();
    }

    /**
     * 解析 apiKey，优先使用配置中的，否则使用默认值
     */
    private String resolveApiKey(ModelConfig config, String defaultApiKey) {
        return config.getApiKey() != null ? config.getApiKey() : defaultApiKey;
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
