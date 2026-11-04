# SDH-RAG 企业知识与业务 Agent 平台

基于 Spring AI 和 Vue 3 的企业知识平台，覆盖知识接入、RAG 问答、质量评测、知识发布以及受控 Agent 业务执行。

当前采用 **Vue SPA + Spring Boot 模块化单体**，不是微服务架构。完整现状、数据流与演进方向见 [平台架构与设计](docs/architecture.md)。

## 当前能力

- **知识接入**：文件上传、API Push、带 API Key 的系统集成、REST JSON 定时增量同步。
- **知识加工**：去重与版本、结构化解析、多种分块、Embedding、摘要粗索引和知识图谱。
- **RAG 问答**：知识库意图路由、查询改写、Query Expansion、HyDE、向量与 BM25 混合检索、RRF、Rerank、引用与拒答。
- **知识治理**：知识库成员权限、草稿审核、索引发布、回归门禁、回滚与审计。
- **质量运营**：版本化测试集、检索/生成/完整评测、多变体实验、业务切片、定时回归、人工复核与 Webhook 告警。
- **可靠执行**：数据库任务队列、原子抢占、租约心跳、失败退避、取消、死信重放和任务中心。
- **Agent 执行**：业务工具注册、JSON Schema 参数、风险分级、人工审批、幂等调用、运行轨迹和业务结果反馈。
- **工作流与记忆**：DAG 工作流、审批后续跑，以及 Working/Episodic/Semantic/Abstract/Pattern 多层记忆。

## 技术栈

### 后端

- Java 21、Spring Boot 3.5.2、Spring AI 1.1.2
- MyBatis Plus、Flyway、Druid、JWT
- DashScope/OpenAI 兼容 Chat、Embedding 与 Rerank 模型

### 前端

- Vue 3、TypeScript、Vite
- Ant Design Vue、Pinia、Vue Router
- AntV G6、Vue Flow

### 基础设施

- MySQL 8：业务主数据
- Elasticsearch 8：全文、向量、摘要和语义记忆索引
- Redis：缓存、工作记忆、限流和分布式锁
- Neo4j：知识图谱和模式记忆
- MinIO：原始文档与发布文件
- Docker Compose、Nginx

## 架构文档

- [AgentScope 2.x 架构升级方案](docs/agentscope-2-architecture-upgrade.md)
- [AgentScope 运行时实施与启用](docs/agentscope-runtime-implementation.md)
- [可靠 Agent 动作与后台续跑（V32）](docs/reliable-agent-action-runtime.md)
- [Agent 执行租约与中断识别（V33）](docs/agent-run-execution-lease.md)
- [Agent 安全恢复与聊天入口（V34）](docs/agent-run-safe-recovery.md)
- [Agent 累计预算与取消状态完善](docs/agent-run-budget-and-cancellation.md)
- [真实 MySQL 与进程故障验收（P0-1）](docs/agent-mysql-fault-acceptance.md)
- [Agent 运行生命周期收敛（P0-2）](docs/agent-run-lifecycle-refactoring.md)
- [平台架构与设计](docs/architecture.md)
- [平台升级方向与演进路线图](docs/platform-evolution-roadmap.md)
- [P0 安全加固与部署迁移指南](docs/security-hardening-guide.md)
- [P1 可靠任务运行时](docs/reliable-job-runtime.md)
- [RAG 检索设计](docs/rag-retrieval-design.md)
- [知识源连接器](docs/knowledge-source-connectors.md)
- [知识发布流水线](docs/knowledge-publishing-pipeline.md)
- [分层 RAG 评测](docs/layered-rag-evaluation-implementation.md)
- [Agent 业务闭环](docs/agent-business-loop.md)

## 快速开始

### 环境要求

- **Java**: JDK 21+
- **Node.js**: 18+
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **Elasticsearch**: 8.0+

### Docker 部署（推荐）

#### 1. 克隆项目

```bash
git clone https://github.com/your-username/sdh-rag.git
cd sdh-rag
```

#### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，配置数据库、Neo4j、MinIO 和 JWT 密钥
# 使用云模型时再配置 DASHSCOPE_API_KEY 或 OPENAI_API_KEY
```

#### 3. 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

#### 4. 访问应用

- **前端地址**: http://localhost
- **后端 API**: http://localhost:8989
- **默认账号**: admin / 123456

### 本地开发

#### 后端开发

```bash
# 进入后端目录
cd rag-backend

# 配置数据库
# 修改 src/main/resources/application.yml 中的数据库配置

# 初始化数据库
mysql -u root -p < src/main/resources/db/init.sql

# 启动应用
mvn spring-boot:run
```

#### 前端开发

```bash
# 进入前端目录
cd rag-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

访问 http://localhost:3000 开始使用。

## 项目结构

