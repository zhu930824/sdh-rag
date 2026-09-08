package cn.sdh.backend.workflow.executor;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.Workflow;
import cn.sdh.backend.entity.WorkflowExecution;
import cn.sdh.backend.service.KnowledgeAccessService;
import cn.sdh.backend.service.WorkflowExecutionService;
import cn.sdh.backend.service.WorkflowService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 工作流执行引擎(确定性 DAG)。
 * <p>
 * 执行全过程落 workflow_execution(create/节点进度/paused_state 游标/终态);
 * 受控工具节点遇审批暂停(WAITING_APPROVAL)后,可经 resume 从暂停游标继续执行,
 * 下游节点通过 {{nodeId.xxx}} 引用已通过动作的结果。
 */
@Slf4j
@Service
public class WorkflowEngine {

    @Autowired
    private KnowledgeAccessService knowledgeAccess;

    public void requireOwner(Workflow workflow, Long actor) {
        if (actor == null || workflow == null || !actor.equals(workflow.getUserId())) {
            throw new BusinessException(403, "无权访问或执行该工作流");
        }
    }

    private void authorizeNode(WorkflowNode node, Long actor) {
        if ("retrieval".equals(node.getType())) {
            Object id = node.getData() == null ? null : node.getData().get("knowledgeBaseId");
            if (!(id instanceof Number)) throw new BusinessException(400, "请选择有效的知识库");
            knowledgeAccess.requireRead(((Number) id).longValue(), actor);
        }
    }

    @Autowired
    private DAGParser dagParser;

    @Autowired
    private NodeExecutorFactory executorFactory;

    @Autowired
    @Lazy
    private WorkflowExecutionService workflowExecutionService;

    @Autowired
    @Lazy
    private WorkflowService workflowService;

