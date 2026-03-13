# Nginx 配置示例

本文档提供了完整的 Nginx 配置示例，用于部署 sdh-rag 项目。

## 基础配置

### HTTP 配置 (nginx-http.conf)

```nginx
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
    use epoll;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    # 日志格式
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';

    access_log /var/log/nginx/access.log main;

    # 性能优化
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;
    client_max_body_size 100M;

    # Gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css text/xml text/javascript
               application/json application/javascript application/xml+rss
               application/rss+xml font/truetype font/opentype
               application/vnd.ms-fontobject image/svg+xml;
    gzip_disable "msie6";

    # 包含站点配置
    include /etc/nginx/conf.d/*.conf;
}
```

### 站点配置 (sdh-rag.conf)

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 字符集
    charset utf-8;

    # 访问日志
    access_log /var/log/nginx/sdh-rag-access.log;
    error_log /var/log/nginx/sdh-rag-error.log;

    # 前端静态资源
    location / {
        root /var/www/sdh-rag/dist;
        index index.html;
        try_files $uri $uri/ /index.html;

        # 静态资源缓存配置
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
            access_log off;
        }

        # HTML 文件不缓存
        location ~* \.html$ {
            add_header Cache-Control "no-cache, no-store, must-revalidate";
            add_header Pragma "no-cache";
            add_header Expires "0";
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
        proxy_busy_buffers_size 8k;

        # WebSocket 支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # 文件上传大小限制
    client_max_body_size 100M;

    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # 禁止访问隐藏文件
    location ~ /\. {
        deny all;
        access_log off;
        log_not_found off;
    }
}
```

## HTTPS 配置

### 使用 Let's Encrypt 证书

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # Let's Encrypt 验证路径
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # 其他请求重定向到 HTTPS
    location / {
        return 301 https://$server_name$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    # SSL 证书路径
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    # SSL 协议和加密套件
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers off;

    # SSL 会话缓存
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_session_tickets off;

    # OCSP Stapling
    ssl_stapling on;
    ssl_stapling_verify on;
    ssl_trusted_certificate /etc/letsencrypt/live/your-domain.com/chain.pem;

    # HSTS（可选）
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # 其他配置同 HTTP 配置
    charset utf-8;

    # 前端静态资源
    location / {
        root /var/www/sdh-rag/dist;
        index index.html;
        try_files $uri $uri/ /index.html;

        # 静态资源缓存配置
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
            access_log off;
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

        # WebSocket 支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # 文件上传大小限制
    client_max_body_size 100M;

    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # 禁止访问隐藏文件
    location ~ /\. {
        deny all;
        access_log off;
        log_not_found off;
    }
}
```

## 负载均衡配置

### 后端服务负载均衡

```nginx
upstream backend_servers {
    # 负载均衡算法：least_conn（最少连接）
    least_conn;

    # 后端服务器列表
    server backend1:8989 weight=3;
    server backend2:8989 weight=2;
    server backend3:8989 weight=1;

    # 健康检查
    keepalive 32;
}

server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    location / {
        root /var/www/sdh-rag/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理（负载均衡）
    location /api/ {
        proxy_pass http://backend_servers/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 连接保持
        proxy_http_version 1.1;
        proxy_set_header Connection "";

        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # 其他配置...
}
```

## 部署步骤

### 1. 安装 Nginx

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install nginx

# CentOS/RHEL
sudo yum install nginx

# 验证安装
nginx -v
```

### 2. 配置 Nginx

```bash
# 复制配置文件
sudo cp nginx-http.conf /etc/nginx/nginx.conf
sudo cp sdh-rag.conf /etc/nginx/conf.d/sdh-rag.conf

# 测试配置
sudo nginx -t

# 重载 Nginx
sudo nginx -s reload
```

### 3. 部署前端

```bash
# 构建前端项目
cd rag-frontend
npm run build

# 复制构建产物
sudo cp -r dist/* /var/www/sdh-rag/dist/

# 设置权限
sudo chown -R nginx:nginx /var/www/sdh-rag
```

### 4. 配置 SSL（可选）

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

## 性能优化

### 1. 启用 HTTP/2

```nginx
listen 443 ssl http2;
```

### 2. 配置缓存

```nginx
# 静态资源长期缓存
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}

# API 响应缓存（可选）
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:10m max_size=1g inactive=60m;

location /api/ {
    proxy_cache api_cache;
    proxy_cache_valid 200 5m;
    proxy_cache_key "$scheme$request_method$host$request_uri";
}
```

### 3. 限制请求速率

```nginx
# 限制 IP 请求频率
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

location /api/ {
    limit_req zone=api_limit burst=20 nodelay;
    # 其他配置...
}
```

### 4. 启用 Brotli 压缩（需要安装模块）

```nginx
brotli on;
brotli_comp_level 6;
brotli_types text/plain text/css text/xml text/javascript
             application/json application/javascript application/xml+rss
             application/rss+xml font/truetype font/opentype
             application/vnd.ms-fontobject image/svg+xml;
```

## 监控和日志

### 1. 访问日志分析

```bash
# 查看实时访问日志
sudo tail -f /var/log/nginx/sdh-rag-access.log

# 统计访问量
sudo awk '{print $1}' /var/log/nginx/sdh-rag-access.log | sort | uniq -c | sort -nr

# 统计状态码
sudo awk '{print $9}' /var/log/nginx/sdh-rag-access.log | sort | uniq -c | sort -nr
```

### 2. 错误日志分析

```bash
# 查看错误日志
sudo tail -f /var/log/nginx/sdh-rag-error.log

# 统计错误类型
sudo grep -i error /var/log/nginx/sdh-rag-error.log | wc -l
```

### 3. 性能监控

```bash
# 查看 Nginx 状态（需要配置 stub_status）
curl http://localhost/nginx_status
```

## 故障排查

### 1. 配置测试失败

```bash
# 查看详细错误信息
sudo nginx -t

# 检查配置文件语法
sudo nginx -T
```

### 2. 502 Bad Gateway

- 检查后端服务是否运行
- 检查 proxy_pass 配置是否正确
- 检查防火墙设置

### 3. 504 Gateway Timeout

- 增加 proxy_read_timeout
- 检查后端服务响应时间
- 优化后端性能

### 4. 静态资源 404

- 检查 root 路径是否正确
- 检查文件权限
- 检查 try_files 配置

## 安全建议

1. **隐藏 Nginx 版本**:
   ```nginx
   server_tokens off;
   ```

2. **限制请求方法**:
   ```nginx
   if ($request_method !~ ^(GET|HEAD|POST|PUT|DELETE|OPTIONS)$) {
       return 405;
   }
   ```

3. **防止 DDoS**:
   ```nginx
   limit_conn_zone $binary_remote_addr zone=conn_limit:10m;
   limit_conn conn_limit 10;
   ```

4. **配置 WAF**（可选）:
   ```bash
   # 安装 ModSecurity
   sudo apt install libnginx-mod-security
   ```

## 备份和恢复

### 备份配置

```bash
# 备份 Nginx 配置
sudo tar -czf nginx-backup-$(date +%Y%m%d).tar.gz /etc/nginx

# 备份日志
sudo tar -czf nginx-logs-backup-$(date +%Y%m%d).tar.gz /var/log/nginx
```

### 恢复配置

```bash
# 恢复配置
sudo tar -xzf nginx-backup-YYYYMMDD.tar.gz -C /

# 重载 Nginx
sudo nginx -s reload
```
