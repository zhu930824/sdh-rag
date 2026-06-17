# 技术栈与前端UI规范文档

## 一、后端技术栈

### 1. 核心框架
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 编程语言 |
| Spring Boot | 3.4 | 核心框架 |
| Spring AI | 1.1.2 | AI集成框架 |
| Spring AI Alibaba (DashScope) | 1.1.2.0 | 通义千问集成 |

### 2. 数据层
| 技术 | 版本 | 说明 |
|------|------|------|
| MyBatis Plus | - | ORM框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存/会话存储 |
| Elasticsearch | 8.0+ | 向量数据库 |
| Neo4j | - | 知识图谱数据库 |
| Druid | - | 数据库连接池 |
| Flyway | - | 数据库迁移工具 |

### 3. AI/ML组件
| 技术 | 说明 |
|------|------|
| DashScope (通义千问) | LLM服务 |
| IK Analyzer | 中文分词 |
| Spring AI Vector Store | 向量检索 |
| Spring AI Advisor | RAG增强机制 |

### 4. 其他组件
| 技术 | 说明 |
|------|------|
| JWT (jjwt) | 身份认证 |
| MinIO | 对象存储 |
| EasyExcel | Excel导入导出 |
| Hutool | Java工具库 |
| FastJSON2 | JSON处理 |
| Lombok | 代码简化 |

### 5. 文档处理
| 技术 | 说明 |
|------|------|
| Apache Tika | 文档解析 |
| Apache PDFBox | PDF处理 |

---

## 二、前端技术栈

### 1. 核心框架
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.4.21 | 渐进式JS框架 |
| TypeScript | 5.4.2 | 类型安全的JS超集 |
| Vite | 5.2.0 | 下一代构建工具 |

### 2. UI组件库
| 技术 | 版本 | 说明 |
|------|------|------|
| Ant Design Vue | 4.2.6 | 企业级UI组件库 |
| @ant-design/icons-vue | 7.0.1 | 图标库 |

### 3. 状态与路由
| 技术 | 版本 | 说明 |
|------|------|------|
| Pinia | 2.1.7 | Vue官方状态管理 |
| Vue Router | 4.3.0 | 路由管理 |

### 4. 数据可视化与流程图
| 技术 | 版本 | 说明 |
|------|------|------|
| @antv/g6 | 4.8.25 | 图可视化引擎（知识图谱） |
| @vue-flow/core | 1.48.2 | 工作流画布 |
| @vue-flow/background | 1.3.2 | 画布背景 |
| @vue-flow/controls | 1.1.3 | 画布控件 |
| @vue-flow/minimap | 1.5.4 | 小地图导航 |

### 5. 工具库
| 技术 | 版本 | 说明 |
|------|------|------|
| Axios | 1.6.8 | HTTP请求库 |
| Marked | 17.0.4 | Markdown解析 |
| Highlight.js | 11.11.1 | 代码高亮 |
| Sass | 1.72.0 | CSS预处理器 |

### 6. 构建优化
| 技术 | 说明 |
|------|------|
| unplugin-auto-import | Vue/Router/Pinia API自动导入 |
| unplugin-vue-components | Ant Design Vue组件自动导入 |
| Terser | 代码压缩（移除console/debugger） |

---

## 三、前端UI设计规范

### 1. 设计风格
**Modern SaaS Analytics + Glassmorphism（玻璃拟态）**

- 清爽现代的SaaS分析平台风格
- 玻璃拟态效果增强层次感
- 专业但不沉闷的视觉体验

### 2. 配色方案

#### 主色调 - Modern Indigo-Violet
```css
--primary-color: #6366F1;      /* 主色：靛蓝紫 */
--primary-hover: #4F46E5;      /* 悬停态 */
--primary-active: #4338CA;     /* 激活态 */
--primary-light: #C7D2FE;      /* 浅色 */
--primary-lighter: #EEF2FF;    /* 更浅 */
--primary-gradient: linear-gradient(135deg, #6366F1 0%, #8B5CF6 50%, #A855F7 100%);
```

#### 辅助色 - Emerald（成功状态）
```css
--secondary-color: #10B981;
--secondary-hover: #059669;
```

#### 强调色
```css
--accent-pink: #EC4899;    --accent-orange: #F97316;
--accent-cyan: #06B6D4;   --accent-amber: #F59E0B;
--accent-rose: #F43F5E;   --accent-sky: #0EA5E9;
```

#### 语义色
```css
--success-color: #10B981;
--warning-color: #F59E0B;
--danger-color: #EF4444;
--info-color: #0EA5E9;
```

#### 中性灰阶
```css
--gray-50: #F8FAFC;   --gray-100: #F1F5F9;
--gray-200: #E2E8F0;  --gray-300: #CBD5E1;
--gray-400: #94A3B8;  --gray-500: #64748B;
--gray-600: #475569;  --gray-700: #334155;
--gray-800: #1E293B;  --gray-900: #0F172A;
```

### 3. 暗色模式
项目支持完整的暗色模式，通过 `html.dark` 类切换：

```css
html.dark {
  --bg-body: #0F172A;        /* 深空背景 */
  --bg-page: #1E293B;        /* 页面背景 */
  --text-primary: #F8FAFC;   /* 主文本 */
  --text-secondary: #94A3B8; /* 次要文本 */
}
```

### 4. 字体规范

