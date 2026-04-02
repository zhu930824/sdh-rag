package cn.sdh.backend.workflow.executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点执行器工厂
 */
@Component
public class NodeExecutorFactory {

    private final Map<String, NodeExecutor> executors = new HashMap<>();

    @Autowired
    public NodeExecutorFactory(List<NodeExecutor> executorList) {
        for (NodeExecutor executor : executorList) {
            executors.put(executor.getSupportedNodeType(), executor);
        }
    }

    /**
     * 根据节点类型获取执行器
     *
     * @param nodeType 节点类型
     * @return 执行器
     * @throws RuntimeException 不支持的节点类型
     */
    public NodeExecutor getExecutor(String nodeType) {
        NodeExecutor executor = executors.get(nodeType);
        if (executor == null) {
            throw new RuntimeException("不支持的节点类型: " + nodeType);
        }
        return executor;
    }

    /**
     * 检查是否支持该节点类型
     *
     * @param nodeType 节点类型
     * @return 是否支持
     */
    public boolean supports(String nodeType) {
        return executors.containsKey(nodeType);
    }
}
