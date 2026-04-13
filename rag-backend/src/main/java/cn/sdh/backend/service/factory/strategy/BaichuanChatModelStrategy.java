package cn.sdh.backend.service.factory.strategy;

import cn.sdh.backend.service.factory.ModelProvider;
import org.springframework.stereotype.Component;

/**
 * 百川 Chat 模型创建策略
 */
@Component
public class BaichuanChatModelStrategy extends AbstractChatModelStrategy {

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.BAICHUAN;
    }
}
