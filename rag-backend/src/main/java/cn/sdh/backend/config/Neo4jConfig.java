package cn.sdh.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j 配置类
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "cn.sdh.backend.graph.repository")
public class Neo4jConfig {
}
