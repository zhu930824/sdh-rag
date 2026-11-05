package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.DatasetInfo;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.dto.EvaluationRunConfig;
import cn.sdh.backend.dto.KnowledgeQualityReport;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.job.EvaluationJobHandler;
import cn.sdh.backend.job.EvaluationJobPayload;
import cn.sdh.backend.job.PlatformJobCancelledException;
import cn.sdh.backend.job.PlatformJobExecutionContext;
import cn.sdh.backend.mapper.EvaluationQaMapper;
import cn.sdh.backend.mapper.EvaluationTaskMapper;
import cn.sdh.backend.service.*;
import cn.sdh.backend.service.TestDatasetService;
import cn.sdh.backend.entity.TestDataset;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationTaskMapper taskMapper;
    private final EvaluationQaMapper qaMapper;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RagSearchService ragSearchService;
    private final ChatService chatService;
    private final TestDatasetService testDatasetService;
    private final ObjectMapper objectMapper;
    private final KnowledgeAccessService knowledgeAccess;
    private final PlatformJobService platformJobs;
    private final EvaluationExecutionService executions;

    private static final String QA_GENERATION_PROMPT = """
        请根据以下文档内容，生成一个高质量的问题和答案。

        文档内容：
        %s

        要求：
        1. 问题应该能从文档内容中找到答案
        2. 问题应该有一定的复杂度，不能太简单
        3. 答案应该是文档内容的准确概括或提取
        4. 输出JSON格式：{"question": "问题内容", "answer": "答案内容"}
        5. 只输出JSON，不要其他内容
        """;

    private static final String DATASETS_PATH = "datasets/";
    private static final Set<String> EVALUATION_MODES = Set.of("RETRIEVAL", "GENERATION", "FULL");
    private static final String ANSWER_PROMPT = """
            你是企业知识库问答助手。请只根据给定 Context 回答问题；没有依据时明确回答“根据现有知识无法回答”。
            问题：%s
            Context：
            %s
            """;
    private static final String JUDGE_PROMPT = """
            你是严格的 RAG 评测员。请根据问题、Context、待评估答案和参考答案评分。
            每项输出 1-5 的整数；忠实度要求答案内容能在 Context 找到依据。
            问题：%s
            Context：%s
            待评估答案：%s
            参考答案：%s
            只输出 JSON：{"contextPrecision":1,"contextRecall":1,"faithfulness":1,"answerRelevancy":1,"completeness":1,"fluency":1,"overall":1,"reason":"一句话理由"}
            """;

    public EvaluationServiceImpl(
            EvaluationTaskMapper taskMapper,
            EvaluationQaMapper qaMapper,
            VectorStoreService vectorStoreService,
            KnowledgeBaseService knowledgeBaseService,
            RagSearchService ragSearchService,
            ChatService chatService,
            TestDatasetService testDatasetService,
            ObjectMapper objectMapper,
            KnowledgeAccessService knowledgeAccess,
            PlatformJobService platformJobs,EvaluationExecutionService executions) {
        this.taskMapper = taskMapper;
        this.qaMapper = qaMapper;
        this.vectorStoreService = vectorStoreService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragSearchService = ragSearchService;
        this.chatService = chatService;
        this.testDatasetService = testDatasetService;
        this.objectMapper = objectMapper;
        this.knowledgeAccess = knowledgeAccess;
        this.platformJobs = platformJobs;
        this.executions=executions;
    }

    @Override
    @Transactional
    public EvaluationTask generateAndRun(Long knowledgeId, int qaCount, String taskName) {
        return generateAndRun(knowledgeId, qaCount, taskName, new EvaluationRunConfig());
    }

    @Override
    @Transactional
    public EvaluationTask generateAndRun(Long knowledgeId, int qaCount, String taskName, EvaluationRunConfig config) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        knowledgeAccess.requireEdit(knowledgeId, userId);

        EvaluationTask task = createTask(knowledgeId, taskName, qaCount, "generated", userId, config);
        taskMapper.insert(task);

        enqueueEvaluation(task, new EvaluationJobPayload(task.getId(), EvaluationJobPayload.GENERATED, List.of()));

        return task;
    }

    @Override
    @Transactional
    public EvaluationTask importAndRun(Long knowledgeId, List<EvaluationQaItem> items, String taskName) {
        return importAndRun(knowledgeId, items, taskName, new EvaluationRunConfig());
    }

    @Override
    @Transactional
    public EvaluationTask importAndRun(Long knowledgeId, List<EvaluationQaItem> items, String taskName, EvaluationRunConfig config) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        knowledgeAccess.requireEdit(knowledgeId, userId);
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("导入数据不能为空");
        }

        EvaluationTask task = createTask(knowledgeId, taskName, items.size(), "imported", userId, config);
        taskMapper.insert(task);

        enqueueEvaluation(task, new EvaluationJobPayload(task.getId(), EvaluationJobPayload.DATASET, items));

        return task;
    }

    @Override
    @Transactional
    public EvaluationTask runBuiltinDataset(String datasetName, Long knowledgeId) {
        return runBuiltinDataset(datasetName, knowledgeId, new EvaluationRunConfig());
    }

    @Override
    @Transactional
    public EvaluationTask runBuiltinDataset(String datasetName, Long knowledgeId, EvaluationRunConfig config) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        if (knowledgeId == null) {
            throw new RuntimeException("请选择知识库");
        }
        knowledgeAccess.requireEdit(knowledgeId, userId);

        List<EvaluationQaItem> items = loadBuiltinDataset(datasetName);
        if (items.isEmpty()) {
            throw new RuntimeException("数据集为空或不存在: " + datasetName);
        }

        String taskName = "内置数据集-" + datasetName;
        EvaluationTask task = createTask(knowledgeId, taskName, items.size(), "builtin", userId, config);
        taskMapper.insert(task);

        enqueueEvaluation(task, new EvaluationJobPayload(task.getId(), EvaluationJobPayload.DATASET, items));

        return task;
    }

    @Override
    @Transactional
    public EvaluationTask runDatasetEvaluation(Long datasetId, Long knowledgeId) {
        return runDatasetEvaluation(datasetId, knowledgeId, new EvaluationRunConfig());
    }

    @Override
    @Transactional
    public EvaluationTask runDatasetEvaluation(Long datasetId, Long knowledgeId, EvaluationRunConfig config) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return runDatasetEvaluationForUser(datasetId, knowledgeId, config, userId, null);
    }

    @Override
    @Transactional
    public EvaluationTask runDatasetEvaluationForUser(Long datasetId, Long knowledgeId, EvaluationRunConfig config,
                                                      Long userId, String requestedTaskName) {
        if (userId == null) {
            throw new RuntimeException("评测任务缺少执行用户");
        }
        if (knowledgeId == null) {
            throw new RuntimeException("请选择知识库");
        }
        knowledgeAccess.requireEdit(knowledgeId, userId);

        // 通过 TestDatasetService 获取数据集的QA项
        List<EvaluationQaItem> items = testDatasetService.getDatasetQaItems(datasetId);
        if (items.isEmpty()) {
            throw new RuntimeException("数据集为空或不存在");
        }

        // 获取数据集信息
        TestDataset dataset = testDatasetService.getDatasetDetail(datasetId);
        String taskName = requestedTaskName != null && !requestedTaskName.isBlank()
                ? requestedTaskName
                : (dataset != null ? dataset.getName() + "-评估" : "数据集评估-" + datasetId);
        String datasetType = dataset != null ? dataset.getDatasetType() : "custom";

        EvaluationTask task = createTask(knowledgeId, taskName, items.size(), datasetType, userId, config);
        task.setDatasetId(datasetId);
        task.setDatasetVersion(dataset == null || dataset.getVersion() == null ? 1 : dataset.getVersion());
        enrichDatasetSnapshot(task, dataset);
        taskMapper.insert(task);

        enqueueEvaluation(task, new EvaluationJobPayload(task.getId(), EvaluationJobPayload.DATASET, items));

        return task;
    }

    @Override
    public List<DatasetInfo> listBuiltinDatasets() {
        List<DatasetInfo> datasets = new ArrayList<>();

        String[] datasetFiles = {"rag_scenarios.json", "rag_evaluation_mvp.json"};
        for (String fileName : datasetFiles) {
            try {
                Resource resource = new ClassPathResource(DATASETS_PATH + fileName);
                if (!resource.exists()) {
                    continue;
                }

                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> dataset = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                    DatasetInfo info = new DatasetInfo();
                    info.setName((String) dataset.get("name"));
                    info.setDescription((String) dataset.get("description"));
                    info.setFileName(fileName);

                    List<Map<String, Object>> items = (List<Map<String, Object>>) dataset.get("items");
                    if (items != null) {
                        info.setItemCount(items.size());
                        info.setNegativeCount((int) items.stream()
                                .filter(i -> Boolean.TRUE.equals(i.get("isNegative")))
                                .count());
                    }

                    datasets.add(info);
                }
            } catch (Exception e) {
                log.warn("加载数据集信息失败: {}", fileName, e);
            }
        }

        return datasets;
    }

    private EvaluationTask createTask(Long knowledgeId, String taskName, int qaCount, String datasetType,
                                      Long userId, EvaluationRunConfig requestedConfig) {
        EvaluationRunConfig config = normalizeConfig(requestedConfig);
        EvaluationTask task = new EvaluationTask();
        task.setKnowledgeId(knowledgeId);
        task.setTaskName(taskName != null ? taskName : "评估任务-" + System.currentTimeMillis());
        task.setQaCount(qaCount);
        task.setDatasetType(datasetType);
        task.setEvaluationMode(config.getEvaluationMode());
        task.setTopK(config.getTopK());
        task.setLabeledCount(0);
        task.setJudgedCount(0);
        task.setJudgeModel(config.getJudgeModel());
        task.setBaselineTaskId(config.getBaselineTaskId());
        task.setRegressionStatus(config.getBaselineTaskId() == null ? "NOT_COMPARED" : null);
        task.setStatus(0);
        task.setUserId(userId);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        if (config.getBaselineTaskId() != null) {
            EvaluationTask baseline = taskMapper.selectById(config.getBaselineTaskId());
            if (baseline == null || !Objects.equals(baseline.getKnowledgeId(), knowledgeId)) {
                throw new IllegalArgumentException("基线任务不存在或不属于当前知识库");
            }
            if (!Objects.equals(baseline.getStatus(), 2)) {
                throw new IllegalArgumentException("基线任务尚未完成");
            }
            if (!Objects.equals(Optional.ofNullable(baseline.getTopK()).orElse(5), config.getTopK())) {
                throw new IllegalArgumentException("基线任务与当前任务的 Top K 不一致");
            }
            String baselineMode = Optional.ofNullable(baseline.getEvaluationMode()).orElse("RETRIEVAL");
            if (!Objects.equals(baselineMode, config.getEvaluationMode())) {
                throw new IllegalArgumentException("基线任务与当前任务的评测层级不一致");
            }
        }

        KnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeId);
        if (kb != null) {
            try {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("knowledgeBase", kb);
                snapshot.put("evaluationMode", config.getEvaluationMode());
                snapshot.put("topK", config.getTopK());
                snapshot.put("judgeModel", config.getJudgeModel());
                task.setConfigSnapshot(objectMapper.writeValueAsString(snapshot));
            } catch (JsonProcessingException e) {
                log.warn("保存配置快照失败", e);
            }
        }

        return task;
    }

    private void enqueueEvaluation(EvaluationTask task, EvaluationJobPayload payload) {
        try {
            PlatformJobService.EnqueueCommand command = new PlatformJobService.EnqueueCommand(
                    "evaluation:" + task.getId(), EvaluationJobHandler.TYPE, "EVALUATION", task.getId(),
                    objectMapper.writeValueAsString(payload), 0, 3, task.getUserId());
            PlatformJob job = platformJobs.enqueue(command);
            task.setJobId(job.getId());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("评测任务无法写入可靠队列", e);
        }
    }

    private EvaluationRunConfig normalizeConfig(EvaluationRunConfig requested) {
        EvaluationRunConfig config = requested == null ? new EvaluationRunConfig() : requested;
        String mode = config.getEvaluationMode() == null ? "RETRIEVAL" : config.getEvaluationMode().trim().toUpperCase(Locale.ROOT);
        if (!EVALUATION_MODES.contains(mode)) throw new IllegalArgumentException("评测模式不正确");
        config.setEvaluationMode(mode);
        config.setTopK(Math.max(1, Math.min(config.getTopK() == null ? 5 : config.getTopK(), 20)));
        if (config.getJudgeModel() != null && config.getJudgeModel().isBlank()) config.setJudgeModel(null);
        return config;
    }

    private void enrichDatasetSnapshot(EvaluationTask task, TestDataset dataset) {
        if (dataset == null) return;
        try {
            Map<String, Object> snapshot = task.getConfigSnapshot() == null
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(task.getConfigSnapshot(), new TypeReference<LinkedHashMap<String, Object>>() {});
            snapshot.put("datasetId", dataset.getId());
            snapshot.put("datasetName", dataset.getName());
            snapshot.put("datasetVersion", dataset.getVersion() == null ? 1 : dataset.getVersion());
            task.setConfigSnapshot(objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) {
            log.warn("保存数据集版本快照失败: datasetId={}", dataset.getId(), e);
        }
    }

    private List<EvaluationQaItem> loadBuiltinDataset(String datasetName) {
        try {
            Resource resource = new ClassPathResource(DATASETS_PATH + datasetName);
            if (!resource.exists()) {
                // 尝试加上 .json 后缀
                resource = new ClassPathResource(DATASETS_PATH + datasetName + ".json");
            }
            try (InputStream is = resource.getInputStream()) {
                Map<String, Object> dataset = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                List<Map<String, Object>> items = (List<Map<String, Object>>) dataset.get("items");
                if (items == null) {
                    return Collections.emptyList();
                }

                List<EvaluationQaItem> result = new ArrayList<>();
                for (Map<String, Object> item : items) result.add(objectMapper.convertValue(item, EvaluationQaItem.class));
                return result;
            }
        } catch (Exception e) {
            log.error("加载内置数据集失败: {}", datasetName, e);
            throw new RuntimeException("加载数据集失败: " + e.getMessage());
        }
    }

    @Override
    public void runEvaluation(Long taskId) {
        runEvaluation(taskId, null);
    }

    @Override
    public void runEvaluation(Long taskId, PlatformJobExecutionContext context) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("评估任务不存在: {}", taskId);
            return;
        }

        if(executions.alreadyCompleted(task,context)) return;
        try (var execution=executions.start(task,context,promptVersions())) {
            execution.check();

            long totalChunks = vectorStoreService.countPublishedChunks(task.getKnowledgeId());
            if (totalChunks == 0) {
                throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
            }

            int fetchCount = Math.min(task.getQaCount() * 3, (int) Math.min(totalChunks, 100));
            List<Document> allChunks = vectorStoreService.getPublishedChunks(task.getKnowledgeId(), 0, fetchCount);

            if (allChunks.isEmpty()) {
                throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
            }

            int sampleCount = Math.min(task.getQaCount(), allChunks.size());
            Collections.shuffle(allChunks,new Random(execution.seed()));
            List<Document> sampledChunks = allChunks.subList(0, sampleCount);

            KnowledgeBase knowledgeBase=execution.base();
            String qaModelId = null;
            if (knowledgeBase != null && knowledgeBase.getHydeModel() != null && !knowledgeBase.getHydeModel().isEmpty()) {
                qaModelId = knowledgeBase.getHydeModel();
                log.info("QA生成使用 HyDE 模型: {}", qaModelId);
            }

            List<EvaluationQa> qaList = new ArrayList<>();
            for (Document doc : sampledChunks) {
                checkpoint(context);
                EvaluationQa qa=generateQaFromDocument(doc,taskId,qaModelId);
                if(qa==null) throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
                execution.check(); qa.setSourceType("generated"); qaList.add(qa);
            }

            execution.inputs(qaList.stream().map(qa -> {
                var input=new EvaluationQaItem(); input.setQuestion(qa.getQuestion()); input.setExpectedAnswer(qa.getExpectedAnswer());
                input.setSourceChunkId(qa.getSourceChunkId()); input.setSourceDocumentId(qa.getSourceDocumentId()); return input;
            }).toList());
            List<EvaluationQa> evaluatedQaList = new ArrayList<>();
            for (EvaluationQa qa : qaList) {
                checkpoint(context);
                evaluateQa(qa,task,execution.base());
                execution.record(qa); evaluatedQaList.add(qa);
            }

            calculateMetrics(task, evaluatedQaList);

            execution.complete();

            log.info("评估任务完成: taskId={}, qaCount={}, hitRate={}, mrr={}",
                    taskId, evaluatedQaList.size(), task.getHitRate(), task.getMrr());

        } catch (PlatformJobCancelledException cancelled) {
            throw cancelled;
        } catch (Exception e) {
            log.error("评估任务执行失败: taskId={}", taskId, e);
            executions.failTask(task,context);
            throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
        }
    }

    /**
     * 执行导入/内置数据集的评估
     */
    @Override
    public void runImportedEvaluation(Long taskId, List<EvaluationQaItem> items,
                                      PlatformJobExecutionContext context) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("评估任务不存在: {}", taskId);
            return;
        }

        if(executions.alreadyCompleted(task,context)) return;
        try (var execution=executions.start(task,context,promptVersions())) {
            execution.check();

            if(items==null) items=executions.frozenReplayInputs(taskId,context);
            var fixedItems=com.alibaba.fastjson2.JSON.parseArray(com.alibaba.fastjson2.JSON.toJSONString(items),EvaluationQaItem.class);
            if(fixedItems==null || fixedItems.isEmpty()) throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
            execution.inputs(fixedItems);
            List<EvaluationQa> qaList = new ArrayList<>();
            for (EvaluationQaItem item : fixedItems) {
                checkpoint(context);
                if (item.getQuestion() == null || item.getQuestion().trim().isEmpty()) {
                    throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
                }

                EvaluationQa qa = new EvaluationQa();
                qa.setTaskId(taskId);
                qa.setQuestion(item.getQuestion().trim());
                qa.setExpectedAnswer(item.getExpectedAnswer() != null ? item.getExpectedAnswer().trim() : "");
                qa.setSourceChunkId(item.getSourceChunkId());
                qa.setSourceDocumentId(item.getSourceDocumentId());
                qa.setExpectedDocumentIds(writeJson(item.getExpectedDocumentIds()));
                qa.setRelevanceJudgments(writeJson(item.getRelevanceJudgments()));
                qa.setIsNegative(item.getIsNegative() != null ? item.getIsNegative() : false);
                qa.setSourceType(task.getDatasetType());
                qa.setExternalId(item.getExternalId());
                qa.setCategory(item.getCategory());
                qa.setDifficulty(item.getDifficulty());
                qa.setBusinessLine(item.getBusinessLine());
                qa.setUserRole(item.getUserRole());
                qa.setHit(false);
                qa.setDocHit(false);
                qa.setCreateTime(LocalDateTime.now());

                evaluateQa(qa,task,execution.base());
                if(qa.getOverallScore()!=null) qa.setHumanReviewStatus("PENDING");
                execution.record(qa); qaList.add(qa);
            }

            calculateMetrics(task, qaList);

            execution.complete();

            log.info("导入评估任务完成: taskId={}, qaCount={}, hitRate={}, negativeHitRate={}",
                    taskId, qaList.size(), task.getHitRate(), task.getNegativeHitRate());

        } catch (PlatformJobCancelledException cancelled) {
            throw cancelled;
        } catch (Exception e) {
            log.error("导入评估任务执行失败: taskId={}", taskId, e);
            executions.failTask(task,context);
            throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
        }
    }

    private EvaluationQa generateQaFromDocument(Document doc, Long taskId, String modelId) {
        String content = doc.getText();
        String vectorId = Objects.toString(doc.getMetadata().get("id"), null);
        Object docIdObj = doc.getMetadata().get("document_id");
        Long documentId = null;
        if (docIdObj instanceof Number) {
            documentId = ((Number) docIdObj).longValue();
        }

        String prompt = String.format(QA_GENERATION_PROMPT, content);
        String response = chatService.chat(prompt, modelId);

        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> qaMap = objectMapper.readValue(response, Map.class);
            String question = (String) qaMap.get("question");

            if (question == null || question.trim().isEmpty()) {
                return null;
            }

            String answer = extractAnswer(qaMap.get("answer"));

            EvaluationQa qa = new EvaluationQa();
            qa.setTaskId(taskId);
            qa.setQuestion(question.trim());
            qa.setExpectedAnswer(answer != null ? answer.trim() : "");
            qa.setSourceChunkId(vectorId);
            qa.setSourceDocumentId(documentId);
            if (documentId != null) {
                qa.setExpectedDocumentIds(writeJson(List.of(documentId)));
                qa.setRelevanceJudgments(writeJson(Map.of(documentId, 3)));
            }
            qa.setSourceChunkContent(content);
            qa.setIsNegative(false);
            qa.setHit(false);
            qa.setDocHit(false);
            qa.setCreateTime(LocalDateTime.now());

            return qa;
        } catch (Exception e) {
            log.warn("解析QA JSON失败，响应长度: {}", response == null ? 0 : response.length(), e);
            return null;
        }
    }

    private String extractAnswer(Object answerObj) {
        if (answerObj == null) {
            return "";
        }
        if (answerObj instanceof String) {
            return (String) answerObj;
        }
        if (answerObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> answerList = (List<String>) answerObj;
            return String.join("; ", answerList);
        }
        return answerObj.toString();
    }

    private Map<String,String> promptVersions() {
        return Map.of("qaGeneration",cn.sdh.backend.utils.ContentFingerprint.sha256(QA_GENERATION_PROMPT),
                "answer",cn.sdh.backend.utils.ContentFingerprint.sha256(ANSWER_PROMPT),
                "judge",cn.sdh.backend.utils.ContentFingerprint.sha256(JUDGE_PROMPT));
    }

    private void evaluateQa(EvaluationQa qa, EvaluationTask task,KnowledgeBase frozenBase) {
        RagSearchService.RagSearchResult result = ragSearchService.search(
                qa.getQuestion(), frozenBase, null
        );

        List<Document> docs = result.getDocuments() == null ? List.of() : result.getDocuments();
        int topK = task.getTopK() == null ? 5 : task.getTopK();
        List<Document> topDocs = docs.stream().limit(topK).toList();

        List<String> retrievedIds = new ArrayList<>();
        List<Long> retrievedDocIds = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (Document doc : topDocs) {
            Object id = doc.getMetadata().get("id");
            if (id != null) retrievedIds.add(id.toString());
            Long documentId = longValue(doc.getMetadata().get("document_id"));
            retrievedDocIds.add(documentId);
            if (context.length() > 0) context.append("\n\n---\n\n");
            context.append("[文档 ").append(documentId == null ? "未知" : documentId).append("]\n")
                    .append(Objects.toString(doc.getText(), ""));
        }
        qa.setRetrievedChunkIds(toJsonArray(retrievedIds));
        qa.setRetrievedDocumentIds(writeJson(retrievedDocIds));
        qa.setRetrievedContext(context.toString());
        qa.setHit(false);
        qa.setDocHit(false);
        qa.setHitRank(null);
        qa.setDocHitRank(null);

        if (Boolean.TRUE.equals(qa.getIsNegative())) {
            if (generationEnabled(task) && qa.getExpectedAnswer() != null && !qa.getExpectedAnswer().isBlank()) {
                evaluateGeneration(qa, task);
            }
            classifyFailure(qa);
            return;
        }

        String sourceId = qa.getSourceChunkId();
        if (sourceId != null && !sourceId.isBlank()) {
            for (int i = 0; i < retrievedIds.size(); i++) {
                if (sourceId.equals(retrievedIds.get(i))) {
                    qa.setHit(true);
                    qa.setHitRank(i + 1);
                    break;
                }
            }
        }

        Map<Long, Integer> judgments = readJudgments(qa.getRelevanceJudgments());
        for (Long id : readLongList(qa.getExpectedDocumentIds())) judgments.putIfAbsent(id, 1);
        if (qa.getSourceDocumentId() != null) judgments.putIfAbsent(qa.getSourceDocumentId(), 3);
        if (!judgments.isEmpty()) {
            RagEvaluationMetrics.Result metrics = RagEvaluationMetrics.calculate(retrievedDocIds, judgments, topK);
            qa.setRecallAtK(decimal(metrics.recall()));
            qa.setPrecisionAtK(decimal(metrics.precision()));
            qa.setReciprocalRank(decimal(metrics.reciprocalRank()));
            qa.setNdcgAtK(decimal(metrics.ndcg()));
            qa.setAveragePrecision(decimal(metrics.averagePrecision()));
            qa.setDocHit(metrics.hit());
            qa.setDocHitRank(metrics.firstRank());
        }

        if (generationEnabled(task) && qa.getExpectedAnswer() != null && !qa.getExpectedAnswer().isBlank()) {
            evaluateGeneration(qa, task);
        }
        classifyFailure(qa);
    }

    private void evaluateGeneration(EvaluationQa qa, EvaluationTask task) {
        try {
            String context = Objects.toString(qa.getRetrievedContext(), "");
            String answer = chatService.chat(String.format(ANSWER_PROMPT, qa.getQuestion(), context), task.getJudgeModel());
            qa.setActualAnswer(answer);
            String judgeResponse = chatService.chat(String.format(JUDGE_PROMPT, qa.getQuestion(), context,
                    Objects.toString(answer, ""), qa.getExpectedAnswer()), task.getJudgeModel());
            Map<String, Object> scores = objectMapper.readValue(extractJson(judgeResponse), new TypeReference<Map<String, Object>>() {});
            qa.setContextPrecision(score(scores, "contextPrecision"));
            qa.setContextRecall(score(scores, "contextRecall"));
            qa.setFaithfulness(score(scores, "faithfulness"));
            qa.setAnswerRelevancy(score(scores, "answerRelevancy"));
            qa.setCompleteness(score(scores, "completeness"));
            qa.setFluency(score(scores, "fluency"));
            qa.setOverallScore(score(scores, "overall"));
            if(qa.getContextPrecision()==null || qa.getContextRecall()==null || qa.getFaithfulness()==null || qa.getAnswerRelevancy()==null || qa.getCompleteness()==null || qa.getFluency()==null || qa.getOverallScore()==null) throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
            qa.setJudgeReason(cap(Objects.toString(scores.get("reason"), ""), 1000));
        } catch (Exception e) {
            ModelExecutionScope.failIfActive();
            qa.setJudgeReason("自动评审未完整完成");
            log.warn("生成质量评审失败: question={}, error={}", qa.getQuestion(), e.getMessage());
        }
    }

    private void classifyFailure(EvaluationQa qa) {
        qa.setFailureCategory(null);
        qa.setFailureSuggestion(null);
        if (Boolean.TRUE.equals(qa.getIsNegative())) {
            if (hasRetrieved(qa)) {
                fail(qa, "NEGATIVE_FALSE_POSITIVE", "提高相似度阈值或增加无答案识别，生成阶段必须明确拒答。");
            } else if (low(qa.getFaithfulness(), 0.6) || low(qa.getAnswerRelevancy(), 0.6)) {
                fail(qa, "HALLUCINATION", "负样本回答未正确拒答，应强化仅依据 Context 回答的约束。");
            }
            return;
        }
        boolean labeled = qa.getSourceDocumentId() != null
                || !readLongList(qa.getExpectedDocumentIds()).isEmpty()
                || !readJudgments(qa.getRelevanceJudgments()).isEmpty();
        if (!labeled) {
            fail(qa, "UNLABELED", "补充相关文档或分级相关性标注后再判断检索质量。");
        } else if (qa.getRecallAtK() != null && qa.getRecallAtK().signum() == 0) {
            fail(qa, "RETRIEVAL_MISS", "检查知识是否缺失，并调整切分、查询改写、召回或重排配置。");
        } else if (low(qa.getContextPrecision(), 0.6) || low(qa.getPrecisionAtK(), 0.2)) {
            fail(qa, "CONTEXT_NOISE", "减少无关候选，优化相似度阈值、混合检索权重或重排模型。");
        } else if (low(qa.getFaithfulness(), 0.6)) {
            fail(qa, "HALLUCINATION", "正确证据已进入 Context，建议收紧生成 Prompt 或更换生成模型。");
        } else if (low(qa.getCompleteness(), 0.6)) {
            fail(qa, "INCOMPLETE", "补充参考答案要点，并优化 Context 拼装或回答结构要求。");
        }
    }

    private boolean low(BigDecimal score, double threshold) {
        return score != null && score.compareTo(BigDecimal.valueOf(threshold)) < 0;
    }

    private void fail(EvaluationQa qa, String category, String suggestion) {
        qa.setFailureCategory(category);
        qa.setFailureSuggestion(suggestion);
    }

    private void calculateMetrics(EvaluationTask task, List<EvaluationQa> qaList) {
        if (qaList.isEmpty()) {
            return;
        }

        int totalQa = qaList.size();
        int hitCount = 0;
        int docHitCount = 0;
        double mrrSum = 0.0;
        double hitRankSum = 0.0;
        int top1 = 0, top3 = 0, top5 = 0, top10 = 0;
        int negativeCount = 0;
        int negativeWrongHitCount = 0;
        int positiveCount = 0;
        int labeledCount = 0;
        int judgedCount = 0;
        double recallSum = 0, precisionSum = 0, ndcgSum = 0, mapSum = 0;
        double contextPrecisionSum = 0, contextRecallSum = 0, faithfulnessSum = 0;
        double answerRelevancySum = 0, completenessSum = 0, fluencySum = 0, overallSum = 0;

        for (EvaluationQa qa : qaList) {
            if (Boolean.TRUE.equals(qa.getIsNegative())) {
                negativeCount++;
                String retrieved = qa.getRetrievedDocumentIds();
                if (retrieved != null && !retrieved.equals("[]") && !retrieved.isEmpty()) {
                    negativeWrongHitCount++;
                }
                continue;
            }
            positiveCount++;

            if (Boolean.TRUE.equals(qa.getHit())) {
                hitCount++;
                int rank = qa.getHitRank() != null ? qa.getHitRank() : 0;
                if (rank > 0) {
                    hitRankSum += rank;

                    if (rank <= 1) top1++;
                    if (rank <= 3) top3++;
                    if (rank <= 5) top5++;
                    if (rank <= 10) top10++;
                }
            }
            if (Boolean.TRUE.equals(qa.getDocHit())) {
                docHitCount++;
            }
            if (qa.getRecallAtK() != null) {
                labeledCount++;
                recallSum += qa.getRecallAtK().doubleValue();
                precisionSum += value(qa.getPrecisionAtK());
                mrrSum += value(qa.getReciprocalRank());
                ndcgSum += value(qa.getNdcgAtK());
                mapSum += value(qa.getAveragePrecision());
            }
            if (qa.getOverallScore() != null) {
                judgedCount++;
                contextPrecisionSum += value(qa.getContextPrecision());
                contextRecallSum += value(qa.getContextRecall());
                faithfulnessSum += value(qa.getFaithfulness());
                answerRelevancySum += value(qa.getAnswerRelevancy());
                completenessSum += value(qa.getCompleteness());
                fluencySum += value(qa.getFluency());
                overallSum += value(qa.getOverallScore());
            }
        }

        if (positiveCount > 0) {
            task.setHitRate(decimal((double) hitCount / positiveCount));
            task.setDocHitRate(decimal((double) docHitCount / positiveCount));
        }
        if (labeledCount > 0) {
            task.setRecallAtK(decimal(recallSum / labeledCount));
            task.setPrecisionAtK(decimal(precisionSum / labeledCount));
            task.setMrr(decimal(mrrSum / labeledCount));
            task.setNdcgAtK(decimal(ndcgSum / labeledCount));
            task.setMapScore(decimal(mapSum / labeledCount));
        }

        BigDecimal avgHitRank = hitCount > 0
                ? BigDecimal.valueOf(hitRankSum / hitCount).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        task.setAvgHitRank(avgHitRank);
        Map<String, Integer> topKHits = new HashMap<>();
        topKHits.put("top1", top1);
        topKHits.put("top3", top3);
        topKHits.put("top5", top5);
        topKHits.put("top10", top10);
        try {
            task.setTopKHits(objectMapper.writeValueAsString(topKHits));
        } catch (JsonProcessingException e) {
            log.warn("序列化Top-K命中分布失败", e);
        }

        task.setNegativeCount(negativeCount);
        task.setLabeledCount(labeledCount);
        task.setJudgedCount(judgedCount);
        if (negativeCount > 0) {
            task.setNegativeHitRate(decimal((double) negativeWrongHitCount / negativeCount));
        }
        if (judgedCount > 0) {
            task.setContextPrecision(decimal(contextPrecisionSum / judgedCount));
            task.setContextRecall(decimal(contextRecallSum / judgedCount));
            task.setFaithfulness(decimal(faithfulnessSum / judgedCount));
            task.setAnswerRelevancy(decimal(answerRelevancySum / judgedCount));
            task.setCompleteness(decimal(completenessSum / judgedCount));
            task.setFluency(decimal(fluencySum / judgedCount));
            task.setOverallScore(decimal(overallSum / judgedCount));
        }
        compareBaseline(task);

        log.info("分层评估完成: total={}, labeled={}, judged={}, recall@{}={}, mrr={}, faithfulness={}",
                totalQa, labeledCount, judgedCount, task.getTopK(), task.getRecallAtK(), task.getMrr(), task.getFaithfulness());
    }

    private void compareBaseline(EvaluationTask task) {
        if (task.getBaselineTaskId() == null) {
            task.setRegressionStatus("NOT_COMPARED");
            return;
        }
        EvaluationTask baseline = taskMapper.selectById(task.getBaselineTaskId());
        if (!executions.comparable(task,baseline) || (baseline.getRecallAtK()==null && baseline.getMrr()==null && baseline.getFaithfulness()==null && baseline.getAnswerRelevancy()==null)) {
            task.setRegressionStatus("INCOMPARABLE");
            return;
        }
        boolean passed = notLower(task.getRecallAtK(), baseline.getRecallAtK())
                && notLower(task.getMrr(), baseline.getMrr())
                && notLower(task.getFaithfulness(), baseline.getFaithfulness())
                && notLower(task.getAnswerRelevancy(), baseline.getAnswerRelevancy());
        task.setRegressionStatus(passed ? "PASSED" : "FAILED");
    }

    private boolean notLower(BigDecimal current, BigDecimal baseline) {
        return baseline == null || (current != null && current.compareTo(baseline) >= 0);
    }

    private boolean generationEnabled(EvaluationTask task) {
        return "GENERATION".equals(task.getEvaluationMode()) || "FULL".equals(task.getEvaluationMode());
    }

    private BigDecimal score(Map<String, Object> values, String key) {
        Object raw = values.get(key);
        if (!(raw instanceof Number number)) return null;
        double normalized = Math.max(1, Math.min(5, number.doubleValue())) / 5.0;
        return decimal(normalized);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private double value(BigDecimal value) { return value == null ? 0 : value.doubleValue(); }

    private Long longValue(Object raw) {
        if (raw instanceof Number number) return number.longValue();
        if (raw != null) try { return Long.parseLong(raw.toString()); } catch (NumberFormatException ignored) { }
        return null;
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { return null; }
    }

    private List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<Long>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private Map<Long, Integer> readJudgments(String json) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return result;
        try { result.putAll(objectMapper.readValue(json, new TypeReference<Map<Long, Integer>>() {})); }
        catch (Exception ignored) { }
        return result;
    }

    private String extractJson(String response) {
        if (response == null) throw new IllegalArgumentException("评审模型未返回结果");
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("评审模型返回格式不正确");
        return response.substring(start, end + 1);
    }

    private String cap(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String toJsonArray(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Override
    public EvaluationTask getTaskDetail(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    @Override
    public List<EvaluationQa> getTaskQaList(Long taskId) {
        return qaMapper.selectList(
                new LambdaQueryWrapper<EvaluationQa>()
                        .eq(EvaluationQa::getTaskId, taskId)
                        .orderByAsc(EvaluationQa::getId)
        );
    }

    @Override
    public IPage<EvaluationTask> listPaged(Long knowledgeId, int page, int pageSize) {
        Page<EvaluationTask> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<EvaluationTask> wrapper = new LambdaQueryWrapper<EvaluationTask>()
                .orderByDesc(EvaluationTask::getCreateTime);
        Long actor = UserContext.getCurrentUserId();
        List<Long> readableIds = knowledgeAccess.readableKnowledgeIds(actor);
        if (readableIds.isEmpty()) {
            wrapper.eq(EvaluationTask::getId, -1L);
        } else {
            wrapper.in(EvaluationTask::getKnowledgeId, readableIds);
        }
        if (knowledgeId != null) {
            knowledgeAccess.requireRead(knowledgeId, actor);
            wrapper.eq(EvaluationTask::getKnowledgeId, knowledgeId);
        }
        IPage<EvaluationTask> result = taskMapper.selectPage(pageParam, wrapper);
        fillKnowledgeName(result.getRecords());
        return result;
    }

    private void fillKnowledgeName(List<EvaluationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<Long> kbIds = tasks.stream()
                .map(EvaluationTask::getKnowledgeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (kbIds.isEmpty()) {
            return;
        }
        Map<Long, String> kbNameMap = new HashMap<>();
        Map<Long, KnowledgeBase> kbMap = new HashMap<>();
        for (Long kbId : kbIds) {
            try {
                KnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(kbId);
                if (kb != null) {
                    kbNameMap.put(kbId, kb.getName());
                    kbMap.put(kbId, kb);
                }
            } catch (Exception ignored) {
            }
        }
        for (EvaluationTask task : tasks) {
            if (task.getKnowledgeId() != null) {
                task.setKnowledgeName(kbNameMap.get(task.getKnowledgeId()));
                KnowledgeBase kb = kbMap.get(task.getKnowledgeId());
                Long actor = UserContext.getCurrentUserId();
                boolean operator = actor != null && (actor.equals(task.getUserId()) || "owner".equals(knowledgeAccess.role(kb, actor)));
                PlatformJob job = task.getJobId() == null ? null : platformJobs.get(task.getJobId());
                task.setJobStatus(job == null ? null : job.getStatus());
                task.setCanDelete(operator && !isActiveJob(job));
            }
        }
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        EvaluationTask task = taskMapper.selectById(taskId);
        Long actor = UserContext.getCurrentUserId();
        if (task == null) return;
        KnowledgeBase base = knowledgeAccess.requireRead(task.getKnowledgeId(), actor);
        if (!actor.equals(task.getUserId()) && !"owner".equals(knowledgeAccess.role(base, actor))) {
            throw new cn.sdh.backend.common.exception.BusinessException(403, "只有任务创建者或知识库所有者可删除评估任务");
        }
        PlatformJob job = task.getJobId() == null ? null : platformJobs.get(task.getJobId());
        if (Integer.valueOf(0).equals(task.getStatus()) || Integer.valueOf(1).equals(task.getStatus()) || isActiveJob(job)) {
            throw new cn.sdh.backend.common.exception.BusinessException(409, "请先取消任务，再执行删除");
        }
        qaMapper.delete(new LambdaQueryWrapper<EvaluationQa>()
                .eq(EvaluationQa::getTaskId, taskId));
        taskMapper.deleteById(taskId);
    }

    @Override
    @Transactional
    public void cancelTask(Long taskId) {
        EvaluationTask task = requireTaskOperator(taskId);
        if (task.getJobId() == null) throw new cn.sdh.backend.common.exception.BusinessException(409, "该历史任务不支持取消");
        platformJobs.cancel(task.getJobId(), task.getUserId());
        task.setStatus(4);
        task.setErrorMessage("取消请求已提交");
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    @Transactional
    public void replayTask(Long taskId) {
        EvaluationTask task = requireTaskOperator(taskId);
        if (task.getJobId() == null) throw new cn.sdh.backend.common.exception.BusinessException(409, "该历史任务不支持重放");
        platformJobs.replay(task.getJobId(), task.getUserId());
        task.setStatus(0);
        task.setErrorMessage(null);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private EvaluationTask requireTaskOperator(Long taskId) {
        EvaluationTask task = taskMapper.selectById(taskId);
        Long actor = UserContext.getCurrentUserId();
        if (task == null) throw new cn.sdh.backend.common.exception.BusinessException(404, "评估任务不存在");
        KnowledgeBase base = knowledgeAccess.requireEdit(task.getKnowledgeId(), actor);
        if (!actor.equals(task.getUserId()) && !"owner".equals(knowledgeAccess.role(base, actor))) {
            throw new cn.sdh.backend.common.exception.BusinessException(403, "只有任务创建者或知识库所有者可操作评估任务");
        }
        return task;
    }

    private boolean isActiveJob(PlatformJob job) {
        return job != null && Set.of(PlatformJob.PENDING, PlatformJob.RUNNING, PlatformJob.RETRY_WAIT,
                PlatformJob.CANCEL_REQUESTED).contains(job.getStatus());
    }

    private void checkpoint(PlatformJobExecutionContext context) {
        if (context != null) context.checkpoint();
    }

    @Override
    public KnowledgeQualityReport getQualityReport(Long knowledgeId, int issueLimit) {
        knowledgeAccess.requireRead(knowledgeId, UserContext.getCurrentUserId());
        int safeLimit = Math.max(1, Math.min(issueLimit, 100));
        List<EvaluationTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<EvaluationTask>()
                .eq(EvaluationTask::getKnowledgeId, knowledgeId)
                .eq(EvaluationTask::getStatus, 2)
                .orderByDesc(EvaluationTask::getCreateTime)
                .last("LIMIT 20"));
        if (tasks == null || tasks.isEmpty()) {
            return new KnowledgeQualityReport(knowledgeId, 0, 0, 0, 0, 0, 0,
                    List.of("先运行一次评估，建立知识库质量基线"), List.of());
        }
        List<Long> taskIds = tasks.stream().map(EvaluationTask::getId).toList();
        List<EvaluationQa> qaList = qaMapper.selectList(new LambdaQueryWrapper<EvaluationQa>()
                .in(EvaluationQa::getTaskId, taskIds).orderByDesc(EvaluationQa::getId));
        if (qaList == null) qaList = List.of();

        int chunkMisses = 0, documentMisses = 0, negativeFalsePositives = 0, openIssueCount = 0, total = qaList.size();
        List<KnowledgeQualityReport.QualityIssue> allIssues = new ArrayList<>();
        for (EvaluationQa qa : qaList) {
            boolean falsePositive = Boolean.TRUE.equals(qa.getIsNegative()) && hasRetrieved(qa);
            boolean hasDocumentLabel = qa.getSourceDocumentId() != null
                    || !readLongList(qa.getExpectedDocumentIds()).isEmpty()
                    || !readJudgments(qa.getRelevanceJudgments()).isEmpty();
            boolean hasChunkLabel = qa.getSourceChunkId() != null && !qa.getSourceChunkId().isBlank();
            boolean documentMiss = !Boolean.TRUE.equals(qa.getIsNegative()) && hasDocumentLabel && !Boolean.TRUE.equals(qa.getDocHit());
            boolean chunkMiss = !Boolean.TRUE.equals(qa.getIsNegative()) && hasChunkLabel && !Boolean.TRUE.equals(qa.getHit());
            boolean generatedFailure = Set.of("CONTEXT_NOISE", "HALLUCINATION", "INCOMPLETE")
                    .contains(Objects.toString(qa.getFailureCategory(), ""));
            if (chunkMiss) chunkMisses++;
            if (documentMiss) documentMisses++;
            if (falsePositive) negativeFalsePositives++;
            if ((falsePositive || documentMiss || chunkMiss || generatedFailure) && (qa.getIssueStatus() == null || "open".equals(qa.getIssueStatus()))) openIssueCount++;
            if (falsePositive || documentMiss || chunkMiss || generatedFailure) {
                String status = qa.getIssueStatus() == null ? "open" : qa.getIssueStatus();
                String reason = generatedFailure ? failureReason(qa.getFailureCategory())
                        : falsePositive ? "负样本被错误召回" : documentMiss ? "未召回来源文档" : "召回了文档但未命中正确分块";
                String severity = falsePositive || documentMiss || "HALLUCINATION".equals(qa.getFailureCategory()) ? "high" : "medium";
                allIssues.add(new KnowledgeQualityReport.QualityIssue(qa.getId(), qa.getTaskId(), qa.getQuestion(),
                        qa.getExpectedAnswer(), qa.getSourceDocumentId(), reason, severity, status, qa.getIssueNote()));
            }
        }
        Comparator<KnowledgeQualityReport.QualityIssue> issueOrder = Comparator
                .comparingInt((KnowledgeQualityReport.QualityIssue issue) -> "open".equals(issue.status()) ? 0 : 1)
                .thenComparing(KnowledgeQualityReport.QualityIssue::qaId, Comparator.reverseOrder());
        List<KnowledgeQualityReport.QualityIssue> issues = allIssues.stream()
                .sorted(issueOrder).limit(safeLimit).toList();
        List<String> recommendations = new ArrayList<>();
        if (documentMisses > 0) recommendations.add("优先补齐缺失知识或检查数据源同步范围");
        if (chunkMisses > documentMisses) recommendations.add("文档已召回但分块不准，建议调整切分、重写或重排配置");
        if (negativeFalsePositives > 0) recommendations.add("降低无关召回：提高相似度阈值并补充业务负样本");
        if (recommendations.isEmpty()) recommendations.add("当前质量稳定，建议持续加入真实业务问题做回归评估");
        return new KnowledgeQualityReport(knowledgeId, tasks.size(), total, chunkMisses, documentMisses,
                negativeFalsePositives, openIssueCount, recommendations, issues);
    }

    private String failureReason(String category) {
        return switch (Objects.toString(category, "")) {
            case "CONTEXT_NOISE" -> "检索上下文噪声过多";
            case "HALLUCINATION" -> "回答存在无依据内容或拒答失败";
            case "INCOMPLETE" -> "回答未覆盖关键要点";
            default -> "评测未通过";
        };
    }

    @Override
    public void updateIssue(Long qaId, String status, String note) {
        if (!("open".equals(status) || "resolved".equals(status) || "ignored".equals(status))) {
            throw new cn.sdh.backend.common.exception.BusinessException(400, "问题状态必须为 open、resolved 或 ignored");
        }
        EvaluationQa qa = qaMapper.selectById(qaId);
        if (qa == null) throw new cn.sdh.backend.common.exception.BusinessException(404, "评估问题不存在");
        EvaluationTask task = taskMapper.selectById(qa.getTaskId());
        Long actor = UserContext.getCurrentUserId();
        knowledgeAccess.requireEdit(task == null ? null : task.getKnowledgeId(), actor);
        qa.setIssueStatus(status);
        qa.setIssueNote(note == null ? null : note.trim().substring(0, Math.min(note.trim().length(), 500)));
        qa.setResolvedBy("open".equals(status) ? null : actor);
        qa.setResolvedTime("open".equals(status) ? null : LocalDateTime.now());
        qaMapper.updateById(qa);
    }

    private boolean hasRetrieved(EvaluationQa qa) {
        String retrieved = qa.getRetrievedChunkIds();
        return retrieved != null && !retrieved.isBlank() && !"[]".equals(retrieved);
    }
}
