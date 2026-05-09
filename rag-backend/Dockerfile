# 多阶段构建：构建阶段
FROM maven:3.9-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 设置编码环境变量
ENV MAVEN_OPTS="-Dfile.encoding=UTF-8"

# 配置 Maven 阿里云镜像
RUN mkdir -p /root/.m2 && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" \
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd"> \
          <mirrors> \
            <mirror> \
              <id>aliyunmaven</id> \
              <name>aliyun maven</name> \
              <url>https://maven.aliyun.com/repository/public</url> \
              <mirrorOf>*</mirrorOf> \
            </mirror> \
          </mirrors> \
        </settings>' > /root/.m2/settings.xml

# 先复制所有 POM 文件（利用 Docker 缓存层）
COPY pom.xml ./pom.xml
COPY rag-backend/pom.xml ./rag-backend/pom.xml

# 安装父 POM 并下载依赖（合并为一步，避免重复解析）
RUN mvn install -N -B -q && \
    mvn -f rag-backend dependency:go-offline -B || \
    mvn -f rag-backend dependency:go-offline -B

# 复制源代码
COPY rag-backend/src ./rag-backend/src

# 构建应用（依赖已缓存，仅编译打包）
RUN mvn -f rag-backend package -DskipTests -B -q

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

# 安装必要的工具
RUN apk add --no-cache curl

# 创建应用用户（非 root 用户运行）
RUN addgroup -S spring && adduser -S spring -G spring

# 设置工作目录
WORKDIR /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/rag-backend/target/rag-backend-*.jar app.jar

# 更改文件所有者
RUN chown -R spring:spring /app

# 切换到非 root 用户
USER spring:spring

# 暴露端口
EXPOSE 8989

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8989/actuator/health || exit 1

# JVM 参数优化
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
