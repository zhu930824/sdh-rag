package cn.sdh.backend.service;

import cn.sdh.backend.entity.DocumentChunk;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface DocumentChunkService extends IService<DocumentChunk> {

    int countChunksByKnowledge(Long knowledgeId);

    int countEmbeddingsByKnowledge(Long knowledgeId);

    int countEmptyChunksByKnowledge(Long knowledgeId);

    long getIndexSizeByKnowledge(Long knowledgeId);

    long getLastUpdateTimeByKnowledge(Long knowledgeId);

    List<DocumentChunk> getChunksByDocument(Long documentId);

    void deleteByDocument(Long documentId);

    void createChunks(Long documentId, List<String> contents, int chunkSize, int overlap);

    void updateEmbedding(Long chunkId, String embedding);
}
