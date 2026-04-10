package cn.sdh.backend.service.factory;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.ModelFactory;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Component;

/**
 * Rerank 模型工厂
 * 负责创建和管理 RerankModel 实例
 *
 * 注：目前主要支持 DashScope 的 Rerank 模型
 */
@Slf4j
@Component
public class RerankModelFactory extends ModelFactory<RerankModel> {

    private static final String DEFAULT_MODEL = "qwen3-rerank";
    private static final String MODEL_TYPE = "reranker";
    private static final String DASHSCOPE_RERANK_URL = "https://dashscope.aliyuncs.com";

    public RerankModelFactory(ModelConfigMapper modelConfigMapper) {
        super(modelConfigMapper);
    }

    @Override
    protected RerankModel createModel(ModelConfig config) {
        log.info("创建 Rerank 模型: name={}, provider={}, modelId={}",
                config.getName(), config.getProvider(), config.getModelId());

        ModelProvider provider = ModelProvider.fromString(config.getProvider());

        if (provider == null || !provider.supportsRerank()) {
            log.warn("提供者 {} 不支持 Rerank 功能，尝试使用 DashScope", config.getProvider());
        }

        // 目前仅 DashScope 支持原生的 Rerank 功能
        String apiKey = config.getApiKey() != null ? config.getApiKey() : dashscopeApiKey;
        return createDashScopeRerankModel(apiKey, config.getModelId());
    }

    @Override
    protected RerankModel createDefaultModel(String modelName) {
        log.info("使用默认配置创建 Rerank 模型: {}", modelName);

        if (dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            return createDashScopeRerankModel(
                    dashscopeApiKey,
                    modelName != null ? modelName : DEFAULT_MODEL
            );
        }

        log.error("没有可用的 API Key 配置，无法创建 Rerank 模型");
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

    private RerankModel createDashScopeRerankModel(String apiKey, String modelName) {
        return new DashScopeRerankModel(
                DashScopeApi.builder()
                        .baseUrl(DASHSCOPE_RERANK_URL)
                        .apiKey(apiKey)
                        .build(),
                DashScopeRerankOptions.builder()
                        .model(modelName)
                        .build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }
}
