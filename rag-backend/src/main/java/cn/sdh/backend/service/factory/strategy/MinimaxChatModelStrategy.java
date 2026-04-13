package cn.sdh.backend.service.factory.strategy;

import cn.sdh.backend.service.factory.ModelProvider;
import org.springframework.stereotype.Component;

/**
 * MiniMax Chat 模型创建策略
 */
@Component
public class MinimaxChatModelStrategy extends AbstractChatModelStrategy {

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.MINIMAX;
    }
}
