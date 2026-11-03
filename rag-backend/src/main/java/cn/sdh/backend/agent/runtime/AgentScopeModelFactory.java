package cn.sdh.backend.agent.runtime;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.factory.ModelProvider;
import io.agentscope.core.model.*;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentScopeModelFactory {
    @Value("${spring.ai.dashscope.api-key:}") private String dashscopeKey;
    @Value("${spring.ai.openai.api-key:}") private String openaiKey;

    public Model create(ModelConfig config) {
        if (config == null) throw new BusinessException(409, "运行关联的模型配置已不存在");
        ModelProvider provider = ModelProvider.fromString(config.getProvider());
        String key = config.getApiKey();
        if (key == null || key.isBlank()) key = provider == ModelProvider.DASHSCOPE ? dashscopeKey : openaiKey;
        if (key == null || key.isBlank()) throw new BusinessException(400, "模型未配置可用凭据");
        GenerateOptions options = GenerateOptions.builder().temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens() == null ? 8192 : config.getMaxTokens()).parallelToolCalls(false).build();
        if (provider == ModelProvider.DASHSCOPE) {
            var builder = DashScopeChatModel.builder().apiKey(key).modelName(config.getModelId())
                    .stream(true).defaultOptions(options);
            if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
                builder.baseUrl(config.getBaseUrl().replace("/compatible-mode", "").replaceAll("/+$", ""));
            }
            return builder.build();
        }
        if (provider == ModelProvider.OPENAI) {
            var builder = OpenAIChatModel.builder().apiKey(key).modelName(config.getModelId())
                    .stream(true).generateOptions(options);
            if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) builder.baseUrl(config.getBaseUrl());
            return builder.build();
        }
        throw new BusinessException(400, "AgentScope 运行时暂只支持 DashScope/OpenAI");
    }
}
