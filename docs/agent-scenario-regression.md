# Agent 场景回归集（P0-3）

本轮为 AgentScope 2.0.3 建立固定输入、固定模型响应、真实 SDK 循环和 JDBC 状态存储的离线回归入口。它检查运行时协议与边界行为，作为模型接入和运行时升级前的快速检查。

## 场景与断言

种子集位于 `rag-backend/src/test/resources/agent-regression/scenarios.json`。每条记录有稳定 `id` 和受枚举约束的 `kind`，JUnit 参数化测试使用 ID 作为显示名；Maven XML 使用参数序号，脚本严格校验序号并在摘要中映射回 ID。新增场景必须有可观察的结果断言。

| 类别 | 数量 | 验证结果 |
|---|---:|---|
| ANSWER | 6 | 问题进入模型上下文，中文、英文、Markdown、JSON、Unicode、换行增量合并为完整答案；只调用一次模型 |
| TOOL | 6 | 真实 SDK 将模型调用路由到指定工具，保留调用 ID 及数字、布尔、嵌套对象、数组、空对象参数；工具结果回到下一轮上下文 |
| RESUME | 4 | 工具挂起后停止模型循环；重新创建运行时及存储实例，恢复序列化状态并注入原调用结果；不重复执行工具 |
| BUDGET | 6 | 无剩余 Token/步骤时不创建模型；恰好用完可以完成，超额拒绝且保留本次用量 |
| ISOLATION | 4 | 同 Run 不同用户、同用户不同 Run、两者均不同及大用户 ID 均不读取其他会话问答 |
| CANCEL | 2 | 启动前与事件检查点取消均传播取消原因，并停止文本发布 |
| MODEL_FAILURE | 2 | 首个文本前/后模型流失败均传播原故障，无隐式重试 |

每条场景独占 H2 数据库连接，结束后关闭。测试不共享 SDK 实例，不访问外部工具或真实模型，不需要 API Key。H2 只承载状态序列化检查；租约、事务和崩溃恢复继续由现有真实 MySQL 验收覆盖。

审批场景里的 `SUSPENDED` 是运行时协议，审批授权、危险动作判断和动作队列属于平台服务；它们继续由 `AgentActionReliabilityTest`、`AgentActionTransactionTest`、`AgentAskServiceLifecycleTest` 和 MySQL 测试验证。本种子集没有将模拟挂起当成真实审批流程验收，也不度量模型工具选择准确率、回答正确率或真实延迟。

## 执行

需要 JDK 21、Maven 3.9 和 PowerShell。仓库根目录执行：

```powershell
./scripts/test-agent-regression.ps1
# 本地 Maven 与 settings 可通过参数指定；已缓存依赖时可加 -Offline。
./scripts/test-agent-regression.ps1 -MavenPath E:/develop/apache-maven-3.9.8/bin/mvn.cmd -MavenSettings C:/Users/Administrator/.m2/settings-sdh-unblock.xml -Offline
```

不依赖 PowerShell 的构建环境可直接运行相同测试：

```sh
mvn -pl rag-backend -am -Dtest=AgentScenarioRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

成功条件：不少于 30 条场景，ID 唯一、所有类别存在；每条场景及数据集校验实际执行，无失败、错误或跳过。脚本还会拒绝过期/缺失的 JUnit 报告，并在运行前移除本门禁的旧摘要，避免失败后误用旧的 PASS。

输出均在忽略的构建目录：

- `rag-backend/target/surefire-reports/TEST-cn.sdh.backend.agent.AgentScenarioRegressionTest.xml`：参数序号、耗时和失败断言，序号对应 JSON 数组的顺序。
- `rag-backend/target/agent-regression-summary.json`：SDK 版本、数据集 SHA-256、场景清单、实际 JUnit 结果及总数。

完整后端回归执行 `mvn -pl rag-backend -am test`；真实 MySQL 故障门禁沿用 [MySQL 验收文档](agent-mysql-fault-acceptance.md)。新增入口没有替代这些检查，也没有配置 GitHub 分支保护或部署开关。

## 后续顺序

1. 基于现有评测数据集和运行轨迹增加 Agent 场景导入及结果记录，保存 Agent、模型、Prompt、工具和知识版本快照。
2. 接入受控真实模型评测：任务完成、工具选择/参数、关键步骤与禁止动作；与本离线运行时门禁分别报告。
3. 增加基线比较和人工复核，再把质量、安全、成本阈值接入发布流程。

## 行动记录

- 核对既有 P0-2 交付及工作区，确认按顺序进入场景回归，保留当前运行时实现。
- 使用版本化 JSON 表达 30 条场景，以稳定 ID 对应 JUnit 断言，避免只统计条数。
- 运行真实 SDK 和 JDBC 状态存储，固定外部模型响应，让失败能重复定位。
- 增加独立执行脚本和结构化报告，拒绝未执行、跳过和过期报告。
- 更新路线图和运行入口，明确后续先接场景与结果持久化，再做真实模型评分和发布门禁。

## 验证记录（2026-11-04）

- 首次场景执行：31 项通过，包含 30 条场景和 1 项数据集校验，无跳过。
- 反向验证：临时将两条场景设为同一个 ID，数据集校验准确报错（30 条记录只有 29 个唯一 ID）；脚本失败且清除预先放入的旧 PASS 摘要。随后在 `finally` 中恢复源数据。
- 正向脚本验证：恢复后 31 项全部通过；报告包含 AgentScope 2.0.3、数据集哈希及全部 30 个稳定 ID 与实际 JUnit 结果的对应关系。
- 全量后端测试：204 项，203 项通过，1 项沿用已有配置跳过，失败/错误均为 0。
- `git diff --check` 通过。本轮未修改生产代码、数据库迁移或前端，未重复运行真实 MySQL 故障测试和前端构建。

本地执行日志保存在忽略目录 `.workbuddy/agent-scenarios-first.log`、`.workbuddy/agent-scenarios-negative.log`、`.workbuddy/agent-scenarios-gate.log`、`.workbuddy/agent-scenarios-full-test.log`，不随源码提交。

当前工具环境未提供 crapper、dryer、mutator，不能据此声称通过复杂度、重复代码或变异测试门禁。
