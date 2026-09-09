package cn.sdh.backend.service;

import cn.sdh.backend.dto.DatasetInfo;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.dto.KnowledgeQualityReport;
import cn.sdh.backend.dto.EvaluationRunConfig;
import cn.sdh.backend.job.PlatformJobExecutionContext;

import java.util.List;

/**
 * RAG评估服务接口
 */
public interface EvaluationService {

    /**
     * 生成测试集并运行评估
     * @param knowledgeId 知识库ID
     * @param qaCount QA数量
     * @param taskName 任务名称
     * @return 评估任务
     */
    EvaluationTask generateAndRun(Long knowledgeId, int qaCount, String taskName);
    EvaluationTask generateAndRun(Long knowledgeId, int qaCount, String taskName, EvaluationRunConfig config);

    /**
     * 导入外部测试集并运行评估
     * @param knowledgeId 知识库ID
     * @param items QA数据项列表
     * @param taskName 任务名称
     * @return 评估任务
     */
    EvaluationTask importAndRun(Long knowledgeId, List<EvaluationQaItem> items, String taskName);
    EvaluationTask importAndRun(Long knowledgeId, List<EvaluationQaItem> items, String taskName, EvaluationRunConfig config);

    /**
     * 运行内置数据集评估
     * @param datasetName 数据集名称
     * @param knowledgeId 知识库ID（可选）
     * @return 评估任务
     */
    EvaluationTask runBuiltinDataset(String datasetName, Long knowledgeId);
    EvaluationTask runBuiltinDataset(String datasetName, Long knowledgeId, EvaluationRunConfig config);

    /**
     * 运行数据集评估（支持数据库中的数据集ID）
     * @param datasetId 数据集ID
     * @param knowledgeId 知识库ID
     * @return 评估任务
     */
    EvaluationTask runDatasetEvaluation(Long datasetId, Long knowledgeId);
    EvaluationTask runDatasetEvaluation(Long datasetId, Long knowledgeId, EvaluationRunConfig config);

    /** Run a dataset on behalf of a persisted experiment owner. Not exposed directly as an API. */
    EvaluationTask runDatasetEvaluationForUser(Long datasetId, Long knowledgeId, EvaluationRunConfig config,
                                               Long userId, String taskName);

    /**
     * 获取内置数据集列表
     * @return 数据集信息列表
     */
    List<DatasetInfo> listBuiltinDatasets();

    /**
     * 执行评估
     * @param taskId 任务ID
     */
    void runEvaluation(Long taskId);

    /** Reliable-job execution entry with lease heartbeat and cooperative cancellation. */
    void runEvaluation(Long taskId, PlatformJobExecutionContext context);

    /** Reliable-job execution entry for imported or versioned dataset items. */
    void runImportedEvaluation(Long taskId, List<EvaluationQaItem> items, PlatformJobExecutionContext context);

    /**
     * 获取评估任务详情
     * @param taskId 任务ID
     * @return 任务详情
     */
    EvaluationTask getTaskDetail(Long taskId);

    /**
     * 获取任务的QA列表
     * @param taskId 任务ID
     * @return QA列表
     */
    List<EvaluationQa> getTaskQaList(Long taskId);

    /**
     * 分页获取评估任务列表
     * @param knowledgeId 知识库ID（可选，为空则返回全部）
     * @param page 页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    com.baomidou.mybatisplus.core.metadata.IPage<EvaluationTask> listPaged(Long knowledgeId, int page, int pageSize);

    /**
     * 删除评估任务
     * @param taskId 任务ID
     */
    void deleteTask(Long taskId);

    void cancelTask(Long taskId);

    void replayTask(Long taskId);

    KnowledgeQualityReport getQualityReport(Long knowledgeId, int issueLimit);

    void updateIssue(Long qaId, String status, String note);
}
