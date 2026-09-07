# 业务知识源连接器规范

连接器实现 `KnowledgeSourceConnector`，负责把外部系统内容转换为统一的 `SourceItem`，知识处理流水线负责去重、版本更新、分块、向量化和索引。

每条内容必须提供稳定的 `externalId`。增量同步优先使用外部游标，其次使用 `changedSince`；删除通过 `deleted=true` 传播，不能只追加新版本。`metadata` 用于部门、产品、地区、生效时间等业务过滤字段，禁止写入访问令牌、Cookie、私钥等凭据。

连接器配置和凭据后续应分离存储：普通配置进入数据库，机密字段进入密钥管理服务，只在同步任务执行时短暂解析。同步任务需要记录开始/结束时间、游标、扫描数、新增数、更新数、删除数、失败原因，并以 `(source_id, external_id)` 保证幂等。

推荐首批适配器按实际客户场景选择，而不是一次接入全部平台：

1. 数据库/REST API：覆盖业务主数据、产品和规则系统。
2. 企业文档平台：覆盖 SharePoint、飞书、企业微信或 Notion。
3. 工单/客服系统：把高频真实问题持续送入评估数据集和知识补全队列。

## 已实现：API Push

知识库所有者可在“业务数据源”页创建 `api_push` 数据源，编辑者可通过界面联调，或由业务系统调用：

```http
POST /api/knowledge-base/{knowledgeId}/sources/{sourceId}/ingest
```

```json
{
  "cursor": "2026-09-07T15:00:00",
  "items": [
    {
      "externalId": "ticket-1001",
      "title": "退款规则",
      "content": "退款申请应在七天内提交。",
      "sourceUrl": "https://business.example/tickets/1001",
      "sourceUpdatedAt": "2026-09-07T14:30:00",
      "metadata": { "department": "客服部", "docType": "policy", "docDate": "2026-09-07" },
      "deleted": false
    }
  ]
}
```

`externalId` 在单一数据源内唯一：内容哈希未改变时不会重复索引；内容改变时更新文档并重新处理；`deleted=true` 时移除知识库关联及检索索引，但保留来源映射和同步记录。单批最多 500 条，失败后可以用相同 `externalId` 重试。

普通配置禁止包含 token、密码、Cookie、Authorization 或 API Key。`credentialRef` 只保存密钥管理系统中的引用。

## 生产接入：API Key

知识库所有者可在数据源的“API Key”面板创建、轮换或吊销生产密钥。密钥使用安全随机数生成，数据库只保存 SHA-256 摘要，明文只在创建或轮换后返回一次。生产系统调用独立端点，不再依赖用户登录态：

```http
POST /api/integration/knowledge-sources/{sourceId}/ingest
X-SDH-API-Key: sdh_xxxxxxxxxxxx.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Content-Type: application/json
```

请求体与上面的 API Push 格式相同。响应包含 `X-Request-Id`、`X-RateLimit-Limit` 和 `X-RateLimit-Remaining`，并使用真实 HTTP 401、403、429 或 5xx 状态。每个有效密钥可独立配置过期时间和每分钟调用上限；Redis 用于跨节点计数，Redis 故障时自动降级为单节点限流。

每次通过认证的同步调用都会写入审计记录，包括请求 ID、条目数、同步任务 ID、状态码、耗时和失败原因。审计不保存请求正文或 API Key 明文。

## 自动拉取：REST JSON

`rest_json` 连接器通过 GET 请求拉取与 API Push 相同的标准内容结构。平台会自动追加配置的游标参数与分页大小参数：

```http
GET https://business.example/api/knowledge?cursor=cursor-1&limit=100
Authorization: Bearer ${BUSINESS_KNOWLEDGE_TOKEN}
```

业务接口返回：

```json
{
  "items": [
    {
      "externalId": "policy-1001",
      "title": "退款规则",
      "content": "退款申请应在七天内提交。",
      "sourceUrl": "https://business.example/policies/1001",
      "sourceUpdatedAt": "2026-09-07T07:00:00Z",
      "metadata": { "department": "客服部", "docType": "policy" },
      "deleted": false
    }
  ],
  "nextCursor": "cursor-2",
  "hasMore": true
}
```

连接器配置支持 `endpoint`、`authType`、`authHeader`、`cursorParam`、`limitParam`、`pageSize`、`maxPagesPerRun`、`maxRunSeconds`、`timeoutSeconds`、`allowPrivateNetwork` 和 `allowInsecureHttp`。认证值不能进入配置，应通过 `credentialRef=env:VARIABLE_NAME` 从运行环境解析。

安全默认值包括 HTTPS、禁止跳转、5MB 响应上限、分页和运行时限，以及对回环、链路本地、组播和默认内网地址的 SSRF 防护。企业内网必须由所有者显式启用，回环和云元数据常用的链路本地地址始终禁止。

开启自动同步后，调度器每分钟扫描到期数据源，并在有界线程池中执行。每个数据源使用 Redis 分布式锁，避免多实例及手动/定时任务并发写入。失败后保留原游标，按 5、10、20、40、80、160、320 分钟退避，最高约 6 小时；已经落库的部分内容在重试时依靠 `externalId` 和内容哈希去重。

部署时依次执行 `V17__knowledge_sources.sql`、`V18__knowledge_source_api_keys.sql` 和 `V19__knowledge_source_scheduling.sql`，创建数据源同步、API Key、调用审计及可靠调度相关结构。