    /** 后台续跑线程池(审批通过后自动续跑工作流) */
    private static final ExecutorService RESUME_POOL = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "workflow-resume");
        thread.setDaemon(true);
        return thread;
    });

    // 执行记录状态(引擎统一大写语义)
    private static final String EXEC_RUNNING = "RUNNING";
    private static final String EXEC_SUCCESS = "SUCCESS";
    private static final String EXEC_FAILED = "FAILED";
    private static final String EXEC_WAITING = "WAITING_APPROVAL";

    /**
     * 执行工作流（无回调）
     */
    public ExecutionResponse execute(Workflow workflow, String inputData) {
        return executeWithCallback(workflow, inputData, null);
    }

    /**
     * 执行工作流（带事件回调，用于 SSE）
     */
    public ExecutionResponse executeWithCallback(Workflow workflow, String inputData,
                                                 Consumer<ExecutionEvent> eventCallback) {
        return executeWithCallback(workflow, inputData, eventCallback, UserContext.getCurrentUserId());
    }

    /**
     * 执行工作流（带事件回调与显式 actor，供异步线程使用，不依赖 ThreadLocal）
     */
    public ExecutionResponse executeWithCallback(Workflow workflow, String inputData,
                                                 Consumer<ExecutionEvent> eventCallback, Long actor) {
        requireOwner(workflow, actor);
        // 新执行:创建执行记录(后续 resume 以此行承载游标)
        WorkflowExecution execution = workflowExecutionService.create(workflow.getId(), actor, inputData);
        execution.setStatus(EXEC_RUNNING);
        execution.setStartTime(LocalDateTime.now());
        workflowExecutionService.update(execution);
        return runCore(workflow, eventCallback, actor, null, execution);
    }

    /**
     * 审批后续跑:从暂停游标继续执行下游节点(状态 CAS 保证并发恰一次)。
     * @param nodeOverrides 已决定动作按节点注入的下游引用(nodeId → 输出字段,含 result/actionStatus)
     */
    public ExecutionResponse resume(Long executionRowId, Long actor, Consumer<ExecutionEvent> eventCallback,
                                    Map<String, Map<String, Object>> nodeOverrides) {
        WorkflowExecution execution = workflowExecutionService.getById(executionRowId);
        if (execution == null) {
            throw new BusinessException(404, "执行记录不存在");
        }
        Workflow workflow = workflowService.getById(execution.getWorkflowId());
        requireOwner(workflow, actor);
        if (!workflowExecutionService.transition(execution.getId(), EXEC_WAITING, EXEC_RUNNING)) {
            throw new BusinessException(409, "执行状态不是等待审批(当前:" + execution.getStatus()
                    + "),可能已在续跑,请刷新后重试");
        }
        try {
            return runCore(workflow, eventCallback, actor,
                    applyOverrides(buildResumeSeed(execution), nodeOverrides), execution);
        } catch (Exception | Error e) {
            // 续跑失败:回滚到 WAITING_APPROVAL,允许修复后重试(避免卡在 RUNNING)
            try {
                workflowExecutionService.transition(execution.getId(), EXEC_RUNNING, EXEC_WAITING);
            } catch (Exception ignored) {
                // 回滚失败仅记录,不掩盖原始异常
            }
            throw e;
        }
    }

    /** 审批后续跑(无下游覆盖注入) */
    public ExecutionResponse resume(Long executionRowId, Long actor, Consumer<ExecutionEvent> eventCallback) {
        return resume(executionRowId, actor, eventCallback, null);
    }

    /** 后台续跑(审批中心 approve 缺省路径);终态写入同一条执行记录 */
    public void resumeInBackground(Long executionRowId) {
        resumeInBackground(executionRowId, null);
    }

    /** 后台续跑(带已决定动作的下游覆盖) */
    public void resumeInBackground(Long executionRowId, Map<String, Map<String, Object>> nodeOverrides) {
        try {
            WorkflowExecution execution = workflowExecutionService.getById(executionRowId);
            if (execution == null) {
                log.warn("后台续跑失败:执行记录 {} 不存在", executionRowId);
                return;
            }
            RESUME_POOL.execute(() -> {
                try {
                    resume(executionRowId, execution.getUserId(), null, nodeOverrides);
                    log.info("工作流执行后台续跑完成 executionId={}", executionRowId);
                } catch (Exception e) {
                    log.warn("工作流执行后台续跑失败 executionId={}: {}", executionRowId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("后台续跑启动失败 executionId={}: {}", executionRowId, e.getMessage());
        }
    }

    /** 把审批已决定的动作结果按节点合入续跑上下文,供下游 {{nodeId.xxx}} 引用 */
    private ResumeSeed applyOverrides(ResumeSeed seed, Map<String, Map<String, Object>> overrides) {
        if (seed == null || overrides == null || overrides.isEmpty()) {
            return seed;
        }
        Map<String, Map<String, Object>> outputs = extractNodeOutputs(seed.currentInput());
        List<ExecutionResponse.NodeResult> results = new ArrayList<>();
        for (ExecutionResponse.NodeResult result : seed.nodeResults()) {
            Map<String, Object> override = overrides.get(result.getNodeId());
            if (override != null) {
                Map<String, Object> merged = new HashMap<>(
                        outputs.getOrDefault(result.getNodeId(), new HashMap<>()));
                merged.putAll(override);
                merged.put("__workflowPaused__", false);
                outputs.put(result.getNodeId(), merged);
                result.setOutput(JSON.toJSONString(merged));
                result.setStatus("SUCCESS");
            }
            results.add(result);
        }
        Map<String, Object> context = new HashMap<>(seed.currentInput());
        context.put("__nodeOutputs__", outputs);
        return new ResumeSeed(seed.nextNodeId(), results, context);
    }

    /** 从上下文快照还原各节点输出映射(续跑时供下游 {{nodeId.xxx}} 引用) */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> extractNodeOutputs(Map<String, Object> currentInput) {
        Map<String, Map<String, Object>> outputs = new HashMap<>();
        Object stored = currentInput.get("__nodeOutputs__");
        if (stored instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> value) {
                    outputs.put(String.valueOf(entry.getKey()), (Map<String, Object>) value);
                }
            }
        }
        return outputs;
    }

    /** 从执行记录恢复游标(nodeResults + 上下文快照 + paused_state) */
    private ResumeSeed buildResumeSeed(WorkflowExecution execution) {
        List<ExecutionResponse.NodeResult> nodeResults = new ArrayList<>();
        if (execution.getNodeResults() != null && !execution.getNodeResults().isBlank()) {
            try {
                nodeResults = JSON.parseArray(execution.getNodeResults(), ExecutionResponse.NodeResult.class);
            } catch (Exception e) {
                log.warn("执行记录节点结果解析失败: {}", e.getMessage());
            }
        }
        Map<String, Object> currentInput = new HashMap<>();
        if (execution.getOutputData() != null && !execution.getOutputData().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> snapshot = JSON.parseObject(execution.getOutputData(), Map.class);
                if (snapshot != null) {
                    currentInput.putAll(snapshot);
                }
            } catch (Exception e) {
                log.warn("执行记录上下文快照解析失败: {}", e.getMessage());
            }
        }
        Map<String, Object> paused = new HashMap<>();
        if (execution.getPausedState() != null && !execution.getPausedState().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JSON.parseObject(execution.getPausedState(), Map.class);
                if (parsed != null) {
                    paused.putAll(parsed);
                }
            } catch (Exception e) {
                log.warn("暂停游标解析失败: {}", e.getMessage());
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeOutputsRaw = (Map<String, Object>) currentInput.get("__nodeOutputs__");
        Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();
        if (nodeOutputsRaw != null) {
            for (Map.Entry<String, Object> entry : nodeOutputsRaw.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> output) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) output;
                    nodeOutputs.put(entry.getKey(), cast);
                }
            }
        }
        if (nodeOutputs.isEmpty()) {
            // 快照缺失时由 nodeResults 反推各节点输出,供下游 {{nodeId.xxx}} 引用
            for (ExecutionResponse.NodeResult result : nodeResults) {
                if (result.getOutput() == null || result.getOutput().isBlank()) continue;
                try {
                    Map<String, Object> output = JSON.parseObject(result.getOutput(), Map.class);
                    if (output != null) {
                        nodeOutputs.put(result.getNodeId(), output);
                    }
                } catch (Exception ignored) {
                    // 单节点输出不可解析时跳过,不影响续跑
                }
            }
            currentInput.put("__nodeOutputs__", nodeOutputs);
        }
        String nextNodeId = paused.get("nextNodeId") == null ? null : String.valueOf(paused.get("nextNodeId"));
        return new ResumeSeed(nextNodeId, nodeResults, currentInput);
    }

    private ExecutionResponse runCore(Workflow workflow, Consumer<ExecutionEvent> eventCallback,
                                      Long actor, ResumeSeed seed, WorkflowExecution execution) {
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

        Map<String, List<WorkflowEdge>> outgoingEdges = new HashMap<>();
        if (config.getEdges() != null) {
            for (WorkflowEdge edge : config.getEdges()) {
                outgoingEdges.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
            }
        }

        // 执行上下文(新执行与续跑共用)
        List<ExecutionResponse.NodeResult> nodeResults = new ArrayList<>();
        Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();
        Map<String, Object> currentInput = new HashMap<>();
        String startNodeId;

        if (seed == null) {
            // ---- 全新执行:定位起始节点并解析输入 ----
            Set<String> hasIncoming = new HashSet<>();
            if (config.getEdges() != null) {
                for (WorkflowEdge edge : config.getEdges()) {
                    hasIncoming.add(edge.getTarget());
                }
            }
            startNodeId = null;
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

            String inputData = execution != null ? execution.getInputData() : null;
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
            if (!currentInput.containsKey("query")) {
                currentInput.put("query", inputData != null ? inputData : "");
            }
            if (!currentInput.containsKey("input")) {
                currentInput.put("input", inputData != null ? inputData : "");
            }
        } else {
            // ---- 续跑:复用暂停时快照的上下文与游标 ----
            // 暂停节点无下游(工具为末节点)时 startNodeId=null:直接以既有结果收尾成功
            startNodeId = seed.nextNodeId();
            nodeResults = seed.nodeResults();
            currentInput = new HashMap<>(seed.currentInput());
            nodeOutputs = extractNodeOutputs(currentInput);
            if (currentInput.get("query") == null) currentInput.put("query", "");
            if (currentInput.get("input") == null) currentInput.put("input", "");
            log.info("工作流续跑从节点开始: {}", startNodeId);
        }

        // 添加节点输出映射与执行上下文
        currentInput.put("__nodeOutputs__", nodeOutputs);
        currentInput.put("__actorId__", actor);
        currentInput.put("__workflowId__", workflow.getId());
        if (execution != null) {
            currentInput.put("__executionId__", execution.getId());
        }

        String status = "SUCCESS";
        String errorMessage = null;

        if (eventCallback != null) {
            eventCallback.accept(ExecutionEvent.workflowStart(
                    execution != null ? String.valueOf(execution.getId()) : null));
        }

        // 执行节点（支持条件分支;循环检测;审批暂停与失败即终止）
        String currentNodeId = startNodeId;
        Set<String> executedNodes = new HashSet<>();
        for (ExecutionResponse.NodeResult result : nodeResults) {
            executedNodes.add(result.getNodeId());
        }

        while (currentNodeId != null) {
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

            if (eventCallback != null) {
                eventCallback.accept(ExecutionEvent.nodeStart(nodeId, nodeName));
            }

            ExecutionResponse.NodeResult nodeResult = new ExecutionResponse.NodeResult();
            nodeResult.setNodeId(nodeId);
            nodeResult.setNodeName(nodeName);
            nodeResult.setNodeType(nodeType);
            nodeResult.setInput(JSON.toJSONString(publicContext(currentInput)));

            try {
                authorizeNode(node, actor);
                NodeExecutor executor = executorFactory.getExecutor(nodeType);
                Map<String, Object> output = executor.execute(node, currentInput, eventCallback);

                nodeOutputs.put(nodeId, output);
                currentInput.putAll(output);
                currentInput.put("__nodeOutputs__", nodeOutputs);

                boolean paused = Boolean.TRUE.equals(output.get("__workflowPaused__"));
                nodeResult.setStatus(paused ? "WAITING_APPROVAL" : "SUCCESS");
                nodeResult.setOutput(JSON.toJSONString(output));

                long nodeEndTime = System.currentTimeMillis();
                int nodeDuration = (int) (nodeEndTime - nodeStartTime);
                nodeResult.setDuration(nodeDuration);

                if (eventCallback != null) {
                    eventCallback.accept(ExecutionEvent.nodeSuccess(nodeId, nodeName, output, nodeDuration));
                }

                log.info("节点执行成功: {} (耗时: {}ms)", nodeId, nodeDuration);
                nodeResults.add(nodeResult);

                if (paused) {
                    // 审批暂停:落游标后终止本轮(后续经 resume 继续)
                    status = EXEC_WAITING;
                    Map<String, Object> pausedState = new HashMap<>();
                    pausedState.put("nextNodeId", determineNextNode(nodeId, nodeType, output, outgoingEdges));
                    pausedState.put("executed", new ArrayList<>(executedNodes));
                    persistExecution(execution, EXEC_WAITING, null, currentInput, nodeResults, pausedState,
                            (int) (System.currentTimeMillis() - startTime));
                    currentNodeId = null;
                } else {
                    currentNodeId = determineNextNode(currentNodeId, nodeType, output, outgoingEdges);
                }

            } catch (Exception e) {
                log.error("节点执行失败: {}", nodeId, e);
                nodeResult.setStatus("FAILED");
                nodeResult.setError(e.getMessage());

                status = "FAILED";
                errorMessage = "节点 " + nodeId + " 执行失败: " + e.getMessage();

                if (eventCallback != null) {
                    eventCallback.accept(ExecutionEvent.nodeError(nodeId, nodeName, e.getMessage()));
                }

                long nodeEndTime = System.currentTimeMillis();
                nodeResult.setDuration((int) (nodeEndTime - nodeStartTime));
                nodeResults.add(nodeResult);

                // 失败即终止
                persistExecution(execution, EXEC_FAILED, errorMessage, currentInput, nodeResults, null,
                        (int) (System.currentTimeMillis() - startTime));
                break;
            }
        }

        long endTime = System.currentTimeMillis();
        int duration = (int) (endTime - startTime);

        if (EXEC_WAITING.equals(status)) {
            // 暂停:执行记录已带游标落库,响应回到调用方,等审批后 resume
            ExecutionResponse response = new ExecutionResponse();
            response.setExecutionId(execution != null ? execution.getId() : null);
            response.setStatus(status);
            response.setNodeResults(nodeResults);
            response.setDuration(duration);
            response.setErrorMessage(null);
            return response;
        }

        // 准备输出数据 - 检查是否有输出节点的最终输出
        Map<String, Object> outputData = new HashMap<>();
        for (ExecutionResponse.NodeResult result : nodeResults) {
            if ("output".equals(result.getNodeType())) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> outputNodeResult = JSON.parseObject(result.getOutput(), Map.class);
                    if (outputNodeResult != null) {
                        outputData.putAll(outputNodeResult);
                    }
                } catch (Exception e) {
                    log.warn("解析输出节点结果失败", e);
                }
            }
        }

        // 如果没有输出节点或输出节点没有返回数据，使用默认行为
        if (outputData.isEmpty() || !outputData.containsKey("__isFinalOutput__")) {
            outputData = new HashMap<>(currentInput);
        }

        // 移除内部标记
        outputData.remove("__nodeOutputs__");
        outputData.remove("__isFinalOutput__");
        outputData.remove("__actorId__");
        outputData.remove("__workflowId__");
        outputData.remove("__workflowPaused__");
        outputData.remove("__executionId__");

        if (eventCallback != null) {
            eventCallback.accept(ExecutionEvent.workflowComplete(status, outputData, duration));
        }

        // 构建响应
        ExecutionResponse response = new ExecutionResponse();
        response.setExecutionId(execution != null ? execution.getId() : null);
        response.setStatus(status);
        response.setNodeResults(nodeResults);
        response.setOutputData(outputData);
        response.setErrorMessage(errorMessage);
        response.setDuration(duration);

        // 终态落库(成功/失败)
        persistExecution(execution, status, errorMessage, outputData, nodeResults, null, duration);

        log.info("工作流执行完成: status={}, duration={}ms", status, duration);

        return response;
    }

    /**
     * 落库执行进度/终态
     */
    private void persistExecution(WorkflowExecution execution, String status, String errorMessage,
                                  Map<String, Object> contextSnapshot,
                                  List<ExecutionResponse.NodeResult> nodeResults,
                                  Map<String, Object> pausedState, Integer duration) {
        if (execution == null) return;
        try {
            execution.setStatus(status);
            if (errorMessage != null) {
                execution.setErrorMessage(errorMessage);
            }
            if (nodeResults != null) {
                execution.setNodeResults(JSON.toJSONString(nodeResults));
            }
            if (contextSnapshot != null) {
                execution.setOutputData(JSON.toJSONString(contextSnapshot));
            }
            if (pausedState != null) {
                execution.setPausedState(JSON.toJSONString(pausedState));
            }
            if (duration != null) {
                execution.setDuration(duration);
            }
            if (!EXEC_RUNNING.equals(status)) {
                execution.setEndTime(LocalDateTime.now());
            }
            workflowExecutionService.update(execution);
        } catch (Exception e) {
            log.warn("执行记录落库失败: {}", e.getMessage());
        }
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
    public Map<String, Object> debugNode(Workflow workflow, String nodeId,
                                          Map<String, Object> inputs) {
        Long actor = UserContext.getCurrentUserId();
        requireOwner(workflow, actor);
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
        currentInput.put("__actorId__", actor);
        currentInput.put("__workflowId__", workflow.getId());
        currentInput.put("__debugMode__", true);

        // 执行节点
        String nodeType = targetNode.getType();
        authorizeNode(targetNode, actor);
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
            result.put("input", publicContext(currentInput));
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
            result.put("input", publicContext(currentInput));
            result.put("error", e.getMessage());
            result.put("duration", System.currentTimeMillis() - startTime);
            return result;
        }
    }

    private Map<String, Object> publicContext(Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>(context);
        result.remove("__actorId__");
        result.remove("__workflowId__");
        result.remove("__debugMode__");
        result.remove("__executionId__");
        return result;
    }

    /** 续跑游标 */
    private record ResumeSeed(String nextNodeId,
                              List<ExecutionResponse.NodeResult> nodeResults,
                              Map<String, Object> currentInput) {
    }
}
