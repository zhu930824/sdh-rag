package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.GraphBuildResponse;
import cn.sdh.backend.dto.GraphDataResponse;
import cn.sdh.backend.dto.GraphPathResponse;
import cn.sdh.backend.dto.GraphStatsResponse;
import cn.sdh.backend.service.GraphBuildService;
import cn.sdh.backend.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final GraphBuildService graphBuildService;

    /**
     * 获取图谱数据
     */
    @GetMapping("/data")
    public Result<GraphDataResponse> getGraphData(
            @RequestParam(required = false) Long centerNodeId,
            @RequestParam(defaultValue = "2") int depth) {
        return Result.success(graphService.getGraphData(centerNodeId, depth));
    }

    /**
     * 搜索节点
     */
    @GetMapping("/nodes")
    public Result<List<GraphDataResponse.NodeData>> searchNodes(@RequestParam String keyword) {
        return Result.success(graphService.searchNodes(keyword));
    }

    /**
     * 获取节点详情
     */
    @GetMapping("/nodes/{id}")
    public Result<GraphDataResponse.NodeData> getNode(@PathVariable Long id) {
        GraphDataResponse.NodeData node = graphService.getNodeDetail(id);
        if (node == null) {
            return Result.notFound("节点不存在");
        }
        return Result.success(node);
    }

    /**
     * 获取节点邻居
     */
    @GetMapping("/nodes/{nodeId}/neighbors")
    public Result<GraphDataResponse> getNodeNeighbors(@PathVariable Long nodeId) {
        return Result.success(graphService.getNeighbors(nodeId));
    }

    /**
     * 获取最短路径
     */
    @GetMapping("/path")
    public Result<GraphPathResponse> getShortestPath(
            @RequestParam Long startId,
            @RequestParam Long endId) {
        GraphPathResponse path = graphService.getShortestPath(startId, endId);
        if (path == null) {
            return Result.notFound("未找到路径");
        }
        return Result.success(path);
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public Result<GraphStatsResponse> getStats() {
        return Result.success(graphService.getStats());
    }

    /**
     * 按类型获取节点
     */
    @GetMapping("/nodes/type/{nodeType}")
    public Result<List<GraphDataResponse.NodeData>> getNodesByType(
            @PathVariable String nodeType,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(graphService.getNodesByType(nodeType, limit));
    }

    /**
     * 从文档构建图谱
     */
    @PostMapping("/build/{documentId}")
    public Result<GraphBuildResponse> buildFromDocument(@PathVariable Long documentId) {
        return Result.success(graphBuildService.buildFromDocument(documentId));
    }

    /**
     * 重建文档图谱
     */
    @PostMapping("/rebuild/{documentId}")
    public Result<GraphBuildResponse> rebuildFromDocument(@PathVariable Long documentId) {
        return Result.success(graphBuildService.rebuildFromDocument(documentId));
    }

    /**
     * 批量构建图谱
     */
    @PostMapping("/build/batch")
    public Result<Void> batchBuild(@RequestBody Long[] documentIds) {
        graphBuildService.batchBuild(documentIds);
        return Result.success();
    }

    /**
     * 从知识库构建图谱
     */
    @PostMapping("/build/knowledge/{knowledgeId}")
    public Result<GraphBuildResponse> buildFromKnowledgeBase(@PathVariable Long knowledgeId) {
        return Result.success(graphBuildService.buildFromKnowledgeBase(knowledgeId));
    }

    /**
     * 重建知识库图谱
     */
    @PostMapping("/rebuild/knowledge/{knowledgeId}")
    public Result<GraphBuildResponse> rebuildFromKnowledgeBase(@PathVariable Long knowledgeId) {
        return Result.success(graphBuildService.rebuildFromKnowledgeBase(knowledgeId));
    }

    /**
     * 删除知识库图谱
     */
    @DeleteMapping("/knowledge/{knowledgeId}")
    public Result<Void> deleteByKnowledgeBase(@PathVariable Long knowledgeId) {
        graphBuildService.deleteByKnowledgeBase(knowledgeId);
        return Result.success();
    }

    /**
     * 获取知识库图谱构建状态
     */
    @GetMapping("/status/knowledge/{knowledgeId}")
    public Result<GraphBuildResponse.KnowledgeGraphStatus> getKnowledgeGraphStatus(@PathVariable Long knowledgeId) {
        return Result.success(graphBuildService.getKnowledgeGraphStatus(knowledgeId));
    }
}
