package cn.sdh.backend.service;

import cn.sdh.backend.dto.GraphDataResponse;
import cn.sdh.backend.dto.GraphPathResponse;
import cn.sdh.backend.dto.GraphStatsResponse;
import cn.sdh.backend.entity.GraphNode;

import java.util.List;

/**
 * 图谱服务接口
 */
public interface GraphService {

    /**
     * 获取图谱数据（用于可视化）
     * @param centerNodeId 中心节点ID
     * @param depth 展开深度
     * @return 图谱数据
     */
    GraphDataResponse getGraphData(Long centerNodeId, int depth);

    /**
     * 获取指定知识库的图谱数据
     * @param knowledgeBaseId 知识库ID
     * @param centerNodeId 中心节点ID
     * @param depth 展开深度
     * @return 图谱数据
     */
    GraphDataResponse getGraphData(Long knowledgeBaseId, Long centerNodeId, int depth);

    /**
     * 搜索节点
     * @param keyword 关键词
     * @return 节点列表
     */
    List<GraphDataResponse.NodeData> searchNodes(String keyword);

    /**
     * 在指定知识库中搜索节点
     * @param keyword 关键词
     * @param knowledgeBaseId 知识库ID
     * @return 节点列表
     */
    List<GraphDataResponse.NodeData> searchNodes(String keyword, Long knowledgeBaseId);

    /**
     * 获取节点详情
     * @param nodeId 节点ID
     * @return 节点数据
     */
    GraphDataResponse.NodeData getNodeDetail(Long nodeId);

    /**
     * 获取节点的邻居节点
     * @param nodeId 节点ID
     * @return 图谱数据
     */
    GraphDataResponse getNeighbors(Long nodeId);

    /**
     * 获取两个节点之间的最短路径
     * @param startId 起始节点ID
     * @param endId 结束节点ID
     * @return 路径数据
     */
    GraphPathResponse getShortestPath(Long startId, Long endId);

    /**
     * 获取图谱统计信息
     * @return 统计数据
     */
    GraphStatsResponse getStats();

    /**
     * 获取指定知识库的图谱统计信息
     * @param knowledgeBaseId 知识库ID
     * @return 统计数据
     */
    GraphStatsResponse getStats(Long knowledgeBaseId);

    /**
     * 获取指定类型的节点
     * @param nodeType 节点类型
     * @param limit 数量限制
     * @return 节点列表
     */
    List<GraphDataResponse.NodeData> getNodesByType(String nodeType, int limit);
}
