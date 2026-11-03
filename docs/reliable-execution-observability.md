# 可靠执行可观测性

> 实施状态：基础 Trace、持久化指标和阈值告警已完成
> 更新时间：2026-09-14

## Trace 协议

HTTP 入口优先读取 W3C `traceparent` 的 Trace ID，其次读取 `X-Trace-Id`。只有格式合法、Trace ID 与 Span ID 均非零的 W3C 上下文会被接受，非法值会被替换，响应始终返回最终使用的 `X-Trace-Id`。

```text
HTTP traceparent / X-Trace-Id
  └─ TraceContext + 日志 MDC
       ├─ transactional_outbox.trace_id
       └─ platform_job.trace_id
            └─ Worker 恢复 MDC
                 └─ Webhook traceparent + X-Trace-Id
```

日志控制台格式包含 `[trace=...]`。异步任务不依赖线程局部变量保存 Trace，而是从数据库任务记录恢复，因此重启和租约接管不会断链。

## 管理员指标

管理员调用 `GET /api/admin/reliable-execution/metrics`，或打开任务中心，可查看：

- 排队、等待重试、运行中和累计死信任务数；
- Outbox 积压与死信；
- Webhook 投递积压与死信；
- 最近 24 小时任务数、成功数、终态失败数、发生重试的任务数和成功率；
- 最老排队任务等待秒数；
- 当前活动告警及实际值、阈值。

Micrometer 同时注册以下 Gauge，后续接入 Prometheus Registry 后可直接采集：

| 指标 | `state` 标签 |
|---|---|
| `sdh.jobs.count` | `backlog`、`running`、`dead_letter` |
| `sdh.outbox.count` | `backlog` |
| `sdh.webhook.deliveries.count` | `backlog`、`dead_letter` |

指标从数据库持久化状态采样，不会因为进程重启而清零。默认每 15 秒更新一次，管理 API 返回最近一次成功快照，数据库短暂故障不会覆盖已有快照。

## 告警阈值

| 环境变量 | 默认值 | 触发条件 |
|---|---:|---|
| `ALERT_JOB_BACKLOG` | 100 | 排队与等待重试任务总数达到阈值 |
| `ALERT_OLDEST_JOB_SECONDS` | 300 | 最老排队任务等待时间达到阈值 |
| `ALERT_OUTBOX_BACKLOG` | 20 | 未完成中继的 Outbox 事件达到阈值 |
| `ALERT_DEAD_LETTERS_24H` | 1 | 最近 24 小时终态失败达到阈值 |
| `OPERATIONS_METRICS_REFRESH_MS` | 15000 | 指标刷新周期，不是告警阈值 |

告警只在“正常 → 触发”和“触发 → 恢复”时写日志，避免轮询周期内重复通知。当前管理 API 和日志是首期告警出口；下一阶段接企业告警通道，并增加抑制、合并和责任人路由。

## 当前边界

- 当前 Trace 是统一关联 ID，不等同于完整的 OpenTelemetry Trace/Span；检索、模型和工具步骤级 Span 仍待建设。
- 当前成功率按最近 24 小时进入终态的成功与失败任务计算，取消任务不计入分母。
- Actuator 仍只公开健康检查，避免未经管理员鉴权直接暴露运营指标；指标通过管理员 API 读取。
