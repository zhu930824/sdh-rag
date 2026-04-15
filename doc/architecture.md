# SDH-RAG 系统架构设计文档

## 1. 项目概述

### 1.1 项目简介
SDH-RAG 是一个企业级 RAG（Retrieval-Augmented Generation）知识库管理系统，基于 Spring AI 和 Vue 3 构建。系统提供智能问答、文档管理、知识检索、知识图谱等核心功能，支持多知识库管理、多模型配置、工作流编排等高级特性。

### 1.2 技术栈

#### 后端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | JDK 版本 |
| Spring Boot | 3.4 | 基础框架 |
| Spring AI | Latest | AI 集成框架 |
| Spring AI Alibaba | Latest | 阿里云通义千问集成 |
| MyBatis Plus | Latest | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存数据库 |
| Elasticsearch | 8.0+ | 向量存储与全文检索 |
| Neo4j | Latest | 知识图谱数据库 |
| MinIO | Latest | 对象存储 |
| JWT | 0.12.6 | 认证授权 |
| Flyway | Latest | 数据库版本管理 |

#### 前端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.4 | 前端框架 |
| TypeScript | 5.4 | 类型系统 |
| Vite | 5.2 | 构建工具 |
| Ant Design Vue | 4.2 | UI 组件库 |
| Pinia | 2.1 | 状态管理 |
| Vue Router | 4.3 | 路由管理 |
| Axios | 1.6 | HTTP 客户端 |
| @antv/g6 | 4.8 | 图谱可视化 |
| @vue-flow | 1.48 | 工作流编排 |
| Marked | 17.0 | Markdown 解析 |
| Highlight.js | 11.11 | 代码高亮 |

### 1.3 系统特性
- 多知识库管理，支持知识库级别的数据隔离
- 多模型配置，支持通义千问等大模型
- 智能问答，支持 RAG 检索增强生成
- 知识图谱，支持实体、关系、概念、关键词提取
- 工作流编排，可视化流程设计
- 向量检索与全文检索混合搜索
- 敏感词过滤、热点词分析
- 定时任务、Webhook 支持

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端层 (Vue 3)                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│  │ 知识库   │  │ 智能问答 │  │ 知识图谱 │  │ 工作流   │  │ 系统管理 │          │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Pinia Store (状态管理)                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Axios (HTTP 请求)                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            后端层 (Spring Boot)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Controller Layer (控制器层)                       │   │
│  │  UserController / ChatController / KnowledgeController / Graph...   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Service Layer (服务层)                            │   │
│  │  ChatService / KnowledgeService / GraphService / RagSearchService   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Spring AI Layer (AI集成层)                        │   │
│  │  ChatClient / EmbeddingModel / VectorStore / Advisor                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Repository Layer (数据访问层)                     │   │
│  │  MyBatis Plus / Neo4j Repository / Elasticsearch Repository         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            数据存储层                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐              │
│  │   MySQL   │  │   Redis   │  │Elasticsearch│ │   Neo4j   │              │
│  │ 业务数据   │  │ 缓存/会话  │  │  向量存储   │  │ 知识图谱  │              │
│  └───────────┘  └───────────┘  └───────────┘  └───────────┘              │
│  ┌───────────┐                                                            │
│  │   MinIO   │                                                            │
│  │ 文件存储   │                                                            │
│  └───────────┘                                                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            外部服务层                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                    DashScope (阿里云通义千问)                          │ │
│  │  Qwen3 (Chat) / Text-Embedding-v3 (Embedding) / Qwen3-Rerank         │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 核心模块

#### 2.2.1 后端模块结构

