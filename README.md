# SDH-RAG 智能知识库管理系统

基于 Spring AI 和 Vue 3 的企业级 RAG（检索增强生成）知识库管理系统，提供智能问答、文档管理、知识检索等功能。

## 项目简介

SDH-RAG 是一个功能完整的智能知识库管理系统，集成了以下核心能力：

- **智能问答**: 基于大语言模型的智能对话，支持上下文记忆
- **文档管理**: 支持多种格式的文档上传、解析和分类管理
- **知识检索**: 基于 Elasticsearch 的向量检索，提供精准的知识匹配
- **用户管理**: 完整的用户认证授权体系，支持多角色管理
- **会话管理**: 支持多会话切换，保存完整的对话历史

## 技术栈

### 后端技术

- **Java 17**: 基础开发语言
- **Spring Boot 3.x**: 应用框架
- **Spring AI**: AI 集成框架
- **通义千问**: 大语言模型服务
- **Elasticsearch**: 向量数据库和全文检索
- **MySQL 8.0**: 关系型数据库
- **Redis 6.0**: 缓存和会话存储
- **MyBatis Plus**: ORM 框架
- **JWT**: 用户认证
- **Druid**: 数据库连接池

### 前端技术

- **Vue 3**: 前端框架
- **TypeScript**: 类型安全
- **Vite**: 构建工具
- **Element Plus**: UI 组件库
- **Pinia**: 状态管理
- **Vue Router**: 路由管理
- **Axios**: HTTP 客户端
- **Marked**: Markdown 解析
- **Highlight.js**: 代码高亮

### 基础设施

- **Docker**: 容器化部署
- **Docker Compose**: 多容器编排
- **Nginx**: 反向代理和静态资源服务

## 功能特性

### 核心功能

#### 1. 智能问答
- 支持自然语言提问
- 基于知识库的精准回答
- 引用来源展示
- 支持多轮对话
- 会话历史管理

#### 2. 文档管理
- 支持多种文档格式（PDF、Word、TXT 等）
- 文档自动解析和分块
- 文档分类管理
- 文档上传和下载
- 文档状态跟踪

#### 3. 知识检索
- 向量相似度检索
- 全文检索
- 混合检索策略
- 相关性排序
- 结果高亮显示

#### 4. 用户管理
- 用户注册和登录
- JWT 认证
- 角色权限管理
- 用户信息管理
- 密码加密存储

#### 5. 系统管理
- 分类管理
- 系统配置
- 日志查看
- 数据统计

### 技术特性

- **高性能**: 基于 Elasticsearch 的向量检索，毫秒级响应
- **可扩展**: 微服务架构，支持水平扩展
- **易部署**: Docker 容器化，一键部署
- **高可用**: 支持负载均衡和故障转移
- **安全性**: JWT 认证，HTTPS 加密，SQL 注入防护
- **易维护**: 完善的日志和监控体系

## 快速开始

### 环境要求

- **Java**: JDK 17+
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

# 编辑 .env 文件，配置必要的环境变量
# 必须配置：DASHSCOPE_API_KEY（通义千问 API 密钥）
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

- `DASHSCOPE_API_KEY`: 通义千问 API 密钥（必须）

### 可选配置的环境变量

- `DB_USERNAME`: 数据库用户名（默认: rag_user）
- `DB_PASSWORD`: 数据库密码（默认: rag_password）
- `REDIS_PASSWORD`: Redis 密码（默认: 空）
- `JWT_ADMIN_SECRET`: JWT 管理员密钥
- `JWT_USER_SECRET`: JWT 用户密钥

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
- [Element Plus](https://element-plus.org/)
- [Elasticsearch](https://www.elastic.co/)
- [通义千问](https://tongyi.aliyun.com/)

## 更新日志

### v1.0.0 (2024-01-01)

- 初始版本发布
- 实现基础问答功能
- 实现文档管理功能
- 实现用户认证授权
- 完善部署文档
