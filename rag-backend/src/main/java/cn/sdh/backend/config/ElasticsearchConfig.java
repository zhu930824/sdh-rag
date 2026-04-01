package cn.sdh.backend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
    public ApplicationListener<ApplicationReadyEvent> elasticsearchIndexInitializer() {
        return event -> {
            log.info("开始初始化 Elasticsearch 索引...");
            // 索引初始化由 Spring AI ElasticsearchVectorStore 自动完成
            // 这里只打印日志确认
            log.info("Elasticsearch 向量存储配置完成，索引名: {}, 维度: {}, 相似度: {}",
                indexName, dimensions, similarity);
        };
    }
}
