package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.knowledge.connector.KnowledgeSourceConnector;
import cn.sdh.backend.knowledge.connector.KnowledgeSourceConnectorRegistry;
import cn.sdh.backend.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSourceService {
    private final KnowledgeSourceMapper sources;
    private final KnowledgeSourceItemMapper sourceItems;
    private final KnowledgeSyncTaskMapper syncTasks;
    private final KnowledgeDocumentMapper documents;
    private final KnowledgeDocumentRelationMapper relations;
    private final KnowledgeBaseMapper bases;
    private final KnowledgeAccessService access;
    private final KnowledgeSourceConnectorRegistry connectors;
    private final MinioService minio;
    private final DocumentProcessService documentProcessor;
    private final ObjectMapper objectMapper;
    private final KnowledgeSourceCredentialResolver credentialResolver;

    public record SourceCommand(String name, String sourceType, Map<String, Object> configuration,
                                String credentialRef, Integer status, Boolean syncEnabled, Integer syncIntervalMinutes) {
        public SourceCommand(String name, String sourceType, Map<String, Object> configuration,
                             String credentialRef, Integer status) {
            this(name, sourceType, configuration, credentialRef, status, false, 60);
        }
    }
    public record PushItem(String externalId, String title, String content, String sourceUrl,
                           LocalDateTime sourceUpdatedAt, Map<String, Object> metadata, boolean deleted) {}
    public record PushCommand(String cursor, List<PushItem> items) {}
    public record IngestResult(Long taskId, int scanned, int created, int updated, int unchanged, int deleted) {}

    public KnowledgeSource create(Long knowledgeId, SourceCommand command) {
        Long actor = UserContext.getCurrentUserId();
        access.requireOwner(knowledgeId, actor);
        validateCommand(command);
        KnowledgeSourceConnector connector = connectors.require(command.sourceType());
        connector.validate(command.configuration());
        KnowledgeSource source = new KnowledgeSource();
        source.setKnowledgeId(knowledgeId);
        source.setName(command.name().trim());
        source.setSourceType(connector.type());
        source.setConfigJson(toJson(command.configuration()));
        source.setCredentialRef(normalizeCredentialRef(command.credentialRef()));
        source.setStatus(command.status() == null || command.status() != 0 ? 1 : 0);
        configureSchedule(source, connector, command);
        source.setCreatedBy(actor);
        source.setCreateTime(LocalDateTime.now());
        source.setUpdateTime(LocalDateTime.now());
        sources.insert(source);
        return source;
    }

    public List<KnowledgeSource> list(Long knowledgeId) {
        access.requireEdit(knowledgeId, UserContext.getCurrentUserId());
        return sources.selectList(new LambdaQueryWrapper<KnowledgeSource>()
                .eq(KnowledgeSource::getKnowledgeId, knowledgeId).orderByDesc(KnowledgeSource::getCreateTime));
    }

    public KnowledgeSource update(Long knowledgeId, Long sourceId, SourceCommand command) {
        access.requireOwner(knowledgeId, UserContext.getCurrentUserId());
        KnowledgeSource source = requireSource(knowledgeId, sourceId);
        validateCommand(command);
        KnowledgeSourceConnector connector = connectors.require(command.sourceType());
        connector.validate(command.configuration());
        source.setName(command.name().trim());
        source.setSourceType(connector.type());
        source.setConfigJson(toJson(command.configuration()));
        source.setCredentialRef(normalizeCredentialRef(command.credentialRef()));
        if (command.status() != null) source.setStatus(command.status() == 0 ? 0 : 1);
        configureSchedule(source, connector, command);
        source.setUpdateTime(LocalDateTime.now());
        sources.updateById(source);
        return source;
    }

    public void disable(Long knowledgeId, Long sourceId) {
        access.requireOwner(knowledgeId, UserContext.getCurrentUserId());
        KnowledgeSource source = requireSource(knowledgeId, sourceId);
        source.setStatus(0);
        source.setNextSyncTime(null);
        source.setUpdateTime(LocalDateTime.now());
        sources.updateById(source);
    }

    public List<KnowledgeSyncTask> history(Long knowledgeId, Long sourceId) {
        access.requireRead(knowledgeId, UserContext.getCurrentUserId());
        requireSource(knowledgeId, sourceId);
        return syncTasks.selectList(new LambdaQueryWrapper<KnowledgeSyncTask>()
                .eq(KnowledgeSyncTask::getSourceId, sourceId)
                .orderByDesc(KnowledgeSyncTask::getStartTime).last("LIMIT 50"));
    }

    public IngestResult ingest(Long knowledgeId, Long sourceId, PushCommand command) {
        Long actor = UserContext.getCurrentUserId();
        access.requireEdit(knowledgeId, actor);
        KnowledgeSource source = requireSource(knowledgeId, sourceId);
        if (!Integer.valueOf(1).equals(source.getStatus())) throw new BusinessException(400, "数据源已停用");
        KnowledgeSourceConnector connector = connectors.require(source.getSourceType());
        if (connector.mode() != KnowledgeSourceConnector.Mode.PUSH) throw new BusinessException(400, "该数据源不接受推送同步");
        List<PushItem> items = command == null || command.items() == null ? List.of() : command.items();
        if (items.isEmpty() || items.size() > 500) throw new BusinessException(400, "单次同步需包含 1-500 条内容");

        KnowledgeSyncTask task = startTask(source, actor, "API_PUSH");
        int created = 0, updated = 0, unchanged = 0, deleted = 0;
        try {
            for (PushItem item : items) {
                Change change = applyItem(source, item);
                if (change == Change.CREATED) created++;
                else if (change == Change.UPDATED) updated++;
                else if (change == Change.UNCHANGED) unchanged++;
                else deleted++;
            }
            finishTask(task, command.cursor(), items.size(), created, updated, unchanged, deleted, null);
            source.setSyncCursor(trim(command.cursor(), 500));
            source.setLastSyncStatus("COMPLETED");
            source.setLastSyncTime(LocalDateTime.now());
            sources.updateById(source);
            return new IngestResult(task.getId(), items.size(), created, updated, unchanged, deleted);
        } catch (Exception e) {
            finishTask(task, command == null ? null : command.cursor(), items.size(), created, updated, unchanged, deleted, e.getMessage());
            source.setLastSyncStatus("FAILED");
            source.setLastSyncTime(LocalDateTime.now());
            sources.updateById(source);
            if (e instanceof BusinessException business) throw business;
            throw new BusinessException(500, "同步失败，可使用相同 externalId 重试: " + e.getMessage());
        }
    }

    public IngestResult pullNow(Long knowledgeId, Long sourceId) {
        Long actor = UserContext.getCurrentUserId();
        access.requireEdit(knowledgeId, actor);
        return pull(requireSource(knowledgeId, sourceId), actor, "MANUAL_PULL");
    }

    public IngestResult pullScheduled(Long sourceId) {
        KnowledgeSource source = sources.selectById(sourceId);
        if (source == null) throw new BusinessException(404, "知识源不存在");
        return pull(source, source.getCreatedBy(), "SCHEDULED_PULL");
    }

    private IngestResult pull(KnowledgeSource source, Long actor, String triggerType) {
        if (!Integer.valueOf(1).equals(source.getStatus())) throw new BusinessException(400, "数据源已停用");
        KnowledgeSourceConnector connector = connectors.require(source.getSourceType());
        if (connector.mode() != KnowledgeSourceConnector.Mode.PULL) throw new BusinessException(400, "该数据源不支持拉取同步");
        KnowledgeSyncTask task = startTask(source, actor, triggerType);
        int scanned = 0, created = 0, updated = 0, unchanged = 0, deleted = 0;
        String cursor = source.getSyncCursor();
        try {
            Map<String, Object> configuration = fromJson(source.getConfigJson());
            String credential = credentialResolver.resolve(source.getCredentialRef());
            int pageSize = integer(configuration, "pageSize", 100, 1, 500);
            int maxPages = integer(configuration, "maxPagesPerRun", 20, 1, 100);
            long deadline = System.nanoTime() + integer(configuration, "maxRunSeconds", 600, 30, 900) * 1_000_000_000L;
            for (int page = 0; page < maxPages; page++) {
                if (System.nanoTime() > deadline) throw new BusinessException(503, "单次拉取超过最大运行时间，将从原游标重试");
                KnowledgeSourceConnector.FetchResult batch = connector.fetch(new KnowledgeSourceConnector.FetchRequest(
                        configuration, credential, cursor,
                        source.getLastSyncTime() == null ? null : source.getLastSyncTime().atZone(ZoneId.systemDefault()).toInstant(),
                        pageSize));
                if (batch == null || batch.items() == null) throw new BusinessException(502, "连接器返回了无效结果");
                for (KnowledgeSourceConnector.SourceItem item : batch.items()) {
                    Change change = applyItem(source, toPushItem(item));
                    scanned++;
                    if (change == Change.CREATED) created++;
                    else if (change == Change.UPDATED) updated++;
                    else if (change == Change.UNCHANGED) unchanged++;
                    else deleted++;
                }
                String nextCursor = trim(batch.nextCursor(), 500);
                if (!batch.hasMore()) { cursor = nextCursor == null ? cursor : nextCursor; break; }
                if (nextCursor == null || Objects.equals(cursor, nextCursor)) {
                    throw new BusinessException(502, "连接器声明仍有下一页，但没有推进游标");
                }
                cursor = nextCursor;
                if (page == maxPages - 1) throw new BusinessException(503, "单次拉取达到最大分页数，请缩小同步范围或增大 maxPagesPerRun");
            }
            finishTask(task, cursor, scanned, created, updated, unchanged, deleted, null);
            markPullSuccess(source, cursor);
            return new IngestResult(task.getId(), scanned, created, updated, unchanged, deleted);
        } catch (Exception e) {
            finishTask(task, cursor, scanned, created, updated, unchanged, deleted, e.getMessage());
            markPullFailure(source, e.getMessage());
            if (e instanceof BusinessException business) throw business;
            throw new BusinessException(500, "拉取同步失败，将按退避策略重试: " + e.getMessage(), e);
        }
    }

    private Change applyItem(KnowledgeSource source, PushItem item) {
        if (item == null || item.externalId() == null || item.externalId().isBlank()) {
            throw new BusinessException(400, "externalId 不能为空");
        }
        String externalId = trim(item.externalId().trim(), 200);
        KnowledgeSourceItem mapping = sourceItems.selectOne(new LambdaQueryWrapper<KnowledgeSourceItem>()
                .eq(KnowledgeSourceItem::getSourceId, source.getId())
                .eq(KnowledgeSourceItem::getExternalId, externalId));
        if (item.deleted()) return deleteItem(source, mapping);
        if (item.title() == null || item.title().isBlank() || item.content() == null || item.content().isBlank()) {
            throw new BusinessException(400, "非删除内容必须包含 title 和 content");
        }
        byte[] content = item.content().getBytes(StandardCharsets.UTF_8);
        String hash = DigestUtils.md5DigestAsHex(content);
        LocalDateTime now = LocalDateTime.now();
        if (mapping != null && hash.equals(mapping.getContentHash()) && "ACTIVE".equals(mapping.getItemStatus())) {
            mapping.setLastSeenTime(now);
            mapping.setSourceUpdatedAt(item.sourceUpdatedAt());
            mapping.setSourceUrl(trim(item.sourceUrl(), 1000));
            sourceItems.updateById(mapping);
            return Change.UNCHANGED;
        }

        String objectName = "sources/" + source.getId() + "/" + UUID.randomUUID() + ".txt";
        minio.uploadFile(new ByteArrayInputStream(content), objectName, "text/plain; charset=utf-8", content.length);
        if (mapping == null) {
            KnowledgeDocument document = newDocument(source, item, objectName, content.length, now);
            documents.insert(document);
            createRelation(source.getKnowledgeId(), document.getId(), now);
            KnowledgeBase base = bases.selectById(source.getKnowledgeId());
            documentProcessor.processDocumentForKnowledge(document.getId(), source.getKnowledgeId(),
                    base.getChunkSize(), base.getChunkOverlap());
            mapping = new KnowledgeSourceItem();
            mapping.setSourceId(source.getId());
            mapping.setExternalId(externalId);
            mapping.setDocumentId(document.getId());
            mapping.setVersionNo(1);
            mapping.setCreateTime(now);
            updateMapping(mapping, item, hash, now);
            sourceItems.insert(mapping);
            return Change.CREATED;
        }

        KnowledgeDocument document = documents.selectById(mapping.getDocumentId());
        if (document == null) throw new BusinessException(500, "同步映射对应的文档不存在");
        String oldPath = document.getFilePath();
        updateDocument(document, item, objectName, content.length, now);
        documents.updateById(document);
        ensureRelation(source.getKnowledgeId(), document.getId(), now);
        documentProcessor.reprocessDocument(document.getId(), source.getKnowledgeId());
        mapping.setVersionNo((mapping.getVersionNo() == null ? 1 : mapping.getVersionNo()) + 1);
        updateMapping(mapping, item, hash, now);
        sourceItems.updateById(mapping);
        if (oldPath != null && !oldPath.equals(objectName)) {
            try { minio.deleteFile(oldPath); } catch (Exception e) { log.warn("清理旧同步文件失败: {}", oldPath, e); }
        }
        return Change.UPDATED;
    }

    private Change deleteItem(KnowledgeSource source, KnowledgeSourceItem mapping) {
        if (mapping == null || "DELETED".equals(mapping.getItemStatus())) return Change.UNCHANGED;
        documentProcessor.unlinkDocumentFromKnowledge(mapping.getDocumentId(), source.getKnowledgeId());
        mapping.setItemStatus("DELETED");
        mapping.setLastSeenTime(LocalDateTime.now());
        mapping.setUpdateTime(LocalDateTime.now());
        sourceItems.updateById(mapping);
        return Change.DELETED;
    }

    private KnowledgeDocument newDocument(KnowledgeSource source, PushItem item, String path, long size, LocalDateTime now) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(source.getCreatedBy());
        updateDocument(document, item, path, size, now);
        document.setCreateTime(now);
        return document;
    }

    private void updateDocument(KnowledgeDocument document, PushItem item, String path, long size, LocalDateTime now) {
        document.setTitle(trim(item.title().trim(), 255));
        document.setContent(item.content());
        document.setFileType("txt");
        document.setFilePath(path);
        document.setFileSize(size);
        document.setStatus(1);
        document.setUpdateTime(now);
        Map<String, Object> metadata = item.metadata() == null ? Map.of() : item.metadata();
        document.setDepartment(trim(asString(metadata.get("department")), 100));
        document.setDocType(trim(asString(metadata.get("docType")), 50));
        String docDate = asString(metadata.get("docDate"));
        if (docDate != null && !docDate.isBlank()) {
            try { document.setDocDate(LocalDate.parse(docDate)); } catch (Exception e) { throw new BusinessException(400, "docDate 必须为 yyyy-MM-dd"); }
        }
    }

    private void createRelation(Long knowledgeId, Long documentId, LocalDateTime now) {
        KnowledgeBase base = bases.selectById(knowledgeId);
        KnowledgeDocumentRelation relation = new KnowledgeDocumentRelation();
        relation.setKnowledgeId(knowledgeId);
        relation.setDocumentId(documentId);
        relation.setProcessStatus(0);
        relation.setChunkCount(0);
        relation.setChunkMode("default");
        relation.setChunkSize(base.getChunkSize());
        relation.setChunkOverlap(base.getChunkOverlap());
        relation.setEmbeddingModel(base.getEmbeddingModel());
        relation.setCreateTime(now);
        relations.insert(relation);
    }

    private void ensureRelation(Long knowledgeId, Long documentId, LocalDateTime now) {
        if (relations.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId) == null) createRelation(knowledgeId, documentId, now);
    }

    private void updateMapping(KnowledgeSourceItem mapping, PushItem item, String hash, LocalDateTime now) {
        mapping.setSourceUrl(trim(item.sourceUrl(), 1000));
        mapping.setSourceUpdatedAt(item.sourceUpdatedAt());
        mapping.setContentHash(hash);
        mapping.setItemStatus("ACTIVE");
        mapping.setLastSeenTime(now);
        mapping.setUpdateTime(now);
    }

    private KnowledgeSyncTask startTask(KnowledgeSource source, Long actor, String triggerType) {
        KnowledgeSyncTask task = new KnowledgeSyncTask();
        task.setSourceId(source.getId()); task.setKnowledgeId(source.getKnowledgeId()); task.setTriggerType(triggerType);
        task.setTaskStatus("RUNNING"); task.setCursorBefore(source.getSyncCursor()); task.setStartedBy(actor);
        task.setStartTime(LocalDateTime.now());
        syncTasks.insert(task);
        return task;
    }

    private void configureSchedule(KnowledgeSource source, KnowledgeSourceConnector connector, SourceCommand command) {
        int interval = command.syncIntervalMinutes() == null ? 60 : command.syncIntervalMinutes();
        if (interval < 5 || interval > 10_080) throw new BusinessException(400, "同步周期必须在 5-10080 分钟之间");
        boolean enabled = connector.mode() == KnowledgeSourceConnector.Mode.PULL && Boolean.TRUE.equals(command.syncEnabled());
        source.setSyncEnabled(enabled ? 1 : 0);
        source.setSyncIntervalMinutes(interval);
        source.setNextSyncTime(enabled ? LocalDateTime.now() : null);
        if (source.getConsecutiveFailures() == null) source.setConsecutiveFailures(0);
    }

    private void markPullSuccess(KnowledgeSource source, String cursor) {
        LocalDateTime now = LocalDateTime.now();
        source.setSyncCursor(trim(cursor, 500));
        source.setLastSyncStatus("COMPLETED"); source.setLastSyncTime(now);
        source.setConsecutiveFailures(0); source.setLastError(null);
        source.setNextSyncTime(Integer.valueOf(1).equals(source.getSyncEnabled())
                ? now.plusMinutes(source.getSyncIntervalMinutes() == null ? 60 : source.getSyncIntervalMinutes()) : null);
        source.setUpdateTime(now);
        sources.updateById(source);
    }

    private void markPullFailure(KnowledgeSource source, String error) {
        LocalDateTime now = LocalDateTime.now();
        int failures = (source.getConsecutiveFailures() == null ? 0 : source.getConsecutiveFailures()) + 1;
        long retryMinutes = Math.min(360, 5L * (1L << Math.min(failures - 1, 6)));
        source.setLastSyncStatus("FAILED"); source.setLastSyncTime(now);
        source.setConsecutiveFailures(failures); source.setLastError(trim(error, 1000));
        source.setNextSyncTime(Integer.valueOf(1).equals(source.getSyncEnabled()) ? now.plusMinutes(retryMinutes) : null);
        source.setUpdateTime(now);
        sources.updateById(source);
    }

    private PushItem toPushItem(KnowledgeSourceConnector.SourceItem item) {
        LocalDateTime updatedAt = item.sourceUpdatedAt() == null ? null
                : LocalDateTime.ofInstant(item.sourceUpdatedAt(), ZoneId.systemDefault());
        return new PushItem(item.externalId(), item.title(), item.content(), item.sourceUrl(), updatedAt,
                item.metadata(), item.deleted());
    }

    private void finishTask(KnowledgeSyncTask task, String cursor, int scanned, int created, int updated,
                            int unchanged, int deleted, String error) {
        task.setTaskStatus(error == null ? "COMPLETED" : "FAILED"); task.setCursorAfter(trim(cursor, 500));
        task.setScannedCount(scanned); task.setCreatedCount(created); task.setUpdatedCount(updated);
        task.setUnchangedCount(unchanged); task.setDeletedCount(deleted); task.setErrorMessage(trim(error, 1000));
        task.setFinishTime(LocalDateTime.now());
        syncTasks.updateById(task);
    }

    private KnowledgeSource requireSource(Long knowledgeId, Long sourceId) {
        KnowledgeSource source = sources.selectById(sourceId);
        if (source == null || !knowledgeId.equals(source.getKnowledgeId())) throw new BusinessException(404, "知识源不存在");
        return source;
    }

    private void validateCommand(SourceCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.sourceType() == null) {
            throw new BusinessException(400, "数据源名称和类型不能为空");
        }
        if (command.name().trim().length() > 120) throw new BusinessException(400, "数据源名称不能超过 120 个字符");
        rejectSecrets(command.configuration());
    }

    private void rejectSecrets(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                if (key.matches(".*(password|secret|token|cookie|authorization|api.?key).*$")) {
                    throw new BusinessException(400, "敏感凭据不能写入普通配置，请使用 credentialRef");
                }
                rejectSecrets(entry.getValue());
            }
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(this::rejectSecrets);
        }
    }

    private String normalizeCredentialRef(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String result = ref.trim();
        if (result.length() > 200 || !result.matches("[A-Za-z0-9._:/-]+")) throw new BusinessException(400, "credentialRef 格式不正确");
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException e) { throw new BusinessException(400, "数据源配置无法序列化"); }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception e) { throw new BusinessException(500, "数据源配置无法解析"); }
    }

    private int integer(Map<String, Object> config, String key, int defaultValue, int min, int max) {
        Object value = config.get(key);
        int result;
        try { result = value == null ? defaultValue : Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { throw new BusinessException(400, key + " 必须是整数"); }
        if (result < min || result > max) throw new BusinessException(400, key + " 必须在 " + min + "-" + max + " 之间");
        return result;
    }

    private String asString(Object value) { return value == null ? null : String.valueOf(value); }
    private String trim(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
    private enum Change { CREATED, UPDATED, UNCHANGED, DELETED }
}
