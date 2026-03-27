package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.service.BatchProcessService;
import cn.sdh.backend.service.DocumentService;
import cn.sdh.backend.service.EmbeddingService;
import cn.sdh.backend.service.DocumentChunkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessServiceImpl implements BatchProcessService {

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentService documentService;
    private final EmbeddingService embeddingService;
    private final DocumentChunkService chunkService;
    private final ObjectMapper objectMapper;

    private final Map<Long, Map<String, Object>> batchStatusMap = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchUpload(Long knowledgeId, List<String> fileUrls, String category) {
        log.info("批量上传文档: knowledgeId={}, count={}", knowledgeId, fileUrls.size());

        List<Long> uploadedIds = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (String fileUrl : fileUrls) {
            try {
                KnowledgeDocument document = new KnowledgeDocument();
                document.setKnowledgeId(knowledgeId);
                document.setTitle(extractFileName(fileUrl));
                document.setFilePath(fileUrl);
                document.setCategory(category);
                document.setStatus(0);
                document.setCreateTime(LocalDateTime.now());
                documentMapper.insert(document);

                uploadedIds.add(document.getId());
                successCount++;

                documentService.processDocument(document.getId());
            } catch (Exception e) {
                log.error("上传文档失败: {} - {}", fileUrl, e.getMessage());
                failedFiles.add(fileUrl);
                failCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("uploadedIds", uploadedIds);
        result.put("failedFiles", failedFiles);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchDelete(List<Long> documentIds) {
        log.info("批量删除文档: count={}", documentIds.size());

        int successCount = 0;
        int failCount = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Long docId : documentIds) {
            try {
                chunkService.deleteByDocument(docId);
                documentMapper.deleteById(docId);
                successCount++;
            } catch (Exception e) {
                log.error("删除文档失败: {} - {}", docId, e.getMessage());
                failedIds.add(docId);
                failCount++;
            }
        }

        return Map.of(
            "successCount", successCount,
            "failCount", failCount,
            "failedIds", failedIds
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchMove(List<Long> documentIds, Long targetKnowledgeId) {
        log.info("批量移动文档: count={}, targetKnowledgeId={}", documentIds.size(), targetKnowledgeId);

        int successCount = 0;
        for (Long docId : documentIds) {
            KnowledgeDocument document = documentMapper.selectById(docId);
            if (document != null) {
                document.setKnowledgeId(targetKnowledgeId);
                document.setUpdateTime(LocalDateTime.now());
                documentMapper.updateById(document);
                successCount++;
            }
        }

        return Map.of(
            "successCount", successCount,
            "failCount", documentIds.size() - successCount
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchUpdateCategory(List<Long> documentIds, Long categoryId) {
        log.info("批量更新分类: count={}, categoryId={}", documentIds.size(), categoryId);

        int successCount = 0;
        for (Long docId : documentIds) {
            KnowledgeDocument document = documentMapper.selectById(docId);
            if (document != null) {
                document.setCategoryId(categoryId);
                document.setUpdateTime(LocalDateTime.now());
                documentMapper.updateById(document);
                successCount++;
            }
        }

        return Map.of(
            "successCount", successCount,
            "failCount", documentIds.size() - successCount
        );
    }

    @Override
    public Map<String, Object> batchProcess(List<Long> documentIds, String processType) {
        log.info("批量处理文档: count={}, type={}", documentIds.size(), processType);

        Long batchId = System.currentTimeMillis();

        Map<String, Object> status = new HashMap<>();
        status.put("batchId", batchId);
        status.put("total", documentIds.size());
        status.put("processed", 0);
        status.put("success", 0);
        status.put("failed", 0);
        status.put("status", "running");
        status.put("startTime", LocalDateTime.now());
        batchStatusMap.put(batchId, status);

        processInBackground(batchId, documentIds, processType);

        return Map.of("batchId", batchId, "status", "started");
    }

    @Override
    @Async
    public void processInBackground(Long batchId, List<Long> documentIds, String processType) {
        Map<String, Object> status = batchStatusMap.get(batchId);
        if (status == null) return;

        int processed = 0;
        int success = 0;
        int failed = 0;

        for (Long docId : documentIds) {
            try {
                switch (processType) {
                    case "embed":
                        documentService.embedDocument(docId);
                        break;
                    case "reindex":
                        documentService.reindexDocument(docId);
                        break;
                    case "reparse":
                        documentService.processDocument(docId);
                        break;
                    default:
                        log.warn("未知处理类型: {}", processType);
                }
                success++;
            } catch (Exception e) {
                log.error("处理文档失败: {} - {}", docId, e.getMessage());
                failed++;
            }
            processed++;

            status.put("processed", processed);
            status.put("success", success);
            status.put("failed", failed);
        }

        status.put("status", "completed");
        status.put("endTime", LocalDateTime.now());
    }

    @Override
    public Map<String, Object> batchEmbed(List<Long> documentIds) {
        return batchProcess(documentIds, "embed");
    }

    @Override
    public Map<String, Object> batchExport(List<Long> documentIds, String format) {
        log.info("批量导出文档: count={}, format={}", documentIds.size(), format);

        List<Map<String, Object>> exported = new ArrayList<>();

        for (Long docId : documentIds) {
            KnowledgeDocument document = documentMapper.selectById(docId);
            if (document != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", document.getId());
                item.put("title", document.getTitle());
                item.put("content", document.getContent());
                item.put("createTime", document.getCreateTime());
                exported.add(item);
            }
        }

        return Map.of(
            "documents", exported,
            "count", exported.size(),
            "format", format
        );
    }

    @Override
    public Map<String, Object> getBatchStatus(Long batchId) {
        return batchStatusMap.getOrDefault(batchId, Map.of("status", "not_found"));
    }

    @Override
    public void cancelBatch(Long batchId) {
        Map<String, Object> status = batchStatusMap.get(batchId);
        if (status != null && "running".equals(status.get("status"))) {
            status.put("status", "cancelled");
            status.put("endTime", LocalDateTime.now());
        }
    }

    private String extractFileName(String url) {
        if (url == null) return "unknown";
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
    }
}
