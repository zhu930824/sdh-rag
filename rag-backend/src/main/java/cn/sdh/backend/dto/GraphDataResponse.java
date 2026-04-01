package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图谱数据响应 - 用于前端可视化
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphDataResponse {

    /**
     * 节点列表
     */
    private List<NodeData> nodes;

    /**
     * 边列表
     */
    private List<EdgeData> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeData {
        /**
         * 节点ID
         */
        private Long id;

        /**
         * 节点标签（显示名称）
         */
        private String label;

        /**
         * 节点类型: Document/Entity/Concept/Keyword
         */
        private String type;

        /**
         * 实体子类型: PERSON/ORG/LOCATION/DATE/MISC
         */
        private String entityType;

        /**
         * 节点描述
         */
        private String description;

        /**
         * 文档ID
         */
        private Long documentId;

        /**
         * 来源文档ID
         */
        private Long sourceDocumentId;

        /**
         * 权重
         */
        private Integer weight;

        /**
         * 频率
         */
        private Integer frequency;

        /**
         * 额外属性
         */
        private Object properties;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EdgeData {
        /**
         * 边ID
         */
        private String id;

        /**
         * 源节点ID
         */
        private Long source;

        /**
         * 目标节点ID
         */
        private Long target;

        /**
         * 关系类型
         */
        private String relationType;

        /**
         * 权重
         */
        private Double weight;
    }
}
