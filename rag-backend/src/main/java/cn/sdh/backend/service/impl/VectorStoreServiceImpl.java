package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.service.ElasticsearchIndexService;
import cn.sdh.backend.service.EmbeddingService;
import cn.sdh.backend.service.VectorStoreService;
import cn.sdh.backend.service.PublishedIndexService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
    private final PublishedIndexService publications;

    public VectorStoreServiceImpl(
            ElasticsearchClient elasticsearchClient,
            ElasticsearchIndexService elasticsearchIndexService,
            @Lazy EmbeddingService embeddingService,
            KnowledgeBaseMapper knowledgeBaseMapper, PublishedIndexService publications) {
        this.elasticsearchClient = elasticsearchClient;
        this.elasticsearchIndexService = elasticsearchIndexService;
        this.embeddingService = embeddingService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.publications = publications;
    }

    @Override public void refreshIndex(Long knowledgeId) {
        try {
            var response = elasticsearchClient.indices().refresh(r -> r.index(elasticsearchIndexService.getIndexName(knowledgeId)));
            if (response.shards().failed().intValue() > 0) throw new IllegalStateException("索引刷新存在失败分片");
        } catch (Exception failure) { throw new IllegalStateException("索引刷新失败，保留原发布代际", failure); }
    }

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId) {
        String embeddingModelName = getEmbeddingModelName(knowledgeId);
        return addVector(chunk, knowledgeId, embeddingModelName);
    }

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId, String embeddingModelName) {
        return addVector(chunk, knowledgeId, embeddingModelName, Map.of());
    }

    @Override
    public String addVector(KnowledgeChunk chunk, Long knowledgeId, String embeddingModelName,
                            Map<String, Object> documentMetadata) {
        try {
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);
            elasticsearchIndexService.ensureIndexExists(knowledgeId, embeddingModelName);

            float[] embedding = embeddingService.getEmbeddingByModel(chunk.getContent(), embeddingModelName);
            if (embedding == null || embedding.length == 0) {
                log.error("获取向量失败: chunkId={}, embeddingModel={}", chunk.getId(), embeddingModelName);
                return null;
            }

            Object revision = documentMetadata == null ? null : documentMetadata.getOrDefault("index_generation", documentMetadata.get("index_revision"));
            String vectorId = deterministicVectorId(
                    knowledgeId, chunk.getDocumentId(), chunk.getChunkIndex(), revision);
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", chunk.getChunkId());
            doc.put("document_id", chunk.getDocumentId());
            doc.put("knowledge_id", knowledgeId);
            doc.put("chunk_index", chunk.getChunkIndex());
            doc.put("content", chunk.getContent());
            doc.put("embedding", toFloatList(embedding));
            doc.put("embedding_model", embeddingModelName);
            doc.put("create_time", LocalDateTime.now().toString());
            if (documentMetadata != null) {
                documentMetadata.forEach((key, value) -> {
                    if (value != null) {
                        doc.put(key, value);
                    }
                });
            }

            // Bind the checksum to the text actually written; caller metadata must not override it.
            doc.put("chunk_content_sha256", cn.sdh.backend.utils.ContentFingerprint.sha256((String) doc.get("content")));

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
                    .knn(k -> k.field("embedding").queryVector(queryVector).k(topK).numCandidates(topK * 10)
                            .filter(publications.current(knowledgeId).filter("document_id")))
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
                    copySearchMetadata(doc, source, hit.id());
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
            throw new IllegalStateException("删除文档向量失败: " + e.getMessage(), e);
        }
    }

    static String deterministicVectorId(Long knowledgeId, Long documentId,
                                        Integer chunkIndex, Object revision) {
        String key = knowledgeId + ":" + documentId + ":" + chunkIndex + ":" + Objects.toString(revision, "0");
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
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
                copySearchMetadata(doc, source, response.id());
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
                    .bool(b -> b.must(m -> m.match(t -> t.field("content").query(query)))
                            .filter(publications.current(knowledgeId).filter("document_id"))));

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
                copySearchMetadata(doc, source, hit.id());
                doc.getMetadata().put("score", hit.score());
                documents.add(doc);
            }
        }
        return documents;
    }

    // ==================== RAG optimization new methods ====================

    @Override
    public List<Document> searchByDocumentIds(List<String> queries, Set<Long> documentIds,
            Long knowledgeId, int topKPerDoc, String embeddingModel) {
        return searchByDocumentIds(queries, documentIds, knowledgeId, topKPerDoc,
            embeddingModel, Map.of());
    }

    private void copySearchMetadata(Document document, Map<String, Object> source, String hitId) {
        document.getMetadata().put("id", hitId);
        List<String> fields = List.of(
            "chunk_id", "chunk_index", "document_id", "document_title", "knowledge_id",
            "total_chunks", "department", "doc_type", "doc_date", "freshness_score",
            "document_summary", "embedding_model", "create_time", "index_revision",
            "indexed_content_sha256", "chunk_content_sha256", "index_generation");
        for (String field : fields) {
            Object value = source.get(field);
            if (value != null) {
                document.getMetadata().put(field, value);
            }
        }
    }

    @Override
    public List<Document> searchByDocumentIds(List<String> queries, Set<Long> documentIds,
            Long knowledgeId, int topKPerDoc, String embeddingModel,
            Map<String, Object> metadataFilters) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        String indexName = elasticsearchIndexService.getIndexName(knowledgeId);
        List<Document> allResults = new ArrayList<>();
        try {
            List<String> docIdStrings = documentIds.stream().map(String::valueOf).collect(Collectors.toList());
            for (String query : queries) {
                float[] embedding = embeddingService.getEmbeddingByModel(query, embeddingModel);
                List<Float> queryVector = new ArrayList<>();
                for (float v : embedding) queryVector.add(v);

                var docIdFilter = new co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField.Builder()
                    .value(docIdStrings.stream()
                        .map(d -> FieldValue.of(d))
                        .collect(Collectors.toList()))
                    .build();

                var boolQuery = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder()
                    .must(m -> m.terms(t -> t.field("document_id").terms(docIdFilter)));
                applyMetadataFilters(boolQuery, metadataFilters);
                boolQuery.filter(publications.current(knowledgeId).filter("document_id"));

                var knn = co.elastic.clients.elasticsearch._types.KnnSearch.of(k -> k
                    .field("embedding")
                    .queryVector(queryVector)
                    .k(topKPerDoc * documentIds.size())
                    .numCandidates(topKPerDoc * documentIds.size() * 10)
                    .filter(boolQuery.build()._toQuery())
                );

                var response = elasticsearchClient.search(s -> s
                    .index(indexName).knn(knn).size(topKPerDoc * documentIds.size()), Map.class);
                allResults.addAll(convertHitsToDocuments(response.hits().hits()));
            }
        } catch (Exception e) {
            log.error("searchByDocumentIds failed: kbId={}", knowledgeId, e);
        }
        return allResults;
    }

    @Override
    public List<Document> hybridSearch(String query, Long knowledgeId, int vectorTopK,
            int keywordTopK, double vectorWeight, double keywordWeight,
            String embeddingModelName, Double minScore,
            Map<String, Object> metadataFilters) {
        List<Document> vectorResults = similaritySearchWithFilter(
            query, knowledgeId, vectorTopK, embeddingModelName, minScore, metadataFilters);
        List<Document> keywordResults = keywordSearchWithFilter(
            query, knowledgeId, keywordTopK, metadataFilters);
        return rrfMerge(vectorResults, keywordResults, vectorWeight, keywordWeight);
    }

    @Override
    public List<Document> coldSearch(String query, Long knowledgeId, int topK,
            String embeddingModelName, Map<String, Object> metadataFilters) {
        try {
            String indexName = getColdIndexName(knowledgeId);
            float[] embedding = embeddingService.getEmbeddingByModel(query, embeddingModelName);
            List<Float> queryVector = new ArrayList<>();
            for (float v : embedding) queryVector.add(v);

            var boolBuilder = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
            applyMetadataFilters(boolBuilder, metadataFilters);
            boolBuilder.filter(publications.current(knowledgeId).filter("document_id"));

            var knn = co.elastic.clients.elasticsearch._types.KnnSearch.of(k -> k
                .field("embedding").queryVector(queryVector)
                .k(topK).numCandidates(topK * 10)
                .filter(boolBuilder.build()._toQuery())
            );

            var response = elasticsearchClient.search(s -> s.index(indexName).knn(knn).size(topK), Map.class);
            return convertHitsToDocuments(response.hits().hits());
        } catch (Exception e) {
            log.warn("coldSearch failed: kbId={}, error={}", knowledgeId, e.getMessage());
            return List.of();
        }
    }

    private List<Document> similaritySearchWithFilter(String query, Long knowledgeId,
            int topK, String embeddingModelName, Double minScore,
            Map<String, Object> metadataFilters) {
        try {
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);
            float[] embedding = embeddingService.getEmbeddingByModel(query, embeddingModelName);
            List<Float> queryVector = new ArrayList<>();
            for (float v : embedding) queryVector.add(v);

            var boolBuilder = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
            applyMetadataFilters(boolBuilder, metadataFilters);
            boolBuilder.filter(publications.current(knowledgeId).filter("document_id"));

            var knn = co.elastic.clients.elasticsearch._types.KnnSearch.of(k -> k
                .field("embedding").queryVector(queryVector)
                .k(topK).numCandidates(topK * 10)
                .filter(boolBuilder.build()._toQuery())
            );

            var response = elasticsearchClient.search(s -> s.index(indexName).knn(knn).size(topK), Map.class);
            List<Document> results = convertHitsToDocuments(response.hits().hits());
            if (minScore != null && minScore > 0) {
                results = results.stream()
                    .filter(d -> (double) d.getMetadata().getOrDefault("score", 0.0) >= minScore)
                    .collect(Collectors.toList());
            }
            return results;
        } catch (Exception e) {
            log.error("similaritySearchWithFilter failed: kbId={}", knowledgeId, e);
            return List.of();
        }
    }

    private List<Document> keywordSearchWithFilter(String query, Long knowledgeId,
            int topK, Map<String, Object> metadataFilters) {
        try {
            String indexName = elasticsearchIndexService.getIndexName(knowledgeId);
            var boolBuilder = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder()
                .must(m -> m.match(mm -> mm.field("content").query(query)));
            applyMetadataFilters(boolBuilder, metadataFilters);
            boolBuilder.filter(publications.current(knowledgeId).filter("document_id"));
            var response = elasticsearchClient.search(s -> s
                .index(indexName).query(q -> q.bool(boolBuilder.build())).size(topK), Map.class);
            return convertHitsToDocuments(response.hits().hits());
        } catch (Exception e) {
            log.error("keywordSearchWithFilter failed: kbId={}", knowledgeId, e);
            return List.of();
        }
    }

    private List<Document> rrfMerge(List<Document> vectorResults, List<Document> keywordResults,
            double vectorWeight, double keywordWeight) {
        int k = 60;
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, Document> docMap = new LinkedHashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            String id = (String) vectorResults.get(i).getMetadata().get("id");
            if (id == null) id = "v_" + i;
            scoreMap.merge(id, vectorWeight / (k + i + 1), Double::sum);
            docMap.putIfAbsent(id, vectorResults.get(i));
        }
        for (int i = 0; i < keywordResults.size(); i++) {
            String id = (String) keywordResults.get(i).getMetadata().get("id");
            if (id == null) id = "k_" + i;
            scoreMap.merge(id, keywordWeight / (k + i + 1), Double::sum);
            docMap.putIfAbsent(id, keywordResults.get(i));
        }
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> {
                Document doc = docMap.get(e.getKey());
                doc.getMetadata().put("score", e.getValue());
                return doc;
            })
            .collect(Collectors.toList());
    }

    /**
     * 将受支持的元数据条件应用到 ES bool query。日期范围使用专用的内部键，
     * 其余字段使用精确 term，避免把范围条件错误地当作字段名查询。
     */
    private void applyMetadataFilters(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder boolBuilder,
            Map<String, Object> metadataFilters) {
        if (metadataFilters == null || metadataFilters.isEmpty()) {
            return;
        }

        String dateFrom = Objects.toString(metadataFilters.get("doc_date_gte"), null);
        String dateTo = Objects.toString(metadataFilters.get("doc_date_lte"), null);

        metadataFilters.forEach((field, value) -> {
            if (value == null || "doc_date_gte".equals(field) || "doc_date_lte".equals(field)) {
                return;
            }
            boolBuilder.filter(f -> f.term(t -> t.field(field).value(value.toString())));
        });

        if (dateFrom != null || dateTo != null) {
            boolBuilder.filter(f -> f.range(r -> r.date(d -> {
                d.field("doc_date");
                if (dateFrom != null) {
                    d.gte(dateFrom);
                }
                if (dateTo != null) {
                    d.lte(dateTo);
                }
                return d;
            })));
        }
    }

    private String getColdIndexName(Long knowledgeId) {
        return "sdh-rag-kb-" + knowledgeId + "-cold";
    }
}
