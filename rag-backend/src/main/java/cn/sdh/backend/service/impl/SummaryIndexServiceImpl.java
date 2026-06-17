package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.ChatService;
import cn.sdh.backend.service.EmbeddingService;
import cn.sdh.backend.service.SummaryIndexService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryIndexServiceImpl implements SummaryIndexService {

    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;
    private final ChatService chatService;

    @Override
    @Async
    public void indexDocumentSummary(Long documentId, Long knowledgeId, String title,
                                      String content, String embeddingModel) {
        try {
            String truncated = content.substring(0, Math.min(3000, content.length()));
            String summary = generateSummary(title, truncated);
            float[] embedding = embeddingService.getEmbeddingByModel(summary, embeddingModel);

            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            Map<String, Object> doc = new HashMap<>();
            doc.put("doc_id", documentId);
            doc.put("summary_text", summary);
            doc.put("summary_embedding", embedding);
            doc.put("create_time", new Date());

            esClient.index(i -> i.index(indexName).id("doc_" + documentId).document(doc));
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
            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            float[] queryEmbedding = embeddingService.getEmbeddingByModel(query, embeddingModel);

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            if (metadataFilters != null && !metadataFilters.isEmpty()) {
                for (var entry : metadataFilters.entrySet()) {
                    boolBuilder.must(m -> m.term(
                        TermQuery.of(t -> t.field(entry.getKey()).value(entry.getValue().toString()))
                    ));
                }
            }

            KnnQuery knn = KnnQuery.of(k -> k
                .field("summary_embedding")
                .queryVector(Arrays.asList(toFloatList(queryEmbedding)))
                .k(topK)
                .numCandidates(topK * 10)
            );

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
            return chatService.chat(prompt, null);
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
}
