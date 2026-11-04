package cn.sdh.backend.service;

import cn.hutool.json.JSONUtil;
import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class KnowledgePublishingService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "REJECTED", "APPROVED");

    private final KnowledgeDraftMapper drafts;
    private final KnowledgePublishRecordMapper records;
    private final KnowledgeDocumentMapper documents;
    private final KnowledgeDocumentRelationMapper relations;
    private final KnowledgeAccessService access;
    private final AgentImprovementService improvements;
    private final MinioService minioService;
    private final DocumentProcessService documentProcessService;
    private final VectorStoreService vectorStoreService;

    public record DraftCommand(Long improvementId, Long knowledgeId, String title, String content,
                               List<String> regressionQueries) {}
    public record ReviewCommand(Boolean approved, String note) {}
    public record ValidationItem(String query, boolean passed, Integer rank) {}
    public record ValidationResult(boolean passed, List<ValidationItem> queries) {}

    public List<KnowledgeDraft> list(String status) {
        Long actor = actor();
        String filter = status == null || status.isBlank() ? null : normalizeStatus(status);
        List<KnowledgeDraft> result = drafts.selectAccessible(actor, filter);
        return result;
    }

    public List<KnowledgePublishRecord> records(Long draftId) {
        KnowledgeDraft draft = visible(draftId);
        return records.selectList(new LambdaQueryWrapper<KnowledgePublishRecord>()
                .eq(KnowledgePublishRecord::getDraftId, draft.getId())
                .orderByDesc(KnowledgePublishRecord::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDraft create(DraftCommand command) {
        Long actor = actor();
        requireCommand(command);
        access.requireEdit(command.knowledgeId(), actor);
        AgentImprovementItem improvement = command.improvementId() == null ? null
                : improvements.requireOwned(command.improvementId(), actor);
        if (improvement != null && improvement.getKnowledgeId() != null
                && !improvement.getKnowledgeId().equals(command.knowledgeId())) {
            throw new BusinessException(400, "草稿知识库与改进项关联知识库不一致");
        }
        if (command.improvementId() != null) {
            KnowledgeDraft existing = drafts.selectOne(new LambdaQueryWrapper<KnowledgeDraft>()
                    .eq(KnowledgeDraft::getImprovementId, command.improvementId()));
            if (existing != null) return existing;
        }
        KnowledgeDraft draft = new KnowledgeDraft();
        draft.setImprovementId(command.improvementId());
        draft.setKnowledgeId(command.knowledgeId());
        draft.setTitle(cap(command.title().trim(), 255));
        draft.setContent(command.content().trim());
        draft.setStatus("DRAFT");
        draft.setRegressionQueries(queriesJson(command.regressionQueries()));
        draft.setContentChecksum(checksum(draft.getContent()));
        draft.setValidationStatus("NOT_RUN");
        draft.setCreatedBy(actor);
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        drafts.insert(draft);
        return draft;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDraft update(Long id, DraftCommand command) {
        KnowledgeDraft draft = owned(id);
        if (!EDITABLE.contains(draft.getStatus()) || draft.getPublishedDocumentId()!=null) throw new BusinessException(409, "当前状态不允许编辑草稿");
        requireCommand(command);
        if (!Objects.equals(draft.getKnowledgeId(), command.knowledgeId())) {
            access.requireEdit(command.knowledgeId(), actor());
            draft.setKnowledgeId(command.knowledgeId());
        }
        draft.setTitle(cap(command.title().trim(), 255));
        draft.setContent(command.content().trim());
        draft.setRegressionQueries(queriesJson(command.regressionQueries()));
        draft.setContentChecksum(checksum(draft.getContent()));
        draft.setStatus("DRAFT");
        draft.setReviewNote(null);
        draft.setReviewedBy(null); draft.setReviewedAt(null);
        draft.setErrorMessage(null);
        draft.setValidationStatus("NOT_RUN");
        draft.setValidationResult(null);
        draft.setUpdatedAt(LocalDateTime.now());
        drafts.updateById(draft);
        drafts.clearReview(draft.getId());
        return draft;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDraft submit(Long id) {
        KnowledgeDraft draft = owned(id);
        access.requireEdit(draft.getKnowledgeId(), actor());
        if (!EDITABLE.contains(draft.getStatus()) || draft.getPublishedDocumentId()!=null) throw new BusinessException(409, "当前内容不能重新提交审核");
        validateContent(draft);
        KnowledgePublicationContent.queries(draft.getRegressionQueries());
        draft.setStatus("IN_REVIEW");
        draft.setReviewedBy(null); draft.setReviewedAt(null); draft.setReviewNote(null);
        draft.setUpdatedAt(LocalDateTime.now());
        drafts.updateById(draft);
        drafts.clearReview(draft.getId());
        return draft;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDraft review(Long id, ReviewCommand command) {
        KnowledgeDraft draft = visibleForUpdate(id);
        Long actor = actor();
        access.requireEdit(draft.getKnowledgeId(), actor);
        if (!"IN_REVIEW".equals(draft.getStatus())) throw new BusinessException(409, "草稿不在待审核状态");
        if (command == null || command.approved() == null) throw new BusinessException(400, "审核结论不能为空");
        draft.setStatus(command.approved() ? "APPROVED" : "REJECTED");
        draft.setReviewNote(capNullable(command.note(), 1000));
        draft.setReviewedBy(actor);
        draft.setReviewedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        drafts.updateById(draft);
        return draft;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDraft publish(Long id) {
        KnowledgeDraft draft = visibleForUpdate(id);
        Long actor = actor();
        KnowledgeBase base = access.requireEdit(draft.getKnowledgeId(), actor);
        if (!"APPROVED".equals(draft.getStatus())) throw new BusinessException(409, "只有审核通过的草稿可发布");
        if(draft.getReviewedBy()==null || draft.getReviewedAt()==null) throw new BusinessException(409,"草稿缺少审核记录");
        validateContent(draft);
        KnowledgePublicationContent.queries(draft.getRegressionQueries());

        byte[] data = markdown(draft).getBytes(StandardCharsets.UTF_8);
        if(data.length>KnowledgePublicationContent.MAX_SOURCE_BYTES) throw new BusinessException(400,"发布草稿文件超过 1 MiB");
        String objectName = "knowledge-publishing/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/" + UUID.randomUUID() + ".md";
        minioService.uploadFile(new ByteArrayInputStream(data), objectName, "text/markdown; charset=utf-8", data.length);
        cleanupSourceOnRollback(objectName);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(draft.getTitle());
        document.setContent(draft.getContent());
        document.setFileType("md");
        document.setFilePath(objectName);
        document.setFileSize((long) data.length);
        document.setUserId(actor);
        document.setStatus(0);
        document.setDocType("faq");
        document.setDocDate(LocalDate.now());
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        documents.insert(document);

        KnowledgeDocumentRelation relation = new KnowledgeDocumentRelation();
        relation.setKnowledgeId(draft.getKnowledgeId());
        relation.setDocumentId(document.getId());
        relation.setProcessStatus(0);
        relation.setChunkCount(0);
        relation.setChunkMode("smart");
        relation.setChunkSize(base.getChunkSize());
        relation.setChunkOverlap(base.getChunkOverlap());
        relation.setEmbeddingModel(base.getEmbeddingModel());
        relation.setCreateTime(LocalDateTime.now());
        relations.insert(relation);

        draft.setPublishedDocumentId(document.getId());
        draft.setStatus("PUBLISHING");
        draft.setErrorMessage(null);
        draft.setValidationStatus("NOT_RUN");
        draft.setValidationResult(null);
        draft.setUpdatedAt(LocalDateTime.now());
        drafts.updateById(draft);
        audit(draft, "PUBLISH", "SUBMITTED", "已提交统一文档切片与增量索引", actor);

        DocumentLinkConfig config = new DocumentLinkConfig();
        config.setChunkMode("smart");
        config.setChunkSize(base.getChunkSize());
        config.setChunkOverlap(base.getChunkOverlap());
        config.setEmbeddingModel(base.getEmbeddingModel());
        documentProcessService.processDocumentForKnowledge(document.getId(),draft.getKnowledgeId(),config);
        return draft;
    }

    @Transactional(rollbackFor = Exception.class)
    public ValidationResult validate(Long id) {
        KnowledgeDraft draft = visible(id);
        access.requireEdit(draft.getKnowledgeId(), actor());
        if (!"PUBLISHED".equals(draft.getStatus())) throw new BusinessException(409, "知识完成索引后才能执行回归验证");
        ValidationResult validation = evaluateQueries(draft);
        boolean passed = validation.passed();
        draft.setValidationStatus(passed ? "PASSED" : "FAILED");
        draft.setValidationResult(JSONUtil.toJsonStr(validation));
        draft.setUpdatedAt(LocalDateTime.now());
        if(drafts.updateValidation(draft.getId(),draft.getPublishedGeneration(),draft.getValidationStatus(),draft.getValidationResult())!=1) throw new BusinessException(409,"发布版本已变化，请重新验证");
        audit(draft, "VALIDATE", passed ? "PASSED" : "FAILED", draft.getValidationResult(), actor());
        return validation;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDraft rollback(Long id) {
        KnowledgeDraft draft = visibleForUpdate(id);
        Long actor = actor();
        access.requireEdit(draft.getKnowledgeId(), actor);
        if (!Set.of("PUBLISHED", "PUBLISH_FAILED", "GATE_FAILED").contains(draft.getStatus())) {
            throw new BusinessException(409, "只有已发布或发布失败的知识可回滚");
        }
        Long documentId = draft.getPublishedDocumentId();
        KnowledgeDocumentRelation relation = relations.selectByKnowledgeIdAndDocumentId(draft.getKnowledgeId(), documentId);
        if (relation != null) documentProcessService.unlinkDocumentFromKnowledge(documentId, draft.getKnowledgeId());
        KnowledgeDocument document = documents.selectById(documentId);
        if (document != null) {
            document.setStatus(2);
            document.setUpdateTime(LocalDateTime.now());
            documents.updateById(document);
        }
        draft.setStatus("ROLLED_BACK");
        draft.setRolledBackAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        drafts.updateById(draft);
        audit(draft, "ROLLBACK", "COMPLETED", "已移除知识库关联与检索向量，原始文档保留用于审计", actor);
        return draft;
    }

    private void cleanupSourceOnRollback(String objectName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { minioService.deleteFile(objectName); } catch (Exception ignored) { }
                }
            }
        });
    }

    private void requireCommand(DraftCommand command) {
        if (command == null || command.knowledgeId() == null) throw new BusinessException(400, "知识库不能为空");
        if (command.title() == null || command.title().isBlank()) throw new BusinessException(400, "标题不能为空");
        if (command.content() == null || command.content().trim().length() < 50) throw new BusinessException(400, "知识正文至少 50 个字符");
    }

    private ValidationResult evaluateQueries(KnowledgeDraft draft) {
        List<String> queries = parseQueries(draft.getRegressionQueries());
        if (queries.isEmpty()) throw new BusinessException(400, "请先为草稿配置至少一个回归问题");
        List<ValidationItem> result = new ArrayList<>();
        for (String query : queries) {
            List<Document> hits = vectorStoreService.similaritySearch(query, draft.getKnowledgeId(), 5);
            Integer rank = null;
            for (int i = 0; i < hits.size(); i++) {
                if (sameId(hits.get(i).getMetadata().get("document_id"), draft.getPublishedDocumentId())) {
                    rank = i + 1;
                    break;
                }
            }
            result.add(new ValidationItem(query, rank != null, rank));
        }
        return new ValidationResult(result.stream().allMatch(ValidationItem::passed), result);
    }

    private void validateContent(KnowledgeDraft draft) {
        if (draft.getTitle() == null || draft.getTitle().isBlank() || draft.getContent() == null || draft.getContent().trim().length() < 50) {
            throw new BusinessException(400, "标题不能为空，知识正文至少 50 个字符");
        }
        if(markdown(draft).getBytes(StandardCharsets.UTF_8).length>KnowledgePublicationContent.MAX_SOURCE_BYTES) throw new BusinessException(400,"发布草稿文件超过 1 MiB");
    }

    private KnowledgeDraft owned(Long id) {
        Long actor = actor();
        KnowledgeDraft draft = id == null ? null : drafts.selectByIdForUpdate(id);
        if (draft == null || !actor.equals(draft.getCreatedBy())) throw new BusinessException(404, "知识草稿不存在");
        return draft;
    }

    private KnowledgeDraft visible(Long id) {
        KnowledgeDraft draft = id == null ? null : drafts.selectById(id);
        if (draft == null) throw new BusinessException(404, "知识草稿不存在");
        access.requireRead(draft.getKnowledgeId(), actor());
        return draft;
    }
    private KnowledgeDraft visibleForUpdate(Long id) {
        KnowledgeDraft draft=id==null?null:drafts.selectByIdForUpdate(id);
        if(draft==null) throw new BusinessException(404,"知识草稿不存在");
        access.requireRead(draft.getKnowledgeId(),actor()); return draft;
    }

    private Long actor() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new BusinessException(401, "请先登录");
        return actor;
    }

    private void audit(KnowledgeDraft draft, String action, String status, String detail, Long actor) {
        KnowledgePublishRecord record = new KnowledgePublishRecord();
        record.setDraftId(draft.getId());
        record.setKnowledgeId(draft.getKnowledgeId());
        record.setDocumentId(draft.getPublishedDocumentId());
        record.setAction(action);
        record.setStatus(status);
        record.setDetail(detail);
        record.setOperatorId(actor);
        record.setCreatedAt(LocalDateTime.now());
        records.insert(record);
    }

    private String markdown(KnowledgeDraft draft) {
        return KnowledgePublicationContent.markdown(draft.getTitle(),draft.getContent());
    }

    private String queriesJson(List<String> values) {
        if (values == null) return "[]";
        List<String> cleaned = values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty())
                .distinct().limit(10).map(v -> cap(v, 500)).toList();
        return JSONUtil.toJsonStr(cleaned);
    }

    private List<String> parseQueries(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return JSONUtil.toList(json, String.class); }
        catch (Exception ignored) { return List.of(); }
    }

    private String checksum(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean sameId(Object value, Long expected) {
        return value != null && expected != null && expected.toString().equals(value.toString());
    }

    private String normalizeStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "IN_REVIEW", "APPROVED", "REJECTED", "PUBLISHING", "PUBLISHED", "PUBLISH_FAILED", "GATE_FAILED", "ROLLED_BACK").contains(status)) {
            throw new BusinessException(400, "草稿状态不正确");
        }
        return status;
    }

    private String cap(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
    private String capNullable(String value, int max) {
        return value == null || value.isBlank() ? null : cap(value.trim(), max);
    }
}
