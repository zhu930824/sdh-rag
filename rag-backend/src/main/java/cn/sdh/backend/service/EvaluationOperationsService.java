package cn.sdh.backend.service;

import cn.sdh.backend.dto.EvaluationHumanReviewRequest;
import cn.sdh.backend.dto.EvaluationJudgeAlignment;
import cn.sdh.backend.dto.EvaluationSliceMetric;
import cn.sdh.backend.entity.EvaluationQa;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface EvaluationOperationsService {
    List<EvaluationSliceMetric> slices(Long taskId, String dimension);
    IPage<EvaluationQa> reviewQueue(Long taskId, String status, int page, int pageSize);
    void review(Long qaId, EvaluationHumanReviewRequest request);
    EvaluationJudgeAlignment alignment(Long taskId);
}
