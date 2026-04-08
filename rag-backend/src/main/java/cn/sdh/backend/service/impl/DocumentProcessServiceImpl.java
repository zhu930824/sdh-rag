package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.mapper.KnowledgeChunkMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import cn.sdh.backend.service.DocumentProcessService;
import cn.sdh.backend.service.MinioService;
import cn.sdh.backend.service.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档处理服务实现
 * 采用独立存储方案：每个知识库独立存储自己的切分版本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessServiceImpl implements DocumentProcessService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentRelationMapper relationMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final MinioService minioService;
    private final VectorStoreService vectorStoreService;

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processDocumentForKnowledge(Long documentId, Long knowledgeId, Integer chunkSize, Integer chunkOverlap) {
        log.info("开始处理文档: documentId={}, knowledgeId={}, chunkSize={}, chunkOverlap={}", documentId, knowledgeId, chunkSize, chunkOverlap);

        // 获取关联记录
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) {
            log.error("关联记录不存在: documentId={}, knowledgeId={}", documentId, knowledgeId);
            return;
        }

        // 更新状态为处理中
        relation.setProcessStatus(1);
        relation.setProcessTime(LocalDateTime.now());
        relationMapper.updateById(relation);

        try {
            // 获取文档
            KnowledgeDocument document = documentMapper.selectById(documentId);
            if (document == null) {
                throw new RuntimeException("文档不存在: " + documentId);
            }

            // 解析文档内容
            String content = parseDocumentContent(document.getFilePath());
            if (content == null || content.trim().isEmpty()) {
                log.warn("文档内容为空: documentId={}", documentId);
                relation.setProcessStatus(2); // 成功，但无内容
                relation.setChunkCount(0);
                relation.setProcessTime(LocalDateTime.now());
                relationMapper.updateById(relation);
                return;
            }

            // 分块
            List<String> chunkTexts = splitContent(content, chunkSize, chunkOverlap);
            log.info("文档分块完成: documentId={}, chunkCount={}", documentId, chunkTexts.size());

            // 删除该文档在该知识库下的旧分块（如果有）
            chunkMapper.deleteByDocumentIdAndKnowledgeId(documentId, knowledgeId);

            // 创建分块并存储向量
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (int i = 0; i < chunkTexts.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(documentId);
                chunk.setKnowledgeId(knowledgeId);
                chunk.setChunkIndex(i);
                chunk.setContent(chunkTexts.get(i));
                chunk.setChunkSize(chunkTexts.get(i).length());
                chunk.setCreateTime(LocalDateTime.now());
                chunk.setUpdateTime(LocalDateTime.now());
                chunkMapper.insert(chunk);

                // 存储向量（向量中包含 knowledgeId 用于过滤）
                String vectorId = vectorStoreService.addVector(chunk, knowledgeId);
                if (vectorId != null) {
                    chunk.setVectorId(vectorId);
                    chunkMapper.updateById(chunk);
                }

                chunks.add(chunk);
            }

            // 更新关联记录
            relation.setProcessStatus(2); // 成功
            relation.setChunkCount(chunks.size());
            relation.setChunkSize(chunkSize);
            relation.setChunkOverlap(chunkOverlap);
            relation.setProcessTime(LocalDateTime.now());
            relationMapper.updateById(relation);

            log.info("文档处理完成: documentId={}, knowledgeId={}, chunkCount={}", documentId, knowledgeId, chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: documentId={}, knowledgeId={}, error={}", documentId, knowledgeId, e.getMessage(), e);

            // 更新状态为失败
            relation.setProcessStatus(3);
            relation.setProcessTime(LocalDateTime.now());
            relationMapper.updateById(relation);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reprocessDocument(Long documentId, Long knowledgeId) {
        log.info("重新处理文档: documentId={}, knowledgeId={}", documentId, knowledgeId);

        // 获取关联记录
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) {
            log.error("关联记录不存在: documentId={}, knowledgeId={}", documentId, knowledgeId);
            return;
        }

        // 删除旧分块和向量
        List<KnowledgeChunk> oldChunks = chunkMapper.selectByDocumentIdAndKnowledgeId(documentId, knowledgeId);
        for (KnowledgeChunk chunk : oldChunks) {
            if (chunk.getVectorId() != null) {
                vectorStoreService.deleteVector(chunk.getVectorId());
            }
        }
        chunkMapper.deleteByDocumentIdAndKnowledgeId(documentId, knowledgeId);

        // 重置状态
        relation.setProcessStatus(0);
        relation.setChunkCount(0);
        relationMapper.updateById(relation);

        // 重新处理（使用关联记录中保存的配置）
        processDocumentForKnowledge(
                documentId,
                knowledgeId,
                relation.getChunkSize() != null ? relation.getChunkSize() : 500,
                relation.getChunkOverlap() != null ? relation.getChunkOverlap() : 50
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkDocumentFromKnowledge(Long documentId, Long knowledgeId) {
        log.info("取消文档关联: documentId={}, knowledgeId={}", documentId, knowledgeId);

        // 获取该文档在该知识库下的所有分块
        List<KnowledgeChunk> chunks = chunkMapper.selectByDocumentIdAndKnowledgeId(documentId, knowledgeId);

        // 删除向量
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getVectorId() != null) {
                vectorStoreService.deleteVector(chunk.getVectorId());
            }
        }

        // 删除分块记录
        chunkMapper.deleteByDocumentIdAndKnowledgeId(documentId, knowledgeId);

        // 删除关联记录
        relationMapper.deleteByKnowledgeIdAndDocumentId(knowledgeId, documentId);

        log.info("取消文档关联完成: documentId={}, knowledgeId={}", documentId, knowledgeId);
    }

    @Override
    public KnowledgeDocumentRelation getProcessStatus(Long documentId, Long knowledgeId) {
        return relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
    }

    /**
     * 解析文档内容
     */
    private String parseDocumentContent(String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                return "";
            }

            // 从 MinIO 获取文件流
            InputStream inputStream = minioService.getFile(filePath);
            if (inputStream == null) {
                log.error("无法获取文件: {}", filePath);
                return "";
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
            return "";
        }
    }

    /**
     * 分割内容
     */
    private List<String> splitContent(String content, int chunkSize, int chunkOverlap) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认值
        if (chunkSize <= 0) chunkSize = 500;
        if (chunkOverlap < 0) chunkOverlap = 50;

        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,
                chunkOverlap,
                5,
                10000,
                true
        );

        Document doc = new Document(content);
        List<Document> documents = splitter.apply(List.of(doc));

        List<String> chunks = new ArrayList<>();
        for (Document d : documents) {
            String text = d.getText();
            if (text != null && !text.trim().isEmpty()) {
                chunks.add(text.trim());
            }
        }
        return chunks;
    }
}
