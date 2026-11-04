package cn.sdh.backend.controller;

import cn.sdh.backend.agent.evaluation.AgentEvaluationService;
import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import java.util.List;
import static cn.sdh.backend.agent.evaluation.AgentEvaluationData.*;

@RestController
@RequestMapping("/api/agent/evaluation")
public class AgentEvaluationController {
    private final AgentEvaluationService service;
    public AgentEvaluationController(AgentEvaluationService service) { this.service = service; }
    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> invalidRequest() { return Result.paramError("场景请求格式错误，请检查必填字段、JSON 格式和风险等级"); }
    @PostMapping("/datasets") public Result<Dataset> importDataset(@RequestBody ImportRequest request) {
        return Result.success(service.importDataset(request, UserContext.getCurrentUserId()));
    }
    @GetMapping("/datasets") public Result<List<DatasetSummary>> datasets() {
        return Result.success(service.datasets(UserContext.getCurrentUserId()));
    }
    @GetMapping("/datasets/{id}") public Result<Dataset> dataset(@PathVariable String id) {
        return Result.success(service.dataset(id, UserContext.getCurrentUserId()));
    }
    @PostMapping("/records") public Result<EvaluationRecord> record(@RequestBody RecordRequest request) {
        return Result.success(service.record(request, UserContext.getCurrentUserId()));
    }
    @GetMapping("/records") public Result<List<RecordSummary>> records(@RequestParam String datasetId) {
        return Result.success(service.records(datasetId, UserContext.getCurrentUserId()));
    }
    @GetMapping("/records/{id}") public Result<EvaluationRecord> detail(@PathVariable String id) {
        return Result.success(service.detail(id, UserContext.getCurrentUserId()));
    }
}
