# 前端部署文档

## 环境要求

### 基础环境
- **Node.js**: 18.0 或更高版本
- **npm**: 9.0 或更高版本（或使用 pnpm/yarn）
- **Git**: 用于版本控制

### 推荐工具
- **pnpm**: 更快的包管理器（可选）
- **yarn**: 另一个流行的包管理器（可选）

## 安装依赖命令

### 使用 npm（默认）

```bash
# 进入前端目录
cd d:/workspace/code/sdh-rag/rag-frontend

# 安装依赖
npm install

# 如果安装速度慢，可以使用国内镜像
npm install --registry=https://registry.npmmirror.com
```

### 使用 pnpm（推荐）

```bash
# 安装 pnpm（如果尚未安装）
npm install -g pnpm

# 安装依赖
pnpm install
```

### 使用 yarn

```bash
# 安装 yarn（如果尚未安装）
npm install -g yarn

# 安装依赖
yarn install
```

## 环境变量配置

### 开发环境 (.env.development)

```bash
# 后端 API 地址
VITE_API_BASE_URL=http://localhost:8989

# 应用标题
VITE_APP_TITLE=智能知识库管理系统
```

### 生产环境 (.env.production)

```bash
# 生产环境使用相对路径，由 Nginx 代理
VITE_API_BASE_URL=/

# 应用标题
VITE_APP_TITLE=智能知识库管理系统
```

### 自定义环境变量

创建 `.env.local` 文件（会被 git 忽略）：

```bash
# 本地开发配置
VITE_API_BASE_URL=http://localhost:8989
VITE_APP_TITLE=智能知识库管理系统（本地）
```

## 开发环境启动

### 启动开发服务器

```bash
# 使用 npm
npm run dev

# 使用 pnpm
pnpm dev

# 使用 yarn
yarn dev
```

开发服务器将在 `http://localhost:3000` 启动。

### 开发服务器配置

开发服务器已在 `vite.config.ts` 中配置：

```typescript
server: {
  port: 3000,        // 服务端口
  open: true,        // 自动打开浏览器
  proxy: {
    '/api': {
      target: 'http://localhost:8989',  // 后端地址
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, ''),
    },
  },
}
```

### 开发环境特性

- **热模块替换 (HMR)**: 代码修改后自动刷新
- **自动导入**: Vue、Vue Router、Pinia 和 Element Plus 组件自动导入
- **TypeScript 支持**: 完整的类型检查
- **路径别名**: 使用 `@` 指向 `src` 目录

## 生产环境构建

### 构建命令

```bash
# 使用 npm
npm run build

# 使用 pnpm
pnpm build

# 使用 yarn
yarn build
```

### 构建输出

构建完成后，生成的文件将在 `dist` 目录中：

```
dist/
├── index.html
├── assets/
│   ├── css/
│   │   └── index-[hash].css
│   └── js/
│       ├── vue-[hash].js
│       ├── elementPlus-[hash].js
│       └── index-[hash].js
└── favicon.ico
```

### 构建优化

生产构建已配置以下优化：

1. **代码压缩**: 使用 Terser 压缩 JavaScript
2. **移除调试代码**: 自动移除 console 和 debugger
3. **代码分割**: 将 Vue、Element Plus 等库单独打包
4. **哈希命名**: 文件名包含哈希值，便于缓存控制
5. **Tree Shaking**: 移除未使用的代码

### 构建配置

```typescript
build: {
  outDir: 'dist',           // 输出目录
  sourcemap: false,         // 不生成 source map
  minify: 'terser',         // 使用 Terser 压缩
  terserOptions: {
    compress: {
      drop_console: true,   // 移除 console
      drop_debugger: true,  // 移除 debugger
    },
  },
  rollupOptions: {
    output: {
      manualChunks: {
        vue: ['vue', 'vue-router', 'pinia'],
        elementPlus: ['element-plus', '@element-plus/icons-vue'],
      },
    },
  },
}
```

### 预览构建结果

```bash
# 使用 npm
npm run preview

# 使用 pnpm
pnpm preview

# 使用 yarn
yarn preview
```

## Nginx 配置示例

### 基础配置

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    location / {
        root /var/www/sdh-rag/dist;
        index index.html;
        try_files $uri $uri/ /index.html;

        # 缓存配置
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }

        # HTML 文件不缓存
        location ~* \.html$ {
            add_header Cache-Control "no-cache, no-store, must-revalidate";
        }
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://localhost:8989/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;

        # 缓冲设置
        proxy_buffering on;
        proxy_buffer_size 4k;
        proxy_buffers 8 4k;
    }

    # 文件上传大小限制
    client_max_body_size 100M;

    # Gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/xml text/javascript
               application/json application/javascript application/xml+rss
               application/rss+xml font/truetype font/opentype
               application/vnd.ms-fontobject image/svg+xml;
}
```

### HTTPS 配置

```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    # SSL 证书配置
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    # SSL 优化配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # 其他配置同上...
}

