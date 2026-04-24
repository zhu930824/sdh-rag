package cn.sdh.backend.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆系统配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "memory")
public class MemoryConfig {

    /** 是否启用记忆系统 */
    private boolean enabled = true;

    /** 工作记忆窗口大小 */
    private int workingWindowSize = 10;

    /** 工作记忆过期时间(小时) */
    private int workingMemoryTtlHours = 1;

    /** 语义记忆相似度阈值 */
    private double semanticSimilarityThreshold = 0.75;

    /** 语义记忆去重相似度阈值 */
    private double semanticDedupThreshold = 0.9;

    /** 模式记忆最小观察次数 */
    private int patternMinObservations = 3;

    /** 偏好提取重要性阈值 */
    private int preferenceImportanceThreshold = 5;

    /** 语义边界检测窗口大小 */
    private int boundaryDetectionWindowSize = 3;

    /** 摘要最大消息数阈值 */
    private int summaryMaxMessages = 20;

    /** 记忆衰减任务Cron表达式 */
    private String decayCron = "0 0 3 * * ?";

    /** ES记忆索引名称 */
    private String memoryIndexName = "sdh-memory-index";

    /** Redis工作记忆Key前缀 */
    private String workingMemoryKeyPrefix = "memory:working:";
}