```
sdh-rag/
├── rag-backend/                 # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── cn/sdh/backend/
│   │   │   │       ├── common/          # 公共模块
│   │   │   │       │   ├── context/     # 用户上下文
│   │   │   │       │   ├── exception/    # 异常处理
│   │   │   │       │   └── result/       # 统一响应
│   │   │   │       ├── config/          # 配置类
│   │   │   │       ├── controller/      # 控制器
│   │   │   │       ├── dto/             # 数据传输对象
│   │   │   │       ├── entity/          # 实体类
│   │   │   │       ├── interceptor/     # 拦截器
│   │   │   │       ├── mapper/          # 数据访问层
│   │   │   │       ├── service/         # 业务逻辑层
│   │   │   │       └── utils/           # 工具类
│   │   │   └── resources/
│   │   │       ├── db/                  # 数据库脚本
│   │   │       ├── application.yml      # 主配置文件
│   │   │       └── application-dev.yml  # 开发环境配置
│   │   └── test/                        # 测试代码
│   ├── Dockerfile                       # Docker 镜像构建文件
│   ├── pom.xml                          # Maven 配置文件
│   └── DEPLOYMENT.md                    # 后端部署文档
│
├── rag-frontend/                # 前端项目
│   ├── src/
│   │   ├── api/                 # API 接口
│   │   ├── assets/              # 静态资源
│   │   ├── components/          # 公共组件
│   │   ├── composables/         # 组合式函数
│   │   ├── layouts/             # 布局组件
│   │   ├── router/              # 路由配置
│   │   ├── stores/              # 状态管理
│   │   ├── styles/              # 全局样式
│   │   ├── types/               # TypeScript 类型定义
│   │   ├── utils/               # 工具函数
│   │   ├── views/               # 页面组件
│   │   ├── App.vue              # 根组件
│   │   └── main.ts              # 入口文件
│   ├── Dockerfile               # Docker 镜像构建文件
│   ├── nginx.conf               # Nginx 配置文件
│   ├── package.json             # 项目依赖
│   ├── vite.config.ts           # Vite 配置
│   └── DEPLOYMENT.md            # 前端部署文档
│
├── nginx/                       # Nginx 配置
│   └── README.md                # Nginx 配置文档
│
├── docker-compose.yml           # Docker Compose 配置
├── .env.example                 # 环境变量模板
├── .gitignore                   # Git 忽略文件
└── README.md                    # 项目说明文档
```

## 部署文档

详细的部署文档请参考：

- [后端部署文档](rag-backend/DEPLOYMENT.md)
- [前端部署文档](rag-frontend/DEPLOYMENT.md)
- [Nginx 配置文档](nginx/README.md)

## 配置说明

### 必须配置的环境变量

- `DB_PASSWORD`：数据库密码
- `NEO4J_PASSWORD`：Neo4j 密码
- `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`：MinIO 凭据
- `JWT_ADMIN_SECRET`、`JWT_USER_SECRET`：两个不同且至少 32 个随机字符的 JWT 密钥

### 可选配置的环境变量

- `DASHSCOPE_API_KEY`、`OPENAI_API_KEY`：按实际模型供应商配置
- `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`：数据库连接信息
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`：Redis 连接信息
- `ELASTICSEARCH_URI`、`NEO4J_URI`、`MINIO_ENDPOINT`：基础设施地址
- `WEBHOOK_ALLOW_HTTP`、`WEBHOOK_ALLOW_PRIVATE_NETWORK`：Webhook 网络例外，生产环境应保持关闭

## 常见问题

### 1. 启动失败

检查以下内容：
- 所有依赖服务是否正常启动（MySQL、Redis、Elasticsearch）
- 环境变量是否正确配置
- 端口是否被占用

### 2. API 请求失败

- 检查后端服务是否正常运行
- 检查网络连接
- 查看 Nginx 代理配置

### 3. 文档上传失败

- 检查文件大小限制
- 检查磁盘空间
- 查看后端日志

## 开发指南

### 后端开发

```bash
# 运行测试
mvn test

# 代码格式化
mvn spotless:apply

# 构建项目
mvn clean package
```

### 前端开发

```bash
# 代码检查
npm run lint

# 类型检查
npm run type-check

# 构建生产版本
npm run build
```

Agent 运行时升级前可执行 `./scripts/test-agent-regression.ps1`，运行 30 条固定模型响应的 SDK 场景检查。执行参数、报告及覆盖边界见 [Agent 场景回归集](docs/agent-scenario-regression.md)。

评测页的“Agent 场景与结果”支持导入不可变场景版本并归档已结束运行，使用方法与快照边界见 [场景版本与运行结果归档](docs/agent-evaluation-archives.md)。

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

- 项目主页: https://github.com/your-username/sdh-rag
- 问题反馈: https://github.com/your-username/sdh-rag/issues
- 邮箱: your-email@example.com

## 致谢

感谢以下开源项目：

- [Spring AI](https://spring.io/projects/spring-ai)
- [Vue.js](https://vuejs.org/)
- [Ant Design Vue](https://antdv.com/)
- [Elasticsearch](https://www.elastic.co/)
- [通义千问](https://tongyi.aliyun.com/)

## 更新日志

### v1.0.0 (2024-01-01)

- 初始版本发布
- 实现基础问答功能
- 实现文档管理功能
- 实现用户认证授权
- 完善部署文档
