package cn.sdh.backend.knowledge.connector;

import cn.sdh.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ApiPushKnowledgeSourceConnector implements KnowledgeSourceConnector {
    @Override public String type() { return "api_push"; }
    @Override public Mode mode() { return Mode.PUSH; }

    @Override
    public void validate(Map<String, Object> configuration) {
        if (configuration != null && configuration.size() > 20) {
            throw new BusinessException(400, "数据源配置项过多");
        }
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        throw new BusinessException(400, "API Push 数据源由业务系统主动推送，不支持服务端拉取");
    }
}
