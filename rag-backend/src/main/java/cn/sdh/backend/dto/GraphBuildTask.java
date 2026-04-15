package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图谱构建任务状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphBuildTask {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 文档ID（单文档构建时使用）
     */
    private Long documentId;

    /**
     * 任务类型: KNOWLEDGE_BASE / DOCUMENT
     */
    private TaskType taskType;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 进度百分比 (0-100)
     */
    private int progress;

    /**
     * 当前处理阶段描述
     */
    private String currentStage;

    /**
     * 已处理文档数
     */
    private int processedDocuments;

    /**
     * 总文档数
     */
    private int totalDocuments;

    /**
     * 已创建实体数
     */
    private int entityCount;

    /**
     * 已创建关系数
     */
    private int relationCount;

    /**
     * 已创建概念数
     */
    private int conceptCount;

    /**
     * 已创建关键词数
     */
    private int keywordCount;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    public enum TaskType {
        KNOWLEDGE_BASE,
        DOCUMENT
    }

    public enum TaskStatus {
        PENDING,      // 等待中
        RUNNING,      // 执行中
        COMPLETED,    // 已完成
        FAILED        // 失败
    }
}
