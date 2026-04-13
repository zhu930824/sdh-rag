package cn.sdh.backend.service.factory.strategy;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.factory.ModelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 抽象 Chat 模型创建策略基类
 * 提供 OpenAI 兼容模型创建的通用方法
 */
@Slf4j
public abstract class AbstractChatModelStrategy implements ModelCreationStrategy<ChatModel> {

    @Override
    public ChatModel createModel(ModelConfig config, String defaultApiKey) {
        String baseUrl = resolveBaseUrl(config);
        String apiKey = resolveApiKey(config, defaultApiKey);
        String modelName = config.getModelId();

        log.info("创建 Chat 模型: provider={}, baseUrl={}, model={}", getProvider(), baseUrl, modelName);

        return doCreateModel(baseUrl, apiKey, modelName, config);
    }

    @Override
    public ChatModel createDefaultModel(String modelName, String apiKey) {
        log.info("使用默认配置创建 Chat 模型: provider={}, model={}", getProvider(), modelName);
        return doCreateModel(getProvider().getDefaultBaseUrl(), apiKey, modelName, null);
    }

    /**
     * 子类实现具体的模型创建逻辑
     */
    protected ChatModel doCreateModel(String baseUrl, String apiKey, String modelName, ModelConfig config) {
        // 默认使用 OpenAI 兼容方式创建
        return createOpenAiCompatibleModel(baseUrl, apiKey, modelName, config);
    }

    /**
     * 创建 OpenAI 兼容的 Chat 模型
     */
    protected ChatModel createOpenAiCompatibleModel(String baseUrl, String apiKey, String modelName, ModelConfig config) {
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

    /**
     * 解析 baseUrl，优先使用配置中的，否则使用默认值
     */
    protected String resolveBaseUrl(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        return getProvider().getDefaultBaseUrl();
    }

    /**
     * 解析 apiKey，优先使用配置中的，否则使用默认值
     */
    protected String resolveApiKey(ModelConfig config, String defaultApiKey) {
        return config.getApiKey() != null && !config.getApiKey().isEmpty()
                ? config.getApiKey()
                : defaultApiKey;
    }
}