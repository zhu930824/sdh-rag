package cn.sdh.backend.service.factory;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.ModelFactory;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Component;

/**
 * Embedding 模型工厂
 * 负责创建和管理 EmbeddingModel 实例
 */
@Slf4j
@Component
public class EmbeddingModelFactory extends ModelFactory<EmbeddingModel> {

    private static final String DEFAULT_MODEL = "text-embedding-v3";
    private static final String MODEL_TYPE = "embedding";

    // DashScope Embedding 专用 baseUrl（与 Chat 不同）
    private static final String DASHSCOPE_EMBEDDING_URL = "https://dashscope.aliyuncs.com";

    public EmbeddingModelFactory(ModelConfigMapper modelConfigMapper) {
        super(modelConfigMapper);
    }

    @Override
    protected EmbeddingModel createModel(ModelConfig config) {
        String provider = config.getProvider();
        log.info("创建 Embedding 模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        ModelProvider modelProvider = ModelProvider.fromString(provider);

        if (modelProvider == null) {
            log.warn("未知的模型提供者: {}, 使用默认配置", provider);
            return createDefaultModel(config.getModelId());
        }

        return switch (modelProvider) {
            case OPENAI -> createOpenAiEmbeddingModel(
                    resolveBaseUrl(config, ModelProvider.OPENAI),
                    config.getApiKey(),
                    config.getModelId()
            );
            case DASHSCOPE -> createDashScopeEmbeddingModel(
                    resolveDashScopeBaseUrl(config),
                    resolveApiKey(config, dashscopeApiKey),
                    config.getModelId()
            );
            case DEEPSEEK, MOONSHOT, SILICON, ZHIPU, BAICHUAN, MINIMAX, LOCAL, CUSTOM ->
                    createOpenAiEmbeddingModel(
                            resolveBaseUrl(config, modelProvider),
                            resolveApiKey(config, null),
                            config.getModelId()
                    );
        };
    }

    @Override
    protected EmbeddingModel createDefaultModel(String modelName) {
        log.info("使用默认配置创建 Embedding 模型: {}", modelName);

        // 规范化模型名称
        modelName = normalizeModelName(modelName);

        // 优先使用 DashScope
        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            return createDashScopeEmbeddingModel(
                    DASHSCOPE_EMBEDDING_URL,
                    dashscopeApiKey,
                    modelName != null ? modelName : DEFAULT_MODEL
            );
        }

        // 其次使用 OpenAI
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            return createOpenAiEmbeddingModel(
                    ModelProvider.OPENAI.getDefaultBaseUrl(),
                    openaiApiKey,
                    modelName != null ? modelName : "text-embedding-ada-002"
            );
        }

        log.error("没有可用的 API Key 配置，无法创建 Embedding 模型");
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

    @Override
    public EmbeddingModel getModel(String modelName) {
        // 模型名称兼容映射
        if (modelName != null) {
            modelName = normalizeModelName(modelName);
        }
        return super.getModel(modelName);
    }

    // ==================== 具体创建方法 ====================

    private EmbeddingModel createOpenAiEmbeddingModel(String baseUrl, String apiKey, String modelName) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(modelName)
                .build();

        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }

    private EmbeddingModel createDashScopeEmbeddingModel(String baseUrl, String apiKey, String modelName) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(modelName)
                .build();

        return new DashScopeEmbeddingModel(
                dashScopeApi,
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
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
     * DashScope Embedding 使用专用 URL
     */
    private String resolveDashScopeBaseUrl(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        return DASHSCOPE_EMBEDDING_URL;
    }

    /**
     * 解析 apiKey，优先使用配置中的，否则使用默认值
     */
    private String resolveApiKey(ModelConfig config, String defaultApiKey) {
        if (StringUtils.isNotBlank(config.getApiKey())) {
            return config.getApiKey();
        }else {
            return defaultApiKey;
        }
    }

    /**
     * 规范化模型名称，处理旧版本名称映射
     */
    private String normalizeModelName(String modelName) {
        if ("text-embedding-v4".equals(modelName)) {
            return "text-embedding-v3";
        }
        return modelName;
    }
}
