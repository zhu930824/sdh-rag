# AgentScope 运行时升级实施记录

日期：2026-11-03。对应设计：[架构升级方案](agentscope-2-architecture-upgrade.md)。

> 本文记录 V31 首批实现。后续版本已补充写动作队列、执行租约与无动作运行的安全恢复，当前部署与行为契约请同时阅读 [V32 实施说明](reliable-agent-action-runtime.md)、[V33 执行租约](agent-run-execution-lease.md) 和 [V34 安全恢复](agent-run-safe-recovery.md)。

## 已交付的可构建版本

本轮实现了 AgentScope Java 2.0.3 的可切换 Agent 执行链路，以及快照、记忆隔离、动作核对和后台续跑的基础改造。它是分阶段方案的第一批代码交付，尚未完成方案中的全部生产运营能力，也未部署到实际环境。

| 能力 | 本次行为 |
|---|---|
| SDK 接入 | 锁定 core、DashScope、OpenAI 扩展为 2.0.3；显式配置模型，无新增自动配置 Starter |
| 运行边界 | `AgentRuntimePort` 隔离 SDK 消息；每次尝试创建独立 ReActAgent，复用现有 RAG 与受控工具服务 |
| 灰度与兼容 | Run 保存 engine / engineVersion；历史数据默认 legacy；恢复按原 Run 引擎执行，不跟随当前开关重新选择 |
| 模型输出 | SDK `streamEvents()` 的文本增量映射为现有 content 事件，结束时不重复推送整段答案 |
| 工具审批 | 保留平台审批中心；SDK ToolResultBlock 结构化挂起，审批后按原 toolCallId 注入结果 |
| SDK 检查点 | MySQL `agent_runtime_state` 按 userId + runKey + stateKey 保存；CAS 拒绝陈旧写入；读取故障直接失败 |
| 旧快照修复 | 按完整消息淘汰旧轮次；单条超限拒绝覆盖；损坏或错误形状的快照返回 409 |
| 工具调用幂等 | 校验同一幂等键的参数一致性；SDK 按 Run、工具及排序后的参数生成去重键，重复提议可复用原动作 |
| 审批定义保护 | 新动作记录工具定义指纹；执行前检查 endpoint、认证引用、Schema 和审批策略等是否变化 |
| 结果不确定 | 外部写操作异常或非成功响应记 UNKNOWN，禁止直接重试；提供核对 API 与页面 |
| 中断动作 | 超过 5 分钟仍 EXECUTING 的动作转 UNKNOWN；旧执行者不能覆盖已经改变的状态 |
| 后台续跑 | 从进程内 subscribe 改为 AGENT_RESUME Job；扫描等待审批的运行，从已决定动作补投递遗漏的续跑 |
| 记忆隔离 | 工作记忆使用用户和会话作用域；摘要历史查询增加 userId；移除线程级 episode 状态 |
| 记忆设置 | Run 保存 memoryEnabled，审批续跑沿用用户原选择 |

主要新增代码：

- [运行时接口](../rag-backend/src/main/java/cn/sdh/backend/agent/runtime/AgentRuntimePort.java)
- [SDK 适配器](../rag-backend/src/main/java/cn/sdh/backend/agent/runtime/AgentScopeRuntimeAdapter.java)
- [模型适配](../rag-backend/src/main/java/cn/sdh/backend/agent/runtime/AgentScopeModelFactory.java)
- [数据库检查点](../rag-backend/src/main/java/cn/sdh/backend/agent/runtime/JdbcAgentStateStore.java)
- [续跑 Job](../rag-backend/src/main/java/cn/sdh/backend/job/AgentResumeJobHandler.java)
- [续跑恢复扫描](../rag-backend/src/main/java/cn/sdh/backend/agent/AgentResumeRecoveryScheduler.java)
- [V31 迁移](../rag-backend/src/main/resources/db/migration/V31__agentscope_runtime.sql)

## 启用与回滚

1. 先备份测试环境数据库。通过项目正常 Flyway 流程按顺序执行至 V31；本次只在 H2 的 MySQL 兼容模式中验证了 V31，没有对真实 MySQL 执行迁移。
2. 使用新后端包部署测试环境；保留 `AGENT_RUNTIME=legacy`，先验证旧聊天、工具审批和历史查询。
3. 在测试环境设置 `AGENT_RUNTIME=agentscope` 并重启。沿用模型管理中的 provider、modelId、apiKey 和 baseUrl，支持 DashScope / OpenAI。未在模型行配置密钥时，回退到现有对应环境凭据。
4. 验证：只读查询、售后工具审批、拒绝、核对、审批后重启续跑、重复点击、关闭记忆后续跑。检查 run 详情中的 engine / engineVersion。
5. 验证通过后再扩大新 Run 流量。当前开关是实例级新运行选择，不包含百分比或租户灰度控制台。

回滚新流量：改回 `AGENT_RUNTIME=legacy` 并重启。已有 agentscope Run 仍由新版本后端按原引擎处理；不要直接降级到不认识 V31 字段和 SDK 状态的旧后端。新增列与检查点表保留，待活跃运行排空后再制定清理迁移。

工作记忆采用 `memory:working:v2:<userId>:<encodedSessionId>`。升级不读取原来仅含 sessionId 的缓存，避免把来源不明的缓存归给某用户；旧缓存按原 TTL 到期。当前没有 tenantId 隔离模型。

## 新的业务核对流程

UNKNOWN 表示无法凭平台响应确定下游是否已经执行。发起人或指定审批人可在业务动作页选择“核对结果”，提交业务回执或核对依据：

