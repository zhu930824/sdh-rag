package cn.sdh.backend.service.factory;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.ModelFactory;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import lombok.extern.slf4j.Slf4j;
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

    public EmbeddingModelFactory(ModelConfigMapper modelConfigMapper) {
        super(modelConfigMapper);
    }

    @Override
    protected EmbeddingModel createModel(ModelConfig config) {
        String provider = config.getProvider();
        log.info("创建 Embedding 模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        String providerLower = provider.toLowerCase();
        switch (providerLower) {
            case "openai":
                return createOpenAiEmbeddingModel(config);
            case "dashscope":
            case "alibaba":
            case "qwen":
                return createDashScopeEmbeddingModel(config);
            default:
                log.warn("未知的模型提供者: {}, 使用默认配置", provider);
                return createDefaultModel(config.getModelId());
        }
    }

    @Override
    protected EmbeddingModel createDefaultModel(String modelName) {
        log.info("使用默认配置创建 Embedding 模型: {}", modelName);

        // 规范化模型名称
        modelName = normalizeModelName(modelName);

        // 优先使用 DashScope
        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            return createDashScopeEmbeddingModel(
                    "https://dashscope.aliyuncs.com",
                    dashscopeApiKey,
                    modelName != null ? modelName : DEFAULT_MODEL
            );
        }

        // 其次使用 OpenAI
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            return createOpenAiEmbeddingModel(
                    "https://api.openai.com",
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
    public M getModel(String modelName) {
        // 模型名称兼容映射
        if (modelName != null) {
            modelName = normalizeModelName(modelName);
        }
        return super.getModel(modelName);
    }

    // ==================== 具体创建方法 ====================

    private EmbeddingModel createOpenAiEmbeddingModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }
        return createOpenAiEmbeddingModel(baseUrl, config.getApiKey(), config.getModelId());
    }

    private EmbeddingModel createDashScopeEmbeddingModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://dashscope.aliyuncs.com";
        }
        String apiKey = config.getApiKey() != null ? config.getApiKey() : dashscopeApiKey;
        return createDashScopeEmbeddingModel(baseUrl, apiKey, config.getModelId());
    }

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
                .model(DashScopeApiSpec.EmbeddingRequest.builder()
                        .model(modelName).build().model())
                .build();

        return new DashScopeEmbeddingModel(
                dashScopeApi,
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
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
