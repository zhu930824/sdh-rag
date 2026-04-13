package cn.sdh.backend.service.factory.strategy;

import cn.sdh.backend.service.factory.ModelProvider;
import org.springframework.stereotype.Component;

/**
 * 智谱 AI Chat 模型创建策略
 */
@Component
public class ZhipuChatModelStrategy extends AbstractChatModelStrategy {

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.ZHIPU;
    }
}
