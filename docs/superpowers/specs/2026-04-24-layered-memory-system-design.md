# 分层记忆系统设计文档

> 创建日期：2026-04-24
> 状态：待实现

## 1. 概述

### 1.1 背景

SDH-RAG 当前已具备基础的对话存储（ChatSession、ChatHistory）和 Spring AI ChatMemory 机制，但仅支持简单的滑动窗口记忆，无法实现跨会话记忆和智能记忆抽象。

### 1.2 目标

1. **智能记忆抽象**：语义边界触发摘要，而非简单截断历史
2. **跨会话记忆**：记住用户偏好、知识事实、行为模式，下次对话自动生效

### 1.3 范围

- 记忆的存储、检索、衰减机制
- 语义边界检测与摘要生成
- 与现有 ChatService 的集成
- 前端记忆管理界面

---

## 2. 架构设计

### 2.1 整体架构

采用**分层记忆架构**，灵感来自认知心理学的人类记忆模型：

```
┌─────────────────────────────────────────────────────────────┐
│                    LLM (生成回复)                             │
└─────────────────────┬───────────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        │     Memory Orchestrator   │  ← 统一调度入口
        └─────────────┬─────────────┘
                      │
┌─────────┬───────────┼───────────┬─────────┬─────────────────────┐
│Working  │ Episodic  │  Semantic │ Pattern │   Abstract          │
│Memory   │ Memory    │  Memory   │ Memory  │   Memory            │
│(Redis)  │ (MySQL)   │  (ES向量) │ (Neo4j) │   (MySQL摘要表)     │
└─────────┴───────────┴───────────┴─────────┴─────────────────────┘
```

### 2.2 各层职责

| 层级 | 存储介质 | 作用 | 生命周期 |
|------|---------|------|---------|
| Working | Redis | 当前会话上下文，滑动窗口 | 会话结束后 1 小时 |
| Episodic | MySQL | 完整对话记录，按语义分块 | 永久（可归档） |
| Semantic | ES 向量 | 用户偏好/事实，向量检索 | 按重要性衰减 |
| Pattern | Neo4j | 用户行为模式，图谱关系 | 30 天未观察则衰减 |
| Abstract | MySQL | 语义分块摘要 | 180 天后归档 |

---

## 3. 核心组件设计

### 3.1 包结构

```
cn.sdh.backend.memory
├── core/                        # 核心接口与抽象
│   ├── MemoryLayer.java         # 记忆层接口
│   ├── MemoryEntry.java         # 记忆条目实体
│   └── MemoryContext.java       # 记忆上下文
│
├── layer/                       # 各层实现
│   ├── WorkingMemoryLayer.java  # Redis
│   ├── EpisodicMemoryLayer.java # MySQL
│   ├── SemanticMemoryLayer.java # ES 向量
│   ├── PatternMemoryLayer.java  # Neo4j
│   └── AbstractMemoryLayer.java # MySQL 摘要
│
├── orchestrator/                # 编排层
│   ├── MemoryOrchestrator.java
│   └── MemoryAssembler.java
│
├── extractor/                   # 提取器
│   ├── PreferenceExtractor.java
│   ├── FactExtractor.java
│   └── PatternExtractor.java
│
└── boundary/                    # 语义边界检测
    ├── SemanticBoundaryDetector.java
    └── SummarizationTrigger.java
```

### 3.2 核心接口

```java
public interface MemoryLayer {
    void store(MemoryEntry entry);
    List<MemoryEntry> recall(MemoryQuery query);
    MemoryLayerType getType();
}

public class MemoryEntry {
    private String id;
    private Long userId;
    private String sessionId;
    private MemoryLayerType layer;
    private MemoryType type;        // PREFERENCE / FACT / PATTERN / EPISODE
    private String content;
    private float[] embedding;      // 向量（ES层用）
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private int importance;         // 1-10
    private int accessCount;
}
```

### 3.3 MemoryOrchestrator

```java
public class MemoryOrchestrator {
    
    // 组装上下文（用户发消息时调用）
    public MemoryContext assembleContext(Long userId, String sessionId, String currentQuestion) {
        // 1. Working → 最近 N 轮对话
        // 2. Semantic → 相关偏好/事实
        // 3. Pattern → 用户行为模式
        // 4. Abstract → 历史摘要
    }
    
    // 处理并存储（AI 回复后异步调用）
    public void processAndStore(ChatExchange exchange) {
        // 1. 检测语义边界
        // 2. 并行提取 Preference / Fact / Pattern
        // 3. 写入各层存储
    }
}
```

---

## 4. 各层详细设计

### 4.1 Working Memory

