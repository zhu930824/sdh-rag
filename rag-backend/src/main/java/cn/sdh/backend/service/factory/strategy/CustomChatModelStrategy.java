package cn.sdh.backend.service.factory.strategy;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.factory.ModelProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 自定义模型 Chat 模型创建策略
 * 使用配置中指定的 baseUrl 和 apiKey
 */
@Component
public class CustomChatModelStrategy extends AbstractChatModelStrategy {

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.CUSTOM;
    }

    @Override
    protected String resolveBaseUrl(ModelConfig config) {
        // 自定义模型必须提供 baseUrl
        return config.getBaseUrl();
    }

    @Override
    public ChatModel createDefaultModel(String modelName, String apiKey) {
        // 自定义模型不支持默认创建
        throw new UnsupportedOperationException("自定义模型必须通过配置创建，不支持默认创建");
    }
}