```
rag-backend/src/main/java/cn/sdh/backend/
├── advisor/                    # Spring AI Advisor
│   ├── common/                 # 通用 Advisor
│   │   ├── AIModelObservationHandler.java    # 模型观测
│   │   ├── ContextSettingAdvisor.java        # 上下文设置
│   │   ├── HotwordAdvisor.java               # 热点词处理
│   │   ├── LoggingAdvisor.java               # 日志记录
│   │   ├── MySQLChatMemoryRepository.java    # 对话记忆存储
│   │   ├── SensitiveWordAdvisor.java         # 敏感词过滤
│   │   └── TokenUsageAdvisor.java            # Token统计
│   └── rag/                    # RAG 相关 Advisor
│       ├── HistoryAwareQueryTransformer.java # 历史感知查询转换
│       ├── HybridDocumentRetriever.java      # 混合检索器
│       ├── RagAdvisorFactory.java            # RAG Advisor 工厂
│       └── RerankDocumentPostProcessor.java  # 重排序处理器
├── aop/                        # AOP 切面
│   └── OperationLogAspect.java # 操作日志切面
├── common/                     # 公共组件
│   ├── context/                # 上下文
│   │   └── UserContext.java    # 用户上下文
│   ├── exception/              # 异常处理
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   └── result/                 # 统一响应
│       └── Result.java
├── config/                     # 配置类
│   ├── AsyncConfig.java        # 异步任务配置
│   ├── ChatClientConfig.java   # 聊天客户端配置
│   ├── CorsConfig.java         # 跨域配置
│   ├── ElasticsearchConfig.java# ES配置
│   ├── MinioConfig.java        # MinIO配置
│   ├── MybatisPlusConfig.java  # MyBatis配置
│   ├── Neo4jConfig.java        # Neo4j配置
│   ├── RagConfig.java          # RAG配置
│   └── WebConfig.java          # Web配置
├── controller/                 # 控制器层 (26个)
│   ├── AnnouncementController.java
│   ├── BatchProcessController.java
│   ├── ChatController.java
│   ├── DashboardController.java
│   ├── DocumentController.java
│   ├── GraphController.java
│   ├── KnowledgeBaseController.java
│   ├── ModelConfigController.java
│   ├── UserController.java
│   └── ...
├── dto/                        # 数据传输对象 (30+)
├── entity/                     # 实体类 (46个)
│   ├── User.java
│   ├── Role.java
│   ├── KnowledgeBase.java
│   ├── KnowledgeDocument.java
│   ├── KnowledgeChunk.java
│   ├── ChatHistory.java
│   ├── ChatSession.java
│   ├── ModelConfig.java
│   ├── Workflow.java
│   └── ...
├── graph/                      # 知识图谱模块
│   ├── extractor/               # 实体提取器
│   │   ├── EntityExtractor.java
│   │   └── impl/LLMEntityExtractor.java
│   ├── node/                   # 图谱节点
│   │   ├── BaseNode.java
│   │   ├── EntityNode.java
│   │   ├── ConceptNode.java
│   │   ├── KeywordNode.java
│   │   └── DocumentNode.java
│   ├── relation/               # 图谱关系
│   └── repository/             # 图谱仓库
│       ├── CustomGraphRepository.java
│       ├── EntityNodeRepository.java
│       └── ...
├── interceptor/                # 拦截器
│   └── JwtInterceptor.java
├── mapper/                     # MyBatis Mapper
├── service/                    # 服务接口层 (35+)
│   ├── impl/                   # 服务实现
│   ├── ChatService.java
│   ├── KnowledgeBaseService.java
│   ├── KnowledgeService.java
│   ├── GraphService.java
│   ├── GraphBuildService.java
│   ├── GraphBuildAsyncService.java
│   ├── RagSearchService.java
│   ├── RerankService.java
│   ├── VectorStoreService.java
│   └── ...
└── utils/                      # 工具类
    └── JwtUtil.java
```

#### 2.2.2 前端模块结构

