package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.EvaluationExperimentDetail;
import cn.sdh.backend.dto.EvaluationExperimentRequest;
import cn.sdh.backend.entity.EvaluationExperiment;
import cn.sdh.backend.entity.EvaluationExperimentRun;
import cn.sdh.backend.service.EvaluationExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation/experiment")
@RequiredArgsConstructor
public class EvaluationExperimentController {
    private final EvaluationExperimentService experimentService;

    @GetMapping("/list")
    public Result<List<EvaluationExperiment>> list() { return Result.success(experimentService.list()); }

    @GetMapping("/{id}")
    public Result<EvaluationExperimentDetail> detail(@PathVariable Long id) { return Result.success(experimentService.detail(id)); }

    @PostMapping
    public Result<EvaluationExperiment> create(@RequestBody EvaluationExperimentRequest request) {
        return Result.success(experimentService.create(request));
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) { experimentService.toggle(id); return Result.success(); }

    @PostMapping("/{id}/run")
    public Result<EvaluationExperimentRun> run(@PathVariable Long id) { return Result.success(experimentService.runNow(id)); }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { experimentService.delete(id); return Result.success(); }
}
