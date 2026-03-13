# 后端部署文档

## 环境要求

### 基础环境
- **Java**: JDK 17 或更高版本
- **Maven**: 3.6+ (用于构建项目)
- **MySQL**: 8.0 或更高版本
- **Redis**: 6.0 或更高版本
- **Elasticsearch**: 8.0 或更高版本

### 硬件要求
- **CPU**: 4核心以上
- **内存**: 8GB 以上（建议 16GB）
- **磁盘**: 50GB 以上可用空间

## 配置文件说明

### application.yml 主配置文件

```yaml
server:
  port: 8989  # 服务端口

spring:
  servlet:
    multipart:
      max-file-size: 100MB  # 最大文件上传大小
      max-request-size: 100MB
  profiles:
    active: dev  # 激活的配置文件

  # AI 配置
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}  # 通义千问 API 密钥

  # Elasticsearch 配置
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:localhost:9200}
    vectorstore:
      elasticsearch:
        initialize-schema: true
        index-name: sdh-rag-index
        dimensions: 1536
        similarity: cosine

  # 数据库配置
  datasource:
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
    url: jdbc:mysql://${DB_HOST:localhost}:3306/sdh_rag?serverTimezone=UTC
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource

  # Redis 配置
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 4
          min-idle: 1
          max-wait: 2000ms

# JWT 配置
xushu:
  jwt:
    admin-secret-key: ${JWT_ADMIN_SECRET:your_admin_secret_key_here}
    admin-ttl: 7200000  # 2小时
    admin-token-name: token
    user-secret-key: ${JWT_USER_SECRET:your_user_secret_key_here}
    user-ttl: 7200000  # 2小时
    user-token-name: authorization
```

### application-dev.yml 开发环境配置

```yaml
xushu:
  alioss:
    endpoint: oss-cn-beijing.aliyuncs.com
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    bucket-name: xushu-rag
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
  websearch:
    api-key: ${WEBSEARCH_API_KEY}
```

## 数据库初始化步骤

### 1. 创建数据库

```bash
# 登录 MySQL
mysql -u root -p

# 执行初始化脚本
source d:/workspace/code/sdh-rag/rag-backend/src/main/resources/db/init.sql
```

### 2. 验证数据库

```bash
# 检查数据库是否创建成功
mysql -u root -p -e "SHOW DATABASES LIKE 'sdh_rag';"

# 检查表是否创建成功
mysql -u root -p sdh_rag -e "SHOW TABLES;"
```

### 3. 默认用户

系统会自动创建一个默认管理员用户：
- **用户名**: admin
- **密码**: 123456

**⚠️ 生产环境请立即修改默认密码！**

## 启动命令

### 开发环境

```bash
# 进入后端目录
cd d:/workspace/code/sdh-rag/rag-backend

# 使用 Maven 启动
mvn spring-boot:run

# 或者先打包再运行
mvn clean package
java -jar target/rag-backend-0.0.1-SNAPSHOT.jar
```

### 生产环境

```bash
# 打包项目
mvn clean package -DskipTests

# 运行 JAR 文件
java -jar target/rag-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.port=8989

# 后台运行（Linux）
nohup java -jar target/rag-backend-0.0.1-SNAPSHOT.jar > app.log 2>&1 &

# 指定 JVM 参数运行
java -Xms512m -Xmx2g \
  -jar target/rag-backend-0.0.1-SNAPSHOT.jar
```

### 环境变量配置

可以通过环境变量覆盖配置：

```bash
# Windows PowerShell
$env:DASHSCOPE_API_KEY="your_api_key"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:ELASTICSEARCH_URI="localhost:9200"
$env:REDIS_HOST="localhost"

# Linux/Mac
export DASHSCOPE_API_KEY="your_api_key"
export DB_USERNAME="root"
export DB_PASSWORD="your_password"
export ELASTICSEARCH_URI="localhost:9200"
export REDIS_HOST="localhost"
```

## 常见问题排查

### 1. 端口被占用

**问题**: `Port 8989 was already in use`

**解决方案**:
```bash
# Windows 查找占用端口的进程
netstat -ano | findstr :8989

# 结束进程（替换 PID）
taskkill /PID <PID> /F

# 或修改 application.yml 中的端口
```

### 2. 数据库连接失败

**问题**: `Communications link failure`

**解决方案**:
- 检查 MySQL 服务是否启动
- 验证数据库地址、端口、用户名、密码是否正确
- 检查防火墙设置
- 确认数据库 sdh_rag 是否存在

### 3. Redis 连接失败

**问题**: `Unable to connect to Redis`

**解决方案**:
- 检查 Redis 服务是否启动
- 验证 Redis 地址和端口
- 如果设置了密码，确保配置正确

### 4. Elasticsearch 连接失败

**问题**: `Elasticsearch connection refused`

**解决方案**:
- 检查 Elasticsearch 服务是否启动
- 验证 Elasticsearch 地址和端口（默认 9200）
- 检查 Elasticsearch 健康状态：`curl http://localhost:9200/_cluster/health`

### 5. 文件上传失败

**问题**: `Maximum upload size exceeded`

**解决方案**:
- 检查 application.yml 中的文件大小限制配置
- 确认 Nginx 配置中的 client_max_body_size 设置

### 6. 内存不足

**问题**: `Java heap space`

**解决方案**:
```bash
# 增加 JVM 堆内存
java -Xms1g -Xmx4g -jar target/rag-backend-0.0.1-SNAPSHOT.jar
```

### 7. API 密钥错误

**问题**: `Invalid API key for DashScope`

**解决方案**:
- 检查 DASHSCOPE_API_KEY 是否正确配置
- 确认 API 密钥是否有效且未过期
- 检查 API 密钥的权限设置

### 8. CORS 跨域问题

**问题**: 前端无法访问后端 API

**解决方案**:
- 检查 CorsConfig.java 配置
- 确认允许的域名和请求方法
- 生产环境建议通过 Nginx 反向代理处理

## 健康检查

```bash
# 检查应用是否启动成功
curl http://localhost:8989/actuator/health

# 查看应用日志
tail -f logs/application.log
```

## 性能优化建议

1. **JVM 参数优化**:
   ```bash
   java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar target/rag-backend-0.0.1-SNAPSHOT.jar
   ```

2. **数据库连接池优化**:
   - 根据实际负载调整连接池大小
   - 监控连接池使用情况

3. **Elasticsearch 优化**:
   - 配置合适的分片和副本数量
   - 定期清理过期索引

4. **Redis 缓存策略**:
   - 设置合理的缓存过期时间
   - 监控缓存命中率

## 日志配置

日志文件位置：`logs/application.log`

日志级别可在 application.yml 中配置：

```yaml
logging:
  level:
    root: INFO
    cn.sdh.backend: DEBUG
    org.springframework.ai: DEBUG
  file:
    name: logs/application.log
    max-size: 100MB
    max-history: 30
```

## 监控指标

建议监控以下指标：
- JVM 内存使用情况
- 线程池状态
- 数据库连接池状态
- API 响应时间
- 错误率
- Elasticsearch 查询性能
- Redis 缓存命中率

## 备份策略

1. **数据库备份**:
   ```bash
   # 每日备份
   mysqldump -u root -p sdh_rag > backup_$(date +%Y%m%d).sql
   ```

2. **Elasticsearch 备份**:
   - 配置快照仓库
   - 定期创建索引快照

3. **应用配置备份**:
   - 定期备份 application.yml 和环境变量配置
