package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 评估QA数据项（用于导入）
 */
@Data
public class EvaluationQaItem {

    private String question;

    private String expectedAnswer;

    private String sourceChunkId;

    private Long sourceDocumentId;

    /**
     * 是否负样本（答案不在知识库中）
     */
    private Boolean isNegative;

    /**
     * 外部数据ID（可选，用于追溯）
     */
    private String externalId;
}
