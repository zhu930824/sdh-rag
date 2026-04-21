package cn.sdh.backend.service.factory;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import cn.sdh.backend.service.ModelFactory;
import cn.sdh.backend.service.factory.strategy.ModelCreationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chat 模型工厂
 * 负责创建和管理 ChatModel 实例
 * 使用策略模式支持多种模型提供者
 */
@Slf4j
@Component
public class ChatModelFactory extends ModelFactory<ChatModel> {

    private static final String DEFAULT_MODEL = "qwen-plus";
    private static final String MODEL_TYPE = "chat";

    private final Map<ModelProvider, ModelCreationStrategy<ChatModel>> strategyMap;

    public ChatModelFactory(
            ModelConfigMapper modelConfigMapper,
            List<ModelCreationStrategy<ChatModel>> strategies) {
        super(modelConfigMapper);
        // 将策略列表转换为以 ModelProvider 为 key 的 Map
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        ModelCreationStrategy::getProvider,
                        Function.identity()
                ));
        log.info("已加载 {} 个 Chat 模型创建策略: {}",
                strategyMap.size(),
                strategyMap.keySet().stream().map(Enum::name).collect(Collectors.joining(", ")));
    }

    @Override
    protected ChatModel createModel(ModelConfig config) {
        String provider = config.getProvider();
        log.info("创建 Chat 模型: name={}, provider={}, modelId={}",
                config.getName(), provider, config.getModelId());

        ModelProvider modelProvider = ModelProvider.fromString(provider);

        if (modelProvider == null) {
            log.warn("未知的模型提供者: {}, 使用默认配置", provider);
            return createDefaultModel(config.getModelId());
        }

        ModelCreationStrategy<ChatModel> strategy = strategyMap.get(modelProvider);
        if (strategy == null) {
            log.warn("未找到模型提供者 {} 的创建策略, 使用默认配置", provider);
            return createDefaultModel(config.getModelId());
        }

        // DashScope 使用配置的 dashscopeApiKey
        String defaultApiKey = modelProvider == ModelProvider.DASHSCOPE ? dashscopeApiKey : null;
        return strategy.createModel(config, defaultApiKey);
    }

    @Override
    protected ChatModel createDefaultModel(String modelName) {
        log.info("使用默认配置创建 Chat 模型: {}", modelName);

        // 优先使用 DashScope
        ModelCreationStrategy<ChatModel> dashscopeStrategy = strategyMap.get(ModelProvider.DASHSCOPE);
        if (dashscopeStrategy != null && dashscopeApiKey != null && !dashscopeApiKey.isEmpty()) {
            return dashscopeStrategy.createDefaultModel(
                    modelName != null ? modelName : DEFAULT_MODEL,
                    dashscopeApiKey
            );
        }

        // 其次使用 OpenAI
        ModelCreationStrategy<ChatModel> openaiStrategy = strategyMap.get(ModelProvider.OPENAI);
        if (openaiStrategy != null && openaiApiKey != null && !openaiApiKey.isEmpty()) {
            return openaiStrategy.createDefaultModel(
                    modelName != null ? modelName : "gpt-3.5-turbo",
                    openaiApiKey
            );
        }

        log.error("没有可用的 API Key 配置，无法创建 Chat 模型");
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

    /**
     * 获取已注册的策略提供者列表
     */
    public List<ModelProvider> getRegisteredProviders() {
        return List.copyOf(strategyMap.keySet());
    }
}