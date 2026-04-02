package cn.sdh.backend.workflow.executor;

import cn.sdh.backend.workflow.dag.DAGParser;
import cn.sdh.backend.workflow.dto.ExecutionEvent;
import cn.sdh.backend.workflow.dto.ExecutionResponse;
import cn.sdh.backend.workflow.model.WorkflowConfig;
import cn.sdh.backend.workflow.model.WorkflowNode;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流执行引擎
 */
@Slf4j
@Service
public class WorkflowEngine {

    @Autowired
    private DAGParser dagParser;

    @Autowired
    private NodeExecutorFactory executorFactory;

    /**
     * 执行工作流（无回调）
     */
    public ExecutionResponse execute(cn.sdh.backend.entity.Workflow workflow, String inputData) {
        return executeWithCallback(workflow, inputData, null);
    }

    /**
     * 执行工作流（带事件回调，用于 SSE）
     */
    public ExecutionResponse executeWithCallback(cn.sdh.backend.entity.Workflow workflow, String inputData,
                                                   Consumer<ExecutionEvent> eventCallback) {
        long startTime = System.currentTimeMillis();

        // 解析工作流配置
        WorkflowConfig config = JSON.parseObject(workflow.getFlowData(), WorkflowConfig.class);
        if (config == null || config.getNodes() == null || config.getNodes().isEmpty()) {
            ExecutionResponse response = new ExecutionResponse();
            response.setStatus("FAILED");
            response.setErrorMessage("工作流配置为空");
            return response;
        }

        // 拓扑排序获取执行顺序
        List<WorkflowNode> sortedNodes;
        try {
            sortedNodes = dagParser.parse(config);
        } catch (Exception e) {
            log.error("工作流解析失败", e);
            ExecutionResponse response = new ExecutionResponse();
            response.setStatus("FAILED");
            response.setErrorMessage("工作流解析失败: " + e.getMessage());
            return response;
        }

        log.info("工作流执行顺序: {}", sortedNodes.stream().map(WorkflowNode::getId).toList());

        // 初始化执行上下文
        List<ExecutionResponse.NodeResult> nodeResults = new ArrayList<>();
        Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();
        Map<String, Object> currentInput = new HashMap<>();

        // 解析输入数据
        if (inputData != null && !inputData.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> inputMap = JSON.parseObject(inputData, Map.class);
                if (inputMap != null) {
                    currentInput.putAll(inputMap);
                }
            } catch (Exception e) {
                // 作为纯文本输入
                currentInput.put("input", inputData);
                currentInput.put("query", inputData);
            }
        }

        // 如果没有输入，设置默认值
        if (!currentInput.containsKey("query")) {
            currentInput.put("query", inputData != null ? inputData : "");
        }
        if (!currentInput.containsKey("input")) {
            currentInput.put("input", inputData != null ? inputData : "");
        }

        // 添加节点输出映射
        currentInput.put("__nodeOutputs__", nodeOutputs);

        String status = "SUCCESS";
        String errorMessage = null;

        // 发送工作流开始事件
        if (eventCallback != null) {
            eventCallback.accept(ExecutionEvent.workflowStart(null));
        }

        // 按顺序执行节点
        for (WorkflowNode node : sortedNodes) {
            long nodeStartTime = System.currentTimeMillis();

            String nodeType = node.getType();
            String nodeId = node.getId();
            String nodeName = node.getData() != null
                ? (String) node.getData().get("label")
                : nodeId;

            log.info("开始执行节点: {} ({})", nodeId, nodeType);

            // 发送节点开始事件
            if (eventCallback != null) {
                eventCallback.accept(ExecutionEvent.nodeStart(nodeId, nodeName));
            }

            ExecutionResponse.NodeResult nodeResult = new ExecutionResponse.NodeResult();
            nodeResult.setNodeId(nodeId);
            nodeResult.setNodeName(nodeName);
            nodeResult.setNodeType(nodeType);
            nodeResult.setInput(JSON.toJSONString(currentInput));

            try {
                // 获取执行器并执行
                NodeExecutor executor = executorFactory.getExecutor(nodeType);
                Map<String, Object> output = executor.execute(node, currentInput, eventCallback);

                // 保存节点输出
                nodeOutputs.put(nodeId, output);

                // 更新当前输入（下一个节点的输入）
                currentInput.putAll(output);
                currentInput.put("__nodeOutputs__", nodeOutputs);

                nodeResult.setStatus("SUCCESS");
                nodeResult.setOutput(JSON.toJSONString(output));

                long nodeEndTime = System.currentTimeMillis();
                int nodeDuration = (int) (nodeEndTime - nodeStartTime);
                nodeResult.setDuration(nodeDuration);

                // 发送节点成功事件
                if (eventCallback != null) {
                    eventCallback.accept(ExecutionEvent.nodeSuccess(nodeId, nodeName, output, nodeDuration));
                }

                log.info("节点执行成功: {} (耗时: {}ms)", nodeId, nodeDuration);

            } catch (Exception e) {
                log.error("节点执行失败: {}", nodeId, e);
                nodeResult.setStatus("FAILED");
                nodeResult.setError(e.getMessage());

                status = "FAILED";
                errorMessage = "节点 " + nodeId + " 执行失败: " + e.getMessage();

                // 发送节点错误事件
                if (eventCallback != null) {
                    eventCallback.accept(ExecutionEvent.nodeError(nodeId, nodeName, e.getMessage()));
                }

                // 记录节点结果后终止执行
                long nodeEndTime = System.currentTimeMillis();
                nodeResult.setDuration((int) (nodeEndTime - nodeStartTime));
                nodeResults.add(nodeResult);
                break;
            }

            nodeResults.add(nodeResult);
        }

        long endTime = System.currentTimeMillis();
        int duration = (int) (endTime - startTime);

        // 准备输出数据
        Map<String, Object> outputData = new HashMap<>(currentInput);
        outputData.remove("__nodeOutputs__");

        // 发送工作流完成事件
        if (eventCallback != null) {
            eventCallback.accept(ExecutionEvent.workflowComplete(status, outputData, duration));
        }

        // 构建响应
        ExecutionResponse response = new ExecutionResponse();
        response.setStatus(status);
        response.setNodeResults(nodeResults);
        response.setOutputData(outputData);
        response.setErrorMessage(errorMessage);
        response.setDuration(duration);

        log.info("工作流执行完成: status={}, duration={}ms", status, duration);

        return response;
    }
}
