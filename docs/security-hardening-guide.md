# P0 安全加固与部署迁移指南

> 更新时间：2026-09-09
>
> 本文对应 [平台升级方向与演进路线图](platform-evolution-roadmap.md) 的 P0 第一轮实现。

## 1. 已落地的控制

- 应用配置不再保存可用的数据库、Neo4j、MinIO、JWT 或模型密钥默认值。
- `.env` 和 `.env.*` 默认不进入 Git，仅保留无密钥的 `.env.example`。
- `prod` Profile 会校验数据库、Neo4j、MinIO 和 JWT 凭据；缺失、弱值或两个 JWT 密钥相同都会阻止启动。
- 模型、系统设置、计划任务、Webhook、用户、角色和操作日志接口会从数据库重新确认当前用户仍是有效管理员。
- 模型接口只返回 `hasApiKey` 和固定掩码，不返回密钥片段。
- Webhook 管理响应不返回签名 Secret 与自定义 Header；更新时留空会保留原值。
- Webhook 默认仅允许公网 HTTPS，始终拒绝回环、链路本地和组播地址；内网地址与 HTTP 必须显式开启。
- 聊天、模型、工作流、评测、意图路由和记忆相关日志默认不输出正文或密钥片段。

## 2. 部署前必需变量

从 `.env.example` 复制配置，并至少设置：

```text
DB_PASSWORD
NEO4J_PASSWORD
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
JWT_ADMIN_SECRET
JWT_USER_SECRET
```

使用外部模型时，再配置 `DASHSCOPE_API_KEY` 或 `OPENAI_API_KEY`。两个 JWT Secret 必须不同且至少 32 个随机字符；不要使用姓名、项目名、日期或示例值。

PowerShell 可使用系统安全随机数生成 JWT Secret：

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Linux/macOS 可使用：

```bash
openssl rand -base64 48
```

## 3. 历史凭据轮换顺序

历史上进入过仓库、制品或共享日志的密钥都应视为已经泄露。仅删除代码中的字符串不能使旧密钥重新安全。

1. 在数据库、Neo4j 和 MinIO 中创建新凭据，并先验证新凭据可用。
2. 更新部署平台的 Secret 或主机 `.env`，滚动重启后端。
3. 验证健康检查、知识上传、检索、图谱与文件读取。
4. 禁用并删除旧基础设施凭据。
5. 在模型供应商创建新 API Key，更新 Secret，验证模型调用后撤销旧 Key。
6. 最后轮换 JWT Secret；这会使现有登录 Token 失效，应提前通知用户重新登录。
7. 检查 CI 日志、镜像层、备份和历史发布包，删除仍携带旧密钥的可访问制品。

## 4. Webhook 网络策略

生产环境默认保持：

```text
WEBHOOK_ALLOW_HTTP=false
WEBHOOK_ALLOW_PRIVATE_NETWORK=false
```

只有 Webhook 接收方位于受控内网且完成网络风险评估后，才设置 `WEBHOOK_ALLOW_PRIVATE_NETWORK=true`。即使开启，回环、链路本地和组播地址仍会被拒绝，以降低访问主机服务和云元数据端点的风险。

`WEBHOOK_ALLOW_HTTP=true` 只适合隔离测试环境。生产环境不应通过该开关连接明文 HTTP 接收方。

## 5. 上线验证

- 不设置任一必需 Secret，以 `prod` Profile 启动时，应用应明确失败。
- 普通用户访问模型管理、系统设置、计划任务、Webhook、用户、角色或操作日志接口，应返回业务码 403。
- 被禁用的管理员即使持有未过期 JWT，也不能继续使用管理员接口。
- 模型列表响应不能包含 `apiKey`，`apiKeyMasked` 只能是固定的 `********`。
- Webhook 查询响应中的 `secret` 和 `headers` 必须为空。
- Webhook 指向 `127.0.0.1`、链路本地地址或未授权内网地址时必须被拒绝。
- 运行安全与业务单元测试：`mvn -pl rag-backend test`。需要启动完整 Spring 上下文时，先准备外部基础设施和全部环境变量，再设置 `RUN_INTEGRATION_TESTS=true`。

## 6. 下一轮安全工作

- 提供可插拔 Secret Provider，对接企业 KMS/Vault，并迁移数据库中的模型 Key、Webhook Secret 和连接器凭据。
- 建立租户模型和 `tenant_id` 数据边界。
- 在 CI 增加 Gitleaks/同类凭据扫描、依赖漏洞扫描、Flyway 集成测试和 API 鉴权回归。
- 为 JWT 增加 `kid` 与双密钥轮换窗口，避免后续轮换必须让所有会话同时失效。
- 建立数据分类和敏感字段策略，统一控制日志、模型调用、导出与评测样本的内容范围。
