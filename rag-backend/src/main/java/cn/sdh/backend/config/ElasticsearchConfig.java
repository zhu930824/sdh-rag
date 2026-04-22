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
 * 索引已改为按知识库动态创建（ElasticsearchIndexService），不再在启动时创建全局索引
 */
@Slf4j
@Configuration
public class ElasticsearchConfig {

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:sdh-rag-index}")
    private String indexName;

    @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1536}")
    private int dimensions;

    @Value("${spring.ai.vectorstore.elasticsearch.similarity:cosine}")
    private String similarity;

    /**
     * 应用启动完成后检查 ES 连接
     */
    @Bean
    @ConditionalOnProperty(name = "spring.elasticsearch.uris")
    public ApplicationListener<ApplicationReadyEvent> elasticsearchHealthChecker(ElasticsearchClient elasticsearchClient) {
        return event -> {
            log.info("检查 Elasticsearch 连接...");
            try {
                boolean healthy = elasticsearchClient.ping().value();
                if (healthy) {
                    log.info("Elasticsearch 连接正常，索引将按知识库动态创建");
                } else {
                    log.warn("Elasticsearch 连接异常");
                }
            } catch (Exception e) {
                log.error("Elasticsearch 连接检查失败: {}", e.getMessage(), e);
            }
        };
    }
}
