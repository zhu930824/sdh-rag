package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.DocumentChunkMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.service.DocumentChunkService;
import cn.sdh.backend.service.DocumentService;
import cn.sdh.backend.service.EmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processDocument(Long documentId) {
        log.info("开始处理文档: {}", documentId);

        KnowledgeDocument document = getDocument(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在: " + documentId);
        }

        try {
            // 更新状态为处理中
            document.setStatus(0);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);

            // 解析文档内容
            String content = parseDocumentContent(document.getFilePath());
            document.setContent(content);
            documentMapper.updateById(document);

            // 分块处理
            List<String> chunks = splitContent(content);

            // 删除旧的分块
            chunkService.deleteByDocument(documentId);

            // 保存新的分块
            saveChunks(document, chunks);

            // 更新状态为成功
            document.setStatus(1);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);

            log.info("文档处理完成: {}", documentId);
        } catch (Exception e) {
            log.error("文档处理失败: {} - {}", documentId, e.getMessage(), e);
            document.setStatus(2);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void embedDocument(Long documentId) {
        log.info("开始嵌入文档向量: {}", documentId);

        KnowledgeDocument document = getDocument(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在: " + documentId);
        }

        List<DocumentChunk> chunks = chunkService.getChunksByDocument(documentId);

        for (DocumentChunk chunk : chunks) {
            try {
                float[] embedding = embeddingService.getEmbedding(chunk.getContent());
                if (embedding != null && embedding.length > 0) {
                    chunk.setEmbedding(objectMapper.writeValueAsString(embedding));
                    chunk.setUpdateTime(LocalDateTime.now());
                    chunkMapper.updateById(chunk);
                }
            } catch (Exception e) {
                log.error("嵌入向量失败: chunk {} - {}", chunk.getId(), e.getMessage());
            }
        }

        log.info("文档向量嵌入完成: {}", documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindexDocument(Long documentId) {
        log.info("重新索引文档: {}", documentId);

        // 先处理文档（重新解析分块）
        processDocument(documentId);

        // 然后嵌入向量
        embedDocument(documentId);

        log.info("文档重新索引完成: {}", documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncAllDocuments() {
        log.info("开始同步所有文档");

        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getStatus, 0);

        List<KnowledgeDocument> documents = documentMapper.selectList(wrapper);
        int count = 0;

        for (KnowledgeDocument document : documents) {
            try {
                processDocument(document.getId());
                count++;
            } catch (Exception e) {
                log.error("同步文档失败: {} - {}", document.getId(), e.getMessage());
            }
        }

        log.info("文档同步完成，共同步 {} 个文档", count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupOldDocuments(int days) {
        log.info("开始清理 {} 天前的旧文档", days);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);

        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(KnowledgeDocument::getCreateTime, cutoffDate);

        List<KnowledgeDocument> oldDocuments = documentMapper.selectList(wrapper);
        int count = 0;

        for (KnowledgeDocument document : oldDocuments) {
            try {
                // 先删除分块
                chunkService.deleteByDocument(document.getId());
                // 再删除文档
                documentMapper.deleteById(document.getId());
                count++;
            } catch (Exception e) {
                log.error("清理文档失败: {} - {}", document.getId(), e.getMessage());
            }
        }

        log.info("文档清理完成，共清理 {} 个文档", count);
        return count;
    }

    @Override
    public KnowledgeDocument getDocument(Long documentId) {
        return documentMapper.selectById(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocument(KnowledgeDocument document) {
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private String parseDocumentContent(String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                return "";
            }

            UrlResource resource = new UrlResource(filePath);
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();

            StringBuilder content = new StringBuilder();
            for (Document doc : documents) {
                content.append(doc.getText()).append("\n");
            }

            return content.toString().trim();
        } catch (Exception e) {
            log.error("解析文档内容失败: {} - {}", filePath, e.getMessage());
            return "";
        }
    }

    private List<String> splitContent(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        TokenTextSplitter splitter = new TokenTextSplitter(
            DEFAULT_CHUNK_SIZE,
            DEFAULT_CHUNK_OVERLAP,
            5,
            10000,
            true
        );

        // 将字符串转换为 Document 列表
        Document doc = new Document(content);
        List<Document> documents = splitter.apply(List.of(doc));

        // 提取分割后的文本
        List<String> chunks = new ArrayList<>();
        for (Document d : documents) {
            chunks.add(d.getText());
        }
        return chunks;
    }

    private void saveChunks(KnowledgeDocument document, List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setKnowledgeId(document.getKnowledgeId());
            chunk.setCategoryId(document.getCategoryId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setChunkSize(chunks.get(i).length());
            chunk.setCreateTime(LocalDateTime.now());
            chunkMapper.insert(chunk);
        }
    }
}