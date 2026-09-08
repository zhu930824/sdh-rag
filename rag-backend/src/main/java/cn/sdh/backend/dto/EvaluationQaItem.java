package cn.sdh.backend.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 评估QA数据项（用于导入）
 */
@Data
public class EvaluationQaItem {

    private String question;

    private String expectedAnswer;

    private String sourceChunkId;

    private Long sourceDocumentId;

    /** Pointwise/listwise ground truth: all relevant document IDs. */
    private List<Long> expectedDocumentIds;

    /** Graded relevance for NDCG, document ID -> 0..3. */
    private Map<Long, Integer> relevanceJudgments;

    /**
     * 是否负样本（答案不在知识库中）
     */
    private Boolean isNegative;

    /**
     * 外部数据ID（可选，用于追溯）
     */
    private String externalId;

    private String category;

    private String difficulty;

    private String businessLine;

    private String userRole;
}
