# 第十阶段：业务经验到知识发布闭环

## 目标

把 Agent 的真实业务结果从“改进项”推进为正式知识，而不是停留在运营报表或人工备忘录。整个过程复用现有文档、MinIO、切片、向量索引与知识库权限体系。

## 状态流转

`DRAFT → IN_REVIEW → APPROVED → PUBLISHING → PUBLISHED`

- 审核不通过：`IN_REVIEW → REJECTED → DRAFT`
- 索引异常：`PUBLISHING → PUBLISH_FAILED`
- 撤销发布：`PUBLISHED/PUBLISH_FAILED → ROLLED_BACK`

## 发布链路

1. 在 Agent 运营改进队列中将“可复用知识”生成草稿，也可手工新建草稿。
2. 编辑知识正文，并配置最多 10 个真实业务回归问题。
3. 提交人工审核；知识库编辑者审核通过后才能发布。
4. 发布时生成 Markdown 文档并写入 MinIO、`knowledge_document` 与知识库关联表。
5. 数据库事务提交后，复用 `DocumentProcessService` 执行智能切片与 ES 增量索引。
6. 页面刷新时同步索引结果；只有产生可检索分块才进入 `PUBLISHED`。
7. 回归验证逐题执行向量召回，发布文档进入 Top 5 才算该问题通过。
8. 回滚会移除知识库关联和检索向量，原文档与发布审计仍保留，便于追溯。

## 数据与审计

- `knowledge_draft`：内容、来源改进项、审核结论、索引状态、回归问题与结果。
- `knowledge_publish_record`：发布、验证、回滚的操作人、时间、结果与详情。
- `agent_improvement_item`：仅在索引成功后自动标记为已解决，避免“提交发布即完成”的假闭环。

## 上线前检查

- 执行 Flyway V23、V24。
- 验证 MinIO、嵌入模型和 Elasticsearch 可用。
- 用一条真实 Agent 反馈完成“生成草稿—审核—发布—验证—回滚”全链路验收。
- 将业务回归问题纳入团队的知识质量门槛，关键知识建议要求全部通过后再对外使用。
