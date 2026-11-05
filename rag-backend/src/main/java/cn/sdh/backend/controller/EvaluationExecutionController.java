package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.service.EvaluationExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationExecutionController {
    private final EvaluationExecutionService executions;

    @GetMapping("/tasks/{taskId}/executions")
    public Result<List<Map<String,Object>>> attempts(@PathVariable Long taskId) {
        return Result.success(executions.attempts(taskId,UserContext.getCurrentUserId()));
    }

    @GetMapping("/executions/{executionId}/results")
    public Result<List<Map<String,Object>>> results(@PathVariable String executionId,
            @RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="100") int limit) {
        return Result.success(executions.results(executionId,UserContext.getCurrentUserId(),offset,limit));
    }
}
