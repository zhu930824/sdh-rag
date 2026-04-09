package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嵌入模型工厂
 * 根据模型配置动态创建 EmbeddingModel 实例
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingModelFactory {

    private final ModelConfigService modelConfigService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    // 缓存已创建的 EmbeddingModel 实例
    private final Map<String, EmbeddingModel> modelCache = new ConcurrentHashMap<>();

    // 默认嵌入模型名称 (DashScope 支持的模型)
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v3";

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    /**
     * 根据 embeddingModel 名称获取 EmbeddingModel 实例
     * @param embeddingModelName 嵌入模型名称（如 text-embedding-v3, text-embedding-ada-002 等）
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel getEmbeddingModel(String embeddingModelName) {
        if (embeddingModelName == null || embeddingModelName.isEmpty()) {
            embeddingModelName = DEFAULT_EMBEDDING_MODEL;
        }

        // 模型名称兼容映射（处理旧版本模型名称）
        embeddingModelName = normalizeModelName(embeddingModelName);

        // 检查缓存
        String cacheKey = embeddingModelName;
        if (modelCache.containsKey(cacheKey)) {
            return modelCache.get(cacheKey);
        }

        // 根据模型名称查找对应的模型配置
        ModelConfig config = findModelConfigByName(embeddingModelName);

        EmbeddingModel embeddingModel;
        if (config != null) {
            embeddingModel = createEmbeddingModel(config);
        } else {
            // 使用默认配置创建
            embeddingModel = createDefaultEmbeddingModel(embeddingModelName);
        }

        // 缓存模型实例
        modelCache.put(cacheKey, embeddingModel);

        return embeddingModel;
    }

    /**
     * 规范化模型名称，处理旧版本名称映射
     */
    private String normalizeModelName(String modelName) {
        // DashScope 嵌入模型名称映射
        // text-embedding-v4 不存在，映射到 text-embedding-v3
        if ("text-embedding-v4".equals(modelName)) {
            return "text-embedding-v3";
        }
        return modelName;
    }

    /**
     * 根据知识库ID获取对应的嵌入模型
     * @param knowledgeId 知识库ID
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel getEmbeddingModelByKnowledgeId(Long knowledgeId) {
        // 获取知识库配置
        cn.sdh.backend.entity.KnowledgeBase knowledgeBase = getKnowledgeBaseById(knowledgeId);

        if (knowledgeBase != null && knowledgeBase.getEmbeddingModel() != null) {
            return getEmbeddingModel(knowledgeBase.getEmbeddingModel());
        }

        // 返回默认模型
        return getEmbeddingModel(DEFAULT_EMBEDDING_MODEL);
    }

    /**
     * 清除模型缓存
     */
    public void clearCache() {
        modelCache.clear();
        log.info("嵌入模型缓存已清除");
    }

    /**
     * 清除指定模型的缓存
     */
    public void clearCache(String embeddingModelName) {
        modelCache.remove(embeddingModelName);
        log.info("已清除嵌入模型缓存: {}", embeddingModelName);
    }

    /**
     * 根据模型名称查找配置
     */
    private ModelConfig findModelConfigByName(String modelName) {
        // 尝试从数据库中查找匹配的模型配置
        // 优先匹配 modelId 字段
        for (ModelConfig config : modelConfigService.getActiveList()) {
            if (modelName.equals(config.getModelId())) {
                return config;
            }
        }

        // 其次匹配 name 字段
        for (ModelConfig config : modelConfigService.getActiveList()) {
            if (modelName.equals(config.getName())) {
                return config;
            }
        }

        return null;
    }

    /**
     * 根据配置创建 EmbeddingModel
     */
    private EmbeddingModel createEmbeddingModel(ModelConfig config) {
        String provider = config.getProvider();

        log.info("创建嵌入模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        switch (provider.toLowerCase()) {
            case "openai":
                return createOpenAiEmbeddingModel(config);
            case "dashscope":
            case "alibaba":
                return createDashScopeEmbeddingModel(config);
            default:
                log.warn("未知的模型提供者: {}, 使用默认配置", provider);
                return createDefaultEmbeddingModel(config.getModelId());
        }
    }

    /**
     * 创建 OpenAI 嵌入模型
     */
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

        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    /**
     * 创建 DashScope 嵌入模型
     * 注：DashScope 使用 OpenAI 兼容接口
     */
    private EmbeddingModel createDashScopeEmbeddingModel(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://dashscope.aliyuncs.com";
        }

        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(baseUrl)
                .apiKey(config.getApiKey())
                .build();

        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(DashScopeApiSpec.EmbeddingRequest.builder()
                        .model(config.getModelId()).build().model()
                ).build();


        return new DashScopeEmbeddingModel(
                dashScopeApi,
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }

    /**
     * 创建默认嵌入模型
     */
    private EmbeddingModel createDefaultEmbeddingModel(String modelName) {
        // 使用配置文件中的默认 API Key
        log.info("使用默认配置创建嵌入模型: {}", modelName);

        // 优先使用 DashScope（阿里云通义千问）
        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
            DashScopeApi dashScopeApi = DashScopeApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(dashscopeApiKey)
                    .build();

            DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                    .model(DashScopeApiSpec.EmbeddingRequest.builder()
                            .model(DEFAULT_EMBEDDING_MODEL).build().model()
                    ).build();

            return new DashScopeEmbeddingModel(
                    dashScopeApi,
                    MetadataMode.EMBED,
                    options,
                    RetryUtils.DEFAULT_RETRY_TEMPLATE
            );
        }

        // 其次使用 OpenAI
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(openaiApiKey)
                    .build();

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(modelName != null ? modelName : "text-embedding-ada-002")
                    .build();

            return new OpenAiEmbeddingModel(
                    openAiApi,
                    MetadataMode.EMBED,
                    options,
                    RetryUtils.DEFAULT_RETRY_TEMPLATE);
        }

        log.error("没有可用的 API Key 配置，无法创建嵌入模型");
        return null;
    }

    /**
     * 获取知识库信息
     */
    private KnowledgeBase getKnowledgeBaseById(Long knowledgeId) {
        if (knowledgeId == null) {
            return null;
        }
        return knowledgeBaseMapper.selectById(knowledgeId);
    }
}
