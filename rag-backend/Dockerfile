# 多阶段构建：构建阶段
FROM maven:3.9-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 设置编码环境变量
ENV MAVEN_OPTS="-Dfile.encoding=UTF-8"

# 先复制父 POM 并安装到本地仓库
COPY pom.xml ./pom.xml
RUN mvn install -N -B -Dproject.build.sourceEncoding=UTF-8

# 复制子模块 POM 并下载依赖（利用 Docker 缓存）
COPY rag-backend/pom.xml ./rag-backend/pom.xml
RUN mvn -f rag-backend dependency:go-offline -B -Dproject.build.sourceEncoding=UTF-8

# 复制源代码
COPY rag-backend/src ./rag-backend/src

# 构建应用
RUN mvn -f rag-backend clean package -DskipTests -B -Dproject.build.sourceEncoding=UTF-8

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
