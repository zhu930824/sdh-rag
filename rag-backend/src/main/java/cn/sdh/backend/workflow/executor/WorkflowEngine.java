package cn.sdh.backend.workflow.executor;

import cn.sdh.backend.entity.WorkflowExecution;
import cn.sdh.backend.service.WorkflowExecutionService;
import cn.sdh.backend.workflow.dag.DAGParser;
import cn.sdh.backend.workflow.dto.ExecutionEvent;
import cn.sdh.backend.workflow.dto.ExecutionResponse;
import cn.sdh.backend.workflow.model.WorkflowConfig;
import cn.sdh.backend.workflow.model.WorkflowEdge;
import cn.sdh.backend.workflow.model.WorkflowNode;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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

    @Autowired
    @Lazy
    private WorkflowExecutionService workflowExecutionService;

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

        // 构建节点映射和边映射
        Map<String, WorkflowNode> nodeMap = new HashMap<>();
        for (WorkflowNode node : config.getNodes()) {
            nodeMap.put(node.getId(), node);
        }

        // 构建出边映射: nodeId -> List<Edge>
        Map<String, List<WorkflowEdge>> outgoingEdges = new HashMap<>();
        if (config.getEdges() != null) {
            for (WorkflowEdge edge : config.getEdges()) {
                outgoingEdges.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
            }
        }

        // 找到起始节点（入度为0的节点）
        Set<String> hasIncoming = new HashSet<>();
        if (config.getEdges() != null) {
            for (WorkflowEdge edge : config.getEdges()) {
                hasIncoming.add(edge.getTarget());
            }
        }

        String startNodeId = null;
        for (WorkflowNode node : config.getNodes()) {
            if (!hasIncoming.contains(node.getId())) {
                startNodeId = node.getId();
                break;
            }
        }

        if (startNodeId == null) {
            ExecutionResponse response = new ExecutionResponse();
            response.setStatus("FAILED");
            response.setErrorMessage("未找到起始节点");
            return response;
        }

        log.info("工作流起始节点: {}", startNodeId);

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

        // 执行节点（支持条件分支）
        String currentNodeId = startNodeId;
        Set<String> executedNodes = new HashSet<>();

        while (currentNodeId != null) {
            // 检测循环
            if (executedNodes.contains(currentNodeId)) {
                log.warn("检测到循环执行，跳过节点: {}", currentNodeId);
                break;
            }
            executedNodes.add(currentNodeId);

            WorkflowNode node = nodeMap.get(currentNodeId);
            if (node == null) {
                log.warn("节点不存在: {}", currentNodeId);
                break;
            }

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

                // 决定下一个节点（支持条件分支）
                currentNodeId = determineNextNode(currentNodeId, nodeType, output, outgoingEdges);

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

    /**
     * 决定下一个要执行的节点
     * 对于条件节点，根据分支结果选择下一个节点
     */
    private String determineNextNode(String currentNodeId, String nodeType, Map<String, Object> output,
                                      Map<String, List<WorkflowEdge>> outgoingEdges) {
        List<WorkflowEdge> edges = outgoingEdges.get(currentNodeId);
        if (edges == null || edges.isEmpty()) {
            return null; // 没有后续节点
        }

        // 如果是条件节点，根据branch结果选择
        if ("condition".equals(nodeType)) {
            String branch = (String) output.get("branch");
            log.info("条件节点 {} 选择分支: {}", currentNodeId, branch);

            // 查找匹配分支的边
            for (WorkflowEdge edge : edges) {
                String sourceHandle = edge.getSourceHandle();
                // sourceHandle 存储分支标识，如 "true", "false", "default" 或自定义分支名
                if (sourceHandle != null && sourceHandle.equals(branch)) {
                    log.info("跟随分支边: {} -> {} (handle={})", currentNodeId, edge.getTarget(), branch);
                    return edge.getTarget();
                }
            }

            // 没有匹配的分支，尝试找默认分支
            for (WorkflowEdge edge : edges) {
                if (edge.getSourceHandle() == null || "default".equals(edge.getSourceHandle())) {
                    log.info("跟随默认分支边: {} -> {}", currentNodeId, edge.getTarget());
                    return edge.getTarget();
                }
            }

            // 没有任何分支匹配
            log.warn("条件节点 {} 没有匹配的分支", currentNodeId);
            return null;
        }

        // 非条件节点，返回第一条边的目标（如果有多个，只取第一个，这种情况需要并行执行，暂不支持）
        return edges.get(0).getTarget();
    }

    /**
     * 调试单个节点
     */
    public Map<String, Object> debugNode(cn.sdh.backend.entity.Workflow workflow, String nodeId,
                                          Map<String, Object> inputs) {
        // 解析工作流配置
        WorkflowConfig config = JSON.parseObject(workflow.getFlowData(), WorkflowConfig.class);
        if (config == null || config.getNodes() == null) {
            throw new RuntimeException("工作流配置为空");
        }

        // 查找目标节点
        WorkflowNode targetNode = config.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("节点不存在: " + nodeId));

        // 初始化输入数据
        Map<String, Object> currentInput = new HashMap<>();
        if (inputs != null) {
            currentInput.putAll(inputs);
        }

        // 设置默认值
        if (!currentInput.containsKey("query")) {
            currentInput.put("query", "");
        }
        if (!currentInput.containsKey("input")) {
            currentInput.put("input", "");
        }

        // 执行节点
        String nodeType = targetNode.getType();
        NodeExecutor executor = executorFactory.getExecutor(nodeType);

        log.info("调试节点: {} ({})", nodeId, nodeType);
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> output = executor.execute(targetNode, currentInput, null);
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("nodeId", nodeId);
            result.put("nodeType", nodeType);
            result.put("status", "SUCCESS");
            result.put("input", currentInput);
            result.put("output", output);
            result.put("duration", duration);

            log.info("节点调试成功: {} (耗时: {}ms)", nodeId, duration);
            return result;

        } catch (Exception e) {
            log.error("节点调试失败: {}", nodeId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("nodeId", nodeId);
            result.put("nodeType", nodeType);
            result.put("status", "FAILED");
            result.put("input", currentInput);
            result.put("error", e.getMessage());
            result.put("duration", System.currentTimeMillis() - startTime);
            return result;
        }
    }
}
