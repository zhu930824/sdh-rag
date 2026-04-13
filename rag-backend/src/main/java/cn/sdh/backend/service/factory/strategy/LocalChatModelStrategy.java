package cn.sdh.backend.service.factory.strategy;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.factory.ModelProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 本地模型 (Ollama) Chat 模型创建策略
 */
@Component
public class LocalChatModelStrategy extends AbstractChatModelStrategy {

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.LOCAL;
    }

    @Override
    protected String resolveApiKey(ModelConfig config, String defaultApiKey) {
        // Ollama 通常不需要 API Key，提供一个占位符
        String apiKey = super.resolveApiKey(config, defaultApiKey);
        return apiKey != null && !apiKey.isEmpty() ? apiKey : "ollama";
    }
}