| 项目 | 说明 |
|------|------|
| 存储 | Redis，key: `memory:working:{sessionId}` |
| 内容 | 最近 N 轮对话原文 |
| 策略 | 滑动窗口，FIFO |
| TTL | 会话结束后 1 小时 |
| 配置 | `rag.memory-window-size: 10` |

### 4.2 Episodic Memory

复用现有 `chat_history` 表，新增字段：

```sql
ALTER TABLE chat_history ADD COLUMN topic_tag VARCHAR(200) COMMENT '话题标签';
ALTER TABLE chat_history ADD COLUMN episode_id VARCHAR(100) COMMENT '情景块ID';
```

语义边界检测后，将连续对话标记为同一 `episode_id`。

### 4.3 Semantic Memory

新建 ES 索引 `sdh-memory-index`：

```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "userId": { "type": "long" },
      "type": { "type": "keyword" },
      "content": { "type": "text", "analyzer": "ik_max_word" },
      "embedding": { "type": "dense_vector", "dims": 1536, "similarity": "cosine" },
      "importance": { "type": "integer" },
      "accessCount": { "type": "integer" },
      "lastAccessAt": { "type": "date" },
      "createdAt": { "type": "date" }
    }
  }
}
```

去重策略：写入前检索同类记忆，余弦相似度 > 0.9 且 type 相同则更新。

### 4.4 Pattern Memory

Neo4j 图谱结构：

```cypher
// 用户节点
(u:User {id: 1})

// 行为模式
(u)-[:HAS_PATTERN]->(p:BehaviorPattern {
  name: "detail_driven",
  description: "习惯先问概述再追问细节",
  confidence: 0.85,
  observedCount: 5
})

// 偏好关系
(u)-[:PREFERS]->(pref:Preference {
  name: "response_style",
  value: "structured_with_code"
})

// 熟悉主题
(u)-[:FAMILIAR_WITH]->(t:Topic {
  name: "Spring AI",
  level: "advanced"
})
```

### 4.5 Abstract Memory

新建 MySQL 表：

```sql
CREATE TABLE memory_abstract (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id VARCHAR(100),
  episode_id VARCHAR(100),
  topic_tag VARCHAR(200) COMMENT '话题标签',
  summary TEXT COMMENT 'LLM 生成的摘要',
  key_points TEXT COMMENT 'JSON，关键结论列表',
  importance INT DEFAULT 5,
  access_count INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_topic (topic_tag),
  INDEX idx_episode (episode_id)
);
```

---

## 5. 语义边界检测

### 5.1 检测策略

采用 **LLM 双重判断** + **启发式规则** 结合：

1. **启发式规则（快速判断）**
   - 显式过渡词："换个话题"、"顺便问一下"
   - 滑动窗口累计消息数达到上限

2. **LLM 语义判断（异步）**
   - 检测最近 3 轮对话与当前问题的话题相关性
   - 使用轻量模型（qwen-turbo）降低延迟

### 5.2 边界类型

| 类型 | 触发条件 | 处理动作 |
|------|---------|---------|
| TRANSITION | 检测到话题切换 | 触发摘要，开始新 episode |
| LENGTH_LIMIT | 达到最大消息数 | 强制触发摘要 |
| NONE | 无边界 | 继续当前 episode |

### 5.3 LLM Prompt 模板

```
分析以下对话，判断是否有话题切换。

最近对话：{recent_dialogues}
当前问题：{current_question}

返回JSON：{ "isTransition": true/false, "topicTag": "当前话题", "reason": "原因" }
```

---

## 6. 记忆提取器

### 6.1 提取流程

```
ChatExchange
     │
     ├────────────────┬────────────────┐
     ↓                ↓                ↓
PreferenceExtractor FactExtractor PatternExtractor
     │                │                │
     ↓                ↓                ↓
ES (语义层)       ES (语义层)     Neo4j (模式层)
```

### 6.2 PreferenceExtractor

提取用户偏好：语言偏好、回复风格、技术栈等。

```
从对话中提取用户偏好，返回JSON数组：

对话：
用户：{question}
助手：{answer}

提取规则：
1. 语言偏好（中文/英文）
2. 回复风格偏好（简洁/详细/代码示例）
3. 技术栈偏好
4. 其他显式偏好表达

返回格式：[{ "type": "PREFERENCE", "content": "...", "importance": 1-10 }]
无偏好则返回空数组 []
```

### 6.3 FactExtractor

提取客观事实：项目信息、已确认知识点、时间地点等。

### 6.4 PatternExtractor

累积多轮对话（>=3 轮）后判断行为模式：
- 提问风格（先概述后细节 / 直接深入 / 偏好示例）
- 关注点类型（理论 / 实践 / 排错）
- 技术水平（beginner / intermediate / advanced）

---

## 7. 数据流

### 7.1 ASSEMBLE PHASE（组装上下文）