```
rag-frontend/src/
├── api/                        # API 接口 (24个)
│   ├── announcement.ts
│   ├── chat.ts
│   ├── chatSession.ts
│   ├── dashboard.ts
│   ├── document.ts
│   ├── feedback.ts
│   ├── graph.ts
│   ├── hotwords.ts
│   ├── knowledgeBase.ts
│   ├── model.ts
│   ├── promptTemplate.ts
│   ├── user.ts
│   ├── workflow.ts
│   └── ...
├── components/                 # 公共组件
│   ├── AnnouncementMarquee.vue # 公告跑马灯
│   └── TableToolbar.vue       # 表格工具栏
├── composables/                # 组合式函数
├── layouts/                    # 布局组件
│   ├── MainLayout.vue         # 主布局
│   └── components/
│       ├── Header.vue         # 头部
│       ├── Sidebar.vue        # 侧边栏
│       ├── MobileMenu.vue     # 移动端菜单
│       ├── Breadcrumb.vue     # 面包屑
│       └── Tabs.vue           # 标签页
├── router/                     # 路由配置
│   └── index.ts
├── stores/                     # 状态管理
│   ├── app.ts                 # 应用状态
│   ├── user.ts                # 用户状态
│   ├── chat.ts                # 聊天状态
│   ├── document.ts            # 文档状态
│   ├── workflow.ts            # 工作流状态
│   └── index.ts
├── types/                      # 类型定义
│   └── index.ts
├── utils/                      # 工具函数
│   ├── request.ts             # Axios封装
│   ├── message.ts             # 消息提示
│   ├── confirm.ts             # 确认框
│   └── form.ts                # 表单工具
├── views/                      # 页面组件 (25+)
│   ├── login/                 # 登录页
│   ├── register/              # 注册页
│   ├── dashboard/             # 首页仪表盘
│   ├── knowledge-base/        # 知识库管理
│   │   ├── index.vue
│   │   ├── detail.vue
│   │   └── components/
│   ├── document/              # 文档管理
│   ├── chat/                  # 智能问答
│   │   ├── index.vue
│   │   └── components/
│   ├── graph/                 # 知识图谱
│   │   ├── index.vue
│   │   └── components/
│   ├── workflow/              # 工作流编排
│   │   ├── index.vue
│   │   └── components/
│   ├── model/                 # 模型管理
│   ├── user/                  # 用户管理
│   ├── role/                  # 角色管理
│   ├── tag/                   # 标签管理
│   ├── sensitive/             # 敏感词管理
│   ├── hotwords/              # 热点词分析
│   ├── stats/                 # 数据统计
│   ├── feedback/              # 问答评价
│   ├── log/                   # 日志管理
│   ├── settings/              # 系统设置
│   ├── profile/               # 个人中心
│   ├── scheduled-task/        # 定时任务
│   ├── webhook/               # Webhook
│   ├── voice/                 # 语音问答
│   ├── prompt/                # Prompt模板
│   └── session/               # 会话管理
├── App.vue                     # 根组件
└── main.ts                     # 入口文件
```

---

## 3. 核心功能模块

### 3.1 用户认证与授权

#### 3.1.1 认证流程
```
用户登录请求
    │
    ▼
UserController.login()
    │
    ▼
UserService.login() → 验证用户名密码
    │
    ▼
JwtUtil.generateToken() → 生成JWT Token
    │
    ▼
返回Token给前端
    │
    ▼
前端存储Token (localStorage/sessionStorage)
    │
    ▼
后续请求携带Token (Header: token)
    │
    ▼
JwtInterceptor → 验证Token并设置用户上下文
```

#### 3.1.2 权限控制
- 基于角色的权限控制 (RBAC)
- 用户 → 角色 → 权限 (菜单路径)
- 前端路由守卫 + 后端接口拦截

### 3.2 知识库管理

#### 3.2.1 数据模型
```
KnowledgeBase (知识库)
    │
    ├── KnowledgeDocument (文档) ──┐
    │       │                      │
    │       └── KnowledgeChunk (分块)
    │              │
    │              └── 向量化存储到 Elasticsearch
    │
    ├── KnowledgeDocumentRelation (文档关联)
    │
    └── KnowledgeBaseTag (标签关联)
```

#### 3.2.2 文档处理流程
```
上传文档
    │
    ▼
MinIO 存储原始文件
    │
    ▼
文档解析 (Tika/PDF Reader)
    │
    ▼
文本分块 (ChunkSize=500, Overlap=50)
    │
    ▼
向量化 (Embedding Model)
    │
    ▼
存储到 Elasticsearch
```

### 3.3 智能问答 (RAG)

#### 3.3.1 RAG 架构
```
用户提问
    │
    ▼
┌─────────────────────────────────────────────────────┐
│                   Spring AI Advisor Chain           │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │ 1. ContextSettingAdvisor                    │   │
│  │    设置系统上下文、知识库ID等                 │   │
│  └─────────────────────────────────────────────┘   │
│                      │                             │
│                      ▼                             │
│  ┌─────────────────────────────────────────────┐   │
│  │ 2. HistoryAwareQueryTransformer             │   │
│  │    基于对话历史改写查询                       │   │
│  └─────────────────────────────────────────────┘   │
│                      │                             │
│                      ▼                             │
│  ┌─────────────────────────────────────────────┐   │
│  │ 3. HybridDocumentRetriever                  │   │
│  │    混合检索: 向量检索 + 关键词检索            │   │
│  └─────────────────────────────────────────────┘   │
│                      │                             │
│                      ▼                             │
│  ┌─────────────────────────────────────────────┐   │
│  │ 4. RerankDocumentPostProcessor              │   │
│  │    重排序检索结果                            │   │
│  └─────────────────────────────────────────────┘   │
│                      │                             │
│                      ▼                             │
│  ┌─────────────────────────────────────────────┐   │
│  │ 5. SensitiveWordAdvisor                     │   │
│  │    敏感词过滤                                │   │
│  └─────────────────────────────────────────────┘   │
│                      │                             │
│                      ▼                             │
│  ┌─────────────────────────────────────────────┐   │
│  │ 6. ChatClient                               │   │
│  │    调用LLM生成回答                           │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
└─────────────────────────────────────────────────────┘
    │
    ▼
返回回答 + 引用来源
```

