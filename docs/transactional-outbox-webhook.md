# Transactional Outbox 与 Webhook 可靠投递

> 实施状态：已完成第一阶段
> 对应数据库版本：Flyway V29
> 更新时间：2026-09-14

## 目标

业务代码不再在当前请求或调度线程中直接发送 Webhook。事件与业务状态在同一数据库事务内写入 `transactional_outbox`，提交后由中继器生成独立的 `WEBHOOK_DELIVERY` 任务，从而保证应用重启、下游短暂故障和多实例竞争时事件仍可恢复。

## 执行链路

```text
业务事务
  └─ 写 transactional_outbox + webhook_delivery
       └─ Outbox Relay（CAS 抢占、租约恢复）
            └─ platform_job / WEBHOOK_DELIVERY
                 ├─ 成功：delivery = SUCCEEDED
                 ├─ 暂时失败：Job 退避重试，delivery = FAILED
                 └─ 尝试耗尽：Job + delivery = DEAD_LETTER
```

`transactional_outbox.PUBLISHED` 表示事件已经可靠转交任务系统，不代表接收端已经成功处理；最终投递结果以 `webhook_delivery` 和对应 `platform_job` 为准。

## 一致性与幂等约束

- `event_key` 是业务事件幂等键。评测回归和运行失败使用实验运行 ID 构造稳定键，业务重试不会产生第二个事件。
- 每个 Outbox 事件和 Webhook 配置只生成一条投递记录，数据库唯一键阻止重复扇出。
- 每条投递使用稳定 `delivery_key`，同时写入请求体 `deliveryId` 和请求头 `X-Webhook-Delivery`。
- 入口请求的 Trace ID 会写入 Outbox 和 Job，并通过 `X-Trace-Id` 传递给接收端。
- 接收端应持久化 `X-Webhook-Delivery` 并拒绝重复处理。HTTP 外部副作用无法由发送方单独实现严格 exactly-once，因此平台提供 at-least-once + 接收端幂等协议。
- 中继器使用 CAS、租约和退避重试；在“任务已创建、Outbox 尚未标记发布”时崩溃也不会重复创建任务，因为 `platform_job.job_key` 唯一。

## 运维参数

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `OUTBOX_RELAY_INTERVAL_MS` | 1000 | 中继扫描间隔 |
| `OUTBOX_RELAY_BATCH_SIZE` | 50 | 单轮最大事件数 |
| `OUTBOX_RELAY_LEASE_SECONDS` | 60 | 中继租约时长 |
| `JOB_DISPATCH_DELAY_MS` | 2000 | 可靠任务扫描间隔 |

## 当前边界与下一步

- 管理员投递历史与 Outbox 死信处理界面尚未开放，当前可通过数据库和统一任务记录排查。
- 已补充队列积压、重试率、死信指标与阈值告警，并把 Trace 从 Outbox 透传到 Job 和 HTTP 请求头。
- 下一批补充 OpenTelemetry Span、外部告警通道、管理端投递历史和模型成本预算。
- 随后将文档解析/索引、评测实验编排和 Agent 外部动作迁移到同一执行语义。
