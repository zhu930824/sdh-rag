package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.ElasticsearchIndexService;
import cn.sdh.backend.service.factory.EmbeddingDimensionMapping;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch 索引管理服务实现
 * 每个知识库对应独立的 ES 索引，索引名格式：sdh-rag-kb-{knowledgeId}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexServiceImpl implements ElasticsearchIndexService {

    private static final String INDEX_PREFIX = "sdh-rag-kb-";

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public String getIndexName(Long knowledgeId) {
        if (knowledgeId == null) {
            throw new IllegalArgumentException("知识库ID不能为空");
        }
        return INDEX_PREFIX + knowledgeId;
    }

    @Override
    public void createIndexForKnowledgeBase(Long knowledgeId, String embeddingModelName) {
        String indexName = getIndexName(knowledgeId);
        int dimensions = EmbeddingDimensionMapping.getDimension(embeddingModelName);

        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(e -> e.index(indexName)).value();

            if (exists) {
                log.info("ES 索引已存在: {}, 跳过创建", indexName);
                return;
            }

            Map<String, Property> properties = buildMappingProperties(dimensions);

            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m.properties(properties))));

            log.info("ES 索引创建成功: {}, embeddingModel={}, dimensions={}",
                    indexName, embeddingModelName, dimensions);
        } catch (Exception e) {
            log.error("ES 索引创建失败: knowledgeId={}, embeddingModel={}, error={}",
                    knowledgeId, embeddingModelName, e.getMessage(), e);
            throw new RuntimeException("创建ES索引失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteIndexForKnowledgeBase(Long knowledgeId) {
        String indexName = getIndexName(knowledgeId);

        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(e -> e.index(indexName)).value();

            if (!exists) {
                log.info("ES 索引不存在: {}, 跳过删除", indexName);
                return;
            }

            elasticsearchClient.indices().delete(d -> d.index(indexName));
            log.info("ES 索引删除成功: {}", indexName);
        } catch (Exception e) {
            log.error("ES 索引删除失败: knowledgeId={}, error={}",
                    knowledgeId, e.getMessage(), e);
        }
    }

    @Override
    public boolean indexExists(Long knowledgeId) {
        String indexName = getIndexName(knowledgeId);
        try {
            return elasticsearchClient.indices()
                    .exists(e -> e.index(indexName)).value();
        } catch (Exception e) {
            log.error("检查ES索引存在性失败: knowledgeId={}, error={}",
                    knowledgeId, e.getMessage());
            return false;
        }
    }

    @Override
    public void ensureIndexExists(Long knowledgeId, String embeddingModelName) {
        if (!indexExists(knowledgeId)) {
            createIndexForKnowledgeBase(knowledgeId, embeddingModelName);
        }
    }

    private Map<String, Property> buildMappingProperties(int dimensions) {
        Map<String, Property> properties = new HashMap<>();

        properties.put("chunk_id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
        properties.put("document_id", Property.of(p -> p.keyword(KeywordProperty.of(l -> l))));
        properties.put("knowledge_id", Property.of(p -> p.keyword(KeywordProperty.of(l -> l))));
        properties.put("chunk_index", Property.of(p -> p.long_(LongNumberProperty.of(l -> l))));
        properties.put("content", Property.of(p -> p.text(TextProperty.of(t -> t
                .analyzer("ik_max_word")
                .searchAnalyzer("ik_smart")))));
        properties.put("embedding", Property.of(p -> p.denseVector(DenseVectorProperty.of(dv -> dv
                .dims(dimensions)
                .index(true)
                .similarity(DenseVectorSimilarity.Cosine)))));
        properties.put("embedding_model", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
        properties.put("create_time", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));

        return properties;
    }
}
