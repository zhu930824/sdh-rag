# SDH-RAG 系统设计与架构文档

## 1. 系统概述

SDH-RAG 是一个企业级 RAG（Retrieval-Augmented Generation）知识库管理系统，提供智能问答、文档管理、知识检索、知识图谱和工作流编排等核心能力。

### 1.1 技术栈

**后端**
- Java 17+ / Spring Boot 3.4
- Spring AI 1.0.0（RAG 框架）
- Spring AI Alibaba（DashScope/通义千问）
- MyBatis Plus（ORM）
- MySQL 8.0（关系数据库）
- Redis（缓存）
- Elasticsearch 8.0（向量存储）
- Neo4j（知识图谱）
- MinIO（对象存储）

**前端**
- Vue 3.4 / TypeScript
- Vite 5（构建工具）
- Ant Design Vue 4（UI 组件库）
- Pinia（状态管理）
- Vue Router 4（路由）
- Vue Flow（工作流可视化）
- AntV G6（图谱可视化）
- Axios（HTTP 客户端）
- Marked + Highlight.js（Markdown 渲染）

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              前端层 (Vue 3)                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ 知识库管理 │ │ 智能问答  │ │ 文档管理  │ │ 知识图谱  │ │ 工作流编排 │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │ HTTP/WebSocket/SSE
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              API 网关层                                   │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                    JWT 认证拦截器 (JwtInterceptor)                 │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              业务服务层                                   │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
│  │ ChatService │ │DocumentService│ │KnowledgeService│ │GraphService│      │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘        │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
│  │UserService │ │WorkflowEngine│ │EmbeddingService│ │RerankService│      │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              RAG 核心层                                   │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    Spring AI Advisor 机制                          │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │  │
│  │  │SensitiveWord│ │RetrievalAug │ │ MessageChat │ │ ChatClient  │  │  │
│  │  │  Advisor    │ │ mentationAdvisor│ │ MemoryAdvisor│ │            │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │  │
│  │                          │                                         │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │  │
│  │  │QueryTrans- │ │Document    │ │DocumentPost │ │QueryAugmenter│  │  │
│  │  │ former     │ │ Retriever  │ │ Processor   │ │             │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              数据存储层                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │  MySQL   │ │  Redis   │ │Elasticsearch│ │  Neo4j  │ │  MinIO   │       │
│  │ 业务数据  │ │ 缓存/会话 │ │ 向量存储  │ │ 知识图谱 │ │ 文件存储  │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              外部服务层                                   │
│  ┌──────────────────────────┐ ┌──────────────────────────┐             │
│  │  DashScope (通义千问)      │ │   OpenAI 兼容接口         │             │
│  │  - LLM (qwen-turbo)      │ │   - DeepSeek/Moonshot/    │             │
│  │  - Embedding (text-embed)│ │   SiliconFlow             │             │
│  │  - Rerank (qwen3-rerank) │ │                           │             │
│  └──────────────────────────┘ └──────────────────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 后端项目结构

```
rag-backend/src/main/java/cn/sdh/backend/
├── common/                     # 公共模块
│   ├── context/               # 上下文（UserContext）
│   ├── exception/             # 异常处理
│   └── result/                # 统一响应封装
├── config/                     # 配置类
│   ├── CorsConfig.java        # CORS 配置
│   ├── WebConfig.java         # Web 配置
│   ├── ElasticsearchConfig.java
│   ├── Neo4jConfig.java
│   ├── MinioConfig.java
│   ├── ChatClientConfig.java  # ChatClient 配置
│   └── RagConfig.java         # RAG 配置
├── controller/                 # REST API 控制器
│   ├── ChatController.java    # 问答接口
│   ├── KnowledgeBaseController.java
│   ├── DocumentController.java
│   ├── GraphController.java
│   ├── WorkflowController.java
│   ├── UserController.java
│   └── ...
├── dto/                        # 数据传输对象
├── entity/                     # 数据库实体
│   ├── User.java
│   ├── KnowledgeBase.java
│   ├── KnowledgeDocument.java
│   ├── KnowledgeChunk.java
│   ├── ChatHistory.java
│   ├── ChatSession.java
│   ├── GraphNode.java
│   ├── GraphEdge.java
│   ├── Workflow.java
│   ├── ModelConfig.java
│   └── ...
├── graph/                      # 知识图谱模块
│   ├── node/                  # 图节点定义
│   │   ├── BaseNode.java
│   │   ├── DocumentNode.java
│   │   ├── EntityNode.java
│   │   ├── ConceptNode.java
│   │   └── KeywordNode.java
│   ├── repository/            # Neo4j 仓库
│   └── extractor/             # 实体抽取器
│       └── impl/LLMEntityExtractor.java
├── interceptor/                # 拦截器
│   └── JwtInterceptor.java
├── mapper/                     # MyBatis Plus Mapper
├── rag/                        # RAG 核心模块
│   ├── RagAdvisorFactory.java
│   ├── HybridDocumentRetriever.java
│   ├── RerankDocumentPostProcessor.java
│   ├── HistoryAwareQueryTransformer.java
│   ├── MySQLChatMemoryRepository.java
│   └── SensitiveWordAdvisor.java
├── service/                    # 业务服务接口
│   └── impl/                   # 业务服务实现
├── utils/                      # 工具类
└── workflow/                   # 工作流引擎
    ├── dag/                    # DAG 解析
    │   └── DAGParser.java
    ├── dto/                    # 数据传输对象
    ├── executor/               # 执行器
    │   ├── WorkflowEngine.java
    │   ├── NodeExecutor.java
    │   ├── NodeExecutorFactory.java
    │   └── impl/               # 节点执行器实现
    │       ├── InputNodeExecutor.java
    │       ├── OutputNodeExecutor.java
    │       ├── LlmNodeExecutor.java
    │       ├── RetrievalNodeExecutor.java
    │       ├── ConditionNodeExecutor.java
    │       ├── HttpNodeExecutor.java
    │       └── CodeNodeExecutor.java
    └── model/                  # 工作流模型
        ├── WorkflowConfig.java
        ├── WorkflowNode.java
        └── WorkflowEdge.java
```

