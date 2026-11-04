package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.EmbeddingService;
import cn.sdh.backend.service.SummaryIndexService;
import cn.sdh.backend.service.factory.ChatModelFactory;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryIndexServiceImpl implements SummaryIndexService {

    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;
    private final ChatModelFactory chatModelFactory;
    private final cn.sdh.backend.service.PublishedIndexService publications;

    @Override
    @Async
    public void indexDocumentSummary(Long documentId, Long knowledgeId, String title,
                                      String content, String embeddingModel) {
        indexDocumentSummary(documentId, knowledgeId, title, content, embeddingModel, Map.of());
    }

    @Override
    @Async
    public void indexDocumentSummary(Long documentId, Long knowledgeId, String title,
                                      String content, String embeddingModel,
                                      Map<String, Object> documentMetadata) {
        try {
            String generation = documentMetadata == null ? null : Objects.toString(documentMetadata.get("index_generation"), null);
            if (generation == null) return; // Unversioned summaries must not bypass the publication boundary.
            String truncated = content.substring(0, Math.min(3000, content.length()));
            String summary = generateSummary(title, truncated);
            float[] embedding = embeddingService.getEmbeddingByModel(summary, embeddingModel);

            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            esClient.indices().putMapping(m -> m.index(indexName).properties(Map.of("index_generation",
                    co.elastic.clients.elasticsearch._types.mapping.Property.of(p -> p.keyword(k -> k)))));
            Map<String, Object> doc = new HashMap<>();
            doc.put("doc_id", documentId);
            doc.put("summary_text", summary);
            doc.put("summary_embedding", embedding);
            doc.put("create_time", new Date());
            if (documentMetadata != null) {
                documentMetadata.forEach((key, value) -> {
                    if (value != null && Set.of("department", "doc_type", "doc_date",
                            "freshness_score", "index_generation", "index_revision", "indexed_content_sha256").contains(key)) {
                        doc.put(key, value);
                    }
                });
            }

            esClient.index(i -> i.index(indexName).id(generation).document(doc));
            var refresh = esClient.indices().refresh(r -> r.index(indexName));
            if (refresh.shards().failed().intValue() > 0) throw new IllegalStateException("摘要刷新失败");
            publications.summaryReady(generation);
            log.info("摘要索引写入成功: docId={}, kbId={}", documentId, knowledgeId);
        } catch (Exception e) {
            log.error("摘要索引写入失败: docId={}, kbId={}", documentId, knowledgeId, e);
        }
    }

    @Override
    public Map<Long, Double> coarseRetrieval(String query, Long knowledgeId, int topK,
                                              String embeddingModel,
                                              Map<String, Object> metadataFilters) {
        try {
            if (!publications.current(knowledgeId).snapshot().summariesComplete()) return Collections.emptyMap();
            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            float[] queryEmbedding = embeddingService.getEmbeddingByModel(query, embeddingModel);

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            applyMetadataFilters(boolBuilder, metadataFilters);
            boolBuilder.filter(publications.current(knowledgeId).filter("doc_id"));

            KnnSearch.Builder knnBuilder = new KnnSearch.Builder()
                .field("summary_embedding")
                .queryVector(Arrays.asList(toFloatList(queryEmbedding)))
                .k(topK)
                .numCandidates(topK * 10);
            knnBuilder.filter(boolBuilder.build()._toQuery());
            KnnSearch knn = knnBuilder.build();

            SearchResponse<Map> response = esClient.search(s -> s
                .index(indexName)
                .knn(knn)
                .size(topK),
                Map.class
            );

            Map<Long, Double> results = new LinkedHashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source != null && source.get("doc_id") != null) {
                    Long docId = Long.valueOf(source.get("doc_id").toString());
                    Double score = hit.score() != null ? hit.score() : 0.0;
                    results.put(docId, score);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("摘要粗筛检索失败: kbId={}", knowledgeId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public void deleteDocumentSummary(Long documentId, Long knowledgeId) {
        try {
            esClient.delete(d -> d.index(SUMMARY_INDEX_PREFIX + knowledgeId).id("doc_" + documentId));
        } catch (Exception e) {
            log.warn("删除摘要索引失败: docId={}, kbId={}", documentId, knowledgeId);
        }
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        try {
            esClient.indices().delete(d -> d.index(SUMMARY_INDEX_PREFIX + knowledgeId));
        } catch (Exception e) {
            log.warn("删除摘要索引失败: kbId={}", knowledgeId);
        }
    }

    private String generateSummary(String title, String content) {
        try {
            String prompt = String.format("""
                请为以下文档生成一段150-200字的核心摘要，涵盖：
                1. 文档主题和目的
                2. 关键知识点/结论
                3. 适用场景/范围
                文档标题：%s
                文档内容：%s
                请直接输出摘要，不要添加额外说明。""", title, content);
            ChatModel chatModel = chatModelFactory.getModel(null);
            if (chatModel == null) {
                throw new IllegalStateException("没有可用的摘要生成模型");
            }
            return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
        } catch (Exception e) {
            log.error("摘要生成失败: {}", title, e);
            return content.substring(0, Math.min(200, content.length())) + "...";
        }
    }

    private Float[] toFloatList(float[] arr) {
        Float[] result = new Float[arr.length];
        for (int i = 0; i < arr.length; i++) result[i] = arr[i];
        return result;
    }

    private void applyMetadataFilters(BoolQuery.Builder boolBuilder,
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
            boolBuilder.filter(f -> f.term(TermQuery.of(
                t -> t.field(field).value(value.toString()))));
        });
        if (dateFrom != null || dateTo != null) {
            boolBuilder.filter(f -> f.range(r -> r.date(d -> {
                d.field("doc_date");
                if (dateFrom != null) d.gte(dateFrom);
                if (dateTo != null) d.lte(dateTo);
                return d;
            })));
        }
    }
}
