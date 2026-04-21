package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.service.EvaluationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * 生成测试集并运行评估
     */
    @PostMapping("/generate-testset")
    public Result<EvaluationTask> generateTestset(@RequestBody Map<String, Object> params) {
        Long knowledgeId = Long.valueOf(params.get("knowledgeId").toString());
        int qaCount = params.containsKey("qaCount") ? Integer.parseInt(params.get("qaCount").toString()) : 10;
        String taskName = params.containsKey("taskName") ? params.get("taskName").toString() : null;

        EvaluationTask task = evaluationService.generateAndRun(knowledgeId, qaCount, taskName);
        return Result.success(task);
    }

    /**
     * 获取评估任务详情
     */
    @GetMapping("/task/{id}")
    public Result<EvaluationTask> getTaskDetail(@PathVariable Long id) {
        EvaluationTask task = evaluationService.getTaskDetail(id);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 获取任务的QA列表
     */
    @GetMapping("/task/{id}/qa-list")
    public Result<List<EvaluationQa>> getTaskQaList(@PathVariable Long id) {
        List<EvaluationQa> qaList = evaluationService.getTaskQaList(id);
        return Result.success(qaList);
    }

    /**
     * 获取评估任务列表
     */
    @GetMapping("/list")
    public Result<List<EvaluationTask>> list(@RequestParam(required = false) Long knowledgeId) {
        List<EvaluationTask> tasks;
        if (knowledgeId != null) {
            tasks = evaluationService.listByKnowledgeId(knowledgeId);
        } else {
            tasks = evaluationService.listAll();
        }
        return Result.success(tasks);
    }

    /**
     * 删除评估任务
     */
    @PostMapping("/task/{id}/delete")
    public Result<Void> deleteTask(@PathVariable Long id) {
        evaluationService.deleteTask(id);
        return Result.success();
    }
}