### 2.3 前端项目结构

```
rag-frontend/src/
├── api/                        # API 接口
│   ├── chat.ts                # 问答接口
│   ├── knowledgeBase.ts       # 知识库接口
│   ├── document.ts            # 文档接口
│   ├── graph.ts               # 知识图谱接口
│   ├── workflow.ts            # 工作流接口
│   ├── user.ts                # 用户接口
│   └── ...
├── components/                 # 公共组件
├── composables/                # 组合式函数
├── layouts/                    # 页面布局
│   ├── MainLayout.vue
│   └── components/
│       ├── Header.vue
│       ├── Sidebar.vue
│       └── Breadcrumb.vue
├── router/                     # 路由配置
│   └── index.ts
├── stores/                     # Pinia 状态管理
│   ├── user.ts                # 用户状态
│   ├── chat.ts                # 问答状态
│   ├── workflow.ts            # 工作流状态
│   └── document.ts            # 文档状态
├── styles/                     # 样式文件
├── types/                      # TypeScript 类型定义
├── utils/                      # 工具函数
│   ├── request.ts             # Axios 封装
│   ├── message.ts             # 消息提示
│   └── confirm.ts             # 确认框
└── views/                      # 页面视图
    ├── dashboard/             # 首页仪表盘
    ├── knowledge-base/        # 知识库管理
    │   ├── index.vue
    │   ├── detail.vue
    │   └── components/
    ├── document/              # 文档管理
    ├── chat/                  # 智能问答
    │   └── components/
    │       ├── MessageList.vue
    │       ├── ChatInput.vue
    │       ├── SessionList.vue
    │       └── SourceCard.vue
    ├── graph/                 # 知识图谱
    │   └── components/
    ├── workflow/              # 工作流编排
    │   └── components/
    │       ├── FlowCanvas.vue
    │       ├── NodePanel.vue
    │       ├── ConfigPanel.vue
    │       ├── nodes/         # 自定义节点组件
    │       └── configs/       # 节点配置组件
    ├── workflow-list/         # 工作流列表
    ├── model/                 # 模型配置
    ├── user/                  # 用户管理
    ├── role/                  # 角色管理
    ├── sensitive/             # 敏感词管理
    ├── hotwords/              # 热点词分析
    ├── stats/                 # 数据统计
    ├── settings/              # 系统设置
    └── ...
```

## 3. 核心模块设计

### 3.1 RAG 检索增强生成模块

#### 3.1.1 Spring AI Advisor 架构

