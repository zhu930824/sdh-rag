package cn.sdh.backend.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class IntentRouterResponse {
    /**
     * 是否需要检索知识库
     */
    private boolean needRetrieval;

    /**
     * 推荐知识库列表（按分数排序）
     */
    private List<KnowledgeBaseScore> recommendedBases;

    /**
     * 判断理由
     */
    private String reason;

    /**
     * 改写后的查询（可选）
     */
    private String rewrittenQuery;

    /**
     * 获取分数最高的知识库ID
     */
    public Long getTopKnowledgeBaseId() {
        if (recommendedBases != null && !recommendedBases.isEmpty()) {
            return recommendedBases.get(0).getKnowledgeBaseId();
        }
        return null;
    }

    /**
     * 创建直接对话响应（不需要检索）
     */
    public static IntentRouterResponse directChat(String reason) {
        IntentRouterResponse response = new IntentRouterResponse();
        response.setNeedRetrieval(false);
        response.setReason(reason);
        response.setRecommendedBases(new ArrayList<>());
        return response;
    }

    /**
     * 知识库评分
     */
    @Data
    public static class KnowledgeBaseScore {
        private Long knowledgeBaseId;
        private String knowledgeBaseName;
        private double score;

        public KnowledgeBaseScore() {
        }

        public KnowledgeBaseScore(Long knowledgeBaseId, String knowledgeBaseName, double score) {
            this.knowledgeBaseId = knowledgeBaseId;
            this.knowledgeBaseName = knowledgeBaseName;
            this.score = score;
        }
    }
}