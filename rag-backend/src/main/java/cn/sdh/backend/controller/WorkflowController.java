package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.Workflow;
import cn.sdh.backend.entity.WorkflowExecution;
import cn.sdh.backend.service.WorkflowService;
import cn.sdh.backend.service.WorkflowExecutionService;
import cn.sdh.backend.service.AgentActionService;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.workflow.dto.ExecutionEvent;
import cn.sdh.backend.dto.WorkflowDebugRequest;
import cn.sdh.backend.workflow.dto.ExecutionResponse;
import cn.sdh.backend.workflow.dto.NodeTypeDefinition;
import cn.sdh.backend.workflow.executor.WorkflowEngine;
import cn.sdh.backend.workflow.service.NodeTypeDefinitionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.alibaba.fastjson2.JSON;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowEngine workflowEngine;
    private final NodeTypeDefinitionService nodeTypeDefinitionService;
    private final AgentActionService agentActionService;

    @Autowired
    @Qualifier("workflowExecutor")
    private Executor executorService;

    @GetMapping("/list")
    public Result<IPage<Workflow>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {

        if (pageSize > 100) pageSize = 100;

        IPage<Workflow> result = workflowService.getPage(page, pageSize, keyword);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Workflow> getById(@PathVariable Long id) {
        Workflow workflow = workflowService.getById(id);
        workflowEngine.requireOwner(workflow, UserContext.getCurrentUserId());
        if (workflow == null) {
            return Result.notFound("工作流不存在");
        }
        return Result.success(workflow);
    }

    @PostMapping
    public Result<Workflow> save(@Valid @RequestBody Workflow workflow) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        workflow.setUserId(userId);
        workflowService.save(workflow);
        return Result.success(workflow);
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Workflow workflow) {
        Workflow existing = workflowService.getById(id);
        if (existing == null) {
            return Result.notFound("工作流不存在");
        }

        Long userId = UserContext.getCurrentUserId();
        if (userId == null || !userId.equals(existing.getUserId())) {
            return Result.forbidden("无权修改此工作流");
        }

        workflow.setId(id);
        workflow.setUserId(existing.getUserId());
        workflowService.update(workflow);
        return Result.success();
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Workflow existing = workflowService.getById(id);
        if (existing == null) {
            return Result.notFound("工作流不存在");
        }

        Long userId = UserContext.getCurrentUserId();
        if (userId == null || !userId.equals(existing.getUserId())) {
            return Result.forbidden("无权删除此工作流");
        }

        workflowService.deleteById(id);
        return Result.success();
    }

    @PostMapping("/status/{id}")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        Workflow existing = workflowService.getById(id);
        if (existing == null) {
            return Result.notFound("工作流不存在");
        }

        Long userId = UserContext.getCurrentUserId();
        if (userId == null || !userId.equals(existing.getUserId())) {
            return Result.forbidden("无权修改此工作流");
        }

        workflowService.toggleStatus(id);
        return Result.success();
    }

    /**
     * 同步执行工作流
     */
    @PostMapping("/{id}/execute")
    public Result<ExecutionResponse> execute(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request) {

        Workflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return Result.notFound("工作流不存在");
        }

        // 保持 JSON 序列化传入引擎(此前 Map.toString() 产生 {query=...} 形式,
        // 引擎 JSON 解析失败会整串当纯文本,变量引用全部失效)
        String inputData = "";
        if (request != null && request.get("inputs") != null) {
            Object inputs = request.get("inputs");
            inputData = inputs instanceof String s ? s : JSON.toJSONString(inputs);
        }

        workflowEngine.requireOwner(workflow, UserContext.getCurrentUserId());
        ExecutionResponse response = workflowEngine.execute(workflow, inputData);
        return Result.success(response);
    }

    /**
     * 审批后续跑:从暂停游标继续执行下游节点(同步返回终态)。
     * 审批中心 approve 已自动后台续跑;此端点用于前端手动补按/同步查看结果。
     */
    @PostMapping("/execution/{executionId}/resume")
    public Result<ExecutionResponse> resumeExecution(@PathVariable Long executionId) {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) {
            return Result.unauthorized();
        }
        WorkflowExecution execution = workflowExecutionService.getById(executionId);
        if (execution == null) {
            return Result.notFound("执行记录不存在");
        }
        workflowEngine.requireOwner(workflowService.getById(execution.getWorkflowId()), actor);
        List<AgentAction> linkedActions = agentActionService.listByWorkflowExecution(executionId);
        if (linkedActions.isEmpty()) {
            return Result.error(409, "执行记录没有可续跑的审批动作");
        }
        boolean undecided = linkedActions.stream().anyMatch(action ->
                cn.sdh.backend.agent.AgentActionState.blocksContinuation(action.getStatus()));
        if (undecided) {
            return Result.error(409, "审批动作尚未全部决定，不能续跑工作流");
        }
        Map<String, Map<String, Object>> overrides = new java.util.HashMap<>();
        for (AgentAction action : linkedActions) {
            if (action.getNodeId() == null) continue;
            Map<String, Object> fields = new java.util.LinkedHashMap<>();
            fields.put("actionId", action.getId());
            fields.put("actionStatus", action.getStatus());
            fields.put("approvalRequired", false);
            fields.put("responseCode", action.getResponseCode());
            fields.put("result", parseActionResult(action.getResultJson()));
            fields.put("error", action.getErrorMessage());
            overrides.put(action.getNodeId(), fields);
        }
        ExecutionResponse response = workflowEngine.resume(executionId, actor, null, overrides);
        return Result.success("工作流已继续执行", response);
    }

    /**
     * SSE 流式执行工作流
     */
    @GetMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeStream(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String inputData) {

        Workflow workflow = workflowService.getById(id);
        Long actor = UserContext.getCurrentUserId();
        workflowEngine.requireOwner(workflow, actor);
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        if (workflow == null) {
            executorService.execute(() -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name("ERROR")
                        .data("{\"message\":\"工作流不存在\"}"));
                    emitter.complete();
                } catch (IOException e) {
                    log.error("SSE 发送失败", e);
                }
            });
            return emitter;
        }

        final String finalInputData = inputData;

        executorService.execute(() -> {
            try {
                ExecutionResponse response = workflowEngine.executeWithCallback(
                    workflow,
                    finalInputData,
                    event -> {
                        try {
                            String eventJson = cn.sdh.backend.workflow.dto.ExecutionEvent.class
                                .getMethod("getEventType")
                                .invoke(event) != null
                                ? toJson(event)
                                : "{}";

                            emitter.send(SseEmitter.event()
                                .name(event.getEventType())
                                .data(eventJson));
                        } catch (Exception e) {
                            log.error("SSE 发送事件失败", e);
                        }
                    }, actor
                );

                // 发送最终结果
                emitter.send(SseEmitter.event()
                    .name("COMPLETE")
                    .data(toJson(response)));
                emitter.complete();

            } catch (Exception e) {
                log.error("工作流执行失败", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("ERROR")
                        .data("{\"message\":\"" + e.getMessage() + "\"}"));
                    emitter.completeWithError(e);
                } catch (IOException ioException) {
                    log.error("SSE 发送错误失败", ioException);
                }
            }
        });

        emitter.onCompletion(() -> log.debug("SSE 连接关闭: workflowId={}", id));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: workflowId={}", id);
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 获取节点类型定义列表
     */
    @GetMapping("/node-types")
    public Result<List<NodeTypeDefinition>> getNodeTypeDefinitions() {
        return Result.success(nodeTypeDefinitionService.getAllNodeTypeDefinitions());
    }

    /**
     * 获取工作流执行历史
     */
    @GetMapping("/{id}/executions")
    public Result<IPage<WorkflowExecution>> getExecutions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        if (pageSize > 100) pageSize = 100;
        workflowEngine.requireOwner(workflowService.getById(id), UserContext.getCurrentUserId());
        IPage<WorkflowExecution> result = workflowExecutionService.getPage(id, page, pageSize);
        return Result.success(result);
    }

    /**
     * 获取执行记录详情
     */
    @GetMapping("/execution/{executionId}")
    public Result<WorkflowExecution> getExecutionDetail(@PathVariable Long executionId) {
        WorkflowExecution execution = workflowExecutionService.getById(executionId);
        if (execution == null) {
            return Result.notFound("执行记录不存在");
        }
        workflowEngine.requireOwner(workflowService.getById(execution.getWorkflowId()), UserContext.getCurrentUserId());
        return Result.success(execution);
    }

    /**
     * 调试单个节点
     */
    @PostMapping("/{id}/debug-node")
    public Result<Map<String, Object>> debugNode(
            @PathVariable Long id,
            @RequestBody WorkflowDebugRequest request) {

        Workflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return Result.notFound("工作流不存在");
        }

        try {
            Map<String, Object> result = workflowEngine.debugNode(workflow, request.getNodeId(), request.getInputs());
            return Result.success(result);
        } catch (Exception e) {
            log.error("调试节点失败", e);
            return Result.error("调试节点失败: " + e.getMessage());
        }
    }

    /**
     * 简单的 JSON 序列化
     */
    private String toJson(Object obj) {
        try {
            return com.alibaba.fastjson2.JSON.toJSONString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Object parseActionResult(String value) {
        if (value == null || value.isBlank()) return null;
        try { return JSON.parse(value); }
        catch (Exception e) { return value; }
    }
}
