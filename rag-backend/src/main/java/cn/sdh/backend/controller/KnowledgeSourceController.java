package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.KnowledgeSource;
import cn.sdh.backend.entity.KnowledgeSyncTask;
import cn.sdh.backend.entity.KnowledgeSourceApiAudit;
import cn.sdh.backend.service.KnowledgeSourceApiKeyService;
import cn.sdh.backend.service.KnowledgeSourceService;
import cn.sdh.backend.service.KnowledgeSourceSyncCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge-base/{knowledgeId}/sources")
public class KnowledgeSourceController {
    private final KnowledgeSourceService sourceService;
    private final KnowledgeSourceApiKeyService apiKeyService;
    private final KnowledgeSourceSyncCoordinator syncCoordinator;

    @GetMapping
    public Result<List<KnowledgeSource>> list(@PathVariable Long knowledgeId) {
        return Result.success(sourceService.list(knowledgeId));
    }

    @PostMapping
    public Result<KnowledgeSource> create(@PathVariable Long knowledgeId,
                                           @RequestBody KnowledgeSourceService.SourceCommand command) {
        return Result.success("数据源已创建", sourceService.create(knowledgeId, command));
    }

    @PostMapping("/{sourceId}")
    public Result<KnowledgeSource> update(@PathVariable Long knowledgeId, @PathVariable Long sourceId,
                                           @RequestBody KnowledgeSourceService.SourceCommand command) {
        return Result.success("数据源已更新", sourceService.update(knowledgeId, sourceId, command));
    }

    @DeleteMapping("/{sourceId}")
    public Result<Void> disable(@PathVariable Long knowledgeId, @PathVariable Long sourceId) {
        sourceService.disable(knowledgeId, sourceId);
        return Result.success("数据源已停用，历史记录予以保留", null);
    }

    @PostMapping("/{sourceId}/ingest")
    public Result<KnowledgeSourceService.IngestResult> ingest(
            @PathVariable Long knowledgeId, @PathVariable Long sourceId,
            @RequestBody KnowledgeSourceService.PushCommand command) {
        return Result.success("同步内容已接收并进入索引处理队列", sourceService.ingest(knowledgeId, sourceId, command));
    }

    @GetMapping("/{sourceId}/tasks")
    public Result<List<KnowledgeSyncTask>> history(@PathVariable Long knowledgeId, @PathVariable Long sourceId) {
        return Result.success(sourceService.history(knowledgeId, sourceId));
    }

    @PostMapping("/{sourceId}/pull")
    public Result<KnowledgeSourceService.IngestResult> pull(
            @PathVariable Long knowledgeId, @PathVariable Long sourceId) {
        return Result.success("拉取同步已完成", syncCoordinator.pullNow(knowledgeId, sourceId));
    }

    @GetMapping("/{sourceId}/api-keys")
    public Result<List<KnowledgeSourceApiKeyService.ApiKeySummary>> apiKeys(
            @PathVariable Long knowledgeId, @PathVariable Long sourceId) {
        return Result.success(apiKeyService.list(knowledgeId, sourceId));
    }

    @PostMapping("/{sourceId}/api-keys")
    public Result<KnowledgeSourceApiKeyService.CreatedApiKey> createApiKey(
            @PathVariable Long knowledgeId, @PathVariable Long sourceId,
            @RequestBody KnowledgeSourceApiKeyService.ApiKeyCommand command) {
        return Result.success("API Key 已创建，明文仅展示一次", apiKeyService.create(knowledgeId, sourceId, command));
    }

    @PostMapping("/{sourceId}/api-keys/{keyId}/rotate")
    public Result<KnowledgeSourceApiKeyService.CreatedApiKey> rotateApiKey(
            @PathVariable Long knowledgeId, @PathVariable Long sourceId, @PathVariable Long keyId) {
        return Result.success("旧密钥已吊销，新密钥明文仅展示一次", apiKeyService.rotate(knowledgeId, sourceId, keyId));
    }

    @DeleteMapping("/{sourceId}/api-keys/{keyId}")
    public Result<Void> revokeApiKey(
            @PathVariable Long knowledgeId, @PathVariable Long sourceId, @PathVariable Long keyId) {
        apiKeyService.revoke(knowledgeId, sourceId, keyId);
        return Result.success("API Key 已吊销", null);
    }

    @GetMapping("/{sourceId}/api-audits")
    public Result<List<KnowledgeSourceApiAudit>> apiAudits(
            @PathVariable Long knowledgeId, @PathVariable Long sourceId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(apiKeyService.auditHistory(knowledgeId, sourceId, limit));
    }
}
