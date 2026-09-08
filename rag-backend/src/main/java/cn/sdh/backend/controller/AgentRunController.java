package cn.sdh.backend.controller;

import cn.sdh.backend.agent.AgentAskService;
import cn.sdh.backend.agent.RunStateService;
import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.entity.AgentStep;
import cn.sdh.backend.service.AgentActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行档案与续跑端点。
 * resume 为 SSE 续流(聊天卡内联审批后调用);resume-async 为后台续跑(审批中心路径)。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/runs")
public class AgentRunController {

    private static final int DETAIL_PREVIEW_CAP = 8 * 1024;

    private final AgentAskService agentAskService;
    private final RunStateService runStateService;
    private final AgentActionService agentActionService;

    /**
     * 运行详情(run 概要 + 步骤轨迹 + 动作记录),审批页/聊天页轮询刷新用
     */
    @GetMapping("/{runKey}")
    public Result<Map<String, Object>> detail(@PathVariable String runKey) {
        AgentRun run = runStateService.getByRunKey(runKey);
        if (run == null) {
            return Result.notFound("Agent 运行不存在");
        }
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) {
            return Result.unauthorized();
        }
        boolean allowed = actor.equals(run.getUserId());
        if (!allowed) {
            for (AgentAction action : agentActionService.listByRun(run.getId())) {
                if (actor.equals(action.getApproverId())) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) {
            return Result.forbidden("无权查看该 Agent 运行");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runKey", run.getRunKey());
        result.put("status", run.getStatus());
        result.put("stepCount", run.getStepCount());
        result.put("maxSteps", run.getMaxSteps());
        result.put("question", run.getQuestion());
        result.put("sessionId", run.getSessionId());
        result.put("historyId", run.getHistoryId());
        result.put("knowledgeId", run.getKnowledgeId());
        result.put("modelId", run.getModelId());
        result.put("pendingActionId", run.getPendingActionId());
        result.put("promptTokens", run.getPromptTokens());
        result.put("completionTokens", run.getCompletionTokens());
        result.put("totalTokens", run.getTotalTokens());
        result.put("errorMessage", run.getErrorMessage());
        result.put("createdAt", run.getCreatedAt());
        result.put("updatedAt", run.getUpdatedAt());
        result.put("completedAt", run.getCompletedAt());

        List<Map<String, Object>> steps = new ArrayList<>();
        for (AgentStep step : runStateService.stepsOf(run.getId())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", step.getSeq());
            item.put("type", step.getStepType());
            item.put("toolCode", step.getToolCode());
            item.put("actionId", step.getActionId());
            item.put("status", step.getStatus());
            item.put("latencyMs", step.getLatencyMs());
            item.put("tokens", step.getTokens());
            item.put("inputPreview", cap(step.getInputJson()));
            item.put("outputPreview", cap(step.getOutputJson()));
            steps.add(item);
        }
        result.put("steps", steps);

        List<Map<String, Object>> actions = new ArrayList<>();
        for (AgentAction action : agentActionService.listByRun(run.getId())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", action.getId());
            item.put("toolName", action.getToolName());
            item.put("status", action.getStatus());
            item.put("idempotencyKey", action.getIdempotencyKey());
            actions.add(item);
        }
        result.put("actions", actions);
        return Result.success(result);
    }

    /**
     * 续跑(SSE 续流)。前置校验失败以 error 事件(含 code)返回,与 chat 事件协议一致。
     */
    @PostMapping(value = "/{runKey}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> resume(@PathVariable String runKey,
                               @RequestBody(required = false) Map<String, Object> body) {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) {
            return Flux.just(errorJson(401, "未授权"));
        }
        Long actionId = null;
        if (body != null && body.get("actionId") != null) {
            Object value = body.get("actionId");
            actionId = value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
        }
        try {
            return agentAskService.resumeFlux(runKey, actor, actionId);
        } catch (BusinessException e) {
            log.warn("续跑前置校验失败 runKey={}: {}", runKey, e.getMessage());
            return Flux.just(errorJson(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 后台续跑(审批中心外带路径):202 受理,终态落 chat_history。
     */
    @PostMapping("/{runKey}/resume-async")
    public Result<Void> resumeAsync(@PathVariable String runKey,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) {
            return Result.unauthorized();
        }
        AgentRun run = runStateService.getByRunKey(runKey);
        if (run == null) {
            return Result.notFound("Agent 运行不存在");
        }
        boolean allowed = actor.equals(run.getUserId());
        for (AgentAction action : agentActionService.listByRun(run.getId())) {
            if (actor.equals(action.getApproverId())) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            return Result.forbidden("仅运行发起人或动作审批人可续跑");
        }
        Long actionId = null;
        if (body != null && body.get("actionId") != null) {
            Object value = body.get("actionId");
            actionId = value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
        }
        try {
            agentAskService.resumeFlux(runKey, run.getUserId(), actionId)
                    .subscribe(value -> {
                    }, e -> log.warn("后台续跑失败 runKey={}: {}", runKey, e.getMessage()));
            return Result.success("已受理,运行将在后台继续执行", null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    private String errorJson(int code, String message) {
        String safe = message == null ? "未知错误"
                : message.replace("\\", "/").replace("\"", "'").replace("\n", " ");
        return "{\"type\":\"error\",\"code\":" + code + ",\"message\":\"" + safe + "\"}";
    }

    private String cap(String value) {
        if (value == null) return null;
        return value.length() > DETAIL_PREVIEW_CAP ? value.substring(0, DETAIL_PREVIEW_CAP) + "..." : value;
    }
}
