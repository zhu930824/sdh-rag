package cn.sdh.backend.graph.repository;

import cn.sdh.backend.dto.GraphStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义图谱查询实现 - 使用 Neo4jClient 直接执行 Cypher 查询
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CustomGraphRepository {

    private final Neo4jClient neo4jClient;

    /**
     * 获取全图概览 - 获取top节点ID列表
     */
    public List<Long> getTopNodeIds(int limit) {
        String query = "MATCH (n) RETURN id(n) as id ORDER BY coalesce(n.weight, 0) DESC, coalesce(n.frequency, 0) DESC LIMIT $limit";

        return neo4jClient.query(query)
                .bind(limit).to("limit")
                .fetch()
                .all()
                .stream()
                .map(map -> {
                    Object id = map.get("id");
                    if (id instanceof Long) {
                        return (Long) id;
                    }
                    return Long.valueOf(id.toString());
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取指定节点ID列表的节点详情
     */
    public List<Map<String, Object>> getNodesByIds(List<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return new ArrayList<>();
        }

        String query = "MATCH (n) WHERE id(n) IN $nodeIds " +
                       "RETURN id(n) as id, labels(n) as labels, n.name as name, " +
                       "n.entityType as entityType, n.weight as weight, n.documentId as documentId, n.frequency as frequency, " +
                       "n.description as description";

        return neo4jClient.query(query)
                .bind(nodeIds).to("nodeIds")
                .fetch()
                .all()
                .stream()
                .map(this::mapNodeRecord)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapNodeRecord(Map<String, Object> record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.get("id"));
        map.put("labels", record.get("labels"));
        map.put("name", record.get("name"));
        map.put("entityType", record.get("entityType"));
        map.put("weight", record.get("weight") != null ? record.get("weight") : 0);
        map.put("documentId", record.get("documentId"));
        map.put("frequency", record.get("frequency") != null ? record.get("frequency") : 0);
        map.put("description", record.get("description"));
        return map;
    }

    /**
     * 获取指定节点ID列表之间的关系
     */
    public List<Map<String, Object>> getRelationshipsBetweenNodes(List<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return new ArrayList<>();
        }

        String query = "MATCH (a)-[r]-(b) WHERE id(a) IN $nodeIds AND id(b) IN $nodeIds " +
                       "RETURN DISTINCT type(r) as type, id(startNode(r)) as source, id(endNode(r)) as target, r.weight as weight";

        return neo4jClient.query(query)
                .bind(nodeIds).to("nodeIds")
                .fetch()
                .all()
                .stream()
                .map(this::mapRelationshipRecord)
                .collect(Collectors.toList());
    }

    private Map<String, Object> mapRelationshipRecord(Map<String, Object> record) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", record.get("type"));
        map.put("source", record.get("source"));
        map.put("target", record.get("target"));
        map.put("weight", record.get("weight") != null ? record.get("weight") : 1.0);
        return map;
    }

    /**
     * 以某节点为中心展开图 - 获取指定深度内的所有节点ID
     */
    @SuppressWarnings("unchecked")
    public List<Long> expandNodeIds(Long centerNodeId, int depth) {
        String query = "MATCH (center) WHERE id(center) = $centerNodeId " +
                       "OPTIONAL MATCH (center)-[*1.." + depth + "]-(leaf) " +
                       "RETURN collect(DISTINCT id(center)) + collect(DISTINCT id(leaf)) as nodeIds";

        return neo4jClient.query(query)
                .bind(centerNodeId).to("centerNodeId")
                .fetch()
                .one()
                .map(record -> {
                    Object nodeIdsObj = record.get("nodeIds");
                    if (nodeIdsObj instanceof List<?>) {
                        return ((List<?>) nodeIdsObj).stream()
                                .filter(obj -> obj instanceof Long)
                                .map(obj -> (Long) obj)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList());
                    }
                    return new ArrayList<Long>();
                })
                .orElse(new ArrayList<>());
    }

    /**
     * 查询节点的邻居节点
     */
    public Map<String, Object> findNeighbors(Long nodeId) {
        String query = "MATCH (n)-[r]-(neighbor) WHERE id(n) = $nodeId " +
                       "RETURN collect(DISTINCT {id: id(neighbor), labels: labels(neighbor), name: neighbor.name, " +
                       "entityType: neighbor.entityType, weight: neighbor.weight, frequency: neighbor.frequency}) as neighbors, " +
                       "collect(DISTINCT {type: type(r), source: id(startNode(r)), target: id(endNode(r)), weight: r.weight}) as relationships";

        return neo4jClient.query(query)
                .bind(nodeId).to("nodeId")
                .fetch()
                .one()
                .map(this::mapNeighborsResult)
                .orElse(new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapNeighborsResult(Map<String, Object> record) {
        Map<String, Object> result = new HashMap<>();

        Object neighborsObj = record.get("neighbors");
        if (neighborsObj instanceof List<?>) {
            List<Map<String, Object>> neighbors = ((List<?>) neighborsObj).stream()
                    .filter(obj -> obj instanceof Map)
                    .map(obj -> (Map<String, Object>) obj)
                    .map(m -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", m.get("id"));
                        map.put("labels", m.get("labels"));
                        map.put("name", m.get("name"));
                        map.put("entityType", m.get("entityType"));
                        map.put("weight", m.get("weight") != null ? m.get("weight") : 0);
                        map.put("frequency", m.get("frequency") != null ? m.get("frequency") : 0);
                        return map;
                    })
                    .collect(Collectors.toList());
            result.put("neighbors", neighbors);
        } else {
            result.put("neighbors", new ArrayList<>());
        }

        Object relationshipsObj = record.get("relationships");
        if (relationshipsObj instanceof List<?>) {
            List<Map<String, Object>> relationships = ((List<?>) relationshipsObj).stream()
                    .filter(obj -> obj instanceof Map)
                    .map(obj -> (Map<String, Object>) obj)
                    .map(m -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("type", m.get("type"));
                        map.put("source", m.get("source"));
                        map.put("target", m.get("target"));
                        map.put("weight", m.get("weight") != null ? m.get("weight") : 1.0);
                        return map;
                    })
                    .collect(Collectors.toList());
            result.put("relationships", relationships);
        } else {
            result.put("relationships", new ArrayList<>());
        }

        return result;
    }

    /**
     * 查询两个节点之间的最短路径
     */
    public Map<String, Object> findShortestPath(Long startId, Long endId) {
        String query = "MATCH path = shortestPath((start)-[*]-(end)) " +
                       "WHERE id(start) = $startId AND id(end) = $endId " +
                       "RETURN [n in nodes(path) | {id: id(n), labels: labels(n), name: n.name}] as nodes, " +
                       "[r in relationships(path) | {type: type(r), source: id(startNode(r)), target: id(endNode(r))}] as relationships";

        return neo4jClient.query(query)
                .bind(startId).to("startId")
                .bind(endId).to("endId")
                .fetch()
                .one()
                .map(this::mapShortestPathResult)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapShortestPathResult(Map<String, Object> record) {
        Map<String, Object> result = new HashMap<>();

        Object nodesObj = record.get("nodes");
        if (nodesObj instanceof List<?>) {
            List<Map<String, Object>> nodes = ((List<?>) nodesObj).stream()
                    .filter(obj -> obj instanceof Map)
                    .map(obj -> (Map<String, Object>) obj)
                    .map(m -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", m.get("id"));
                        map.put("labels", m.get("labels"));
                        map.put("name", m.get("name"));
                        return map;
                    })
                    .collect(Collectors.toList());
            result.put("nodes", nodes);
        } else {
            result.put("nodes", new ArrayList<>());
        }

        Object relationshipsObj = record.get("relationships");
        if (relationshipsObj instanceof List<?>) {
            List<Map<String, Object>> relationships = ((List<?>) relationshipsObj).stream()
                    .filter(obj -> obj instanceof Map)
                    .map(obj -> (Map<String, Object>) obj)
                    .map(m -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("type", m.get("type"));
                        map.put("source", m.get("source"));
                        map.put("target", m.get("target"));
                        return map;
                    })
                    .collect(Collectors.toList());
            result.put("relationships", relationships);
        } else {
            result.put("relationships", new ArrayList<>());
        }

        return result;
    }

    /**
     * 获取图统计信息
     */
    public GraphStatsResponse getStats() {
        // 获取节点总数
        Long nodeCount = neo4jClient.query("MATCH (n) RETURN count(n) as nodeCount")
                .fetch()
                .one()
                .map(record -> {
                    Object count = record.get("nodeCount");
                    if (count instanceof Long) {
                        return (Long) count;
                    }
                    return Long.valueOf(count.toString());
                })
                .orElse(0L);

        // 获取关系总数
        Long relationshipCount = neo4jClient.query("MATCH ()-[r]->() RETURN count(r) as relationshipCount")
                .fetch()
                .one()
                .map(record -> {
                    Object count = record.get("relationshipCount");
                    if (count instanceof Long) {
                        return (Long) count;
                    }
                    return Long.valueOf(count.toString());
                })
                .orElse(0L);

        // 获取节点类型统计
        List<Map<String, Object>> nodesByType = neo4jClient.query("MATCH (n) RETURN labels(n)[0] as type, count(n) as count ORDER BY count DESC")
                .fetch()
                .all()
                .stream()
                .map(record -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", record.get("type"));
                    Object count = record.get("count");
                    if (count instanceof Long) {
                        m.put("count", count);
                    } else if (count != null) {
                        m.put("count", Long.valueOf(count.toString()));
                    } else {
                        m.put("count", 0L);
                    }
                    return m;
                })
                .collect(Collectors.toList());

        // 获取关系类型统计
        List<Map<String, Object>> relationshipsByType = neo4jClient.query("MATCH ()-[r]->() RETURN type(r) as type, count(r) as count ORDER BY count DESC")
                .fetch()
                .all()
                .stream()
                .map(record -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", record.get("type"));
                    Object count = record.get("count");
                    if (count instanceof Long) {
                        m.put("count", count);
                    } else if (count != null) {
                        m.put("count", Long.valueOf(count.toString()));
                    } else {
                        m.put("count", 0L);
                    }
                    return m;
                })
                .collect(Collectors.toList());

        return GraphStatsResponse.builder()
                .totalNodes(nodeCount)
                .totalRelationships(relationshipCount)
                .nodesByType(nodesByType)
                .relationshipsByType(relationshipsByType)
                .build();
    }
}