#### 3.3.2 混合检索策略
```java
// 向量检索 (语义相似度)
List<Document> vectorResults = vectorStore.similaritySearch(query);

// 关键词检索 (精确匹配)
List<Document> keywordResults = elasticsearchService.keywordSearch(query);

// 融合排序
List<Document> mergedResults = hybridRetriever.merge(vectorResults, keywordResults);

// 重排序
List<Document> rerankedResults = rerankService.rerank(query, mergedResults);
```

### 3.4 知识图谱

#### 3.4.1 图谱数据模型 (Neo4j)
```
                    ┌──────────────┐
                    │ DocumentNode │
                    │  (文档节点)   │
                    └──────┬───────┘
                           │
           ┌───────────────┼───────────────┐
           │ CONTAINS      │ CONTAINS      │ CONTAINS
           ▼               ▼               ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ EntityNode   │ │ ConceptNode  │ │ KeywordNode  │
    │  (实体节点)   │ │  (概念节点)   │ │  (关键词节点) │
    └──────┬───────┘ └──────────────┘ └──────────────┘
           │
           │ RELATED_TO
           ▼
    ┌──────────────┐
    │ EntityNode   │
    │  (关联实体)   │
    └──────────────┘

节点属性:
- BaseNode: id, name, description, knowledgeBaseId, createTime, updateTime
- EntityNode: entityType, confidence, sourceDocumentId, frequency
- ConceptNode: category, weight
- KeywordNode: tfidf, sourceDocumentId, frequency
```

#### 3.4.2 图谱构建流程 (异步)
```
构建请求
    │
    ▼
创建构建任务 (GraphBuildTask)
    │
    ▼
异步执行 (GraphBuildAsyncService)
    │
    ├── 获取文档分块
    │
    ├── LLM 提取实体/关系/概念/关键词
    │
    ├── 创建节点 (带 knowledgeBaseId)
    │
    ├── 创建关系
    │
    └── 更新任务状态
    │
    ▼
前端轮询任务状态
```

### 3.5 工作流编排

#### 3.5.1 节点类型
| 节点类型 | 说明 |
|---------|------|
| Input | 输入节点，定义输入参数 |
| LLM | LLM节点，调用大模型 |
| Retrieval | 检索节点，RAG检索 |
| Code | 代码节点，执行脚本 |
| HTTP | HTTP节点，调用外部API |
| Condition | 条件节点，分支判断 |
| Output | 输出节点，定义输出 |

#### 3.5.2 工作流执行
```
Workflow (工作流定义)
    │
    ├── WorkflowNode[] (节点列表)
    │       ├── type (节点类型)
    │       ├── config (节点配置)
    │       └── position (位置信息)
    │
    └── 执行引擎
            ├── 解析节点依赖关系
            ├── 拓扑排序
            └── 顺序执行节点
```

---

## 4. 数据库设计

### 4.1 MySQL 表结构

#### 核心业务表
| 表名 | 说明 |
|------|------|
| user | 用户表 |
| role | 角色表 |
| user_role | 用户角色关联表 |
| knowledge_base | 知识库表 |
| knowledge_document | 文档表 |
| knowledge_chunk | 文档分块表 |
| knowledge_document_relation | 文档知识库关联表 |
| knowledge_base_tag | 知识库标签关联表 |
| chat_history | 对话历史表 |
| chat_session | 对话会话表 |
| model_config | 模型配置表 |
| prompt_template | Prompt模板表 |
| workflow | 工作流表 |
| scheduled_task | 定时任务表 |
| webhook_config | Webhook配置表 |
| sensitive_word | 敏感词表 |
| tag | 标签表 |
| announcement | 公告表 |
| qa_feedback | 问答评价表 |

### 4.2 Elasticsearch 索引