```
用户查询
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ SensitiveWordAdvisor (order=0)                              │
│ - 敏感词检测                                                 │
│ - 发现敏感词则拦截返回错误                                    │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ RetrievalAugmentationAdvisor                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ HistoryAwareQueryTransformer                         │   │
│  │ - 多轮对话查询改写                                    │   │
│  │ - 补充上下文信息                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                         │                                   │
│                         ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ HybridDocumentRetriever                              │   │
│  │ - 向量检索 + 关键字检索                               │   │
│  │ - RRF 融合排序                                        │   │
│  └─────────────────────────────────────────────────────┘   │
│                         │                                   │
│                         ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ RerankDocumentPostProcessor                          │   │
│  │ - 调用重排序模型                                      │   │
│  │ - 相似度阈值过滤                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                         │                                   │
│                         ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ ContextualQueryAugmenter                             │   │
│  │ - 将检索文档注入 Prompt                              │   │
│  │ - 支持 {query} 和 {context} 占位符                   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ MessageChatMemoryAdvisor                                    │
│ - 加载对话历史                                               │
│ - 支持多轮对话上下文                                         │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ ChatModel                                                   │
│ - 支持 DashScope / OpenAI / DeepSeek / Moonshot 等          │
│ - 流式响应输出                                               │
└─────────────────────────────────────────────────────────────┘
```

#### 3.1.2 混合检索策略

```java
// HybridDocumentRetriever 核心逻辑
public List<Document> retrieve(Query query) {
    // 1. 向量检索
    List<Document> vectorResults = vectorStoreService.similaritySearch(
        query.text(), knowledgeId, vectorTopK, embeddingModel
    );
    
    // 2. 关键字检索 (BM25)
    List<Document> keywordResults = vectorStoreService.keywordSearch(
        query.text(), knowledgeId, keywordTopK
    );
    
    // 3. RRF 融合排序
    return mergeWithRRF(vectorResults, keywordResults, 
        vectorWeight, keywordWeight);
}
```

#### 3.1.3 配置参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| vectorTopK | 向量检索返回数量 | 10 |
| keywordTopK | 关键字检索返回数量 | 10 |
| vectorWeight | 向量检索权重 | 0.7 |
| keywordWeight | 关键字检索权重 | 0.3 |
| similarityThreshold | 相似度阈值 | 0.5 |
| enableRewrite | 启用查询改写 | true |

### 3.2 文档处理模块

#### 3.2.1 文档处理流程

```
文档上传 (MinIO)
    │
    ▼
┌─────────────────────────────────────┐
│ DocumentProcessService              │
│                                     │
│  1. 文档解析 (Tika/PDF)             │
│  2. 文本分块 (ChunkSplitter)        │
│  3. 向量嵌入 (EmbeddingService)     │
│  4. 存储向量 (Elasticsearch)        │
│  5. 知识图谱抽取 (GraphBuildService)│
│                                     │
└─────────────────────────────────────┘
    │
    ▼
存储结果
├── MySQL: 文档元数据、分块信息
├── Elasticsearch: 向量数据
├── Neo4j: 知识图谱节点
└── MinIO: 原始文件
```

#### 3.2.2 分块策略

- 按字符数分块，支持配置分块大小和重叠
- 默认分块大小：500 字符
- 默认重叠大小：50 字符
- 支持按知识库单独配置分块参数

### 3.3 知识图谱模块

#### 3.3.1 图谱节点类型

| 节点类型 | 说明 | 属性 |
|---------|------|------|
| DocumentNode | 文档节点 | documentId, title, source |
| EntityNode | 实体节点 | name, type |
| ConceptNode | 概念节点 | name, description |
| KeywordNode | 关键词节点 | name, frequency |

#### 3.3.2 实体抽取

使用 LLM 进行实体抽取，支持：
- 命名实体识别（人名、地名、机构名等）
- 概念抽取
- 关键词提取
- 关系抽取

### 3.4 工作流引擎模块

#### 3.4.1 工作流节点类型

| 节点类型 | 说明 | 执行器 |
|---------|------|--------|
| InputNode | 输入节点 | InputNodeExecutor |
| OutputNode | 输出节点 | OutputNodeExecutor |
| LlmNode | LLM 调用节点 | LlmNodeExecutor |
| RetrievalNode | 检索节点 | RetrievalNodeExecutor |
| ConditionNode | 条件分支节点 | ConditionNodeExecutor |
| HttpNode | HTTP 请求节点 | HttpNodeExecutor |
| CodeNode | 代码执行节点 | CodeNodeExecutor |

#### 3.4.2 工作流执行流程

```
1. DAGParser 解析工作流配置
2. 拓扑排序确定执行顺序
3. NodeExecutorFactory 获取节点执行器
4. 按顺序执行各节点
5. 节点间数据流转
6. 支持流式输出 (SSE)
```

## 4. 数据模型设计

### 4.1 核心实体关系

```
┌─────────────┐     1:N      ┌─────────────────┐
│    User     │──────────────│  KnowledgeBase  │
└─────────────┘              └─────────────────┘
                                   │
                                   │ N:M
                                   ▼
                             ┌─────────────────┐
                             │KnowledgeDocument│
                             └─────────────────┘
                                   │
                                   │ 1:N
                                   ▼
                             ┌─────────────────┐
                             │ KnowledgeChunk  │
                             └─────────────────┘
                                   │
                                   │ 向量化存储
                                   ▼
                             ┌─────────────────┐
                             │ Elasticsearch   │
                             │  (向量索引)      │
                             └─────────────────┘
```

