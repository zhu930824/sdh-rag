package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.GraphNode;
import cn.sdh.backend.entity.GraphEdge;
import cn.sdh.backend.mapper.GraphNodeMapper;
import cn.sdh.backend.mapper.GraphEdgeMapper;
import cn.sdh.backend.service.GraphService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphServiceImpl extends ServiceImpl<GraphNodeMapper, GraphNode> implements GraphService {

    private final GraphNodeMapper graphNodeMapper;
    private final GraphEdgeMapper graphEdgeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphNode createNode(GraphNode node) {
        node.setWeight(node.getWeight() != null ? node.getWeight() : 1);
        node.setStatus(node.getStatus() != null ? node.getStatus() : 1);
        save(node);
        return node;
    }

    @Override
    public GraphNode getNodeById(Long id) {
        return getById(id);
    }

    @Override
    public List<GraphNode> getNodesByType(String nodeType, int limit) {
        return graphNodeMapper.selectTopByType(nodeType, limit);
    }

    @Override
    public List<GraphNode> getNodesByDocument(Long documentId) {
        return graphNodeMapper.selectByDocumentId(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphEdge createEdge(GraphEdge edge) {
        graphEdgeMapper.insert(edge);
        return edge;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEdge(Long id) {
        graphEdgeMapper.deleteById(id);
    }

    @Override
    public List<GraphEdge> getEdgesByNodeId(Long nodeId) {
        return graphEdgeMapper.selectByNodeId(nodeId);
    }

    @Override
    public Map<String, Object> getGraphData(Long centerNodeId, int depth) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<Long> visitedNodeIds = new HashSet<>();

        if (centerNodeId != null) {
            collectGraphData(centerNodeId, depth, nodes, edges, visitedNodeIds);
        } else {
            LambdaQueryWrapper<GraphNode> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(GraphNode::getStatus, 1).orderByDesc(GraphNode::getWeight).last("LIMIT 100");
            nodes = list(wrapper);
            
            if (!nodes.isEmpty()) {
                String nodeIds = nodes.stream()
                    .map(n -> String.valueOf(n.getId()))
                    .collect(Collectors.joining(","));
                edges = graphEdgeMapper.selectByNodeIds(nodeIds);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private void collectGraphData(Long nodeId, int depth, List<GraphNode> nodes, 
                                   List<GraphEdge> edges, Set<Long> visitedNodeIds) {
        if (depth < 0 || visitedNodeIds.contains(nodeId)) {
            return;
        }

        GraphNode node = getById(nodeId);
        if (node == null) {
            return;
        }

        nodes.add(node);
        visitedNodeIds.add(nodeId);

        List<GraphEdge> relatedEdges = graphEdgeMapper.selectByNodeId(nodeId);
        edges.addAll(relatedEdges);

        for (GraphEdge edge : relatedEdges) {
            Long nextNodeId = edge.getSourceId().equals(nodeId) ? edge.getTargetId() : edge.getSourceId();
            collectGraphData(nextNodeId, depth - 1, nodes, edges, visitedNodeIds);
        }
    }

    @Override
    public List<GraphNode> searchNodes(String keyword) {
        LambdaQueryWrapper<GraphNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GraphNode::getStatus, 1)
               .like(GraphNode::getName, keyword)
               .or()
               .like(GraphNode::getDescription, keyword)
               .orderByDesc(GraphNode::getWeight)
               .last("LIMIT 50");
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void buildGraphFromDocument(Long documentId) {
        // TODO: 实现文档到知识图谱的自动构建
        // 1. 提取文档中的实体
        // 2. 创建节点
        // 3. 创建关系
    }
}