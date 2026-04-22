package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.service.ElasticsearchIndexService;
import cn.sdh.backend.service.EmbeddingService;
import cn.sdh.backend.service.VectorStoreService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量存储服务实现
 * 每个知识库使用独立的 ES 索引，索引名格式：sdh-rag-kb-{knowledgeId}
 * 支持根据知识库配置动态选择嵌入模型和维度
 */
@Slf4j
@Service
public class VectorStoreServiceImpl implements VectorStoreService {

    private static final int RRF_K = 10;

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchIndexService elasticsearchIndexService;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public VectorStoreServiceImpl(
            ElasticsearchClient elasticsearchClient,
            ElasticsearchIndexService elasticsearchIndexService,
            @Lazy EmbeddingService embeddingService,
            KnowledgeBaseMapper knowledgeBaseMapper) {
        this.elasticsearchClient = elasticsearchClient;
        this.elasticsearchIndexService = elasticsearchIndexService;
        this.embeddingService = embeddingService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId) {
        String embeddingModelName = getEmbeddingModelName(knowledgeId);
        return addVector(chunk, knowledgeId, embeddingModelName);
    }

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId, String embeddingModelName) {
        try {
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);
            elasticsearchIndexService.ensureIndexExists(knowledgeId, embeddingModelName);

            float[] embedding = embeddingService.getEmbeddingByModel(chunk.getContent(), embeddingModelName);
            if (embedding == null || embedding.length == 0) {
                log.error("获取向量失败: chunkId={}, embeddingModel={}", chunk.getId(), embeddingModelName);
                return null;
            }

            String vectorId = UUID.randomUUID().toString();
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", chunk.getId());
            doc.put("document_id", chunk.getDocumentId());
            doc.put("knowledge_id", knowledgeId);
            doc.put("chunk_index", chunk.getChunkIndex());
            doc.put("content", chunk.getContent());
            doc.put("embedding", toFloatList(embedding));
            doc.put("embedding_model", embeddingModelName);
            doc.put("create_time", LocalDateTime.now().toString());

            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .id(vectorId)
                    .document(doc));

            elasticsearchClient.index(request);

            log.info("添加向量成功: vectorId={}, chunkIndex={}, knowledgeId={}, embeddingModel={}, index={}",
                    vectorId, chunk.getChunkIndex(), knowledgeId, embeddingModelName, indexName);
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
        log.warn("addKnowledgeToVector 方法在独立存储方案下已弃用");
    }

    @Override
    public void removeKnowledgeFromVector(String vectorId, Long knowledgeId) {
        deleteVector(vectorId, knowledgeId);
    }

    @Override
    public void deleteVector(String vectorId) {
        log.warn("deleteVector(String vectorId) 已弃用，请使用 deleteVector(String vectorId, Long knowledgeId)");
    }

    public void deleteVector(String vectorId, Long knowledgeId) {
        try {
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);
            elasticsearchClient.delete(d -> d.index(indexName).id(vectorId));
            log.info("删除向量成功: vectorId={}, index={}", vectorId, indexName);
        } catch (Exception e) {
            log.error("删除向量失败: vectorId={}, knowledgeId={}, error={}", vectorId, knowledgeId, e.getMessage());
        }
    }

    @Override
    public void batchDeleteVectors(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        log.warn("batchDeleteVectors 需要知识库ID，当前方法已弃用");
    }

    public void batchDeleteVectors(List<String> vectorIds, Long knowledgeId) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        for (String vectorId : vectorIds) {
            deleteVector(vectorId, knowledgeId);
        }
    }

    @Override
    public void deleteVectorsByKnowledgeId(Long knowledgeId) {
        try {
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                log.info("知识库索引不存在，跳过删除: knowledgeId={}", knowledgeId);
                return;
            }

            Query query = Query.of(q -> q.matchAll(m -> m));

            elasticsearchClient.deleteByQuery(d -> d
                    .index(indexName)
                    .query(query));

            log.info("删除知识库向量完成: knowledgeId={}, index={}", knowledgeId, indexName);
        } catch (Exception e) {
            log.error("删除知识库向量失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
    }

    @Override
    public List<Document> similaritySearch(String query, Long knowledgeId, int topK) {
        String embeddingModelName = getEmbeddingModelName(knowledgeId);
        return similaritySearch(query, knowledgeId, topK, embeddingModelName, null);
    }

    @Override
    public List<Document> similaritySearch(String query, Long knowledgeId, int topK, String embeddingModelName) {
        return similaritySearch(query, knowledgeId, topK, embeddingModelName, null);
    }

    @Override
    public List<Document> similaritySearch(String query, Long knowledgeId, int topK,
                                            String embeddingModelName, Double minScore) {
        try {
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                log.info("知识库索引不存在: knowledgeId={}", knowledgeId);
                return Collections.emptyList();
            }

            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            float[] queryEmbedding = embeddingService.getEmbeddingByModel(query, embeddingModelName);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return Collections.emptyList();
            }

            List<Float> queryVector = toFloatList(queryEmbedding);

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .knn(knn -> knn
                            .field("embedding")
                            .queryVector(queryVector)
                            .k(topK)
                            .numCandidates(topK * 10))
                    .size(topK), Map.class);

            List<Document> documents = new ArrayList<>();
            int filtered = 0;
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    double score = hit.score() != null ? hit.score() : 0.0;

                    if (minScore != null && minScore > 0 && score < minScore) {
                        filtered++;
                        continue;
                    }

                    Document doc = new Document((String) source.get("content"));
                    doc.getMetadata().put("id", hit.id());
                    doc.getMetadata().put("chunk_id", source.get("chunk_id"));
                    doc.getMetadata().put("document_id", source.get("document_id"));
                    doc.getMetadata().put("knowledge_id", source.get("knowledge_id"));
                    doc.getMetadata().put("embedding_model", source.get("embedding_model"));
                    doc.getMetadata().put("score", score);
                    documents.add(doc);
                }
            }

            log.info("相似度搜索完成: knowledgeId={}, embeddingModel={}, results={}, filtered={}",
                    knowledgeId, embeddingModelName, documents.size(), filtered);
            return documents;
        } catch (Exception e) {
            log.error("相似度搜索失败: knowledgeId={}, embeddingModel={}, error={}",
                    knowledgeId, embeddingModelName, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteVectorsByDocumentId(Long documentId) {
        log.warn("deleteVectorsByDocumentId(Long documentId) 需要遍历所有知识库，建议使用 deleteVectorsByDocumentIdAndKnowledgeId");
    }

    @Override
    public void deleteVectorsByDocumentIdAndKnowledgeId(Long documentId, Long knowledgeId) {
        try {
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                log.info("知识库索引不存在，跳过删除: documentId={}, knowledgeId={}", documentId, knowledgeId);
                return;
            }

            Query query = Query.of(q -> q
                    .term(t -> t.field("document_id").value(FieldValue.of(documentId))));

            elasticsearchClient.deleteByQuery(d -> d
                    .index(indexName)
                    .query(query));

            log.info("删除文档向量完成: documentId={}, knowledgeId={}, index={}", documentId, knowledgeId, indexName);
        } catch (Exception e) {
            log.error("删除文档向量失败: documentId={}, knowledgeId={}, error={}",
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

    @Override
    public long countChunksByKnowledgeId(Long knowledgeId) {
        try {
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return 0;
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);
            CountResponse response = elasticsearchClient.count(c -> c.index(indexName));
            return response.count();
        } catch (Exception e) {
            log.error("统计分块数量失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            return 0;
        }
    }

    @Override
    public long countChunksByDocumentId(Long documentId, Long knowledgeId) {
        try {
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return 0;
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            Query query = Query.of(q -> q
                    .term(t -> t.field("document_id").value(FieldValue.of(documentId))));

            CountResponse response = elasticsearchClient.count(c -> c.index(indexName).query(query));
            return response.count();
        } catch (Exception e) {
            log.error("统计文档分块数量失败: documentId={}, error={}", documentId, e.getMessage());
            return 0;
        }
    }

    @Override
    public List<Document> getChunksByKnowledgeId(Long knowledgeId, int page, int size) {
        try {
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return Collections.emptyList();
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .from(page * size)
                    .size(size)
                    .sort(sort -> sort.field(f -> f.field("chunk_index")
                            .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc))),
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
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return Collections.emptyList();
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            Query query = Query.of(q -> q
                    .term(t -> t.field("document_id").value(FieldValue.of(documentId))));

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .from(page * size)
                    .size(size)
                    .sort(sort -> sort.field(f -> f.field("chunk_index")
                            .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc))),
                    Map.class);

            return convertHitsToDocuments(response.hits().hits());
        } catch (Exception e) {
            log.error("获取文档分块列表失败: documentId={}, error={}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Document getChunkById(String vectorId) {
        log.warn("getChunkById(String vectorId) 需要知识库ID，请使用其他方式获取分块");
        return null;
    }

    public Document getChunkById(String vectorId, Long knowledgeId) {
        try {
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return null;
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            GetResponse<Map> response = elasticsearchClient.get(g -> g
                    .index(indexName)
                    .id(vectorId), Map.class);

            if (response.found() && response.source() != null) {
                Map<String, Object> source = response.source();
                Document doc = new Document((String) source.get("content"));
                doc.getMetadata().put("id", response.id());
                doc.getMetadata().put("chunk_index", source.get("chunk_index"));
                doc.getMetadata().put("document_id", source.get("document_id"));
                doc.getMetadata().put("knowledge_id", source.get("knowledge_id"));
                doc.getMetadata().put("embedding_model", source.get("embedding_model"));
                doc.getMetadata().put("create_time", source.get("create_time"));
                return doc;
            }
        } catch (Exception e) {
            log.error("获取分块详情失败: vectorId={}, knowledgeId={}, error={}", vectorId, knowledgeId, e.getMessage());
        }
        return null;
    }

    @Override
    public long getIndexSizeByKnowledgeId(Long knowledgeId) {
        try {
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return 0;
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            var statsResponse = elasticsearchClient.indices().stats(i -> i.index(indexName));

            if (statsResponse.indices() != null && statsResponse.indices().containsKey(indexName)) {
                var indexStats = statsResponse.indices().get(indexName);
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
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return 0;
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .size(1)
                    .sort(sort -> sort.field(f -> f.field("create_time")
                            .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))),
                    Map.class);

            if (!response.hits().hits().isEmpty()) {
                Map<String, Object> source = response.hits().hits().get(0).source();
                if (source != null && source.get("create_time") != null) {
                    String createTimeStr = (String) source.get("create_time");
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
            if (!elasticsearchIndexService.indexExists(knowledgeId)) {
                return Collections.emptyList();
            }
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);

            Query keywordQuery = Query.of(q -> q
                    .match(m -> m.field("content").query(query)));

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
        return hybridSearch(query, knowledgeId, vectorTopK, keywordTopK,
                vectorWeight, keywordWeight, embeddingModelName, null);
    }

    @Override
    public List<Document> hybridSearch(String query, Long knowledgeId, int vectorTopK, int keywordTopK,
                                       double vectorWeight, double keywordWeight,
                                       String embeddingModelName, Double minScore) {
        try {
            List<Document> vectorResults = similaritySearch(query, knowledgeId, vectorTopK, embeddingModelName, minScore);

            List<Document> keywordResults = keywordSearch(query, knowledgeId, keywordTopK);

            Map<String, Document> docMap = new HashMap<>();
            Map<String, Double> scoreMap = new HashMap<>();

            for (int i = 0; i < vectorResults.size(); i++) {
                Document doc = vectorResults.get(i);
                String docId = (String) doc.getMetadata().get("id");
                if (docId == null) docId = String.valueOf(doc.hashCode());

                docMap.putIfAbsent(docId, doc);
                double rrfScore = vectorWeight / (RRF_K + i + 1);
                scoreMap.merge(docId, rrfScore, Double::sum);
            }

            for (int i = 0; i < keywordResults.size(); i++) {
                Document doc = keywordResults.get(i);
                String docId = (String) doc.getMetadata().get("id");
                if (docId == null) docId = String.valueOf(doc.hashCode());

                docMap.putIfAbsent(docId, doc);
                double rrfScore = keywordWeight / (RRF_K + i + 1);
                scoreMap.merge(docId, rrfScore, Double::sum);
            }

            List<Document> mergedResults = scoreMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(e -> {
                        Document doc = docMap.get(e.getKey());
                        doc.getMetadata().put("hybrid_score", e.getValue());
                        return doc;
                    })
                    .collect(Collectors.toList());

            log.info("混合检索完成: knowledgeId={}, vectorResults={}, keywordResults={}, mergedResults={}",
                    knowledgeId, vectorResults.size(), keywordResults.size(), mergedResults.size());

            return mergedResults;
        } catch (Exception e) {
            log.error("混合检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<Document> convertHitsToDocuments(List<Hit<Map>> hits) {
        List<Document> documents = new ArrayList<>();
        for (Hit<Map> hit : hits) {
            Map<String, Object> source = hit.source();
            if (source != null) {
                Document doc = new Document((String) source.get("content"));
                doc.getMetadata().put("id", hit.id());
                doc.getMetadata().put("chunk_id", source.get("chunk_id"));
                doc.getMetadata().put("chunk_index", source.get("chunk_index"));
                doc.getMetadata().put("document_id", source.get("document_id"));
                doc.getMetadata().put("knowledge_id", source.get("knowledge_id"));
                doc.getMetadata().put("embedding_model", source.get("embedding_model"));
                doc.getMetadata().put("create_time", source.get("create_time"));
                doc.getMetadata().put("score", hit.score());
                documents.add(doc);
            }
        }
        return documents;
    }
}
