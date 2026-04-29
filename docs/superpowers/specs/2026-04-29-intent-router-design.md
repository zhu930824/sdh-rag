# 智能路由系统设计文档

## 概述

实现智能路由系统：当用户未选择知识库时，自动判断用户query是否需要知识库检索，若需要则自动选择合适的知识库。

## 设计决策

| 决策项 | 选择 |
|--------|------|
| 触发时机 | 用户未选择知识库时自动触发 |
| 判断方式 | LLM意图判断 |
| 知识库选择 | LLM自动选择知识库 |
| 返回信息 | 显示路由详情 |

## 整体架构

```
用户发送请求 (knowledgeBaseId = null)
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│ ChatServiceImpl.chat() 处理逻辑                         │
│                                                         │
│ if (request.getKnowledgeBaseId() == null) {             │
│     // 触发智能路由                                      │
│     IntentRouterResponse route =                        │
│         intentRouterService.route(query, userId);       │
│                                                         │
│     if (!route.isNeedRetrieval()) {                     │
│         // 直接LLM回答                                   │
│         return directChat(query, route.getReason());    │
│     }                                                   │
│                                                         │
│     // 使用推荐的知识库进行RAG                           │
│     Long kbId = route.getTopKnowledgeBaseId();          │
│     return ragChat(query, kbId, route);                 │
│ }                                                       │
│                                                         │
│ // 用户已选择知识库，走现有流程                          │
│ return ragChat(query, request.getKnowledgeBaseId());    │
└─────────────────────────────────────────────────────────┘
```

## 新增文件

```
rag-backend/src/main/java/cn/sdh/backend/
├── service/
│   └── IntentRouterService.java          # 意图路由服务接口
├── service/impl/
│   └── IntentRouterServiceImpl.java      # 意图路由服务实现
├── dto/
│   ├── IntentRouterRequest.java          # 路由请求DTO
│   └── IntentRouterResponse.java         # 路由响应DTO
├── config/
│   └── IntentRouterConfig.java           # 配置类
```

## 核心组件设计

### 1. IntentRouterService 接口

```java
public interface IntentRouterService {
    /**
     * 分析用户查询意图，决定是否需要检索知识库
     * @param query 用户查询
     * @param userId 用户ID（用于获取用户偏好）
     * @return 路由决策结果
     */
    IntentRouterResponse route(String query, Long userId);
}
```

### 2. IntentRouterResponse DTO

```java
public class IntentRouterResponse {
    private boolean needRetrieval;           // 是否需要检索
    private List<KnowledgeBaseScore> recommendedBases; // 推荐知识库（按分数排序）
    private String reason;                   // 判断理由（用于前端展示）
    private String rewrittenQuery;           // 改写后的查询（可选）

    public static class KnowledgeBaseScore {
        private Long knowledgeBaseId;
        private String knowledgeBaseName;
        private double score;                // 相关度分数 0-1
    }

    // 便捷方法
    public Long getTopKnowledgeBaseId() {
        return recommendedBases != null && !recommendedBases.isEmpty()
            ? recommendedBases.get(0).getKnowledgeBaseId()
            : null;
    }

    public static IntentRouterResponse directChat(String reason) {
        IntentRouterResponse response = new IntentRouterResponse();
        response.setNeedRetrieval(false);
        response.setReason(reason);
        return response;
    }
}
```

### 3. LLM Prompt 设计

```
你是一个意图分析助手。请分析用户问题，判断是否需要从知识库检索信息。

## 用户问题
{query}

## 可用知识库列表
{knowledgeBases}

## 分析要求
1. 判断问题是否需要检索知识库：
   - 需要检索：问题涉及具体业务知识、技术文档、公司政策、产品信息等
   - 不需要检索：闲聊、通用常识、代码生成、创意写作、数学计算等

2. 如果需要检索，评估每个知识库的相关性（0-1分）：
   - 0.8-1.0：高度相关，问题直接对应知识库内容
   - 0.5-0.8：中度相关，问题可能涉及知识库内容
   - 0.0-0.5：低相关，问题与知识库内容关联不大

## 输出格式（仅输出JSON，不要其他内容）
{
  "needRetrieval": true,
  "reason": "问题涉及数据库配置，属于技术文档范畴",
  "recommendedBases": [
    {"knowledgeBaseId": 1, "knowledgeBaseName": "技术文档库", "score": 0.92},
    {"knowledgeBaseId": 2, "knowledgeBaseName": "产品知识库", "score": 0.31}
  ]
}
```

### 4. ChatResponse 增强

```java
public class ChatResponse {
    // 现有字段
    private String answer;
    private List<DocumentReference> sources;

    // 新增路由信息
    private RouterInfo routerInfo;

    public static class RouterInfo {
        private boolean used;                    // 是否使用了智能路由
        private boolean needRetrieval;           // 是否需要检索
        private String reason;                   // 判断理由
        private Long selectedKnowledgeBaseId;    // 选中的知识库ID
        private String selectedKnowledgeBaseName;// 选中的知识库名称
        private List<KnowledgeBaseScore> candidates; // 候选知识库列表
    }
}
```

## 配置项

```yaml
# application.yml 新增配置
intent-router:
  enabled: true                              # 是否启用智能路由
  min-relevance-score: 0.3                   # 最小相关度阈值，低于此值不推荐
  max-recommended-bases: 3                   # 最大推荐知识库数量
  show-router-info: true                     # 是否返回路由详情
  cache-enabled: true                        # 是否缓存路由结果
  cache-ttl-minutes: 10                      # 缓存过期时间
```

## 错误处理

| 场景 | 处理方式 |
|------|---------|
| LLM意图判断失败 | 降级为直接LLM回答，返回 `routerInfo.used = false` |
| 无可用知识库 | 直接LLM回答，reason = "暂无可用知识库" |
| 所有知识库相关度低于阈值 | 直接LLM回答，reason = "未找到相关知识库" |
| 推荐知识库不存在 | 取下一个候选知识库，或降级直接回答 |
| 用户禁用智能路由 | 走原有流程，要求用户手动选择 |

## 前端改动

### 知识库选择器

- 新增"自动选择(推荐)"默认选项
- 选择"自动选择"时，knowledgeBaseId传null

### 消息展示

- 新增路由信息展示组件
- 显示：已选择的知识库、判断理由、候选知识库列表

## 实现步骤

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 新增DTO类 | IntentRouterRequest.java, IntentRouterResponse.java |
| 2 | 新增配置类 | IntentRouterConfig.java |
| 3 | 新增IntentRouterService | IntentRouterService.java, IntentRouterServiceImpl.java |
| 4 | 修改ChatResponse | 添加RouterInfo内部类 |
| 5 | 修改ChatService | 在chat方法中添加智能路由逻辑 |
| 6 | 前端：知识库选择器 | 添加"自动选择"选项 |
| 7 | 前端：消息展示 | 添加路由信息展示 |

## 影响范围

- **后端**：新增5个文件，修改约3个文件
- **前端**：修改知识库选择组件、Chat消息展示组件
- **配置**：新增intent-router配置项
