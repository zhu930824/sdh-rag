package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图谱构建响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphBuildResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 创建的实体数量
     */
    private int entityCount;

    /**
     * 创建的关系数量
     */
    private int relationCount;

    /**
     * 创建的概念数量
     */
    private int conceptCount;

    /**
     * 创建的关键词数量
     */
    private int keywordCount;

    /**
     * 已处理文档数
     */
    private int processedDocuments;

    /**
     * 总文档数
     */
    private int totalDocuments;

    public static GraphBuildResponse success(Long documentId, int entityCount, int relationCount, int conceptCount, int keywordCount) {
        return GraphBuildResponse.builder()
                .success(true)
                .message("图谱构建成功")
                .documentId(documentId)
                .entityCount(entityCount)
                .relationCount(relationCount)
                .conceptCount(conceptCount)
                .keywordCount(keywordCount)
                .build();
    }

    public static GraphBuildResponse successForKnowledgeBase(Long knowledgeId, int entityCount, int relationCount,
                                                               int conceptCount, int keywordCount,
                                                               int processedDocuments, int totalDocuments) {
        return GraphBuildResponse.builder()
                .success(true)
                .message("知识库图谱构建成功")
                .knowledgeId(knowledgeId)
                .entityCount(entityCount)
                .relationCount(relationCount)
                .conceptCount(conceptCount)
                .keywordCount(keywordCount)
                .processedDocuments(processedDocuments)
                .totalDocuments(totalDocuments)
                .build();
    }

    public static GraphBuildResponse fail(Long documentId, String message) {
        return GraphBuildResponse.builder()
                .success(false)
                .message(message)
                .documentId(documentId)
                .build();
    }

    public static GraphBuildResponse failForKnowledgeBase(Long knowledgeId, String message) {
        return GraphBuildResponse.builder()
                .success(false)
                .message(message)
                .knowledgeId(knowledgeId)
                .build();
    }

    /**
     * 知识库图谱构建状态
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeGraphStatus {
        /**
         * 知识库ID
         */
        private Long knowledgeId;

        /**
         * 是否已构建图谱
         */
        private boolean graphBuilt;

        /**
         * 节点数量
         */
        private int nodeCount;

        /**
         * 关系数量
         */
        private int relationshipCount;

        /**
         * 最后构建时间
         */
        private String lastBuildTime;

        /**
         * 已构建文档数
         */
        private int builtDocumentCount;

        /**
         * 总文档数
         */
        private int totalDocumentCount;
    }
}