```
用户发送消息
     │
     ▼
Working Memory ──→ 最近 N 轮对话
Semantic Memory ─→ 向量检索相关偏好/事实
Pattern Memory  ─→ Cypher 查询行为模式
Abstract Memory ─→ 相关历史摘要
     │
     ▼
MemoryContext
     │
     ▼
LLM 生成回复
```

### 7.2 PROCESS PHASE（异步处理）

```
AI 回复完成
     │
     ├────────────────┬────────────────┐
     ↓                ↓                ↓
SemanticBoundary  Preference      Pattern
Detector          Extractor       Extractor
     │                │                │
     ↓                ↓                ↓
Summarizer        ES Store        Neo4j Update
     │
     ↓
MySQL Abstract Store
```

---

## 8. 记忆衰减策略

### 8.1 重要性计算

```
importance_score = base_importance × recency_factor × access_factor

recency_factor = e^(-0.05 × days_since_last_access)
access_factor  = 1 + log(1 + access_count) / 10
```

### 8.2 衰减规则

| 记忆类型 | 衰减周期 | 最低保留 | 清理策略 |
|---------|---------|---------|---------|
| 偏好 | 90 天 | importance >= 7 永久保留 | 90 天未访问且 importance < 5 → 清理 |
| 事实 | 60 天 | importance >= 8 永久保留 | 60 天未访问 → 重新验证 |
| 模式 | 30 天 | observedCount >= 5 永久保留 | 30 天未观察 → confidence 下降 |
| 摘要 | 180 天 | 无 | 180 天后归档 |

### 8.3 定时任务

复用现有 `scheduled_task` 机制，每天凌晨 3 点执行衰减计算。

---

## 9. 错误处理

| 场景 | 处理方式 |
|------|---------|
| ES 向量检索超时 | 降级为只返回 Working Memory |
| Neo4j 不可用 | 跳过 Pattern 层 |
| LLM 提取失败 | 记录日志，下次重试 |
| 摘要生成失败 | 保留原始对话，标记 `needs_summary=true` |
| 语义边界判断失败 | 默认无边界，继续当前 episode |

**核心原则**：记忆系统是增强功能，任何一层失败都不应阻塞正常对话。

---

## 10. 性能目标

| 操作 | 延迟目标 |
|------|---------|
| assembleContext | < 50ms |
| extract (单个) | < 2s（异步） |
| 语义边界检测 | < 500ms |
| 摘要生成 | < 5s（异步） |

---

## 11. API 设计

### 11.1 新增接口

```
GET  /api/memory/overview/{userId}    # 记忆概览
POST /api/memory/preference           # 手动添加偏好
DELETE /api/memory/{memoryId}         # 删除记忆
GET  /api/memory/search               # 记忆搜索
```

### 11.2 与 ChatService 集成

```java
public Flux<StreamEvent> ask(AskRequest request) {
    // 1. 组装记忆上下文
    MemoryContext ctx = memoryOrchestrator.assembleContext(...);
    
    // 2. 构建 Prompt
    String systemPrompt = buildSystemPrompt(ctx);
    
    // 3. 调用 LLM
    Flux<StreamEvent> response = chatClient.stream()...;
    
    // 4. 异步处理记忆
    response.doOnComplete(() -> {
        CompletableFuture.runAsync(() -> {
            memoryOrchestrator.processAndStore(...);
        });
    });
    
    return response;
}
```

---

## 12. 前端设计

### 12.1 记忆管理页面

路由：`/memory`

功能：
- 分类展示偏好、事实、模式、摘要
- 支持编辑、删除、手动添加
- 清理过期记忆按钮

### 12.2 聊天页面增强

- 记忆增强开关（默认开启）
- 展开面板显示当前召回的记忆条目

---

## 13. 测试策略

| 测试类型 | 覆盖范围 | 方式 |
|---------|---------|------|
| 单元测试 | MemoryLayer、Extractor | Mock LLM 和存储层 |
| 集成测试 | Orchestrator 完整流程 | Testcontainers |
| 语义边界测试 | 边界检测准确性 | 预设场景验证 |
| 性能测试 | assembleContext 延迟 | JMeter |
| 前端测试 | 记忆管理页面 | Vitest |

---

## 14. 实施计划

### Phase 1：基础框架
- 核心接口定义
- MemoryOrchestrator 骨架
- Working Memory 实现

### Phase 2：语义层
- ES 索引创建
- SemanticMemoryLayer 实现
- PreferenceExtractor / FactExtractor

### Phase 3：模式层
- Neo4j 图谱设计
- PatternMemoryLayer 实现
- PatternExtractor

### Phase 4：摘要机制
- SemanticBoundaryDetector
- AbstractMemoryLayer
- SummarizationTrigger

### Phase 5：集成与前端
- ChatService 集成
- 记忆管理页面
- 测试与优化
