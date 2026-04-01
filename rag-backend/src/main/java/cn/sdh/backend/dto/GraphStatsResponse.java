package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 图谱统计响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphStatsResponse {

    /**
     * 总节点数
     */
    private Long totalNodes;

    /**
     * 总关系数
     */
    private Long totalRelationships;

    /**
     * 按类型统计节点
     */
    private List<Map<String, Object>> nodesByType;

    /**
     * 按类型统计关系
     */
    private List<Map<String, Object>> relationshipsByType;
}
