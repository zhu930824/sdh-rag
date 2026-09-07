package cn.sdh.backend.knowledge.connector;

import cn.sdh.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class KnowledgeSourceConnectorRegistry {
    private final Map<String, KnowledgeSourceConnector> connectors;

    public KnowledgeSourceConnectorRegistry(List<KnowledgeSourceConnector> connectorBeans) {
        Map<String, KnowledgeSourceConnector> registered = new LinkedHashMap<>();
        for (KnowledgeSourceConnector connector : connectorBeans) {
            String type = normalize(connector.type());
            if (type.isBlank()) throw new IllegalStateException("知识源连接器类型不能为空");
            if (registered.putIfAbsent(type, connector) != null) {
                throw new IllegalStateException("重复的知识源连接器类型: " + type);
            }
        }
        this.connectors = Map.copyOf(registered);
    }

    public KnowledgeSourceConnector require(String type) {
        KnowledgeSourceConnector connector = connectors.get(normalize(type));
        if (connector == null) throw new BusinessException(400, "不支持的知识源类型: " + type);
        return connector;
    }

    public Set<String> supportedTypes() {
        return connectors.keySet();
    }

    private String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
