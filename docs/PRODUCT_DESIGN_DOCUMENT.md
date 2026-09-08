# SDH-RAG 智能知识库管理系统 — 产品设计文档

> **版本**: v2.0  
> **最后更新**: 2026-09-08  
> **文档说明**：本文描述产品能力与交互设计；技术架构、运行边界和演进优先级以 [`architecture.md`](architecture.md) 为当前基线。

---

## 目录

1. [产品概述](#1-产品概述)
2. [用户体系](#2-用户体系)
3. [功能模块详解](#3-功能模块详解)
   - [3.1 智能问答系统](#31-智能问答系统)
   - [3.2 知识库管理](#32-知识库管理)
   - [3.3 文档管理](#33-文档管理)
   - [3.4 知识图谱](#34-知识图谱)
   - [3.5 工作流引擎](#35-工作流引擎)
   - [3.6 检索评估系统](#36-检索评估系统)
   - [3.7 记忆系统](#37-记忆系统)
   - [3.8 用户与权限管理](#38-用户与权限管理)
   - [3.9 系统管理](#39-系统管理)
   - [3.10 数据分析与统计](#310-数据分析与统计)
4. [技术架构](#4-技术架构)
5. [数据模型](#5-数据模型)
6. [API设计](#6-api设计)
7. [前端设计](#7-前端设计)
8. [部署架构](#8-部署架构)

---

## 1. 产品概述

### 1.1 产品定位

SDH-RAG 是一个**企业知识与业务 Agent 平台**。它将文档和业务数据转化为可治理、可检索、可评测的知识，在可信 RAG 问答之上进一步提供受控业务工具、人工审批、执行轨迹和经验回流。

### 1.2 核心价值

| 价值维度 | 描述 |
|---------|------|
| **知识资产化** | 将散落的文档转化为结构化、可检索、可问答的知识资产 |
| **智能检索** | 向量检索 + 全文检索的混合策略，毫秒级精准召回 |
| **可控生成** | 基于知识库的检索增强生成，有效抑制大模型幻觉 |
| **知识图谱** | 自动从文档中提取实体与关系，构建可视化知识网络 |
| **持续优化** | 真实反馈回流、版本化数据集、分层评测、实验、切片和人工校准 |
| **知识治理** | 知识来源可追溯，发布前审核与回归门禁，支持失败阻断和回滚 |
| **业务执行** | Agent 在风险、审批、幂等和审计约束下调用真实业务工具 |

### 1.3 用户场景

1. **企业知识库客服**: 上传产品手册、FAQ文档，构建智能客服机器人
2. **技术文档问答**: 开发者上传技术文档，通过自然语言快速查询 API 用法
3. **法规政策查询**: 法务团队上传法规文件，精准检索条款内容
4. **学术研究辅助**: 研究人员上传论文资料，跨文档关联发现知识
5. **业务操作助手**: 客服、运营或销售在知识支持下查询订单、库存并发起售后动作
6. **知识质量运营**: 运营团队把线上失败问题转成回归集，持续验证检索、生成和发布质量

### 1.4 核心功能矩阵

| 产品域 | 已实现能力 | 主要页面 |
|---|---|---|
| 知识使用 | 流式问答、多模型、来源引用、意图路由、多轮对话、多层记忆 | 智能问答、会话、记忆中心 |
| 知识管理 | 文件与 API 接入、增量同步、去重版本、多策略分块、图谱 | 知识库、文档、知识图谱 |
| 知识治理 | 成员权限、知识草稿、人工审核、索引、回归门禁、回滚审计 | 知识审核与发布 |
| RAG 质量 | 版本化数据集、检索/生成/FULL 评测、失败归因、质量问题 | 检索评估 |
| 持续评测 | 多变体实验、定时回归、预算重试、业务切片、人工复核 | 评测实验室 |
| Agent 执行 | 工具注册、Schema、风险分级、审批、幂等执行、轨迹和反馈 | 业务工具中心、Agent审批与执行 |
| Agent 运营 | 完成率、工具成功率、业务结果、知识/流程改进项 | Agent运营与改进 |
| 流程编排 | DAG、检索/模型/条件/工具节点、暂停与审批后续跑 | 工作流编排 |
| 平台管理 | 用户角色、模型、Prompt、敏感词、日志、统计、Webhook | 系统管理与运营页面 |

---

## 2. 用户体系

### 2.1 角色定义

系统采用基于角色的访问控制（RBAC），支持以下角色类型：

| 角色 | 编码 | 权限范围 |
|------|------|---------|
| **超级管理员** | `admin` | 全部功能，含用户管理、系统配置、角色管理 |
| **普通用户** | `user` | 知识库管理、文档管理、问答、个人设置 |

角色的权限通过逗号分隔的权限字符串定义，存储在 `role` 表的 `permissions` 字段中。

### 2.2 用户属性

每个用户包含以下核心信息：

- **身份信息**: 用户名、密码（加密存储）、昵称、头像
- **联系方式**: 邮箱、手机号
- **偏好设置**: 主题（亮色/暗色/自动）、语言、回复语言偏好
- **个性化**: 个性签名、默认模型选择
- **通知偏好**: 邮件通知开关、声音提醒开关
- **成长体系**: 用户等级、经验值
- **账号状态**: 正常/禁用

### 2.3 认证流程

```
用户 ──→ POST /api/user/login ──→ 验证凭证 ──→ 生成JWT Token ──→ 返回Token
  │                                                                     │
  └──────────── 后续请求携带 Token（Header/Cookie） ─────────────────────┘
                                    │
                          JwtInterceptor 拦截验证
                                    │
                     ┌──────────────┴──────────────┐
                     │                             │
               Admin Token                    User Token
           (独立密钥 + TTL)              (独立密钥 + TTL)
                     │                             │
                     └──────────────┬──────────────┘
                                    │
                           设置 UserContext (ThreadLocal)
```

**安全特性**:
- 双通道认证：Admin 和 User 使用独立的 JWT 密钥和过期时间
- 默认 Token 有效期：2小时
- 密码加密存储

---

## 3. 功能模块详解

### 3.1 智能问答系统

智能问答是系统的核心功能，提供基于知识库的检索增强生成（RAG）对话能力。

#### 3.1.1 问答流程

```
用户提问
    │
    ▼
┌──────────────┐
│  敏感词检测   │ ← SensitiveWordAdvisor (最高优先级)
└──────┬───────┘
       │ 通过
       ▼
┌──────────────┐
│  意图路由     │ ← IntentRouterService (可选)
│  判断是否需要  │   判断是否需要检索、推荐知识库
│  检索知识库   │
└──────┬───────┘
       │
   ┌───┴───┐
   │       │
 需要检索  不需要检索
   │       │
   ▼       ▼
┌──────┐ ┌──────┐
│ RAG  │ │ 直接 │
│ 检索  │ │ 对话  │
└──┬───┘ └──┬───┘
   │        │
   ▼        │
┌──────────┐ │
│ 查询改写  │ │ ← 多轮对话上下文
├──────────┤ │
│ 查询扩展  │ │ ← 生成语义相关查询
├──────────┤ │
│ HyDE生成 │ │ ← 生成假设性文档
├──────────┤ │
│ 混合检索  │ │ ← 向量检索 + 关键字检索
├──────────┤ │
│ 重排序   │ │ ← qwen3-rerank
├──────────┤ │
│ 上下文拼接│ │
└────┬─────┘ │
     │       │
     └───┬───┘
         ▼
┌──────────────┐
│  LLM 生成    │ ← ChatClient (流式 SSE)
├──────────────┤
│ • Token统计  │ ← TokenUsageAdvisor
│ • 热词记录   │ ← HotwordAdvisor
│ • 日志记录   │ ← LoggingAdvisor
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  流式返回     │ ← SSE (type: content/router/done/error)
└──────┬───────┘
       │
       ▼
   用户看到回答
```

#### 3.1.2 检索增强策略

系统支持多种检索增强技术，每个知识库可独立配置：

| 策略 | 配置项 | 说明 |
|------|--------|------|
| **混合检索** | `keywordTopK`, `vectorTopK` | 同时进行关键字检索（BM25）和向量相似度检索，按权重融合 |
| **检索权重** | `keywordWeight`, `vectorWeight` | 控制关键字检索和向量检索的融合比例 |
| **查询改写** | `enableRewrite` | 利用对话历史将指代不清的问题改写为完整查询 |
| **查询扩展** | `enableQueryExpansion`, `queryExpansionCount` | 生成多个语义相关的查询变体以提升召回率 |
| **HyDE** | `enableHyde`, `hydeModel` | 生成假设性文档，用假设文档的向量进行检索 |
| **重排序** | `rankModel` | 对召回结果进行语义重排序，提升精确度 |
| **相似度阈值** | `similarityThreshold` | 过滤低相关度的检索结果 |

#### 3.1.3 智能意图路由

系统具备智能意图路由能力，当用户未指定知识库时自动决策：

- **路由判断**: 分析用户查询意图，决定是否需要检索知识库
- **知识库推荐**: 当需要检索时，推荐最相关的知识库（最多3个）
- **理由透明**: 向用户展示路由决策的依据
- **降级兜底**: 路由失败时自动降级为原有逻辑
- **结果缓存**: 10分钟缓存TTL，提升路由效率

#### 3.1.4 多模型支持

系统通过策略模式支持多种 LLM 提供商：

| 模型提供商 | 策略类 |
|-----------|--------|
| **阿里通义千问 (DashScope)** | `DashScopeChatModelStrategy` |
| **OpenAI** | `OpenAiChatModelStrategy` |
| **DeepSeek** | `DeepSeekChatModelStrategy` |
| **智谱 (Zhipu)** | `ZhipuChatModelStrategy` |
| **百川 (Baichuan)** | `BaichuanChatModelStrategy` |
| **月之暗面 (Moonshot)** | `MoonshotChatModelStrategy` |
| **MiniMax** | `MinimaxChatModelStrategy` |
| **硅基流动 (SiliconFlow)** | `SiliconChatModelStrategy` |
| **自定义** | `CustomChatModelStrategy` |
| **本地模型** | `LocalChatModelStrategy` |

每种策略配置：
- 模型名称（如 `qwen-plus`, `qwen-max`, `deepseek-chat`）
- API 地址
- API Key
- 温度等参数

用户可为每个知识库或对话指定不同的模型。

#### 3.1.5 对话管理

| 功能 | 实现 |
|------|------|
| **多轮对话** | 基于 sessionId 管理对话上下文，窗口大小可配置（默认10轮） |
| **会话列表** | 按 sessionId 分组，以首条消息为标题，支持分页 |
| **会话删除** | 支持按会话批量删除历史记录 |
| **会话标题** | 支持自定义会话标题 |
| **消息评价** | 用户可对 AI 回答进行评分（点赞/踩） |
| **对话记忆** | Spring AI MessageChatMemoryAdvisor 实现 |

#### 3.1.6 流式响应协议

前端通过 SSE（Server-Sent Events）接收流式 JSON 消息：

```json
// 路由信息
{"type":"router","used":true,"needRetrieval":true,"reason":"...","recommendedBases":[...]}

// 内容片段
{"type":"content","content":"根据知识库中的资料..."}

// 完成标记
{"type":"done","historyId":123}

// 错误信息
{"type":"error","message":"错误描述"}
```

---

### 3.2 知识库管理

知识库是组织知识的核心单元，每个用户可以创建多个知识库。

#### 3.2.1 知识库数据结构

```
KnowledgeBase
├── 基本信息: name, description, icon, color, isPublic
├── 分块配置: chunkSize(默认500), chunkOverlap(默认50)
├── 嵌入配置: embeddingModel(默认text-embedding-v2)
├── 检索配置:
│   ├── similarityThreshold (相似度阈值)
│   ├── keywordTopK / vectorTopK (检索数量)
│   ├── keywordWeight / vectorWeight (检索权重)
│   ├── enableRewrite (查询改写开关)
│   ├── enableQueryExpansion / queryExpansionCount (查询扩展)
│   └── enableHyde / hydeModel (HyDE配置)
├── 重排序: rankModel (默认qwen3-rerank)
├── 标签列表: [Tag1, Tag2, ...]
└── 统计信息: 文档数、分块数
```

#### 3.2.2 知识库操作

| 操作 | 说明 |
|------|------|
| **创建知识库** | 设置名称、描述、图标、颜色、公开性 |
| **配置更新** | 独立更新分块参数、嵌入模型、检索策略、HyDE配置 |
| **配置完整更新** | 一次性更新所有配置 + 标签关联 |
| **文档关联** | 将文档关联到知识库（支持批量、支持每个文档独立配置） |
| **文档取消关联** | 移除文档与知识库的关联（不删除原文档） |
| **文档重新处理** | 触发已关联文档的重新分块和向量化 |
| **标签管理** | 为知识库添加/移除标签 |
| **分块查看** | 查看知识库下所有文档的分块列表 |
| **统计查询** | 获取知识库总数、文档总数、分块总数等统计 |

#### 3.2.3 独立存储方案

每个知识库采用独立的 Elasticsearch 索引存储其向量数据：
- 索引命名模式：根据知识库 ID 动态创建
- 文档在不同知识库中可以有独立的分块版本
- 支持按知识库独立管理向量数据的增删

---

### 3.3 文档管理

#### 3.3.1 文档生命周期

```
上传 ──→ 存储(MinIO) ──→ 解析(Tika) ──→ 分块 ──→ 向量化 ──→ 存储(ES)
  │                                              │
  └──────── 关联到知识库 ────────────────────────────┘
                                                      │
                                          知识库独立分块版本
```

#### 3.3.2 文档属性

```
KnowledgeDocument
├── 基本信息: title, fileType, fileSize
├── 存储信息: filePath (MinIO对象路径)
├── 归属信息: userId (上传者), categoryId (分类)
├── 处理状态: 0-处理中, 1-成功, 2-失败
└── 时间信息: createTime, updateTime
```

#### 3.3.3 支持的文件格式

通过 Apache Tika 实现多格式文档解析：PDF、Word (doc/docx)、TXT、Markdown、HTML 等。

#### 3.3.4 文档管理功能

| 功能 | 说明 |
|------|------|
| **上传** | 支持多文件上传到 MinIO，按日期组织目录结构 |
| **预览** | 获取文档预览 URL |
| **列表/搜索** | 分页列表、关键词搜索、按分类筛选、附带标签信息 |
| **删除** | 删除文档记录 + MinIO 文件 |
| **分类** | 树形分类体系，支持创建/更新/删除分类 |
| **标签** | 文档关联标签，支持多标签 |
| **版本** | 文档版本历史管理 |
| **关联配置** | 每个文档在知识库中可独立配置分块参数 |

#### 3.3.5 分块管理

```
KnowledgeChunk
├── 文档关联: documentId, knowledgeId
├── 分块信息: chunkIndex, content, chunkSize
├── 向量关联: vectorId (Elasticsearch)
└── 元数据: metadata (JSON)
```

---

### 3.4 知识图谱

知识图谱模块从文档中自动提取实体和关系，构建可查询、可可视化的知识网络。

#### 3.4.1 图谱架构

```
文档 ──→ 实体抽取(LLM) ──→ 节点(GraphNode)
                    │
                    └──→ 关系提取(LLM) ──→ 边(GraphEdge)
                                              │
                                    Neo4j 图数据库存储
```

#### 3.4.2 图谱功能

| 功能 | API | 说明 |
|------|-----|------|
| **图谱数据查询** | `GET /api/graph/data` | 获取图谱数据（支持知识库、中心节点、深度参数） |
| **节点搜索** | `GET /api/graph/nodes` | 按关键词搜索节点 |
| **节点详情** | `GET /api/graph/nodes/{id}` | 查看节点详情 |
| **邻居查询** | `GET /api/graph/nodes/{nodeId}/neighbors` | 获取节点的邻居节点和边 |
| **最短路径** | `GET /api/graph/path` | 查找两个节点间的最短路径 |
| **按类型查询** | `GET /api/graph/nodes/type/{type}` | 按实体类型筛选节点 |
| **统计信息** | `GET /api/graph/stats` | 节点数、边数、类型分布等 |
| **单文档构建** | `POST /api/graph/build/{documentId}` | 从单个文档构建图谱 |
| **知识库构建** | `POST /api/graph/build/knowledge/{knowledgeId}` | 从知识库异步构建图谱 |
| **批量构建** | `POST /api/graph/build/batch` | 批量构建多个文档的图谱 |
| **重建** | `POST /api/graph/rebuild/{documentId}` | 重新构建图谱 |
| **删除** | `DELETE /api/graph/knowledge/{knowledgeId}` | 删除知识库对应的图谱 |
| **构建状态** | `GET /api/graph/status/knowledge/{knowledgeId}` | 查询构建进度 |
| **任务状态** | `GET /api/graph/task/{taskId}` | 查询异步任务状态 |

#### 3.4.3 可视化方案

前端使用 **@antv/g6** (v4.8.25) 实现知识图谱的可视化展示，支持：
- 力导向布局
- 节点/边的自定义样式
- 缩放、拖拽交互
- 节点展开/收缩
- 按类型着色

---

### 3.5 工作流引擎

工作流引擎提供可视化的业务流程编排能力，支持拖拽式构建 LLM 驱动的自动化流程。

#### 3.5.1 工作流模型

```
Workflow (工作流定义)
├── 基本信息: name, description, icon
├── 状态: enabled/disabled
├── 节点配置: nodes (JSON) - 包含节点类型、位置、参数
├── 边配置: edges (JSON) - 节点间的连接关系
└── 归属: userId
```

每个工作流由多个节点和边组成，节点类型由 `NodeTypeDefinitionService` 动态提供。

#### 3.5.2 工作流功能

| 功能 | 说明 |
|------|------|
| **CRUD** | 工作流的创建、查询、更新、删除 |
| **状态切换** | 启用/禁用工作流 |
| **同步执行** | `POST /api/workflow/{id}/execute` — 返回完整执行结果 |
| **SSE流式执行** | `GET /api/workflow/{id}/execute/stream` — 实时推流执行事件 |
| **节点调试** | `POST /api/workflow/{id}/debug-node` — 单节点调试 |
| **执行历史** | 查询工作流执行记录，支持分页 |
| **节点类型** | `GET /api/workflow/node-types` — 获取可用节点类型定义 |

#### 3.5.3 执行流程

```
输入 ──→ [节点1] ──→ [节点2] ──→ [节点3] ──→ 输出
              │            │            │
              └── SSE Event ─┘── SSE Event ─┘
              (实时推送每个节点的执行结果)
```

前端使用 **@vue-flow** 系列库实现工作流画布的可视化编辑。

---

### 3.6 检索评估系统

检索评估系统用于量化评估 RAG 系统的检索效果，帮助优化检索策略和参数配置。

#### 3.6.1 评估流程

```
┌─────────────────────────────────────────────────────┐
│                   评估流程                           │
│                                                     │
│  1. 准备测试集 ──→ 2. 运行评估 ──→ 3. 指标计算        │
│       │                  │              │            │
│  • AI自动生成       • 逐条检索      • 命中率          │
│  • 外部导入         • 比对结果      • MRR            │
│  • 内置数据集       • 记录排名      • 平均命中排名     │
│  • 数据集管理                       • 负样本误判率    │
│                                     • 文档命中率     │
│                                                     │
│  4. 报告导出 (Excel: 概览Sheet + 详情Sheet)          │
└─────────────────────────────────────────────────────┘
```

#### 3.6.2 测试集管理

| 功能 | 说明 |
|------|------|
| **AI生成** | 根据知识库内容，LLM自动生成N条问答对 |
| **文件导入** | 支持 JSON/Excel 格式导入外部测试集 |
| **模板下载** | 提供标准 JSON 模板 |
| **数据集CRUD** | 创建、查看、更新、删除数据集 |
| **条目管理** | 批量/单个添加、更新、删除测试条目 |
| **导入导出** | 数据集 Excel 导入导出 |

#### 3.6.3 评估指标

| 指标 | 说明 |
|------|------|
| **分块命中率 (Hit Rate)** | 检索结果中包含目标分块的比例 |
| **文档命中率 (Doc Hit Rate)** | 检索结果中包含目标文档的比例 |
| **MRR (Mean Reciprocal Rank)** | 第一个相关结果的倒数排名的平均值 |
| **平均命中排名 (Avg Hit Rank)** | 相关结果在检索列表中的平均位置 |
| **负样本误判率** | 不应检索到任何结果的查询中，实际召回的比例 |
| **TopK命中分布** | 各排名位置上的命中分布情况 |

#### 3.6.4 报告导出

评估报告以 Excel 格式导出，包含两个 Sheet：
- **评估概览**: 任务名称、知识库、指标汇总
- **QA详情**: 每条问题的检索结果详情

---

### 3.7 记忆系统

记忆系统实现了多层架构的对话记忆管理，使 AI 能够记住用户偏好、事实知识和行为模式。

#### 3.7.1 三层记忆架构

```
┌──────────────────────────────────────────────────────┐
│                    记忆系统架构                        │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Layer 1: 工作记忆 (Working Memory)                   │
│  ├── 存储: Redis                                      │
│  ├── 内容: 当前会话的对话上下文                         │
│  ├── TTL: 1小时                                       │
│  └── 窗口: 最近10轮对话                                │
│                                                      │
│  Layer 2: 情节记忆 (Episodic/Abstract Memory)         │
│  ├── 存储: MySQL (memory_abstract表)                  │
│  ├── 内容: 会话摘要、关键要点                           │
│  ├── 触发: 语义边界检测 + 消息数量阈值                  │
│  └── 衰减: 每日凌晨3点清理低重要性记忆                   │
│                                                      │
│  Layer 3: 模式记忆 (Pattern Memory)                   │
│  ├── 存储: Neo4j + Elasticsearch                      │
│  ├── 内容: 用户偏好、行为模式                           │
│  ├── 提取: FactExtractor / PreferenceExtractor        │
│  └── 语义去重: 相似度阈值0.9                           │
│                                                      │
└──────────────────────────────────────────────────────┘
```

#### 3.7.2 记忆配置

```yaml
memory:
  enabled: true
  working-window-size: 10           # 工作记忆窗口
  working-memory-ttl-hours: 1       # 工作记忆过期时间
  semantic-similarity-threshold: 0.75   # 语义相似度阈值
  semantic-dedup-threshold: 0.9         # 语义去重阈值
  pattern-min-observations: 3           # 模式最小观测次数
  preference-importance-threshold: 5    # 偏好重要性阈值
  boundary-detection-window-size: 3     # 边界检测窗口
  summary-max-messages: 20              # 摘要最大消息数
  decay-cron: "0 0 3 * * ?"            # 衰减定时任务
```

#### 3.7.3 记忆API

| 功能 | API | 说明 |
|------|-----|------|
| **记忆概览** | `GET /api/memory/overview` | 查看用户的偏好、事实、模式、摘要 |
| **添加偏好** | `POST /api/memory/preference` | 手动添加偏好记忆 |
| **删除记忆** | `DELETE /api/memory/{id}` | 按层级删除指定记忆 |
| **语义搜索** | `GET /api/memory/search` | 语义搜索记忆内容 |
| **清除全部** | `DELETE /api/memory/all` | 清除用户所有记忆 |

---

### 3.8 用户与权限管理

#### 3.8.1 用户管理 (Admin)

| 功能 | 说明 |
|------|------|
| **用户列表** | 分页查询、条件筛选 |
| **用户详情** | 查看用户基本信息与状态 |
| **创建/编辑用户** | 管理员创建/修改用户信息 |
| **状态管理** | 启用/禁用用户账号 |
| **角色分配** | 为用户分配角色 |

#### 3.8.2 角色管理

| 功能 | 说明 |
|------|------|
| **角色列表** | 查看所有角色 |
| **创建角色** | 定义角色名称、编码、权限列表 |
| **编辑角色** | 修改角色权限配置 |
| **删除角色** | 删除未使用的角色 |
| **权限定义** | 逗号分隔的权限标识符 |

#### 3.8.3 个人中心

| 功能 | API | 说明 |
|------|-----|------|
| **个人信息** | `GET /api/user/info` | 获取当前用户信息和权限 |
| **更新资料** | `POST /api/user/profile` | 修改昵称、邮箱、签名等 |
| **修改密码** | `POST /api/user/password` | 修改登录密码 |
| **头像上传** | `POST /api/user/avatar` | 上传/更换头像 |
| **用户统计** | `GET /api/user/stats` | 获取个人使用统计 |

---

### 3.9 系统管理

#### 3.9.1 系统设置

基于 key-value 的灵活配置系统：
- 支持按 key 分组管理（如 `general.*`, `rag.*`, `email.*`）
- 支持批量查询和更新
- 配置项可动态扩展

#### 3.9.2 敏感词管理

| 功能 | 说明 |
|------|------|
| **敏感词CRUD** | 添加/编辑/删除敏感词 |
| **分类管理** | 按类别组织敏感词 |
| **自动过滤** | 问答时自动检测和拦截敏感内容 |
| **分页查询** | 支持关键词和分类筛选 |

#### 3.9.3 操作日志

自动记录所有管理操作：

```
OperationLog
├── 执行人: userId, username
├── 操作信息: type, content
├── 请求信息: requestUrl, requestMethod, requestParams, ip
├── 响应信息: responseData, errorMsg
├── 环境信息: browser, os
├── 性能: duration (耗时)
└── 时间: createTime
```

支持按类型、用户名、状态、时间范围筛选查询。

#### 3.9.4 公告管理

系统公告的 CRUD，支持向所有用户推送通知。

#### 3.9.5 计划任务

管理定时任务（如知识图谱构建、记忆衰减、数据统计等）。

#### 3.9.6 模型配置

管理 LLM 模型配置（模型名称、提供商、API密钥、参数等）。

#### 3.9.7 嵌入配置

管理嵌入模型配置，支持嵌入小部件（Embed Widget）功能。

#### 3.9.8 提示词模板

管理可复用的提示词模板。

---

### 3.10 数据分析与统计

#### 3.10.1 仪表盘

| 统计项 | 说明 |
|--------|------|
| 知识库数量 | 用户创建的知识库总数 |
| 文档数量 | 上传的文档总数 |
| 对话次数 | 累计问答次数 |
| 用户数量 | 系统用户总数 |

#### 3.10.2 热度统计

| 功能 | 说明 |
|------|------|
| **热门词排行** | Top N 高频查询词 |
| **趋势分析** | 单个词的日度查询趋势 |
| **整体统计** | 总查询数、唯一词数、增长率 |
| **数据导出** | Excel 格式导出热度数据 |

#### 3.10.3 Token用量

追踪每次 LLM 调用的 Token 消耗，支持按用户、会话、模型维度统计。

#### 3.10.4 每日统计

`StatsDaily` 表记录每日的聚合统计数据。

---

## 4. 技术架构

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端层 (Vue 3)                           │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │ 知识库   │ │ 问答对话  │ │ 知识图谱  │ │ 工作流   │ │ 系统管理│ │
│  │ 管理     │ │          │ │ @antv/g6 │ │ @vue-flow│ │         │ │
│  └────┬────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └───┬────┘ │
│       └───────────┴────────────┴────────────┴────────────┘       │
│                              │ HTTP/SSE                          │
├──────────────────────────────┼──────────────────────────────────┤
│                     Nginx (反向代理)                              │
├──────────────────────────────┼──────────────────────────────────┤
│                        后端层 (Spring Boot 3.5.2)                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Interceptor Layer                      │   │
│  │              JwtInterceptor (认证 + ThreadLocal)          │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Controller Layer                        │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Domain Service Layer                    │   │
│  │  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌──────────────┐  │   │
│  │  │ Chat    │ │Knowledge│ │ Document │ │ Graph/Workflow│  │   │
│  │  │ RAG     │ │Base     │ │ Process  │ │ Evaluation   │  │   │
│  │  └─────────┘ └─────────┘ └──────────┘ └──────────────┘  │   │
│  │  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌──────────────┐  │   │
│  │  │ Intent  │ │ Memory  │ │ Model    │ │ SensitiveWord│  │   │
│  │  │ Router  │ │ System  │ │ Factory  │ │ Hotword      │  │   │
│  │  └─────────┘ └─────────┘ └──────────┘ └──────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Spring AI Advisor Chain                      │   │
│  │  SensitiveWord → RAG → Memory → Context → Token → Log    │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Mapper / Repository Layer              │   │
│  │                   MyBatis Plus + XML                      │   │
│  └──────────────────────────────────────────────────────────┘   │
├──────────────────────────────────────────────────────────────────┤
│                         数据层                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────┐       │
│  │  MySQL   │ │  Redis   │ │Elasticsearch │ │  Neo4j   │       │
│  │  业务数据 │ │ 缓存/会话│ │  向量存储     │ │ 知识图谱 │       │
│  │  记忆摘要 │ │ 工作记忆 │ │  全文检索     │ │ 模式记忆 │       │
│  └──────────┘ └──────────┘ └──────────────┘ └──────────┘       │
│  ┌──────────┐                                                    │
│  │  MinIO   │  对象存储（文档文件）                                │
│  └──────────┘                                                    │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 技术选型理由

| 技术 | 选型理由 |
|------|---------|
| **Spring Boot 3.5.2** | 成熟的 Java 企业级框架，生态完善 |
| **Spring AI 1.1.2** | 官方 AI 集成框架，统一的 ChatClient/Advisor/VectorStore 抽象 |
| **MyBatis Plus** | 简化 CRUD 开发，灵活的自定义 SQL |
| **Elasticsearch 8** | 兼具向量检索和全文检索能力，单一技术栈解决混合检索需求 |
| **Neo4j** | 原生图数据库，天然适合知识图谱存储和路径查询 |
| **Redis** | 高性能缓存，支持会话存储和工作记忆 |
| **MinIO** | S3 兼容的对象存储，适合文档文件存储 |
| **Flyway** | 数据库版本管理，确保环境一致性 |
| **Vue 3 + Ant Design Vue** | 现代化前端框架 + 企业级组件库 |
| **@antv/g6** | 阿里开源图可视化引擎，知识图谱可视化首选 |
| **@vue-flow** | Vue 3 原生工作流编辑器，支持拖拽节点编排 |

### 4.3 Spring AI Advisor 链

系统使用 Spring AI 的 Advisor 机制构建可插拔的处理管道：

```
请求进入
    │
    ▼
[Order: 0] SensitiveWordAdvisor     ← 敏感词检测（最高优先级）
    │
    ▼
[Order: N] RetrievalAugmentationAdvisor ← RAG检索增强（可选）
    │
    ▼
[Order: N] MessageChatMemoryAdvisor ← 对话记忆管理
    │
    ▼
[Order: N] ContextSettingAdvisor    ← 上下文信息传递
    │
    ▼
[Order: N] TokenUsageAdvisor        ← Token 用量统计
    │
    ▼
[Order: N] HotwordAdvisor           ← 热词记录
    │
    ▼
[Order: 10000] LoggingAdvisor       ← 日志记录（最低优先级）
    │
    ▼
响应返回
```

### 4.4 模型工厂策略模式

```
ChatModelFactory
    │
    ├── DashScopeChatModelStrategy    → 通义千问 (qwen-plus/max/turbo)
    ├── OpenAiChatModelStrategy       → OpenAI (gpt-4o/gpt-4-turbo)
    ├── DeepSeekChatModelStrategy     → DeepSeek (deepseek-chat)
    ├── ZhipuChatModelStrategy        → 智谱 (glm-4/glm-4-flash)
    ├── BaichuanChatModelStrategy     → 百川
    ├── MoonshotChatModelStrategy     → 月之暗面 (moonshot-v1)
    ├── MinimaxChatModelStrategy      → MiniMax
    ├── SiliconChatModelStrategy      → 硅基流动
    ├── CustomChatModelStrategy       → 自定义兼容 OpenAI 的 API
    └── LocalChatModelStrategy        → 本地部署模型

EmbeddingModelFactory → text-embedding-v2 (DashScope) 等
RerankModelFactory   → qwen3-rerank (DashScope) 等
```

---

## 5. 数据模型

### 5.1 核心实体关系

```
┌──────────┐       ┌──────────────────┐       ┌──────────────┐
│   User   │───1:N──│  KnowledgeBase  │───N:M──│  Document    │
└──────────┘       └────────┬─────────┘       └──────┬───────┘
      │                      │                        │
      │ 1:N                  │ 1:N                    │ 1:N
      ▼                      ▼                        ▼
┌──────────┐       ┌──────────────────┐       ┌──────────────┐
│ChatHistory│      │ KnowledgeChunk  │       │DocumentChunk │
└────┬─────┘       └────────┬─────────┘       └──────────────┘
     │                       │
     │ sessionId             │ vectorId
     ▼                       ▼
┌──────────┐       ┌──────────────────┐
│ChatSession│      │ Elasticsearch    │
└──────────┘       │ (Vector Store)   │
                   └──────────────────┘

┌──────────┐       ┌──────────────────┐
│  GraphNode│──N:M──│   GraphEdge     │
└──────────┘       └──────────────────┘
     │
     │ (Neo4j)
     ▼
  知识图谱可视化

┌──────────┐       ┌──────────────────┐
│ Workflow │───1:N──│ WorkflowNode    │
└────┬─────┘       └──────────────────┘
     │
     │ 1:N
     ▼
┌──────────────────┐
│WorkflowExecution │
└──────────────────┘

┌────────────────┐       ┌──────────────────┐
│ EvaluationTask │───1:N──│  EvaluationQa   │
└────────────────┘       └──────────────────┘

┌────────────┐       ┌──────────────────┐
│ TestDataset │───1:N──│ TestDatasetItem │
└────────────┘       └──────────────────┘
```

### 5.2 数据库表清单

#### 核心业务表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `user` | `User` | 用户表 |
| `user_profile` | `UserProfile` | 用户扩展信息 |
| `role` | `Role` | 角色表 |
| `knowledge_base` | `KnowledgeBase` | 知识库 |
| `knowledge_document` | `KnowledgeDocument` | 知识库文档（共享文档池） |
| `knowledge_chunk` | `KnowledgeChunk` | 知识库分块（独立存储） |
| `knowledge_document_relation` | `KnowledgeDocumentRelation` | 文档-知识库关联 |
| `knowledge_base_tag` | `KnowledgeBaseTag` | 知识库-标签关联 |
| `document_category` | `DocumentCategory` | 文档分类 |
| `document_chunk` | `DocumentChunk` | 文档分块 |
| `document_version` | `DocumentVersion` | 文档版本 |
| `document_tag` | `DocumentTag` | 文档-标签关联 |
| `tag` | `Tag` | 标签 |
| `chat_history` | `ChatHistory` | 聊天历史 |
| `chat_session` | `ChatSession` | 聊天会话 |
| `session_share` | `SessionShare` | 会话分享 |

#### 知识图谱表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `graph_node` | `GraphNode` | 图谱节点（MySQL备份） |
| `graph_edge` | `GraphEdge` | 图谱边（MySQL备份） |

#### 工作流表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `workflow` | `Workflow` | 工作流定义 |
| `workflow_node` | `WorkflowNode` | 工作流节点 |
| `workflow_execution` | `WorkflowExecution` | 工作流执行记录 |

#### 评估表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `evaluation_task` | `EvaluationTask` | 评估任务 |
| `evaluation_qa` | `EvaluationQa` | 评估问答对 |
| `test_dataset` | `TestDataset` | 测试数据集 |
| `test_dataset_item` | `TestDatasetItem` | 测试集条目 |

#### 记忆表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `memory_abstract` | `MemoryAbstract` | 情节记忆摘要 |

#### 系统管理表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `system_settings` | `SystemSettings` | 系统设置 |
| `sensitive_word` | `SensitiveWord` | 敏感词 |
| `operation_log` | `OperationLog` | 操作日志 |
| `announcement` | `Announcement` | 公告 |
| `scheduled_task` | `ScheduledTask` | 计划任务 |
| `prompt_template` | `PromptTemplate` | 提示词模板 |
| `model_config` | `ModelConfig` | 模型配置 |
| `embed_config` | `EmbedConfig` | 嵌入配置 |
| `webhook_config` | `WebhookConfig` | Webhook配置 |
| `channel_config` | `ChannelConfig` | 渠道配置 |

#### 统计表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `hotword_record` | `HotwordRecord` | 热词记录 |
| `stats_daily` | `StatsDaily` | 每日统计 |
| `api_call_log` | `ApiCallLog` | API调用日志 |
| `token_usage` | `TokenUsage` | Token用量 |
| `voice_record` | `VoiceRecord` | 语音记录 |

#### 积分/审批表

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `points_record` | `PointsRecord` | 积分记录 |
| `points_goods` | `PointsGoods` | 积分商品 |
| `points_exchange` | `PointsExchange` | 积分兑换 |
| `approval_task` | `ApprovalTask` | 审批任务 |
| `approval_record` | `ApprovalRecord` | 审批记录 |

#### 仪表盘

| 表名 | 实体类 | 说明 |
|------|--------|------|
| `dashboard_stats` | `DashboardStats` | 仪表盘统计 |

---

## 6. API设计

### 6.1 API概览

系统共有 **26个 Controller**，提供完整的 RESTful API：

| 模块 | Controller | 基础路径 | 说明 |
|------|-----------|---------|------|
| **认证** | `UserController` | `/api/user` | 登录/注册/个人信息 |
| **问答** | `ChatController` | `/api/chat` | 智能问答、会话管理 |
| **知识库** | `KnowledgeBaseController` | `/api/knowledge-base` | 知识库CRUD |
| **文档** | `DocumentController` | `/api/document` | 文档CRUD |
| **图谱** | `GraphController` | `/api/graph` | 知识图谱 |
| **工作流** | `WorkflowController` | `/api/workflow` | 工作流管理 |
| **评估** | `EvaluationController` | `/api/evaluation` | 检索评估 |
| **数据集** | `TestDatasetController` | `/api/dataset` | 测试集管理 |
| **记忆** | `MemoryController` | `/api/memory` | 记忆系统 |
| **标签** | `TagController` | `/api/tag` | 标签管理 |
| **热词** | `HotwordController` | `/api/hotword` | 热度统计 |
| **统计** | `StatsController` | `/api/stats` | 每日统计 |
| **仪表盘** | `DashboardController` | `/api/dashboard` | 面板数据 |
| **用户管理** | `UserManageController` | `/api/admin/user` | 管理端用户管理 |
| **角色** | `RoleController` | `/api/role` | 角色管理 |
| **设置** | `SystemSettingsController` | `/api/system-settings` | 系统设置 |
| **敏感词** | `SensitiveWordController` | `/api/sensitive-word` | 敏感词管理 |
| **日志** | `OperationLogController` | `/api/operation-log` | 操作日志 |
| **公告** | `AnnouncementController` | `/api/announcement` | 公告管理 |
| **计划任务** | `ScheduledTaskController` | `/api/scheduled-task` | 计划任务 |
| **模型** | `ModelConfigController` | `/api/model-config` | 模型配置 |
| **嵌入** | `EmbedConfigController` | `/api/embed-config` | 嵌入配置 |
| **提示词** | `PromptTemplateController` | `/api/prompt-template` | 提示词模板 |
| **语音** | `VoiceRecordController` | `/api/voice-record` | 语音记录 |
| **批量处理** | `BatchProcessController` | `/api/batch-process` | 批量处理 |
| **反馈** | `QaFeedbackController` | `/api/qa-feedback` | 问答反馈 |

### 6.2 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

错误码规范：
- `200`: 成功
- `401`: 未授权
- `403`: 无权限
- `404`: 资源不存在
- `400`: 参数错误
- `500`: 服务器错误

### 6.3 认证方式

- 请求头: `token: <JWT_TOKEN>`
- Cookie: `token=<JWT_TOKEN>`
- 免认证路径: `/api/user/login`, `/api/user/register`

---

## 7. 前端设计

### 7.1 设计风格

**Modern SaaS Analytics + Glassmorphism（玻璃拟态）**

- 主色调：靛蓝紫渐变 (`#6366F1` → `#8B5CF6` → `#A855F7`)
- 设计语言：清爽现代的 SaaS 分析平台风格
- 玻璃拟态效果增强层次感
- 完整支持暗色模式

### 7.2 页面结构

```
┌──────────────────────────────────────────────────────┐
│                   Navbar (64px)                       │
│  [Logo] [面包屑]              [搜索] [通知] [用户]     │
├──────────┬───────────────────────────────────────────┤
│          │  TagsView (多标签页导航)                    │
│ Sidebar  ├───────────────────────────────────────────┤
│ (260px)  │                                           │
│          │  Content Area                             │
│ [菜单1]  │  ┌─────────────────────────────────────┐  │
│ [菜单2]  │  │     Page Content (router-view)       │  │
│ [菜单3]  │  │                                      │  │
│ [菜单4]  │  └─────────────────────────────────────┘  │
│          │                                           │
└──────────┴───────────────────────────────────────────┘
```

- **Sidebar**: 可折叠（260px ⇄ 72px），含菜单导航
- **Navbar**: 顶部导航，含面包屑、搜索、通知、用户菜单
- **TagsView**: 多标签页导航，支持右键菜单（关闭/刷新/关闭其他）
- **响应式**: 移动端支持侧边抽屉菜单

### 7.3 路由设计

| 路由路径 | 页面 | 菜单图标 |
|---------|------|---------|
| `/` | 仪表盘 | `DashboardOutlined` |
| `/knowledge-base` | 知识库列表 | `DatabaseOutlined` |
| `/knowledge-base/:id` | 知识库详情 | - |
| `/chat` | 智能问答 | `MessageOutlined` |
| `/chat/:sessionId` | 对话详情 | - |
| `/graph` | 知识图谱 | `ApartmentOutlined` |
| `/workflow` | 工作流 | `DeploymentUnitOutlined` |
| `/evaluation` | 检索评估 | `CheckCircleOutlined` |
| `/evaluation/datasets` | 测试集管理 | - |
| `/system/settings` | 系统设置 | `SettingOutlined` |
| `/system/users` | 用户管理 | `TeamOutlined` |
| `/system/roles` | 角色管理 | `SafetyOutlined` |
| `/system/tags` | 标签管理 | `TagsOutlined` |
| `/system/prompts` | 提示词模板 | `FileTextOutlined` |
| `/system/sensitive-words` | 敏感词 | `ShieldOutlined` |
| `/system/model-config` | 模型配置 | `CloudOutlined` |
| `/system/announcements` | 公告管理 | `NotificationOutlined` |
| `/system/scheduled-tasks` | 计划任务 | `ClockCircleOutlined` |
| `/logs` | 操作日志 | `FileSearchOutlined` |
| `/stats` | 统计分析 | `BarChartOutlined` |
| `/login` | 登录页 | - |

### 7.4 状态管理

**Pinia Store**: `useAppStore`

```typescript
{
  sidebarCollapsed: boolean    // 侧边栏折叠状态
  isDark: boolean              // 暗色模式开关
  device: 'desktop' | 'mobile' // 设备类型
  mobileMenuOpen: boolean      // 移动端菜单
  visitedTabs: TabItem[]       // 多标签页记录
}
```

### 7.5 组件依赖

| 功能 | 组件库 | 版本 |
|------|--------|------|
| UI框架 | Ant Design Vue | 4.2.6 |
| 图标 | @ant-design/icons-vue | 7.0.1 |
| 知识图谱可视化 | @antv/g6 | 4.8.25 |
| 工作流编辑器 | @vue-flow/core | 1.48.2 |
| Markdown渲染 | marked | 17.0.4 |
| 代码高亮 | highlight.js | 11.11.1 |

---

## 8. 部署架构

### 8.1 Docker Compose 部署

```yaml
services:
  nginx:        # 反向代理 + 静态资源
  backend:      # Spring Boot 应用 (端口 8989)
  frontend:     # Vue 3 静态资源 (开发端口 3000)
  mysql:        # MySQL 8.0 (端口 3306)
  redis:        # Redis 6.0 (端口 6379)
  elasticsearch:# Elasticsearch 8.0 (端口 9200)
  neo4j:        # Neo4j 图数据库 (端口 7687)
  minio:        # MinIO 对象存储 (端口 9000)
```

### 8.2 环境变量

| 变量 | 必须 | 说明 |
|------|------|------|
| `DASHSCOPE_API_KEY` | ✅ | 通义千问 API 密钥 |
| `DB_USERNAME` | - | 数据库用户名 |
| `DB_PASSWORD` | - | 数据库密码 |
| `REDIS_PASSWORD` | - | Redis 密码 |
| `JWT_ADMIN_SECRET` | - | JWT 管理员密钥 |
| `JWT_USER_SECRET` | - | JWT 用户密钥 |

### 8.3 访问地址

| 服务 | 地址 |
|------|------|
| 前端应用 | `http://localhost` |
| 后端 API | `http://localhost:8989` |
| 开发环境前端 | `http://localhost:3000` |
| MinIO Console | `http://localhost:9001` |
| 默认账号 | `admin / 123456` |

---

## 附录

### A. 开发状态说明

| 组件 | 完成度 | 说明 |
|------|--------|------|
| **后端** | ✅ 已完成 | 26个Controller, 46个Service, 52个Entity, 45个Mapper |
| **前端** | 🚧 骨架阶段 | 基础脚手架已搭建，仅实现了appStore；视图/组件/API模块待开发 |

### B. 关键技术名词解释

| 术语 | 全称 | 说明 |
|------|------|------|
| RAG | Retrieval-Augmented Generation | 检索增强生成 |
| HyDE | Hypothetical Document Embeddings | 假设性文档嵌入 |
| MRR | Mean Reciprocal Rank | 平均倒数排名 |
| BM25 | Best Match 25 | 经典全文检索排序算法 |
| SSE | Server-Sent Events | 服务端推送事件 |
| JWT | JSON Web Token | JSON Web 令牌 |
| RBAC | Role-Based Access Control | 基于角色的访问控制 |

### C. 文件清单

- **后端**: 180+ Java 源文件
- **前端**: 1 个 Pinia Store 文件（骨架阶段）
- **配置文件**: `application.yml`, `vite.config.ts`, `docker-compose.yml`
- **数据库迁移**: Flyway (`classpath:db/migration`)
- **部署脚本**: `deploy.sh`, `deploy.ps1`, `deploy-package.ps1`