#### 字体族
```css
--font-display: 'Space Grotesk', sans-serif;  /* 标题字体 */
--font-sans: 'Inter', sans-serif;            /* 正文字体 */
```

#### 字号体系
```css
--font-size-xs: 11px;
--font-size-sm: 13px;
--font-size-base: 14px;   /* 基准 */
--font-size-md: 15px;
--font-size-lg: 16px;
--font-size-xl: 18px;
--font-size-2xl: 20px;
--font-size-3xl: 24px;
--font-size-4xl: 30px;
--font-size-5xl: 36px;
--font-size-6xl: 48px;
```

#### 字重
```css
--font-weight-normal: 400;
--font-weight-medium: 500;
--font-weight-semibold: 600;
--font-weight-bold: 700;
--font-weight-extrabold: 800;
```

### 5. 间距体系
```css
--space-1: 4px;   --space-2: 8px;
--space-3: 12px;  --space-4: 16px;
--space-5: 20px;  --space-6: 24px;
--space-8: 32px;  --space-10: 40px;
--space-12: 48px; --space-16: 64px;
```

### 6. 圆角体系
```css
--radius-xs: 4px;    --radius-sm: 6px;
--radius-md: 10px;   --radius-lg: 14px;
--radius-xl: 18px;   --radius-2xl: 24px;
--radius-3xl: 32px;  --radius-full: 9999px;
```

### 7. 阴影体系
```css
--shadow-card: 0 1px 3px rgba(15,23,42,0.04), 0 1px 2px rgba(15,23,42,0.03);
--shadow-card-hover: 0 10px 40px -10px rgba(99,102,241,0.15), 0 4px 6px -4px rgba(15,23,42,0.05);
--shadow-glow: 0 0 40px rgba(99,102,241,0.15);
```

### 8. 布局规范

#### 布局尺寸
```css
--sidebar-width: 260px;          /* 侧边栏宽度 */
--sidebar-collapsed-width: 72px;  /* 收起宽度 */
--header-height: 64px;           /* 头部高度 */
--card-gap: 20px;                /* 卡片间距 */
--page-padding: 24px;            /* 页面内边距 */
```

#### 响应式断点
| 断点 | 范围 | 说明 |
|------|------|------|
| Mobile | ≤640px | 移动端，单列布局 |
| Tablet | 641px-1024px | 平板，双列布局 |
| Desktop | ≥1025px | 桌面端，多列布局 |

### 9. 动画规范

#### 过渡时长
```css
--duration-fast: 150ms;    /* 快速交互 */
--duration-normal: 200ms; /* 常规过渡 */
--duration-slow: 300ms;   /* 慢速动画 */
```

#### 缓动函数
```css
--ease-default: cubic-bezier(0.4, 0, 0.2, 1);
--ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);  /* 弹性效果 */
```

### 10. 组件规范

#### 卡片（Card）
```scss
.ant-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);  // 18px
  box-shadow: var(--shadow-card);

  &:hover {
    box-shadow: var(--shadow-card-hover);
  }
}
```

#### 按钮（Button）
```scss
.ant-btn-primary {
  background: var(--primary-gradient);
  border: none;
  border-radius: var(--radius-md);  // 10px
  box-shadow: 0 2px 8px rgba(99,102,241,0.25);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 16px rgba(99,102,241,0.35);
  }
}
```

#### 输入框（Input）
```scss
.ant-input {
  background: var(--bg-surface-secondary);
  border: 1px solid transparent;
  border-radius: var(--radius-md);  // 10px

  &:focus {
    background: var(--bg-surface);
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(99,102,241,0.1);
  }
}
```

---

## 四、项目结构

### 后端目录结构
```
rag-backend/
├── src/main/java/cn/sdh/backend/
│   ├── common/          # 通用工具、异常、上下文
│   ├── config/          # Spring配置（CORS、MyBatis、ES、Neo4j）
│   ├── controller/      # REST API端点
│   ├── dto/             # 数据传输对象
│   ├── entity/          # JPA实体（数据库模型）
│   ├── graph/           # 知识图谱（节点、关系、提取器、仓库）
│   ├── interceptor/     # 请求拦截器（JWT认证）
│   ├── mapper/          # MyBatis Plus Mapper
│   ├── service/         # 业务逻辑层
│   │   └── impl/       # 服务实现
│   └── utils/           # 工具类
└── src/main/resources/
    └── db/migration/    # Flyway迁移脚本
```

### 前端目录结构
```
rag-frontend/
├── src/
│   ├── api/             # Axios API客户端模块
│   ├── components/      # 可复用Vue组件
│   ├── composables/     # Vue组合式函数
│   ├── layouts/         # 页面布局
│   │   └── components/ # 布局组件（Sidebar、Header、Tabs）
│   ├── router/          # Vue Router配置
│   ├── stores/          # Pinia状态管理
│   ├── styles/          # 全局样式（index.scss）
│   ├── types/           # TypeScript类型定义
│   ├── utils/           # 工具函数
│   └── views/           # 页面组件（一个路由一个目录）
└── vite.config.ts       # Vite配置（代理、自动导入）
```

---

## 五、API规范

- **后端地址**: `http://localhost:8989`
- **前端开发代理**: `/api/*` → 后端
- **认证方式**: JWT Token（`token` header/cookie）
- **响应格式**: 统一封装（`common/result/`）

## 
