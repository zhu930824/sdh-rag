package cn.sdh.backend.service.impl;

import cn.sdh.backend.dto.GraphDataResponse;
import cn.sdh.backend.dto.GraphPathResponse;
import cn.sdh.backend.dto.GraphStatsResponse;
import cn.sdh.backend.graph.node.ConceptNode;
import cn.sdh.backend.graph.node.EntityNode;
import cn.sdh.backend.graph.node.KeywordNode;
import cn.sdh.backend.graph.repository.ConceptNodeRepository;
import cn.sdh.backend.graph.repository.CustomGraphRepository;
import cn.sdh.backend.graph.repository.EntityNodeRepository;
import cn.sdh.backend.graph.repository.KeywordNodeRepository;
import cn.sdh.backend.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private final CustomGraphRepository customGraphRepository;
    private final EntityNodeRepository entityNodeRepository;
    private final ConceptNodeRepository conceptNodeRepository;
    private final KeywordNodeRepository keywordNodeRepository;

    @Override
    public GraphDataResponse getGraphData(Long centerNodeId, int depth) {
        List<Long> nodeIds;

        if (centerNodeId != null) {
            // 以指定节点为中心展开
            nodeIds = customGraphRepository.expandNodeIds(centerNodeId, depth);
        } else {
            // 获取全图概览 - top 100节点
            nodeIds = customGraphRepository.getTopNodeIds(100);
        }

        if (nodeIds == null || nodeIds.isEmpty()) {
            return GraphDataResponse.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        }

        // 过滤掉null值
        nodeIds = nodeIds.stream().filter(Objects::nonNull).collect(Collectors.toList());

        // 获取节点详情
        List<Map<String, Object>> nodesData = customGraphRepository.getNodesByIds(nodeIds);

        // 获取关系
        List<Map<String, Object>> relationshipsData = customGraphRepository.getRelationshipsBetweenNodes(nodeIds);

        return buildGraphResponse(nodesData, relationshipsData);
    }

    @Override
    public List<GraphDataResponse.NodeData> searchNodes(String keyword) {
        List<GraphDataResponse.NodeData> results = new ArrayList<>();

        // 搜索实体
        List<EntityNode> entities = entityNodeRepository.searchByName(keyword, 20);
        results.addAll(entities.stream()
                .map(this::convertEntityToNodeData)
                .collect(Collectors.toList()));

        // 搜索概念
        List<ConceptNode> concepts = conceptNodeRepository.searchByName(keyword, 10);
        results.addAll(concepts.stream()
                .map(this::convertConceptToNodeData)
                .collect(Collectors.toList()));

        // 搜索关键词
        List<KeywordNode> keywords = keywordNodeRepository.searchByName(keyword, 10);
        results.addAll(keywords.stream()
                .map(this::convertKeywordToNodeData)
                .collect(Collectors.toList()));

        return results.stream().limit(50).collect(Collectors.toList());
    }

    @Override
    public GraphDataResponse.NodeData getNodeDetail(Long nodeId) {
        // 尝试从各类节点中查找
        Optional<EntityNode> entity = entityNodeRepository.findByIdWithRelationships(nodeId);
        if (entity.isPresent()) {
            return convertEntityToNodeData(entity.get());
        }

        Optional<ConceptNode> concept = conceptNodeRepository.findByIdWithRelationships(nodeId);
        if (concept.isPresent()) {
            return convertConceptToNodeData(concept.get());
        }

        Optional<KeywordNode> keyword = keywordNodeRepository.findById(nodeId);
        if (keyword.isPresent()) {
            return convertKeywordToNodeData(keyword.get());
        }

        return null;
    }

    @Override
    public GraphDataResponse getNeighbors(Long nodeId) {
        Map<String, Object> result = customGraphRepository.findNeighbors(nodeId);
        return parseNeighborsResult(result);
    }

    @Override
    public GraphPathResponse getShortestPath(Long startId, Long endId) {
        Map<String, Object> result = customGraphRepository.findShortestPath(startId, endId);

        if (result == null || result.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> nodesData = (List<Map<String, Object>>) result.get("nodes");
        List<Map<String, Object>> relationsData = (List<Map<String, Object>>) result.get("relationships");

        List<GraphPathResponse.NodeInfo> nodes = new ArrayList<>();
        if (nodesData != null) {
            for (Map<String, Object> n : nodesData) {
                nodes.add(GraphPathResponse.NodeInfo.builder()
                        .id(((Number) n.get("id")).longValue())
                        .labels((List<String>) n.get("labels"))
                        .name((String) n.get("name"))
                        .entityType((String) n.get("entityType"))
                        .build());
            }
        }

        List<GraphPathResponse.RelationInfo> relationships = new ArrayList<>();
        if (relationsData != null) {
            for (Map<String, Object> r : relationsData) {
                relationships.add(GraphPathResponse.RelationInfo.builder()
                        .type((String) r.get("type"))
                        .source(((Number) r.get("source")).longValue())
                        .target(((Number) r.get("target")).longValue())
                        .build());
            }
        }

        return GraphPathResponse.builder()
                .nodes(nodes)
                .relationships(relationships)
                .length(nodes.size() - 1)
                .build();
    }

    @Override
    public GraphStatsResponse getStats() {
        return customGraphRepository.getStats();
    }

    @Override
    public List<GraphDataResponse.NodeData> getNodesByType(String nodeType, int limit) {
        List<GraphDataResponse.NodeData> results = new ArrayList<>();

        switch (nodeType.toUpperCase()) {
            case "ENTITY":
                List<EntityNode> entities = entityNodeRepository.findAll();
                results.addAll(entities.stream()
                        .limit(limit)
                        .map(this::convertEntityToNodeData)
                        .collect(Collectors.toList()));
                break;
            case "CONCEPT":
                List<ConceptNode> concepts = conceptNodeRepository.findTopConcepts(limit);
                results.addAll(concepts.stream()
                        .map(this::convertConceptToNodeData)
                        .collect(Collectors.toList()));
                break;
            case "KEYWORD":
                List<KeywordNode> keywords = keywordNodeRepository.findTopKeywords(limit);
                results.addAll(keywords.stream()
                        .map(this::convertKeywordToNodeData)
                        .collect(Collectors.toList()));
                break;
        }

        return results;
    }

    /**
     * 构建图响应数据
     */
    private GraphDataResponse buildGraphResponse(List<Map<String, Object>> nodesData, List<Map<String, Object>> relationshipsData) {
        List<GraphDataResponse.NodeData> nodes = new ArrayList<>();
        List<GraphDataResponse.EdgeData> edges = new ArrayList<>();

        Set<Long> addedNodeIds = new HashSet<>();
        if (nodesData != null) {
            for (Map<String, Object> n : nodesData) {
                Long id = ((Number) n.get("id")).longValue();
                if (addedNodeIds.contains(id)) {
                    continue;
                }
                addedNodeIds.add(id);

                List<String> labels = (List<String>) n.get("labels");
                String type = labels != null && !labels.isEmpty() ? labels.get(0) : "Unknown";

                nodes.add(GraphDataResponse.NodeData.builder()
                        .id(id)
                        .label((String) n.get("name"))
                        .type(type)
                        .entityType((String) n.get("entityType"))
                        .description((String) n.get("description"))
                        .documentId(n.get("documentId") != null ? ((Number) n.get("documentId")).longValue() : null)
                        .weight(n.get("weight") != null ? ((Number) n.get("weight")).intValue() : 0)
                        .frequency(n.get("frequency") != null ? ((Number) n.get("frequency")).intValue() : 0)
                        .build());
            }
        }

        Set<String> addedEdgeIds = new HashSet<>();
        if (relationshipsData != null) {
            for (Map<String, Object> r : relationshipsData) {
                Long source = ((Number) r.get("source")).longValue();
                Long target = ((Number) r.get("target")).longValue();
                String type = (String) r.get("type");
                String edgeId = source + "-" + type + "-" + target;

                if (addedEdgeIds.contains(edgeId)) {
                    continue;
                }
                addedEdgeIds.add(edgeId);

                edges.add(GraphDataResponse.EdgeData.builder()
                        .id(edgeId)
                        .source(source)
                        .target(target)
                        .relationType(type)
                        .weight(r.get("weight") != null ? ((Number) r.get("weight")).doubleValue() : 1.0)
                        .build());
            }
        }

        return GraphDataResponse.builder().nodes(nodes).edges(edges).build();
    }

    private GraphDataResponse parseNeighborsResult(Map<String, Object> result) {
        List<GraphDataResponse.NodeData> nodes = new ArrayList<>();
        List<GraphDataResponse.EdgeData> edges = new ArrayList<>();

        if (result == null) {
            return GraphDataResponse.builder().nodes(nodes).edges(edges).build();
        }

        // 处理邻居节点（返回的是neighbors字段）
        List<Map<String, Object>> neighborsData = (List<Map<String, Object>>) result.get("neighbors");
        List<Map<String, Object>> relationshipsData = (List<Map<String, Object>>) result.get("relationships");

        if (neighborsData != null) {
            Set<Long> addedIds = new HashSet<>();
            for (Map<String, Object> n : neighborsData) {
                Long id = ((Number) n.get("id")).longValue();
                if (addedIds.contains(id)) {
                    continue;
                }
                addedIds.add(id);

                List<String> labels = (List<String>) n.get("labels");
                String type = labels != null && !labels.isEmpty() ? labels.get(0) : "Unknown";

                nodes.add(GraphDataResponse.NodeData.builder()
                        .id(id)
                        .label((String) n.get("name"))
                        .type(type)
                        .entityType((String) n.get("entityType"))
                        .description((String) n.get("description"))
                        .documentId(n.get("documentId") != null ? ((Number) n.get("documentId")).longValue() : null)
                        .weight(n.get("weight") != null ? ((Number) n.get("weight")).intValue() : 0)
                        .frequency(n.get("frequency") != null ? ((Number) n.get("frequency")).intValue() : 0)
                        .build());
            }
        }

        if (relationshipsData != null) {
            Set<String> addedEdges = new HashSet<>();
            for (Map<String, Object> r : relationshipsData) {
                Long source = ((Number) r.get("source")).longValue();
                Long target = ((Number) r.get("target")).longValue();
                String type = (String) r.get("type");
                String edgeId = source + "-" + type + "-" + target;

                if (addedEdges.contains(edgeId)) {
                    continue;
                }
                addedEdges.add(edgeId);

                edges.add(GraphDataResponse.EdgeData.builder()
                        .id(edgeId)
                        .source(source)
                        .target(target)
                        .relationType(type)
                        .weight(r.get("weight") != null ? ((Number) r.get("weight")).doubleValue() : 1.0)
                        .build());
            }
        }

        return GraphDataResponse.builder().nodes(nodes).edges(edges).build();
    }

    private GraphDataResponse.NodeData convertEntityToNodeData(EntityNode entity) {
        return GraphDataResponse.NodeData.builder()
                .id(entity.getId())
                .label(entity.getName())
                .type("Entity")
                .entityType(entity.getEntityType())
                .description(entity.getDescription())
                .sourceDocumentId(entity.getSourceDocumentId())
                .frequency(entity.getFrequency())
                .build();
    }

    private GraphDataResponse.NodeData convertConceptToNodeData(ConceptNode concept) {
        return GraphDataResponse.NodeData.builder()
                .id(concept.getId())
                .label(concept.getName())
                .type("Concept")
                .description(concept.getDescription())
                .weight(concept.getWeight())
                .build();
    }

    private GraphDataResponse.NodeData convertKeywordToNodeData(KeywordNode keyword) {
        return GraphDataResponse.NodeData.builder()
                .id(keyword.getId())
                .label(keyword.getName())
                .type("Keyword")
                .sourceDocumentId(keyword.getSourceDocumentId())
                .frequency(keyword.getFrequency())
                .build();
    }
}
