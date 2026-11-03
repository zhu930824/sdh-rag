package cn.sdh.backend.controller;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentActionFeedback;
import cn.sdh.backend.service.AgentActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent-actions")
public class AgentActionController {
    private final AgentActionService actionService;

    @PostMapping
    public Result<AgentAction> request(@RequestBody AgentActionService.ActionCommand command) {
        if (command.runId() != null || command.stepId() != null || command.workflowExecutionId() != null || command.fence() != null) {
            throw new BusinessException(400, "运行和工作流关联只能由服务端执行器建立");
        }
        return Result.success("Agent 动作已受理", actionService.request(command));
    }

    @GetMapping
    public Result<List<AgentAction>> list(@RequestParam(required = false) String status) {
        return Result.success(actionService.listMine(status));
    }

    @GetMapping("/pending")
    public Result<List<AgentAction>> pending() { return Result.success(actionService.listPending()); }

    @GetMapping("/{id}")
    public Result<AgentAction> detail(@PathVariable Long id) { return Result.success(actionService.get(id)); }

    @PostMapping("/{id}/approve")
    public Result<AgentAction> approve(@PathVariable Long id,
                                       @RequestHeader(value = "X-Agent-Resume", required = false) String resumeHeader) {
        AgentAction action = actionService.approve(id);
        return Result.success("审批通过，动作已进入执行队列", action);
    }

    @PostMapping("/{id}/reject")
    public Result<AgentAction> reject(@PathVariable Long id,
                                      @RequestBody(required = false) AgentActionService.DecisionCommand command,
                                      @RequestHeader(value = "X-Agent-Resume", required = false) String resumeHeader) {
        AgentAction action = actionService.reject(id, command);
        return Result.success("动作已拒绝", action);
    }

    @PostMapping("/{id}/retry")
    public Result<AgentAction> retry(@PathVariable Long id) {
        AgentAction action = actionService.retry(id);
        return Result.success("动作已加入重试队列", action);
    }

    @PostMapping("/{id}/feedback")
    public Result<AgentActionFeedback> feedback(@PathVariable Long id,
                                                @RequestBody AgentActionService.FeedbackCommand command) {
        return Result.success("业务结果已回流", actionService.feedback(id, command));
    }

    @PostMapping("/{id}/reconcile")
    public Result<AgentAction> reconcile(@PathVariable Long id,
                                         @RequestBody AgentActionService.ReconcileCommand command) {
        AgentAction action = actionService.reconcile(id, command);
        return Result.success("核对结果已记录", action);
    }
}
