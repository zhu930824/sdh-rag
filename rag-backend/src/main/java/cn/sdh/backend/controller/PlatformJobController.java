package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.PlatformJobView;
import cn.sdh.backend.entity.PlatformJob;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.mapper.EvaluationTaskMapper;
import cn.sdh.backend.service.PlatformJobService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jobs")
public class PlatformJobController {
    private final PlatformJobService jobs;
    private final EvaluationTaskMapper evaluationTasks;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String jobType,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        IPage<PlatformJob> result = jobs.listMine(UserContext.getCurrentUserId(), status, jobType, page, pageSize);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", result.getRecords().stream().map(PlatformJobView::from).toList());
        response.put("total", result.getTotal());
        return Result.success(response);
    }

    @GetMapping("/{id}")
    public Result<PlatformJobView> detail(@PathVariable Long id) {
        return Result.success(PlatformJobView.from(jobs.requireOwned(id, UserContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public Result<Void> cancel(@PathVariable Long id) {
        PlatformJob job = jobs.requireOwned(id, UserContext.getCurrentUserId());
        jobs.cancel(id, UserContext.getCurrentUserId());
        syncEvaluationState(job, 4, "取消请求已提交");
        return Result.success("取消请求已提交", null);
    }

    @PostMapping("/{id}/replay")
    @Transactional
    public Result<Void> replay(@PathVariable Long id) {
        PlatformJob job = jobs.requireOwned(id, UserContext.getCurrentUserId());
        jobs.replay(id, UserContext.getCurrentUserId());
        syncEvaluationState(job, 0, null);
        return Result.success("任务已重新进入队列", null);
    }

    private void syncEvaluationState(PlatformJob job, int status, String error) {
        if (!"EVALUATION".equals(job.getResourceType()) || job.getResourceId() == null) return;
        EvaluationTask task = evaluationTasks.selectById(job.getResourceId());
        if (task == null) return;
        task.setStatus(status);
        task.setErrorMessage(error);
        task.setUpdateTime(java.time.LocalDateTime.now());
        evaluationTasks.updateById(task);
    }
}
