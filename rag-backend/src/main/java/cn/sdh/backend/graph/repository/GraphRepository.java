package cn.sdh.backend.graph.repository;

import cn.sdh.backend.graph.node.EntityNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

/**
 * 图谱查询 Repository - 自定义查询
 * 使用 EntityNode 作为实体类型以满足 Neo4jRepository 要求
 */
public interface GraphRepository extends Neo4jRepository<EntityNode, Long> {

    /**
     * 查询两个节点之间的最短路径
     */
    @Query("MATCH path = shortestPath((start)-[*]-(end)) " +
           "WHERE id(start) = $startId AND id(end) = $endId " +
           "RETURN [n in nodes(path) | {id: id(n), labels: labels(n), name: n.name}] as nodes, " +
           "[r in relationships(path) | {type: type(r), source: id(startNode(r)), target: id(endNode(r))}] as relationships")
    Map<String, Object> findShortestPath(@Param("startId") Long startId, @Param("endId") Long endId);

    /**
     * 查询节点的邻居节点
     */
    @Query("MATCH (n)-[r]-(neighbor) WHERE id(n) = $nodeId " +
           "RETURN collect(DISTINCT {id: id(neighbor), labels: labels(neighbor), name: neighbor.name, " +
           "entityType: neighbor.entityType, weight: neighbor.weight, frequency: neighbor.frequency}) as neighbors, " +
           "collect(DISTINCT {type: type(r), source: id(startNode(r)), target: id(endNode(r)), weight: r.weight}) as relationships")
    Map<String, Object> findNeighbors(@Param("nodeId") Long nodeId);

    /**
     * 获取全图概览 - 分步查询
     * 第一步：获取top节点ID列表
     */
    @Query("MATCH (n) RETURN id(n) as id ORDER BY n.weight DESC, n.frequency DESC LIMIT $limit")
    List<Map<String, Object>> getTopNodeIds(@Param("limit") int limit);

    /**
     * 获取指定节点ID列表的节点详情
     */
    @Query("MATCH (n) WHERE id(n) IN $nodeIds " +
           "RETURN id(n) as id, labels(n) as labels, n.name as name, " +
           "n.entityType as entityType, n.weight as weight, n.documentId as documentId, n.frequency as frequency")
    List<Map<String, Object>> getNodesByIds(@Param("nodeIds") List<Long> nodeIds);

    /**
     * 获取指定节点ID列表之间的关系
     */
    @Query("MATCH (a)-[r]-(b) WHERE id(a) IN $nodeIds AND id(b) IN $nodeIds " +
           "RETURN DISTINCT type(r) as type, id(startNode(r)) as source, id(endNode(r)) as target, r.weight as weight")
    List<Map<String, Object>> getRelationshipsBetweenNodes(@Param("nodeIds") List<Long> nodeIds);

    /**
     * 以某节点为中心展开图 - 获取指定深度内的所有节点ID
     */
    @Query("MATCH (center) WHERE id(center) = $centerNodeId " +
           "OPTIONAL MATCH (center)-[*1..$depth]-(leaf) " +
           "RETURN collect(DISTINCT id(center)) + collect(DISTINCT id(leaf)) as nodeIds")
    List<Long> expandNodeIds(@Param("centerNodeId") Long centerNodeId, @Param("depth") int depth);

    /**
     * 获取图统计信息
     */
    @Query("MATCH (n) RETURN count(n) as nodeCount")
    Long countNodes();

    @Query("MATCH ()-[r]->() RETURN count(r) as relationshipCount")
    Long countRelationships();

    @Query("MATCH (n) RETURN labels(n)[0] as type, count(n) as count ORDER BY count DESC")
    List<Map<String, Object>> countNodesByType();

    @Query("MATCH ()-[r]->() RETURN type(r) as type, count(r) as count ORDER BY count DESC")
    List<Map<String, Object>> countRelationshipsByType();
}
