package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
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
import java.util.stream.Collectors;

/**
 * 向量存储服务实现
 * 支持按知识库ID过滤的向量搜索
 * 支持根据知识库配置动态选择嵌入模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:sdh-rag-index}")
    private String indexName;

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId) {
        // 从知识库获取嵌入模型配置
        String embeddingModelName = getEmbeddingModelName(knowledgeId);
        return addVector(chunk, knowledgeId, embeddingModelName);
    }

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId, String embeddingModelName) {
        try {
            // 获取向量（使用指定的嵌入模型）
            float[] embedding = embeddingService.getEmbeddingByModel(chunk.getContent(), embeddingModelName);
            if (embedding == null || embedding.length == 0) {
                log.error("获取向量失败: chunkId={}, embeddingModel={}", chunk.getId(), embeddingModelName);
                return null;
            }

            // 构建文档
            String vectorId = UUID.randomUUID().toString();
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", chunk.getId());
            doc.put("document_id", chunk.getDocumentId());
            doc.put("knowledge_id", knowledgeId);
            doc.put("chunk_index", chunk.getChunkIndex()); // 分块索引，用于排序
            doc.put("content", chunk.getContent());
            doc.put("embedding", toFloatList(embedding));
            doc.put("embedding_model", embeddingModelName); // 记录使用的嵌入模型
            doc.put("create_time", LocalDateTime.now().toString());

            // 索引文档
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .id(vectorId)
                    .document(doc));

            elasticsearchClient.index(request);

            log.info("添加向量成功: vectorId={}, chunkId={}, knowledgeId={}, embeddingModel={}",
                    vectorId, chunk.getId(), knowledgeId, embeddingModelName);
            return vectorId;
        } catch (Exception e) {
            log.error("添加向量失败: chunkId={}, error={}", chunk.getId(), e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<String> batchAddVectors(List<KnowledgeChunk> chunks, Long knowledgeId) {
        String embeddingModelName = getEmbeddingModelName(knowledgeId);
        return batchAddVectors(chunks, knowledgeId, embeddingModelName);
    }

    @Override
    public List<String> batchAddVectors(List<KnowledgeChunk> chunks, Long knowledgeId, String embeddingModelName) {
        List<String> vectorIds = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            String vectorId = addVector(chunk, knowledgeId, embeddingModelName);
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
                            .value(knowledgeId.toString())));

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
        // 使用知识库配置的嵌入模型
        String embeddingModelName = getEmbeddingModelName(knowledgeId);
        return similaritySearch(query, knowledgeId, topK, embeddingModelName);
    }

    @Override
    public List<Document> similaritySearch(String query, Long knowledgeId, int topK, String embeddingModelName) {
        try {
            // 获取查询向量（使用指定的嵌入模型）
            float[] queryEmbedding = embeddingService.getEmbeddingByModel(query, embeddingModelName);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return Collections.emptyList();
            }

            // 使用 knn 搜索 (Elasticsearch 8.x 的推荐方式)
            List<Float> queryVector = toFloatList(queryEmbedding);

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .knn(knn -> knn
                            .field("embedding")
                            .queryVector(queryVector)
                            .k(topK)
                            .numCandidates(topK * 10)
                            .filter(f -> f.term(t -> t
                                    .field("knowledge_id")
                                    .value(knowledgeId.toString()))))
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
                    doc.getMetadata().put("embedding_model", source.get("embedding_model"));
                    doc.getMetadata().put("score", hit.score());
                    documents.add(doc);
                }
            }

            log.info("相似度搜索完成: knowledgeId={}, embeddingModel={}, results={}",
                    knowledgeId, embeddingModelName, documents.size());
            return documents;
        } catch (Exception e) {
            log.error("相似度搜索失败: knowledgeId={}, embeddingModel={}, error={}",
                    knowledgeId, embeddingModelName, e.getMessage(), e);
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
                            .value(documentId.toString())));

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

    @Override
    public void deleteVectorsByDocumentIdAndKnowledgeId(Long documentId, Long knowledgeId) {
        try {
            // 构建查询条件：同时匹配 document_id 和 knowledge_id
            Query query = Query.of(q -> q
                    .bool(b -> b
                            .must(m -> m.term(t -> t.field("document_id").value(documentId.toString())))
                            .must(m -> m.term(t -> t.field("knowledge_id").value(knowledgeId.toString())))));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .size(10000), Map.class);

            // 删除所有向量
            for (Hit<Map> hit : response.hits().hits()) {
                deleteVector(hit.id());
            }

            log.info("删除文档知识库向量完成: documentId={}, knowledgeId={}, count={}",
                    documentId, knowledgeId, response.hits().hits().size());
        } catch (Exception e) {
            log.error("删除文档知识库向量失败: documentId={}, knowledgeId={}, error={}",
                    documentId, knowledgeId, e.getMessage());
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    /**
     * 根据知识库ID获取嵌入模型名称
     */
    private String getEmbeddingModelName(Long knowledgeId) {
        if (knowledgeId == null) {
            return null;
        }
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeId);
            if (knowledgeBase != null && knowledgeBase.getEmbeddingModel() != null) {
                return knowledgeBase.getEmbeddingModel();
            }
        } catch (Exception e) {
            log.warn("获取知识库嵌入模型失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
        return null;
    }

    // ==================== 分块查询方法实现 ====================

    @Override
    public long countChunksByKnowledgeId(Long knowledgeId) {
        try {
            Query query = Query.of(q -> q
                    .term(t -> t
                            .field("knowledge_id")
                            .value(knowledgeId.toString())));

            CountResponse response = elasticsearchClient.count(c -> c
                    .index(indexName)
                    .query(query));

            return response.count();
        } catch (Exception e) {
            log.error("统计分块数量失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            return 0;
        }
    }

    @Override
    public long countChunksByDocumentId(Long documentId, Long knowledgeId) {
        try {
            Query query = Query.of(q -> q
                    .bool(b -> {
                        b.must(m -> m.term(t -> t.field("document_id").value(documentId.toString())));
                        if (knowledgeId != null) {
                            b.must(m -> m.term(t -> t.field("knowledge_id").value(knowledgeId.toString())));
                        }
                        return b;
                    }));

            CountResponse response = elasticsearchClient.count(c -> c
                    .index(indexName)
                    .query(query));

            return response.count();
        } catch (Exception e) {
            log.error("统计文档分块数量失败: documentId={}, error={}", documentId, e.getMessage());
            return 0;
        }
    }

    @Override
    public List<Document> getChunksByKnowledgeId(Long knowledgeId, int page, int size) {
        try {
            Query query = Query.of(q -> q
                    .term(t -> t
                            .field("knowledge_id")
                            .value(knowledgeId.toString())));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .from(page * size)
                    .size(size)
                    .sort(sort -> sort.field(f -> f.field("chunk_id").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc))),
                    Map.class);

            return convertHitsToDocuments(response.hits().hits());
        } catch (Exception e) {
            log.error("获取知识库分块列表失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Document> getChunksByDocumentId(Long documentId, Long knowledgeId, int page, int size) {
        try {
            Query query = Query.of(q -> q
                    .bool(b -> {
                        b.must(m -> m.term(t -> t.field("document_id").value(documentId.toString())));
                        if (knowledgeId != null) {
                            b.must(m -> m.term(t -> t.field("knowledge_id").value(knowledgeId.toString())));
                        }
                        return b;
                    }));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .from(page * size)
                    .size(size),
                    Map.class);

            return convertHitsToDocuments(response.hits().hits());
        } catch (Exception e) {
            log.error("获取文档分块列表失败: documentId={}, error={}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Document getChunkById(String vectorId) {
        try {
            GetResponse<Map> response = elasticsearchClient.get(g -> g
                    .index(indexName)
                    .id(vectorId),
                    Map.class);

            if (response.found() && response.source() != null) {
                Map<String, Object> source = response.source();
                Document doc = new Document((String) source.get("content"));
                doc.getMetadata().put("id", response.id());
                doc.getMetadata().put("chunk_id", source.get("chunk_id"));
                doc.getMetadata().put("document_id", source.get("document_id"));
                doc.getMetadata().put("knowledge_id", source.get("knowledge_id"));
                doc.getMetadata().put("embedding_model", source.get("embedding_model"));
                doc.getMetadata().put("create_time", source.get("create_time"));
                return doc;
            }
        } catch (Exception e) {
            log.error("获取分块详情失败: vectorId={}, error={}", vectorId, e.getMessage());
        }
        return null;
    }

    @Override
    public long getIndexSizeByKnowledgeId(Long knowledgeId) {
        try {
            // 获取索引统计信息
            var statsResponse = elasticsearchClient.indices().stats(i -> i.index(indexName));

            if (statsResponse.indices() != null && statsResponse.indices().containsKey(indexName)) {
                var indexStats = statsResponse.indices().get(indexName);
                // 返回索引的存储大小（字节）
                return indexStats.primaries().store().sizeInBytes();
            }
        } catch (Exception e) {
            log.warn("获取索引大小失败: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public long getLastUpdateTimeByKnowledgeId(Long knowledgeId) {
        try {
            Query query = Query.of(q -> q
                    .term(t -> t
                            .field("knowledge_id")
                            .value(knowledgeId.toString())));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .size(1)
                    .sort(sort -> sort.field(f -> f.field("create_time").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))),
                    Map.class);

            if (!response.hits().hits().isEmpty()) {
                Map<String, Object> source = response.hits().hits().get(0).source();
                if (source != null && source.get("create_time") != null) {
                    String createTimeStr = (String) source.get("create_time");
                    // 解析 ISO 时间格式
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(createTimeStr,
                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
            }
        } catch (Exception e) {
            log.warn("获取最后更新时间失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
        return 0;
    }

    @Override
    public List<Document> keywordSearch(String query, Long knowledgeId, int topK) {
        try {
            // 使用 ES 的 match 查询进行关键字搜索（BM25）
            Query keywordQuery = Query.of(q -> q
                    .bool(b -> b
                            .must(m -> m.match(mt -> mt
                                    .field("content")
                                    .query(query)))
                            .filter(f -> f.term(t -> t
                                    .field("knowledge_id")
                                    .value(knowledgeId.toString())))));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(keywordQuery)
                    .size(topK)
                    .sort(sort -> sort.score(sc -> sc.order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))),
                    Map.class);

            List<Document> documents = convertHitsToDocuments(response.hits().hits());
            log.info("关键字搜索完成: knowledgeId={}, query={}, topK={}, results={}",
                    knowledgeId, query, topK, documents.size());
            return documents;
        } catch (Exception e) {
            log.error("关键字搜索失败: knowledgeId={}, query={}, error={}", knowledgeId, query, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Document> hybridSearch(String query, Long knowledgeId, int vectorTopK, int keywordTopK,
                                       double vectorWeight, double keywordWeight, String embeddingModelName) {
        try {
            // 1. 向量检索
            List<Document> vectorResults = similaritySearch(query, knowledgeId, vectorTopK, embeddingModelName);

            // 2. 关键字检索
            List<Document> keywordResults = keywordSearch(query, knowledgeId, keywordTopK);

            // 3. 合并结果（RRF - Reciprocal Rank Fusion）
            Map<String, Document> docMap = new HashMap<>();
            Map<String, Double> scoreMap = new HashMap<>();

            // 向量结果打分
            for (int i = 0; i < vectorResults.size(); i++) {
                Document doc = vectorResults.get(i);
                String docId = (String) doc.getMetadata().get("id");
                if (docId == null) docId = String.valueOf(doc.hashCode());

                docMap.putIfAbsent(docId, doc);
                double rrfScore = vectorWeight / (60 + i + 1); // RRF 公式，k=60
                scoreMap.merge(docId, rrfScore, Double::sum);
            }

            // 关键字结果打分
            for (int i = 0; i < keywordResults.size(); i++) {
                Document doc = keywordResults.get(i);
                String docId = (String) doc.getMetadata().get("id");
                if (docId == null) docId = String.valueOf(doc.hashCode());

                docMap.putIfAbsent(docId, doc);
                double rrfScore = keywordWeight / (60 + i + 1);
                scoreMap.merge(docId, rrfScore, Double::sum);
            }

            // 按分数排序
            List<Document> mergedResults = scoreMap.entrySet().stream()
                    .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                    .map(e -> {
                        Document doc = docMap.get(e.getKey());
                        doc.getMetadata().put("hybrid_score", e.getValue());
                        return doc;
                    })
                    .collect(java.util.stream.Collectors.toList());

            log.info("混合检索完成: knowledgeId={}, vectorResults={}, keywordResults={}, mergedResults={}",
                    knowledgeId, vectorResults.size(), keywordResults.size(), mergedResults.size());

            return mergedResults;
        } catch (Exception e) {
            log.error("混合检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将 ES 搜索结果转换为 Document 列表
     */
    private List<Document> convertHitsToDocuments(List<Hit<Map>> hits) {
        List<Document> documents = new ArrayList<>();
        for (Hit<Map> hit : hits) {
            Map<String, Object> source = hit.source();
            if (source != null) {
                Document doc = new Document((String) source.get("content"));
                doc.getMetadata().put("id", hit.id());
                doc.getMetadata().put("chunk_id", source.get("chunk_id"));
                doc.getMetadata().put("document_id", source.get("document_id"));
                doc.getMetadata().put("knowledge_id", source.get("knowledge_id"));
                doc.getMetadata().put("chunk_index", source.get("chunk_index"));
                doc.getMetadata().put("embedding_model", source.get("embedding_model"));
                doc.getMetadata().put("create_time", source.get("create_time"));
                doc.getMetadata().put("score", hit.score());
                documents.add(doc);
            }
        }
        return documents;
    }
}
