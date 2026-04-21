package cn.sdh.backend.service;

import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.entity.EvaluationQa;

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

    /**
     * 执行评估
     * @param taskId 任务ID
     */
    void runEvaluation(Long taskId);

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
     * 获取知识库的评估任务列表
     * @param knowledgeId 知识库ID
     * @return 任务列表
     */
    List<EvaluationTask> listByKnowledgeId(Long knowledgeId);

    /**
     * 删除评估任务
     * @param taskId 任务ID
     */
    void deleteTask(Long taskId);
}
