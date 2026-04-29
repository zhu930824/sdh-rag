package cn.sdh.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 意图路由配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "intent-router")
public class IntentRouterConfig {

    /**
     * 是否启用智能路由
     */
    private boolean enabled = true;

    /**
     * 最小相关度阈值，低于此值不推荐知识库
     */
    private double minRelevanceScore = 0.3;

    /**
     * 最大推荐知识库数量
     */
    private int maxRecommendedBases = 3;

    /**
     * 是否返回路由详情
     */
    private boolean showRouterInfo = true;

    /**
     * 是否缓存路由结果
     */
    private boolean cacheEnabled = true;

    /**
     * 缓存过期时间（分钟）
     */
    private int cacheTtlMinutes = 10;
}