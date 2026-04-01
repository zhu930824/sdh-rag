package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图谱路径响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphPathResponse {

    /**
     * 路径上的节点列表
     */
    private List<NodeInfo> nodes;

    /**
     * 路径上的关系列表
     */
    private List<RelationInfo> relationships;

    /**
     * 路径长度
     */
    private Integer length;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeInfo {
        private Long id;
        private List<String> labels;
        private String name;
        private String entityType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationInfo {
        private String type;
        private Long source;
        private Long target;
    }
}