# HTTP 重定向到 HTTPS
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}
```

### 部署步骤

1. **构建前端项目**:
   ```bash
   npm run build
   ```

2. **上传构建产物**:
   ```bash
   # 将 dist 目录上传到服务器
   scp -r dist/* user@server:/var/www/sdh-rag/dist/
   ```

3. **配置 Nginx**:
   ```bash
   # 复制配置文件
   sudo cp nginx.conf /etc/nginx/sites-available/sdh-rag

   # 创建软链接
   sudo ln -s /etc/nginx/sites-available/sdh-rag /etc/nginx/sites-enabled/

   # 测试配置
   sudo nginx -t

   # 重载 Nginx
   sudo nginx -s reload
   ```

## 常见问题排查

### 1. 依赖安装失败

**问题**: `npm install` 报错

**解决方案**:
```bash
# 清除缓存
npm cache clean --force

# 删除 node_modules 和 package-lock.json
rm -rf node_modules package-lock.json

# 重新安装
npm install

# 或使用国内镜像
npm install --registry=https://registry.npmmirror.com
```

### 2. 开发服务器启动失败

**问题**: `Port 3000 is already in use`

**解决方案**:
```bash
# Windows 查找占用端口的进程
netstat -ano | findstr :3000

# 结束进程
taskkill /PID <PID> /F

# 或修改 vite.config.ts 中的端口
```

### 3. 构建失败

**问题**: TypeScript 类型错误

**解决方案**:
```bash
# 跳过类型检查进行构建
npm run build -- --skipLibCheck

# 或修复类型错误后重新构建
```

### 4. API 请求失败

**问题**: 前端无法连接后端 API

**解决方案**:
- 检查环境变量 `VITE_API_BASE_URL` 是否正确
- 确认后端服务是否启动
- 检查 Nginx 代理配置
- 查看浏览器控制台的网络请求详情

### 5. 页面刷新 404

**问题**: 刷新页面后出现 404 错误

**解决方案**:
确保 Nginx 配置中包含 `try_files $uri $uri/ /index.html;`

### 6. 样式丢失

**问题**: 页面样式未加载

**解决方案**:
- 检查构建是否成功
- 确认 Nginx 静态资源路径配置正确
- 检查浏览器控制台是否有资源加载错误

### 7. Element Plus 图标不显示

**问题**: Element Plus 图标无法显示

**解决方案**:
- 确认 `@element-plus/icons-vue` 已安装
- 检查组件自动导入配置
- 查看浏览器控制台错误信息

## 性能优化建议

### 1. 代码分割

已配置自动代码分割，将大型库单独打包：
- Vue 核心库
- Element Plus UI 库

### 2. 图片优化

- 使用 WebP 格式
- 压缩图片大小
- 使用懒加载

### 3. CDN 加速

将静态资源上传到 CDN：

```javascript
// vite.config.ts
export default defineConfig({
  base: 'https://cdn.your-domain.com/',
  // ...
})
```

### 4. 预加载关键资源

在 `index.html` 中添加预加载：

```html
<link rel="preload" href="/assets/js/index-[hash].js" as="script">
<link rel="preload" href="/assets/css/index-[hash].css" as="style">
```

### 5. 启用 HTTP/2

在 Nginx 中启用 HTTP/2：

```nginx
listen 443 ssl http2;
```

## 监控和日志

### 前端监控

建议集成以下监控工具：
- **Sentry**: 错误追踪
- **Google Analytics**: 用户行为分析
- **Lighthouse**: 性能分析

### 性能指标

关注以下指标：
- **首次内容绘制 (FCP)**: < 1.5s
- **最大内容绘制 (LCP)**: < 2.5s
- **首次输入延迟 (FID)**: < 100ms
- **累积布局偏移 (CLS)**: < 0.1

## 安全建议

1. **启用 HTTPS**: 使用 SSL/TLS 加密传输
2. **设置 CSP**: 配置内容安全策略
3. **XSS 防护**: Vue 已内置 XSS 防护
4. **CSRF 防护**: 后端已实现 CSRF 保护
5. **敏感信息**: 不要在前端代码中硬编码敏感信息

## CI/CD 集成

### GitHub Actions 示例

```yaml
name: Build and Deploy

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v2

    - name: Setup Node.js
      uses: actions/setup-node@v2
      with:
        node-version: '18'

    - name: Install dependencies
      run: npm install

    - name: Build
      run: npm run build

    - name: Deploy
      uses: peaceiris/actions-gh-pages@v3
      with:
        github_token: ${{ secrets.GITHUB_TOKEN }}
        publish_dir: ./dist
```

## 版本管理

建议使用语义化版本号：
- **主版本号**: 不兼容的 API 修改
- **次版本号**: 向下兼容的功能性新增
- **修订号**: 向下兼容的问题修正

示例：`1.0.0` → `1.1.0` → `1.1.1`
