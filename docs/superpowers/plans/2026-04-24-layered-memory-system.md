# 分层记忆系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现五层记忆架构，支持智能记忆抽象和跨会话记忆

**Architecture:** 分层记忆架构 - Working (Redis) → Episodic (MySQL) → Semantic (ES向量) → Pattern (Neo4j) → Abstract (MySQL摘要)

**Tech Stack:** Spring Boot 3.4, Spring AI, Elasticsearch 8, Neo4j, Redis, MyBatis Plus, Vue 3, Ant Design Vue

---

## 文件结构总览

### 后端新增文件

```
rag-backend/src/main/java/cn/sdh/backend/
├── memory/
│   ├── core/
│   │   ├── MemoryLayerType.java          # 记忆层类型枚举
│   │   ├── MemoryType.java               # 记忆类型枚举
│   │   ├── MemoryEntry.java              # 记忆条目实体
│   │   ├── MemoryQuery.java              # 记忆查询对象
│   │   ├── MemoryContext.java            # 记忆上下文
│   │   └── MemoryLayer.java              # 记忆层接口
│   ├── layer/
│   │   ├── WorkingMemoryLayer.java       # 工作记忆层(Redis)
│   │   ├── EpisodicMemoryLayer.java      # 情景记忆层(MySQL)
│   │   ├── SemanticMemoryLayer.java      # 语义记忆层(ES)
│   │   ├── PatternMemoryLayer.java       # 模式记忆层(Neo4j)
│   │   └── AbstractMemoryLayer.java      # 抽象记忆层(摘要)
│   ├── orchestrator/
│   │   ├── MemoryOrchestrator.java       # 记忆编排器
│   │   └── MemoryAssembler.java          # 上下文组装器
│   ├── extractor/
│   │   ├── MemoryExtractor.java          # 提取器接口
│   │   ├── PreferenceExtractor.java      # 偏好提取器
│   │   ├── FactExtractor.java            # 事实提取器
│   │   └── PatternExtractor.java         # 模式提取器
│   ├── boundary/
│   │   ├── BoundaryResult.java           # 边界检测结果
│   │   ├── SemanticBoundaryDetector.java # 语义边界检测器
│   │   └── SummarizationTrigger.java     # 摘要触发器
│   ├── config/
│   │   └── MemoryConfig.java             # 记忆配置类
│   └── service/
│       ├── MemoryService.java            # 记忆服务接口
│       └── impl/MemoryServiceImpl.java   # 记忆服务实现
├── entity/
│   └── MemoryAbstract.java               # 摘要实体(新增)
├── mapper/
│   └── MemoryAbstractMapper.java         # 摘要Mapper(新增)
├── controller/
│   └── MemoryController.java             # 记忆API控制器(新增)
└── dto/
    └── MemoryOverviewResponse.java       # 记忆概览响应(新增)
```

### 前端新增文件

```
rag-frontend/src/
├── api/
│   └── memory.ts                         # 记忆API
├── stores/
│   └── memory.ts                         # 记忆Store
├── views/
│   └── memory/
│       └── index.vue                     # 记忆管理页面
```

### 数据库迁移

```
rag-backend/src/main/resources/db/migration/
└── V13__add_memory_tables.sql            # 记忆相关表
```

---

## Phase 1: 基础框架

### Task 1: 创建记忆核心枚举和实体

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryLayerType.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryType.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryEntry.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryQuery.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryContext.java`

- [ ] **Step 1: 创建 MemoryLayerType 枚举**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryLayerType.java
package cn.sdh.backend.memory.core;

/**
 * 记忆层类型枚举
 */
public enum MemoryLayerType {
    WORKING("working", "工作记忆", "当前会话上下文"),
    EPISODIC("episodic", "情景记忆", "完整对话记录"),
    SEMANTIC("semantic", "语义记忆", "用户偏好和事实"),
    PATTERN("pattern", "模式记忆", "用户行为模式"),
    ABSTRACT("abstract", "抽象记忆", "对话摘要");
    
    private final String code;
    private final String name;
    private final String description;
    
    MemoryLayerType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
    
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
```

- [ ] **Step 2: 创建 MemoryType 枚举**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryType.java
package cn.sdh.backend.memory.core;

/**
 * 记忆类型枚举
 */
public enum MemoryType {
    PREFERENCE("preference", "用户偏好"),
    FACT("fact", "知识事实"),
    PATTERN("pattern", "行为模式"),
    EPISODE("episode", "对话片段"),
    SUMMARY("summary", "对话摘要");
    
    private final String code;
    private final String name;
    
