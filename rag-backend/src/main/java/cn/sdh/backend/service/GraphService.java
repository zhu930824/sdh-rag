package cn.sdh.backend.service;

import cn.sdh.backend.entity.GraphNode;
import cn.sdh.backend.entity.GraphEdge;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface GraphService extends IService<GraphNode> {

    GraphNode createNode(GraphNode node);

    GraphNode getNodeById(Long id);

    List<GraphNode> getNodesByType(String nodeType, int limit);

    List<GraphNode> getNodesByDocument(Long documentId);

    GraphEdge createEdge(GraphEdge edge);

    void deleteEdge(Long id);

    List<GraphEdge> getEdgesByNodeId(Long nodeId);

    Map<String, Object> getGraphData(Long centerNodeId, int depth);

    List<GraphNode> searchNodes(String keyword);

    void buildGraphFromDocument(Long documentId);
}