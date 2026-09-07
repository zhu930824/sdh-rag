package cn.sdh.backend.controller;

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
    public Result<AgentAction> approve(@PathVariable Long id) {
        return Result.success("审批通过并已触发执行", actionService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public Result<AgentAction> reject(@PathVariable Long id,
                                      @RequestBody(required = false) AgentActionService.DecisionCommand command) {
        return Result.success("动作已拒绝", actionService.reject(id, command));
    }

    @PostMapping("/{id}/retry")
    public Result<AgentAction> retry(@PathVariable Long id) {
        return Result.success("动作已重试", actionService.retry(id));
    }

    @PostMapping("/{id}/feedback")
    public Result<AgentActionFeedback> feedback(@PathVariable Long id,
                                                @RequestBody AgentActionService.FeedbackCommand command) {
        return Result.success("业务结果已回流", actionService.feedback(id, command));
    }
}
