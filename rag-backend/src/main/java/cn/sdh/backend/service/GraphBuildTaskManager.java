package cn.sdh.backend.service;

import cn.sdh.backend.dto.GraphBuildTask;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图谱构建任务管理器
 */
@Service
public class GraphBuildTaskManager {

    private final Map<String, GraphBuildTask> tasks = new ConcurrentHashMap<>();

    /**
     * 创建新任务
     */
    public GraphBuildTask createTask(Long knowledgeId, Long documentId, GraphBuildTask.TaskType taskType) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        GraphBuildTask task = GraphBuildTask.builder()
                .taskId(taskId)
                .knowledgeId(knowledgeId)
                .documentId(documentId)
                .taskType(taskType)
                .status(GraphBuildTask.TaskStatus.PENDING)
                .progress(0)
                .processedDocuments(0)
                .totalDocuments(0)
                .entityCount(0)
                .relationCount(0)
                .conceptCount(0)
                .keywordCount(0)
                .createTime(java.time.LocalDateTime.now())
                .build();

        tasks.put(taskId, task);
        return task;
    }

    /**
     * 获取任务
     */
    public Optional<GraphBuildTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * 更新任务
     */
    public void updateTask(GraphBuildTask task) {
        tasks.put(task.getTaskId(), task);
    }

    /**
     * 更新任务进度
     */
    public void updateProgress(String taskId, int progress, String currentStage) {
        getTask(taskId).ifPresent(task -> {
            task.setProgress(progress);
            task.setCurrentStage(currentStage);
            tasks.put(taskId, task);
        });
    }

    /**
     * 更新任务统计
     */
    public void updateStats(String taskId, int processedDocuments, int totalDocuments,
                           int entityCount, int relationCount, int conceptCount, int keywordCount) {
        getTask(taskId).ifPresent(task -> {
            task.setProcessedDocuments(processedDocuments);
            task.setTotalDocuments(totalDocuments);
            task.setEntityCount(entityCount);
            task.setRelationCount(relationCount);
            task.setConceptCount(conceptCount);
            task.setKeywordCount(keywordCount);
            if (totalDocuments > 0) {
                task.setProgress((int) ((processedDocuments * 100.0) / totalDocuments));
            }
            tasks.put(taskId, task);
        });
    }

    /**
     * 标记任务开始
     */
    public void markRunning(String taskId) {
        getTask(taskId).ifPresent(task -> {
            task.setStatus(GraphBuildTask.TaskStatus.RUNNING);
            task.setStartTime(java.time.LocalDateTime.now());
            tasks.put(taskId, task);
        });
    }

    /**
     * 标记任务完成
     */
    public void markCompleted(String taskId) {
        getTask(taskId).ifPresent(task -> {
            task.setStatus(GraphBuildTask.TaskStatus.COMPLETED);
            task.setProgress(100);
            task.setFinishTime(java.time.LocalDateTime.now());
            tasks.put(taskId, task);
        });
    }

    /**
     * 标记任务失败
     */
    public void markFailed(String taskId, String errorMessage) {
        getTask(taskId).ifPresent(task -> {
            task.setStatus(GraphBuildTask.TaskStatus.FAILED);
            task.setErrorMessage(errorMessage);
            task.setFinishTime(java.time.LocalDateTime.now());
            tasks.put(taskId, task);
        });
    }

    /**
     * 删除任务
     */
    public void removeTask(String taskId) {
        tasks.remove(taskId);
    }

    /**
     * 清理过期任务（超过24小时）
     */
    public void cleanupExpiredTasks() {
        java.time.LocalDateTime expireTime = java.time.LocalDateTime.now().minusHours(24);
        tasks.entrySet().removeIf(entry ->
            entry.getValue().getCreateTime() != null &&
            entry.getValue().getCreateTime().isBefore(expireTime)
        );
    }
}
