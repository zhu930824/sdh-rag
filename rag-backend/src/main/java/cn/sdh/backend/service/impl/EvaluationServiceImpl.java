package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.mapper.EvaluationQaMapper;
import cn.sdh.backend.mapper.EvaluationTaskMapper;
import cn.sdh.backend.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public EvaluationServiceImpl(
            EvaluationTaskMapper taskMapper,
            EvaluationQaMapper qaMapper,
            VectorStoreService vectorStoreService,
            KnowledgeBaseService knowledgeBaseService,
            RagSearchService ragSearchService,
            ChatService chatService,
            ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.qaMapper = qaMapper;
        this.vectorStoreService = vectorStoreService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragSearchService = ragSearchService;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public EvaluationTask generateAndRun(Long knowledgeId, int qaCount, String taskName) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        // 创建任务
        EvaluationTask task = new EvaluationTask();
        task.setKnowledgeId(knowledgeId);
        task.setTaskName(taskName != null ? taskName : "评估任务-" + System.currentTimeMillis());
        task.setQaCount(qaCount);
        task.setStatus(0); // 待运行
        task.setUserId(userId);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        // 保存知识库配置快照
        KnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeId);
        if (kb != null) {
            try {
                task.setConfigSnapshot(objectMapper.writeValueAsString(kb));
            } catch (JsonProcessingException e) {
                log.warn("保存配置快照失败", e);
            }
        }

        taskMapper.insert(task);

        // 异步执行评估
        new Thread(() -> runEvaluation(task.getId())).start();

        return task;
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
            // 更新状态为运行中
            task.setStatus(1);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            // 1. 从ES获取知识库的分块数量
            long totalChunks = vectorStoreService.countChunksByKnowledgeId(task.getKnowledgeId());
            if (totalChunks == 0) {
                task.setStatus(3);
                task.setErrorMessage("知识库没有可用的分块数据");
                taskMapper.updateById(task);
                return;
            }

            // 2. 从ES获取分块（随机采样：获取足够多的分块）
            int fetchCount = Math.min(task.getQaCount() * 3, (int) Math.min(totalChunks, 100));
            List<Document> allChunks = vectorStoreService.getChunksByKnowledgeId(task.getKnowledgeId(), 0, fetchCount);

            if (allChunks.isEmpty()) {
                task.setStatus(3);
                task.setErrorMessage("知识库没有可用的分块数据");
                taskMapper.updateById(task);
                return;
            }

            // 3. 随机采样分块
            int sampleCount = Math.min(task.getQaCount(), allChunks.size());
            Collections.shuffle(allChunks);
            List<Document> sampledChunks = allChunks.subList(0, sampleCount);

            // 获取知识库配置，用于获取 HyDE 模型
            KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBaseById(task.getKnowledgeId());
            String qaModelId = null;
            if (knowledgeBase != null && knowledgeBase.getHydeModel() != null && !knowledgeBase.getHydeModel().isEmpty()) {
                qaModelId = knowledgeBase.getHydeModel();
                log.info("QA生成使用 HyDE 模型: {}", qaModelId);
            }

            // 4. 为每个分块生成QA
            List<EvaluationQa> qaList = new ArrayList<>();
            for (Document doc : sampledChunks) {
                try {
                    EvaluationQa qa = generateQaFromDocument(doc, taskId, qaModelId);
                    if (qa != null) {
                        qaList.add(qa);
                    }
                } catch (Exception e) {
                    log.warn("生成QA失败: docId={}", doc.getId(), e);
                }
            }

            // 5. 执行检索评估
            for (EvaluationQa qa : qaList) {
                try {
                    evaluateQa(qa, task.getKnowledgeId());
                    qaMapper.insert(qa);
                } catch (Exception e) {
                    log.warn("评估QA失败: question={}", qa.getQuestion(), e);
                }
            }

            // 6. 计算指标
            calculateMetrics(task, qaList);

            // 更新任务状态
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

    private EvaluationQa generateQaFromDocument(Document doc, Long taskId, String modelId) {
        String content = doc.getText();
        String vectorId = doc.getId();

        String prompt = String.format(QA_GENERATION_PROMPT, content);
        String response = chatService.chat(prompt, modelId);

        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        try {
            // 解析JSON响应
            Map<String, Object> qaMap = objectMapper.readValue(response, Map.class);
            String question = (String) qaMap.get("question");

            if (question == null || question.trim().isEmpty()) {
                return null;
            }

            // answer可能是String或List<String>类型
            String answer = extractAnswer(qaMap.get("answer"));

            EvaluationQa qa = new EvaluationQa();
            qa.setTaskId(taskId);
            qa.setQuestion(question.trim());
            qa.setExpectedAnswer(answer != null ? answer.trim() : "");
            qa.setSourceChunkId(vectorId);
            qa.setSourceChunkContent(content);
            qa.setHit(false);
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
        // 执行检索
        RagSearchService.RagSearchResult result = ragSearchService.search(
                qa.getQuestion(), knowledgeId, null
        );

        List<Document> docs = result.getDocuments();
        if (docs == null || docs.isEmpty()) {
            qa.setHit(false);
            qa.setHitRank(null);
            qa.setRetrievedChunkIds("[]");
            return;
        }

        // 提取检索到的分块ID
        List<String> retrievedIds = docs.stream()
                .map(doc -> (String) doc.getMetadata().get("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        qa.setRetrievedChunkIds(toJsonArray(retrievedIds));

        // 检查是否命中
        String sourceId = qa.getSourceChunkId();
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (sourceId.equals(retrievedIds.get(i))) {
                qa.setHit(true);
                qa.setHitRank(i + 1);
                return;
            }
        }

        qa.setHit(false);
        qa.setHitRank(null);
    }

    private void calculateMetrics(EvaluationTask task, List<EvaluationQa> qaList) {
        if (qaList.isEmpty()) {
            return;
        }

        int totalQa = qaList.size();
        int hitCount = 0;
        double mrrSum = 0.0;

        for (EvaluationQa qa : qaList) {
            if (qa.getHit()) {
                hitCount++;
                if (qa.getHitRank() != null && qa.getHitRank() > 0) {
                    mrrSum += 1.0 / qa.getHitRank();
                }
            }
        }

        // Hit Rate
        BigDecimal hitRate = BigDecimal.valueOf((double) hitCount / totalQa)
                .setScale(4, RoundingMode.HALF_UP);
        task.setHitRate(hitRate);

        // MRR (Mean Reciprocal Rank)
        BigDecimal mrr = BigDecimal.valueOf(mrrSum / totalQa)
                .setScale(4, RoundingMode.HALF_UP);
        task.setMrr(mrr);

        // Recall@K (这里用 Hit Rate 近似)
        task.setAvgRecall(hitRate);
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
    public List<EvaluationTask> listByKnowledgeId(Long knowledgeId) {
        List<EvaluationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<EvaluationTask>()
                        .eq(EvaluationTask::getKnowledgeId, knowledgeId)
                        .orderByDesc(EvaluationTask::getCreateTime)
        );
        fillKnowledgeName(tasks);
        return tasks;
    }

    @Override
    public List<EvaluationTask> listAll() {
        List<EvaluationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<EvaluationTask>()
                        .orderByDesc(EvaluationTask::getCreateTime)
        );
        fillKnowledgeName(tasks);
        return tasks;
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
        // 先删除QA
        qaMapper.delete(new LambdaQueryWrapper<EvaluationQa>()
                .eq(EvaluationQa::getTaskId, taskId));
        // 再删除任务
        taskMapper.deleteById(taskId);
    }
}
