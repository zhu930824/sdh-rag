package cn.sdh.backend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch 配置类
 * 负责项目启动时创建必要的索引
 */
@Slf4j
@Configuration
public class ElasticsearchConfig {

    /**
     * 知识库向量索引名称
     */
    @Value("${spring.ai.vectorstore.elasticsearch.index-name:sdh-rag-index}")
    private String indexName;

    /**
     * 向量维度（根据 embedding 模型确定）
     * text-embedding-v3 默认 1024 维，OpenAI 默认 1536 维
     */
    @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1536}")
    private int dimensions;

    /**
     * 相似度算法
     */
    @Value("${spring.ai.vectorstore.elasticsearch.similarity:cosine}")
    private String similarity;

    /**
     * 应用启动完成后初始化 ES 索引
     */
    @Bean
    @ConditionalOnProperty(name = "spring.elasticsearch.uris")
    public ApplicationListener<ApplicationReadyEvent> elasticsearchIndexInitializer(ElasticsearchClient elasticsearchClient) {
        return event -> {
            log.info("开始初始化 Elasticsearch 索引...");
            try {
                createIndexIfNotExists(elasticsearchClient);
                log.info("Elasticsearch 向量存储配置完成，索引名: {}, 维度: {}, 相似度: {}",
                    indexName, dimensions, similarity);
            } catch (Exception e) {
                log.error("Elasticsearch 索引初始化失败: {}", e.getMessage(), e);
            }
        };
    }

    /**
     * 如果索引不存在则创建
     */
    private void createIndexIfNotExists(ElasticsearchClient client) throws Exception {
        // 检查索引是否存在
        boolean exists = client.indices().exists(e -> e.index(indexName)).value();

        if (exists) {
            log.info("Elasticsearch 索引已存在: {}", indexName);
            return;
        }

        log.info("创建 Elasticsearch 索引: {}", indexName);

        // 创建索引 mapping
        Map<String, Property> properties = new HashMap<>();

        // chunk_id - 关键字类型
        properties.put("chunk_id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));

        // document_id - 长整型
        properties.put("document_id", Property.of(p -> p.keyword(KeywordProperty.of(l -> l))));

        // knowledge_id - 长整型
        properties.put("knowledge_id", Property.of(p -> p.keyword(KeywordProperty.of(l -> l))));

        // chunk_index - 长整型
        properties.put("chunk_index", Property.of(p -> p.long_(LongNumberProperty.of(l -> l))));

        // content - 文本类型，使用 ik 分词器（需要 ES 服务端安装 elasticsearch-analysis-ik 插件）
        properties.put("content", Property.of(p -> p.text(TextProperty.of(t -> t
            .analyzer("ik_max_word")
            .searchAnalyzer("ik_smart")))));

        // embedding - 密集向量（用于 knn 搜索）
        // 使用 index: true 启用 knn 索引
        properties.put("embedding", Property.of(p -> p.denseVector(DenseVectorProperty.of(dv -> dv
            .dims(dimensions)
            .index(true)
            .similarity(DenseVectorSimilarity.valueOf(similarity))))));

        // embedding_model - 关键字类型
        properties.put("embedding_model", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));

        // create_time - 关键字类型（存储为字符串）
        properties.put("create_time", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));

        // 创建索引
        client.indices().create(c -> c
            .index(indexName)
            .mappings(m -> m.properties(properties)));

        log.info("Elasticsearch 索引创建成功: {}", indexName);
    }
}
