package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.GraphBuildResponse;
import cn.sdh.backend.dto.GraphBuildTask;
import cn.sdh.backend.dto.GraphDataResponse;
import cn.sdh.backend.dto.GraphPathResponse;
import cn.sdh.backend.dto.GraphStatsResponse;
import cn.sdh.backend.service.GraphBuildService;
import cn.sdh.backend.service.GraphService;
import cn.sdh.backend.service.KnowledgeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final GraphBuildService graphBuildService;
    private final KnowledgeAccessService access;

    /**
     * 获取图谱数据
     */
    @GetMapping("/data")
    public Result<GraphDataResponse> getGraphData(
            @RequestParam Long knowledgeBaseId,
            @RequestParam(required = false) Long centerNodeId,
            @RequestParam(defaultValue = "2") int depth) {
        access.requireRead(knowledgeBaseId, UserContext.getCurrentUserId());
        return Result.success(graphService.getGraphData(knowledgeBaseId, centerNodeId, depth));
    }

    /**
     * 搜索节点
     */
    @GetMapping("/nodes")
    public Result<List<GraphDataResponse.NodeData>> searchNodes(
            @RequestParam String keyword,
            @RequestParam Long knowledgeBaseId) {
        access.requireRead(knowledgeBaseId, UserContext.getCurrentUserId());
        return Result.success(graphService.searchNodes(keyword, knowledgeBaseId));
    }

    /**
     * 获取节点详情
     */
    @GetMapping("/nodes/{id}")
    public Result<GraphDataResponse.NodeData> getNode(@PathVariable Long id, @RequestParam Long knowledgeBaseId) {
        requireNodeRead(knowledgeBaseId, id);
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
    public Result<GraphDataResponse> getNodeNeighbors(@PathVariable Long nodeId, @RequestParam Long knowledgeBaseId) {
        requireNodeRead(knowledgeBaseId, nodeId);
        return Result.success(graphService.getGraphData(knowledgeBaseId, nodeId, 1));
    }

    /**
     * 获取最短路径
     */
    @GetMapping("/path")
    public Result<GraphPathResponse> getShortestPath(
            @RequestParam Long startId,
            @RequestParam Long endId,
            @RequestParam Long knowledgeBaseId) {
        requireNodeRead(knowledgeBaseId, startId);
        requireNodeRead(knowledgeBaseId, endId);
        GraphPathResponse path = graphService.getShortestPath(startId, endId);
        if (path != null && path.getNodes().stream().anyMatch(node -> !nodeBelongsTo(knowledgeBaseId, node.getId()))) {
            path = null;
        }
        if (path == null) {
            return Result.notFound("未找到路径");
        }
        return Result.success(path);
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public Result<GraphStatsResponse> getStats(
            @RequestParam Long knowledgeBaseId) {
        access.requireRead(knowledgeBaseId, UserContext.getCurrentUserId());
        return Result.success(graphService.getStats(knowledgeBaseId));
    }

    /**
     * 按类型获取节点
     */
    @GetMapping("/nodes/type/{nodeType}")
    public Result<List<GraphDataResponse.NodeData>> getNodesByType(
            @PathVariable String nodeType,
            @RequestParam Long knowledgeBaseId,
            @RequestParam(defaultValue = "50") int limit) {
        access.requireRead(knowledgeBaseId, UserContext.getCurrentUserId());
        return Result.success(graphService.getNodesByType(nodeType, limit).stream()
            .filter(node -> nodeBelongsTo(knowledgeBaseId, node.getId())).toList());
    }

    /**
     * 从文档构建图谱
     */
    @PostMapping("/build/{documentId}")
    public Result<GraphBuildResponse> buildFromDocument(@PathVariable Long documentId) {
        access.requireDocumentOwner(documentId, UserContext.getCurrentUserId());
        return Result.success(graphBuildService.buildFromDocument(documentId));
    }

    /**
     * 重建文档图谱
     */
    @PostMapping("/rebuild/{documentId}")
    public Result<GraphBuildResponse> rebuildFromDocument(@PathVariable Long documentId) {
        access.requireDocumentOwner(documentId, UserContext.getCurrentUserId());
        return Result.success(graphBuildService.rebuildFromDocument(documentId));
    }

    /**
     * 批量构建图谱
     */
    @PostMapping("/build/batch")
    public Result<Void> batchBuild(@RequestBody Long[] documentIds) {
        for (Long id : documentIds) access.requireDocumentOwner(id, UserContext.getCurrentUserId());
        graphBuildService.batchBuild(documentIds);
        return Result.success();
    }

    /**
     * 从知识库构建图谱（异步）
     */
    @PostMapping("/build/knowledge/{knowledgeId}")
    public Result<GraphBuildTask> buildFromKnowledgeBase(@PathVariable Long knowledgeId) {
        access.requireEdit(knowledgeId, UserContext.getCurrentUserId());
        GraphBuildTask task = graphBuildService.buildFromKnowledgeBaseAsync(knowledgeId);
        return Result.success(task);
    }

    /**
     * 重建知识库图谱（异步）
     */
    @PostMapping("/rebuild/knowledge/{knowledgeId}")
    public Result<GraphBuildTask> rebuildFromKnowledgeBase(@PathVariable Long knowledgeId) {
        access.requireEdit(knowledgeId, UserContext.getCurrentUserId());
        GraphBuildTask task = graphBuildService.rebuildFromKnowledgeBaseAsync(knowledgeId);
        return Result.success(task);
    }

    /**
     * 删除知识库图谱
     */
    @DeleteMapping("/knowledge/{knowledgeId}")
    public Result<Void> deleteByKnowledgeBase(@PathVariable Long knowledgeId) {
        access.requireEdit(knowledgeId, UserContext.getCurrentUserId());
        graphBuildService.deleteByKnowledgeBase(knowledgeId);
        return Result.success();
    }

    /**
     * 获取知识库图谱构建状态
     */
    @GetMapping("/status/knowledge/{knowledgeId}")
    public Result<GraphBuildResponse.KnowledgeGraphStatus> getKnowledgeGraphStatus(@PathVariable Long knowledgeId) {
        access.requireRead(knowledgeId, UserContext.getCurrentUserId());
        return Result.success(graphBuildService.getKnowledgeGraphStatus(knowledgeId));
    }

    /**
     * 获取构建任务状态
     */
    @GetMapping("/task/{taskId}")
    public Result<GraphBuildTask> getBuildTask(@PathVariable String taskId) {
        GraphBuildTask task = graphBuildService.getBuildTask(taskId);
        if (task == null) {
            return Result.notFound("任务不存在");
        }
        if (task.getKnowledgeId() != null) access.requireRead(task.getKnowledgeId(), UserContext.getCurrentUserId());
        else access.requireDocumentRead(task.getDocumentId(), UserContext.getCurrentUserId());
        return Result.success(task);
    }

    private void requireNodeRead(Long knowledgeBaseId, Long nodeId) {
        access.requireRead(knowledgeBaseId, UserContext.getCurrentUserId());
        if (!nodeBelongsTo(knowledgeBaseId, nodeId)) throw new BusinessException(403, "节点不属于当前知识库");
    }

    private boolean nodeBelongsTo(Long knowledgeBaseId, Long nodeId) {
        return graphService.getGraphData(knowledgeBaseId, nodeId, 1).getNodes().stream()
            .anyMatch(node -> nodeId.equals(node.getId()));
    }
}
