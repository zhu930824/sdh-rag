package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.DatasetInfo;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.*;
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
import org.springframework.scheduling.annotation.Async;
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

    public EvaluationServiceImpl(
            EvaluationTaskMapper taskMapper,
            EvaluationQaMapper qaMapper,
            VectorStoreService vectorStoreService,
            KnowledgeBaseService knowledgeBaseService,
            RagSearchService ragSearchService,
            ChatService chatService,
            TestDatasetService testDatasetService,
            ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.qaMapper = qaMapper;
        this.vectorStoreService = vectorStoreService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragSearchService = ragSearchService;
        this.chatService = chatService;
        this.testDatasetService = testDatasetService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public EvaluationTask generateAndRun(Long knowledgeId, int qaCount, String taskName) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        EvaluationTask task = createTask(knowledgeId, taskName, qaCount, "generated", userId);
        taskMapper.insert(task);

        new Thread(() -> runEvaluation(task.getId())).start();

        return task;
    }

    @Override
    @Transactional
    public EvaluationTask importAndRun(Long knowledgeId, List<EvaluationQaItem> items, String taskName) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("导入数据不能为空");
        }

        EvaluationTask task = createTask(knowledgeId, taskName, items.size(), "imported", userId);
        taskMapper.insert(task);

        new Thread(() -> runImportedEvaluation(task.getId(), items)).start();

        return task;
    }

    @Override
    @Transactional
    public EvaluationTask runBuiltinDataset(String datasetName, Long knowledgeId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        if (knowledgeId == null) {
            throw new RuntimeException("请选择知识库");
        }

        List<EvaluationQaItem> items = loadBuiltinDataset(datasetName);
        if (items.isEmpty()) {
            throw new RuntimeException("数据集为空或不存在: " + datasetName);
        }

        String taskName = "内置数据集-" + datasetName;
        EvaluationTask task = createTask(knowledgeId, taskName, items.size(), "builtin", userId);
        taskMapper.insert(task);

        new Thread(() -> runImportedEvaluation(task.getId(), items)).start();

        return task;
    }

    @Override
    @Transactional
    public EvaluationTask runDatasetEvaluation(Long datasetId, Long knowledgeId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        if (knowledgeId == null) {
            throw new RuntimeException("请选择知识库");
        }

        // 通过 TestDatasetService 获取数据集的QA项
        List<EvaluationQaItem> items = testDatasetService.getDatasetQaItems(datasetId);
        if (items.isEmpty()) {
            throw new RuntimeException("数据集为空或不存在");
        }

        // 获取数据集信息
        TestDataset dataset = testDatasetService.getDatasetDetail(datasetId);
        String taskName = dataset != null ? dataset.getName() + "-评估" : "数据集评估-" + datasetId;
        String datasetType = dataset != null ? dataset.getDatasetType() : "custom";

        EvaluationTask task = createTask(knowledgeId, taskName, items.size(), datasetType, userId);
        taskMapper.insert(task);

        new Thread(() -> runImportedEvaluation(task.getId(), items)).start();

        return task;
    }

    @Override
    public List<DatasetInfo> listBuiltinDatasets() {
        List<DatasetInfo> datasets = new ArrayList<>();

        String[] datasetFiles = {"rag_scenarios.json"};
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

    private EvaluationTask createTask(Long knowledgeId, String taskName, int qaCount, String datasetType, Long userId) {
        EvaluationTask task = new EvaluationTask();
        task.setKnowledgeId(knowledgeId);
        task.setTaskName(taskName != null ? taskName : "评估任务-" + System.currentTimeMillis());
        task.setQaCount(qaCount);
        task.setDatasetType(datasetType);
        task.setStatus(0);
        task.setUserId(userId);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        KnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeId);
        if (kb != null) {
            try {
                task.setConfigSnapshot(objectMapper.writeValueAsString(kb));
            } catch (JsonProcessingException e) {
                log.warn("保存配置快照失败", e);
            }
        }

        return task;
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
                for (Map<String, Object> item : items) {
                    EvaluationQaItem qaItem = new EvaluationQaItem();
                    qaItem.setQuestion((String) item.get("question"));
                    qaItem.setExpectedAnswer((String) item.get("expectedAnswer"));
                    qaItem.setIsNegative(Boolean.TRUE.equals(item.get("isNegative")));
                    if (item.get("sourceChunkId") != null) {
                        qaItem.setSourceChunkId((String) item.get("sourceChunkId"));
                    }
                    if (item.get("externalId") != null) {
                        qaItem.setExternalId((String) item.get("externalId"));
                    }
                    result.add(qaItem);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("加载内置数据集失败: {}", datasetName, e);
            throw new RuntimeException("加载数据集失败: " + e.getMessage());
        }
    }

    @Async
    @Override
    public void runEvaluation(Long taskId) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("评估任务不存在: {}", taskId);
            return;
        }

        try {
            task.setStatus(1);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            long totalChunks = vectorStoreService.countChunksByKnowledgeId(task.getKnowledgeId());
            if (totalChunks == 0) {
                task.setStatus(3);
                task.setErrorMessage("知识库没有可用的分块数据");
                taskMapper.updateById(task);
                return;
            }

            int fetchCount = Math.min(task.getQaCount() * 3, (int) Math.min(totalChunks, 100));
            List<Document> allChunks = vectorStoreService.getChunksByKnowledgeId(task.getKnowledgeId(), 0, fetchCount);

            if (allChunks.isEmpty()) {
                task.setStatus(3);
                task.setErrorMessage("知识库没有可用的分块数据");
                taskMapper.updateById(task);
                return;
            }

            int sampleCount = Math.min(task.getQaCount(), allChunks.size());
            Collections.shuffle(allChunks);
            List<Document> sampledChunks = allChunks.subList(0, sampleCount);

            KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBaseById(task.getKnowledgeId());
            String qaModelId = null;
            if (knowledgeBase != null && knowledgeBase.getHydeModel() != null && !knowledgeBase.getHydeModel().isEmpty()) {
                qaModelId = knowledgeBase.getHydeModel();
                log.info("QA生成使用 HyDE 模型: {}", qaModelId);
            }

            List<EvaluationQa> qaList = new ArrayList<>();
            for (Document doc : sampledChunks) {
                try {
                    EvaluationQa qa = generateQaFromDocument(doc, taskId, qaModelId);
                    if (qa != null) {
                        qa.setSourceType("generated");
                        qaList.add(qa);
                    }
                } catch (Exception e) {
                    log.warn("生成QA失败: docId={}", doc.getId(), e);
                }
            }

            for (EvaluationQa qa : qaList) {
                try {
                    evaluateQa(qa, task.getKnowledgeId());
                    qaMapper.insert(qa);
                } catch (Exception e) {
                    log.warn("评估QA失败: question={}", qa.getQuestion(), e);
                }
            }

            calculateMetrics(task, qaList);

            task.setStatus(2);
            task.setQaCount(qaList.size());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("评估任务完成: taskId={}, qaCount={}, hitRate={}, mrr={}",
                    taskId, qaList.size(), task.getHitRate(), task.getMrr());

        } catch (Exception e) {
            log.error("评估任务执行失败: taskId={}", taskId, e);
            task.setStatus(3);
            task.setErrorMessage(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    /**
     * 执行导入/内置数据集的评估
     */
    private void runImportedEvaluation(Long taskId, List<EvaluationQaItem> items) {
        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("评估任务不存在: {}", taskId);
            return;
        }

        try {
            task.setStatus(1);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            List<EvaluationQa> qaList = new ArrayList<>();
            for (EvaluationQaItem item : items) {
                if (item.getQuestion() == null || item.getQuestion().trim().isEmpty()) {
                    continue;
                }

                EvaluationQa qa = new EvaluationQa();
                qa.setTaskId(taskId);
                qa.setQuestion(item.getQuestion().trim());
                qa.setExpectedAnswer(item.getExpectedAnswer() != null ? item.getExpectedAnswer().trim() : "");
                qa.setSourceChunkId(item.getSourceChunkId());
                qa.setSourceDocumentId(item.getSourceDocumentId());
                qa.setIsNegative(item.getIsNegative() != null ? item.getIsNegative() : false);
                qa.setSourceType(task.getDatasetType());
                qa.setExternalId(item.getExternalId());
                qa.setHit(false);
                qa.setDocHit(false);
                qa.setCreateTime(LocalDateTime.now());

                try {
                    evaluateQa(qa, task.getKnowledgeId());
                    qaMapper.insert(qa);
                    qaList.add(qa);
                } catch (Exception e) {
                    log.warn("评估QA失败: question={}", qa.getQuestion(), e);
                }
            }

            calculateMetrics(task, qaList);

            task.setStatus(2);
            task.setQaCount(qaList.size());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("导入评估任务完成: taskId={}, qaCount={}, hitRate={}, negativeHitRate={}",
                    taskId, qaList.size(), task.getHitRate(), task.getNegativeHitRate());

        } catch (Exception e) {
            log.error("导入评估任务执行失败: taskId={}", taskId, e);
            task.setStatus(3);
            task.setErrorMessage(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private EvaluationQa generateQaFromDocument(Document doc, Long taskId, String modelId) {
        String content = doc.getText();
        String vectorId = (String) doc.getMetadata().get("id");
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
            qa.setSourceChunkContent(content);
            qa.setIsNegative(false);
            qa.setHit(false);
            qa.setDocHit(false);
            qa.setCreateTime(LocalDateTime.now());

            return qa;
        } catch (Exception e) {
            log.warn("解析QA JSON失败: {}", response, e);
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

    private void evaluateQa(EvaluationQa qa, Long knowledgeId) {
        RagSearchService.RagSearchResult result = ragSearchService.search(
                qa.getQuestion(), knowledgeId, null
        );

        List<Document> docs = result.getDocuments();
        if (docs == null || docs.isEmpty()) {
            qa.setRetrievedChunkIds("[]");

            if (Boolean.TRUE.equals(qa.getIsNegative())) {
                // 负样本：检索无结果 = 正确未命中
                qa.setHit(false);
                qa.setDocHit(false);
                qa.setHitRank(null);
                qa.setDocHitRank(null);
                log.debug("负样本正确未命中: question={}", qa.getQuestion());
            } else {
                qa.setHit(false);
                qa.setDocHit(false);
                qa.setHitRank(null);
                qa.setDocHitRank(null);
                log.debug("评估QA检索无结果: question={}", qa.getQuestion());
            }
            return;
        }

        // 提取检索到的分块ID和文档ID
        List<String> retrievedIds = new ArrayList<>();
        List<Long> retrievedDocIds = new ArrayList<>();

        for (Document doc : docs) {
            String id = (String) doc.getMetadata().get("id");
            if (id != null) {
                retrievedIds.add(id);
            }
            Object docIdObj = doc.getMetadata().get("document_id");
            if (docIdObj instanceof Number) {
                retrievedDocIds.add(((Number) docIdObj).longValue());
            } else {
                retrievedDocIds.add(null);
            }
        }

        qa.setRetrievedChunkIds(toJsonArray(retrievedIds));

        // 负样本：检索到结果 = 错误命中（应该检索不到）
        if (Boolean.TRUE.equals(qa.getIsNegative())) {
            qa.setHit(false);
            qa.setDocHit(false);
            qa.setHitRank(null);
            qa.setDocHitRank(null);
            log.debug("负样本错误命中: question={}, retrievedCount={}", qa.getQuestion(), retrievedIds.size());
            return;
        }

        String sourceId = qa.getSourceChunkId();
        Long sourceDocId = qa.getSourceDocumentId();

        // 1. 检查分块级命中（精确匹配）
        if (sourceId != null && !sourceId.isEmpty()) {
            for (int i = 0; i < retrievedIds.size(); i++) {
                if (sourceId.equals(retrievedIds.get(i))) {
                    qa.setHit(true);
                    qa.setHitRank(i + 1);
                    qa.setDocHit(true);
                    qa.setDocHitRank(i + 1);
                    log.debug("评估QA分块命中: question={}, rank={}, sourceId={}", qa.getQuestion(), i + 1, sourceId);
                    return;
                }
            }
        }

        // 2. 检查文档级命中（同一文档的其他分块）
        if (sourceDocId != null) {
            for (int i = 0; i < retrievedDocIds.size(); i++) {
                if (sourceDocId.equals(retrievedDocIds.get(i))) {
                    qa.setDocHit(true);
                    qa.setDocHitRank(i + 1);
                    log.debug("评估QA文档命中: question={}, rank={}, docId={}", qa.getQuestion(), i + 1, sourceDocId);
                    return;
                }
            }
        }

        log.debug("评估QA未命中: question={}, sourceId={}, retrievedCount={}", qa.getQuestion(), sourceId, retrievedIds.size());
        qa.setHit(false);
        qa.setDocHit(false);
        qa.setHitRank(null);
        qa.setDocHitRank(null);
    }

    private void calculateMetrics(EvaluationTask task, List<EvaluationQa> qaList) {
        if (qaList.isEmpty()) {
            return;
        }

        int totalQa = qaList.size();
        int hitCount = 0;
        int docHitCount = 0;
        double mrrSum = 0.0;
        double docMrrSum = 0.0;
        double hitRankSum = 0.0;
        int top1 = 0, top3 = 0, top5 = 0, top10 = 0;

        // 负样本统计
        int negativeCount = 0;
        int negativeWrongHitCount = 0; // 负样本中被错误命中的数量

        for (EvaluationQa qa : qaList) {
            if (Boolean.TRUE.equals(qa.getIsNegative())) {
                negativeCount++;
                // 负样本：检索到结果就是错误命中
                String retrieved = qa.getRetrievedChunkIds();
                if (retrieved != null && !retrieved.equals("[]") && !retrieved.isEmpty()) {
                    negativeWrongHitCount++;
                }
                continue; // 负样本不参与正样本指标计算
            }

            // 分块级指标
            if (Boolean.TRUE.equals(qa.getHit())) {
                hitCount++;
                int rank = qa.getHitRank() != null ? qa.getHitRank() : 0;
                if (rank > 0) {
                    mrrSum += 1.0 / rank;
                    hitRankSum += rank;

                    if (rank <= 1) top1++;
                    if (rank <= 3) top3++;
                    if (rank <= 5) top5++;
                    if (rank <= 10) top10++;
                }
            }

            // 文档级指标
            if (Boolean.TRUE.equals(qa.getDocHit())) {
                docHitCount++;
                int docRank = qa.getDocHitRank() != null ? qa.getDocHitRank() : 0;
                if (docRank > 0) {
                    docMrrSum += 1.0 / docRank;
                }
            }
        }

        int positiveCount = totalQa - negativeCount;

        // 分块级命中率（只算正样本）
        if (positiveCount > 0) {
            BigDecimal hitRate = BigDecimal.valueOf((double) hitCount / positiveCount)
                    .setScale(4, RoundingMode.HALF_UP);
            task.setHitRate(hitRate);

            BigDecimal docHitRate = BigDecimal.valueOf((double) docHitCount / positiveCount)
                    .setScale(4, RoundingMode.HALF_UP);
            task.setDocHitRate(docHitRate);

            BigDecimal mrr = BigDecimal.valueOf(mrrSum / positiveCount)
                    .setScale(4, RoundingMode.HALF_UP);
            task.setMrr(mrr);
        }

        // 平均命中排名
        BigDecimal avgHitRank = hitCount > 0
                ? BigDecimal.valueOf(hitRankSum / hitCount).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        task.setAvgHitRank(avgHitRank);

        // Top-K 命中分布
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

        // 负样本指标
        task.setNegativeCount(negativeCount);
        if (negativeCount > 0) {
            BigDecimal negativeHitRate = BigDecimal.valueOf((double) negativeWrongHitCount / negativeCount)
                    .setScale(4, RoundingMode.HALF_UP);
            task.setNegativeHitRate(negativeHitRate);
        }

        log.info("评估指标计算完成: totalQa={}, positiveCount={}, negativeCount={}, hitRate={}, docHitRate={}, mrr={}, negativeHitRate={}",
                totalQa, positiveCount, negativeCount, task.getHitRate(), task.getDocHitRate(), task.getMrr(), task.getNegativeHitRate());
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
        if (knowledgeId != null) {
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
        for (Long kbId : kbIds) {
            try {
                KnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(kbId);
                if (kb != null) {
                    kbNameMap.put(kbId, kb.getName());
                }
            } catch (Exception ignored) {
            }
        }
        for (EvaluationTask task : tasks) {
            if (task.getKnowledgeId() != null) {
                task.setKnowledgeName(kbNameMap.get(task.getKnowledgeId()));
            }
        }
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        qaMapper.delete(new LambdaQueryWrapper<EvaluationQa>()
                .eq(EvaluationQa::getTaskId, taskId));
        taskMapper.deleteById(taskId);
    }
}
