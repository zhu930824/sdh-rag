package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.EvaluationHumanReviewRequest;
import cn.sdh.backend.dto.EvaluationJudgeAlignment;
import cn.sdh.backend.dto.EvaluationSliceMetric;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.mapper.EvaluationQaMapper;
import cn.sdh.backend.mapper.EvaluationTaskMapper;
import cn.sdh.backend.service.EvaluationOperationsService;
import cn.sdh.backend.service.KnowledgeAccessService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationOperationsServiceImpl implements EvaluationOperationsService {
    private static final Set<String> DIMENSIONS = Set.of("category", "difficulty", "businessLine", "userRole");
    private final EvaluationQaMapper qaMapper;
    private final EvaluationTaskMapper taskMapper;
    private final KnowledgeAccessService knowledgeAccess;

    @Override
    public List<EvaluationSliceMetric> slices(Long taskId, String dimension) {
        requireTask(taskId, false);
        String selected = DIMENSIONS.contains(dimension) ? dimension : "category";
        List<EvaluationQa> rows = qaMapper.selectList(new LambdaQueryWrapper<EvaluationQa>()
                .eq(EvaluationQa::getTaskId, taskId));
        Map<String, List<EvaluationQa>> groups = rows.stream().collect(Collectors.groupingBy(
                qa -> Optional.ofNullable(dimensionValue(qa, selected)).filter(v -> !v.isBlank()).orElse("未标注"),
                LinkedHashMap::new, Collectors.toList()));
        return groups.entrySet().stream()
                .map(entry -> aggregate(selected, entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(EvaluationSliceMetric::sampleCount).reversed())
                .toList();
    }

    @Override
    public IPage<EvaluationQa> reviewQueue(Long taskId, String status, int page, int pageSize) {
        List<Long> taskIds;
        if (taskId != null) {
            requireTask(taskId, false);
            taskIds = List.of(taskId);
        } else {
            List<Long> readable = knowledgeAccess.readableKnowledgeIds(requireActor());
            if (readable.isEmpty()) return new Page<>(page, pageSize);
            taskIds = taskMapper.selectList(new LambdaQueryWrapper<EvaluationTask>()
                            .select(EvaluationTask::getId)
                            .in(EvaluationTask::getKnowledgeId, readable))
                    .stream().map(EvaluationTask::getId).toList();
            if (taskIds.isEmpty()) return new Page<>(page, pageSize);
        }
        String selected = Optional.ofNullable(status).orElse("PENDING").toUpperCase(Locale.ROOT);
        LambdaQueryWrapper<EvaluationQa> query = new LambdaQueryWrapper<EvaluationQa>()
                .in(EvaluationQa::getTaskId, taskIds)
                .isNotNull(EvaluationQa::getOverallScore);
        if ("PENDING".equals(selected)) {
            query.and(q -> q.eq(EvaluationQa::getHumanReviewStatus, "PENDING")
                    .or().isNull(EvaluationQa::getHumanReviewStatus));
        } else {
            query.eq(EvaluationQa::getHumanReviewStatus, selected);
        }
        query.orderByDesc(EvaluationQa::getCreateTime);
        return qaMapper.selectPage(new Page<>(Math.max(1, page), Math.min(100, Math.max(1, pageSize))), query);
    }

    @Override
    @Transactional
    public void review(Long qaId, EvaluationHumanReviewRequest request) {
        EvaluationQa qa = qaMapper.selectById(qaId);
        if (qa == null) throw new IllegalArgumentException("评测样本不存在");
        requireTask(qa.getTaskId(), true);
        String status = Optional.ofNullable(request.getStatus()).orElse("COMPLETED").toUpperCase(Locale.ROOT);
        if (!Set.of("COMPLETED", "SKIPPED").contains(status)) throw new IllegalArgumentException("人工复核状态不正确");
        qa.setHumanReviewStatus(status);
        if ("COMPLETED".equals(status)) {
            qa.setHumanFaithfulness(normalize(request.getFaithfulness()));
            qa.setHumanAnswerRelevancy(normalize(request.getAnswerRelevancy()));
            qa.setHumanCompleteness(normalize(request.getCompleteness()));
            qa.setHumanFluency(normalize(request.getFluency()));
            qa.setHumanOverallScore(normalize(request.getOverallScore()));
        }
        qa.setHumanReviewNote(request.getNote());
        qa.setHumanReviewerId(requireActor());
        qa.setHumanReviewTime(LocalDateTime.now());
        qaMapper.updateById(qa);
    }

    @Override
    public EvaluationJudgeAlignment alignment(Long taskId) {
        requireTask(taskId, false);
        List<EvaluationQa> rows = qaMapper.selectList(new LambdaQueryWrapper<EvaluationQa>()
                .eq(EvaluationQa::getTaskId, taskId)
                .eq(EvaluationQa::getHumanReviewStatus, "COMPLETED"));
        return new EvaluationJudgeAlignment(taskId, rows.size(),
                mae(rows, EvaluationQa::getFaithfulness, EvaluationQa::getHumanFaithfulness),
                mae(rows, EvaluationQa::getAnswerRelevancy, EvaluationQa::getHumanAnswerRelevancy),
                mae(rows, EvaluationQa::getCompleteness, EvaluationQa::getHumanCompleteness),
                mae(rows, EvaluationQa::getFluency, EvaluationQa::getHumanFluency),
                mae(rows, EvaluationQa::getOverallScore, EvaluationQa::getHumanOverallScore),
                withinOneLevel(rows));
    }

    private EvaluationSliceMetric aggregate(String dimension, String value, List<EvaluationQa> rows) {
        return new EvaluationSliceMetric(dimension, value, rows.size(),
                (int) rows.stream().filter(q -> q.getFailureCategory() != null).count(),
                average(rows, EvaluationQa::getRecallAtK),
                average(rows, EvaluationQa::getReciprocalRank),
                average(rows, EvaluationQa::getFaithfulness),
                average(rows, EvaluationQa::getAnswerRelevancy),
                average(rows, EvaluationQa::getOverallScore));
    }

    private BigDecimal average(List<EvaluationQa> rows, Function<EvaluationQa, BigDecimal> getter) {
        DoubleSummaryStatistics stats = rows.stream().map(getter).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).summaryStatistics();
        return stats.getCount() == 0 ? null : decimal(stats.getAverage());
    }

    private BigDecimal mae(List<EvaluationQa> rows, Function<EvaluationQa, BigDecimal> automated,
                           Function<EvaluationQa, BigDecimal> human) {
        DoubleSummaryStatistics stats = rows.stream().filter(q -> automated.apply(q) != null && human.apply(q) != null)
                .mapToDouble(q -> automated.apply(q).subtract(human.apply(q)).abs().doubleValue()).summaryStatistics();
        return stats.getCount() == 0 ? null : decimal(stats.getAverage());
    }

    private BigDecimal withinOneLevel(List<EvaluationQa> rows) {
        long eligible = rows.stream().filter(q -> q.getOverallScore() != null && q.getHumanOverallScore() != null).count();
        if (eligible == 0) return null;
        long matched = rows.stream().filter(q -> q.getOverallScore() != null && q.getHumanOverallScore() != null)
                .filter(q -> q.getOverallScore().subtract(q.getHumanOverallScore()).abs().compareTo(BigDecimal.valueOf(0.2)) <= 0)
                .count();
        return decimal((double) matched / eligible);
    }

    private String dimensionValue(EvaluationQa qa, String dimension) {
        return switch (dimension) {
            case "difficulty" -> qa.getDifficulty();
            case "businessLine" -> qa.getBusinessLine();
            case "userRole" -> qa.getUserRole();
            default -> qa.getCategory();
        };
    }

    private EvaluationTask requireTask(Long taskId, boolean edit) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("评测任务不存在");
        if (edit) knowledgeAccess.requireEdit(task.getKnowledgeId(), requireActor());
        else knowledgeAccess.requireRead(task.getKnowledgeId(), requireActor());
        return task;
    }

    private Long requireActor() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new IllegalStateException("用户未登录");
        return actor;
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) return null;
        BigDecimal normalized = value.compareTo(BigDecimal.ONE) > 0
                ? value.divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP) : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("人工评分应在 1-5 之间");
        }
        return normalized.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(double value) { return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP); }
}
