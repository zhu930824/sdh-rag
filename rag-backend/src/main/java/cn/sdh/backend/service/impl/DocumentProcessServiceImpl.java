package cn.sdh.backend.service.impl;

import cn.hutool.json.JSONUtil;
import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.config.FreshnessConfig;
import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import cn.sdh.backend.job.DocumentIndexJobHandler;
import cn.sdh.backend.job.DocumentIndexJobPayload;
import cn.sdh.backend.job.PlatformJobExecutionContext;
import cn.sdh.backend.service.DocumentDedupService;
import cn.sdh.backend.service.DocumentIndexLock;
import cn.sdh.backend.service.DocumentProcessService;
import cn.sdh.backend.service.MinioService;
import cn.sdh.backend.service.SummaryIndexService;
import cn.sdh.backend.service.VectorStoreService;
import cn.sdh.backend.service.PlatformJobService;
import cn.sdh.backend.entity.PlatformJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文档处理服务实现
 * 采用独立存储方案：每个知识库独立存储自己的切分版本
 * 分块数据直接存储到 Elasticsearch，不再存储到 MySQL
 * 支持根据知识库配置动态选择嵌入模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessServiceImpl implements DocumentProcessService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentRelationMapper relationMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final MinioService minioService;
    private final VectorStoreService vectorStoreService;
    private final DocumentDedupService documentDedupService;
    private final SummaryIndexService summaryIndexService;
    private final PlatformJobService platformJobService;
    private final DocumentIndexLock documentIndexLock;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processDocumentForKnowledge(Long documentId, Long knowledgeId, DocumentLinkConfig config) {
        enqueueIndex(documentId, knowledgeId, config);
    }

    @Override
    public String executeIndexJob(DocumentIndexJobPayload payload, Long jobId,
                                  PlatformJobExecutionContext context) {
        KnowledgeDocumentRelation current = relationMapper.selectById(payload.relationId());
        if (!isCurrentGeneration(current, payload, jobId)) {
            return "索引版本已过期，安全跳过";
        }

        DocumentIndexLock.LockHandle lock = documentIndexLock.acquire(payload.relationId());
        if (lock == null) {
            throw new IllegalStateException("同一文档已有索引任务运行，稍后重试");
        }
        try {
            current = relationMapper.selectById(payload.relationId());
            if (!isCurrentGeneration(current, payload, jobId)) {
                return "索引版本已过期，安全跳过";
            }
            if (relationMapper.markIndexRunning(payload.relationId(), payload.revision(), jobId,
                    LocalDateTime.now()) != 1) {
                return "索引状态已变化，安全跳过";
            }
            return executeUnderLock(payload, jobId, context);
        } finally {
            documentIndexLock.release(lock);
        }
    }

    private String executeUnderLock(DocumentIndexJobPayload payload, Long jobId,
                                    PlatformJobExecutionContext context) {
        Long documentId = payload.documentId();
        Long knowledgeId = payload.knowledgeId();
        DocumentLinkConfig config = payload.config() == null ? new DocumentLinkConfig() : payload.config();
        KnowledgeDocumentRelation relation = relationMapper.selectById(payload.relationId());
        log.info("开始可靠文档索引: relationId={}, revision={}, documentId={}, knowledgeId={}, chunkMode={}",
                payload.relationId(), payload.revision(), documentId, knowledgeId, config.getChunkMode());

        try {
            context.checkpoint();
            // 获取文档
            KnowledgeDocument document = documentMapper.selectById(documentId);
            if (document == null) {
                throw new RuntimeException("文档不存在: " + documentId);
            }

            // 解析文档内容
            String content = parseDocumentContent(document.getFilePath());
            if (content == null || content.trim().isEmpty()) {
                log.warn("文档内容为空: documentId={}", documentId);
                completeEmptyIndex(payload, jobId, documentId, knowledgeId);
                return "文档内容为空，已清理旧索引";
            }

            // === RAG优化：文档去重 ===
            String contentMd5 = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
            Long duplicateDocId = documentDedupService.checkMd5Duplicate(knowledgeId, contentMd5);
            boolean ownHashExists = documentId.equals(duplicateDocId);
            if (duplicateDocId != null && !ownHashExists) {
                document.setContentMd5(contentMd5);
                document.setQualityStatus(2);
                documentMapper.updateById(document);
                documentDedupService.saveDocumentHash(documentId, knowledgeId, contentMd5, content);
                log.warn("文档 [{}] 与 [{}] MD5完全重复，跳过索引", documentId, duplicateDocId);
                completeEmptyIndex(payload, jobId, documentId, knowledgeId);
                return "检测到完全重复文档，已跳过索引";
            }

            DocumentDedupService.SimilarDocResult similar = documentDedupService.checkMinHashSimilarity(knowledgeId, content, 0.85);
            if (similar != null && !documentId.equals(similar.documentId())) {
                document.setContentMd5(contentMd5);
                document.setQualityStatus(2);
                documentMapper.updateById(document);
                documentDedupService.saveDocumentHash(documentId, knowledgeId, contentMd5, content);
                log.warn("文档 [{}] 与 [{}] 近似重复, 跳过索引", documentId, similar.documentId());
                completeEmptyIndex(payload, jobId, documentId, knowledgeId);
                return "检测到近似重复文档，已跳过索引";
            }

            // 保存文档哈希
            document.setContentMd5(contentMd5);
            documentMapper.updateById(document);
            if (!ownHashExists) {
                documentDedupService.saveDocumentHash(documentId, knowledgeId, contentMd5, content);
            }

            // === RAG优化：质量过滤 ===
            if (content.trim().length() < 50) {
                markDocumentLowQuality(document, "文档内容过短");
                completeEmptyIndex(payload, jobId, documentId, knowledgeId);
                return "低质量文档已过滤：内容过短";
            }

            if (isTabularNoise(content)) {
                markDocumentLowQuality(document, "文档主要为结构化表格数据");
                completeEmptyIndex(payload, jobId, documentId, knowledgeId);
                return "低质量文档已过滤：结构化噪声";
            }

            // === RAG优化：新鲜度计算 ===
            if (document.getDocDate() != null) {
                double freshness = FreshnessConfig.calculateFreshness(document.getDocDate());
                document.setFreshnessScore(freshness);
                documentMapper.updateById(document);
            }

            // 根据切分模式选择分块策略
            List<String> chunkTexts;
            String chunkMode = config.getChunkMode() != null ? config.getChunkMode() : "default";

            switch (chunkMode) {
                case "smart":
                    chunkTexts = smartSplitContent(content, config.getSmartConfig());
                    relation.setSmartConfig(JSONUtil.toJsonStr(config.getSmartConfig()));
                    break;
                case "custom":
                    // 根据自定义切分方式选择分块策略
                    String splitType = config.getSplitType() != null ? config.getSplitType() : "length";
                    relation.setSplitType(splitType);

                    switch (splitType) {
                        case "page":
                            chunkTexts = splitByPage(content, config.getPagesPerChunk());
                            relation.setPagesPerChunk(config.getPagesPerChunk());
                            break;
                        case "heading":
                            chunkTexts = splitByHeading(content, config.getHeadingLevels());
                            relation.setHeadingLevels(JSONUtil.toJsonStr(config.getHeadingLevels()));
                            break;
                        case "regex":
                            chunkTexts = splitByRegex(content, config.getRegexPattern());
                            relation.setRegexPattern(config.getRegexPattern());
                            break;
                        case "length":
                        default:
                            int customChunkSize = config.getChunkSize() != null ? config.getChunkSize() : 500;
                            int customChunkOverlap = config.getChunkOverlap() != null ? config.getChunkOverlap() : 50;
                            chunkTexts = splitContent(content, customChunkSize, customChunkOverlap);
                            relation.setChunkSize(customChunkSize);
                            relation.setChunkOverlap(customChunkOverlap);
                            break;
                    }
                    break;
                case "default":
                default:
                    int defaultChunkSize = config.getChunkSize() != null ? config.getChunkSize() : 500;
                    int defaultChunkOverlap = config.getChunkOverlap() != null ? config.getChunkOverlap() : 50;
                    chunkTexts = splitContent(content, defaultChunkSize, defaultChunkOverlap);
                    relation.setChunkSize(defaultChunkSize);
                    relation.setChunkOverlap(defaultChunkOverlap);
                    break;
            }

            log.info("文档分块完成: documentId={}, chunkMode={}, chunkCount={}", documentId, chunkMode, chunkTexts.size());

            // 获取知识库配置中的嵌入模型
            String embeddingModelName = getEmbeddingModelName(knowledgeId, config);
            log.info("使用嵌入模型: documentId={}, knowledgeId={}, embeddingModel={}",
                    documentId, knowledgeId, embeddingModelName);

            Map<String, Object> documentMetadata = buildDocumentMetadata(document, chunkTexts.size());
            documentMetadata.put("index_revision", payload.revision());

            // 创建分块并直接存储到 ES
            int successCount = 0;
            for (int i = 0; i < chunkTexts.size(); i++) {
                context.checkpoint();
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(documentId);
                chunk.setKnowledgeId(knowledgeId);
                chunk.setChunkIndex(i);
                chunk.setChunkId((long) i);
                chunk.setContent(chunkTexts.get(i));
                chunk.setChunkSize(chunkTexts.get(i).length());
                chunk.setCreateTime(LocalDateTime.now());
                chunk.setUpdateTime(LocalDateTime.now());

                // 直接存储向量到 ES（不再存储到 MySQL）
                String vectorId = vectorStoreService.addVector(
                    chunk, knowledgeId, embeddingModelName, documentMetadata);
                if (vectorId == null) {
                    throw new IllegalStateException("向量写入失败: chunkIndex=" + i);
                }
                successCount++;
            }

            // Keep the previous complete generation available while writing; swap by cleanup only after success.
            vectorStoreService.deleteVectorsExceptRevision(documentId, knowledgeId, payload.revision());
            if (relationMapper.markIndexSucceeded(payload.relationId(), payload.revision(), jobId,
                    successCount, LocalDateTime.now()) != 1) {
                return "索引已写入，但当前版本已更新，等待新版本接管";
            }

            log.info("文档处理完成: documentId={}, knowledgeId={}, chunkCount={}", documentId, knowledgeId, successCount);

            // === RAG优化：异步生成摘要 ===
            try {
                KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
                if (kb != null && Boolean.TRUE.equals(kb.getEnableSummaryIndex())) {
                    summaryIndexService.indexDocumentSummary(documentId, knowledgeId,
                        document.getTitle(), content, getEmbeddingModelName(knowledgeId, config),
                        documentMetadata);
                }
            } catch (Exception e) {
                log.warn("摘要生成调度失败: documentId={}, error={}", documentId, e.getMessage());
            }

            return "索引完成，共写入 " + successCount + " 个分块";

        } catch (Exception e) {
            log.error("文档处理失败: documentId={}, knowledgeId={}, error={}", documentId, knowledgeId, e.getMessage(), e);
            relationMapper.markIndexFailed(payload.relationId(), payload.revision(), jobId,
                    capError(e.getMessage()), LocalDateTime.now());
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void completeEmptyIndex(DocumentIndexJobPayload payload, Long jobId,
                                    Long documentId, Long knowledgeId) {
        vectorStoreService.deleteVectorsByDocumentIdAndKnowledgeId(documentId, knowledgeId);
        if (relationMapper.markIndexSucceeded(payload.relationId(), payload.revision(), jobId,
                0, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("文档索引版本已变化");
        }
    }

    @Override
    public void abandonIndexJob(DocumentIndexJobPayload payload, Long jobId) {
        KnowledgeDocumentRelation current = relationMapper.selectById(payload.relationId());
        if (!isCurrentGeneration(current, payload, jobId)) return;
        DocumentIndexLock.LockHandle lock = documentIndexLock.acquire(payload.relationId());
        if (lock == null) {
            log.warn("无法立即清理未完成索引版本: relationId={}, revision={}",
                    payload.relationId(), payload.revision());
            return;
        }
        try {
            current = relationMapper.selectById(payload.relationId());
            if (isCurrentGeneration(current, payload, jobId)) {
                vectorStoreService.deleteVectorsByRevision(
                        payload.documentId(), payload.knowledgeId(), payload.revision());
            }
        } finally {
            documentIndexLock.release(lock);
        }
    }

    private Map<String, Object> buildDocumentMetadata(KnowledgeDocument document, int totalChunks) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfNotNull(metadata, "document_title", document.getTitle());
        putIfNotNull(metadata, "department", document.getDepartment());
        putIfNotNull(metadata, "doc_type", document.getDocType());
        putIfNotNull(metadata, "doc_date",
            document.getDocDate() == null ? null : document.getDocDate().toString());
        putIfNotNull(metadata, "freshness_score", document.getFreshnessScore());
        metadata.put("total_chunks", totalChunks);
        return metadata;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processDocumentForKnowledge(Long documentId, Long knowledgeId, Integer chunkSize, Integer chunkOverlap) {
        // 使用默认模式处理
        DocumentLinkConfig config = new DocumentLinkConfig();
        config.setChunkMode("default");
        config.setChunkSize(chunkSize);
        config.setChunkOverlap(chunkOverlap);
        processDocumentForKnowledge(documentId, knowledgeId, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reprocessDocument(Long documentId, Long knowledgeId) {
        log.info("重新处理文档: documentId={}, knowledgeId={}", documentId, knowledgeId);
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) {
            throw new BusinessException(404, "文档关联不存在");
        }
        enqueueIndex(documentId, knowledgeId, configFromRelation(relation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverPendingRelation(Long relationId) {
        KnowledgeDocumentRelation relation = relationMapper.selectPendingByIdForUpdate(relationId);
        if (relation == null) return;
        enqueueIndex(relation.getDocumentId(), relation.getKnowledgeId(), configFromRelation(relation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkDocumentFromKnowledge(Long documentId, Long knowledgeId) {
        log.info("取消文档关联: documentId={}, knowledgeId={}", documentId, knowledgeId);
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) return;
        DocumentIndexLock.LockHandle lock = documentIndexLock.acquire(relation.getId());
        if (lock == null) throw new BusinessException(409, "文档正在索引，请稍后重试取消关联");
        try {
            vectorStoreService.deleteVectorsByDocumentIdAndKnowledgeId(documentId, knowledgeId);
            relationMapper.deleteByKnowledgeIdAndDocumentId(knowledgeId, documentId);
            log.info("取消文档关联完成: documentId={}, knowledgeId={}", documentId, knowledgeId);
        } finally {
            documentIndexLock.release(lock);
        }
    }

    @Override
    public KnowledgeDocumentRelation getProcessStatus(Long documentId, Long knowledgeId) {
        return relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
    }

    private PlatformJob enqueueIndex(Long documentId, Long knowledgeId, DocumentLinkConfig requestedConfig) {
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) throw new BusinessException(404, "文档关联不存在");

        DocumentLinkConfig config = requestedConfig == null ? configFromRelation(relation) : requestedConfig;
        persistConfig(relation.getId(), config);
        if (relationMapper.prepareNextIndex(relation.getId()) != 1) {
            throw new IllegalStateException("准备文档索引任务失败");
        }
        KnowledgeDocumentRelation prepared = relationMapper.selectById(relation.getId());
        DocumentIndexJobPayload payload = new DocumentIndexJobPayload(prepared.getId(), documentId, knowledgeId,
                prepared.getIndexRevision(), configFromRelation(prepared));

        Long actor = UserContext.getCurrentUserId();
        if (actor == null) {
            KnowledgeDocument document = documentMapper.selectById(documentId);
            actor = document == null ? null : document.getUserId();
        }
        if (actor == null) {
            KnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeId);
            actor = base == null ? null : base.getUserId();
        }
        if (actor == null) throw new IllegalStateException("无法确定文档索引任务创建人");

        PlatformJob job = platformJobService.enqueue(new PlatformJobService.EnqueueCommand(
                "document-index:" + prepared.getId() + ":v" + prepared.getIndexRevision(),
                DocumentIndexJobHandler.TYPE, "KNOWLEDGE_DOCUMENT_RELATION", prepared.getId(),
                toJson(payload), 4, 10, actor));
        if (job == null || job.getId() == null || relationMapper.attachIndexJob(prepared.getId(),
                prepared.getIndexRevision(), job.getId()) != 1) {
            throw new IllegalStateException("绑定文档索引任务失败");
        }
        log.info("文档索引已入队: relationId={}, revision={}, jobId={}",
                prepared.getId(), prepared.getIndexRevision(), job.getId());
        return job;
    }

    private void persistConfig(Long relationId, DocumentLinkConfig config) {
        KnowledgeDocumentRelation values = new KnowledgeDocumentRelation();
        values.setChunkMode(valueOr(config.getChunkMode(), "default"));
        values.setSplitType(valueOr(config.getSplitType(), "length"));
        values.setChunkSize(config.getChunkSize());
        values.setChunkOverlap(config.getChunkOverlap());
        values.setPagesPerChunk(config.getPagesPerChunk());
        values.setHeadingLevels(config.getHeadingLevels() == null
                ? null : JSONUtil.toJsonStr(config.getHeadingLevels()));
        values.setRegexPattern(config.getRegexPattern());
        values.setEmbeddingModel(config.getEmbeddingModel());
        values.setSmartConfig(config.getSmartConfig() == null
                ? null : JSONUtil.toJsonStr(config.getSmartConfig()));
        if (relationMapper.updateIndexConfig(relationId, values) != 1) {
            throw new IllegalStateException("保存文档切分配置失败");
        }
    }

    private DocumentLinkConfig configFromRelation(KnowledgeDocumentRelation relation) {
        DocumentLinkConfig config = new DocumentLinkConfig();
        config.setChunkMode(valueOr(relation.getChunkMode(), "default"));
        config.setSplitType(valueOr(relation.getSplitType(), "length"));
        config.setChunkSize(relation.getChunkSize());
        config.setChunkOverlap(relation.getChunkOverlap());
        config.setPagesPerChunk(relation.getPagesPerChunk());
        config.setRegexPattern(relation.getRegexPattern());
        config.setEmbeddingModel(relation.getEmbeddingModel());
        if (relation.getHeadingLevels() != null && !relation.getHeadingLevels().isBlank()) {
            config.setHeadingLevels(JSONUtil.toList(relation.getHeadingLevels(), String.class));
        }
        if (relation.getSmartConfig() != null && !relation.getSmartConfig().isBlank()) {
            config.setSmartConfig(JSONUtil.toBean(relation.getSmartConfig(), DocumentLinkConfig.SmartChunkConfig.class));
        }
        return config;
    }

    private boolean isCurrentGeneration(KnowledgeDocumentRelation relation,
                                        DocumentIndexJobPayload payload, Long jobId) {
        return relation != null && payload.revision().equals(relation.getIndexRevision())
                && jobId.equals(relation.getJobId())
                && payload.documentId().equals(relation.getDocumentId())
                && payload.knowledgeId().equals(relation.getKnowledgeId());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化文档索引任务失败", e);
        }
    }

    private String capError(String value) {
        String safe = value == null || value.isBlank() ? "未知索引错误" : value;
        return safe.substring(0, Math.min(2000, safe.length()));
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 解析文档内容
     */
    private String parseDocumentContent(String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                throw new IllegalStateException("文档文件路径为空");
            }

            // 从 MinIO 获取文件流
            InputStream inputStream = minioService.getFile(filePath);
            if (inputStream == null) {
                throw new IllegalStateException("无法获取文档文件: " + filePath);
            }

            InputStreamResource resource = new InputStreamResource(inputStream);
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();

            StringBuilder content = new StringBuilder();
            for (Document doc : documents) {
                content.append(doc.getText()).append("\n");
            }

            return content.toString().trim();
        } catch (Exception e) {
            log.error("解析文档内容失败: {} - {}", filePath, e.getMessage());
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("解析文档内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能切分内容
     */
    private List<String> smartSplitContent(String content, DocumentLinkConfig.SmartChunkConfig config) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 设置默认值
        if (config == null) {
            config = new DocumentLinkConfig.SmartChunkConfig();
        }
        int maxChunkSize = config.getMaxChunkSize() != null ? config.getMaxChunkSize() : 1000;
        int minChunkSize = config.getMinChunkSize() != null ? config.getMinChunkSize() : 100;
        boolean respectParagraphs = config.getRespectParagraphs() != null ? config.getRespectParagraphs() : true;
        boolean respectHeaders = config.getRespectHeaders() != null ? config.getRespectHeaders() : true;

        List<String> chunks = new ArrayList<>();

        // 首先按照段落分割
        String[] paragraphs;
        if (respectParagraphs) {
            paragraphs = content.split("\\n\\s*\\n|\\n(?=[A-Z\\u4e00-\\u9fa5])");
        } else {
            paragraphs = new String[]{content};
        }

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            // 检查是否是标题行（简单判断：短行、以数字或符号开头）
            boolean isHeader = respectHeaders && isHeaderLine(paragraph);

            // 如果当前块加上新段落会超过最大值
            if (currentChunk.length() + paragraph.length() > maxChunkSize && currentChunk.length() >= minChunkSize) {
                // 保存当前块
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }

            // 添加段落到当前块
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);

            // 如果是标题且当前块已经够大，可以单独成块
            if (isHeader && currentChunk.length() >= minChunkSize) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
        }

        // 处理剩余内容
        if (currentChunk.length() > 0) {
            // 如果剩余内容太小，尝试合并到上一个块
            if (currentChunk.length() < minChunkSize && !chunks.isEmpty()) {
                int lastIndex = chunks.size() - 1;
                String lastChunk = chunks.get(lastIndex);
                if (lastChunk.length() + currentChunk.length() <= maxChunkSize) {
                    chunks.set(lastIndex, lastChunk + "\n\n" + currentChunk.toString().trim());
                } else {
                    chunks.add(currentChunk.toString().trim());
                }
            } else {
                chunks.add(currentChunk.toString().trim());
            }
        }

        return chunks;
    }

    /**
     * 判断是否是标题行
     */
    private boolean isHeaderLine(String line) {
        if (line == null || line.isEmpty()) return false;

        // 标题通常比较短
        if (line.length() > 50) return false;

        // 以数字开头（如 "1. xxx", "一、xxx"）
        if (line.matches("^[0-9一二三四五六七八九十]+[.、\\s].*")) {
            return true;
        }

        // 以#开头（Markdown标题）
        if (line.startsWith("#")) {
            return true;
        }

        // 全大写或全是中文字符
        if (line.equals(line.toUpperCase()) || line.matches("^[\\u4e00-\\u9fa5]+$")) {
            return true;
        }

        return false;
    }

    /**
     * 分割内容（固定大小切分，按字符）
     */
    private List<String> splitContent(String content, int chunkSize, int chunkOverlap) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认值
        if (chunkSize <= 0) chunkSize = 500;
        if (chunkOverlap < 0) chunkOverlap = 50;

        log.info("切分参数: chunkSize={}, chunkOverlap={}", chunkSize, chunkOverlap);

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int contentLength = content.length();

        while (start < contentLength) {
            int end = Math.min(start + chunkSize, contentLength);

            // 尝试在句子边界处切分
            int breakPoint = findBreakPoint(content, start, end);
            if (breakPoint > start) {
                end = breakPoint;
            }

            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 计算下一个起始位置（考虑重叠）
            start = end - chunkOverlap;
            if (start < 0) start = 0;

            // 避免无限循环
            if (start >= contentLength - chunkOverlap) {
                // 如果剩余内容不足一个重叠窗口，处理剩余部分
                if (end < contentLength) {
                    String lastChunk = content.substring(end).trim();
                    if (!lastChunk.isEmpty()) {
                        chunks.add(lastChunk);
                    }
                }
                break;
            }
        }

        // 打印前几个chunk的重叠情况
        if (chunks.size() > 1) {
            String first = chunks.get(0);
            String second = chunks.get(1);
            log.info("Chunk 0 最后 60 字符: [{}]", first.length() > 60 ? first.substring(first.length() - 60) : first);
            log.info("Chunk 1 前 60 字符: [{}]", second.length() > 60 ? second.substring(0, 60) : second);
        }

        return chunks;
    }

    /**
     * 查找最佳切分点（优先在句子边界切分）
     */
    private int findBreakPoint(String content, int start, int preferredEnd) {
        if (preferredEnd >= content.length()) {
            return content.length();
        }

        // 在 preferredEnd 附近查找句子结束符
        int searchStart = Math.max(start, preferredEnd - 100);
        int searchEnd = Math.min(content.length(), preferredEnd + 50);

        String searchArea = content.substring(searchStart, searchEnd);

        // 查找句子结束符的位置（句号、问号、感叹号、换行等）
        int bestBreak = -1;
        String breakChars = "。！？；,;?!\n";

        for (int i = 0; i < breakChars.length(); i++) {
            char c = breakChars.charAt(i);
            int idx = searchArea.lastIndexOf(c);
            if (idx > 0 && (bestBreak == -1 || idx > bestBreak)) {
                bestBreak = idx;
            }
        }

        if (bestBreak > 0) {
            return searchStart + bestBreak + 1;
        }

        // 如果没有找到句子边界，查找空格或逗号
        int spaceIdx = searchArea.lastIndexOf(' ');
        if (spaceIdx > 0) {
            return searchStart + spaceIdx + 1;
        }

        return preferredEnd;
    }

    /**
     * 按页切分内容
     * 模拟按页切分（实际PDF解析时会有页码信息，这里简化处理）
     */
    private List<String> splitByPage(String content, Integer pagesPerChunk) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认每块1页，假设每页约500字符
        int pageSize = 500;
        if (pagesPerChunk == null || pagesPerChunk < 1) {
            pagesPerChunk = 1;
        }

        // 按换页符或固定长度模拟分页
        String[] pages = content.split("\\f|\\n{3,}|(?=第[一二三四五六七八九十]+页)|(?=Page \\d+)");
        List<String> chunks = new ArrayList<>();

        StringBuilder currentChunk = new StringBuilder();
        int pageCount = 0;

        for (String page : pages) {
            page = page.trim();
            if (page.isEmpty()) continue;

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(page);
            pageCount++;

            if (pageCount >= pagesPerChunk) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                pageCount = 0;
            }
        }

        // 处理剩余内容
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 按标题切分内容
     */
    private List<String> splitByHeading(String content, List<String> headingLevels) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认切分级别
        if (headingLevels == null || headingLevels.isEmpty()) {
            headingLevels = List.of("h1", "h2");
        }

        // 构建标题匹配正则
        StringBuilder patternBuilder = new StringBuilder("(?=");
        boolean first = true;

        for (String level : headingLevels) {
            if (!first) patternBuilder.append("|");
            first = false;

            switch (level.toLowerCase()) {
                case "h1":
                    // Markdown H1 或 数字编号 或 中文编号
                    patternBuilder.append("# [^\\n]+|^[一二三四五六七八九十]+[、.．]");
                    break;
                case "h2":
                    patternBuilder.append("## [^\\n]+|^[0-9]+[.、．][^\\n]+");
                    break;
                case "h3":
                    patternBuilder.append("### [^\\n]+|^[0-9]+\\.[0-9]+[^\\n]*");
                    break;
                case "h4":
                    patternBuilder.append("#### [^\\n]+|^[0-9]+\\.[0-9]+\\.[0-9]+[^\\n]*");
                    break;
            }
        }
        patternBuilder.append(")");

        try {
            Pattern pattern = Pattern.compile(patternBuilder.toString(), Pattern.MULTILINE);
            String[] sections = pattern.split(content);

            List<String> chunks = new ArrayList<>();
            for (String section : sections) {
                section = section.trim();
                if (!section.isEmpty()) {
                    chunks.add(section);
                }
            }

            return chunks.isEmpty() ? List.of(content) : chunks;
        } catch (Exception e) {
            log.warn("标题切分失败，使用原文: {}", e.getMessage());
            return List.of(content);
        }
    }

    /**
     * 按正则切分内容
     */
    private List<String> splitByRegex(String content, String regexPattern) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认按双换行切分
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            regexPattern = "\\n\\s*\\n";
        }

        try {
            Pattern pattern = Pattern.compile(regexPattern);
            String[] parts = pattern.split(content);

            List<String> chunks = new ArrayList<>();
            for (String part : parts) {
                part = part.trim();
                if (!part.isEmpty()) {
                    chunks.add(part);
                }
            }

            return chunks.isEmpty() ? List.of(content) : chunks;
        } catch (Exception e) {
            log.warn("正则切分失败，使用原文: {}", e.getMessage());
            return List.of(content);
        }
    }

    /**
     * 获取嵌入模型名称
     * 优先级：文档关联配置 > 知识库配置 > 默认值
     */
    private String getEmbeddingModelName(Long knowledgeId, DocumentLinkConfig config) {
        // 1. 检查文档关联配置中的嵌入模型
        if (config != null && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isEmpty()) {
            return config.getEmbeddingModel();
        }

        // 2. 检查知识库配置中的嵌入模型
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeId);
            if (knowledgeBase != null && knowledgeBase.getEmbeddingModel() != null
                    && !knowledgeBase.getEmbeddingModel().isEmpty()) {
                return knowledgeBase.getEmbeddingModel();
            }
        } catch (Exception e) {
            log.warn("获取知识库嵌入模型配置失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }

        // 3. 返回默认值（null表示使用系统默认配置）
        return null;
    }

    // ==================== RAG 优化辅助方法 ====================

    private boolean isTabularNoise(String text) {
        if (text == null || text.isEmpty()) return false;
        int totalChars = text.replaceAll("\s+", "").length();
        if (totalChars == 0) return false;
        long numericAndTabular = text.chars()
            .filter(c -> Character.isDigit(c) || c == '|' || c == '	' || c == ',')
            .count();
        return (double) numericAndTabular / totalChars > 0.8;
    }

    private void markDocumentLowQuality(KnowledgeDocument doc, String reason) {
        doc.setQualityStatus(1);
        documentMapper.updateById(doc);
        log.warn("文档 [{}] 标记为低质: {}", doc.getId(), reason);
    }
}