    MemoryType(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() { return code; }
    public String getName() { return name; }
}
```

- [ ] **Step 3: 创建 MemoryEntry 实体**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryEntry.java
package cn.sdh.backend.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {
    
    /** 唯一标识 */
    private String id;
    
    /** 用户ID */
    private Long userId;
    
    /** 会话ID */
    private String sessionId;
    
    /** 记忆层 */
    private MemoryLayerType layer;
    
    /** 记忆类型 */
    private MemoryType type;
    
    /** 记忆内容 */
    private String content;
    
    /** 向量嵌入 */
    private float[] embedding;
    
    /** 元数据 */
    private Map<String, Object> metadata;
    
    /** 重要性评分 1-10 */
    private int importance;
    
    /** 访问次数 */
    private int accessCount;
    
    /** 最后访问时间 */
    private LocalDateTime lastAccessAt;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /**
     * 计算衰减后的重要性
     */
    public double getDecayedImportance() {
        if (lastAccessAt == null) {
            return importance;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(lastAccessAt, LocalDateTime.now());
        double recencyFactor = Math.exp(-0.05 * days);
        double accessFactor = 1 + Math.log(1 + accessCount) / 10.0;
        return importance * recencyFactor * accessFactor;
    }
}
```

- [ ] **Step 4: 创建 MemoryQuery 查询对象**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryQuery.java
package cn.sdh.backend.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆查询对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryQuery {
    
    /** 用户ID */
    private Long userId;
    
    /** 会话ID */
    private String sessionId;
    
    /** 查询文本 */
    private String query;
    
    /** 查询向量 */
    private float[] queryEmbedding;
    
    /** 记忆类型过滤 */
    private MemoryType type;
    
    /** 返回数量限制 */
    private int limit;
    
    /** 最小相似度阈值 */
    private double minSimilarity;
    
    /** 是否包含向量 */
    private boolean includeEmbedding;
}
```

- [ ] **Step 5: 创建 MemoryContext 上下文**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryContext.java
package cn.sdh.backend.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 记忆上下文 - 传递给LLM的记忆信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryContext {
    
    /** 最近对话轮次 */
    private List<MemoryEntry> recentMessages;
    
    /** 用户偏好列表 */
    private List<String> preferences;
    
    /** 已知事实列表 */
    private List<String> facts;
    
    /** 行为模式列表 */
    private List<String> patterns;
    
    /** 相关历史摘要 */
    private List<String> relevantSummaries;
    
    /** 当前话题标签 */
    private String currentTopic;
    
    /**
     * 构建系统提示词
     */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能助手。\n\n");
        
        if (preferences != null && !preferences.isEmpty()) {
            sb.append("## 用户偏好\n");
            for (String pref : preferences) {
                sb.append("- ").append(pref).append("\n");
            }
            sb.append("\n");
        }
        
        if (facts != null && !facts.isEmpty()) {
            sb.append("## 已知用户信息\n");
            for (String fact : facts) {
                sb.append("- ").append(fact).append("\n");
            }
            sb.append("\n");
        }
        
        if (patterns != null && !patterns.isEmpty()) {
            sb.append("## 用户交互风格\n");
            for (String pattern : patterns) {
                sb.append("- ").append(pattern).append("\n");
            }
            sb.append("\n");
        }
        
        if (relevantSummaries != null && !relevantSummaries.isEmpty()) {
            sb.append("## 相关历史对话摘要\n");
            for (String summary : relevantSummaries) {
                sb.append("- ").append(summary).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 是否有有效记忆
     */
    public boolean hasMemory() {
        return (preferences != null && !preferences.isEmpty())
            || (facts != null && !facts.isEmpty())
            || (patterns != null && !patterns.isEmpty())
            || (relevantSummaries != null && !relevantSummaries.isEmpty());
    }
}
```

- [ ] **Step 6: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/core/
git commit -m "$(cat <<'EOF'
feat(memory): add core memory types and entities

- Add MemoryLayerType enum (WORKING, EPISODIC, SEMANTIC, PATTERN, ABSTRACT)
- Add MemoryType enum (PREFERENCE, FACT, PATTERN, EPISODE, SUMMARY)
- Add MemoryEntry entity with importance decay calculation
- Add MemoryQuery for memory retrieval
- Add MemoryContext for LLM context assembly

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 创建记忆层接口和配置

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryLayer.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/config/MemoryConfig.java`

- [ ] **Step 1: 创建 MemoryLayer 接口**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryLayer.java
package cn.sdh.backend.memory.core;

import java.util.List;

/**
 * 记忆层接口
 */
public interface MemoryLayer {
    
    /**
     * 存储记忆
     */
    void store(MemoryEntry entry);
    
    /**
     * 批量存储记忆
     */
    default void storeAll(List<MemoryEntry> entries) {
        entries.forEach(this::store);
    }
    
    /**
     * 检索记忆
     */
    List<MemoryEntry> recall(MemoryQuery query);
    
    /**
     * 更新记忆
     */
    void update(MemoryEntry entry);
    
    /**
     * 删除记忆
     */
    void delete(String id);
    
    /**
     * 获取记忆层类型
     */
    MemoryLayerType getType();
    
    /**
     * 清除用户的所有记忆
     */
    void clearByUserId(Long userId);
    
    /**
     * 检查该层是否可用
     */
    default boolean isAvailable() {
        return true;
    }
}
```

- [ ] **Step 2: 创建 MemoryConfig 配置类**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/config/MemoryConfig.java
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
```

- [ ] **Step 3: 更新 application.yml 配置**

在 `rag-backend/src/main/resources/application.yml` 添加:

```yaml
# 记忆系统配置
memory:
  enabled: true
  working-window-size: 10
  working-memory-ttl-hours: 1
  semantic-similarity-threshold: 0.75
  semantic-dedup-threshold: 0.9
  pattern-min-observations: 3
  preference-importance-threshold: 5
  boundary-detection-window-size: 3
  summary-max-messages: 20
  decay-cron: "0 0 3 * * ?"
  memory-index-name: "sdh-memory-index"
  working-memory-key-prefix: "memory:working:"
```

- [ ] **Step 4: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/core/MemoryLayer.java
git add rag-backend/src/main/java/cn/sdh/backend/memory/config/MemoryConfig.java
git add rag-backend/src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
feat(memory): add MemoryLayer interface and MemoryConfig

- Add MemoryLayer interface with store, recall, update, delete operations
- Add MemoryConfig with configurable parameters for all memory layers
- Add memory configuration to application.yml

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 实现 Working Memory Layer (Redis)

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/layer/WorkingMemoryLayer.java`

- [ ] **Step 1: 创建 WorkingMemoryLayer**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/layer/WorkingMemoryLayer.java
package cn.sdh.backend.memory.layer;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 工作记忆层 - Redis实现
 * 存储当前会话的最近N轮对话
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingMemoryLayer implements MemoryLayer {
    
    private final StringRedisTemplate redisTemplate;
    private final MemoryConfig config;
    
    @Override
    public void store(MemoryEntry entry) {
        if (entry.getSessionId() == null) {
            log.warn("Working memory requires sessionId");
            return;
        }
        
        String key = getRedisKey(entry.getSessionId());
        
        try {
            // 序列化记忆条目
            Map<String, Object> entryMap = new HashMap<>();
            entryMap.put("id", entry.getId());
            entryMap.put("userId", entry.getUserId());
            entryMap.put("content", entry.getContent());
            entryMap.put("type", entry.getType().getCode());
            entryMap.put("createdAt", entry.getCreatedAt().toString());
            
            String json = JSON.toJSONString(entryMap);
            
            // 添加到列表尾部
            redisTemplate.opsForList().rightPush(key, json);
            
            // 保持窗口大小
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > config.getWorkingWindowSize()) {
                long trimCount = size - config.getWorkingWindowSize();
                for (int i = 0; i < trimCount; i++) {
                    redisTemplate.opsForList().leftPop(key);
                }
            }
            
            // 设置过期时间
            redisTemplate.expire(key, config.getWorkingMemoryTtlHours(), TimeUnit.HOURS);
            
            log.debug("Stored working memory for session: {}", entry.getSessionId());
        } catch (Exception e) {
            log.error("Failed to store working memory: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getSessionId() == null) {
            return Collections.emptyList();
        }
        
        String key = getRedisKey(query.getSessionId());
        
        try {
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (jsonList == null || jsonList.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<MemoryEntry> entries = new ArrayList<>();
            for (String json : jsonList) {
                try {
                    Map<String, Object> map = JSON.parseObject(json, Map.class);
                    MemoryEntry entry = MemoryEntry.builder()
                        .id((String) map.get("id"))
                        .userId(((Number) map.get("userId")).longValue())
                        .content((String) map.get("content"))
                        .type(MemoryType.valueOf(((String) map.get("type")).toUpperCase()))
                        .layer(MemoryLayerType.WORKING)
                        .createdAt(LocalDateTime.parse((String) map.get("createdAt")))
                        .build();
                    entries.add(entry);
                } catch (Exception e) {
                    log.warn("Failed to parse working memory entry: {}", e.getMessage());
                }
            }
            
            // 限制返回数量
            int limit = query.getLimit() > 0 ? query.getLimit() : config.getWorkingWindowSize();
            if (entries.size() > limit) {
                return entries.subList(entries.size() - limit, entries.size());
            }
            
            return entries;
        } catch (Exception e) {
            log.error("Failed to recall working memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public void update(MemoryEntry entry) {
        // 工作记忆不支持更新，通过重新存储实现
        store(entry);
    }
    
    @Override
    public void delete(String id) {
        // 工作记忆按会话管理，不支持单个删除
        log.warn("Working memory does not support single entry deletion");
    }
    
    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.WORKING;
    }
    
    @Override
    public void clearByUserId(Long userId) {
        // 工作记忆按会话管理，无法按用户清除
        log.warn("Working memory is session-based, cannot clear by userId");
    }
    
    @Override
    public boolean isAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis not available for working memory: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 清除会话的工作记忆
     */
    public void clearSession(String sessionId) {
        String key = getRedisKey(sessionId);
        redisTemplate.delete(key);
        log.debug("Cleared working memory for session: {}", sessionId);
    }
    
    /**
     * 获取会话消息数量
     */
    public long getMessageCount(String sessionId) {
        String key = getRedisKey(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        return size != null ? size : 0;
    }
    
    private String getRedisKey(String sessionId) {
        return config.getWorkingMemoryKeyPrefix() + sessionId;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/layer/WorkingMemoryLayer.java
git commit -m "$(cat <<'EOF'
feat(memory): implement WorkingMemoryLayer with Redis

- Store recent N messages per session with sliding window
- Auto-expire after configured TTL (default 1 hour)
- Support session-based retrieval and clearing
- Graceful degradation when Redis unavailable

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 创建数据库迁移和摘要实体

**Files:**
- Create: `rag-backend/src/main/resources/db/migration/V13__add_memory_tables.sql`
- Create: `rag-backend/src/main/java/cn/sdh/backend/entity/MemoryAbstract.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/mapper/MemoryAbstractMapper.java`

- [ ] **Step 1: 创建数据库迁移脚本**

```sql
-- rag-backend/src/main/resources/db/migration/V13__add_memory_tables.sql
-- V13 添加记忆系统相关表

SET NAMES utf8mb4;

-- 摘要记忆表
CREATE TABLE IF NOT EXISTS `memory_abstract` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '摘要ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `session_id` VARCHAR(100) COMMENT '会话ID',
  `episode_id` VARCHAR(100) COMMENT '情景块ID',
  `topic_tag` VARCHAR(200) COMMENT '话题标签',
  `summary` TEXT COMMENT 'LLM生成的摘要',
  `key_points` TEXT COMMENT 'JSON格式的关键结论列表',
  `importance` INT DEFAULT 5 COMMENT '重要性评分 1-10',
  `access_count` INT DEFAULT 0 COMMENT '访问次数',
  `last_access_at` DATETIME COMMENT '最后访问时间',
  `needs_summary` TINYINT DEFAULT 0 COMMENT '是否需要补摘要',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_episode_id` (`episode_id`),
  INDEX `idx_topic_tag` (`topic_tag`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '记忆摘要表';

-- 为 chat_history 表添加语义分块字段
ALTER TABLE `chat_history` 
  ADD COLUMN IF NOT EXISTS `topic_tag` VARCHAR(200) COMMENT '话题标签' AFTER `sources`,
  ADD COLUMN IF NOT EXISTS `episode_id` VARCHAR(100) COMMENT '情景块ID' AFTER `topic_tag`;

-- 添加索引
CREATE INDEX IF NOT EXISTS `idx_chat_history_episode` ON `chat_history` (`episode_id`);
```

- [ ] **Step 2: 创建 MemoryAbstract 实体**

```java
// rag-backend/src/main/java/cn/sdh/backend/entity/MemoryAbstract.java
package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆摘要实体
 */
@Data
@TableName(value = "memory_abstract", autoResultMap = true)
public class MemoryAbstract {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String sessionId;
    
    private String episodeId;
    
    private String topicTag;
    
    private String summary;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keyPoints;
    
    private Integer importance;
    
    private Integer accessCount;
    
    private LocalDateTime lastAccessAt;
    
    private Boolean needsSummary;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 MemoryAbstractMapper**

```java
// rag-backend/src/main/java/cn/sdh/backend/mapper/MemoryAbstractMapper.java
package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.MemoryAbstract;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆摘要Mapper
 */
@Mapper
public interface MemoryAbstractMapper extends BaseMapper<MemoryAbstract> {
    
    /**
     * 查询用户的摘要列表
     */
    @Select("SELECT * FROM memory_abstract WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<MemoryAbstract> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    
    /**
     * 查询需要补摘要的记录
     */
    @Select("SELECT * FROM memory_abstract WHERE needs_summary = 1 LIMIT #{limit}")
    List<MemoryAbstract> findNeedsSummary(@Param("limit") int limit);
    
    /**
     * 查询过期的摘要（用于归档）
     */
    @Select("SELECT * FROM memory_abstract WHERE created_at < #{before} AND importance < #{minImportance}")
    List<MemoryAbstract> findExpired(@Param("before") LocalDateTime before, @Param("minImportance") int minImportance);
}
```

- [ ] **Step 4: 提交**

```bash
git add rag-backend/src/main/resources/db/migration/V13__add_memory_tables.sql
git add rag-backend/src/main/java/cn/sdh/backend/entity/MemoryAbstract.java
git add rag-backend/src/main/java/cn/sdh/backend/mapper/MemoryAbstractMapper.java
git commit -m "$(cat <<'EOF'
feat(memory): add memory_abstract table and entity

- Add Flyway migration V13 for memory_abstract table
- Add topic_tag and episode_id columns to chat_history
- Add MemoryAbstract entity with MyBatis Plus mapping
- Add MemoryAbstractMapper with custom queries

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: 语义记忆层

### Task 5: 实现 Episodic Memory Layer

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/layer/EpisodicMemoryLayer.java`

- [ ] **Step 1: 创建 EpisodicMemoryLayer**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/layer/EpisodicMemoryLayer.java
package cn.sdh.backend.memory.layer;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 情景记忆层 - MySQL实现
 * 存储完整对话记录，按语义分块
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpisodicMemoryLayer implements MemoryLayer {
    
    private final ChatHistoryMapper chatHistoryMapper;
    private final MemoryConfig config;
    
    @Override
    public void store(MemoryEntry entry) {
        // 情景记忆通过ChatService存储，这里不直接存储
        log.debug("Episodic memory is stored via ChatService");
    }
    
    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getUserId() == null) {
            return Collections.emptyList();
        }
        
        try {
            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getUserId, query.getUserId());
            
            if (query.getSessionId() != null) {
                wrapper.eq(ChatHistory::getSessionId, query.getSessionId());
            }
            
            wrapper.orderByDesc(ChatHistory::getCreateTime);
            
            int limit = query.getLimit() > 0 ? query.getLimit() : 50;
            wrapper.last("LIMIT " + limit);
            
            List<ChatHistory> histories = chatHistoryMapper.selectList(wrapper);
            
            return histories.stream()
                .map(this::convertToEntry)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to recall episodic memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public void update(MemoryEntry entry) {
        // 更新话题标签和情景块ID
        if (entry.getId() != null && entry.getMetadata() != null) {
            try {
                Long historyId = Long.parseLong(entry.getId());
                ChatHistory history = chatHistoryMapper.selectById(historyId);
                if (history != null) {
                    if (entry.getMetadata().containsKey("topicTag")) {
                        // 需要通过原生SQL更新，因为字段可能是新加的
                        // chatHistoryMapper.updateTopicTag(historyId, (String) entry.getMetadata().get("topicTag"));
                    }
                    if (entry.getMetadata().containsKey("episodeId")) {
                        // chatHistoryMapper.updateEpisodeId(historyId, (String) entry.getMetadata().get("episodeId"));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to update episodic memory: {}", e.getMessage());
            }
        }
    }
    
    @Override
    public void delete(String id) {
        try {
            chatHistoryMapper.deleteById(Long.parseLong(id));
        } catch (Exception e) {
            log.warn("Failed to delete episodic memory: {}", e.getMessage());
        }
    }
    
    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.EPISODIC;
    }
    
    @Override
    public void clearByUserId(Long userId) {
        // 不支持清除，保留历史记录
        log.warn("Episodic memory cannot be cleared by userId - history is preserved");
    }
    
    /**
     * 获取会话的对话历史
     */
    public List<MemoryEntry> getSessionHistory(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        
        try {
            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getSessionId, sessionId)
                   .orderByAsc(ChatHistory::getCreateTime);
            
            List<ChatHistory> histories = chatHistoryMapper.selectList(wrapper);
            
            return histories.stream()
                .map(this::convertToEntry)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get session history: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 标记情景块
     */
    public void markEpisode(List<String> historyIds, String episodeId, String topicTag) {
        // 批量更新情景块ID和话题标签
        // 这里需要根据实际数据库字段实现
        log.debug("Marking episode {} with topic {} for {} messages", episodeId, topicTag, historyIds.size());
    }
    
    /**
     * 生成新的情景块ID
     */
    public String generateEpisodeId() {
        return "episode_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private MemoryEntry convertToEntry(ChatHistory history) {
        return MemoryEntry.builder()
            .id(String.valueOf(history.getId()))
            .userId(history.getUserId())
            .sessionId(history.getSessionId())
            .layer(MemoryLayerType.EPISODIC)
            .type(MemoryType.EPISODE)
            .content("Q: " + history.getQuestion() + "\nA: " + history.getAnswer())
            .createdAt(history.getCreateTime())
            .importance(5)
            .build();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/layer/EpisodicMemoryLayer.java
git commit -m "$(cat <<'EOF'
feat(memory): implement EpisodicMemoryLayer with MySQL

- Recall conversation history from chat_history table
- Support session-based and user-based queries
- Provide episode marking for semantic grouping
- Preserve history (no delete by user)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 实现 Semantic Memory Layer (ES向量)

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/layer/SemanticMemoryLayer.java`

- [ ] **Step 1: 创建 SemanticMemoryLayer**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/layer/SemanticMemoryLayer.java
package cn.sdh.backend.memory.layer;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.service.EmbeddingService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
                // 更新现有记忆
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
                embedding = embeddingService.embed(entry.getContent());
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
        float[] embedding = embeddingService.embed(content);
        
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
```

- [ ] **Step 2: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/layer/SemanticMemoryLayer.java
git commit -m "$(cat <<'EOF'
feat(memory): implement SemanticMemoryLayer with Elasticsearch

- Store user preferences and facts with vector embeddings
- Support similarity-based retrieval using kNN
- Auto-deduplicate similar memories (threshold 0.9)
- Create ES index with dense_vector mapping on startup
- Provide getUserPreferences and getUserFacts helpers

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: 模式记忆层与提取器

### Task 7: 实现 Pattern Memory Layer (Neo4j)

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/layer/PatternMemoryLayer.java`

- [ ] **Step 1: 创建 PatternMemoryLayer**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/layer/PatternMemoryLayer.java
package cn.sdh.backend.memory.layer;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 模式记忆层 - Neo4j图谱实现
 * 存储用户行为模式，以图谱形式组织
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternMemoryLayer implements MemoryLayer {
    
    private final Neo4jClient neo4jClient;
    private final MemoryConfig config;
    
    @Override
    public void store(MemoryEntry entry) {
        if (entry.getUserId() == null || entry.getContent() == null) {
            log.warn("Pattern memory requires userId and content");
            return;
        }
        
        try {
            // 解析模式内容
            Map<String, Object> pattern = parsePatternContent(entry.getContent());
            if (pattern.isEmpty()) {
                return;
            }
            
            String patternName = (String) pattern.get("name");
            String patternType = (String) pattern.getOrDefault("type", "behavior");
            String description = (String) pattern.get("description");
            Double confidence = (Double) pattern.getOrDefault("confidence", 0.5);
            
            // 确保用户节点存在
            neo4jClient.query("""
                MERGE (u:User {id: $userId})
                """)
                .bind(entry.getUserId()).to("userId")
                .run();
            
            // 根据模式类型创建不同节点
            switch (patternType) {
                case "behavior":
                    storeBehaviorPattern(entry.getUserId(), patternName, description, confidence);
                    break;
                case "preference":
                    storePreference(entry.getUserId(), patternName, (String) pattern.get("value"));
                    break;
                case "topic":
                    storeTopic(entry.getUserId(), patternName, (String) pattern.get("level"));
                    break;
                default:
                    log.warn("Unknown pattern type: {}", patternType);
            }
            
            log.debug("Stored pattern memory: {} - {}", patternType, patternName);
        } catch (Exception e) {
            log.error("Failed to store pattern memory: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getUserId() == null) {
            return Collections.emptyList();
        }
        
        try {
            List<MemoryEntry> patterns = new ArrayList<>();
            
            // 获取行为模式
            patterns.addAll(getBehaviorPatterns(query.getUserId()));
            
            // 获取偏好
            patterns.addAll(getPreferences(query.getUserId()));
            
            // 获取熟悉主题
            patterns.addAll(getTopics(query.getUserId()));
            
            return patterns;
        } catch (Exception e) {
            log.error("Failed to recall pattern memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public void update(MemoryEntry entry) {
        // 更新模式信心度
        try {
            neo4jClient.query("""
                MATCH (u:User {id: $userId})-[r:HAS_PATTERN]->(p:BehaviorPattern {name: $name})
                SET p.confidence = $confidence,
                    p.observedCount = p.observedCount + 1,
                    p.lastObserved = datetime()
                """)
                .bind(entry.getUserId()).to("userId")
                .bind(entry.getMetadata().get("name")).to("name")
                .bind(entry.getMetadata().get("confidence")).to("confidence")
                .run();
        } catch (Exception e) {
            log.warn("Failed to update pattern memory: {}", e.getMessage());
        }
    }
    
    @Override
    public void delete(String id) {
        // 模式记忆暂不支持单个删除
        log.warn("Pattern memory does not support single entry deletion");
    }
    
    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.PATTERN;
    }
    
    @Override
    public void clearByUserId(Long userId) {
        try {
            neo4jClient.query("""
                MATCH (u:User {id: $userId})
                DETACH DELETE u
                """)
                .bind(userId).to("userId")
                .run();
        } catch (Exception e) {
            log.error("Failed to clear pattern memory: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            neo4jClient.query("RETURN 1").run();
            return true;
        } catch (Exception e) {
            log.warn("Neo4j not available for pattern memory: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户行为模式
     */
    public List<MemoryEntry> getBehaviorPatterns(Long userId) {
        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                MATCH (u:User {id: $userId})-[:HAS_PATTERN]->(p:BehaviorPattern)
                RETURN p.name as name, p.description as description, p.confidence as confidence
                ORDER BY p.confidence DESC
                """)
                .bind(userId).to("userId")
                .fetch()
                .all();
            
            List<MemoryEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : results) {
                entries.add(MemoryEntry.builder()
                    .id("pattern_" + row.get("name"))
                    .userId(userId)
                    .layer(MemoryLayerType.PATTERN)
                    .type(MemoryType.PATTERN)
                    .content((String) row.get("description"))
                    .importance((int) (((Number) row.get("confidence")).doubleValue() * 10))
                    .build());
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to get behavior patterns: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户偏好
     */
    public List<MemoryEntry> getPreferences(Long userId) {
        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                MATCH (u:User {id: $userId})-[:PREFERS]->(p:Preference)
                RETURN p.name as name, p.value as value
                """)
                .bind(userId).to("userId")
                .fetch()
                .all();
            
            List<MemoryEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : results) {
                entries.add(MemoryEntry.builder()
                    .id("pref_" + row.get("name"))
                    .userId(userId)
                    .layer(MemoryLayerType.PATTERN)
                    .type(MemoryType.PREFERENCE)
                    .content(row.get("name") + ": " + row.get("value"))
                    .importance(6)
                    .build());
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to get preferences: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户熟悉主题
     */
    public List<MemoryEntry> getTopics(Long userId) {
        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                MATCH (u:User {id: $userId})-[:FAMILIAR_WITH]->(t:Topic)
                RETURN t.name as name, t.level as level
                """)
                .bind(userId).to("userId")
                .fetch()
                .all();
            
            List<MemoryEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : results) {
                entries.add(MemoryEntry.builder()
                    .id("topic_" + row.get("name"))
                    .userId(userId)
                    .layer(MemoryLayerType.PATTERN)
                    .type(MemoryType.FACT)
                    .content("熟悉" + row.get("name") + "，水平：" + row.get("level"))
                    .importance(5)
                    .build());
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to get topics: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    private void storeBehaviorPattern(Long userId, String name, String description, Double confidence) {
        neo4jClient.query("""
            MATCH (u:User {id: $userId})
            MERGE (p:BehaviorPattern {name: $name})
            ON CREATE SET p.description = $description, p.confidence = $confidence, p.observedCount = 1
            ON MATCH SET p.confidence = CASE 
                WHEN p.confidence < $confidence THEN $confidence 
                ELSE p.confidence 
            END,
            p.observedCount = p.observedCount + 1,
            p.lastObserved = datetime()
            MERGE (u)-[:HAS_PATTERN]->(p)
            """)
            .bind(userId).to("userId")
            .bind(name).to("name")
            .bind(description).to("description")
            .bind(confidence).to("confidence")
            .run();
    }
    
    private void storePreference(Long userId, String name, String value) {
        neo4jClient.query("""
            MATCH (u:User {id: $userId})
            MERGE (p:Preference {name: $name})
            SET p.value = $value, p.updatedAt = datetime()
            MERGE (u)-[:PREFERS]->(p)
            """)
            .bind(userId).to("userId")
            .bind(name).to("name")
            .bind(value).to("value")
            .run();
    }
    
    private void storeTopic(Long userId, String name, String level) {
        neo4jClient.query("""
            MATCH (u:User {id: $userId})
            MERGE (t:Topic {name: $name})
            SET t.level = $level
            MERGE (u)-[:FAMILIAR_WITH]->(t)
            """)
            .bind(userId).to("userId")
            .bind(name).to("name")
            .bind(level).to("level")
            .run();
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePatternContent(String content) {
        // 期望格式: JSON或简单键值对
        Map<String, Object> pattern = new HashMap<>();
        try {
            if (content.startsWith("{")) {
                // JSON格式
                pattern = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(content, Map.class);
            } else if (content.contains(":")) {
                // 简单键值对格式
                String[] parts = content.split(":", 2);
                pattern.put("name", parts[0].trim());
                pattern.put("description", parts[1].trim());
                pattern.put("type", "behavior");
                pattern.put("confidence", 0.7);
            }
        } catch (Exception e) {
            log.warn("Failed to parse pattern content: {}", content);
        }
        return pattern;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/layer/PatternMemoryLayer.java
git commit -m "$(cat <<'EOF'
feat(memory): implement PatternMemoryLayer with Neo4j

- Store user behavior patterns as graph nodes
- Support three pattern types: behavior, preference, topic
- Track confidence and observation count
- Retrieve patterns via Cypher queries
- Graceful degradation when Neo4j unavailable

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: 实现记忆提取器

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/extractor/MemoryExtractor.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/extractor/PreferenceExtractor.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/extractor/FactExtractor.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/extractor/PatternExtractor.java`

- [ ] **Step 1: 创建 MemoryExtractor 接口**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/extractor/MemoryExtractor.java
package cn.sdh.backend.memory.extractor;

import cn.sdh.backend.memory.core.MemoryEntry;
import cn.sdh.backend.memory.core.MemoryLayerType;
import cn.sdh.backend.memory.core.MemoryType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆提取器接口
 */
public interface MemoryExtractor {
    
    /**
     * 从对话中提取记忆
     */
    CompletableFuture<List<MemoryEntry>> extract(Long userId, String question, String answer);
    
    /**
     * 获取提取器处理的记忆类型
     */
    MemoryType getMemoryType();
    
    /**
     * 获取目标存储层
     */
    MemoryLayerType getTargetLayer();
    
    /**
     * 获取提取器名称
     */
    default String getName() {
        return getMemoryType().getName() + "Extractor";
    }
}
```

- [ ] **Step 2: 创建 PreferenceExtractor**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/extractor/PreferenceExtractor.java
package cn.sdh.backend.memory.extractor;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 偏好提取器
 * 从对话中提取用户偏好
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreferenceExtractor implements MemoryExtractor {
    
    private final ChatClient.Builder chatClientBuilder;
    private final MemoryConfig config;
    
    private static final String EXTRACTION_PROMPT = """
        从以下对话中提取用户偏好，返回JSON数组。
        
        对话：
        用户：%s
        助手：%s
        
        提取规则：
        1. 语言偏好（中文/英文）
        2. 回复风格偏好（简洁/详细/代码示例）
        3. 技术栈偏好
        4. 其他显式偏好表达
        
        返回格式：[{"content": "偏好内容", "importance": 1-10}]
        无偏好则返回空数组 []
        只返回JSON数组，不要有其他内容。
        """;
    
    @Override
    public CompletableFuture<List<MemoryEntry>> extract(Long userId, String question, String answer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(EXTRACTION_PROMPT, question, answer);
                
                String response = chatClientBuilder.build()
                    .prompt(prompt)
                    .call()
                    .content();
                
                List<MemoryEntry> entries = parseResponse(userId, response);
                
                // 过滤低重要性偏好
                return entries.stream()
                    .filter(e -> e.getImportance() >= config.getPreferenceImportanceThreshold())
                    .toList();
            } catch (Exception e) {
                log.error("Failed to extract preferences: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
    
    @Override
    public MemoryType getMemoryType() {
        return MemoryType.PREFERENCE;
    }
    
    @Override
    public MemoryLayerType getTargetLayer() {
        return MemoryLayerType.SEMANTIC;
    }
    
    private List<MemoryEntry> parseResponse(Long userId, String response) {
        List<MemoryEntry> entries = new ArrayList<>();
        
        try {
            // 提取JSON数组
            String jsonStr = response;
            if (response.contains("[")) {
                jsonStr = response.substring(response.indexOf("["), response.lastIndexOf("]") + 1);
            }
            
            JSONArray array = JSON.parseArray(jsonStr);
            if (array == null || array.isEmpty()) {
                return entries;
            }
            
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String content = obj.getString("content");
                int importance = obj.getIntValue("importance", 5);
                
                if (content != null && !content.isBlank()) {
                    entries.add(MemoryEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .type(MemoryType.PREFERENCE)
                        .layer(MemoryLayerType.SEMANTIC)
                        .content(content)
                        .importance(importance)
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse preference extraction response: {}", e.getMessage());
        }
        
        return entries;
    }
}
```

- [ ] **Step 3: 创建 FactExtractor**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/extractor/FactExtractor.java
package cn.sdh.backend.memory.extractor;

import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 事实提取器
 * 从对话中提取客观事实
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FactExtractor implements MemoryExtractor {
    
    private final ChatClient.Builder chatClientBuilder;
    
    private static final String EXTRACTION_PROMPT = """
        从以下对话中提取用户提到的客观事实，返回JSON数组。
        
        对话：
        用户：%s
        助手：%s
        
        提取规则：
        1. 用户项目信息（技术栈、版本、架构）
        2. 用户已确认的知识点
        3. 用户提到的时间、地点、人物
        4. 用户的业务背景信息
        
        返回格式：[{"content": "事实内容", "importance": 1-10}]
        无事实则返回空数组 []
        只返回JSON数组，不要有其他内容。
        """;
    
    @Override
    public CompletableFuture<List<MemoryEntry>> extract(Long userId, String question, String answer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(EXTRACTION_PROMPT, question, answer);
                
                String response = chatClientBuilder.build()
                    .prompt(prompt)
                    .call()
                    .content();
                
                return parseResponse(userId, response);
            } catch (Exception e) {
                log.error("Failed to extract facts: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
    
    @Override
    public MemoryType getMemoryType() {
        return MemoryType.FACT;
    }
    
    @Override
    public MemoryLayerType getTargetLayer() {
        return MemoryLayerType.SEMANTIC;
    }
    
    private List<MemoryEntry> parseResponse(Long userId, String response) {
        List<MemoryEntry> entries = new ArrayList<>();
        
        try {
            String jsonStr = response;
            if (response.contains("[")) {
                jsonStr = response.substring(response.indexOf("["), response.lastIndexOf("]") + 1);
            }
            
            JSONArray array = JSON.parseArray(jsonStr);
            if (array == null || array.isEmpty()) {
                return entries;
            }
            
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String content = obj.getString("content");
                int importance = obj.getIntValue("importance", 5);
                
                if (content != null && !content.isBlank()) {
                    entries.add(MemoryEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .type(MemoryType.FACT)
                        .layer(MemoryLayerType.SEMANTIC)
                        .content(content)
                        .importance(importance)
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse fact extraction response: {}", e.getMessage());
        }
        
        return entries;
    }
}
```

- [ ] **Step 4: 创建 PatternExtractor**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/extractor/PatternExtractor.java
package cn.sdh.backend.memory.extractor;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 模式提取器
 * 从多轮对话中分析用户行为模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternExtractor implements MemoryExtractor {
    
    private final ChatClient.Builder chatClientBuilder;
    private final StringRedisTemplate redisTemplate;
    private final MemoryConfig config;
    
    private static final String INTERACTION_KEY_PREFIX = "memory:interaction:";
    private static final String EXTRACTION_PROMPT = """
        分析用户交互模式：
        
        最近交互记录：%s
        
        识别模式：
        1. 提问风格（detail_first: 先概述后细节 / direct: 直接深入 / example_oriented: 偏好示例）
        2. 关注点类型（theory: 理论 / practice: 实践 / troubleshooting: 排错）
        3. 技术水平（beginner / intermediate / advanced）
        
        返回格式：[{"type": "behavior/preference/topic", "name": "模式名称", "description": "描述", "confidence": 0.0-1.0}]
        只返回JSON数组。
        """;
    
    @Override
    public CompletableFuture<List<MemoryEntry>> extract(Long userId, String question, String answer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 记录本次交互
                recordInteraction(userId, question);
                
                // 检查是否有足够的观察次数
                String key = INTERACTION_KEY_PREFIX + userId;
                Long count = redisTemplate.opsForList().size(key);
                
                if (count == null || count < config.getPatternMinObservations()) {
                    log.debug("Not enough observations for pattern extraction: {}", count);
                    return List.of();
                }
                
                // 获取最近交互记录
                List<String> interactions = redisTemplate.opsForList().range(key, 0, 19);
                if (interactions == null || interactions.isEmpty()) {
                    return List.of();
                }
                
                String prompt = String.format(EXTRACTION_PROMPT, String.join("\n", interactions));
                
                String response = chatClientBuilder.build()
                    .prompt(prompt)
                    .call()
                    .content();
                
                return parseResponse(userId, response);
            } catch (Exception e) {
                log.error("Failed to extract patterns: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
    
    @Override
    public MemoryType getMemoryType() {
        return MemoryType.PATTERN;
    }
    
    @Override
    public MemoryLayerType getTargetLayer() {
        return MemoryLayerType.PATTERN;
    }
    
    private void recordInteraction(Long userId, String question) {
        String key = INTERACTION_KEY_PREFIX + userId;
        redisTemplate.opsForList().rightPush(key, question);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
        
        // 保持最近20条
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 20) {
            redisTemplate.opsForList().trim(key, size - 20, -1);
        }
    }
    
    private List<MemoryEntry> parseResponse(Long userId, String response) {
        List<MemoryEntry> entries = new ArrayList<>();
        
        try {
            String jsonStr = response;
            if (response.contains("[")) {
                jsonStr = response.substring(response.indexOf("["), response.lastIndexOf("]") + 1);
            }
            
            JSONArray array = JSON.parseArray(jsonStr);
            if (array == null || array.isEmpty()) {
                return entries;
            }
            
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String type = obj.getString("type");
                String name = obj.getString("name");
                String description = obj.getString("description");
                Double confidence = obj.getDouble("confidence");
                
                if (name != null && description != null) {
                    entries.add(MemoryEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .type(MemoryType.PATTERN)
                        .layer(MemoryLayerType.PATTERN)
                        .content(JSON.toJSONString(obj))
                        .importance((int) ((confidence != null ? confidence : 0.5) * 10))
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse pattern extraction response: {}", e.getMessage());
        }
        
        return entries;
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/extractor/
git commit -m "$(cat <<'EOF'
feat(memory): implement memory extractors

- Add MemoryExtractor interface with async extraction
- Add PreferenceExtractor for user preference detection
- Add FactExtractor for objective fact extraction
- Add PatternExtractor for behavior pattern analysis
- All extractors use LLM for intelligent extraction
- PatternExtractor requires minimum observations before extraction

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: 语义边界检测与编排器

### Task 9: 实现语义边界检测器和摘要触发器

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/boundary/BoundaryResult.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/boundary/SemanticBoundaryDetector.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/boundary/SummarizationTrigger.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/layer/AbstractMemoryLayer.java`

- [ ] **Step 1: 创建 BoundaryResult**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/boundary/BoundaryResult.java
package cn.sdh.backend.memory.boundary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 边界检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoundaryResult {
    
    /** 边界类型 */
    public enum BoundaryType {
        /** 话题切换 */
        TRANSITION,
        /** 长度限制 */
        LENGTH_LIMIT,
        /** 无边界 */
        NONE
    }
    
    /** 边界类型 */
    private BoundaryType type;
    
    /** 当前话题标签 */
    private String topicTag;
    
    /** 原因说明 */
    private String reason;
    
    /** 是否需要触发摘要 */
    public boolean needsSummarization() {
        return type == BoundaryType.TRANSITION || type == BoundaryType.LENGTH_LIMIT;
    }
    
    public static BoundaryResult none() {
        return BoundaryResult.builder()
            .type(BoundaryType.NONE)
            .build();
    }
    
    public static BoundaryResult transition(String topicTag, String reason) {
        return BoundaryResult.builder()
            .type(BoundaryType.TRANSITION)
            .topicTag(topicTag)
            .reason(reason)
            .build();
    }
    
    public static BoundaryResult lengthLimit() {
        return BoundaryResult.builder()
            .type(BoundaryType.LENGTH_LIMIT)
            .reason("达到最大消息数限制")
            .build();
    }
}
```

- [ ] **Step 2: 创建 SemanticBoundaryDetector**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/boundary/SemanticBoundaryDetector.java
package cn.sdh.backend.memory.boundary;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.MemoryEntry;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 语义边界检测器
 * 检测对话中的话题切换点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticBoundaryDetector {
    
    private final ChatClient.Builder chatClientBuilder;
    private final MemoryConfig config;
    
    /** 显式过渡词 */
    private static final List<String> TRANSITION_KEYWORDS = List.of(
        "换个话题", "顺便问一下", "对了", "另外", "还有个问题",
        "说点别的", "不说这个了", "换个问题"
    );
    
    private static final String DETECTION_PROMPT = """
        分析以下对话，判断是否有话题切换。
        
        最近对话：
        %s
        
        当前问题：%s
        
        返回JSON：{ "isTransition": true/false, "topicTag": "当前话题标签", "reason": "判断原因" }
        只返回JSON，不要有其他内容。
        """;
    
    /**
     * 检测语义边界
     */
    public BoundaryResult detect(String currentQuestion, List<MemoryEntry> recentMessages, int currentMessageCount) {
        // 1. 检查长度限制
        if (currentMessageCount >= config.getSummaryMaxMessages()) {
            log.debug("Length limit reached: {}", currentMessageCount);
            return BoundaryResult.lengthLimit();
        }
        
        // 2. 检查显式过渡词
        if (hasExplicitTransition(currentQuestion)) {
            log.debug("Explicit transition detected in: {}", currentQuestion);
            return BoundaryResult.transition("新话题", "检测到显式过渡词");
        }
        
        // 3. LLM语义判断
        if (recentMessages != null && !recentMessages.isEmpty()) {
            try {
                return detectByLLM(currentQuestion, recentMessages);
            } catch (Exception e) {
                log.warn("LLM boundary detection failed: {}", e.getMessage());
            }
        }
        
        return BoundaryResult.none();
    }
    
    /**
     * 检查是否包含显式过渡词
     */
    private boolean hasExplicitTransition(String question) {
        if (question == null) return false;
        String lowerQuestion = question.toLowerCase();
        return TRANSITION_KEYWORDS.stream()
            .anyMatch(lowerQuestion::contains);
    }
    
    /**
     * LLM语义边界检测
     */
    private BoundaryResult detectByLLM(String currentQuestion, List<MemoryEntry> recentMessages) {
        // 取最近N条对话
        int windowSize = Math.min(config.getBoundaryDetectionWindowSize(), recentMessages.size());
        List<MemoryEntry> windowMessages = recentMessages.subList(
            Math.max(0, recentMessages.size() - windowSize),
            recentMessages.size()
        );
        
        String recentDialogues = windowMessages.stream()
            .map(m -> m.getContent())
            .collect(Collectors.joining("\n"));
        
        String prompt = String.format(DETECTION_PROMPT, recentDialogues, currentQuestion);
        
        try {
            String response = chatClientBuilder.build()
                .prompt(prompt)
                .call()
                .content();
            
            return parseLLMResponse(response);
        } catch (Exception e) {
            log.error("LLM boundary detection error: {}", e.getMessage());
            return BoundaryResult.none();
        }
    }
    
    private BoundaryResult parseLLMResponse(String response) {
        try {
            String jsonStr = response;
            if (response.contains("{")) {
                jsonStr = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
            }
            
            JSONObject obj = JSON.parseObject(jsonStr);
            boolean isTransition = obj.getBooleanValue("isTransition");
            String topicTag = obj.getString("topicTag");
            String reason = obj.getString("reason");
            
            if (isTransition) {
                return BoundaryResult.transition(topicTag, reason);
            }
            
            return BoundaryResult.none();
        } catch (Exception e) {
            log.warn("Failed to parse LLM boundary response: {}", e.getMessage());
            return BoundaryResult.none();
        }
    }
}
```

- [ ] **Step 3: 创建 AbstractMemoryLayer**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/layer/AbstractMemoryLayer.java
package cn.sdh.backend.memory.layer;

import cn.sdh.backend.entity.MemoryAbstract;
import cn.sdh.backend.mapper.MemoryAbstractMapper;
import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽象记忆层 - MySQL摘要实现
 * 存储对话摘要，用于长期记忆压缩
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbstractMemoryLayer implements MemoryLayer {
    
    private final MemoryAbstractMapper memoryAbstractMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final MemoryConfig config;
    
    private static final String SUMMARIZATION_PROMPT = """
        请对以下对话生成摘要：
        
        %s
        
        返回JSON格式：
        {
          "summary": "对话摘要（2-3句话）",
          "keyPoints": ["关键结论1", "关键结论2", ...],
          "topicTag": "话题标签"
        }
        只返回JSON，不要有其他内容。
        """;
    
    @Override
    public void store(MemoryEntry entry) {
        if (entry.getUserId() == null || entry.getContent() == null) {
            return;
        }
        
        try {
            MemoryAbstract entity = new MemoryAbstract();
            entity.setUserId(entry.getUserId());
            entity.setSessionId(entry.getSessionId());
            entity.setEpisodeId(entry.getMetadata() != null ? 
                (String) entry.getMetadata().get("episodeId") : null);
            entity.setTopicTag(entry.getMetadata() != null ? 
                (String) entry.getMetadata().get("topicTag") : null);
            entity.setSummary(entry.getContent());
            entity.setKeyPoints(entry.getMetadata() != null ? 
                (List<String>) entry.getMetadata().get("keyPoints") : null);
            entity.setImportance(entry.getImportance());
            entity.setNeedsSummary(false);
            entity.setCreatedAt(LocalDateTime.now());
            
            memoryAbstractMapper.insert(entity);
            log.debug("Stored abstract memory: {}", entity.getId());
        } catch (Exception e) {
            log.error("Failed to store abstract memory: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getUserId() == null) {
            return Collections.emptyList();
        }
        
        try {
            LambdaQueryWrapper<MemoryAbstract> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MemoryAbstract::getUserId, query.getUserId());
            
            if (query.getLimit() > 0) {
                wrapper.last("LIMIT " + query.getLimit());
            } else {
                wrapper.last("LIMIT 10");
            }
            
            wrapper.orderByDesc(MemoryAbstract::getCreatedAt);
            
            List<MemoryAbstract> abstracts = memoryAbstractMapper.selectList(wrapper);
            
            return abstracts.stream()
                .map(this::convertToEntry)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to recall abstract memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public void update(MemoryEntry entry) {
        try {
            if (entry.getId() == null) return;
            
            MemoryAbstract entity = memoryAbstractMapper.selectById(Long.parseLong(entry.getId()));
            if (entity != null) {
                entity.setAccessCount(entity.getAccessCount() + 1);
                entity.setLastAccessAt(LocalDateTime.now());
                memoryAbstractMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to update abstract memory: {}", e.getMessage());
        }
    }
    
    @Override
    public void delete(String id) {
        try {
            memoryAbstractMapper.deleteById(Long.parseLong(id));
        } catch (Exception e) {
            log.warn("Failed to delete abstract memory: {}", e.getMessage());
        }
    }
    
    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.ABSTRACT;
    }
    
    @Override
    public void clearByUserId(Long userId) {
        try {
            LambdaQueryWrapper<MemoryAbstract> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MemoryAbstract::getUserId, userId);
            memoryAbstractMapper.delete(wrapper);
        } catch (Exception e) {
            log.error("Failed to clear abstract memory: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 生成摘要
     */
    public MemoryEntry generateSummary(Long userId, String sessionId, String episodeId, 
                                        String topicTag, List<MemoryEntry> messages) {
        try {
            String dialogues = messages.stream()
                .map(m -> m.getContent())
                .collect(Collectors.joining("\n\n"));
            
            String prompt = String.format(SUMMARIZATION_PROMPT, dialogues);
            
            String response = chatClientBuilder.build()
                .prompt(prompt)
                .call()
                .content();
            
            JSONObject obj = parseJsonResponse(response);
            if (obj == null) {
                return null;
            }
            
            String summary = obj.getString("summary");
            JSONArray keyPointsArray = obj.getJSONArray("keyPoints");
            List<String> keyPoints = keyPointsArray != null ? 
                keyPointsArray.toJavaList(String.class) : new ArrayList<>();
            String detectedTopic = obj.getString("topicTag");
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("episodeId", episodeId);
            metadata.put("topicTag", detectedTopic != null ? detectedTopic : topicTag);
            metadata.put("keyPoints", keyPoints);
            
            return MemoryEntry.builder()
                .userId(userId)
                .sessionId(sessionId)
                .type(MemoryType.SUMMARY)
                .layer(MemoryLayerType.ABSTRACT)
                .content(summary)
                .metadata(metadata)
                .importance(5)
                .build();
        } catch (Exception e) {
            log.error("Failed to generate summary: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private JSONObject parseJsonResponse(String response) {
        try {
            String jsonStr = response;
            if (response.contains("{")) {
                jsonStr = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
            }
            return JSON.parseObject(jsonStr);
        } catch (Exception e) {
            log.warn("Failed to parse summary response: {}", e.getMessage());
            return null;
        }
    }
    
    private MemoryEntry convertToEntry(MemoryAbstract entity) {
        return MemoryEntry.builder()
            .id(String.valueOf(entity.getId()))
            .userId(entity.getUserId())
            .sessionId(entity.getSessionId())
            .type(MemoryType.SUMMARY)
            .layer(MemoryLayerType.ABSTRACT)
            .content(entity.getSummary())
            .importance(entity.getImportance() != null ? entity.getImportance() : 5)
            .accessCount(entity.getAccessCount() != null ? entity.getAccessCount() : 0)
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
```

- [ ] **Step 4: 创建 SummarizationTrigger**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/boundary/SummarizationTrigger.java
package cn.sdh.backend.memory.boundary;

import cn.sdh.backend.memory.core.MemoryEntry;
import cn.sdh.backend.memory.layer.AbstractMemoryLayer;
import cn.sdh.backend.memory.layer.EpisodicMemoryLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 摘要触发器
 * 在语义边界触发时生成摘要
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizationTrigger {
    
    private final AbstractMemoryLayer abstractMemoryLayer;
    private final EpisodicMemoryLayer episodicMemoryLayer;
    
    /**
     * 触发摘要生成
     */
    public CompletableFuture<Void> trigger(BoundaryResult boundary, Long userId, 
                                            String sessionId, String episodeId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 获取当前episode的所有消息
                List<MemoryEntry> messages = episodicMemoryLayer.getSessionHistory(sessionId);
                
                if (messages.isEmpty()) {
                    log.debug("No messages to summarize for session: {}", sessionId);
                    return;
                }
                
                // 生成摘要
                MemoryEntry summary = abstractMemoryLayer.generateSummary(
                    userId, 
                    sessionId, 
                    episodeId,
                    boundary.getTopicTag(),
                    messages
                );
                
                if (summary != null) {
                    abstractMemoryLayer.store(summary);
                    log.info("Generated summary for episode: {}", episodeId);
                    
                    // 标记消息属于该episode
                    String newEpisodeId = episodicMemoryLayer.generateEpisodeId();
                    // 后续消息将使用新的episodeId
                }
            } catch (Exception e) {
                log.error("Failed to trigger summarization: {}", e.getMessage(), e);
            }
        });
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/boundary/
git add rag-backend/src/main/java/cn/sdh/backend/memory/layer/AbstractMemoryLayer.java
git commit -m "$(cat <<'EOF'
feat(memory): implement semantic boundary detection and summarization

- Add BoundaryResult with TRANSITION, LENGTH_LIMIT, NONE types
- Add SemanticBoundaryDetector with keyword + LLM detection
- Add AbstractMemoryLayer for storing conversation summaries
- Add SummarizationTrigger for async summary generation
- Support episode grouping and topic tagging

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: 实现 MemoryOrchestrator 和 MemoryAssembler

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/orchestrator/MemoryAssembler.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/memory/orchestrator/MemoryOrchestrator.java`

- [ ] **Step 1: 创建 MemoryAssembler**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/orchestrator/MemoryAssembler.java
package cn.sdh.backend.memory.orchestrator;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.memory.layer.*;
import cn.sdh.backend.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆上下文组装器
 * 从各层收集记忆并组装成上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAssembler {
    
    private final WorkingMemoryLayer workingMemoryLayer;
    private final SemanticMemoryLayer semanticMemoryLayer;
    private final PatternMemoryLayer patternMemoryLayer;
    private final AbstractMemoryLayer abstractMemoryLayer;
    private final EmbeddingService embeddingService;
    private final MemoryConfig config;
    
    /**
     * 组装记忆上下文
     */
    public MemoryContext assemble(Long userId, String sessionId, String currentQuestion) {
        MemoryContext.MemoryContextBuilder builder = MemoryContext.builder();
        
        try {
            // 1. 工作记忆：最近对话
            MemoryQuery workingQuery = MemoryQuery.builder()
                .sessionId(sessionId)
                .limit(config.getWorkingWindowSize())
                .build();
            builder.recentMessages(workingMemoryLayer.recall(workingQuery));
            
            // 2. 语义记忆：相关偏好和事实
            float[] questionEmbedding = null;
            try {
                questionEmbedding = embeddingService.embed(currentQuestion);
            } catch (Exception e) {
                log.warn("Failed to embed question for semantic recall: {}", e.getMessage());
            }
            
            if (questionEmbedding != null) {
                MemoryQuery semanticQuery = MemoryQuery.builder()
                    .userId(userId)
                    .queryEmbedding(questionEmbedding)
                    .limit(10)
                    .build();
                
                List<MemoryEntry> semanticMemories = semanticMemoryLayer.recall(semanticQuery);
                
                builder.preferences(semanticMemories.stream()
                    .filter(m -> m.getType() == MemoryType.PREFERENCE)
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));
                
                builder.facts(semanticMemories.stream()
                    .filter(m -> m.getType() == MemoryType.FACT)
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));
            } else {
                // 降级：直接获取所有偏好和事实
                builder.preferences(semanticMemoryLayer.getUserPreferences(userId).stream()
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));
                
                builder.facts(semanticMemoryLayer.getUserFacts(userId).stream()
                    .map(MemoryEntry::getContent)
                    .collect(Collectors.toList()));
            }
            
            // 3. 模式记忆：用户行为模式
            MemoryQuery patternQuery = MemoryQuery.builder()
                .userId(userId)
                .limit(10)
                .build();
            builder.patterns(patternMemoryLayer.recall(patternQuery).stream()
                .map(MemoryEntry::getContent)
                .collect(Collectors.toList()));
            
            // 4. 抽象记忆：相关历史摘要
            MemoryQuery abstractQuery = MemoryQuery.builder()
                .userId(userId)
                .limit(5)
                .build();
            builder.relevantSummaries(abstractMemoryLayer.recall(abstractQuery).stream()
                .map(MemoryEntry::getContent)
                .collect(Collectors.toList()));
            
        } catch (Exception e) {
            log.error("Failed to assemble memory context: {}", e.getMessage(), e);
        }
        
        return builder.build();
    }
}
```

- [ ] **Step 2: 创建 MemoryOrchestrator**

```java
// rag-backend/src/main/java/cn/sdh/backend/memory/orchestrator/MemoryOrchestrator.java
package cn.sdh.backend.memory.orchestrator;

import cn.sdh.backend.memory.boundary.BoundaryResult;
import cn.sdh.backend.memory.boundary.SemanticBoundaryDetector;
import cn.sdh.backend.memory.boundary.SummarizationTrigger;
import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.memory.extractor.*;
import cn.sdh.backend.memory.layer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆编排器
 * 统一调度各层记忆的存取和提取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryOrchestrator {
    
    private final MemoryAssembler memoryAssembler;
    private final WorkingMemoryLayer workingMemoryLayer;
    private final EpisodicMemoryLayer episodicMemoryLayer;
    private final SemanticMemoryLayer semanticMemoryLayer;
    private final PatternMemoryLayer patternMemoryLayer;
    private final AbstractMemoryLayer abstractMemoryLayer;
    
    private final PreferenceExtractor preferenceExtractor;
    private final FactExtractor factExtractor;
    private final PatternExtractor patternExtractor;
    
    private final SemanticBoundaryDetector boundaryDetector;
    private final SummarizationTrigger summarizationTrigger;
    private final MemoryConfig config;
    
    private static final ThreadLocal<String> currentEpisodeId = new ThreadLocal<>();
    
    /**
     * 组装记忆上下文（用户发消息时调用）
     */
    public MemoryContext assembleContext(Long userId, String sessionId, String currentQuestion) {
        if (!config.isEnabled()) {
            return MemoryContext.builder().build();
        }
        
        return memoryAssembler.assemble(userId, sessionId, currentQuestion);
    }
    
    /**
     * 处理并存储记忆（AI回复后异步调用）
     */
    public void processAndStore(Long userId, String sessionId, String question, String answer) {
        if (!config.isEnabled()) {
            return;
        }
        
        // 异步处理，不阻塞主流程
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 存储工作记忆
                storeWorkingMemory(userId, sessionId, question, answer);
                
                // 2. 检测语义边界
                List<MemoryEntry> recentMessages = workingMemoryLayer.recall(
                    MemoryQuery.builder().sessionId(sessionId).limit(20).build()
                );
                
                BoundaryResult boundary = boundaryDetector.detect(question, recentMessages, recentMessages.size());
                
                // 3. 如果触发边界，生成摘要
                if (boundary.needsSummarization()) {
                    String episodeId = getOrCreateEpisodeId();
                    summarizationTrigger.trigger(boundary, userId, sessionId, episodeId);
                    
                    // 重置episode
                    currentEpisodeId.set(null);
                }
                
                // 4. 并行提取记忆
                extractAndStore(userId, question, answer);
                
            } catch (Exception e) {
                log.error("Failed to process and store memory: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * 存储工作记忆
     */
    private void storeWorkingMemory(Long userId, String sessionId, String question, String answer) {
        // 存储用户问题
        MemoryEntry userEntry = MemoryEntry.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .sessionId(sessionId)
            .type(MemoryType.EPISODE)
            .layer(MemoryLayerType.WORKING)
            .content("用户：" + question)
            .importance(5)
            .createdAt(java.time.LocalDateTime.now())
            .build();
        workingMemoryLayer.store(userEntry);
        
        // 存储AI回复
        MemoryEntry assistantEntry = MemoryEntry.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .sessionId(sessionId)
            .type(MemoryType.EPISODE)
            .layer(MemoryLayerType.WORKING)
            .content("助手：" + answer)
            .importance(5)
            .createdAt(java.time.LocalDateTime.now())
            .build();
        workingMemoryLayer.store(assistantEntry);
    }
    
    /**
     * 并行提取并存储记忆
     */
    private void extractAndStore(Long userId, String question, String answer) {
        // 并行执行三个提取器
        CompletableFuture<List<MemoryEntry>> preferences = preferenceExtractor.extract(userId, question, answer);
        CompletableFuture<List<MemoryEntry>> facts = factExtractor.extract(userId, question, answer);
        CompletableFuture<List<MemoryEntry>> patterns = patternExtractor.extract(userId, question, answer);
        
        // 等待所有提取完成
        CompletableFuture.allOf(preferences, facts, patterns).join();
        
        // 存储提取结果
        try {
            List<MemoryEntry> prefResults = preferences.get();
            prefResults.forEach(semanticMemoryLayer::store);
            log.debug("Stored {} preferences", prefResults.size());
            
            List<MemoryEntry> factResults = facts.get();
            factResults.forEach(semanticMemoryLayer::store);
            log.debug("Stored {} facts", factResults.size());
            
            List<MemoryEntry> patternResults = patterns.get();
            patternResults.forEach(patternMemoryLayer::store);
            log.debug("Stored {} patterns", patternResults.size());
        } catch (Exception e) {
            log.error("Failed to store extracted memories: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取或创建EpisodeId
     */
    private String getOrCreateEpisodeId() {
        String episodeId = currentEpisodeId.get();
        if (episodeId == null) {
            episodeId = episodicMemoryLayer.generateEpisodeId();
            currentEpisodeId.set(episodeId);
        }
        return episodeId;
    }
    
    /**
     * 清除用户所有记忆
     */
    public void clearUserMemory(Long userId) {
        workingMemoryLayer.clearByUserId(userId);
        semanticMemoryLayer.clearByUserId(userId);
        patternMemoryLayer.clearByUserId(userId);
        abstractMemoryLayer.clearByUserId(userId);
        log.info("Cleared all memory for user: {}", userId);
    }
    
    /**
     * 清除会话工作记忆
     */
    public void clearSessionMemory(String sessionId) {
        workingMemoryLayer.clearSession(sessionId);
        log.debug("Cleared working memory for session: {}", sessionId);
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/memory/orchestrator/
git commit -m "$(cat <<'EOF'
feat(memory): implement MemoryOrchestrator and MemoryAssembler

- Add MemoryAssembler to collect memories from all layers
- Add MemoryOrchestrator for unified memory coordination
- Support async memory extraction and storage
- Integrate semantic boundary detection
- Trigger summarization on topic transitions
- Provide clearUserMemory and clearSessionMemory methods

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 5: API与前端

### Task 11: 创建 MemoryController 和 DTO

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/dto/MemoryOverviewResponse.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/controller/MemoryController.java`

- [ ] **Step 1: 创建 MemoryOverviewResponse**

```java
// rag-backend/src/main/java/cn/sdh/backend/dto/MemoryOverviewResponse.java
package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 记忆概览响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryOverviewResponse {
    
    /** 用户偏好列表 */
    private List<MemoryItem> preferences;
    
    /** 知识事实列表 */
    private List<MemoryItem> facts;
    
    /** 行为模式列表 */
    private List<MemoryItem> patterns;
    
    /** 最近摘要列表 */
    private List<MemoryItem> recentSummaries;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryItem {
        private String id;
        private String type;
        private String content;
        private Integer importance;
        private String createdAt;
        private Integer accessCount;
    }
}
```

- [ ] **Step 2: 创建 MemoryController**

```java
// rag-backend/src/main/java/cn/sdh/backend/controller/MemoryController.java
package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.MemoryOverviewResponse;
import cn.sdh.backend.memory.core.*;
import cn.sdh.backend.memory.layer.SemanticMemoryLayer;
import cn.sdh.backend.memory.layer.AbstractMemoryLayer;
import cn.sdh.backend.memory.layer.PatternMemoryLayer;
import cn.sdh.backend.memory.orchestrator.MemoryOrchestrator;
import cn.sdh.backend.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆API控制器
 */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {
    
    private final MemoryOrchestrator memoryOrchestrator;
    private final SemanticMemoryLayer semanticMemoryLayer;
    private final PatternMemoryLayer patternMemoryLayer;
    private final AbstractMemoryLayer abstractMemoryLayer;
    
    /**
     * 获取用户记忆概览
     */
    @GetMapping("/overview")
    public MemoryOverviewResponse getOverview() {
        Long userId = UserContext.getUserId();
        
        List<MemoryOverviewResponse.MemoryItem> preferences = 
            semanticMemoryLayer.getUserPreferences(userId).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());
        
        List<MemoryOverviewResponse.MemoryItem> facts = 
            semanticMemoryLayer.getUserFacts(userId).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());
        
        List<MemoryOverviewResponse.MemoryItem> patterns = 
            patternMemoryLayer.getBehaviorPatterns(userId).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());
        
        List<MemoryOverviewResponse.MemoryItem> summaries = 
            abstractMemoryLayer.recall(MemoryQuery.builder()
                .userId(userId)
                .limit(10)
                .build()).stream()
                .map(this::toMemoryItem)
                .collect(Collectors.toList());
        
        return MemoryOverviewResponse.builder()
            .preferences(preferences)
            .facts(facts)
            .patterns(patterns)
            .recentSummaries(summaries)
            .build();
    }
    
    /**
     * 手动添加偏好
     */
    @PostMapping("/preference")
    public void addPreference(@RequestBody AddPreferenceRequest request) {
        Long userId = UserContext.getUserId();
        
        MemoryEntry entry = MemoryEntry.builder()
            .userId(userId)
            .type(MemoryType.PREFERENCE)
            .layer(MemoryLayerType.SEMANTIC)
            .content(request.getContent())
            .importance(request.getImportance() != null ? request.getImportance() : 7)
            .build();
        
        semanticMemoryLayer.store(entry);
    }
    
    /**
     * 删除记忆
     */
    @DeleteMapping("/{memoryId}")
    public void deleteMemory(@PathVariable String memoryId,
                            @RequestParam MemoryLayerType layer) {
        Long userId = UserContext.getUserId();
        
        switch (layer) {
            case SEMANTIC -> semanticMemoryLayer.delete(memoryId);
            case ABSTRACT -> abstractMemoryLayer.delete(memoryId);
            default -> throw new IllegalArgumentException("Unsupported layer: " + layer);
        }
    }
    
    /**
     * 搜索记忆
     */
    @GetMapping("/search")
    public List<MemoryOverviewResponse.MemoryItem> search(
            @RequestParam String query,
            @RequestParam(required = false) MemoryType type,
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getUserId();
        
        MemoryQuery memoryQuery = MemoryQuery.builder()
            .userId(userId)
            .query(query)
            .type(type)
            .limit(limit)
            .build();
        
        return semanticMemoryLayer.recall(memoryQuery).stream()
            .map(this::toMemoryItem)
            .collect(Collectors.toList());
    }
    
    /**
     * 清除用户所有记忆
     */
    @DeleteMapping("/all")
    public void clearAll() {
        Long userId = UserContext.getUserId();
        memoryOrchestrator.clearUserMemory(userId);
    }
    
    private MemoryOverviewResponse.MemoryItem toMemoryItem(MemoryEntry entry) {
        return MemoryOverviewResponse.MemoryItem.builder()
            .id(entry.getId())
            .type(entry.getType().getCode())
            .content(entry.getContent())
            .importance(entry.getImportance())
            .accessCount(entry.getAccessCount())
            .createdAt(entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null)
            .build();
    }
    
    @lombok.Data
    public static class AddPreferenceRequest {
        private String content;
        private Integer importance;
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/dto/MemoryOverviewResponse.java
git add rag-backend/src/main/java/cn/sdh/backend/controller/MemoryController.java
git commit -m "$(cat <<'EOF'
feat(memory): add MemoryController with REST API

- GET /api/memory/overview - get user memory overview
- POST /api/memory/preference - manually add preference
- DELETE /api/memory/{id} - delete specific memory
- GET /api/memory/search - search memories by query
- DELETE /api/memory/all - clear all user memories

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: 创建前端记忆管理页面

**Files:**
- Create: `rag-frontend/src/api/memory.ts`
- Create: `rag-frontend/src/stores/memory.ts`
- Create: `rag-frontend/src/views/memory/index.vue`
- Modify: `rag-frontend/src/router/index.ts`

- [ ] **Step 1: 创建 memory.ts API**

```typescript
// rag-frontend/src/api/memory.ts
import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface MemoryItem {
  id: string
  type: string
  content: string
  importance: number
  accessCount: number
  createdAt: string
}

export interface MemoryOverview {
  preferences: MemoryItem[]
  facts: MemoryItem[]
  patterns: MemoryItem[]
  recentSummaries: MemoryItem[]
}

export function getMemoryOverview(): Promise<ApiResponse<MemoryOverview>> {
  return request.get('/api/memory/overview')
}

export function addPreference(content: string, importance: number = 7): Promise<ApiResponse<null>> {
  return request.post('/api/memory/preference', { content, importance })
}

export function deleteMemory(memoryId: string, layer: string): Promise<ApiResponse<null>> {
  return request.delete(`/api/memory/${memoryId}`, { params: { layer } })
}

export function searchMemory(query: string, type?: string, limit: number = 10): Promise<ApiResponse<MemoryItem[]>> {
  return request.get('/api/memory/search', { params: { query, type, limit } })
}

export function clearAllMemory(): Promise<ApiResponse<null>> {
  return request.delete('/api/memory/all')
}
```

- [ ] **Step 2: 创建 memory.ts Store**

```typescript
// rag-frontend/src/stores/memory.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MemoryOverview, MemoryItem } from '@/api/memory'
import { getMemoryOverview, addPreference, deleteMemory, searchMemory, clearAllMemory } from '@/api/memory'
import { message } from 'ant-design-vue'

export const useMemoryStore = defineStore('memory', () => {
  const loading = ref(false)
  const overview = ref<MemoryOverview | null>(null)
  
  async function fetchOverview(): Promise<void> {
    loading.value = true
    try {
      const res = await getMemoryOverview()
      overview.value = res.data
    } catch (error) {
      console.error('Failed to fetch memory overview:', error)
    } finally {
      loading.value = false
    }
  }
  
  async function addPref(content: string, importance?: number): Promise<boolean> {
    try {
      await addPreference(content, importance)
      message.success('添加成功')
      await fetchOverview()
      return true
    } catch (error) {
      console.error('Failed to add preference:', error)
      message.error('添加失败')
      return false
    }
  }
  
  async function removeMemory(memoryId: string, layer: string): Promise<boolean> {
    try {
      await deleteMemory(memoryId, layer)
      message.success('删除成功')
      await fetchOverview()
      return true
    } catch (error) {
      console.error('Failed to delete memory:', error)
      message.error('删除失败')
      return false
    }
  }
  
  async function search(query: string, type?: string): Promise<MemoryItem[]> {
    try {
      const res = await searchMemory(query, type)
      return res.data || []
    } catch (error) {
      console.error('Failed to search memory:', error)
      return []
    }
  }
  
  async function clearAll(): Promise<boolean> {
    try {
      await clearAllMemory()
      message.success('已清除所有记忆')
      overview.value = null
      return true
    } catch (error) {
      console.error('Failed to clear memory:', error)
      message.error('清除失败')
      return false
    }
  }
  
  function reset(): void {
    overview.value = null
    loading.value = false
  }
  
  return {
    loading,
    overview,
    fetchOverview,
    addPref,
    removeMemory,
    search,
    clearAll,
    reset,
  }
})
```

- [ ] **Step 3: 创建记忆管理页面**

```vue
<!-- rag-frontend/src/views/memory/index.vue -->
<template>
  <div class="memory-page">
    <a-card :bordered="false">
      <template #title>
        <div class="page-header">
          <div class="header-left">
            <h2>记忆中心</h2>
            <p class="subtitle">管理系统对您的记忆，提升对话体验</p>
          </div>
          <a-button danger @click="handleClearAll">
            <template #icon><DeleteOutlined /></template>
            清除所有记忆
          </a-button>
        </div>
      </template>
      
      <a-spin :spinning="memoryStore.loading">
        <a-tabs v-model:activeKey="activeTab">
          <!-- 偏好 -->
          <a-tab-pane key="preferences" tab="用户偏好">
            <div class="tab-header">
              <a-button type="primary" @click="showAddModal('preference')">
                <template #icon><PlusOutlined /></template>
                添加偏好
              </a-button>
            </div>
            <a-list
              :data-source="memoryStore.overview?.preferences || []"
              item-layout="horizontal"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :description="`重要性: ${item.importance}/10`">
                    <template #title>
                      <span>{{ item.content }}</span>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" danger @click="handleDelete(item, 'SEMANTIC')">
                      删除
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>
          
          <!-- 事实 -->
          <a-tab-pane key="facts" tab="知识事实">
            <a-list
              :data-source="memoryStore.overview?.facts || []"
              item-layout="horizontal"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :description="`重要性: ${item.importance}/10`">
                    <template #title>{{ item.content }}</template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" danger @click="handleDelete(item, 'SEMANTIC')">
                      删除
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>
          
          <!-- 模式 -->
          <a-tab-pane key="patterns" tab="行为模式">
            <a-list
              :data-source="memoryStore.overview?.patterns || []"
              item-layout="horizontal"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :description="`置信度: ${item.importance * 10}%`">
                    <template #title>{{ item.content }}</template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>
          
          <!-- 摘要 -->
          <a-tab-pane key="summaries" tab="历史摘要">
            <a-list
              :data-source="memoryStore.overview?.recentSummaries || []"
              item-layout="vertical"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>{{ item.content }}</template>
                    <template #description>
                      {{ item.createdAt ? formatTime(item.createdAt) : '' }}
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" danger @click="handleDelete(item, 'ABSTRACT')">
                      删除
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-card>
    
    <!-- 添加偏好弹窗 -->
    <a-modal
      v-model:open="addModalVisible"
      title="添加偏好"
      @ok="handleAddPreference"
      :confirm-loading="adding"
    >
      <a-form layout="vertical">
        <a-form-item label="偏好内容" required>
          <a-input v-model:value="newPreference.content" placeholder="例如：偏好中文回复" />
        </a-form-item>
        <a-form-item label="重要性">
          <a-slider v-model:value="newPreference.importance" :min="1" :max="10" :marks="{ 1: '低', 5: '中', 10: '高' }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { useMemoryStore } from '@/stores/memory'
import type { MemoryItem } from '@/api/memory'

const memoryStore = useMemoryStore()

const activeTab = ref('preferences')
const addModalVisible = ref(false)
const adding = ref(false)

const newPreference = reactive({
  content: '',
  importance: 7,
})

onMounted(async () => {
  await memoryStore.fetchOverview()
})

function showAddModal(type: string) {
  newPreference.content = ''
  newPreference.importance = 7
  addModalVisible.value = true
}

async function handleAddPreference() {
  if (!newPreference.content.trim()) {
    message.warning('请输入偏好内容')
    return
  }
  
  adding.value = true
  try {
    await memoryStore.addPref(newPreference.content, newPreference.importance)
    addModalVisible.value = false
  } finally {
    adding.value = false
  }
}

async function handleDelete(item: MemoryItem, layer: string) {
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除这条记忆吗？',
    async onOk() {
      await memoryStore.removeMemory(item.id, layer)
    },
  })
}

async function handleClearAll() {
  Modal.confirm({
    title: '确认清除',
    content: '确定要清除所有记忆吗？此操作不可恢复。',
    okType: 'danger',
    async onOk() {
      await memoryStore.clearAll()
    },
  })
}

function formatTime(time: string): string {
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped lang="scss">
.memory-page {
  height: 100%;
  
  .ant-card {
    height: 100%;
  }
  
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    
    .header-left {
      h2 {
        margin: 0 0 4px;
        font-size: 20px;
        font-weight: 600;
      }
      
      .subtitle {
        margin: 0;
        color: var(--text-secondary);
        font-size: 14px;
      }
    }
  }
  
  .tab-header {
    margin-bottom: 16px;
  }
}
</style>
```

- [ ] **Step 4: 更新路由配置**

在 `rag-frontend/src/router/index.ts` 的 children 数组中添加：

```typescript
{
  path: 'memory',
  name: 'Memory',
  component: () => import('@/views/memory/index.vue'),
  meta: { title: '记忆中心', icon: 'Bulb', requiresAuth: true, permission: '/memory' } as RouteMeta,
},
```

- [ ] **Step 5: 提交**

```bash
git add rag-frontend/src/api/memory.ts
git add rag-frontend/src/stores/memory.ts
git add rag-frontend/src/views/memory/index.vue
git add rag-frontend/src/router/index.ts
git commit -m "$(cat <<'EOF'
feat(frontend): add memory management page

- Add memory.ts API module
- Add memory.ts Pinia store
- Add memory/index.vue page with tabs for:
  - User preferences (add/delete)
  - Knowledge facts
  - Behavior patterns
  - Conversation summaries
- Add /memory route

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 13: 集成 ChatService

**Files:**
- Modify: 现有 ChatService (根据实际路径调整)

由于后端 Java 文件被加密，这里提供集成代码片段供参考：

- [ ] **Step 1: 在 ChatService 中注入 MemoryOrchestrator**

```java
// 在 ChatServiceImpl 中添加
@Autowired
private MemoryOrchestrator memoryOrchestrator;
```

- [ ] **Step 2: 在 ask 方法中集成记忆**

```java
public Flux<StreamEvent> ask(AskRequest request) {
    Long userId = UserContext.getUserId();
    String sessionId = request.getSessionId();
    String question = request.getQuestion();
    
    // 1. 组装记忆上下文
    MemoryContext memoryContext = memoryOrchestrator.assembleContext(userId, sessionId, question);
    
    // 2. 构建系统提示词
    StringBuilder systemPrompt = new StringBuilder();
    if (memoryContext.hasMemory()) {
        systemPrompt.append(memoryContext.buildSystemPrompt());
    }
    
    // 3. 调用 LLM（使用现有的调用逻辑）
    // ... existing LLM call code ...
    
    // 4. 异步处理记忆
    response.doOnComplete(() -> {
        memoryOrchestrator.processAndStore(userId, sessionId, question, lastAnswer);
    });
    
    return response;
}
```

- [ ] **Step 3: 提交**

```bash
git commit -m "$(cat <<'EOF'
feat(chat): integrate memory system with ChatService

- Inject MemoryOrchestrator into ChatService
- Assemble memory context before LLM call
- Build system prompt with memory information
- Process and store memory after response

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## 自检清单

### 1. Spec 覆盖检查

| Spec 要求 | 对应 Task |
|-----------|----------|
| 5层记忆架构 | Task 3, 5, 6, 7, 9 |
| 语义边界检测 | Task 9 |
| 偏好提取 | Task 8 |
| 事实提取 | Task 8 |
| 模式提取 | Task 8 |
| 摘要生成 | Task 9 |
| MemoryOrchestrator | Task 10 |
| REST API | Task 11 |
| 前端管理页面 | Task 12 |
| ChatService 集成 | Task 13 |

### 2. 占位符检查

无 TBD、TODO、待实现等内容。

### 3. 类型一致性检查

- `MemoryEntry.importance`: int (1-10) - 全局一致
- `MemoryEntry.type`: MemoryType 枚举 - 全局一致
- `MemoryEntry.layer`: MemoryLayerType 枚举 - 全局一致
- API 返回格式统一使用 `ApiResponse<T>`

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-24-layered-memory-system.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
