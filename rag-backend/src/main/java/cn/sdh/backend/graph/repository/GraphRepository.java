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
     * 获取子图数据
     */
    @Query("MATCH (n) WHERE id(n) IN $nodeIds " +
           "OPTIONAL MATCH (n)-[r]-(m) WHERE id(m) IN $nodeIds " +
           "RETURN collect(DISTINCT {id: id(n), labels: labels(n), name: n.name, " +
           "entityType: n.entityType, weight: n.weight, documentId: n.documentId}) as nodes, " +
           "collect(DISTINCT {type: type(r), source: id(startNode(r)), target: id(endNode(r))}) as relationships")
    Map<String, Object> getSubgraph(@Param("nodeIds") List<Long> nodeIds);

    /**
     * 获取全图概览（限制节点数量）
     */
    @Query("MATCH (n) " +
           "WITH n ORDER BY n.weight DESC, n.frequency DESC LIMIT $limit " +
           "OPTIONAL MATCH (n)-[r]-(m) WHERE m IN (MATCH (x) RETURN x ORDER BY x.weight DESC LIMIT $limit) " +
           "RETURN collect(DISTINCT {id: id(n), labels: labels(n), name: n.name, " +
           "entityType: n.entityType, weight: n.weight, documentId: n.documentId}) as nodes, " +
           "collect(DISTINCT {type: type(r), source: id(startNode(r)), target: id(endNode(r))}) as relationships")
    Map<String, Object> getGraphOverview(@Param("limit") int limit);

    /**
     * 以某节点为中心展开图
     */
    @Query("MATCH path = (center)-[*1..$depth]-(leaf) WHERE id(center) = $centerNodeId " +
           "RETURN collect(DISTINCT {id: id(center), labels: labels(center), name: center.name, " +
           "entityType: center.entityType, weight: center.weight, documentId: center.documentId}) + " +
           "collect(DISTINCT {id: id(leaf), labels: labels(leaf), name: leaf.name, " +
           "entityType: leaf.entityType, weight: leaf.weight, documentId: leaf.documentId}) as nodes, " +
           "collect(DISTINCT {type: type(r), source: id(startNode(r)), target: id(endNode(r))}) as relationships")
    Map<String, Object> expandFromNode(@Param("centerNodeId") Long centerNodeId, @Param("depth") int depth);

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
