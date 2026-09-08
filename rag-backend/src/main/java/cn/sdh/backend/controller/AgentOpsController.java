package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.AgentImprovementItem;
import cn.sdh.backend.service.AgentImprovementService;
import cn.sdh.backend.service.AgentOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/ops")
public class AgentOpsController {
    private final AgentOpsService opsService;
    private final AgentImprovementService improvementService;

    @GetMapping("/report")
    public Result<AgentOpsService.OpsReport> report(@RequestParam(defaultValue = "30") int days) {
        return Result.success(opsService.report(days));
    }

    @GetMapping("/improvements")
    public Result<List<AgentImprovementItem>> improvements(@RequestParam(required = false) String status) {
        return Result.success(improvementService.list(status));
    }

    @PostMapping("/improvements/{id}")
    public Result<AgentImprovementItem> review(@PathVariable Long id,
                                               @RequestBody AgentImprovementService.ReviewCommand command) {
        return Result.success("改进项已更新", improvementService.review(id, command));
    }
}