#### 向量存储索引 (sdh-rag-index)
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "content": { "type": "text" },
      "embedding": { 
        "type": "dense_vector",
        "dims": 1536,
        "similarity": "cosine"
      },
      "metadata": {
        "properties": {
          "document_id": { "type": "long" },
          "knowledge_id": { "type": "long" },
          "chunk_index": { "type": "integer" },
          "file_type": { "type": "keyword" }
        }
      }
    }
  }
}
```

### 4.3 Neo4j 图谱结构

#### 节点标签
- `DocumentNode` - 文档节点
- `EntityNode` - 实体节点
- `ConceptNode` - 概念节点
- `KeywordNode` - 关键词节点

#### 关系类型
- `CONTAINS` - 文档包含实体/概念/关键词
- `RELATED_TO` - 实体间关联关系

---

## 5. API 接口设计

### 5.1 接口规范

#### 统一响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

#### 分页响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "current": 1,
    "size": 10
  }
}
```

### 5.2 主要接口列表

#### 用户模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/login | 用户登录 |
| POST | /api/user/register | 用户注册 |
| GET | /api/user/info | 获取用户信息 |
| POST | /api/user/logout | 用户登出 |
| POST | /api/user/profile | 更新个人资料 |
| POST | /api/user/password | 修改密码 |
| POST | /api/user/avatar | 上传头像 |
| GET | /api/user/stats | 获取用户统计 |

#### 知识库模块
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/knowledge/list | 知识库列表 |
| GET | /api/knowledge/{id} | 知识库详情 |
| POST | /api/knowledge | 创建知识库 |
| PUT | /api/knowledge/{id} | 更新知识库 |
| DELETE | /api/knowledge/{id} | 删除知识库 |
| POST | /api/knowledge/{id}/documents | 关联文档 |
| GET | /api/knowledge/{id}/stats | 知识库统计 |

#### 对话模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/chat/ask | 发起对话 |
| GET | /api/chat/sessions | 会话列表 |
| DELETE | /api/chat/sessions/{id} | 删除会话 |
| GET | /api/chat/history/{sessionId} | 对话历史 |

#### 图谱模块
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/graph/data | 获取图谱数据 |
| GET | /api/graph/nodes | 搜索节点 |
| GET | /api/graph/path | 最短路径 |
| GET | /api/graph/stats | 统计信息 |
| POST | /api/graph/build/knowledge/{id} | 构建知识库图谱 |
| GET | /api/graph/task/{taskId} | 获取构建任务状态 |

---

## 6. 前端路由设计

### 6.1 路由配置
```typescript
const routes = [
  { path: '/login', name: 'Login', component: Login },
  { path: '/register', name: 'Register', component: Register },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', meta: { permission: '/dashboard' } },
      { path: 'knowledge', name: 'KnowledgeBase', meta: { permission: '/knowledge' } },
      { path: 'knowledge/:id', name: 'KnowledgeBaseDetail', meta: { hidden: true } },
      { path: 'document', name: 'Document', meta: { permission: '/document' } },
      { path: 'chat', name: 'Chat', meta: { permission: '/chat' } },
      { path: 'graph', name: 'Graph', meta: { permission: '/graph' } },
      { path: 'workflow', name: 'Workflow', meta: { permission: '/workflow' } },
      { path: 'model', name: 'Model', meta: { permission: '/model' } },
      { path: 'user', name: 'User', meta: { permission: '/user' } },
      { path: 'role', name: 'Role', meta: { permission: '/role' } },
      { path: 'tag', name: 'Tag', meta: { permission: '/tag' } },
      { path: 'stats', name: 'Stats', meta: { permission: '/stats' } },
      { path: 'hotwords', name: 'Hotwords', meta: { permission: '/hotwords' } },
      { path: 'feedback', name: 'Feedback', meta: { permission: '/feedback' } },
      { path: 'log', name: 'Log', meta: { permission: '/log' } },
      { path: 'sensitive', name: 'Sensitive', meta: { permission: '/sensitive' } },
      { path: 'announcement', name: 'Announcement', meta: { permission: '/announcement' } },
      { path: 'settings', name: 'Settings', meta: { permission: '/settings' } },
      { path: 'scheduled-task', name: 'ScheduledTask', meta: { permission: '/scheduled-task' } },
      { path: 'webhook', name: 'Webhook', meta: { permission: '/webhook' } },
      { path: 'voice', name: 'Voice', meta: { permission: '/voice' } },
      { path: 'prompt', name: 'Prompt', meta: { permission: '/prompt' } },
      { path: 'session', name: 'Session', meta: { permission: '/session' } },
      { path: 'nlp-query', name: 'NlpQuery', meta: { permission: '/nlp-query' } },
      { path: 'profile', name: 'Profile', meta: { hidden: true } },
    ]
  }
]
```

