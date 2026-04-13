package cn.sdh.backend.service.factory.strategy;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.factory.ModelProvider;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * DashScope (阿里云通义千问) Chat 模型创建策略
 * 使用 DashScope 原生 API
 *
 * 注意：DashScope 原生 API 与 OpenAI 兼容 API 使用不同的 baseUrl：
 * - 原生 API: https://dashscope.aliyuncs.com
 * - OpenAI 兼容: https://dashscope.aliyuncs.com/compatible-mode
 */
@Slf4j
@Component
public class DashScopeChatModelStrategy extends AbstractChatModelStrategy {

    /**
     * DashScope 原生 API 基础 URL
     */
    private static final String DASHSCOPE_NATIVE_BASE_URL = "https://dashscope.aliyuncs.com";

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.DASHSCOPE;
    }

    @Override
    protected String resolveBaseUrl(ModelConfig config) {
        // 优先使用配置中的 baseUrl
        String baseUrl = config.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return normalizeBaseUrl(baseUrl);
        }
        // 使用 DashScope 原生 API URL
        return DASHSCOPE_NATIVE_BASE_URL;
    }

    @Override
    protected ChatModel doCreateModel(String baseUrl, String apiKey, String modelName, ModelConfig config) {
        // 脱敏 API Key 用于日志
        String maskedApiKey = maskApiKey(apiKey);
        log.info("创建 DashScope ChatModel - baseUrl: {}, model: {}, apiKey: {}..., temperature: {}, maxTokens: {}",
                baseUrl, modelName, maskedApiKey,
                config != null ? config.getTemperature() : null,
                config != null ? config.getMaxTokens() : null);

        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder()
                .model(modelName)
                .multiModel(true);

        if (config != null) {
            if (config.getTemperature() != null) {
                builder.temperature(config.getTemperature());
            }
            if (config.getMaxTokens() != null) {
                builder.maxToken(config.getMaxTokens());
            }
        }

        DashScopeChatOptions options = builder.build();
        log.debug("DashScopeChatOptions: model={}", options.getModel());

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 规范化 baseUrl，将 OpenAI 兼容模式 URL 转换为原生 API URL
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return DASHSCOPE_NATIVE_BASE_URL;
        }
        // 如果是 OpenAI 兼容模式的 URL，转换为原生 API URL
        return baseUrl.replace("/compatible-mode", "");
    }

    @Override
    public ChatModel createDefaultModel(String modelName, String apiKey) {
        return doCreateModel(DASHSCOPE_NATIVE_BASE_URL, apiKey, modelName, null);
    }

    /**
     * 脱敏 API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "******";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}