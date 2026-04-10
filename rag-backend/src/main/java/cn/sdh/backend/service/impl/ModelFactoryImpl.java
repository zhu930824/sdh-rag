package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.ModelConfigService;
import cn.sdh.backend.service.ModelFactory;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import com.alibaba.cloud.ai.model.RerankModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型工厂实现
 * 统一管理 Chat、Embedding、Rerank 模型的创建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelFactoryImpl implements ModelFactory {

    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    // 缓存已创建的模型客户端
    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    private final Map<String, RerankModel> rerankModelCache = new ConcurrentHashMap<>();

    // 默认模型名称
    private static final String DEFAULT_CHAT_MODEL = "qwen-turbo";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v3";
    private static final String DEFAULT_RERANK_MODEL = "qwen3-rerank";

    // ==================== Chat 模型 ====================

    @Override
    public ChatModel getChatModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            modelName = DEFAULT_CHAT_MODEL;
        }

        final String finalModelName = modelName;
        String cacheKey = "chat:" + modelName;
        return chatModelCache.computeIfAbsent(cacheKey, key -> {
            ModelConfig config = findModelConfigByName(finalModelName, "chat");
            if (config != null) {
                return createChatModel(config);
            }
            return createDefaultChatModel(finalModelName);
        });
    }

    @Override
    public ChatModel getChatModelById(Long modelId) {
        String cacheKey = "chat:id:" + modelId;
        return chatModelCache.computeIfAbsent(cacheKey, key -> {
            ModelConfig config = modelConfigService.getById(modelId);
            if (config != null) {
                return createChatModel(config);
            }
            return null;
        });
    }

    private ChatModel createChatModel(ModelConfig config) {
        String provider = config.getProvider();
        log.info("创建 Chat 模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        String providerLower = provider.toLowerCase();
        if ("openai".equals(providerLower)) {
            return createOpenAiChatModel(config);
        } else if ("dashscope".equals(providerLower) || "alibaba".equals(providerLower) || "qwen".equals(providerLower)) {
            return createDashScopeChatModel(config);
        } else if ("deepseek".equals(providerLower)) {
            return createDeepSeekChatModel(config);
        } else if ("moonshot".equals(providerLower)) {
            return createMoonshotChatModel(config);
        } else if ("silicon".equals(providerLower) || "siliconflow".equals(providerLower)) {
            return createSiliconChatModel(config);
        } else {
            log.warn("未知的模型提供者: {}, 使用默认配置", provider);
            return createDefaultChatModel(config.getModelId());
        }
    }

    private ChatModel createOpenAiChatModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(config.getApiKey())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildChatOptions(config))
                .build();
    }

    private ChatModel createDashScopeChatModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }

        String apiKey = config.getApiKey() != null ? config.getApiKey() : dashscopeApiKey;

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildChatOptions(config))
                .build();
    }

    private ChatModel createDeepSeekChatModel(ModelConfig config) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(config.getApiKey())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildChatOptions(config))
                .build();
    }

    private ChatModel createMoonshotChatModel(ModelConfig config) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.moonshot.cn/v1")
                .apiKey(config.getApiKey())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildChatOptions(config))
                .build();
    }

    private ChatModel createSiliconChatModel(ModelConfig config) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey(config.getApiKey())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildChatOptions(config))
                .build();
    }

    private ChatModel createDefaultChatModel(String modelName) {
        log.info("使用默认配置创建 Chat 模型: {}", modelName);

        // 优先使用 DashScope
        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                    .apiKey(dashscopeApiKey)
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(modelName != null ? modelName : DEFAULT_CHAT_MODEL)
                            .build())
                    .build();
        }

        // 其次使用 OpenAI
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(openaiApiKey)
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(modelName != null ? modelName : "gpt-3.5-turbo")
                            .build())
                    .build();
        }

        log.error("没有可用的 API Key 配置，无法创建 Chat 模型");
        return null;
    }

    private OpenAiChatOptions buildChatOptions(ModelConfig config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(config.getModelId());

        if (config.getTemperature() != null) {
            builder.temperature(config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }

        return builder.build();
    }

    // ==================== Embedding 模型 ====================

    @Override
    public EmbeddingModel getEmbeddingModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            modelName = DEFAULT_EMBEDDING_MODEL;
        }

        // 模型名称兼容映射
        modelName = normalizeEmbeddingModelName(modelName);

        final String finalModelName = modelName;
        String cacheKey = "embedding:" + modelName;
        return embeddingModelCache.computeIfAbsent(cacheKey, key -> {
            ModelConfig config = findModelConfigByName(finalModelName, "embedding");
            if (config != null) {
                return createEmbeddingModel(config);
            }
            return createDefaultEmbeddingModel(finalModelName);
        });
    }

    @Override
    public EmbeddingModel getEmbeddingModelById(Long modelId) {
        String cacheKey = "embedding:id:" + modelId;
        return embeddingModelCache.computeIfAbsent(cacheKey, key -> {
            ModelConfig config = modelConfigService.getById(modelId);
            if (config != null) {
                return createEmbeddingModel(config);
            }
            return null;
        });
    }

    private String normalizeEmbeddingModelName(String modelName) {
        if ("text-embedding-v4".equals(modelName)) {
            return "text-embedding-v3";
        }
        return modelName;
    }

    private EmbeddingModel createEmbeddingModel(ModelConfig config) {
        String provider = config.getProvider();
        log.info("创建 Embedding 模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        String providerLower = provider.toLowerCase();
        if ("openai".equals(providerLower)) {
            return createOpenAiEmbeddingModel(config);
        } else if ("dashscope".equals(providerLower) || "alibaba".equals(providerLower) || "qwen".equals(providerLower)) {
            return createDashScopeEmbeddingModel(config);
        } else {
            log.warn("未知的模型提供者: {}, 使用默认配置", provider);
            return createDefaultEmbeddingModel(config.getModelId());
        }
    }

    private EmbeddingModel createOpenAiEmbeddingModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(config.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.getModelId())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    private EmbeddingModel createDashScopeEmbeddingModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://dashscope.aliyuncs.com";
        }

        String apiKey = config.getApiKey() != null ? config.getApiKey() : dashscopeApiKey;

        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(DashScopeApiSpec.EmbeddingRequest.builder()
                        .model(config.getModelId()).build().model())
                .build();

        return new DashScopeEmbeddingModel(
                dashScopeApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    private EmbeddingModel createDefaultEmbeddingModel(String modelName) {
        log.info("使用默认配置创建 Embedding 模型: {}", modelName);

        // 优先使用 DashScope
        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            DashScopeApi dashScopeApi = DashScopeApi.builder()
                    .baseUrl("https://dashscope.aliyuncs.com")
                    .apiKey(dashscopeApiKey)
                    .build();

            DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                    .model(DashScopeApiSpec.EmbeddingRequest.builder()
                            .model(DEFAULT_EMBEDDING_MODEL).build().model())
                    .build();

            return new DashScopeEmbeddingModel(
                    dashScopeApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
        }

        // 其次使用 OpenAI
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(openaiApiKey)
                    .build();

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(modelName != null ? modelName : "text-embedding-ada-002")
                    .build();

            return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
        }

        log.error("没有可用的 API Key 配置，无法创建 Embedding 模型");
        return null;
    }

    // ==================== Rerank 模型 ====================

    @Override
    public RerankModel getRerankModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            modelName = DEFAULT_RERANK_MODEL;
        }

        final String finalModelName = modelName;
        String cacheKey = "rerank:" + modelName;
        return rerankModelCache.computeIfAbsent(cacheKey, key -> {
            ModelConfig config = findModelConfigByName(finalModelName, "reranker");
            if (config != null) {
                return createRerankModel(config);
            }
            return createDefaultRerankModel(finalModelName);
        });
    }

    @Override
    public RerankModel getRerankModelById(Long modelId) {
        String cacheKey = "rerank:id:" + modelId;
        return rerankModelCache.computeIfAbsent(cacheKey, key -> {
            ModelConfig config = modelConfigService.getById(modelId);
            if (config != null) {
                return createRerankModel(config);
            }
            return null;
        });
    }

    private RerankModel createRerankModel(ModelConfig config) {
        String provider = config.getProvider();
        log.info("创建 Rerank 模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        // 目前主要支持 DashScope 的 Rerank 模型
        String apiKey = config.getApiKey() != null ? config.getApiKey() : dashscopeApiKey;
        String modelName = config.getModelId();

        return new DashScopeRerankModel(
                DashScopeApi.builder()
                        .baseUrl("https://dashscope.aliyuncs.com")
                        .apiKey(apiKey)
                        .build(),
                DashScopeRerankOptions.builder()
                        .model(modelName)
                        .build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }

    private RerankModel createDefaultRerankModel(String modelName) {
        log.info("使用默认配置创建 Rerank 模型: {}", modelName);

        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            // 目前主要支持 DashScope 的 Rerank 模型
            return new DashScopeRerankModel(
                    DashScopeApi.builder()
                            .baseUrl("https://dashscope.aliyuncs.com")
                            .apiKey(dashscopeApiKey)
                            .build(),
                    DashScopeRerankOptions.builder()
                            .model(modelName)
                            .build(),
                    RetryUtils.DEFAULT_RETRY_TEMPLATE
            );
        }

        log.error("没有可用的 API Key 配置，无法创建 Rerank 模型");
        return null;
    }



    // ==================== 工具方法 ====================

    @Override
    public ModelConfig getModelConfig(String modelName, String modelType) {
        return findModelConfigByName(modelName, modelType);
    }

    private ModelConfig findModelConfigByName(String modelName, String modelType) {
        // 优先匹配 modelId 字段
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getModelId, modelName)
                .eq(ModelConfig::getModelType, modelType)
                .eq(ModelConfig::getStatus, 1)
                .last("LIMIT 1");
        ModelConfig config = modelConfigMapper.selectOne(wrapper);
        if (config != null) {
            return config;
        }

        // 其次匹配 name 字段
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getName, modelName)
                .eq(ModelConfig::getModelType, modelType)
                .eq(ModelConfig::getStatus, 1)
                .last("LIMIT 1");
        return modelConfigMapper.selectOne(wrapper);
    }

    @Override
    public void clearCache() {
        chatModelCache.clear();
        embeddingModelCache.clear();
        rerankModelCache.clear();
        log.info("模型缓存已清除");
    }

    @Override
    public void clearCache(String modelType, String modelName) {
        String cacheKey = modelType + ":" + modelName;
        chatModelCache.remove(cacheKey);
        embeddingModelCache.remove(cacheKey);
        rerankModelCache.remove(cacheKey);
        log.info("已清除模型缓存: {}", cacheKey);
    }
}
