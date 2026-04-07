package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.service.EmbeddingService;
import cn.sdh.backend.service.VectorStoreService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 向量存储服务实现
 * 支持按知识库ID过滤的向量搜索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:sdh-rag-index}")
    private String indexName;

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId) {
        try {
            // 获取向量
            float[] embedding = embeddingService.getEmbedding(chunk.getContent());
            if (embedding == null || embedding.length == 0) {
                log.error("获取向量失败: chunkId={}", chunk.getId());
                return null;
            }

            // 构建文档
            String vectorId = UUID.randomUUID().toString();
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", chunk.getId());
            doc.put("document_id", chunk.getDocumentId());
            doc.put("knowledge_id", knowledgeId);
            doc.put("content", chunk.getContent());
            doc.put("embedding", toFloatList(embedding));
            doc.put("create_time", LocalDateTime.now().toString());

            // 索引文档
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .id(vectorId)
                    .document(doc));

            elasticsearchClient.index(request);

            log.info("添加向量成功: vectorId={}, chunkId={}, knowledgeId={}", vectorId, chunk.getId(), knowledgeId);
            return vectorId;
        } catch (Exception e) {
            log.error("添加向量失败: chunkId={}, error={}", chunk.getId(), e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<String> batchAddVectors(List<KnowledgeChunk> chunks, Long knowledgeId) {
        List<String> vectorIds = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            String vectorId = addVector(chunk, knowledgeId);
            if (vectorId != null) {
                vectorIds.add(vectorId);
            }
        }
        return vectorIds;
    }

    @Override
    public void addKnowledgeToVector(String vectorId, Long knowledgeId) {
        // 独立存储方案下，此方法不再需要
        log.warn("addKnowledgeToVector 方法在独立存储方案下已弃用");
    }

    @Override
    public void removeKnowledgeFromVector(String vectorId, Long knowledgeId) {
        // 独立存储方案下，直接删除向量
        deleteVector(vectorId);
    }

    @Override
    public void deleteVector(String vectorId) {
        try {
            elasticsearchClient.delete(d -> d
                    .index(indexName)
                    .id(vectorId));

            log.info("删除向量成功: vectorId={}", vectorId);
        } catch (Exception e) {
            log.error("删除向量失败: vectorId={}, error={}", vectorId, e.getMessage());
        }
    }

    @Override
    public void batchDeleteVectors(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        for (String vectorId : vectorIds) {
            deleteVector(vectorId);
        }
    }

    @Override
    public void deleteVectorsByKnowledgeId(Long knowledgeId) {
        try {
            // 查询所有包含该知识库ID的向量
            Query query = Query.of(q -> q
                    .term(t -> t
                            .field("knowledge_id")
                            .value(knowledgeId)));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .size(10000), Map.class);

            // 删除所有向量
            for (Hit<Map> hit : response.hits().hits()) {
                deleteVector(hit.id());
            }

            log.info("删除知识库向量完成: knowledgeId={}, count={}", knowledgeId, response.hits().hits().size());
        } catch (Exception e) {
            log.error("删除知识库向量失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
    }

    @Override
    public List<Document> similaritySearch(String query, Long knowledgeId, int topK) {
        try {
            // 获取查询向量
            float[] queryEmbedding = embeddingService.getEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return Collections.emptyList();
            }

            // 构建KNN查询
            Query knnQuery = Query.of(q -> q
                    .knn(k -> k
                            .field("embedding")
                            .queryVector(toFloatList(queryEmbedding))
                            .k(topK * 2)
                            .numCandidates(topK * 10)));

            // 构建知识库过滤
            Query filterQuery = Query.of(q -> q
                    .term(t -> t
                            .field("knowledge_id")
                            .value(knowledgeId)));

            // 组合查询
            Query combinedQuery = Query.of(q -> q
                    .bool(b -> b
                            .must(knnQuery)
                            .filter(filterQuery)));

            // 执行搜索
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(combinedQuery)
                    .size(topK), Map.class);

            // 转换结果
            List<Document> documents = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    Document doc = new Document((String) source.get("content"));
                    doc.getMetadata().put("id", hit.id());
                    doc.getMetadata().put("chunk_id", source.get("chunk_id"));
                    doc.getMetadata().put("document_id", source.get("document_id"));
                    doc.getMetadata().put("knowledge_id", source.get("knowledge_id"));
                    doc.getMetadata().put("score", hit.score());
                    documents.add(doc);
                }
            }

            log.info("相似度搜索完成: knowledgeId={}, results={}", knowledgeId, documents.size());
            return documents;
        } catch (Exception e) {
            log.error("相似度搜索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteVectorsByDocumentId(Long documentId) {
        try {
            // 查询该文档的所有向量
            Query query = Query.of(q -> q
                    .term(t -> t
                            .field("document_id")
                            .value(documentId)));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .size(10000), Map.class);

            // 删除所有向量
            for (Hit<Map> hit : response.hits().hits()) {
                deleteVector(hit.id());
            }

            log.info("删除文档向量完成: documentId={}, count={}", documentId, response.hits().hits().size());
        } catch (Exception e) {
            log.error("删除文档向量失败: documentId={}, error={}", documentId, e.getMessage());
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}