---

## 7. 部署架构

### 7.1 开发环境
```
┌─────────────────────────────────────────────────────────────┐
│                      开发机器                                │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │  Frontend:3000  │  │  Backend:8989   │                  │
│  │  (Vite Dev)     │  │  (Spring Boot)  │                  │
│  └────────┬────────┘  └────────┬────────┘                  │
│           │                    │                            │
│           └────────────────────┘                            │
│                    │                                         │
└────────────────────┼─────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                   外部服务 (192.168.2.128)                   │
├─────────────────────────────────────────────────────────────┤
│  MySQL:3306 │ Redis:6379 │ ES:9200 │ Neo4j:7687 │ MinIO:9000 │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 生产环境 (推荐)
```
┌─────────────────────────────────────────────────────────────┐
│                      负载均衡 (Nginx)                        │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│  Frontend     │ │  Frontend     │ │  Frontend     │
│  (Nginx)      │ │  (Nginx)      │ │  (Nginx)      │
└───────────────┘ └───────────────┘ └───────────────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│  Backend      │ │  Backend      │ │  Backend      │
│  (Spring Boot)│ │  (Spring Boot)│ │  (Spring Boot)│
└───────────────┘ └───────────────┘ └───────────────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│  MySQL        │ │  Redis        │ │  ES Cluster   │
│  (主从)       │ │  (Cluster)    │ │               │
└───────────────┘ └───────────────┘ └───────────────┘
        │
        ▼
┌───────────────┐ ┌───────────────┐
│  Neo4j        │ │  MinIO        │
│  (Cluster)    │ │  (Cluster)    │
└───────────────┘ └───────────────┘
```

---

## 8. 技术亮点

### 8.1 Spring AI 集成
- 使用 Spring AI Advisor Chain 实现 RAG 流程
- 支持对话记忆 (MySQL 存储)
- 支持 Token 使用统计
- 支持模型观测

### 8.2 混合检索
- 向量检索 (语义相似度)
- 关键词检索 (精确匹配)
- 融合排序 + Rerank 重排

### 8.3 知识图谱
- LLM 自动提取实体、关系、概念、关键词
- 按知识库隔离存储
- 异步构建 + 任务状态追踪

### 8.4 工作流编排
- 可视化流程设计 (@vue-flow)
- 多种节点类型支持
- 灵活的节点配置

---

## 9. 配置说明

### 9.1 后端配置 (application.yml)
```yaml
server:
  port: 8989

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sdh_rag
    username: root
    password: root
  
  data:
    redis:
      host: localhost
      port: 6379
  
  elasticsearch:
    uris: localhost:9200
  
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: password

  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}

minio:
  endpoint: http://localhost:9000
  access-key: admin
  secret-key: password
  bucket-name: sdh-documents

knowledge:
  default:
    chunk-size: 500
    chunk-overlap: 50
    embeddingModel: text-embedding-v3
    rankModel: qwen3-rerank
```

### 9.2 前端配置 (.env)
```env
VITE_API_BASE_URL=/api
VITE_APP_TITLE=SDH-RAG 知识库管理系统
```

---

## 10. 目录索引

### 10.1 后端核心文件
| 文件 | 说明 |
|------|------|
| `controller/ChatController.java` | 对话接口 |
| `controller/KnowledgeBaseController.java` | 知识库接口 |
| `controller/GraphController.java` | 图谱接口 |
| `service/impl/RagSearchServiceImpl.java` | RAG检索实现 |
| `service/impl/GraphBuildServiceImpl.java` | 图谱构建实现 |
| `advisor/rag/RagAdvisorFactory.java` | RAG Advisor工厂 |
| `graph/extractor/impl/LLMEntityExtractor.java` | LLM实体提取 |

### 10.2 前端核心文件
| 文件 | 说明 |
|------|------|
| `views/chat/index.vue` | 智能问答页面 |
| `views/knowledge-base/detail.vue` | 知识库详情页 |
| `views/graph/index.vue` | 知识图谱页面 |
| `views/workflow/index.vue` | 工作流编排页面 |
| `stores/user.ts` | 用户状态管理 |
| `stores/chat.ts` | 聊天状态管理 |
| `utils/request.ts` | HTTP请求封装 |

---

*文档版本: 1.0*
*更新日期: 2026-04-15*