### 4.2 主要数据表

| 表名 | 说明 |
|------|------|
| user | 用户表 |
| role | 角色表 |
| knowledge_base | 知识库表 |
| knowledge_document | 文档表 |
| knowledge_chunk | 文档分块表 |
| knowledge_document_relation | 文档-知识库关联表 |
| chat_session | 会话表 |
| chat_history | 聊天记录表 |
| model_config | 模型配置表 |
| workflow | 工作流表 |
| sensitive_word | 敏感词表 |
| prompt_template | 提示词模板表 |

### 4.3 Elasticsearch 索引设计

```
索引名: sdh-rag-index
字段:
- chunk_id: 分块ID
- document_id: 文档ID  
- knowledge_id: 知识库ID
- chunk_index: 分块索引
- content: 文本内容
- embedding: 向量 (1536维)
- embedding_model: 嵌入模型名称
- create_time: 创建时间
```

## 5. API 接口设计

### 5.1 主要接口

| 模块 | 接口路径 | 说明 |
|------|---------|------|
| 问答 | POST /api/chat/ask | 智能问答（流式） |
| 知识库 | GET /api/knowledge/list | 获取知识库列表 |
| 知识库 | POST /api/knowledge | 创建知识库 |
| 文档 | POST /api/document/upload | 上传文档 |
| 图谱 | GET /api/graph/nodes | 获取图谱节点 |
| 工作流 | POST /api/workflow/{id}/execute | 执行工作流 |
| 用户 | POST /api/user/login | 用户登录 |

### 5.2 问答接口示例

**请求:**
```json
POST /api/chat/ask
{
  "question": "什么是RAG技术？",
  "sessionId": "xxx",
  "knowledgeId": 1,
  "modelId": 1
}
```

**响应 (SSE 流式):**
```
data: {"type":"content","content":"RAG是检索增强生成..."}
data: {"type":"content","content":"它结合了信息检索和生成模型..."}
data: {"type":"done","historyId":123}
```

## 6. 安全设计

### 6.1 认证授权

- JWT Token 认证
- 基于角色的权限控制 (RBAC)
- 接口权限拦截

### 6.2 敏感词过滤

- `SensitiveWordAdvisor` 在请求进入时检测
- 支持敏感词库管理
- 自动拦截包含敏感词的请求

## 7. 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Nginx                                │
│                    (反向代理/负载均衡)                        │
└─────────────────────────────────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
    ┌───────────┐   ┌───────────┐   ┌───────────┐
    │  前端服务   │   │  后端服务   │   │  后端服务   │
    │  (Vue)    │   │ (Spring)  │   │ (Spring)  │
    └───────────┘   └───────────┘   └───────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
    ┌───────────┐   ┌───────────┐   ┌───────────┐
    │   MySQL   │   │   Redis   │   │Elasticsearch│
    └───────────┘   └───────────┘   └───────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
    ┌───────────┐   ┌───────────┐   ┌───────────┐
    │   Neo4j   │   │   MinIO   │   │ DashScope │
    └───────────┘   └───────────┘   └───────────┘
```

## 8. 配置说明

### 8.1 后端配置 (application.yml)

```yaml
# 服务端口
server:
  port: 8989

# 数据库
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sdh_rag
    username: root
    password: root

  # Redis
  data:
    redis:
      port: 6379

  # Elasticsearch
  elasticsearch:
    uris: localhost:9200

  # Neo4j
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: password

  # AI 模型
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
    vectorstore:
      elasticsearch:
        index-name: sdh-rag-index
        dimensions: 1536

# MinIO
minio:
  endpoint: http://localhost:9000
  access-key: admin
  secret-key: password
  bucket-name: sdh-documents

# RAG 配置
rag:
  use-advisor: true
  enable-rewrite: true
  memory-window-size: 10

# JWT
xushu:
  jwt:
    admin-secret-key: xxx
    admin-ttl: 7200000
```

### 8.2 前端配置

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8989',
        changeOrigin: true,
      }
    }
  }
})
```

## 9. 验证测试

### 9.1 启动服务

```bash
# 后端
cd rag-backend
mvn spring-boot:run

# 前端
cd rag-frontend
npm run dev
```

### 9.2 测试流程

1. 用户注册/登录
2. 创建知识库
3. 上传文档并处理
4. 智能问答测试
5. 知识图谱查看
6. 工作流编排测试
