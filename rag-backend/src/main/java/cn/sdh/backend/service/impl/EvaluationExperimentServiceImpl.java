package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.EvaluationExperimentDetail;
import cn.sdh.backend.dto.EvaluationExperimentRequest;
import cn.sdh.backend.dto.EvaluationRunConfig;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.mapper.*;
import cn.sdh.backend.service.EvaluationExperimentService;
import cn.sdh.backend.service.EvaluationExperimentFinalizer;
import cn.sdh.backend.service.EvaluationService;
import cn.sdh.backend.service.KnowledgeAccessService;
import cn.sdh.backend.service.TestDatasetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationExperimentServiceImpl implements EvaluationExperimentService {
    private static final Set<String> MODES = Set.of("RETRIEVAL", "GENERATION", "FULL");

    private final EvaluationExperimentMapper experimentMapper;
    private final EvaluationExperimentVariantMapper variantMapper;
    private final EvaluationExperimentRunMapper runMapper;
    private final EvaluationExperimentRunTaskMapper runTaskMapper;
    private final EvaluationTaskMapper taskMapper;
    private final EvaluationService evaluationService;
    private final TestDatasetService datasetService;
    private final KnowledgeAccessService knowledgeAccess;
    private final EvaluationExperimentFinalizer finalizer;

    @Override
    public List<EvaluationExperiment> list() {
        Long actor = requireActor();
        return experimentMapper.selectList(new LambdaQueryWrapper<EvaluationExperiment>()
                .eq(EvaluationExperiment::getUserId, actor)
                .orderByDesc(EvaluationExperiment::getCreateTime));
    }

    @Override
    public EvaluationExperimentDetail detail(Long id) {
        EvaluationExperiment experiment = requireOwned(id, false);
        List<EvaluationExperimentVariant> variants = variants(id);
        List<EvaluationExperimentRun> runs = runMapper.selectList(new LambdaQueryWrapper<EvaluationExperimentRun>()
                .eq(EvaluationExperimentRun::getExperimentId, id)
                .orderByDesc(EvaluationExperimentRun::getCreateTime)
                .last("LIMIT 20"));
        List<EvaluationExperimentDetail.RunView> views = runs.stream()
                .map(run -> toRunView(run, variants))
                .toList();
        return new EvaluationExperimentDetail(experiment, variants, views);
    }

    @Override
    @Transactional
    public EvaluationExperiment create(EvaluationExperimentRequest request) {
        Long actor = requireActor();
        validateRequest(request, actor);
        LocalDateTime now = LocalDateTime.now();
        EvaluationExperiment experiment = new EvaluationExperiment();
        experiment.setName(request.getName().trim());
        experiment.setDescription(request.getDescription());
        experiment.setKnowledgeId(request.getKnowledgeId());
        experiment.setDatasetId(request.getDatasetId());
        experiment.setStatus("ACTIVE");
        experiment.setScheduleEnabled(Boolean.TRUE.equals(request.getScheduleEnabled()));
        experiment.setCronExpression(normalizeCron(request));
        experiment.setJudgeBudget(Math.max(0, Optional.ofNullable(request.getJudgeBudget()).orElse(0)));
        experiment.setMaxRetries(Math.max(0, Math.min(3, Optional.ofNullable(request.getMaxRetries()).orElse(1))));
        experiment.setAlertOnRegression(!Boolean.FALSE.equals(request.getAlertOnRegression()));
        experiment.setUserId(actor);
        experiment.setCreateTime(now);
        experiment.setUpdateTime(now);
        calculateNextRun(experiment, now);
        experimentMapper.insert(experiment);

        int order = 0;
        for (EvaluationExperimentRequest.Variant input : request.getVariants()) {
            EvaluationExperimentVariant variant = new EvaluationExperimentVariant();
            variant.setExperimentId(experiment.getId());
            variant.setName(input.getName().trim());
            variant.setEvaluationMode(input.getEvaluationMode().trim().toUpperCase(Locale.ROOT));
            variant.setTopK(Math.max(1, Math.min(20, Optional.ofNullable(input.getTopK()).orElse(5))));
            variant.setJudgeModel(blankToNull(input.getJudgeModel()));
            variant.setBaselineTaskId(input.getBaselineTaskId());
            variant.setSortOrder(order++);
            variant.setCreateTime(now);
            variantMapper.insert(variant);
        }
        return experiment;
    }

    @Override
    @Transactional
    public void toggle(Long id) {
        EvaluationExperiment experiment = requireOwned(id, true);
        experiment.setStatus("ACTIVE".equals(experiment.getStatus()) ? "PAUSED" : "ACTIVE");
        experiment.setUpdateTime(LocalDateTime.now());
        calculateNextRun(experiment, LocalDateTime.now());
        experimentMapper.updateById(experiment);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        EvaluationExperiment experiment = requireOwned(id, true);
        if (hasRunningRun(id)) throw new IllegalArgumentException("实验仍在运行，暂时不能删除");
        List<EvaluationExperimentRun> runs = runMapper.selectList(new LambdaQueryWrapper<EvaluationExperimentRun>()
                .eq(EvaluationExperimentRun::getExperimentId, id));
        if (!runs.isEmpty()) {
            runTaskMapper.delete(new LambdaQueryWrapper<EvaluationExperimentRunTask>()
                    .in(EvaluationExperimentRunTask::getRunId, runs.stream().map(EvaluationExperimentRun::getId).toList()));
        }
        runMapper.delete(new LambdaQueryWrapper<EvaluationExperimentRun>()
                .eq(EvaluationExperimentRun::getExperimentId, id));
        variantMapper.delete(new LambdaQueryWrapper<EvaluationExperimentVariant>()
                .eq(EvaluationExperimentVariant::getExperimentId, id));
        experimentMapper.deleteById(id);
    }

    @Override
    public EvaluationExperimentRun runNow(Long id) {
        return launch(requireOwned(id, true), "MANUAL");
    }

    @Override
    @Scheduled(fixedDelayString = "${evaluation.experiments.scheduler-delay-ms:30000}")
    public void schedulerTick() {
        try {
            reconcileRuns();
            LocalDateTime now = LocalDateTime.now();
            List<EvaluationExperiment> due = experimentMapper.selectList(new LambdaQueryWrapper<EvaluationExperiment>()
                    .eq(EvaluationExperiment::getStatus, "ACTIVE")
                    .eq(EvaluationExperiment::getScheduleEnabled, true)
                    .isNotNull(EvaluationExperiment::getNextRunTime)
                    .le(EvaluationExperiment::getNextRunTime, now));
            for (EvaluationExperiment experiment : due) {
                try {
                    if (!hasRunningRun(experiment.getId())) launch(experiment, "SCHEDULED");
                } catch (Exception e) {
                    log.error("定时评测实验启动失败: experimentId={}", experiment.getId(), e);
                    finalizer.publishSchedulingFailure(experiment, e.getMessage());
                } finally {
                    experiment.setLastRunTime(now);
                    calculateNextRun(experiment, now);
                    experiment.setUpdateTime(now);
                    experimentMapper.updateById(experiment);
                }
            }
        } catch (Exception e) {
            log.error("评测实验调度扫描失败", e);
        }
    }

    private EvaluationExperimentRun launch(EvaluationExperiment experiment, String triggerType) {
        if (hasRunningRun(experiment.getId())) throw new IllegalArgumentException("该实验已有运行中的批次");
        knowledgeAccess.requireEdit(experiment.getKnowledgeId(), experiment.getUserId());
        TestDataset dataset = datasetService.getDatasetDetail(experiment.getDatasetId());
        if (dataset == null) throw new IllegalArgumentException("实验数据集不存在");
        List<EvaluationExperimentVariant> variants = variants(experiment.getId());
        validateBudget(experiment, dataset, variants);

        EvaluationExperimentRun run = new EvaluationExperimentRun();
        run.setExperimentId(experiment.getId());
        run.setTriggerType(triggerType);
        run.setStatus("LAUNCHING");
        run.setStartTime(LocalDateTime.now());
        run.setCreateTime(LocalDateTime.now());
        runMapper.insert(run);
        try {
            for (EvaluationExperimentVariant variant : variants) launchVariant(experiment, run, variant, 0);
            run.setStatus("RUNNING");
            runMapper.updateById(run);
            experiment.setLastRunTime(LocalDateTime.now());
            experiment.setUpdateTime(LocalDateTime.now());
            experimentMapper.updateById(experiment);
            return run;
        } catch (Exception e) {
            finalizer.finalizeRun(experiment, run, "FAILED", e.getMessage(), false);
            throw e;
        }
    }

    private void launchVariant(EvaluationExperiment experiment, EvaluationExperimentRun run,
                               EvaluationExperimentVariant variant, int attempt) {
        EvaluationRunConfig config = new EvaluationRunConfig();
        config.setEvaluationMode(variant.getEvaluationMode());
        config.setTopK(variant.getTopK());
        config.setJudgeModel(variant.getJudgeModel());
        config.setBaselineTaskId(variant.getBaselineTaskId());
        EvaluationTask task = evaluationService.runDatasetEvaluationForUser(
                experiment.getDatasetId(), experiment.getKnowledgeId(), config, experiment.getUserId(),
                experiment.getName() + " / " + variant.getName() + " / #" + run.getId());
        EvaluationExperimentRunTask link = new EvaluationExperimentRunTask();
        link.setRunId(run.getId());
        link.setVariantId(variant.getId());
        link.setTaskId(task.getId());
        link.setAttempt(attempt);
        link.setCreateTime(LocalDateTime.now());
        runTaskMapper.insert(link);
    }

    private void reconcileRuns() {
        List<EvaluationExperimentRun> running = runMapper.selectList(new LambdaQueryWrapper<EvaluationExperimentRun>()
                .eq(EvaluationExperimentRun::getStatus, "RUNNING"));
        for (EvaluationExperimentRun run : running) {
            EvaluationExperiment experiment = experimentMapper.selectById(run.getExperimentId());
            if (experiment == null) continue;
            List<EvaluationExperimentVariant> variants = variants(experiment.getId());
            List<EvaluationExperimentRunTask> links = runTaskMapper.selectList(
                    new LambdaQueryWrapper<EvaluationExperimentRunTask>()
                            .eq(EvaluationExperimentRunTask::getRunId, run.getId())
                            .orderByDesc(EvaluationExperimentRunTask::getAttempt));
            Map<Long, EvaluationExperimentRunTask> latest = links.stream().collect(Collectors.toMap(
                    EvaluationExperimentRunTask::getVariantId, Function.identity(), (a, b) -> a));
            boolean pending = false;
            String failure = null;
            List<EvaluationTask> completed = new ArrayList<>();
            for (EvaluationExperimentVariant variant : variants) {
                EvaluationExperimentRunTask link = latest.get(variant.getId());
                EvaluationTask task = link == null ? null : taskMapper.selectById(link.getTaskId());
                if (task == null || task.getStatus() == null || task.getStatus() < 2) {
                    pending = true;
                } else if (task.getStatus() == 3) {
                    int attempt = Optional.ofNullable(link.getAttempt()).orElse(0);
                    if (attempt < Optional.ofNullable(experiment.getMaxRetries()).orElse(0)) {
                        launchVariant(experiment, run, variant, attempt + 1);
                        pending = true;
                    } else {
                        failure = "变体“" + variant.getName() + "”执行失败：" + task.getErrorMessage();
                    }
                } else {
                    completed.add(task);
                }
            }
            if (failure != null) {
                finalizer.finalizeRun(experiment, run, "FAILED", failure, false);
            } else if (!pending && completed.size() == variants.size()) {
                boolean regression = Boolean.TRUE.equals(experiment.getAlertOnRegression())
                        && completed.stream().anyMatch(t -> "FAILED".equals(t.getRegressionStatus()));
                finalizer.finalizeRun(experiment, run, "COMPLETED", null, regression);
            }
        }
    }

    private void validateRequest(EvaluationExperimentRequest request, Long actor) {
        if (request == null || !StringUtils.hasText(request.getName())) throw new IllegalArgumentException("实验名称不能为空");
        if (request.getKnowledgeId() == null || request.getDatasetId() == null) throw new IllegalArgumentException("请选择知识库和数据集");
        knowledgeAccess.requireEdit(request.getKnowledgeId(), actor);
        TestDataset dataset = datasetService.getDatasetDetail(request.getDatasetId());
        if (dataset == null) throw new IllegalArgumentException("数据集不存在");
        if (!"builtin".equals(dataset.getDatasetType()) && !Objects.equals(dataset.getUserId(), actor)) {
            throw new IllegalArgumentException("不能使用其他用户的自定义数据集");
        }
        if (request.getVariants() == null || request.getVariants().size() < 2) throw new IllegalArgumentException("实验至少需要两个对比变体");
        Set<String> names = new HashSet<>();
        for (EvaluationExperimentRequest.Variant variant : request.getVariants()) {
            if (!StringUtils.hasText(variant.getName()) || !names.add(variant.getName().trim())) throw new IllegalArgumentException("变体名称不能为空且不能重复");
            String mode = Optional.ofNullable(variant.getEvaluationMode()).orElse("RETRIEVAL").trim().toUpperCase(Locale.ROOT);
            if (!MODES.contains(mode)) throw new IllegalArgumentException("评测模式不正确: " + mode);
        }
        normalizeCron(request);
    }

    private void validateBudget(EvaluationExperiment experiment, TestDataset dataset, List<EvaluationExperimentVariant> variants) {
        int calls = variants.stream().mapToInt(v -> "RETRIEVAL".equals(v.getEvaluationMode()) ? 0 : 1).sum()
                * Optional.ofNullable(dataset.getItemCount()).orElse(0);
        if (experiment.getJudgeBudget() != null && experiment.getJudgeBudget() > 0 && calls > experiment.getJudgeBudget()) {
            throw new IllegalArgumentException("预计需要 " + calls + " 次模型评审，超过单次预算 " + experiment.getJudgeBudget());
        }
    }

    private String normalizeCron(EvaluationExperimentRequest request) {
        if (!Boolean.TRUE.equals(request.getScheduleEnabled())) return null;
        if (!StringUtils.hasText(request.getCronExpression())) throw new IllegalArgumentException("启用定时回归时 Cron 不能为空");
        try {
            CronExpression.parse(request.getCronExpression().trim());
            return request.getCronExpression().trim();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cron 表达式不正确");
        }
    }

    private void calculateNextRun(EvaluationExperiment experiment, LocalDateTime from) {
        if (!"ACTIVE".equals(experiment.getStatus()) || !Boolean.TRUE.equals(experiment.getScheduleEnabled())
                || !StringUtils.hasText(experiment.getCronExpression())) {
            experiment.setNextRunTime(null);
            return;
        }
        experiment.setNextRunTime(CronExpression.parse(experiment.getCronExpression()).next(from));
    }

    private EvaluationExperimentDetail.RunView toRunView(EvaluationExperimentRun run,
                                                          List<EvaluationExperimentVariant> variants) {
        Map<Long, EvaluationExperimentVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(EvaluationExperimentVariant::getId, Function.identity()));
        List<EvaluationExperimentRunTask> links = runTaskMapper.selectList(
                new LambdaQueryWrapper<EvaluationExperimentRunTask>()
                        .eq(EvaluationExperimentRunTask::getRunId, run.getId())
                        .orderByDesc(EvaluationExperimentRunTask::getAttempt));
        Set<Long> seen = new HashSet<>();
        List<EvaluationExperimentDetail.VariantResult> results = new ArrayList<>();
        for (EvaluationExperimentRunTask link : links) {
            if (!seen.add(link.getVariantId())) continue;
            results.add(new EvaluationExperimentDetail.VariantResult(
                    variantMap.get(link.getVariantId()), taskMapper.selectById(link.getTaskId()), link.getAttempt()));
        }
        return new EvaluationExperimentDetail.RunView(run, results);
    }

    private List<EvaluationExperimentVariant> variants(Long experimentId) {
        return variantMapper.selectList(new LambdaQueryWrapper<EvaluationExperimentVariant>()
                .eq(EvaluationExperimentVariant::getExperimentId, experimentId)
                .orderByAsc(EvaluationExperimentVariant::getSortOrder));
    }

    private boolean hasRunningRun(Long experimentId) {
        return runMapper.selectCount(new LambdaQueryWrapper<EvaluationExperimentRun>()
                .eq(EvaluationExperimentRun::getExperimentId, experimentId)
                .in(EvaluationExperimentRun::getStatus, "LAUNCHING", "RUNNING")) > 0;
    }

    private EvaluationExperiment requireOwned(Long id, boolean edit) {
        EvaluationExperiment experiment = experimentMapper.selectById(id);
        if (experiment == null) throw new IllegalArgumentException("评测实验不存在");
        Long actor = requireActor();
        if (!Objects.equals(experiment.getUserId(), actor)) throw new IllegalArgumentException("无权访问该评测实验");
        if (edit) knowledgeAccess.requireEdit(experiment.getKnowledgeId(), actor);
        else knowledgeAccess.requireRead(experiment.getKnowledgeId(), actor);
        return experiment;
    }

    private Long requireActor() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new IllegalStateException("用户未登录");
        return actor;
    }

    private String blankToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String cap(String value) { return value == null ? null : value.substring(0, Math.min(1000, value.length())); }
}
