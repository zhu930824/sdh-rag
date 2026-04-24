package cn.sdh.backend.memory.layer;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.service.EmbeddingService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 语义记忆层 - Elasticsearch向量实现
 * 存储用户偏好和知识事实，支持向量检索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticMemoryLayer implements MemoryLayer {

    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;
    private final MemoryConfig config;

    @Value("${knowledge.default.embeddingModel:text-embedding-v2}")
    private String defaultEmbeddingModel;

    private static final String ID_FIELD = "id";
    private static final String USER_ID_FIELD = "userId";
    private static final String TYPE_FIELD = "type";
    private static final String CONTENT_FIELD = "content";
    private static final String EMBEDDING_FIELD = "embedding";
    private static final String IMPORTANCE_FIELD = "importance";
    private static final String ACCESS_COUNT_FIELD = "accessCount";
    private static final String LAST_ACCESS_FIELD = "lastAccessAt";
    private static final String CREATED_AT_FIELD = "createdAt";

    @PostConstruct
    public void init() {
        try {
            createIndexIfNotExists();
        } catch (Exception e) {
            log.error("Failed to initialize semantic memory index: {}", e.getMessage(), e);
        }
    }

    @Override
    public void store(MemoryEntry entry) {
        if (entry.getUserId() == null || entry.getContent() == null) {
            log.warn("Semantic memory requires userId and content");
            return;
        }

        try {
            // 检查是否已存在相似记忆
            List<MemoryEntry> similar = findSimilar(entry.getUserId(), entry.getType(), entry.getContent());
            if (!similar.isEmpty() && similar.get(0).getDecayedImportance() >= config.getSemanticDedupThreshold()) {
                MemoryEntry existing = similar.get(0);
                existing.setAccessCount(existing.getAccessCount() + 1);
                existing.setLastAccessAt(LocalDateTime.now());
                update(existing);
                log.debug("Updated existing semantic memory: {}", existing.getId());
                return;
            }

            // 生成向量
            float[] embedding = entry.getEmbedding();
            if (embedding == null) {
                embedding = embeddingService.getEmbeddingByModel(entry.getContent(), defaultEmbeddingModel);
                entry.setEmbedding(embedding);
            }

            // 生成ID
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID().toString());
            }
            entry.setCreatedAt(LocalDateTime.now());
            entry.setLastAccessAt(LocalDateTime.now());
            entry.setAccessCount(0);
            entry.setLayer(MemoryLayerType.SEMANTIC);

            // 构建文档
            Map<String, Object> doc = new HashMap<>();
            doc.put(ID_FIELD, entry.getId());
            doc.put(USER_ID_FIELD, entry.getUserId());
            doc.put(TYPE_FIELD, entry.getType().getCode());
            doc.put(CONTENT_FIELD, entry.getContent());
            doc.put(EMBEDDING_FIELD, entry.getEmbedding());
            doc.put(IMPORTANCE_FIELD, entry.getImportance());
            doc.put(ACCESS_COUNT_FIELD, entry.getAccessCount());
            doc.put(LAST_ACCESS_FIELD, entry.getLastAccessAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            doc.put(CREATED_AT_FIELD, entry.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // 索引文档
            esClient.index(i -> i
                .index(config.getMemoryIndexName())
                .id(entry.getId())
                .document(doc)
            );

            log.debug("Stored semantic memory: {} - {}", entry.getType(), entry.getContent());
        } catch (Exception e) {
            log.error("Failed to store semantic memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getUserId() == null || query.getQueryEmbedding() == null) {
            return Collections.emptyList();
        }

        try {
            // 构建向量检索查询
            Query vectorQuery = Query.of(q -> q
                .knn(knn -> knn
                    .field(EMBEDDING_FIELD)
                    .queryVector(convertToFloatList(query.getQueryEmbedding()))
                    .k(query.getLimit() > 0 ? query.getLimit() : 10)
                    .numCandidates(100)
                )
            );

            // 构建过滤条件
            List<Query> mustQueries = new ArrayList<>();
            mustQueries.add(Query.of(q -> q.term(t -> t.field(USER_ID_FIELD).value(query.getUserId()))));

            if (query.getType() != null) {
                mustQueries.add(Query.of(q -> q.term(t -> t.field(TYPE_FIELD).value(query.getType().getCode()))));
            }

            Query filterQuery = Query.of(q -> q.bool(BoolQuery.of(b -> b.must(mustQueries))));

            // 执行检索
            SearchResponse<Map> response = esClient.search(s -> s
                .index(config.getMemoryIndexName())
                .query(Query.of(q -> q.bool(b -> b
                    .must(filterQuery)
                    .should(vectorQuery)
                )))
                .size(query.getLimit() > 0 ? query.getLimit() : 10)
            , Map.class);

            return response.hits().hits().stream()
                .filter(hit -> hit.score() != null && hit.score() >= config.getSemanticSimilarityThreshold())
                .map(this::convertToEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to recall semantic memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void update(MemoryEntry entry) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put(IMPORTANCE_FIELD, entry.getImportance());
            doc.put(ACCESS_COUNT_FIELD, entry.getAccessCount());
            doc.put(LAST_ACCESS_FIELD, entry.getLastAccessAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            esClient.update(u -> u
                .index(config.getMemoryIndexName())
                .id(entry.getId())
                .doc(doc)
            , Map.class);
        } catch (Exception e) {
            log.error("Failed to update semantic memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public void delete(String id) {
        try {
            esClient.delete(d -> d
                .index(config.getMemoryIndexName())
                .id(id)
            );
        } catch (Exception e) {
            log.error("Failed to delete semantic memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.SEMANTIC;
    }

    @Override
    public void clearByUserId(Long userId) {
        try {
            esClient.deleteByQuery(d -> d
                .index(config.getMemoryIndexName())
                .query(q -> q.term(t -> t.field(USER_ID_FIELD).value(userId)))
            );
        } catch (Exception e) {
            log.error("Failed to clear semantic memory by userId: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            esClient.indices().exists(e -> e.index(config.getMemoryIndexName()));
            return true;
        } catch (Exception e) {
            log.warn("Elasticsearch not available for semantic memory: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查找相似记忆
     */
    public List<MemoryEntry> findSimilar(Long userId, MemoryType type, String content) {
        float[] embedding = embeddingService.getEmbeddingByModel(content, defaultEmbeddingModel);

        MemoryQuery query = MemoryQuery.builder()
            .userId(userId)
            .type(type)
            .queryEmbedding(embedding)
            .limit(5)
            .minSimilarity(config.getSemanticDedupThreshold())
            .build();

        return recall(query);
    }

    /**
     * 获取用户所有偏好
     */
    public List<MemoryEntry> getUserPreferences(Long userId) {
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index(config.getMemoryIndexName())
                .query(q -> q.bool(b -> b
                    .must(Query.of(mq -> mq.term(t -> t.field(USER_ID_FIELD).value(userId))))
                    .must(Query.of(mq -> mq.term(t -> t.field(TYPE_FIELD).value(MemoryType.PREFERENCE.getCode()))))
                ))
                .size(50)
            , Map.class);

            return response.hits().hits().stream()
                .map(this::convertToEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get user preferences: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户所有事实
     */
    public List<MemoryEntry> getUserFacts(Long userId) {
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index(config.getMemoryIndexName())
                .query(q -> q.bool(b -> b
                    .must(Query.of(mq -> mq.term(t -> t.field(USER_ID_FIELD).value(userId))))
                    .must(Query.of(mq -> mq.term(t -> t.field(TYPE_FIELD).value(MemoryType.FACT.getCode()))))
                ))
                .size(50)
            , Map.class);

            return response.hits().hits().stream()
                .map(this::convertToEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get user facts: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void createIndexIfNotExists() throws Exception {
        boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e
            .index(config.getMemoryIndexName())
        )).value();

        if (!exists) {
            esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(config.getMemoryIndexName())
                .mappings(m -> m
                    .properties(ID_FIELD, p -> p.keyword(k -> k))
                    .properties(USER_ID_FIELD, p -> p.long_(l -> l))
                    .properties(TYPE_FIELD, p -> p.keyword(k -> k))
                    .properties(CONTENT_FIELD, p -> p.text(t -> t.analyzer("ik_max_word")))
                    .properties(EMBEDDING_FIELD, p -> p.denseVector(d -> d
                        .dims(1536)
                        .similarity("cosine")
                    ))
                    .properties(IMPORTANCE_FIELD, p -> p.integer(i -> i))
                    .properties(ACCESS_COUNT_FIELD, p -> p.integer(i -> i))
                    .properties(LAST_ACCESS_FIELD, p -> p.date(d -> d))
                    .properties(CREATED_AT_FIELD, p -> p.date(d -> d))
                )
            ));
            log.info("Created semantic memory index: {}", config.getMemoryIndexName());
        }
    }

    private MemoryEntry convertToEntry(Hit<Map> hit) {
        try {
            Map<String, Object> source = hit.source();
            if (source == null) return null;

            return MemoryEntry.builder()
                .id((String) source.get(ID_FIELD))
                .userId(((Number) source.get(USER_ID_FIELD)).longValue())
                .type(MemoryType.valueOf(((String) source.get(TYPE_FIELD)).toUpperCase()))
                .content((String) source.get(CONTENT_FIELD))
                .importance(((Number) source.get(IMPORTANCE_FIELD)).intValue())
                .accessCount(((Number) source.get(ACCESS_COUNT_FIELD)).intValue())
                .layer(MemoryLayerType.SEMANTIC)
                .build();
        } catch (Exception e) {
            log.warn("Failed to convert ES hit to MemoryEntry: {}", e.getMessage());
            return null;
        }
    }

    private List<Float> convertToFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}
