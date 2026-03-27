package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.GraphNode;
import cn.sdh.backend.entity.GraphEdge;
import cn.sdh.backend.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/data")
    public Result<Map<String, Object>> getGraphData(
            @RequestParam(required = false) Long centerNodeId,
            @RequestParam(defaultValue = "2") int depth) {
        return Result.success(graphService.getGraphData(centerNodeId, depth));
    }

    @GetMapping("/nodes")
    public Result<List<GraphNode>> searchNodes(@RequestParam String keyword) {
        return Result.success(graphService.searchNodes(keyword));
    }

    @GetMapping("/nodes/{id}")
    public Result<GraphNode> getNode(@PathVariable Long id) {
        GraphNode node = graphService.getNodeById(id);
        if (node == null) {
            return Result.notFound("节点不存在");
        }
        return Result.success(node);
    }

    @PostMapping("/nodes")
    public Result<GraphNode> createNode(@RequestBody GraphNode node) {
        return Result.success(graphService.createNode(node));
    }

    @DeleteMapping("/nodes/{id}")
    public Result<Void> deleteNode(@PathVariable Long id) {
        graphService.removeById(id);
        return Result.success();
    }

    @PostMapping("/edges")
    public Result<GraphEdge> createEdge(@RequestBody GraphEdge edge) {
        return Result.success(graphService.createEdge(edge));
    }

    @DeleteMapping("/edges/{id}")
    public Result<Void> deleteEdge(@PathVariable Long id) {
        graphService.deleteEdge(id);
        return Result.success();
    }

    @GetMapping("/nodes/{nodeId}/edges")
    public Result<List<GraphEdge>> getNodeEdges(@PathVariable Long nodeId) {
        return Result.success(graphService.getEdgesByNodeId(nodeId));
    }

    @PostMapping("/build/{documentId}")
    public Result<Void> buildFromDocument(@PathVariable Long documentId) {
        graphService.buildGraphFromDocument(documentId);
        return Result.success();
    }
}