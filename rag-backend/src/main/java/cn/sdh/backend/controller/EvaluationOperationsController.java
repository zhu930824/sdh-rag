package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.EvaluationHumanReviewRequest;
import cn.sdh.backend.dto.EvaluationJudgeAlignment;
import cn.sdh.backend.dto.EvaluationSliceMetric;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.service.EvaluationOperationsService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation/operations")
@RequiredArgsConstructor
public class EvaluationOperationsController {
    private final EvaluationOperationsService operationsService;

    @GetMapping("/task/{taskId}/slices")
    public Result<List<EvaluationSliceMetric>> slices(@PathVariable Long taskId,
                                                      @RequestParam(defaultValue = "category") String dimension) {
        return Result.success(operationsService.slices(taskId, dimension));
    }

    @GetMapping("/review/queue")
    public Result<IPage<EvaluationQa>> queue(@RequestParam(required = false) Long taskId,
                                             @RequestParam(defaultValue = "PENDING") String status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(operationsService.reviewQueue(taskId, status, page, pageSize));
    }

    @PostMapping("/review/{qaId}")
    public Result<Void> review(@PathVariable Long qaId, @RequestBody EvaluationHumanReviewRequest request) {
        operationsService.review(qaId, request);
        return Result.success();
    }

    @GetMapping("/task/{taskId}/alignment")
    public Result<EvaluationJudgeAlignment> alignment(@PathVariable Long taskId) {
        return Result.success(operationsService.alignment(taskId));
    }
}
