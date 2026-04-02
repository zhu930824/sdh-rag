package cn.sdh.backend.workflow.executor;

import cn.sdh.backend.workflow.dto.ExecutionEvent;
import cn.sdh.backend.workflow.model.WorkflowNode;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 节点执行器接口
 */
public interface NodeExecutor {

    /**
     * 执行节点
     *
     * @param node 节点配置
     * @param input 输入数据
     * @return 输出数据
     * @throws Exception 执行异常
     */
    Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception;

    /**
     * 执行节点（带进度回调）
     *
     * @param node 节点配置
     * @param input 输入数据
     * @param progressCallback 进度回调
     * @return 输出数据
     * @throws Exception 执行异常
     */
    default Map<String, Object> execute(WorkflowNode node, Map<String, Object> input,
                                        Consumer<ExecutionEvent> progressCallback) throws Exception {
        return execute(node, input);
    }

    /**
     * 获取支持的节点类型
     *
     * @return 节点类型标识
     */
    String getSupportedNodeType();
}