- 已成功执行：标记 SUCCEEDED，并触发关联 Agent 的后续处理。
- 已确认未执行：标记 FAILED；发起人可显式选择重试。

接口为 `POST /api/agent-actions/{id}/reconcile`，请求示例：

```json
{"outcome":"SUCCEEDED","receipt":"业务系统工单 T-20261103-42，人工查询确认已创建"}
```

平台记录核对人和依据。自动 HTTP 重试不会替代该判断。此流程要求操作者确实查询下游；平台目前没有各业务系统的自动对账连接器。

指纹校验适用于本版本新登记的动作。升级前没有保存指纹的旧待审批动作保持兼容，不能补造其原始工具定义；上线前应复核或排空这部分动作。

## 验证证据

| 验证 | 结果及边界 |
|---|---|
| 原始问题复现 | 新增快照与工作记忆测试先复现 5 个失败；写操作超时与幂等参数冲突也先复现失败，再修实现 |
| 后端全量测试 | 104 项，0 失败、0 错误、1 项环境集成测试按配置跳过；最终以 package 日志为准 |
| SDK 行为 | 使用合成 Model，验证多文本增量、审批后停止模型循环、原 toolCallId 恢复且不重复执行工具 |
| 检查点恢复 | SDK 测试使用 JDBC + H2；恢复时创建新的运行时和存储实例，跨 JSON 序列化恢复工具调用和结果 |
| 状态并发 | 插入竞争和陈旧版本更新被 CAS 拒绝；不等同于完整多副本故障压测 |
| V31 | H2 MySQL 模式执行迁移；需补真实 MySQL 迁移演练 |
| 后端制品 | `mvn -pl rag-backend -am package` 通过，生成可执行 Spring Boot JAR |
| 前端构建 | `npm run build` 通过，仍有原有大分包提示 |
| 前端全局类型检查 | 失败；隔离副本对照改动前的 agent.ts 和动作页面，前后均为完全相同的 320 条诊断，本次没有新增类型错误 |
| 代码格式 | `git diff --check` 无空白错误；未修改仓库换行策略 |

执行环境的 Maven 未加入 PATH，本轮使用 `E:\develop\apache-maven-3.9.8\bin\mvn.cmd` 与既有 `settings-sdh-unblock.xml`。对应日志保存在 `.workbuddy/agentscope-*.log`，没有输出模型密钥或读取生产配置。

没有执行真实模型供应商请求、完整应用启动联调、线上数据库迁移、生产故障注入或部署。合成 Model 测试证明运行时适配的行为，不代表所有 OpenAI 兼容服务都支持相同工具流格式。

## 剩余实施项

以下内容仍按架构方案继续推进，不能根据本轮代码宣称已完成：

1. **完整中途宕机恢复**：当前检查点以 SDK 调用边界为主；恢复扫描补偿的是 WAITING_APPROVAL 且动作已决定的运行。RUNNING 状态进程被强杀后的自动接管、单会话写入权和 fencing token 仍需实现及故障注入验证。中断动作只会保守转 UNKNOWN，不会自动再次执行。
2. **写操作可靠调度**：本轮把 Agent 后台续跑迁入 Job，动作 HTTP 执行仍在原服务内；批准—动作 Job—结果—恢复之间的完整事务 Outbox 协议尚未迁完。续跑 Job 设一次自动尝试，避免在不明确状态下反复执行，可在任务中心诊断和人工重放。
3. **完整事件回放**：仍兼容现有 SSE 协议，尚未落地平台事件表、游标续传和前端自动补齐；断线后可通过运行详情查看最终状态。
4. **记忆生命周期**：工作记忆可按用户清除，尚未覆盖长期提取任务在删除后的回写代际校验、所有检查点及原始历史的删除策略。不能把当前 clearAll 视为全平台数据擦除承诺。
5. **质量与成本**：Agent Eval 场景集、轨迹断言、发布快照、预算预留、费用归因、统一模型 Fallback 和 OTel Span 尚待实施。现有步骤和 Token 限额提供基础上限，不能替代完整成本预算协议。
6. **扩展能力**：Harness、沙箱、MCP、子 Agent、多租户及独立 Worker 按场景另行验收；本版本没有启用宿主机 Shell / 文件工具。

## 行动记录

| 步骤 | 原因与结果 |
|---|---|
| 确认工作区和依赖 | 保留所有原有未提交改动；找到本机 Maven，解析固定 2.0.3 制品 |
| 建立失败测试 | 复现快照损坏、用户混读、写操作不确定和幂等参数冲突 |
| 修复基础行为 | 完整消息裁剪、拒绝损坏恢复、用户作用域缓存及摘要、UNKNOWN 核对 |
| 接入 SDK | 使用真实制品 API 编译，实现结构化挂起、事件流和数据库检查点 |
| 核对 SDK 状态契约 | JDBC 行为测试发现缺失列表应返回空、缺失版本应为 0；按 SDK 实际契约修正，首存版本为 1 |
| 接入可靠续跑 | 持久化 Job 与遗漏投递扫描；阻止未决定动作提前入队耗尽任务 |
| 加固审批和界面 | 工具定义指纹、状态条件写回、核对入口、引擎与记忆设置持久化 |
| 验证与打包 | 后端测试、H2 检查点恢复、前端构建、类型基线对照和可执行制品 |
| 临时文件清理 | 自动审批检查拒绝删除隔离类型检查副本，返回 blocked by policy；副本保留于 `.workbuddy/agentscope-typecheck-baseline`，包含指向原 node_modules 的目录联接，不应递归跟随该联接清理 |

本轮未提交或推送代码；工作区同时包含用户原有改动，应在评审本轮文件范围后按小步 PR 集成。
