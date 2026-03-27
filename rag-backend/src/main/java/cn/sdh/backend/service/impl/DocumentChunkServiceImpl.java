package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentChunk;
import cn.sdh.backend.mapper.DocumentChunkMapper;
import cn.sdh.backend.service.DocumentChunkService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunk> implements DocumentChunkService {

    private final DocumentChunkMapper chunkMapper;

    @Override
    public int countChunksByKnowledge(Long knowledgeId) {
        return chunkMapper.countByKnowledgeId(knowledgeId);
    }

    @Override
    public int countEmbeddingsByKnowledge(Long knowledgeId) {
        return chunkMapper.countEmbeddingByKnowledgeId(knowledgeId);
    }

    @Override
    public int countEmptyChunksByKnowledge(Long knowledgeId) {
        return chunkMapper.countEmptyByKnowledgeId(knowledgeId);
    }

    @Override
    public long getIndexSizeByKnowledge(Long knowledgeId) {
        return chunkMapper.selectIndexSizeByKnowledge(knowledgeId);
    }

    @Override
    public long getLastUpdateTimeByKnowledge(Long knowledgeId) {
        Long timestamp = chunkMapper.selectLastUpdateTimeByKnowledge(knowledgeId);
        return timestamp != null ? timestamp : 0L;
    }

    @Override
    public List<DocumentChunk> getChunksByDocument(Long documentId) {
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getDocumentId, documentId)
               .orderByAsc(DocumentChunk::getChunkIndex);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocument(Long documentId) {
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getDocumentId, documentId);
        remove(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createChunks(Long documentId, List<String> contents, int chunkSize, int overlap) {
        for (int i = 0; i < contents.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setContent(contents.get(i));
            chunk.setChunkIndex(i);
            chunk.setChunkSize(chunkSize);
            chunk.setCreateTime(LocalDateTime.now());
            save(chunk);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmbedding(Long chunkId, String embedding) {
        DocumentChunk chunk = getById(chunkId);
        if (chunk != null) {
            chunk.setEmbedding(embedding);
            chunk.setUpdateTime(LocalDateTime.now());
            updateById(chunk);
        }
    }
}
