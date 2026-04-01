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

    public static GraphBuildResponse fail(Long documentId, String message) {
        return GraphBuildResponse.builder()
                .success(false)
                .message(message)
                .documentId(documentId)
                .build();
    }
}
